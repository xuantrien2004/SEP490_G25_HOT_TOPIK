package com.hottopik.service;

import com.hottopik.entity.QuestionReport;
import com.hottopik.entity.UserNotification;
import com.hottopik.repository.QuestionReportRepository;
import com.hottopik.repository.UserNotificationRepository;
import com.hottopik.repository.UserRepository;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.QuestionRepository;
import com.hottopik.repository.exam.WritingPromptRepository;
import com.hottopik.repository.exam.ExamRepository;
import com.hottopik.service.exam.LearnerExamService.Choice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionReportService {

    private final QuestionReportRepository reportRepository;
    private final UserNotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questions;
    private final WritingPromptRepository writingPrompts;
    private final ExamRepository exams;

    public record ReportQuestion(String prompt, String passage, String script, String audio, String image,
                                 List<Choice> options, Integer answer, String explanation,
                                 String modelAnswer, String rubric, JsonNode chart, Long sourceId) {}

    @Transactional
    public QuestionReport createReport(Long userId, String reporterName, String reporterEmail,
                                       Long questionId, String questionCode, String questionSummary,
                                       Long examId, String examTitle, Integer questionNo,
                                       String issueType, String details, String questionKind) {
        boolean writing = "WRITING".equals(questionKind);
        if (questionKind != null && !"MC".equals(questionKind) && !writing) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại câu hỏi không hợp lệ.");
        }
        Question q = writing || questionId == null ? null : questions.findById(questionId).orElse(null);
        WritingPrompt w = !writing || questionId == null ? null : writingPrompts.findById(questionId).orElse(null);
        if (q == null && w == null && questionId != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy câu hỏi được báo lỗi.");
        if (questionId != null) {
            questionCode = writing ? w.getCode() : q.getCode();
            questionSummary = writing ? w.getPromptKo() : q.getPromptKo();
            questionNo = writing ? w.getNo() : q.getOriginalNo();
        } else {
            // The older demo mini-test has no bank ID; retain its preview without linking another row.
            if (questionSummary == null || questionSummary.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không có nội dung câu hỏi được báo lỗi.");
            examId = null;
        }
        if (examId != null) {
            Exam exam = exams.findForComposerById(examId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy đề thi."));
            boolean contains = writing ? exam.writingPrompts().stream().anyMatch(p -> p.getId().equals(questionId))
                    : exam.getItems().stream().anyMatch(i -> i.getQuestion().getId().equals(questionId));
            if (!contains) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câu hỏi không thuộc đề thi này.");
            examTitle = exam.getName();
        } else if (questionId != null) {
            ExamSource source = writing ? w.getSource() : q.getSource();
            examTitle = source == null ? null : source.getName();
        }
        QuestionReport report = new QuestionReport();
        report.setQuestionKind(questionId == null ? null : writing ? "WRITING" : "MC");
        report.setUserId(Objects.requireNonNull(userId));
        report.setReporterName(reporterName != null ? reporterName : "Học viên");
        report.setReporterEmail(reporterEmail != null ? reporterEmail : "learner@hottopik.com");
        report.setQuestionId(questionId);
        report.setQuestionCode(questionCode != null ? questionCode : (questionNo != null ? "Câu " + questionNo : "Q-" + System.currentTimeMillis() % 1000));
        report.setQuestionSummary(questionSummary == null ? null : questionSummary.substring(0, Math.min(1000, questionSummary.length())));
        report.setExamId(examId);
        report.setExamTitle(examTitle);
        report.setQuestionNo(questionNo);
        report.setIssueType(issueType != null && !issueType.isBlank() ? issueType : "Nội dung câu hỏi có lỗi");
        report.setDetails(details != null ? details.trim() : "");
        report.setStatus("OPEN");
        report.setRewardCoins(5);
        return reportRepository.save(report);
    }

    @Transactional(readOnly = true)
    public List<QuestionReport> getAllReports() {
        return reportRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<QuestionReport> getReportById(Long id) {
        return reportRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public long getPendingCount() {
        return reportRepository.countByStatus("OPEN");
    }

    @Transactional
    public boolean resolveReport(Long reportId, String resolutionNote, String resolvedBy) {
        Optional<QuestionReport> opt = reportRepository.findForUpdate(reportId);
        if (opt.isEmpty()) return false;

        QuestionReport report = opt.get();
        if (!"OPEN".equals(report.getStatus())) return false;
        report.setStatus("RESOLVED");
        report.setResolutionNote(resolutionNote != null && !resolutionNote.isBlank() ? resolutionNote.trim() : "Đã kiểm tra và chỉnh sửa nội dung câu hỏi chính xác theo chuẩn NIIED.");
        report.setResolvedBy(resolvedBy != null ? resolvedBy : "Content Manager");
        report.setResolvedAt(LocalDateTime.now());
        report.setRewardCoins(5);
        reportRepository.save(report);

        // Cộng +5 xu cho học viên báo lỗi
        if (report.getUserId() != null) {
            userRepository.findById(report.getUserId()).ifPresent(user -> {
                int currentCoins = user.getCoinBalance() != null ? user.getCoinBalance() : 0;
                user.setCoinBalance(currentCoins + 5);
                userRepository.save(user);
                log.info("Cộng +5 xu cho user ID {} sau khi duyệt báo lỗi #{}", user.getId(), reportId);
            });

            // Gửi thông báo đến học viên
            UserNotification noti = new UserNotification();
            noti.setUserId(report.getUserId());
            noti.setTitle("Báo lỗi câu hỏi đã được sửa (+5 Xu) 🎉");
            noti.setMessage("Báo lỗi cho " + report.getQuestionCode() + " đã được Content Manager xác nhận và chỉnh sửa. Bạn được cộng +5 Xu thưởng!");
            noti.setType("REPORT_RESOLVED");
            noti.setLink("/bao-loi/" + report.getId());
            noti.setRead(false);
            notificationRepository.save(noti);
        }

        return true;
    }

    @Transactional
    public boolean rejectReport(Long reportId, String rejectionReason, String resolvedBy) {
        Optional<QuestionReport> opt = reportRepository.findForUpdate(reportId);
        if (opt.isEmpty()) return false;

        QuestionReport report = opt.get();
        if (!"OPEN".equals(report.getStatus())) return false;
        report.setStatus("REJECTED");
        report.setRejectionReason(rejectionReason != null && !rejectionReason.isBlank() ? rejectionReason.trim() : "Đã kiểm tra lại câu hỏi, nội dung và đáp án hoàn toàn chính xác.");
        report.setResolvedBy(resolvedBy != null ? resolvedBy : "Content Manager");
        report.setResolvedAt(LocalDateTime.now());
        reportRepository.save(report);

        // Gửi thông báo đến học viên kèm lý do từ chối
        if (report.getUserId() != null) {
            UserNotification noti = new UserNotification();
            noti.setUserId(report.getUserId());
            noti.setTitle("Phản hồi báo lỗi: " + report.getQuestionCode());
            noti.setMessage("Content Manager đã kiểm tra báo lỗi của bạn (Không phát hiện lỗi). Bấm để xem giải thích chi tiết.");
            noti.setType("REPORT_REJECTED");
            noti.setLink("/bao-loi/" + report.getId());
            noti.setRead(false);
            notificationRepository.save(noti);
        }

        return true;
    }

    @Transactional(readOnly = true)
    public List<UserNotification> getUserNotifications(Long userId) {
        if (userId == null) return List.of();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<UserNotification> getTopUserNotifications(Long userId) {
        if (userId == null) return List.of();
        return notificationRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadNotificationCount(Long userId) {
        if (userId == null) return 0;
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markNotificationAsRead(Long notificationId, Long userId) {
        if (notificationId == null) return;
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (userId != null && userId.equals(n.getUserId())) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    @Transactional(readOnly = true)
    public ReportQuestion reportedQuestion(QuestionReport report) {
        if (report.getQuestionId() == null) return null;
        boolean writing = isWriting(report);
        if (writing) {
            WritingPrompt w = writingPrompts.findById(report.getQuestionId()).orElse(null);
            if (w == null || !matchesLegacy(report, w.getPromptKo())) return null;
            Long source = w.getSource() == null ? null : w.getSource().getId();
            JsonNode chart = null;
            try { if (w.getChartJson() != null) chart = new ObjectMapper().readTree(w.getChartJson()); }
            catch (Exception ignored) { }
            return new ReportQuestion(w.getPromptKo(), null, null, null, media(report, w.getChartImage()),
                    List.of(), null, null, w.getModelAnswer(), w.getRubricNote(), chart, source);
        }
        Question q = questions.findById(report.getQuestionId()).orElse(null);
        if (q == null || !matchesLegacy(report, q.getPromptKo())) return null;
        QuestionGroup g = q.getGroup();
        ExamSource source = q.getSource() != null ? q.getSource() : g == null ? null : g.getSource();
        List<Choice> options = q.options().stream().map(o -> isImage(o)
                ? new Choice(null, media(report, o)) : new Choice(o, null)).toList();
        return new ReportQuestion(q.getPromptKo(), g == null ? null : g.getPassageKo(),
                g == null ? null : g.getScriptKo(), g == null ? null : media(report, g.getAudioFile()),
                g == null ? null : media(report, g.getImageFile()), options, q.getAnswerIndex(),
                q.getExplanationVi(), null, null, null, source == null ? null : source.getId());
    }

    public boolean isWriting(QuestionReport report) {
        return "WRITING".equals(report.getQuestionKind()) || (report.getQuestionKind() == null
                && report.getQuestionCode() != null && report.getQuestionCode().startsWith("Viết"));
    }

    @Transactional(readOnly = true)
    public WritingPrompt writingToEdit(QuestionReport report) {
        if (!isWriting(report) || reportedQuestion(report) == null) {
            throw new IllegalArgumentException("Không tìm thấy câu Viết được báo lỗi.");
        }
        return writingPrompts.findById(report.getQuestionId()).orElseThrow();
    }

    @Transactional
    public void updateWritingAndResolve(Long reportId, WritingPrompt form, String note, String editor) {
        QuestionReport report = reportRepository.findForUpdate(reportId).orElseThrow();
        if (!"OPEN".equals(report.getStatus())) throw new IllegalArgumentException("Báo lỗi đã được xử lý.");
        WritingPrompt prompt = writingToEdit(report);
        requireText(form.getPromptKo(), 4000, "Đề bài", true);
        requireText(form.getModelAnswer(), 4000, "Bài viết tham khảo", true);
        requireText(form.getKeys1(), 1000, "Đáp án ô 1", false);
        requireText(form.getKeys2(), 1000, "Đáp án ô 2", false);
        requireText(form.getChartJson(), 2000, "Dữ liệu biểu đồ", false);
        requireText(form.getRubricNote(), 2000, "Hướng dẫn chấm", false);
        requireText(note, 2000, "Ghi chú gửi người học", true);
        if (form.getChartJson() != null && !form.getChartJson().isBlank()) {
            try { new ObjectMapper().readTree(form.getChartJson()); }
            catch (Exception ex) { throw new IllegalArgumentException("Dữ liệu biểu đồ phải là JSON hợp lệ."); }
        }
        prompt.setPromptKo(form.getPromptKo().trim());
        prompt.setModelAnswer(form.getModelAnswer().trim());
        prompt.setKeys1(form.getKeys1());
        prompt.setKeys2(form.getKeys2());
        prompt.setChartJson(form.getChartJson());
        prompt.setRubricNote(form.getRubricNote());
        resolveReport(reportId, note, editor);
    }

    private static void requireText(String value, int max, String label, boolean required) {
        if ((required && (value == null || value.isBlank())) || (value != null && value.length() > max)) {
            throw new IllegalArgumentException(label + (required ? " bắt buộc và" : "") + " tối đa " + max + " ký tự.");
        }
    }

    private static boolean matchesLegacy(QuestionReport report, String prompt) {
        // Old reports did not distinguish writing IDs from multiple-choice IDs. Never show an unrelated row.
        return report.getQuestionKind() != null || (report.getQuestionSummary() != null
                && Objects.equals(report.getQuestionSummary(), prompt));
    }

    private static boolean isImage(String value) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(png|jpe?g|gif|webp)$");
    }

    private static String media(QuestionReport report, String filename) {
        return filename == null || filename.isBlank() ? null : "/bao-loi/" + report.getId() + "/media/"
                + org.springframework.web.util.UriUtils.encodePathSegment(filename, java.nio.charset.StandardCharsets.UTF_8);
    }
}
