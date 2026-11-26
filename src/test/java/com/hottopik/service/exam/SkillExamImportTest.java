package com.hottopik.service.exam;

import com.hottopik.entity.exam.ExamSource;
import com.hottopik.entity.exam.MediaAsset;
import com.hottopik.entity.exam.MediaKind;
import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;
import com.hottopik.repository.exam.MediaAssetRepository;
import com.hottopik.repository.exam.QuestionTemplateRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SkillExamImportTest {

    @Test
    void testBuildSkillTemplatesHeadersAndRows() throws IOException {
        // TOPIK I Listening (không còn cột "Tên file nghe", tổng 11 cột)
        byte[] top1LisBytes = ExamTemplateBuilder.buildSkillTemplate(Track.TOPIK_I, Skill.LISTENING);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(top1LisBytes))) {
            Sheet sheet = wb.getSheet("NGHE");
            assertNotNull(sheet);
            Row header = sheet.getRow(0);
            assertEquals("Câu", header.getCell(0).getStringCellValue());
            assertEquals("Mã dạng câu", header.getCell(1).getStringCellValue());
            assertEquals("Tên dạng câu", header.getCell(2).getStringCellValue());
            assertEquals("Phụ đề", header.getCell(3).getStringCellValue());
            assertEquals("Câu hỏi/Đề bài", header.getCell(4).getStringCellValue());
            assertEquals("Phương án 1", header.getCell(5).getStringCellValue());
            assertEquals("Phương án 2", header.getCell(6).getStringCellValue());
            assertEquals("Phương án 3", header.getCell(7).getStringCellValue());
            assertEquals("Phương án 4", header.getCell(8).getStringCellValue());
            assertEquals("Đáp án đúng", header.getCell(9).getStringCellValue());
            assertEquals("Giải thích", header.getCell(10).getStringCellValue());

            // 30 questions + 1 header = 31 rows (indices 0 to 30)
            assertEquals(30, sheet.getLastRowNum());
            Row firstRow = sheet.getRow(1);
            assertEquals(1.0, firstRow.getCell(0).getNumericCellValue(), 0.001);
            assertEquals("", firstRow.getCell(1).getStringCellValue());
            assertEquals("", firstRow.getCell(2).getStringCellValue());
            Sheet dangCauSheet = wb.getSheet("DANG_CAU");
            assertNotNull(dangCauSheet);
            assertTrue(dangCauSheet.getRow(0).getCell(0).getStringCellValue().contains("mặc định"));
            assertTrue(dangCauSheet.getRow(1).getCell(2).getStringCellValue().contains("Mặc định"));
        }

        // TOPIK I Reading
        byte[] top1ReadBytes = ExamTemplateBuilder.buildSkillTemplate(Track.TOPIK_I, Skill.READING);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(top1ReadBytes))) {
            Sheet sheet = wb.getSheet("DOC");
            assertNotNull(sheet);
            Row header = sheet.getRow(0);
            assertEquals("Đoạn văn chung", header.getCell(3).getStringCellValue());
            assertEquals("Câu hỏi/Đề bài", header.getCell(4).getStringCellValue());

            // 40 questions (31 to 70)
            assertEquals(40, sheet.getLastRowNum());
            Row firstRow = sheet.getRow(1);
            assertEquals(31.0, firstRow.getCell(0).getNumericCellValue(), 0.001);
            assertEquals("", firstRow.getCell(1).getStringCellValue());
            assertEquals("", firstRow.getCell(2).getStringCellValue());
            assertNotNull(wb.getSheet("DANG_CAU"));
        }

        // TOPIK II Writing
        byte[] top2WriteBytes = ExamTemplateBuilder.buildSkillTemplate(Track.TOPIK_II, Skill.WRITING);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(top2WriteBytes))) {
            Sheet sheet = wb.getSheet("VIET");
            assertNotNull(sheet);
            Row header = sheet.getRow(0);
            assertEquals("Câu", header.getCell(0).getStringCellValue());
            assertEquals("Mã dạng câu", header.getCell(1).getStringCellValue());
            assertEquals("Tên dạng câu", header.getCell(2).getStringCellValue());
            assertEquals("Đoạn văn dùng chung", header.getCell(3).getStringCellValue());
            assertEquals("Câu hỏi/Đề bài", header.getCell(4).getStringCellValue());
            assertEquals("Đáp án chấp nhận chỗ ㉠", header.getCell(5).getStringCellValue());
            assertEquals("Đáp án chấp nhận chỗ ㉡", header.getCell(6).getStringCellValue());
            assertEquals("Giải thích", header.getCell(7).getStringCellValue());

            // 4 questions (51 to 54)
            assertEquals(4, sheet.getLastRowNum());
            Row r51 = sheet.getRow(1);
            assertEquals(51.0, r51.getCell(0).getNumericCellValue(), 0.001);
            assertEquals("", r51.getCell(1).getStringCellValue());
            assertEquals("", r51.getCell(2).getStringCellValue());
            assertNotNull(wb.getSheet("DANG_CAU"));
        }
    }

    @Test
    void testBuildFullTemplateTopikII() throws IOException {
        byte[] fullBytes = ExamTemplateBuilder.buildFullTemplate(Track.TOPIK_II);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(fullBytes))) {
            assertNotNull(wb.getSheet("NGHE"));
            assertNotNull(wb.getSheet("DOC"));
            assertNotNull(wb.getSheet("VIET"));
            assertNotNull(wb.getSheet("DANG_CAU"));

            // Check Listening sheet row count
            assertEquals(50, wb.getSheet("NGHE").getLastRowNum());
            // Check Reading sheet row count
            assertEquals(50, wb.getSheet("DOC").getLastRowNum());
            // Check Writing sheet row count
            assertEquals(4, wb.getSheet("VIET").getLastRowNum());
        }
    }

    @Test
    void testParseSkillListeningWithUntouchedRows() throws IOException {
        byte[] lisTemplate = ExamTemplateBuilder.buildSkillTemplate(Track.TOPIK_I, Skill.LISTENING);
        // Fill question 1 in template, leaving 2..30 untouched
        byte[] filledExcel;
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(lisTemplate));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.getSheet("NGHE");
            Row r1 = s.getRow(1);
            r1.getCell(3).setCellValue("대화 내용");
            r1.getCell(4).setCellValue("질문입니다.");
            r1.getCell(5).setCellValue("선택 1");
            r1.getCell(6).setCellValue("선택 2");
            r1.getCell(7).setCellValue("선택 3");
            r1.getCell(8).setCellValue("선택 4");
            r1.getCell(9).setCellValue("2");
            r1.getCell(10).setCellValue("Giải thích câu 1");
            wb.write(out);
            filledExcel = out.toByteArray();
        }

        ParsedWorkbook parsed = ExamWorkbookParser.parseSkill(
                new ByteArrayInputStream(filledExcel), Track.TOPIK_I, Skill.LISTENING);

        assertEquals(1, parsed.questions.size(), "Chỉ đọc câu 1 có nội dung, bỏ qua các ô mẫu chưa điền");
        ParsedWorkbook.QuestionRow q = parsed.questions.get(0);
        assertEquals(1, q.no);
        assertEquals(Skill.LISTENING, q.skill);
        assertEquals("I-N1", q.typeCode);
        assertEquals("대화 내용", q.scriptKo);
        assertEquals("질문입니다.", q.promptKo);
        assertEquals(2, q.answer);
    }

    @Test
    void testParseFullTemplateWithMultiSheets() throws IOException {
        byte[] fullTemplate = ExamTemplateBuilder.buildFullTemplate(Track.TOPIK_II);
        byte[] filledExcel;
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(fullTemplate));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Fill 1 listening question
            Sheet sLis = wb.getSheet("NGHE");
            Row r1 = sLis.getRow(1);
            r1.getCell(3).setCellValue("대본 1");
            r1.getCell(4).setCellValue("문제 1");
            r1.getCell(5).setCellValue("보기 1");
            r1.getCell(6).setCellValue("보기 2");
            r1.getCell(7).setCellValue("보기 3");
            r1.getCell(8).setCellValue("보기 4");
            r1.getCell(9).setCellValue("1");

            // Fill 1 reading question
            Sheet sDoc = wb.getSheet("DOC");
            Row rDoc1 = sDoc.getRow(1);
            rDoc1.getCell(3).setCellValue("지문 1");
            rDoc1.getCell(4).setCellValue("문제 1");
            rDoc1.getCell(5).setCellValue("선택 1");
            rDoc1.getCell(6).setCellValue("선택 2");
            rDoc1.getCell(7).setCellValue("선택 3");
            rDoc1.getCell(8).setCellValue("선택 4");
            rDoc1.getCell(9).setCellValue("3");

            // Fill 1 writing question
            Sheet sViet = wb.getSheet("VIET");
            Row rV51 = sViet.getRow(1);
            rV51.getCell(3).setCellValue("지문 ( ㉠ ) 그리고 ( ㉡ )");
            rV51.getCell(4).setCellValue("빈칸 채우기");
            rV51.getCell(5).setCellValue("답1");
            rV51.getCell(6).setCellValue("답2");

            wb.write(out);
            filledExcel = out.toByteArray();
        }

        ParsedWorkbook parsed = ExamWorkbookParser.parse(new ByteArrayInputStream(filledExcel), Track.TOPIK_II);

        assertEquals(2, parsed.questions.size());
        assertEquals(1, parsed.writings.size());
        assertTrue(parsed.questions.stream().anyMatch(q -> q.skill == Skill.LISTENING && q.no == 1));
        assertTrue(parsed.questions.stream().anyMatch(q -> q.skill == Skill.READING && q.no == 1));
        assertEquals(51, parsed.writings.get(0).no);
    }

    @Test
    void testParseSkillWritingWithModelAnswerSynthesized() throws IOException {
        byte[] writeTemplate = ExamTemplateBuilder.buildSkillTemplate(Track.TOPIK_II, Skill.WRITING);
        byte[] filledExcel;
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(writeTemplate));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.getSheet("VIET");
            // Fill 51
            Row r51 = s.getRow(1);
            r51.getCell(3).setCellValue("초대합니다. 우리 동아리에서 ( ㉠ ). 시간이 있으신 분은 ( ㉡ ).");
            r51.getCell(4).setCellValue("다음 글의 ㉠과 ㉡에 들어갈 말을 쓰십시오.");
            r51.getCell(5).setCellValue("작은 음악회를 엽니다|음악회를 엽니다");
            r51.getCell(6).setCellValue("꼭 와 주십시오|와 주시기 바랍니다");
            r51.getCell(7).setCellValue("Mỗi chỗ 5 điểm");
            wb.write(out);
            filledExcel = out.toByteArray();
        }

        ParsedWorkbook parsed = ExamWorkbookParser.parseSkill(
                new ByteArrayInputStream(filledExcel), Track.TOPIK_II, Skill.WRITING);

        assertEquals(1, parsed.writings.size());
        ParsedWorkbook.WritingRow w = parsed.writings.get(0);
        assertEquals(51, w.no);
        assertEquals("작은 음악회를 엽니다|음악회를 엽니다", w.keys1);
        assertEquals("꼭 와 주십시오|와 주시기 바랍니다", w.keys2);
        assertTrue(w.modelAnswer.contains("㉠ 작은 음악회를 엽니다 ㉡ 꼭 와 주십시오"));
    }

    @Test
    void testDuplicateContentDetectionInExcel() {
        ParsedWorkbook wb = new ParsedWorkbook();
        ParsedWorkbook.QuestionRow q1 = new ParsedWorkbook.QuestionRow();
        q1.row = 2;
        q1.skill = Skill.READING;
        q1.no = 1;
        q1.typeCode = "I-D1";
        q1.passageKo = "이것은 공통 지문입니다.";
        q1.promptKo = "무엇에 대한 글인지 고르십시오.";
        q1.options = new String[]{"병원", "우체국", "은행", "식당"};
        q1.answerRaw = "3";
        q1.answer = 3;
        q1.explanationVi = "Giải thích";

        ParsedWorkbook.QuestionRow q2 = new ParsedWorkbook.QuestionRow();
        q2.row = 5;
        q2.skill = Skill.READING;
        q2.no = 4;
        q2.typeCode = "I-D1";
        q2.passageKo = "이것은 공통 지문입니다.";
        q2.promptKo = "무엇에 대한 글인지 고르십시오.";
        q2.options = new String[]{"병원", "우체국", "은행", "식당"};
        q2.answerRaw = "3";
        q2.answer = 3;
        q2.explanationVi = "Giải thích";

        wb.questions.add(q1);
        wb.questions.add(q2);

        ExamImportValidator.Result result = ExamImportValidator.validate(wb,
                new ExamImportValidator.Context(Track.TOPIK_I, false, false, java.util.Map.of(), hashes -> java.util.Map.of()));

        assertTrue(result.issues().stream().anyMatch(i ->
                i.message().contains("Trùng câu với Câu 1 (dòng 2)")
        ), "Phải phát hiện trùng lặp nội dung giữa Câu 4 và Câu 1 và chỉ rõ vị trí: " + result.issues());
    }

    @Test
    void testImagePlaceholderAndCleaning() {
        // Test isImagePlaceholder
        assertTrue(ExamImportService.isImagePlaceholder("Có hình ảnh"));
        assertTrue(ExamImportService.isImagePlaceholder("có hình ảnh"));
        assertTrue(ExamImportService.isImagePlaceholder("[Có hình ảnh]"));
        assertTrue(ExamImportService.isImagePlaceholder("(Có ảnh)"));
        assertTrue(ExamImportService.isImagePlaceholder("Có ảnh"));
        assertTrue(ExamImportService.isImagePlaceholder("x"));
        assertTrue(ExamImportService.isImagePlaceholder("yes"));
        assertTrue(ExamImportService.isImagePlaceholder("có"));

        // Valid image files must NOT be treated as placeholders
        assertFalse(ExamImportService.isImagePlaceholder("q1.png"));
        assertFalse(ExamImportService.isImagePlaceholder("graph.jpg"));
        assertFalse(ExamImportService.isImagePlaceholder("diagram.webp"));

        // Regular Korean and Vietnamese text must NOT be treated as placeholders
        assertFalse(ExamImportService.isImagePlaceholder("사과"));
        assertFalse(ExamImportService.isImagePlaceholder("다음 글을 읽고 맞지 않는 것을 고르십시오."));
        assertFalse(ExamImportService.isImagePlaceholder("Đáp án A"));

        // Test cleanImage
        assertNull(ExamImportService.cleanImage("Có hình ảnh"));
        assertNull(ExamImportService.cleanImage("[Có hình ảnh]"));
        assertNull(ExamImportService.cleanImage("Có ảnh"));
        assertNull(ExamImportService.cleanImage("사과")); // Not an image extension
        assertNull(ExamImportService.cleanImage(null));
        assertNull(ExamImportService.cleanImage(""));
        assertEquals("q1.png", ExamImportService.cleanImage("q1.png"));
        assertEquals("graph.jpg", ExamImportService.cleanImage("  graph.jpg  "));

        // Test cleanOptionText
        assertEquals("", ExamImportService.cleanOptionText("[Có hình ảnh]"));
        assertEquals("", ExamImportService.cleanOptionText("Có hình ảnh"));
        assertEquals("", ExamImportService.cleanOptionText("Có ảnh"));
        assertEquals("사과", ExamImportService.cleanOptionText("사과"));
        assertEquals("선택지 1", ExamImportService.cleanOptionText("선택지 1"));

        // Test cleanPromptText
        assertEquals("", ExamImportService.cleanPromptText("Có hình ảnh"));
        assertEquals("다음 그림을 보고 맞는 것을 고르십시오.", ExamImportService.cleanPromptText("다음 그림을 보고 맞는 것을 고르십시오."));
    }

    @Test
    void testSyncManualCardsReadingAndWriting() {
        QuestionTemplateRepository templateRepo = (QuestionTemplateRepository) Proxy.newProxyInstance(
                QuestionTemplateRepository.class.getClassLoader(),
                new Class<?>[]{QuestionTemplateRepository.class},
                (proxy, method, args) -> {
                    if (method.getReturnType().equals(List.class)) return List.of();
                    if (method.getReturnType().equals(Optional.class)) return Optional.empty();
                    return null;
                }
        );
        ExamImportService service = new ExamImportService(null, null, null, null, null, null, null, null, null, templateRepo);

        ExamSource source = new ExamSource();
        source.setTrack(Track.TOPIK_II);

        ParsedWorkbook wb = new ParsedWorkbook();

        // 1. Reading question 1 (single question, marked with "Có hình ảnh")
        ParsedWorkbook.QuestionRow q1 = new ParsedWorkbook.QuestionRow();
        q1.no = 1;
        q1.skill = Skill.READING;
        q1.typeCode = "II-R1";
        q1.promptKo = "다음 글을 읽고 빈칸에 들어갈 것을 고르십시오.";
        q1.options = new String[]{"[Có hình ảnh]", "가을", "겨울", "봄"};
        q1.answer = 1;
        q1.imageFile = "Có hình ảnh";
        q1.explanationVi = "Giải thích câu 1";
        wb.questions.add(q1);

        // 2. Reading questions 19 and 20 (grouped questions sharing passage)
        ParsedWorkbook.QuestionRow q19 = new ParsedWorkbook.QuestionRow();
        q19.no = 19;
        q19.skill = Skill.READING;
        q19.typeCode = "II-R9";
        q19.passageKo = "공통 지문 내용입니다.";
        q19.promptKo = "19번 문제입니다.";
        q19.options = new String[]{"선택1", "선택2", "선택3", "선택4"};
        q19.answer = 2;
        wb.questions.add(q19);

        ParsedWorkbook.QuestionRow q20 = new ParsedWorkbook.QuestionRow();
        q20.no = 20;
        q20.skill = Skill.READING;
        q20.typeCode = "II-R9";
        q20.passageKo = "공통 지문 내용입니다.";
        q20.promptKo = "20번 문제입니다.";
        q20.options = new String[]{"보기1", "보기2", "보기3", "보기4"};
        q20.answer = 4;
        wb.questions.add(q20);

        // 3. Writing question 51
        ParsedWorkbook.WritingRow w51 = new ParsedWorkbook.WritingRow();
        w51.no = 51;
        w51.passageKo = "초대합니다.";
        w51.promptKo = "빈칸 채우기";
        w51.keys1 = "답안 1";
        w51.keys2 = "답안 2";
        w51.modelAnswer = "모범 답안";
        w51.chartJson = "Có ảnh";
        wb.writings.add(w51);

        // Call syncManualCards
        service.syncManualCards(source, wb, null);

        assertFalse(wb.manualCards.isEmpty(), "wb.manualCards phải được nạp");

        // Verify Question 1 card
        ParsedWorkbook.ManualCard cardQ1 = wb.manualCards.stream()
                .filter(c -> !c.asks.isEmpty() && c.asks.get(0).no != null && c.asks.get(0).no == 1)
                .findFirst().orElse(null);
        assertNotNull(cardQ1, "Phải có card cho câu 1");
        assertEquals("TPL2-REA-01", cardQ1.templateCode, "Template code phải map đúng dạng câu");
        assertEquals(1, cardQ1.asks.size());
        ParsedWorkbook.ManualAsk ask1 = cardQ1.asks.get(0);
        assertEquals(1, ask1.no);
        assertEquals("", ask1.options[0], "Chữ [Có hình ảnh] trong phương án phải bị lọc bỏ");
        assertEquals("가을", ask1.options[1]);
        assertNull(ask1.chart, "Ảnh 'Có hình ảnh' phải được gán null để quản lý tự thêm ảnh");

        // Verify Grouped Questions 19-20
        ParsedWorkbook.ManualCard cardQ19_20 = wb.manualCards.stream()
                .filter(c -> !c.asks.isEmpty() && c.asks.get(0).no != null && c.asks.get(0).no == 19)
                .findFirst().orElse(null);
        assertNotNull(cardQ19_20, "Phải có card nhóm cho câu 19-20");
        assertEquals(2, cardQ19_20.asks.size(), "Card dùng chung phải gồm 2 asks (câu 19 và câu 20)");
        assertEquals(19, cardQ19_20.asks.get(0).no);
        assertEquals(20, cardQ19_20.asks.get(1).no);
        assertEquals("공통 지문 내용입니다.", cardQ19_20.passageKo, "Đoạn văn dùng chung phải được gán đúng");

        // Verify Writing Question 51
        ParsedWorkbook.ManualCard cardW51 = wb.manualCards.stream()
                .filter(c -> !c.asks.isEmpty() && c.asks.get(0).no != null && c.asks.get(0).no == 51)
                .findFirst().orElse(null);
        assertNotNull(cardW51, "Phải có card cho câu viết 51");
        assertEquals("TPL2-WRI-01", cardW51.templateCode);
        assertNull(cardW51.asks.get(0).chart, "Biểu đồ câu viết có 'Có ảnh' phải gán null");
        assertEquals("초대합니다.", cardW51.passageKo);
        assertEquals("답안 1", cardW51.asks.get(0).keys1);
    }

    @Test
    void testExtractQuestionNumber() {
        // Standard formats
        assertEquals(1, ExamImportService.extractQuestionNumber("01.mp3"));
        assertEquals(2, ExamImportService.extractQuestionNumber("02.mp3"));
        assertEquals(4, ExamImportService.extractQuestionNumber("04.mp3"));
        assertEquals(14, ExamImportService.extractQuestionNumber("14.mp3"));
        assertEquals(30, ExamImportService.extractQuestionNumber("30.mp3"));
        assertEquals(50, ExamImportService.extractQuestionNumber("50.mp3"));

        // Prefix formats
        assertEquals(1, ExamImportService.extractQuestionNumber("cau_01.mp3"));
        assertEquals(4, ExamImportService.extractQuestionNumber("cau04.mp3"));
        assertEquals(14, ExamImportService.extractQuestionNumber("cau-14.mp3"));
        assertEquals(1, ExamImportService.extractQuestionNumber("q01.mp3"));
        assertEquals(2, ExamImportService.extractQuestionNumber("q-02.mp3"));
        assertEquals(15, ExamImportService.extractQuestionNumber("audio_cau_15.mp3"));

        // Grouped questions format (returns first question number)
        assertEquals(21, ExamImportService.extractQuestionNumber("21-22.mp3"));
        assertEquals(21, ExamImportService.extractQuestionNumber("21, 22.mp3"));
        assertEquals(21, ExamImportService.extractQuestionNumber("cau_21_22.mp3"));

        // Multi-question extraction: extracts both câu 21 và câu 22
        assertEquals(List.of(21, 22), ExamImportService.extractQuestionNumbers("21, 22.mp3"));
        assertEquals(List.of(21, 22), ExamImportService.extractQuestionNumbers("21,22.mp3"));
        assertEquals(List.of(21, 22), ExamImportService.extractQuestionNumbers("21-22.mp3"));
        assertEquals(List.of(21, 22), ExamImportService.extractQuestionNumbers("21_22.mp3"));
        assertEquals(List.of(21, 22), ExamImportService.extractQuestionNumbers("21 22.mp3"));
        assertEquals(List.of(21, 22), ExamImportService.extractQuestionNumbers("cau 21, 22.mp3"));
        assertEquals(List.of(21, 22), ExamImportService.extractQuestionNumbers("cau_21, 22.mp3"));
        assertEquals(List.of(21, 22), ExamImportService.extractQuestionNumbers("21. 22.mp3"));
        assertEquals(List.of(1), ExamImportService.extractQuestionNumbers("01.mp3"));
        assertEquals(List.of(4), ExamImportService.extractQuestionNumbers("04.mp3"));
        assertEquals(List.of(14), ExamImportService.extractQuestionNumbers("14.mp3"));

        // Invalid filenames
        assertNull(ExamImportService.extractQuestionNumber("sound.mp3"));
        assertNull(ExamImportService.extractQuestionNumber("background_music.mp3"));
        assertNull(ExamImportService.extractQuestionNumber(""));
        assertNull(ExamImportService.extractQuestionNumber(null));
        assertTrue(ExamImportService.extractQuestionNumbers("sound.mp3").isEmpty());
    }

    @Test
    void testFormatBytes() {
        assertEquals("500 B", ExamImportService.formatBytes(500));
        assertEquals("1.5 KB", ExamImportService.formatBytes(1536));
        assertEquals("2.0 MB", ExamImportService.formatBytes(2 * 1024 * 1024));
    }

    @Test
    void testMediaResponsesContentTypeHeader() throws IOException {
        java.nio.file.Path tempMp3 = java.nio.file.Files.createTempFile("test-audio", ".mp3");
        try {
            java.nio.file.Files.write(tempMp3, new byte[]{0x49, 0x44, 0x33, 0x03, 0x00}); // ID3 header
            org.springframework.mock.web.MockHttpServletResponse resp = new org.springframework.mock.web.MockHttpServletResponse();
            resp.setCharacterEncoding("UTF-8"); // Giả lập CharacterEncodingFilter của Spring
            MediaResponses.play(tempMp3, "test.mp3", com.hottopik.entity.exam.MediaKind.AUDIO, null, resp);
            System.out.println("ContentType was: " + resp.getContentType());
            System.out.println("Header Content-Type was: " + resp.getHeader("Content-Type"));
            assertEquals("audio/mpeg", resp.getHeader("Content-Type"));
        } finally {
            java.nio.file.Files.deleteIfExists(tempMp3);
        }
    }

    @Test
    void testFindMediaDecodingAndFallback() throws IOException {
        com.hottopik.repository.exam.MediaAssetRepository mediaRepo = (com.hottopik.repository.exam.MediaAssetRepository)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.MediaAssetRepository.class}, (proxy, method, args) -> {
                    if ("findBySourceIdAndFilename".equals(method.getName())) {
                        String name = (String) args[1];
                        if ("01 트랙 1.mp3".equals(name) || "21, 22 트랙 21, 22.mp3".equals(name)) {
                            com.hottopik.entity.exam.MediaAsset ma = new com.hottopik.entity.exam.MediaAsset();
                            ma.setFilename(name);
                            ma.setKind(com.hottopik.entity.exam.MediaKind.AUDIO);
                            ma.setStoragePath("sources/17/media/" + name);
                            return Optional.of(ma);
                        }
                        return Optional.empty();
                    }
                    if ("findBySourceIdOrderByFilenameAsc".equals(method.getName())) {
                        com.hottopik.entity.exam.MediaAsset m1 = new com.hottopik.entity.exam.MediaAsset();
                        m1.setFilename("01 트랙 1.mp3");
                        m1.setKind(com.hottopik.entity.exam.MediaKind.AUDIO);
                        com.hottopik.entity.exam.MediaAsset m2 = new com.hottopik.entity.exam.MediaAsset();
                        m2.setFilename("21, 22 트랙 21, 22.mp3");
                        m2.setKind(com.hottopik.entity.exam.MediaKind.AUDIO);
                        return List.of(m1, m2);
                    }
                    return null;
                });

        com.hottopik.repository.exam.ExamSourceRepository sourceRepo = (com.hottopik.repository.exam.ExamSourceRepository)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.ExamSourceRepository.class}, (proxy, method, args) -> Optional.empty());
        com.hottopik.repository.exam.ImportJobRepository jobRepo = (com.hottopik.repository.exam.ImportJobRepository)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.ImportJobRepository.class}, (proxy, method, args) -> Optional.empty());
        com.hottopik.repository.exam.QuestionRepository qRepo = (com.hottopik.repository.exam.QuestionRepository)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.QuestionRepository.class}, (proxy, method, args) -> List.of());
        com.hottopik.repository.exam.QuestionGroupRepository grpRepo = (com.hottopik.repository.exam.QuestionGroupRepository)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.QuestionGroupRepository.class}, (proxy, method, args) -> List.of());
        com.hottopik.repository.exam.WritingPromptRepository wRepo = (com.hottopik.repository.exam.WritingPromptRepository)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.WritingPromptRepository.class}, (proxy, method, args) -> List.of());
        com.hottopik.repository.exam.QuestionTemplateRepository tplRepo = (com.hottopik.repository.exam.QuestionTemplateRepository)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.QuestionTemplateRepository.class}, (proxy, method, args) -> Optional.empty());

        java.nio.file.Path tempStorage = java.nio.file.Files.createTempDirectory("ht-test-storage-");
        try {
            MediaStorageService storage = new MediaStorageService(tempStorage.toString());
            ExamImportService service = new ExamImportService(sourceRepo, jobRepo, mediaRepo, qRepo, grpRepo, wRepo, storage, null, null, tplRepo);

            // 1. Tìm bằng tên chính xác
            Optional<com.hottopik.entity.exam.MediaAsset> r1 = service.findMedia(17L, "01 트랙 1.mp3");
            assertTrue(r1.isPresent());
            assertEquals("01 트랙 1.mp3", r1.get().getFilename());

            // 2. Tìm bằng tên bị URL-encode (Thymeleaf / trình duyệt gửi)
            String encoded1 = org.springframework.web.util.UriUtils.encodePathSegment("01 트랙 1.mp3", java.nio.charset.StandardCharsets.UTF_8);
            Optional<com.hottopik.entity.exam.MediaAsset> r2 = service.findMedia(17L, encoded1);
            assertTrue(r2.isPresent());
            assertEquals("01 트랙 1.mp3", r2.get().getFilename());

            // 3. File có dấu phẩy và khoảng trắng (câu ghép 21, 22) bị encode %2C
            String encoded2 = org.springframework.web.util.UriUtils.encodePathSegment("21, 22 트랙 21, 22.mp3", java.nio.charset.StandardCharsets.UTF_8);
            Optional<com.hottopik.entity.exam.MediaAsset> r3 = service.findMedia(17L, encoded2);
            assertTrue(r3.isPresent());
            assertEquals("21, 22 트랙 21, 22.mp3", r3.get().getFilename());

            // 4. File fallback trên đĩa khi DB chưa có bản ghi
            java.nio.file.Path diskMediaDir = tempStorage.resolve("sources").resolve("17").resolve("media");
            java.nio.file.Files.createDirectories(diskMediaDir);
            java.nio.file.Files.write(diskMediaDir.resolve("49, 50 트랙 49, 50.mp3"), new byte[]{1, 2, 3});
            Optional<com.hottopik.entity.exam.MediaAsset> r4 = service.findMedia(17L, "49, 50 트랙 49, 50.mp3");
            assertTrue(r4.isPresent());
            assertEquals("49, 50 트랙 49, 50.mp3", r4.get().getFilename());
            assertEquals(com.hottopik.entity.exam.MediaKind.AUDIO, r4.get().getKind());

            // 5. File fallback trên đĩa khi gọi bằng URL encoded
            String encoded4 = org.springframework.web.util.UriUtils.encodePathSegment("49, 50 트랙 49, 50.mp3", java.nio.charset.StandardCharsets.UTF_8);
            Optional<com.hottopik.entity.exam.MediaAsset> r5 = service.findMedia(17L, encoded4);
            assertTrue(r5.isPresent());
            assertEquals("49, 50 트랙 49, 50.mp3", r5.get().getFilename());

        } finally {
            storageDelete(tempStorage);
        }
    }

    @Test
    void testIllegalCharQuestionMarkHandling() throws IOException {
        java.nio.file.Path tempStorage = java.nio.file.Files.createTempDirectory("ht-test-corrupt-");
        try {
            MediaStorageService storage = new MediaStorageService(tempStorage.toString());
            // 1. Kiểm tra resolveStored không bao giờ ném InvalidPathException khi chuỗi chứa '?'
            assertDoesNotThrow(() -> {
                java.nio.file.Path p = storage.resolveStored("sources/18/media/01 ?? 1.mp3");
                assertNotNull(p);
            });

            // 2. Kiểm tra baseName khử ký tự '?' và ký tự bất hợp pháp
            assertEquals("01__1.mp3", MediaStorageService.baseName("01??1.mp3"));
            assertEquals("01_test.mp3", MediaStorageService.baseName("01*test.mp3"));

            // 3. Chuẩn bị file thực tế trên đĩa với tiếng Hàn
            java.nio.file.Path realMediaDir = tempStorage.resolve("sources").resolve("18").resolve("media");
            java.nio.file.Files.createDirectories(realMediaDir);
            java.nio.file.Path realFile = realMediaDir.resolve("01 트랙 1.mp3");
            java.nio.file.Files.write(realFile, new byte[]{1, 2, 3});

            // Giả lập bản ghi bị hỏng storagePath chứa '?' (do lỗi SQL Server varchar)
            com.hottopik.entity.exam.ExamSource src = new com.hottopik.entity.exam.ExamSource();
            src.setId(18L);
            src.setStatus(com.hottopik.entity.exam.SourceStatus.DRAFT);

            com.hottopik.entity.exam.MediaAsset corruptedAsset = new com.hottopik.entity.exam.MediaAsset();
            corruptedAsset.setId(99L);
            corruptedAsset.setSource(src);
            corruptedAsset.setFilename("01 트랙 1.mp3");
            corruptedAsset.setStoragePath("sources/18/media/01 ?? 1.mp3"); // Bị '?'

            final boolean[] deleted = {false};
            final boolean[] saved = {false};
            com.hottopik.repository.exam.MediaAssetRepository mediaRepo = (com.hottopik.repository.exam.MediaAssetRepository)
                    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.MediaAssetRepository.class}, (proxy, method, args) -> {
                        if ("findById".equals(method.getName())) return Optional.of(corruptedAsset);
                        if ("delete".equals(method.getName())) { deleted[0] = true; return null; }
                        if ("save".equals(method.getName())) { saved[0] = true; return corruptedAsset; }
                        return null;
                    });
            com.hottopik.repository.exam.ExamSourceRepository sourceRepo = (com.hottopik.repository.exam.ExamSourceRepository)
                    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.ExamSourceRepository.class}, (proxy, method, args) -> Optional.of(src));
            com.hottopik.repository.exam.ImportJobRepository jobRepo = (com.hottopik.repository.exam.ImportJobRepository)
                    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.ImportJobRepository.class}, (proxy, method, args) -> Optional.empty());
            com.hottopik.repository.exam.QuestionRepository qRepo = (com.hottopik.repository.exam.QuestionRepository)
                    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.QuestionRepository.class}, (proxy, method, args) -> List.of());
            com.hottopik.repository.exam.QuestionGroupRepository grpRepo = (com.hottopik.repository.exam.QuestionGroupRepository)
                    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.QuestionGroupRepository.class}, (proxy, method, args) -> List.of());
            com.hottopik.repository.exam.WritingPromptRepository wRepo = (com.hottopik.repository.exam.WritingPromptRepository)
                    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.WritingPromptRepository.class}, (proxy, method, args) -> List.of());
            com.hottopik.repository.exam.QuestionTemplateRepository tplRepo = (com.hottopik.repository.exam.QuestionTemplateRepository)
                    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.QuestionTemplateRepository.class}, (proxy, method, args) -> Optional.empty());

            ExamImportService service = new ExamImportService(sourceRepo, jobRepo, mediaRepo, qRepo, grpRepo, wRepo, storage, null, null, tplRepo);

            // 4. mediaPath phải tự chữa lành bản ghi và trả về file thực tế trên đĩa mà không bị Exception
            java.nio.file.Path p = service.mediaPath(corruptedAsset);
            assertNotNull(p);
            assertTrue(java.nio.file.Files.exists(p));
            assertEquals("01 트랙 1.mp3", p.getFileName().toString());
            assertTrue(saved[0], "Phải tự động cập nhật lại storagePath hợp lệ vào CSDL");

            // 5. deleteMedia không được ném lỗi Illegal char <?> mà phải xoá thành công
            assertDoesNotThrow(() -> service.deleteMedia(18L, 99L, new com.hottopik.service.exam.Actor("system", com.hottopik.service.exam.Actor.ROLE_CONTENT, 1L)));
            assertTrue(deleted[0], "Bản ghi phải được xoá thành công");
        } finally {
            storageDelete(tempStorage);
        }
    }

    @Test
    void testImportReadingSharedPassage2And3Questions() throws IOException {
        byte[] template = ExamTemplateBuilder.buildSkillTemplate(Track.TOPIK_II, Skill.READING);
        byte[] excelBytes;
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(template)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.getSheet("DOC");
            // Cụm 2 câu dạng II-D7 dùng chung đoạn văn
            Row r1 = sheet.getRow(1) != null ? sheet.getRow(1) : sheet.createRow(1);
            r1.createCell(0).setCellValue(1);
            r1.createCell(1).setCellValue("II-D7");
            r1.createCell(3).setCellValue("Đoạn văn dùng chung cho 2 câu II-D7");
            r1.createCell(4).setCellValue("Đề bài câu 1");
            r1.createCell(5).setCellValue("A"); r1.createCell(6).setCellValue("B"); r1.createCell(7).setCellValue("C"); r1.createCell(8).setCellValue("D");
            r1.createCell(9).setCellValue("1");

            Row r2 = sheet.getRow(2) != null ? sheet.getRow(2) : sheet.createRow(2);
            r2.createCell(0).setCellValue(2);
            r2.createCell(1).setCellValue("II-D7");
            r2.createCell(3).setCellValue(""); // Để trống vì dùng chung với câu 1
            r2.createCell(4).setCellValue("Đề bài câu 2");
            r2.createCell(5).setCellValue("A"); r2.createCell(6).setCellValue("B"); r2.createCell(7).setCellValue("C"); r2.createCell(8).setCellValue("D");
            r2.createCell(9).setCellValue("2");

            // Cụm 3 câu dạng II-D12 dùng chung đoạn văn
            Row r3 = sheet.getRow(3) != null ? sheet.getRow(3) : sheet.createRow(3);
            r3.createCell(0).setCellValue(3);
            r3.createCell(1).setCellValue("II-D12");
            r3.createCell(3).setCellValue("Đoạn văn dài dùng chung cho 3 câu II-D12");
            r3.createCell(4).setCellValue("Đề bài câu 3");
            r3.createCell(5).setCellValue("A"); r3.createCell(6).setCellValue("B"); r3.createCell(7).setCellValue("C"); r3.createCell(8).setCellValue("D");
            r3.createCell(9).setCellValue("3");

            Row r4 = sheet.getRow(4) != null ? sheet.getRow(4) : sheet.createRow(4);
            r4.createCell(0).setCellValue(4);
            r4.createCell(1).setCellValue("II-D12");
            r4.createCell(3).setCellValue(""); // Để trống
            r4.createCell(4).setCellValue("Đề bài câu 4");
            r4.createCell(5).setCellValue("A"); r4.createCell(6).setCellValue("B"); r4.createCell(7).setCellValue("C"); r4.createCell(8).setCellValue("D");
            r4.createCell(9).setCellValue("4");

            Row r5 = sheet.getRow(5) != null ? sheet.getRow(5) : sheet.createRow(5);
            r5.createCell(0).setCellValue(5);
            r5.createCell(1).setCellValue("II-D12");
            r5.createCell(3).setCellValue(""); // Để trống
            r5.createCell(4).setCellValue("Đề bài câu 5");
            r5.createCell(5).setCellValue("A"); r5.createCell(6).setCellValue("B"); r5.createCell(7).setCellValue("C"); r5.createCell(8).setCellValue("D");
            r5.createCell(9).setCellValue("1");

            // Câu 6: câu đơn lẻ
            Row r6 = sheet.getRow(6) != null ? sheet.getRow(6) : sheet.createRow(6);
            r6.createCell(0).setCellValue(6);
            r6.createCell(1).setCellValue("II-D1");
            r6.createCell(3).setCellValue("Đoạn câu 6");
            r6.createCell(4).setCellValue("Đề bài câu 6");
            r6.createCell(5).setCellValue("A"); r6.createCell(6).setCellValue("B"); r6.createCell(7).setCellValue("C"); r6.createCell(8).setCellValue("D");
            r6.createCell(9).setCellValue("2");

            // Xóa các dòng còn lại của template
            for (int r = 7; r <= sheet.getLastRowNum(); r++) {
                Row rem = sheet.getRow(r);
                if (rem != null) sheet.removeRow(rem);
            }
            wb.write(out);
            excelBytes = out.toByteArray();
        }

        ParsedWorkbook pw = ExamWorkbookParser.parseSkill(new ByteArrayInputStream(excelBytes), Track.TOPIK_II, Skill.READING);
        assertEquals(6, pw.questions.size(), "Phải parse đủ 6 câu hỏi từ Excel");

        // Kiểm tra lan truyền tài nguyên trong QuestionRow
        assertEquals("Đoạn văn dùng chung cho 2 câu II-D7", pw.questions.get(1).passageKo, "Câu 2 phải được tự động lan truyền đoạn văn của câu 1");
        assertEquals("Đoạn văn dài dùng chung cho 3 câu II-D12", pw.questions.get(3).passageKo, "Câu 4 phải được tự động lan truyền đoạn văn của câu 3");
        assertEquals("Đoạn văn dài dùng chung cho 3 câu II-D12", pw.questions.get(4).passageKo, "Câu 5 phải được tự động lan truyền đoạn văn của câu 3");

        // Đồng bộ sang ManualCards
        ExamSource source = new ExamSource();
        source.setId(10L);
        source.setTrack(Track.TOPIK_II);

        com.hottopik.repository.exam.QuestionTemplateRepository tplRepo = (com.hottopik.repository.exam.QuestionTemplateRepository)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.QuestionTemplateRepository.class},
                        (proxy, method, args) -> Optional.empty());
        ExamImportService service = new ExamImportService(null, null, null, null, null, null, null, null, null, tplRepo);

        service.syncManualCards(source, pw, Skill.READING);

        // Phải gom thành đúng 3 cards:
        // Card 1: 2 câu (câu 1 và 2 dạng II-D7)
        // Card 2: 3 câu (câu 3, 4, 5 dạng II-D12)
        // Card 3: 1 câu (câu 6 dạng II-D1)
        assertEquals(3, pw.manualCards.size(), "Hệ thống phải tự động gom 6 dòng thành đúng 3 cards");

        ParsedWorkbook.ManualCard card1 = pw.manualCards.get(0);
        assertEquals("II-D7", card1.typeCode);
        assertEquals(2, card1.asks.size(), "Card 1 phải gom chung 2 câu vào cùng 1 lần");
        assertEquals("Đoạn văn dùng chung cho 2 câu II-D7", card1.passageKo);
        assertEquals(1, card1.asks.get(0).no);
        assertEquals(2, card1.asks.get(1).no);

        ParsedWorkbook.ManualCard card2 = pw.manualCards.get(1);
        assertEquals("II-D12", card2.typeCode);
        assertEquals(3, card2.asks.size(), "Card 2 phải gom chung 3 câu vào cùng 1 lần");
        assertEquals("Đoạn văn dài dùng chung cho 3 câu II-D12", card2.passageKo);
        assertEquals(3, card2.asks.get(0).no);
        assertEquals(4, card2.asks.get(1).no);
        assertEquals(5, card2.asks.get(2).no);

        ParsedWorkbook.ManualCard card3 = pw.manualCards.get(2);
        assertEquals("II-D1", card3.typeCode);
        assertEquals(1, card3.asks.size(), "Card 3 là câu đơn lẻ 1 câu/lần");
        assertEquals(6, card3.asks.get(0).no);
    }

    @Test
    void testImportListeningSharedSubtitleAndAudio2Questions() throws IOException {
        byte[] template = ExamTemplateBuilder.buildSkillTemplate(Track.TOPIK_II, Skill.LISTENING);
        byte[] excelBytes;
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(template)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.getSheet("NGHE");
            // Cụm 2 câu dạng II-N6 dùng chung phụ đề và file nghe
            Row r1 = sheet.getRow(1) != null ? sheet.getRow(1) : sheet.createRow(1);
            r1.createCell(0).setCellValue(1);
            r1.createCell(1).setCellValue("II-N6");
            r1.createCell(3).setCellValue("Lời thoại phụ đề tiếng Hàn chung cho 2 câu II-N6");
            r1.createCell(4).setCellValue("Đề bài câu 1 nghe");
            r1.createCell(5).setCellValue("A"); r1.createCell(6).setCellValue("B"); r1.createCell(7).setCellValue("C"); r1.createCell(8).setCellValue("D");
            r1.createCell(9).setCellValue("1");

            Row r2 = sheet.getRow(2) != null ? sheet.getRow(2) : sheet.createRow(2);
            r2.createCell(0).setCellValue(2);
            r2.createCell(1).setCellValue("II-N6");
            r2.createCell(3).setCellValue(""); // Để trống vì dùng chung phụ đề
            r2.createCell(4).setCellValue("Đề bài câu 2 nghe");
            r2.createCell(5).setCellValue("A"); r2.createCell(6).setCellValue("B"); r2.createCell(7).setCellValue("C"); r2.createCell(8).setCellValue("D");
            r2.createCell(9).setCellValue("2");

            // Câu 3: câu đơn lẻ
            Row r3 = sheet.getRow(3) != null ? sheet.getRow(3) : sheet.createRow(3);
            r3.createCell(0).setCellValue(3);
            r3.createCell(1).setCellValue("II-N1");
            r3.createCell(3).setCellValue("Lời thoại câu 3");
            r3.createCell(4).setCellValue("Đề bài câu 3");
            r3.createCell(5).setCellValue("A"); r3.createCell(6).setCellValue("B"); r3.createCell(7).setCellValue("C"); r3.createCell(8).setCellValue("D");
            r3.createCell(9).setCellValue("3");

            for (int r = 4; r <= sheet.getLastRowNum(); r++) {
                Row rem = sheet.getRow(r);
                if (rem != null) sheet.removeRow(rem);
            }
            wb.write(out);
            excelBytes = out.toByteArray();
        }

        ParsedWorkbook pw = ExamWorkbookParser.parseSkill(new ByteArrayInputStream(excelBytes), Track.TOPIK_II, Skill.LISTENING);
        assertEquals(3, pw.questions.size(), "Phải parse đủ 3 câu hỏi Nghe");

        // Đồng bộ sang ManualCards
        ExamSource source = new ExamSource();
        source.setId(11L);
        source.setTrack(Track.TOPIK_II);

        com.hottopik.repository.exam.QuestionTemplateRepository tplRepo = (com.hottopik.repository.exam.QuestionTemplateRepository)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.QuestionTemplateRepository.class},
                        (proxy, method, args) -> Optional.empty());
        ExamImportService service = new ExamImportService(null, null, null, null, null, null, null, null, null, tplRepo);

        service.syncManualCards(source, pw, Skill.LISTENING);

        // Phải gom thành đúng 2 cards:
        // Card 1: 2 câu dạng II-N6
        // Card 2: 1 câu dạng II-N1
        assertEquals(2, pw.manualCards.size(), "Hệ thống phải tự động gom 3 câu thành 2 cards (1 card đôi 2 câu, 1 card đơn)");

        ParsedWorkbook.ManualCard card1 = pw.manualCards.get(0);
        assertEquals("II-N6", card1.typeCode);
        assertEquals(2, card1.asks.size(), "Card 1 phải gom 2 câu hỏi Nghe vào cùng 1 lần");
        assertEquals("Lời thoại phụ đề tiếng Hàn chung cho 2 câu II-N6", card1.scriptKo);
        assertEquals(1, card1.asks.get(0).no);
        assertEquals(2, card1.asks.get(1).no);

        ParsedWorkbook.ManualCard card2 = pw.manualCards.get(1);
        assertEquals("II-N1", card2.typeCode);
        assertEquals(1, card2.asks.size());
        assertEquals(3, card2.asks.get(0).no);
    }

    @Test
    void testImportReadingTypeNameHeaderAndMapping() throws IOException {
        byte[] template = ExamTemplateBuilder.buildSkillTemplate(Track.TOPIK_II, Skill.READING);
        byte[] excelBytes;
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(template)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.getSheet("DOC");
            // Để cột 1 trống (mã dạng trống), nhập tên dạng vào cột 2: "Đọc đoạn văn và trả lời nhiều câu"
            Row r1 = sheet.getRow(1) != null ? sheet.getRow(1) : sheet.createRow(1);
            r1.createCell(0).setCellValue(1);
            r1.createCell(1).setCellValue(""); // Mã dạng để trống
            r1.createCell(2).setCellValue("Đọc đoạn văn và trả lời nhiều câu"); // Tên dạng tiếng Việt
            r1.createCell(3).setCellValue("Đoạn văn đọc theo tên dạng");
            r1.createCell(4).setCellValue("Đề 1");
            r1.createCell(5).setCellValue("A"); r1.createCell(6).setCellValue("B"); r1.createCell(7).setCellValue("C"); r1.createCell(8).setCellValue("D");
            r1.createCell(9).setCellValue("1");

            Row r2 = sheet.getRow(2) != null ? sheet.getRow(2) : sheet.createRow(2);
            r2.createCell(0).setCellValue(2);
            r2.createCell(1).setCellValue(""); // Mã dạng để trống
            r2.createCell(2).setCellValue("Đọc đoạn văn và trả lời nhiều câu");
            r2.createCell(3).setCellValue(""); // Trống vì dùng chung
            r2.createCell(4).setCellValue("Đề 2");
            r2.createCell(5).setCellValue("A"); r2.createCell(6).setCellValue("B"); r2.createCell(7).setCellValue("C"); r2.createCell(8).setCellValue("D");
            r2.createCell(9).setCellValue("2");

            for (int r = 3; r <= sheet.getLastRowNum(); r++) {
                Row rem = sheet.getRow(r);
                if (rem != null) sheet.removeRow(rem);
            }
            wb.write(out);
            excelBytes = out.toByteArray();
        }

        ParsedWorkbook pw = ExamWorkbookParser.parseSkill(new ByteArrayInputStream(excelBytes), Track.TOPIK_II, Skill.READING);
        assertEquals(2, pw.questions.size());
        assertEquals("II-D7", pw.questions.get(0).typeCode, "Hệ thống phải nhận diện được tên dạng 'Đọc đoạn văn và trả lời nhiều câu' thành mã II-D7");

        ExamSource source = new ExamSource();
        source.setId(12L);
        source.setTrack(Track.TOPIK_II);

        com.hottopik.repository.exam.QuestionTemplateRepository tplRepo = (com.hottopik.repository.exam.QuestionTemplateRepository)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.QuestionTemplateRepository.class},
                        (proxy, method, args) -> Optional.empty());
        ExamImportService service = new ExamImportService(null, null, null, null, null, null, null, null, null, tplRepo);

        service.syncManualCards(source, pw, Skill.READING);

        assertEquals(1, pw.manualCards.size(), "Phải tự động gom 2 câu thành đúng 1 card duy nhất");
        assertEquals("II-D7", pw.manualCards.get(0).typeCode);
        assertEquals(2, pw.manualCards.get(0).asks.size(), "Card phải chứa cả 2 câu hỏi");
        assertEquals("Đoạn văn đọc theo tên dạng", pw.manualCards.get(0).passageKo);
    }

    @Test
    void testPreCheckDuplicateDetectionAgainstExistingSystem() throws IOException {
        // 1. Giả lập file nhập vào gồm 15 câu kỹ năng Đọc TOPIK II
        ParsedWorkbook wb = new ParsedWorkbook();
        for (int i = 0; i < 15; i++) {
            ParsedWorkbook.QuestionRow q = new ParsedWorkbook.QuestionRow();
            q.row = i + 2;
            q.skill = Skill.READING;
            q.no = i + 1;
            q.promptKo = "Đề bài câu " + (i + 1);
            q.options = new String[]{"Lựa chọn 1", "Lựa chọn 2", "Lựa chọn 3", "Lựa chọn 4"};
            q.answer = (i % 4) + 1;
            wb.questions.add(q);
        }

        // 2. Tạo existingSystemWb với 15 câu giống hệt đang tồn tại trên hệ thống
        ParsedWorkbook existingSystemWb = new ParsedWorkbook();
        for (int i = 0; i < 15; i++) {
            ParsedWorkbook.QuestionRow q = new ParsedWorkbook.QuestionRow();
            q.skill = Skill.READING;
            q.no = i + 1;
            q.promptKo = "Đề bài câu " + (i + 1);
            q.options = new String[]{"Lựa chọn 1", "Lựa chọn 2", "Lựa chọn 3", "Lựa chọn 4"};
            q.answer = (i % 4) + 1;
            existingSystemWb.questions.add(q);
        }

        ExamImportValidator.Context ctx = new ExamImportValidator.Context(
                Track.TOPIK_II, false, false, Map.of(), hashes -> Map.of(), existingSystemWb
        );

        ExamImportValidator.Result result = ExamImportValidator.validate(wb, ctx);

        // Phải phát hiện đủ 15 cảnh báo trùng lặp với hệ thống
        long dupIssuesCount = result.issues().stream()
                .filter(iss -> iss.message() != null && iss.message().contains("đã có trên hệ thống"))
                .count();
        assertEquals(15, dupIssuesCount, "Phải cảnh báo trùng 15 câu đã có trên hệ thống");

        assertTrue(result.issues().stream().anyMatch(iss -> iss.message().contains("Trùng câu với Câu 1 đã có trên hệ thống (nội dung và đáp án 1 trùng khớp hoàn toàn, nếu nhập sẽ ghi đè dữ liệu cũ).")));
        assertTrue(result.issues().stream().anyMatch(iss -> iss.message().contains("Trùng câu với Câu 15 đã có trên hệ thống")));
    }

    @Test
    void testServicePreCheckDuplicatesWhenReimportingSameExcel() throws IOException {
        java.nio.file.Path tempStorage = java.nio.file.Files.createTempDirectory("ht-test-precheck-dup-");
        try {
            MediaStorageService storage = new MediaStorageService(tempStorage.toString());

            ExamSource src = new ExamSource();
            src.setId(16L);
            src.setTrack(Track.TOPIK_II);
            src.setStatus(com.hottopik.entity.exam.SourceStatus.DRAFT);

            // Tạo file excel 15 câu Đọc
            byte[] excelBytes = createReadingExcelWith15Questions();
            MockMultipartFile mockFile = new MockMultipartFile("file", "HotTopik_Mau_TOPIK_II_Doc.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

            // Giả lập lần import trước đã có 1 job với 15 câu trên hệ thống
            ParsedWorkbook draftWb = ExamWorkbookParser.parseSkill(new ByteArrayInputStream(excelBytes), Track.TOPIK_II, Skill.READING);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                    .setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.FIELD, com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);
            String draftJson = mapper.writeValueAsString(draftWb);

            com.hottopik.entity.exam.ImportJob prevJob = new com.hottopik.entity.exam.ImportJob();
            prevJob.setId(101L);
            prevJob.setSource(src);
            prevJob.setDraftJson(draftJson);
            prevJob.setStatus(com.hottopik.entity.exam.ImportJobStatus.VALID);

            com.hottopik.repository.exam.ExamSourceRepository sourceRepo = (com.hottopik.repository.exam.ExamSourceRepository)
                    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.ExamSourceRepository.class},
                            (proxy, method, args) -> Optional.of(src));
            com.hottopik.repository.exam.ImportJobRepository jobRepo = (com.hottopik.repository.exam.ImportJobRepository)
                    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.ImportJobRepository.class},
                            (proxy, method, args) -> {
                                if ("findFirstBySourceIdOrderByIdDesc".equals(method.getName())) return Optional.of(prevJob);
                                return Optional.empty();
                            });
            com.hottopik.repository.exam.MediaAssetRepository mediaRepo = (com.hottopik.repository.exam.MediaAssetRepository)
                    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.MediaAssetRepository.class},
                            (proxy, method, args) -> List.of());
            com.hottopik.repository.exam.QuestionRepository qRepo = (com.hottopik.repository.exam.QuestionRepository)
                    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.QuestionRepository.class},
                            (proxy, method, args) -> List.of());
            com.hottopik.repository.exam.QuestionGroupRepository grpRepo = (com.hottopik.repository.exam.QuestionGroupRepository)
                    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.QuestionGroupRepository.class},
                            (proxy, method, args) -> List.of());
            com.hottopik.repository.exam.WritingPromptRepository wRepo = (com.hottopik.repository.exam.WritingPromptRepository)
                    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.WritingPromptRepository.class},
                            (proxy, method, args) -> List.of());
            com.hottopik.repository.exam.QuestionTemplateRepository tplRepo = (com.hottopik.repository.exam.QuestionTemplateRepository)
                    Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{com.hottopik.repository.exam.QuestionTemplateRepository.class},
                            (proxy, method, args) -> Optional.empty());

            ExamImportService service = new ExamImportService(sourceRepo, jobRepo, mediaRepo, qRepo, grpRepo, wRepo, storage, null, null, tplRepo);

            Actor actor = new Actor("manager", Actor.ROLE_CONTENT, 1L);
            ExamImportService.ImportPreCheckReport report = service.preCheckExcel(16L, Skill.READING, mockFile, actor);

            assertNotNull(report);
            assertEquals(15, report.totalRows(), "Tổng số câu phải là 15");
            assertEquals(0, report.validRows(), "Số câu hợp lệ phải là 0 vì cả 15 câu đều bị báo Cảnh báo/Trùng");
            assertEquals(0, report.errorRows(), "Số câu lỗi phải là 0");
            assertEquals(15, report.duplicateRows(), "Hệ thống phải phát hiện và báo cáo trùng lặp đúng 15 câu đã có trên hệ thống!");

            long dupCount = report.issues().stream().filter(iss -> "DUPLICATE".equals(iss.issueType())).count();
            assertEquals(15, dupCount, "Tất cả 15 câu phải có issueType DUPLICATE với chi tiết và bằng chứng");
        } finally {
            storageDelete(tempStorage);
        }
    }
    @Test
    void testAutoMatchAudioFromUploadedMediaAssets() {
        ExamSource source = new ExamSource();
        source.setId(100L);
        source.setTrack(Track.TOPIK_I);

        java.util.List<MediaAsset> assets = new java.util.ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            MediaAsset a = new MediaAsset();
            a.setSource(source);
            a.setFilename(String.format("L%02d.mp3", i));
            a.setKind(MediaKind.AUDIO);
            assets.add(a);
        }

        MediaAssetRepository mediaRepo = (MediaAssetRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{MediaAssetRepository.class},
                (proxy, method, args) -> {
                    if ("findBySourceIdOrderByFilenameAsc".equals(method.getName())) {
                        return assets;
                    }
                    return null;
                }
        );

        QuestionTemplateRepository tplRepo = (QuestionTemplateRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{QuestionTemplateRepository.class},
                (proxy, method, args) -> Optional.empty()
        );

        ExamImportService service = new ExamImportService(null, null, mediaRepo, null, null, null, null, null, null, tplRepo);

        ParsedWorkbook pw = new ParsedWorkbook();
        for (int i = 1; i <= 30; i++) {
            ParsedWorkbook.QuestionRow q = new ParsedWorkbook.QuestionRow();
            q.skill = Skill.LISTENING;
            q.no = i;
            q.audioFile = null; // Excel import không có tên file nghe
            q.promptKo = "Đề bài câu " + i;
            q.options = new String[]{"1", "2", "3", "4"};
            q.answer = 1;
            pw.questions.add(q);
        }

        service.syncManualCards(source, pw, Skill.LISTENING);

        assertFalse(pw.manualCards.isEmpty(), "Phải tạo manualCards cho câu nghe");
        for (ParsedWorkbook.ManualCard card : pw.manualCards) {
            assertNotNull(card.audioFile, "Card nghe phải tự động được gán file audio đã upload");
            assertTrue(card.audioFile.endsWith(".mp3"));
        }

        for (ParsedWorkbook.QuestionRow q : pw.questions) {
            assertNotNull(q.audioFile, "QuestionRow nghe phải tự động được gán file audio đã upload");
            if (q.no <= 20) {
                assertEquals(String.format("L%02d.mp3", q.no), q.audioFile);
            }
        }
    }

    @Test
    void testPreserveAudioAndAutoMatchRangeAudio_Cau21_22() {
        ExamSource source = new ExamSource();
        source.setId(101L);
        source.setTrack(Track.TOPIK_I);

        MediaAsset rangeAsset = new MediaAsset();
        rangeAsset.setSource(source);
        rangeAsset.setFilename("cau_21-22.mp3");
        rangeAsset.setKind(MediaKind.AUDIO);

        MediaAssetRepository mediaRepo = (MediaAssetRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{MediaAssetRepository.class},
                (proxy, method, args) -> {
                    if ("findBySourceIdOrderByFilenameAsc".equals(method.getName())) {
                        return List.of(rangeAsset);
                    }
                    return null;
                }
        );

        QuestionTemplateRepository tplRepo = (QuestionTemplateRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{QuestionTemplateRepository.class},
                (proxy, method, args) -> Optional.empty()
        );

        ExamImportService service = new ExamImportService(null, null, mediaRepo, null, null, null, null, null, null, tplRepo);

        ParsedWorkbook pw = new ParsedWorkbook();
        for (int i = 21; i <= 22; i++) {
            ParsedWorkbook.QuestionRow q = new ParsedWorkbook.QuestionRow();
            q.skill = Skill.LISTENING;
            q.no = i;
            q.audioFile = null;
            q.scriptKo = "Hội thoại nghe dùng chung cho câu 21 và 22";
            q.promptKo = "Đề bài câu " + i;
            q.options = new String[]{"1", "2", "3", "4"};
            q.answer = 2;
            pw.questions.add(q);
        }

        service.syncManualCards(source, pw, Skill.LISTENING);

        assertEquals(1, pw.manualCards.size(), "2 câu nghe chung đoạn phải gộp thành 1 card");
        assertEquals("cau_21-22.mp3", pw.manualCards.get(0).audioFile, "Card phải tự động khớp file audio dải câu 21-22");
        assertEquals("cau_21-22.mp3", pw.questions.get(0).audioFile);
        assertEquals("cau_21-22.mp3", pw.questions.get(1).audioFile);
    }

    private byte[] createReadingExcelWith15Questions() throws IOException {
        byte[] template = ExamTemplateBuilder.buildSkillTemplate(Track.TOPIK_II, Skill.READING);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(template)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.getSheet("DOC");
            for (int i = 1; i <= 15; i++) {
                Row row = sheet.getRow(i) != null ? sheet.getRow(i) : sheet.createRow(i);
                row.createCell(0).setCellValue(i);
                row.createCell(1).setCellValue("II-D1");
                row.createCell(3).setCellValue("Đoạn văn đọc số " + i);
                row.createCell(4).setCellValue("Câu hỏi đọc số " + i);
                row.createCell(5).setCellValue("Lựa chọn 1");
                row.createCell(6).setCellValue("Lựa chọn 2");
                row.createCell(7).setCellValue("Lựa chọn 3");
                row.createCell(8).setCellValue("Lựa chọn 4");
                row.createCell(9).setCellValue(String.valueOf((i % 4) + 1));
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void storageDelete(java.nio.file.Path dir) {
        if (!java.nio.file.Files.exists(dir)) return;
        try (var walk = java.nio.file.Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> {
                try { java.nio.file.Files.deleteIfExists(f); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }
}
