package com.hottopik.service.exam;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.QuestionRepository;
import com.hottopik.repository.exam.QuestionTemplateRepository;
import com.hottopik.repository.exam.QuestionTypeRepository;
import com.hottopik.service.ApprovalService;
import com.hottopik.service.PracticeService;
import com.hottopik.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nhập đề đầy đủ → kích hoạt → Full Mock nháp → gửi duyệt → Admin duyệt → tự sinh Mini Test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExamModuleFlowTest {

    private static final Actor CM = new Actor("Lê Thu Hà", Actor.ROLE_CONTENT);
    private static final Actor ADMIN = new Actor("Admin", Actor.ROLE_ADMIN);

    @Autowired ExamImportService importService;
    @Autowired ExamComposerService composer;
    @Autowired QuestionBankService bank;
    @Autowired ApprovalService approvals;
    @Autowired QuestionRepository questions;
    @Autowired MockMvc mvc;
    @Autowired QuestionTemplateRepository templates;
    @Autowired QuestionTemplateSeeder templateSeeder;
    @Autowired QuestionTypeRepository typeRepo;
    @Autowired PracticeService practice;
    @Autowired com.hottopik.repository.exam.WritingPromptRepository writingPrompts;
    @Autowired ProgramRoadmapService roadmap;

    private ExamImportService.ActivationResult importFull(Track track, int variant) throws Exception {
        ExamSource src = importService.createSource(new ExamImportService.SourceForm(
                track.getLabel() + " mẫu " + variant, track, 2026, variant, null, false, "Đề mẫu nội bộ", null), CM);
        SampleExamFactory.Sample s = SampleExamFactory.full(track, variant);
        List<MultipartFile> files = new ArrayList<>();
        for (String a : s.audioFiles()) files.add(new MockMultipartFile("files", a, "audio/mpeg", SampleExamFactory.fakeMp3()));
        for (String i : s.imageFiles()) files.add(new MockMultipartFile("files", i, "image/png", SampleExamFactory.fakePng()));
        importService.uploadMedia(src.getId(), files, CM);
        ImportJob job = importService.uploadExcel(src.getId(), new MockMultipartFile("file", "de.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", s.excel()), CM);
        assertEquals(0, job.getErrorCount(), () -> importService.issues(job).toString());
        return importService.activate(src.getId(), CM);
    }

    @Test
    void writingPageRendersWithAndWithoutBankPrompt() throws Exception {
        writingPrompts.findByNoAndStatusOrderByIdDesc(51, QuestionStatus.ACTIVE).forEach(prompt -> {
            prompt.setStatus(QuestionStatus.DRAFT);
            writingPrompts.save(prompt);
        });
        mvc.perform(get("/writing").param("question", "51"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hiện chưa có đề luyện Viết")));
        importFull(Track.TOPIK_II, 32);
        mvc.perform(get("/writing").param("question", "51"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"promptText\"")));
    }

    @Test
    void learnerPracticeUsesExactTypeCountAndKeepsAttemptHistoryForRetry() throws Exception {
        importFull(Track.TOPIK_I, 31);

        PracticeAttempt attempt = practice.start(902L, "practice@example.com", "I-N1");
        List<PracticeService.QuestionView> questions = practice.questions(attempt);
        assertEquals(4, questions.size());
        assertEquals(Set.of(1, 2, 3, 4), questions.stream().map(PracticeService.QuestionView::no).collect(java.util.stream.Collectors.toSet()));

        Map<String, String> correctAnswers = questions.stream().collect(java.util.stream.Collectors.toMap(
                q -> q.id().toString(), q -> String.valueOf(q.answer())));
        PracticeAttempt completed = practice.submit(902L, attempt.getId(), correctAnswers);
        assertEquals(4, completed.getScore());
        assertEquals(4, practice.result(902L, completed.getId()).answers().size());
        assertEquals(1, practice.historyOfType(902L, "I-N1").size());

        PracticeAttempt retry = practice.retry(902L, "practice@example.com", completed.getId());
        assertEquals(completed.getQuestionIds(), retry.getQuestionIds());

        UserDTO learner = new UserDTO();
        learner.setId(902L);
        learner.setEmail("practice@example.com");
        learner.setRole("STUDENT");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthHelper.SESSION_KEY, learner);

        mvc.perform(get("/practice/prepare").param("typeCode", "I-N1").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lịch sử rèn luyện dạng này")))
                .andExpect(content().string(not(containsString("Câu trong ngân hàng"))));
        mvc.perform(get("/practice/play/{id}", retry.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("I-N1")));
        mvc.perform(get("/practice/result/{id}", completed.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đáp án dựa vào chi tiết")))
                .andExpect(content().string(containsString("Lịch sử các lần luyện dạng này")));

        mvc.perform(get("/practice"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kỹ năng Đọc")))
                .andExpect(content().string(containsString("Kỹ năng Viết")))
                .andExpect(content().string(containsString("I-N1")));
    }

    @Test
    void topik2FullFlowCreatesQuestionsDraftAndMiniTests() throws Exception {
        ExamImportService.ActivationResult r = importFull(Track.TOPIK_II, 1);
        assertEquals(100, r.questionCount());
        assertEquals(4, r.writingCount());
        assertNotNull(r.examId());

        assertTrue(importService.mediaOf(composer.get(r.examId()).getSource().getId()).stream()
                .allMatch(m -> m.getStoragePath().startsWith("sources/")), "đường dẫn file phải tương đối so với thư mục lưu trữ");

        Long sourceId = composer.get(r.examId()).getSource().getId();
        Question first = questions.findBySourceIdOrderBySkillAscOriginalNoAsc(sourceId).get(0);
        assertTrue(first.getCode().matches("E\\d{4}"));
        assertEquals("II-N1", first.getTypeCode());

        ExamComposerService.ExamFrame frame = composer.frame(r.examId());
        assertEquals(100, frame.required());
        assertEquals(100, frame.filled());
        assertTrue(frame.check().isOk(), () -> frame.check().toString());
        assertEquals(ExamStatus.DRAFT, frame.exam().getStatus());

        composer.submitForPublish(r.examId(), CM);
        Exam pending = composer.get(r.examId());
        assertEquals(ExamStatus.PENDING_APPROVAL, pending.getStatus());
        assertThrows(ExamDomainException.class, () -> composer.autofillAll(r.examId(), CM));

        assertTrue(approvals.decideRequest(pending.getApprovalRequestId(), true, "Đồng ý", "Admin"));
        Exam published = composer.get(r.examId());
        assertEquals(ExamStatus.PUBLISHED, published.getStatus());

        List<Exam> minis = composer.children(r.examId());
        assertEquals(3, minis.size());
        Map<ExamKind, Integer> counts = new EnumMap<>(ExamKind.class);
        minis.forEach(m -> counts.put(m.getKind(), m.questionCount()));
        assertEquals(50, counts.get(ExamKind.LISTENING));
        assertEquals(50, counts.get(ExamKind.READING));
        assertEquals(4, counts.get(ExamKind.WRITING));
        assertTrue(minis.stream().allMatch(m -> m.isAuto() && m.getStatus() == ExamStatus.PUBLISHED));
    }

    @Test
    void explanationCanBeEditedOnTheWebWithoutReuploadingExcel() throws Exception {
        ExamSource src = importService.createSource(new ExamImportService.SourceForm(
                "Sửa trên web", Track.TOPIK_I, 2026, 9, null, false, "Đề mẫu nội bộ", null), CM);
        SampleExamFactory.Sample s = SampleExamFactory.full(Track.TOPIK_I, 3);
        importService.uploadMedia(src.getId(), mediaFiles(s), CM);
        ImportJob job = importService.uploadExcel(src.getId(), new MockMultipartFile("file", "de.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", s.excel()), CM);
        assertEquals(0, job.getErrorCount(), () -> importService.issues(job).toString());
        assertFalse(importService.paper(src.getId()).isEmpty());

        importService.savePaper(src.getId(), new ExamImportService.PaperForm(false, Skill.LISTENING, 1,
                "알맞은 것을 고르십시오.", "가", "나", "다", "라", "B",
                "Giải thích bổ sung khi duyệt", "v3_L1.mp3", "남자: 오늘 학교에 가요?", null, null,
                null, null, null, null, null), CM);
        assertEquals(0, importService.latestJob(src.getId()).orElseThrow().getErrorCount());

        ExamImportService.ActivationResult r = importService.activate(src.getId(), CM);
        assertTrue(r.questionCount() > 0);
        Question edited = questions.findBySourceIdOrderBySkillAscOriginalNoAsc(src.getId()).stream()
                .filter(q -> q.getSkill() == Skill.LISTENING && q.getOriginalNo() == 1).findFirst().orElseThrow();
        assertEquals("Giải thích bổ sung khi duyệt", edited.getExplanationVi());
        assertEquals(2, edited.getAnswerIndex());
        Question untouched = questions.findBySourceIdOrderBySkillAscOriginalNoAsc(src.getId()).stream()
                .filter(q -> q.getSkill() == Skill.LISTENING && q.getOriginalNo() == 2).findFirst().orElseThrow();
        assertTrue(untouched.getPromptKo() != null && !untouched.getPromptKo().isBlank());
    }

    @Test
    void savingAnExplanationDoesNotEraseExcelContent() throws Exception {
        ExamSource src = importService.createSource(new ExamImportService.SourceForm(
                "Giữ nội dung Excel", Track.TOPIK_I, 2026, 4, null, false, "Đề mẫu nội bộ", null), CM);
        SampleExamFactory.Sample s = SampleExamFactory.full(Track.TOPIK_I, 4);
        importService.uploadMedia(src.getId(), mediaFiles(s), CM);
        importService.uploadExcel(src.getId(), new MockMultipartFile("file", "de.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", s.excel()), CM);
        ExamImportService.PaperSlot before = importService.paper(src.getId()).stream()
                .filter(p -> "LISTENING-1".equals(p.key())).findFirst().orElseThrow();
        assertFalse(before.prompt().isBlank());
        assertFalse(before.opt1().isBlank());

        importService.savePaper(src.getId(), new ExamImportService.PaperForm(false, Skill.LISTENING, 1,
                "", "", "", "", "", "",
                "Thêm giải thích, không xóa đề", "", "", "", "",
                null, null, null, null, null), CM);

        ExamImportService.PaperSlot after = importService.paper(src.getId()).stream()
                .filter(p -> "LISTENING-1".equals(p.key())).findFirst().orElseThrow();
        assertEquals(before.prompt(), after.prompt());
        assertEquals(before.opt1(), after.opt1());
        assertEquals(before.audio(), after.audio());
        assertEquals("Thêm giải thích, không xóa đề", after.explanation());
        assertEquals(70, importService.paper(src.getId()).stream().filter(ExamImportService.PaperSlot::filled).count());
    }

    @Test
    void oneListeningClipCoversEveryQuestionInTheBai() throws Exception {
        ExamSource src = importService.createSource(new ExamImportService.SourceForm(
                "Một file nghe", Track.TOPIK_II, 2026, 6, 104, false, "Đề mẫu nội bộ", null), CM);
        importService.startManual(src.getId(), CM);
        importService.savePaper(src.getId(), new ExamImportService.PaperForm(false, Skill.LISTENING, 21,
                "남자는 무엇을 합니까?", "회의", "복사", "예약", "검색", "2",
                "Người đàn ông đang photocopy.", "bai21.mp3", "남자: 회의 자료 복사했어요.", null, null,
                null, null, null, null, null), CM);
        importService.savePaper(src.getId(), new ExamImportService.PaperForm(false, Skill.LISTENING, 22,
                "여자는 어디에 갑니까?", "회사", "은행", "학교", "병원", "1",
                "", "", "", "", "",
                null, null, null, null, null), CM);

        ExamImportService.LearnerBai bai = importService.learnerBais(src.getId()).stream()
                .filter(b -> "LISTENING-21".equals(b.key())).findFirst().orElseThrow();
        assertEquals(2, bai.asks().size());
        assertEquals(21, bai.asks().get(0).no());
        assertEquals(22, bai.asks().get(1).no());
        assertEquals("bai21.mp3", bai.audio());
        assertEquals("남자: 회의 자료 복사했어요.", bai.script());
        assertTrue(bai.showGuide());
        assertTrue(bai.instructionKo().contains("듣고"));
        assertTrue(bai.pointsLine().contains("100 điểm"));

        UserDTO user = new UserDTO(2L, "content_manager", "content@hottopik.com", "", "Lê Thu Hà",
                Actor.ROLE_CONTENT, "Content Manager", "ACTIVE", "", "", "TOPIK_6", "TOPIK_5", false, null);
        mvc.perform(get("/content/sources/" + src.getId() + "/preview").sessionAttr(AuthHelper.SESSION_KEY, user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nghe lại được nhiều lần")))
                .andExpect(content().string(containsString("Phụ đề")))
                .andExpect(content().string(containsString("bai21.mp3")))
                .andExpect(content().string(containsString("남자는 무엇을 합니까?")))
                .andExpect(content().string(containsString("여자는 어디에 갑니까?")));
    }

    @Test
    void manualDraftStaysEditableWhileThePaperIsIncomplete() {
        ExamSource src = importService.createSource(new ExamImportService.SourceForm(
                "Nhập tay", Track.TOPIK_II, 2026, 1, 104, false, "Đề mẫu nội bộ", null), CM);
        ImportJob job = importService.startManual(src.getId(), CM);
        assertEquals(104, importService.paper(src.getId()).size());
        assertTrue(job.getErrorCount() > 0, "đề trống còn lỗi nhưng vẫn là bản để sửa");
        importService.savePaper(src.getId(), new ExamImportService.PaperForm(false, Skill.LISTENING, 21,
                "남자가 무엇을 하고 있습니까?", "회의", "복사", "예약", "검색", "1",
                "", "n21.mp3", "남자: 회의 자료 복사했어요?", null, null,
                null, null, null, null, null), CM);
        ImportJob again = importService.latestJob(src.getId()).orElseThrow();
        assertTrue(again.getErrorCount() > 0);
        assertTrue(importService.paper(src.getId()).stream()
                .anyMatch(p -> "LISTENING-21".equals(p.key()) && p.filled() && p.explanation().isEmpty()));
        assertTrue(questions.findBySourceIdOrderBySkillAscOriginalNoAsc(src.getId()).isEmpty(),
                "chưa đưa vào ngân hàng thì chưa có mã câu");
        assertThrows(ExamDomainException.class, () -> importService.activate(src.getId(), CM));
    }

    @Test
    void templateLibraryKeepsAdminEditsAndSharesOneMoldAcrossRanges() throws Exception {
        assertEquals(QuestionTypeCatalog.all().size(), typeRepo.count());
        assertTrue(QuestionTypeCatalog.all().stream().allMatch(t -> QuestionTemplateCatalog.templateCode(t.code()) != null));
        assertEquals("TPL2-LIS-06", QuestionTemplateCatalog.templateCode("II-N6"));
        assertEquals("TPL2-LIS-06", QuestionTemplateCatalog.templateCode("II-N8"));
        assertEquals("TPL2-LIS-06", typeRepo.findById("II-N7").orElseThrow().getTemplateCode());
        long before = templates.count();
        QuestionTemplate picture = templates.findByCode("TPL1-LIS-05").orElseThrow();
        assertEquals(DisplayMode.IMAGE_CHOICE, picture.getDisplayMode());
        assertEquals(4, picture.getOptionCount());
        assertNull(picture.getDefaultPoints());
        QuestionTemplate essay = templates.findByCode("TPL2-WRI-03").orElseThrow();
        assertEquals(200, essay.getMinChars());
        assertEquals(300, essay.getMaxChars());
        assertEquals(GradingMode.AI_ASSISTED, essay.getGrading());
        picture.setName("Khuôn tranh đã sửa");
        picture.setCustomized(true);
        templates.save(picture);
        templateSeeder.run(null);
        assertEquals("Khuôn tranh đã sửa", templates.findByCode("TPL1-LIS-05").orElseThrow().getName());
        assertEquals(before, templates.count());
        assertTrue(templates.findByCode("TPL2-REA-09").isPresent());
        assertTrue(QuestionTemplateCatalog.typeCodes("TPL2-REA-09").isEmpty());

        UserDTO user = new UserDTO(2L, "content_manager", "content@hottopik.com", "", "Lê Thu Hà",
                Actor.ROLE_CONTENT, "Content Manager", "ACTIVE", "", "", "TOPIK_6", "TOPIK_5", false, null);
        mvc.perform(get("/content/templates/TPL2-LIS-06").sessionAttr(AuthHelper.SESSION_KEY, user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dạng câu")))
                .andExpect(content().string(containsString("1 đoạn nghe, nhiều câu")))
                .andExpect(content().string(containsString("Mỗi câu con vẫn tính là một câu")))
                .andExpect(content().string(not(containsString("II-N6"))))
                .andExpect(content().string(not(containsString("Vùng cấp"))));
        mvc.perform(get("/content/question-types").sessionAttr(AuthHelper.SESSION_KEY, user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TPL1-LIS-01")))
                .andExpect(content().string(containsString("TPL2-WRI-04")))
                .andExpect(content().string(containsString("1 đoạn nghe, nhiều câu")))
                .andExpect(content().string(not(containsString("I-N1"))))
                .andExpect(content().string(not(containsString("Vùng cấp"))))
                .andExpect(content().string(not(containsString("1–4"))));
        mvc.perform(get("/content/questions").sessionAttr(AuthHelper.SESSION_KEY, user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Viết")))
                .andExpect(content().string(containsString("TPL1-LIS-01")))
                .andExpect(content().string(not(containsString("Vùng cấp"))))
                .andExpect(content().string(not(containsString("Độ khó"))));
    }

    @Test
    void sourcePageOpensABlankPaperReadyToType() throws Exception {
        ExamSource src = importService.createSource(new ExamImportService.SourceForm(
                "Trang nhập", Track.TOPIK_I, 2026, 8, null, false, "Đề mẫu nội bộ", null), CM);
        UserDTO user = new UserDTO(2L, "content_manager", "content@hottopik.com", "", "Lê Thu Hà",
                Actor.ROLE_CONTENT, "Content Manager", "ACTIVE", "", "", "TOPIK_6", "TOPIK_5", false, null);
        mvc.perform(get("/content/sources/" + src.getId()).sessionAttr(AuthHelper.SESSION_KEY, user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nhập đề")))
                .andExpect(content().string(containsString("Lưu câu này")))
                .andExpect(content().string(containsString("name=\"prompt\"")))
                .andExpect(content().string(containsString("Dạng câu")))
                .andExpect(content().string(containsString("Thêm câu")))
                .andExpect(content().string(containsString("TPL1-LIS-01")))
                .andExpect(content().string(containsString("Nghe câu hỏi, chọn câu trả lời")))
                .andExpect(content().string(containsString("TPL1-LIS-08")))
                .andExpect(content().string(not(containsString("câu 1-4"))))
                .andExpect(content().string(not(containsString("I-N1"))))
                .andExpect(content().string(not(containsString("Đổ nhanh từ Excel"))));
        mvc.perform(get("/content/sources/" + src.getId() + "?skill=READING").sessionAttr(AuthHelper.SESSION_KEY, user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TPL1-REA-01")))
                .andExpect(content().string(containsString("Xác định nội dung câu hoặc văn bản ngắn")))
                .andExpect(content().string(not(containsString("I-D1"))));
        mvc.perform(get("/content/sources/" + src.getId() + "?skill=LISTENING&type=TPL1-LIS-08&nos=8")
                        .sessionAttr(AuthHelper.SESSION_KEY, user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chọn thêm 1 ô")))
                .andExpect(content().string(not(containsString("name=\"prompt\""))));
        mvc.perform(get("/content/sources/" + src.getId() + "?skill=LISTENING&type=TPL1-LIS-08&nos=8&nos=19")
                        .sessionAttr(AuthHelper.SESSION_KEY, user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"prompt\"")))
                .andExpect(content().string(containsString("value=\"8\"")))
                .andExpect(content().string(containsString("value=\"19\"")));
    }

    @Test
    void manualCardKeepsOneAudioForEveryChildQuestion() throws Exception {
        ExamSource src = importService.createSource(new ExamImportService.SourceForm(
                "Nhập theo khuôn", Track.TOPIK_II, 2026, 7, 104, false, "Đề mẫu nội bộ", null), CM);
        importService.startManual(src.getId(), CM);
        importService.uploadMedia(src.getId(), List.of(
                new MockMultipartFile("files", "clip.mp3", "audio/mpeg", SampleExamFactory.fakeMp3()),
                new MockMultipartFile("files", "mot.mp3", "audio/mpeg", SampleExamFactory.fakeMp3())), CM);
        String groupId = importService.saveCard(src.getId(), listeningCard(null, "TPL2-LIS-06", "clip.mp3",
                "남자: 회의 자료 복사했어요.",
                List.of("남자는 무엇을 합니까?", "여자는 어디에 갑니까?"),
                List.of("회의", "회사"), List.of("복사", "은행"), List.of("예약", "학교"), List.of("검색", "병원"),
                List.of("2", "1"),
                List.of("Người đàn ông đang photocopy.", "Người phụ nữ đi làm.")), CM);
        importService.saveCard(src.getId(), listeningCard(null, "TPL2-LIS-02", "mot.mp3", null,
                List.of("다음에 할 말은?"),
                List.of("네"), List.of("아니요"), List.of("잠깐만요"), List.of("감사합니다"),
                List.of("1"), List.of("Chọn lời đáp.")), CM);
        importService.saveCard(src.getId(), listeningCard(groupId, "TPL2-LIS-06", "clip.mp3",
                "남자: 회의 자료 복사했어요.",
                List.of("남자는 지금 무엇을 합니까?", "여자는 어디에 갑니까?"),
                List.of("회의", "회사"), List.of("복사", "은행"), List.of("예약", "학교"), List.of("검색", "병원"),
                List.of("2", "1"),
                List.of("Người đàn ông đang photocopy.", "Người phụ nữ đi làm.")), CM);

        List<ExamImportService.CardView> cards = importService.cards(src.getId());
        assertEquals(2, cards.size());
        assertEquals("TPL2-LIS-02", cards.get(1).templateCode());
        assertEquals("남자는 지금 무엇을 합니까?", cards.get(0).asks().get(0).prompt());
        assertEquals("여자는 어디에 갑니까?", cards.get(0).asks().get(1).prompt());
        assertEquals("clip.mp3", cards.get(0).audio());
        assertEquals(0, importService.latestJob(src.getId()).orElseThrow().getErrorCount(),
                () -> importService.issues(importService.latestJob(src.getId()).orElseThrow()).toString());

        ExamImportService.ActivationResult result = importService.activate(src.getId(), CM);
        assertNotNull(result.examId());
        assertEquals(src.getName(), composer.get(result.examId()).getName());
        assertTrue(composer.frame(result.examId()).enteredPaper());
        assertTrue(composer.frame(result.examId()).check().isOk());
        assertEquals(3, result.questionCount());
        List<Question> saved = questions.findBySourceIdOrderBySkillAscOriginalNoAsc(src.getId());
        assertEquals(3, saved.size());
        assertEquals("TPL2-LIS-06", saved.get(0).getTemplateCode());
        assertEquals("TPL2-LIS-06", saved.get(1).getTemplateCode());
        assertEquals("II-N6", saved.get(0).getTypeCode());
        assertEquals(1, saved.get(0).getOriginalNo());
        assertEquals(2, saved.get(1).getOriginalNo());
        assertEquals(saved.get(0).getGroup().getId(), saved.get(1).getGroup().getId());
        assertEquals("clip.mp3", saved.get(0).getGroup().getAudioFile());
        assertEquals("mot.mp3", saved.get(2).getGroup().getAudioFile());
        assertNotEquals(saved.get(0).getGroup().getId(), saved.get(2).getGroup().getId());

        List<ExamImportService.LearnerBai> bais = importService.learnerBais(src.getId());
        assertEquals(2, bais.size());
        assertEquals(2, bais.get(0).asks().size());
        assertEquals("clip.mp3", bais.get(0).audio());
        assertEquals("남자: 회의 자료 복사했어요.", bais.get(0).script());
        assertEquals("mot.mp3", bais.get(1).audio());
    }

    @Test
    void sharedListeningCellsStayOneClip() throws Exception {
        ExamSource src = importService.createSource(new ExamImportService.SourceForm(
                "Câu dùng chung", Track.TOPIK_II, 2026, 8, 104, false, "Đề mẫu nội bộ", null), CM);
        importService.startManual(src.getId(), CM);
        importService.uploadMedia(src.getId(), List.of(
                new MockMultipartFile("files", "bai21.mp3", "audio/mpeg", SampleExamFactory.fakeMp3())), CM);
        importService.saveCard(src.getId(), new ExamImportService.CardForm(null, null, "bai21.mp3", null, null,
                "남자: 회의 자료 복사했어요.",
                List.of("남자는 무엇을 합니까?", "여자는 어디에 갑니까?"),
                List.of("회의", "회사"), List.of("복사", "은행"), List.of("예약", "학교"), List.of("검색", "병원"),
                List.of("2", "1"),
                List.of("Đang photocopy.", "Đi làm."),
                null, null, null, null, null, "II-N6", List.of(21, 22)), CM);
        assertEquals(0, importService.latestJob(src.getId()).orElseThrow().getErrorCount(),
                () -> importService.issues(importService.latestJob(src.getId()).orElseThrow()).toString());
        ExamImportService.ActivationResult result = importService.activate(src.getId(), CM);
        assertNotNull(result.examId());
        assertTrue(composer.frame(result.examId()).check().isOk());
        composer.submitForPublish(result.examId(), CM);
        assertEquals(ExamStatus.PENDING_APPROVAL, composer.get(result.examId()).getStatus());
        List<Question> saved = questions.findBySourceIdOrderBySkillAscOriginalNoAsc(src.getId());
        assertEquals(2, saved.size());
        assertEquals(21, saved.get(0).getOriginalNo());
        assertEquals(22, saved.get(1).getOriginalNo());
        assertEquals("II-N6", saved.get(0).getTypeCode());
        assertEquals("TPL2-LIS-06", saved.get(0).getTemplateCode());
        assertEquals(saved.get(0).getGroup().getId(), saved.get(1).getGroup().getId());
        assertEquals("bai21.mp3", saved.get(0).getGroup().getAudioFile());
    }

    @Test
    void sharedTemplateAcceptsAnyNumbersInTheSkill() throws Exception {
        ExamSource src = importService.createSource(new ExamImportService.SourceForm(
                "Mẫu đã chốt", Track.TOPIK_I, 2026, 9, 70, false, "Đề mẫu nội bộ", null), CM);
        importService.startManual(src.getId(), CM);
        importService.uploadMedia(src.getId(), List.of(
                new MockMultipartFile("files", "doan.mp3", "audio/mpeg", SampleExamFactory.fakeMp3())), CM);
        importService.saveCard(src.getId(), new ExamImportService.CardForm(null, "TPL1-LIS-08", "doan.mp3", null, null,
                "남자: 회의 자료 복사했어요.",
                List.of("남자는 무엇을 합니까?", "여자는 어디에 갑니까?"),
                List.of("회의", "회사"), List.of("복사", "은행"), List.of("예약", "학교"), List.of("검색", "병원"),
                List.of("2", "1"),
                List.of("Đang photocopy.", "Đi làm."),
                null, null, null, null, null, null, List.of(8, 19)), CM);
        importService.saveCard(src.getId(), new ExamImportService.CardForm(null, "TPL1-LIS-01", "doan.mp3", null, null, null,
                List.of("지금 몇 시예요?"),
                List.of("세 시예요"), List.of("학교예요"), List.of("학생이에요"), List.of("물이에요"),
                List.of("1"), List.of("Chọn giờ."),
                null, null, null, null, null, null, List.of(3)), CM);
        assertEquals(0, importService.latestJob(src.getId()).orElseThrow().getErrorCount(),
                () -> importService.issues(importService.latestJob(src.getId()).orElseThrow()).toString());
        ExamImportService.ActivationResult result = importService.activate(src.getId(), CM);
        assertNotNull(result.examId());
        UserDTO user = new UserDTO(2L, "content_manager", "content@hottopik.com", "", "Lê Thu Hà",
                Actor.ROLE_CONTENT, "Content Manager", "ACTIVE", "", "", "TOPIK_6", "TOPIK_5", false, null);
        mvc.perform(get("/content/questions?type=TPL1-LIS-08").sessionAttr(AuthHelper.SESSION_KEY, user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TPL1-LIS-08")));
        mvc.perform(get("/content/exams/" + result.examId()).sessionAttr(AuthHelper.SESSION_KEY, user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TPL1-LIS-08")))
                .andExpect(content().string(containsString("Gửi duyệt phát hành")))
                .andExpect(content().string(not(containsString("vùng cấp"))));
        List<Question> saved = questions.findBySourceIdOrderBySkillAscOriginalNoAsc(src.getId());
        assertEquals(3, saved.size());
        assertEquals(3, saved.get(0).getOriginalNo());
        assertEquals("TPL1-LIS-01", saved.get(0).getTemplateCode());
        assertEquals(8, saved.get(1).getOriginalNo());
        assertEquals(19, saved.get(2).getOriginalNo());
        assertEquals("TPL1-LIS-08", saved.get(1).getTemplateCode());
        assertEquals(saved.get(1).getGroup().getId(), saved.get(2).getGroup().getId());
        assertNotEquals(saved.get(0).getGroup().getId(), saved.get(1).getGroup().getId());
    }

    private static ExamImportService.CardForm listeningCard(String cardId, String template, String audio, String script,
                                                            List<String> prompts, List<String> opt1, List<String> opt2,
                                                            List<String> opt3, List<String> opt4, List<String> answers,
                                                            List<String> explanations) {
        return new ExamImportService.CardForm(cardId, template, audio, null, null, script,
                prompts, opt1, opt2, opt3, opt4, answers, explanations,
                null, null, null, null, null, null, null);
    }

    private static List<MultipartFile> mediaFiles(SampleExamFactory.Sample s) {
        List<MultipartFile> files = new ArrayList<>();
        for (String a : s.audioFiles()) files.add(new MockMultipartFile("files", a, "audio/mpeg", SampleExamFactory.fakeMp3()));
        for (String i : s.imageFiles()) files.add(new MockMultipartFile("files", i, "image/png", SampleExamFactory.fakePng()));
        return files;
    }

    @Test
    void rejectReturnsExamToDraft() throws Exception {
        ExamImportService.ActivationResult r = importFull(Track.TOPIK_I, 2);
        assertEquals(70, r.questionCount());
        composer.submitForPublish(r.examId(), CM);
        Long req = composer.get(r.examId()).getApprovalRequestId();
        approvals.decideRequest(req, false, "Thiếu giải thích", "Admin");
        assertEquals(ExamStatus.DRAFT, composer.get(r.examId()).getStatus());
        assertTrue(composer.children(r.examId()).isEmpty());
    }

    @Test
    void pendingExamWithLostApprovalRequestIsReleasedOnStartup() throws Exception {
        ExamImportService.ActivationResult r = importFull(Track.TOPIK_I, 5);
        composer.submitForPublish(r.examId(), CM);
        composer.get(r.examId()).setApprovalRequestId(999_999L);
        composer.releaseOrphanedApprovals();
        Exam e = composer.get(r.examId());
        assertEquals(ExamStatus.DRAFT, e.getStatus());
        assertNull(e.getPendingAction());
    }

    @Test
    void composeFromBankKeepsGroupsWholeAndBlocksHiddenQuestions() throws Exception {
        importFull(Track.TOPIK_I, 3);
        Exam draft = composer.createDraft(Track.TOPIK_I, CM);
        assertEquals(70, composer.autofillAll(draft.getId(), CM));
        ExamComposerService.ExamFrame frame = composer.frame(draft.getId());
        assertTrue(frame.check().isOk());

        // Câu 25–26 (I-N8) phải đến từ cùng một nhóm câu.
        ExamComposerService.TypeRow n8 = frame.rows().stream().filter(t -> t.type().code().equals("I-N8")).findFirst().orElseThrow();
        for (ExamComposerService.BlockView b : n8.blocks()) {
            Set<Long> groups = new HashSet<>();
            b.slots().forEach(s -> groups.add(s.question().getGroup().getId()));
            assertEquals(1, groups.size());
        }

        Question q = frame.rows().get(0).blocks().get(0).slots().get(0).question();
        bank.setHidden(q.getId(), true, "Đáp án sai chính tả", CM);
        assertFalse(composer.frame(draft.getId()).check().isOk());
        assertThrows(ExamDomainException.class, () -> composer.submitForPublish(draft.getId(), CM));
    }

    @Test
    void bulkHideOver20NeedsApproval() throws Exception {
        importFull(Track.TOPIK_I, 4);
        List<Long> ids = questions.findAll().stream().limit(25).map(Question::getId).toList();
        QuestionBankService.BulkResult r = bank.bulk(ids, "hide", "Rà soát bản quyền", CM);
        assertNotNull(r.approvalRequestId());
        assertTrue(questions.findAllById(ids).stream().allMatch(q -> q.getStatus() == QuestionStatus.ACTIVE));
        approvals.decideRequest(r.approvalRequestId(), true, "OK", "Admin");
        assertTrue(questions.findAllById(ids).stream().allMatch(q -> q.getStatus() == QuestionStatus.HIDDEN));
    }

    @Test
    void adminIsReadOnly() {
        assertThrows(ExamDomainException.class, () -> composer.createDraft(Track.TOPIK_II, ADMIN));
        assertThrows(ExamDomainException.class, () -> importService.createSource(new ExamImportService.SourceForm(
                "x", Track.TOPIK_I, null, null, null, false, "c", null), ADMIN));
    }

    @Test
    void contentManagerSeesPublishedRoadmapWithoutWriting() throws Exception {
        UserDTO user = new UserDTO(2L, "content_manager", "content@hottopik.com", "", "Lê Thu Hà",
                Actor.ROLE_CONTENT, "Content Manager", "ACTIVE", "", "", "TOPIK_6", "TOPIK_5", false, null);
        mvc.perform(get("/content/roadmap").sessionAttr(AuthHelper.SESSION_KEY, user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("v1")))
                .andExpect(content().string(containsString("Đã phát hành")));

        ProgramVersion published = roadmap.published();
        assertNotNull(published);
        List<String> codes = roadmap.itemsOf(published.getId()).stream().map(i -> i.getTemplate().getCode()).toList();
        assertTrue(codes.contains("TPL1-LIS-01"));
        assertTrue(codes.contains("TPL2-REA-01"));
        assertFalse(codes.contains("TPL2-WRI-01"));
        assertTrue(codes.indexOf("TPL1-LIS-01") < codes.indexOf("TPL1-REA-01"));

        mvc.perform(get("/content/roadmap/" + published.getId()).sessionAttr(AuthHelper.SESSION_KEY, user))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lịch mẫu 21 ngày đầu")))
                .andExpect(content().string(containsString("TPL1-LIS-01")))
                .andExpect(content().string(not(containsString("TPL2-WRI-01"))));
    }
}
