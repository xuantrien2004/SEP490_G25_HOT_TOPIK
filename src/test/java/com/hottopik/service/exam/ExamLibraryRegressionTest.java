package com.hottopik.service.exam;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.ExamAttemptRepository;
import com.hottopik.repository.exam.ExamRepository;
import com.hottopik.service.ApprovalService;
import com.hottopik.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Real service boundaries and detached entities reproduce the supplied template exception.
@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExamLibraryRegressionTest {
    private static final Actor CM = new Actor("Content Manager", Actor.ROLE_CONTENT);
    private static final AtomicInteger VARIANT = new AtomicInteger(2000);
    @Autowired ExamImportService imports;
    @Autowired ExamComposerService composer;
    @Autowired ApprovalService approvals;
    @Autowired ExamRepository exams;
    @Autowired ExamAttemptRepository attempts;
    @Autowired LearnerExamService learners;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;

    private UserDTO user(long id, String role) {
        UserDTO user = new UserDTO();
        user.setId(id);
        user.setEmail("exam-" + id + "@example.com");
        user.setFullName("Người kiểm thử");
        user.setRole(role);
        return user;
    }

    private ExamSource source(Track track) {
        return imports.createSource(new ExamImportService.SourceForm("Đề kiểm tra " + UUID.randomUUID(), track,
                2026, null, null, false, "Đề nội bộ", null), CM);
    }

    private Exam full(Track track) throws Exception {
        ExamSource source = source(track);
        SampleExamFactory.Sample sample = SampleExamFactory.full(track, VARIANT.incrementAndGet());
        List<MultipartFile> media = new ArrayList<>();
        for (String name : sample.audioFiles()) media.add(new MockMultipartFile("files", name, "audio/mpeg", SampleExamFactory.fakeMp3()));
        for (String name : sample.imageFiles()) media.add(new MockMultipartFile("files", name, "image/png", SampleExamFactory.fakePng()));
        imports.uploadMedia(source.getId(), media, CM);
        imports.uploadExcel(source.getId(), new MockMultipartFile("file", "de.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", sample.excel()), CM);
        return composer.get(imports.activate(source.getId(), CM).examId());
    }

    private void publish(Exam exam) {
        composer.submitForPublish(exam.getId(), CM);
        assertTrue(approvals.decideRequest(composer.get(exam.getId()).getApprovalRequestId(), true, "Đồng ý", "Admin"));
    }

    @Test
    void fullAndGeneratedMinisKeepIndependentProgressAndHistory() throws Exception {
        Exam full = full(Track.TOPIK_II);
        publish(full);
        long owner = 819;
        List<Exam> minis = composer.children(full.getId());
        ExamAttempt fullAttempt = learners.start(owner, full.getId(), false);
        learners.save(owner, fullAttempt.getId(), Map.of(), true, false);
        for (Exam mini : minis) {
            var card = learners.library(owner).get("II").get(mini.getKind().name().toLowerCase(Locale.ROOT))
                    .stream().filter(c -> c.id().equals(mini.getId())).findFirst().orElseThrow();
            assertFalse(card.inProgress(), "Mini must not inherit the full mock attempt");
            assertFalse(card.isDone());
        }
        String html = mvc.perform(get("/de-thi").sessionAttr(AuthHelper.SESSION_KEY, user(owner, "STUDENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int dataStart = html.indexOf("const examsData = ") + "const examsData = ".length();
        var browserData = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(html.substring(dataStart, html.indexOf(';', dataStart))).path("II");
        for (Exam child : minis) {
            var browserCard = java.util.stream.StreamSupport.stream(
                            browserData.path(child.getKind().name().toLowerCase(Locale.ROOT)).spliterator(), false)
                    .filter(c -> c.path("id").asLong() == child.getId()).findFirst().orElseThrow();
            assertFalse(browserCard.path("inProgress").asBoolean());
            assertEquals("/exam/" + child.getId() + "/start", browserCard.path("primaryUrl").asText());
        }
        Exam mini = minis.get(0);
        ExamAttempt miniAttempt = learners.start(owner, mini.getId(), false);
        assertNotEquals(fullAttempt.getId(), miniAttempt.getId());
        assertEquals(mini.getId(), miniAttempt.getExam().getId());
        learners.save(owner, miniAttempt.getId(), Map.of(), false, true);
        assertEquals(1, learners.result(owner, miniAttempt.getId()).history().size());
        assertTrue(learners.library(owner).get("II").get("full").stream()
                .filter(c -> c.id().equals(full.getId())).findFirst().orElseThrow().inProgress());
        assertEquals(fullAttempt.getId(), learners.start(owner, full.getId(), false).getId());
        learners.save(owner, fullAttempt.getId(), Map.of(), false, true);
        assertEquals(1, learners.result(owner, fullAttempt.getId()).history().size());
        learners.start(owner, mini.getId(), true);
        assertFalse(learners.library(owner).get("II").get("full").stream()
                .filter(c -> c.id().equals(full.getId())).findFirst().orElseThrow().inProgress());
    }

    @Test
    void fullMockAndItsMinisRenderAfterTheHibernateSessionCloses() throws Exception {
        Exam full = full(Track.TOPIK_II);
        List<Exam> minis = composer.children(full.getId());
        assertEquals(3, minis.size());
        assertTrue(minis.stream().allMatch(e -> e.getStatus() == ExamStatus.DRAFT));
        for (Exam mini : minis) {
            Exam loaded = composer.get(mini.getId());
            assertTrue(MiniTestSelector.compact(loaded));
            if (mini.getKind() != ExamKind.WRITING) assertTrue(mini.questionCount() < 50);
            mvc.perform(get("/content/exams/{id}", mini.getId()).sessionAttr(AuthHelper.SESSION_KEY, user(2, Actor.ROLE_CONTENT)))
                    .andExpect(status().isOk());
        }
        publish(full);
        mvc.perform(get("/content/exams/{id}", full.getId()).sessionAttr(AuthHelper.SESSION_KEY, user(2, Actor.ROLE_CONTENT)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Mini Test tự sinh")));
        assertEquals(3, composer.children(full.getId()).size());
        assertTrue(composer.children(full.getId()).stream().allMatch(e -> e.getStatus() == ExamStatus.PUBLISHED));
    }

    @Test
    void readingOnlyManualPaperCreatesFourteenQuestionsCoveringAllTenTypesAndBackfillsOnce() {
        ExamSource source = source(Track.TOPIK_I);
        imports.startManual(source.getId(), CM);
        for (int no = 31; no <= 70; no++) {
            imports.saveCard(source.getId(), new ExamImportService.CardForm(null, "TPL1-REA-01",
                    null, null, "지문 " + source.getId() + " " + no, null,
                    List.of("질문 " + no), List.of("가"), List.of("나"), List.of("다"), List.of("라"),
                    List.of("2"), List.of("Giải thích " + no), null, null, null, null, null, null, List.of(no)), CM);
        }
        Exam full = composer.get(imports.activate(source.getId(), CM).examId());
        Exam mini = composer.children(full.getId()).get(0);
        assertEquals(ExamKind.READING, mini.getKind());
        assertEquals(14, mini.questionCount());
        assertEquals(21, mini.totalMinutes());
        assertEquals(10, composer.get(mini.getId()).getItems().stream().map(ExamItem::getTypeCode).distinct().count());
        assertTrue(learners.library(810L).get("I").get("reading").stream().noneMatch(c -> c.id().equals(mini.getId())));
        exams.deleteById(mini.getId()); // Reproduce an older activated source without a generated child.
        composer.synchronizeExistingMinis();
        Exam recovered = composer.children(full.getId()).get(0);
        composer.synchronizeExistingMinis();
        assertEquals(List.of(recovered.getId()), composer.children(full.getId()).stream().map(Exam::getId).toList());
        publish(full);
        assertTrue(learners.library(810L).get("I").get("reading").stream().anyMatch(c -> c.id().equals(recovered.getId())));
    }

    @Test
    void learnerCanStartSaveResumeSubmitReviewAndRetryWithPrivatePersistedHistory() throws Exception {
        Exam full = full(Track.TOPIK_I);
        publish(full);
        long owner = 811;
        UserDTO learner = user(owner, "STUDENT");
        mvc.perform(post("/exam/{id}/start", full.getId())).andExpect(redirectedUrl("/login"));
        String redirect = mvc.perform(post("/exam/{id}/start", full.getId()).sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        long id = Long.parseLong(redirect.substring(redirect.lastIndexOf('/') + 1));
        LearnerExamService.Paper play = learners.play(owner, id);
        assertTrue(play.questions().stream().allMatch(q -> q.answer() == null && q.explanation() == null && q.script() == null));
        mvc.perform(get("/lam-bai/{id}", id).sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("Đáp án dựa vào chi tiết"))));
        ExamItem first = full.getItems().get(0);
        String key = "q" + first.getId();
        String correct = Integer.toString(first.getQuestion().getAnswerIndex());
        mvc.perform(post("/exam/attempt/{id}/save", id).sessionAttr(AuthHelper.SESSION_KEY, learner)
                        .param(key, correct).param("q999999", "1").param("score", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(false));
        assertFalse(attempts.findById(id).orElseThrow().getAnswersJson().contains("999"));
        mvc.perform(post("/exam/attempt/{id}/pause", id).sessionAttr(AuthHelper.SESSION_KEY, learner).param(key, correct))
                .andExpect(redirectedUrl("/exam?track=I&kind=full"));
        assertNull(attempts.findById(id).orElseThrow().getLastActiveAt());
        assertTrue(learners.library(owner).get("I").get("full").stream().filter(c -> c.id().equals(full.getId())).findFirst().orElseThrow().inProgress());
        assertEquals(id, learners.start(owner, full.getId(), false).getId());
        assertEquals(correct, learners.play(owner, id).questions().stream().filter(q -> q.key().equals(key)).findFirst().orElseThrow().selected());
        Map<String, String> answers = full.getItems().stream().collect(Collectors.toMap(i -> "q" + i.getId(), i -> Integer.toString(i.getQuestion().getAnswerIndex())));
        answers.put(key, correct.equals("1") ? "2" : "1");
        learners.save(owner, id, answers, false, true);
        learners.save(owner, id, Map.of(key, correct), false, true); // Repeat submission must not change a result.
        assertEquals(full.getItems().size() - 1, learners.result(owner, id).attempt().getCorrect());
        mvc.perform(get("/exam").sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"isDone\":true")));
        mvc.perform(get("/ket-qua/{id}", id).sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Đáp án dựa vào chi tiết")))
                .andExpect(content().string(containsString("Bạn chọn"))).andExpect(content().string(containsString("Lịch sử làm đề này")));
        mvc.perform(get("/ket-qua/{id}", id).sessionAttr(AuthHelper.SESSION_KEY, user(812, "STUDENT"))).andExpect(status().isNotFound());
        mvc.perform(post("/exam/attempt/{id}/save", id).param(key, "1")).andExpect(status().isUnauthorized());
        ExamAttempt retry = learners.start(owner, full.getId(), true);
        assertNotEquals(id, retry.getId());
        assertEquals(play.questions().stream().map(LearnerExamService.QuestionView::key).toList(),
                learners.play(owner, retry.getId()).questions().stream().map(LearnerExamService.QuestionView::key).toList());
        assertEquals(1, learners.result(owner, id).history().size());
        mvc.perform(get("/exam").sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("ATT-MOCK"))))
                .andExpect(content().string(containsString("Hiện chưa có đề")));
    }

    @Test
    void writingChartsAndAnswersPersistWithoutFakeEssayScoresAndTimersCannotBeExtended() throws Exception {
        Exam full = full(Track.TOPIK_II);
        publish(full);
        long owner = 813;
        Exam writing = composer.children(full.getId()).stream().filter(e -> e.getKind() == ExamKind.WRITING).findFirst().orElseThrow();
        ExamAttempt attempt = learners.start(owner, writing.getId(), false);
        LearnerExamService.Paper paper = learners.play(owner, attempt.getId());
        assertEquals(4, paper.writing().size());
        assertFalse(paper.writing().get(2).chart().isEmpty());
        assertTrue(paper.writing().stream().allMatch(q -> q.modelAnswer() == null));
        mvc.perform(get("/lam-bai/{id}", attempt.getId()).sessionAttr(AuthHelper.SESSION_KEY, user(owner, "STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("2015")))
                .andExpect(content().string(not(containsString("조사 결과 1인 가구 비율"))));
        String key = paper.writing().get(0).key();
        String text = "㉠: 신청하세요\n㉡: 감사합니다";
        learners.save(owner, attempt.getId(), Map.of(key, text), true, false);
        assertEquals(text, learners.play(owner, attempt.getId()).writing().get(0).selected());
        ExamAttempt running = attempts.findById(attempt.getId()).orElseThrow();
        running.setLastActiveAt(LocalDateTime.now().minusSeconds(30));
        running.setRemainingSeconds(20);
        attempts.saveAndFlush(running);
        learners.save(owner, attempt.getId(), Map.of(key, "Đổi sau khi hết giờ"), false, true);
        LearnerExamService.Paper result = learners.result(owner, attempt.getId());
        assertEquals(0, result.attempt().getRemainingSeconds());
        assertEquals(text, result.writing().get(0).selected());
        assertNotNull(result.writing().get(0).modelAnswer());
        mvc.perform(get("/ket-qua/{id}", attempt.getId()).sessionAttr(AuthHelper.SESSION_KEY, user(owner, "STUDENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("phần tự luận chưa được tính điểm")));
        mvc.perform(get("/exam/media/{source}/{filename}", full.getSource().getId(), "v" + VARIANT.get() + "_L1.mp3")
                        .sessionAttr(AuthHelper.SESSION_KEY, user(owner, "STUDENT"))).andExpect(status().isOk());
    }

    @Test
    void oversizedLegacyMiniIsReducedOnceAndAttemptedMiniKeepsItsQuestionSlots() throws Exception {
        Exam full = full(Track.TOPIK_I);
        publish(full);
        Long miniId = composer.children(full.getId()).stream().filter(e -> e.getKind() == ExamKind.READING)
                .findFirst().orElseThrow().getId();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Exam legacy = composer.get(miniId);
            legacy.getItems().clear();
            exams.flush();
            for (ExamItem item : composer.get(full.getId()).getItems()) {
                if (item.getSection() == Skill.READING) legacy.getItems().add(new ExamItem(legacy,
                        item.getSection(), item.getNo(), item.getTypeCode(), item.getQuestion()));
            }
            legacy.setReadingMinutes(60);
        });
        assertEquals(40, composer.get(miniId).questionCount());
        composer.synchronizeExistingMinis();
        assertEquals(14, composer.get(miniId).questionCount());
        ExamAttempt attempt = learners.start(814L, miniId, false);
        List<String> before = learners.play(814L, attempt.getId()).questions().stream().map(LearnerExamService.QuestionView::key).toList();
        composer.synchronizeExistingMinis();
        assertEquals(before, learners.play(814L, attempt.getId()).questions().stream().map(LearnerExamService.QuestionView::key).toList());
    }

    @Test
    void deletingADraftAlsoRemovesItsGeneratedDraftMinisAndKeepsTheBank() throws Exception {
        Exam full = full(Track.TOPIK_I);
        Long sourceId = full.getSource().getId();
        List<Long> children = composer.children(full.getId()).stream().map(Exam::getId).toList();
        assertEquals(2, children.size());
        composer.deleteDraft(full.getId(), CM);
        assertFalse(exams.existsById(full.getId()));
        assertTrue(children.stream().noneMatch(exams::existsById));
        assertEquals(70, imports.questionsOf(sourceId).size());
    }
}
