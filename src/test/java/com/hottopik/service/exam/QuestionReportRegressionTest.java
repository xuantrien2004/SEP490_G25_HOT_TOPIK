package com.hottopik.service.exam;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.User;
import com.hottopik.repository.UserRepository;
import com.hottopik.repository.exam.QuestionRepository;
import com.hottopik.repository.exam.WritingPromptRepository;
import com.hottopik.service.QuestionReportService;
import com.hottopik.service.ReportAttemptService;
import com.hottopik.entity.exam.PracticeAttempt;
import com.hottopik.entity.exam.ExamAttempt;
import com.hottopik.repository.exam.PracticeAttemptRepository;
import com.hottopik.repository.exam.ExamAttemptRepository;
import com.hottopik.repository.exam.ExamRepository;
import com.hottopik.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuestionReportRegressionTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired QuestionRepository questions;
    @Autowired WritingPromptRepository writing;
    @Autowired QuestionReportService reports;
    @Autowired ReportAttemptService reportAttempts;
    @Autowired PracticeAttemptRepository practices;
    @Autowired ExamAttemptRepository attempts;
    @Autowired ExamRepository exams;

    private UserDTO user(long id, String role) {
        UserDTO u = new UserDTO();
        u.setId(id);
        u.setFullName("Người kiểm thử");
        u.setEmail("report-" + id + "@example.com");
        u.setRole(role);
        return u;
    }

    private long submit(UserDTO user, long questionId, String kind) throws Exception {
        var response = mvc.perform(post("/api/question-reports").sessionAttr(AuthHelper.SESSION_KEY, user)
                        .param("questionId", "" + questionId).param("questionKind", kind)
                        .param("questionSummary", "Nội dung giả không được tin cậy")
                        .param("questionCode", "Sai mã").param("questionNo", "999")
                        .param("issueType", "Nội dung câu hỏi").param("details", "Đề nghị kiểm tra lại đoạn văn và đáp án."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true)).andReturn();
        return new ObjectMapper().readTree(response.getResponse().getContentAsString()).path("reportId").asLong();
    }

    @Test
    void reportsLoadCanonicalQuestionsAndNotifyOnlyTheOwnerOnce() throws Exception {
        User owner = users.save(User.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("test-only").displayName("Report owner").build());
        UserDTO learner = user(owner.getId(), "STUDENT");
        UserDTO cm = user(99990, Actor.ROLE_CONTENT);
        var q = questions.findAll().stream().filter(it -> it.getPromptKo() != null).findFirst().orElseThrow();
        long id = submit(learner, q.getId(), "MC");
        var report = reports.getReportById(id).orElseThrow();
        assertEquals(q.getCode(), report.getQuestionCode());
        assertEquals(q.getOriginalNo(), report.getQuestionNo());
        assertEquals(q.getPromptKo(), reports.reportedQuestion(report).prompt());
        mvc.perform(get("/content/reports/{id}/edit", id).sessionAttr(AuthHelper.SESSION_KEY, cm))
                .andExpect(redirectedUrl("/content/questions?openQuestionId=" + q.getId() + "&reportId=" + id));
        mvc.perform(get("/content/reports/{id}/edit", id).sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(redirectedUrl("/login?unauthorized=1"));
        mvc.perform(get("/content/questions").param("openQuestionId", "" + q.getId()).param("reportId", "" + id)
                        .sessionAttr(AuthHelper.SESSION_KEY, cm)).andExpect(status().isOk());
        mvc.perform(get("/content").param("tab", "reports").sessionAttr(AuthHelper.SESSION_KEY, cm))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Xử lý Báo lỗi")))
                .andExpect(content().string(not(containsString("Xß╗"))));
        mvc.perform(get("/content/reports/{id}/question", id).sessionAttr(AuthHelper.SESSION_KEY, cm))
                .andExpect(status().isOk()).andExpect(content().string(containsString(q.getPromptKo())));
        mvc.perform(post("/content/report/resolve").sessionAttr(AuthHelper.SESSION_KEY, cm)
                        .param("reportId", "" + id).param("note", "Đã sửa phần giải thích tiếng Việt."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertFalse(reports.resolveReport(id, "Lặp lại", "CM"));
        assertFalse(reports.rejectReport(id, "Lặp lại", "CM"));
        assertEquals(5, users.findById(owner.getId()).orElseThrow().getCoinBalance());
        assertEquals(1, reports.getUserNotifications(owner.getId()).size());
        mvc.perform(get("/api/notifications/summary").sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.notifications[0].link").value("/bao-loi/" + id));
        for (String url : new String[]{"/thong-bao", "/notifications"}) {
            mvc.perform(get(url).sessionAttr(AuthHelper.SESSION_KEY, learner)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("Báo lỗi câu hỏi đã được sửa")))
                    .andExpect(content().string(not(containsString("class=\"ssak-nav-item active\""))))
                    .andExpect(content().string(containsString("aria-current=\"page\"")));
        }
        mvc.perform(get("/bao-loi/{id}", id).sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk()).andExpect(content().string(containsString(q.getPromptKo())))
                .andExpect(content().string(containsString("Đã sửa phần giải thích tiếng Việt.")));
        assertEquals(0, reports.getUnreadNotificationCount(owner.getId()));
        mvc.perform(get("/bao-loi/{id}", id).sessionAttr(AuthHelper.SESSION_KEY, user(99991, "STUDENT")))
                .andExpect(status().isNotFound());

        // Question and WritingPrompt have separate ID sequences: the same number must resolve by kind.
        var w = writing.findAll().stream().filter(it -> questions.existsById(it.getId())).findFirst().orElseThrow();
        long writingReport = submit(learner, w.getId(), "WRITING");
        mvc.perform(get("/content/reports/{id}/edit", writingReport).sessionAttr(AuthHelper.SESSION_KEY, cm))
                .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"promptKo\"")))
                .andExpect(model().attribute("writing", org.hamcrest.Matchers.hasProperty("promptKo", org.hamcrest.Matchers.equalTo(w.getPromptKo()))));
        assertEquals(w.getPromptKo(), reports.reportedQuestion(reports.getReportById(writingReport).orElseThrow()).prompt());
        mvc.perform(post("/content/report/reject").sessionAttr(AuthHelper.SESSION_KEY, cm)
                        .contentType("application/json").content("{\"reportId\":" + writingReport + ",\"reason\":\"Câu hỏi đúng, vui lòng xem giải thích.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertFalse(reports.rejectReport(writingReport, "Lặp lại", "CM"));
        assertEquals(2, reports.getUserNotifications(owner.getId()).size());
        assertEquals(5, users.findById(owner.getId()).orElseThrow().getCoinBalance());
        mvc.perform(get("/bao-loi/{id}", writingReport).sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk()).andExpect(content().string(containsString(w.getPromptKo())))
                .andExpect(content().string(containsString("Câu hỏi đúng, vui lòng xem giải thích.")));
        mvc.perform(get("/content/reports/{id}/question", writingReport).sessionAttr(AuthHelper.SESSION_KEY, cm))
                .andExpect(status().isOk()).andExpect(content().string(containsString(w.getPromptKo())));
        var previewOnly = reports.createReport(owner.getId(), learner.getFullName(), learner.getEmail(), null,
                "Mini Test", "Nội dung bài mini cũ không có ID ngân hàng", null, "Mini Test", 1,
                "Nội dung", "Kiểm tra câu hỏi", "MC");
        assertNull(reports.reportedQuestion(previewOnly));
        mvc.perform(get("/bao-loi/{id}", previewOnly.getId()).sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Nội dung bài mini cũ không có ID ngân hàng")));
    }

    @Test
    void writingCorrectionSavesTheReportedPromptAndNotifiesItsOwner() throws Exception {
        User owner = users.save(User.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("test-only").displayName("Writing reporter").build());
        UserDTO learner = user(owner.getId(), "STUDENT");
        UserDTO cm = user(99990, Actor.ROLE_CONTENT);
        var w = writing.findAll().get(0);
        long id = submit(learner, w.getId(), "WRITING");
        String original = w.getPromptKo();
        String modelAnswer = w.getModelAnswer();
        mvc.perform(post("/content/reports/{id}/writing", id).sessionAttr(AuthHelper.SESSION_KEY, cm)
                        .param("promptKo", original).param("modelAnswer", modelAnswer)
                        .param("chartJson", "invalid").param("resolutionNote", "Kiểm tra biểu đồ"))
                .andExpect(redirectedUrl("/content/reports/" + id + "/edit"));
        assertEquals("OPEN", reports.getReportById(id).orElseThrow().getStatus());
        mvc.perform(post("/content/reports/{id}/writing", id).sessionAttr(AuthHelper.SESSION_KEY, cm)
                        .param("promptKo", original).param("modelAnswer", modelAnswer)
                        .param("keys1", w.getKeys1() == null ? "" : w.getKeys1())
                        .param("keys2", w.getKeys2() == null ? "" : w.getKeys2())
                        .param("chartJson", w.getChartJson() == null ? "" : w.getChartJson())
                        .param("rubricNote", "Hướng dẫn chấm đã được sửa")
                        .param("resolutionNote", "Đã sửa hướng dẫn chấm."))
                .andExpect(redirectedUrl("/content?tab=reports"));
        assertEquals("Hướng dẫn chấm đã được sửa", writing.findById(w.getId()).orElseThrow().getRubricNote());
        assertEquals("RESOLVED", reports.getReportById(id).orElseThrow().getStatus());
        assertEquals(1, reports.getUserNotifications(owner.getId()).size());
        mvc.perform(get("/bao-loi/{id}", id).sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Hướng dẫn chấm đã được sửa")));
    }

    @Test
    void resolvedReportsLinkOnlyToTheExactOwnedCompletedAttempt() throws Exception {
        User owner = users.save(User.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("test-only").displayName("History reporter").build());
        UserDTO learner = user(owner.getId(), "STUDENT");
        var q = questions.findAll().get(0);
        PracticeAttempt practice = new PracticeAttempt();
        practice.setUserId(owner.getId());
        practice.setUserEmail(learner.getEmail());
        practice.setTrack(q.getTrack());
        practice.setSkill(q.getSkill());
        practice.setTypeCode(q.getTypeCode());
        practice.setQuestionIds(q.getId().toString());
        practice.setTotal(1);
        practice = practices.saveAndFlush(practice);
        final Long practiceId = practice.getId();
        var submitted = mvc.perform(post("/api/question-reports").sessionAttr(AuthHelper.SESSION_KEY, learner)
                        .param("questionId", q.getId().toString()).param("questionKind", "MC")
                        .param("attemptId", practiceId.toString()).param("attemptKind", "PRACTICE")
                        .param("issueType", "Nội dung").param("details", "Kiểm tra câu đã làm"))
                .andExpect(status().isOk()).andReturn();
        long id = new ObjectMapper().readTree(submitted.getResponse().getContentAsString()).path("reportId").asLong();
        assertNull(reportAttempts.historyLink(reports.getReportById(id).orElseThrow()));
        reports.rejectReport(id, "Đã kiểm tra nội dung", "CM");
        assertNull(reportAttempts.historyLink(reports.getReportById(id).orElseThrow()), "Unfinished attempts have no history result");
        practice.setCompletedAt(java.time.LocalDateTime.now());
        practices.saveAndFlush(practice);
        String expected = "/practice/result/" + practiceId + "#question-" + q.getId();
        assertEquals(expected, reportAttempts.historyLink(reports.getReportById(id).orElseThrow()));
        mvc.perform(get("/bao-loi/{id}", id).sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"" + expected + "\"")));
        mvc.perform(get("/practice/result/{id}", practiceId).sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk()).andExpect(content().string(containsString("id=\"question-" + q.getId() + "\"")));
        mvc.perform(post("/api/question-reports").sessionAttr(AuthHelper.SESSION_KEY, user(99887, "STUDENT"))
                        .param("questionId", q.getId().toString()).param("attemptId", practiceId.toString())
                        .param("attemptKind", "PRACTICE").param("issueType", "Nội dung").param("details", "Không có quyền"))
                .andExpect(status().isBadRequest());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> reportAttempts.validate(owner.getId(), practiceId, "PRACTICE", Long.MAX_VALUE, "MC", null));
        long legacyId = submit(learner, q.getId(), "MC");
        reports.resolveReport(legacyId, "Đã kiểm tra", "CM");
        assertNull(reportAttempts.historyLink(reports.getReportById(legacyId).orElseThrow()), "Never guess an attempt for legacy reports");

        Long examId = exams.findAll().stream().map(e -> exams.findForComposerById(e.getId()).orElseThrow())
                .filter(e -> !e.getItems().isEmpty()).findFirst().orElseThrow().getId();
        Long examQuestionId = exams.findForComposerById(examId).orElseThrow().getItems().get(0).getQuestion().getId();
        ExamAttempt examAttempt = new ExamAttempt();
        examAttempt.setExam(exams.findById(examId).orElseThrow());
        examAttempt.setUserId(owner.getId());
        examAttempt.setCompletedAt(java.time.LocalDateTime.now());
        examAttempt = attempts.saveAndFlush(examAttempt);
        final Long attemptId = examAttempt.getId();
        reportAttempts.validate(owner.getId(), attemptId, "EXAM", examQuestionId, "MC", examId);
        long examReportId = submit(learner, examQuestionId, "MC");
        var examReport = reports.getReportById(examReportId).orElseThrow();
        examReport.setAttemptId(attemptId);
        examReport.setAttemptKind("EXAM");
        examReport.setStatus("RESOLVED");
        assertEquals("/ket-qua/" + attemptId + "#question-" + examQuestionId, reportAttempts.historyLink(examReport));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> reportAttempts.validate(owner.getId() + 1, attemptId, "EXAM", examQuestionId, "MC", examId));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> reportAttempts.validate(owner.getId(), attemptId, "EXAM", examQuestionId, "MC", Long.MAX_VALUE));
    }

    @Test
    void anonymousRequestsCannotReadOrSubmitAnotherLearnersReports() throws Exception {
        mvc.perform(get("/api/notifications/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/thong-bao")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/api/question-reports").param("issueType", "Nội dung").param("details", "Kiểm tra câu hỏi"))
                .andExpect(status().isUnauthorized());
    }
}
