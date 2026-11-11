package com.hottopik.service.exam;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.ExamSourceRepository;
import com.hottopik.repository.exam.ProgramVersionRepository;
import com.hottopik.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// No test transaction: template rendering must work after the service session closes.
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentSourceRegressionTest {
    private static final Actor CM = new Actor("Lê Thu Hà", Actor.ROLE_CONTENT);
    @Autowired ExamImportService imports;
    @Autowired ExamComposerService composer;
    @Autowired ExamSourceRepository sources;
    @Autowired ProgramVersionRepository programs;
    @Autowired ProgramRoadmapService roadmaps;
    @Autowired MockMvc mvc;

    private UserDTO contentUser() {
        UserDTO user = new UserDTO();
        user.setId(2L);
        user.setEmail("content@hottopik.com");
        user.setFullName("Lê Thu Hà");
        user.setRole(Actor.ROLE_CONTENT);
        return user;
    }

    private ExamSource source() {
        ExamSource source = imports.createSource(new ExamImportService.SourceForm(
                "Nguồn kiểm tra " + UUID.randomUUID(), Track.TOPIK_II, 2026, null,
                null, false, "Đề nội bộ", null), CM);
        imports.startManual(source.getId(), CM);
        return source;
    }

    private String reading(Long sourceId, int no, String prompt, String opt2) {
        return imports.saveCard(sourceId, new ExamImportService.CardForm(null, "TPL2-REA-01",
                null, null, "읽기 지문", null, null, List.of(prompt), List.of("가"), List.of(opt2),
                List.of("다"), List.of("라"), List.of("1"), List.of("Giải thích"),
                null, null, null, null, null, null, List.of(no)), CM);
    }

    private void writing(Long sourceId) {
        imports.saveCard(sourceId, new ExamImportService.CardForm(null, "TPL2-WRI-01",
                null, null, "안내 ㉠ ㉡", null, null, List.of("안내 ㉠ ㉡"), null, null, null, null,
                null, List.of("Đáp án chấp nhận"), List.of("신청하세요"), List.of("오세요"),
                null, null, null, null, List.of(51)), CM);
    }

    @Test
    void statusButtonImportsQuestionsAndWritingAndShowsThemAfterSessionsClose() throws Exception {
        ExamSource source = source();
        reading(source.getId(), 1, "새로 입력한 읽기 문제", "나");
        writing(source.getId());

        mvc.perform(post("/content/sources/" + source.getId() + "/status")
                        .param("status", "ACTIVE").sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/content/exams/*"))
                .andExpect(flash().attribute("flashOk", containsString("1 câu và 1 đề Viết")));
        assertEquals(ImportJobStatus.ACTIVATED, imports.latestJob(source.getId()).orElseThrow().getStatus());
        assertEquals(1, imports.questionsOf(source.getId()).size());
        assertEquals(1, imports.writingOf(source.getId()).size());
        assertEquals(1, composer.library("DRAFT", null, null).stream()
                .filter(e -> e.getSource() != null && source.getId().equals(e.getSource().getId())).count());

        mvc.perform(get("/content/questions").param("source", source.getId().toString())
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk()).andExpect(content().string(containsString(source.getName())))
                .andExpect(content().string(containsString("새로 입력한 읽기 문제")));
        mvc.perform(get("/content/exams").param("tab", "DRAFT")
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk()).andExpect(content().string(containsString(source.getName())));
        mvc.perform(get("/content/sources/" + source.getId())
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("새로 입력한 읽기 문제")))
                .andExpect(content().string(containsString("Mở trình soạn đề")));
        assertThrows(ExamDomainException.class, () -> imports.activate(source.getId(), CM));
        imports.updateSourceStatus(source.getId(), SourceStatus.ACTIVE, CM);
        assertEquals(1, imports.questionsOf(source.getId()).size());
    }

    @Test
    void completesAnOldSourceMarkedActiveWithoutAnActivatedImportJob() throws Exception {
        ExamSource source = source();
        reading(source.getId(), 1, "복구된 문제", "나");
        source.setStatus(SourceStatus.ACTIVE);
        sources.saveAndFlush(source); // Reproduce the previous status-only action.
        mvc.perform(get("/content/sources/" + source.getId()).sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Hoàn tất kích hoạt")));
        imports.updateSourceStatus(source.getId(), SourceStatus.ACTIVE, CM);
        assertEquals(1, imports.questionsOf(source.getId()).size());
        assertEquals(ImportJobStatus.ACTIVATED, imports.latestJob(source.getId()).orElseThrow().getStatus());
    }

    @Test
    void validationMessagesFollowTheSelectedQuestionAndBlankImagesAreOptional() throws Exception {
        ExamSource source = source();
        String first = reading(source.getId(), 1, "", "나");
        String second = reading(source.getId(), 2, "두 번째 문제", "");
        String third = reading(source.getId(), 3, "세 번째 문제", "나");
        mvc.perform(get("/content/sources/" + source.getId()).param("card", first)
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("có thể để trống")))
                .andExpect(content().string(not(containsString("Bài 1, câu 1 thiếu câu hỏi."))))
                .andExpect(content().string(not(containsString("Bài 2, câu 1 thiếu phương án 2."))));
        mvc.perform(get("/content/sources/" + source.getId()).param("card", second)
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Bài 2, câu 1 thiếu phương án 2.")))
                .andExpect(content().string(not(containsString("Bài 1, câu 1 thiếu câu hỏi."))));
        mvc.perform(get("/content/sources/" + source.getId()).param("card", third)
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("id=\"current-question-errors\""))));
        imports.uploadMedia(source.getId(), List.of(
                new MockMultipartFile("files", "test.mp3", "audio/mpeg", SampleExamFactory.fakeMp3()),
                new MockMultipartFile("files", "a1.png", "image/png", SampleExamFactory.fakePng()),
                new MockMultipartFile("files", "a2.png", "image/png", SampleExamFactory.fakePng()),
                new MockMultipartFile("files", "a3.png", "image/png", SampleExamFactory.fakePng()),
                new MockMultipartFile("files", "a4.png", "image/png", SampleExamFactory.fakePng())), CM);
        String picture = imports.saveCard(source.getId(), new ExamImportService.CardForm(null, "TPL2-LIS-01",
                "test.mp3", null, null, "설명", "Nam nói, nữ trả lời.", List.of("그림을 고르세요"),
                List.of("a1.png"), List.of("a2.png"), List.of("a3.png"), List.of("a4.png"), List.of("1"),
                List.of("Giải thích"), null, null, null, null, null, null, List.of(1)), CM);
        assertTrue(imports.cards(source.getId()).stream().filter(c -> c.id().equals(picture))
                .findFirst().orElseThrow().problems().isEmpty());
        String textPictures = imports.saveCard(source.getId(), new ExamImportService.CardForm(null, "TPL2-LIS-01",
                "test.mp3", null, null, "설명", "Nam nói, nữ trả lời.", List.of("그림을 고르세요"),
                List.of("가"), List.of("나"), List.of("다"), List.of("라"), List.of("2"),
                List.of("Giải thích"), null, null, null, null, null, null, List.of(2)), CM);
        assertTrue(imports.cards(source.getId()).stream().filter(c -> c.id().equals(textPictures))
                .findFirst().orElseThrow().problems().stream().anyMatch(p -> p.contains("phải là file ảnh")));
        mvc.perform(post("/content/sources/" + source.getId() + "/status").param("status", "ACTIVE")
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("flashError"));
        assertEquals(SourceStatus.DRAFT, imports.getSource(source.getId()).getStatus());
        assertTrue(imports.questionsOf(source.getId()).isEmpty());
    }

    @Test
    void vipSeesTodayAndRoadmapWithLegacyPublishedArraysAndNoSelectedTarget() throws Exception {
        ProgramVersion program = roadmaps.published();
        program.setReviewOffsets("[2,7,14]");
        program.setPhaseSplit("[40,40,20]");
        programs.saveAndFlush(program);
        UserDTO vip = new UserDTO();
        vip.setId(901L);
        vip.setFullName("Nguyễn Hoàng Nam");
        vip.setEmail("vip-regression@example.com");
        vip.setRole("ROLE_VIP");
        vip.setVip(true);
        mvc.perform(get("/hom-nay").sessionAttr(AuthHelper.SESSION_KEY, vip))
                .andExpect(status().isOk()).andExpect(model().attributeExists("board"))
                .andExpect(model().attributeDoesNotExist("loadError"));
        mvc.perform(get("/lo-trinh").sessionAttr(AuthHelper.SESSION_KEY, vip))
                .andExpect(status().isOk()).andExpect(model().attributeExists("plan"))
                .andExpect(model().attributeDoesNotExist("loadError"));
    }

    @Test
    void errorsFromAnotherChildInASharedCardDoNotFollowTheFocusedQuestion() throws Exception {
        ExamSource source = source();
        imports.uploadMedia(source.getId(), List.of(new MockMultipartFile("files", "shared.mp3",
                "audio/mpeg", SampleExamFactory.fakeMp3())), CM);
        String card = imports.saveCard(source.getId(), new ExamImportService.CardForm(null, "TPL2-LIS-06",
                "shared.mp3", null, null, "설명", "Bản dịch.", List.of("", "두 번째 문제"),
                List.of("가", "가"), List.of("나", ""), List.of("다", "다"), List.of("라", "라"),
                List.of("1", "2"), List.of("", "Giải thích"),
                null, null, null, null, null, null, List.of(21, 22)), CM);
        mvc.perform(get("/content/sources/" + source.getId()).param("card", card).param("focus", "21")
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Bài 1, câu 1 thiếu giải thích.")))
                .andExpect(content().string(not(containsString("Bài 1, câu 2 thiếu phương án 2."))));
        mvc.perform(get("/content/sources/" + source.getId()).param("card", card).param("focus", "22")
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Bài 1, câu 2 thiếu phương án 2.")))
                .andExpect(content().string(not(containsString("Bài 1, câu 1 thiếu giải thích."))));
    }

    @Test
    void topikOneSourceOpensTheManualSheetAndHidesTheExcelDump() throws Exception {
        long id = createTopikOne();
        ExamSource saved = sources.findById(id).orElseThrow();
        assertEquals(Track.TOPIK_I, saved.getTrack());
        assertEquals(70, saved.getExpectedCount());
        mvc.perform(get("/content/sources/" + id).sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nhập đề")))
                .andExpect(content().string(containsString("Dạng câu")))
                .andExpect(content().string(containsString("Thêm câu")))
                .andExpect(content().string(not(containsString("Đổ nhanh từ Excel"))));
    }

    @Test
    void savedReadingQuestionReopensWithItsPrompt() throws Exception {
        long id = createTopikOne();
        mvc.perform(get("/content/sources/" + id).sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk());
        String prompt = "SYS reading prompt for question 31";
        MvcResult saved = mvc.perform(post("/content/sources/" + id + "/cards")
                        .param("templateCode", "TPL1-REA-01")
                        .param("typeCode", "I-D1")
                        .param("qno", "31")
                        .param("prompt", prompt)
                        .param("opt1", "가")
                        .param("opt2", "나")
                        .param("opt3", "다")
                        .param("opt4", "라")
                        .param("answer", "1")
                        .param("explanation", "Giải thích câu đọc")
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = saved.getResponse().getRedirectedUrl();
        assertNotNull(location);
        mvc.perform(get(location.replace("#paper", "")).sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(prompt)))
                .andExpect(content().string(containsString("Lưu câu này")))
                .andExpect(content().string(not(containsString("Mỗi câu cần một ô số trên lưới."))));
    }

    @Test
    void mediaKeepsMp3AndRejectsOtherAudio() throws Exception {
        long id = createTopikOne();
        mvc.perform(get("/content/sources/" + id).sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk());
        mvc.perform(multipart("/content/sources/" + id + "/media")
                        .file(new MockMultipartFile("files", "notes.txt", "text/plain", "not audio".getBytes()))
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flashInfo", containsString("chỉ nhận .mp3")));
        mvc.perform(multipart("/content/sources/" + id + "/media")
                        .file(new MockMultipartFile("files", "clip.mp3", "audio/mpeg", SampleExamFactory.fakeMp3()))
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flashOk", containsString("Đã lưu 1 file")));
        mvc.perform(get("/content/sources/" + id).sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("clip.mp3")));
        mvc.perform(multipart("/content/sources/" + id + "/media")
                        .file(oversized("files", "big.mp3", "audio/mpeg", MediaStorageService.MAX_AUDIO_BYTES + 1))
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flashInfo", containsString("15 MB")));
        assertTrue(imports.mediaOf(id).stream().noneMatch(file -> "big.mp3".equals(file.getFilename())));
        assertTrue(imports.mediaOf(id).stream().anyMatch(file -> "clip.mp3".equals(file.getFilename())));
    }

    @Test
    void excelNamesTheMissingQuestionAndRejectsTheWrongFile() throws Exception {
        long id = createTopikOne();
        mvc.perform(multipart("/content/sources/" + id + "/excel")
                        .file(new MockMultipartFile("file", "de.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", headerOnlyWorkbook()))
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/content/sources/" + id).sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nghe 1: Không đủ số câu")))
                .andExpect(content().string(containsString("thiếu câu")));

        mvc.perform(multipart("/content/sources/" + id + "/excel")
                        .file(new MockMultipartFile("file", "de.xls", "application/vnd.ms-excel", new byte[]{1, 2, 3}))
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flashError", containsString("Chỉ nhận file .xlsx")));
        mvc.perform(multipart("/content/sources/" + id + "/excel")
                        .file(oversized("file", "big.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                MediaStorageService.MAX_EXCEL_BYTES + 1))
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flashError", containsString("tối đa 10 MB")));
    }

    @Test
    void activationStoresOneDraftQuestionAndPreviewShowsFourOptions() throws Exception {
        long id = createTopikOne();
        mvc.perform(get("/content/sources/" + id).sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk());
        String prompt = "SYS reading prompt for activation";
        mvc.perform(post("/content/sources/" + id + "/cards")
                        .param("templateCode", "TPL1-REA-01")
                        .param("typeCode", "I-D1")
                        .param("qno", "31")
                        .param("prompt", prompt)
                        .param("opt1", "가")
                        .param("opt2", "나")
                        .param("opt3", "다")
                        .param("opt4", "라")
                        .param("answer", "2")
                        .param("explanation", "Giải thích")
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().is3xxRedirection());

        mvc.perform(post("/content/sources/" + id + "/activate").sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/content/exams/*"));
        assertEquals(1, imports.questionsOf(id).size());
        assertEquals(1, composer.library("DRAFT", null, null).stream()
                .filter(exam -> exam.getSource() != null && id == exam.getSource().getId()
                        && exam.getStatus() == ExamStatus.DRAFT)
                .count());

        mvc.perform(post("/content/sources/" + id + "/activate").sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flashError", containsString("Không thể kích hoạt lần nữa")));
        assertEquals(1, imports.questionsOf(id).size());

        mvc.perform(get("/content/sources/" + id + "/preview").sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(prompt)))
                .andExpect(content().string(containsString("가")))
                .andExpect(content().string(containsString("나")))
                .andExpect(content().string(containsString("다")))
                .andExpect(content().string(containsString("라")));
    }

    private long createTopikOne() throws Exception {
        MvcResult created = mvc.perform(post("/content/sources")
                        .param("name", "WF09 TOPIK I " + UUID.randomUUID())
                        .param("track", "TOPIK_I")
                        .param("year", "2026")
                        .param("expectedCount", "70")
                        .param("copyrightNote", "Đề nội bộ của nhóm")
                        .sessionAttr(AuthHelper.SESSION_KEY, contentUser()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/content/sources/*"))
                .andReturn();
        String location = created.getResponse().getRedirectedUrl();
        assertNotNull(location);
        return Long.parseLong(location.substring("/content/sources/".length()));
    }

    private static MockMultipartFile oversized(String field, String name, String type, long size) {
        return new MockMultipartFile(field, name, type, new byte[]{1}) {
            @Override
            public long getSize() {
                return size;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }
        };
    }

    private static byte[] headerOnlyWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(ParsedWorkbook.SHEET_QUESTIONS);
            Row header = sheet.createRow(0);
            int column = 0;
            for (String name : ExamWorkbookParser.QUESTION_COLUMNS) header.createCell(column++).setCellValue(name);
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
