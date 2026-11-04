package com.hottopik.service;

import com.hottopik.entity.QuestionReport;
import com.hottopik.entity.exam.ExamAttempt;
import com.hottopik.entity.exam.PracticeAttempt;
import com.hottopik.repository.exam.ExamAttemptRepository;
import com.hottopik.repository.exam.ExamRepository;
import com.hottopik.repository.exam.PracticeAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Arrays;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportAttemptService {
    private final PracticeAttemptRepository practices;
    private final ExamAttemptRepository attempts;
    private final ExamRepository exams;

    /*
     * Ngữ cảnh attempt là tùy chọn để tương thích form cũ; đã gửi ngữ cảnh thì phải đủ và thuộc user + câu được báo lỗi.
     * PRACTICE kiểm tra questionIds; EXAM kiểm tra MC/WritingPrompt thuộc đề của lượt làm, kèm examId nếu form có.
     */
    public void validate(Long userId, Long attemptId, String kind, Long questionId, String questionKind, Long examId) {
        if (attemptId == null && (kind == null || kind.isBlank())) return;
        if (attemptId == null || questionId == null) throw invalid();
        if ("PRACTICE".equals(kind)) {
            PracticeAttempt a = practices.findByIdAndUserId(attemptId, userId).orElseThrow(this::invalid);
            if ("WRITING".equals(questionKind) || !contains(a, questionId)) throw invalid();
        } else if ("EXAM".equals(kind)) {
            ExamAttempt a = attempts.findById(attemptId).filter(it -> userId.equals(it.getUserId())).orElseThrow(this::invalid);
            if ((examId != null && !examId.equals(a.getExam().getId())) || !contains(a, questionId, questionKind)) throw invalid();
        } else throw invalid();
    }

    /*
     * Chỉ tạo liên kết cho báo lỗi đã đóng, lượt của đúng người gửi đã nộp và còn chứa câu này.
     * Anchor #question-ID hoặc #writing-ID khớp ID trên template kết quả; báo lỗi cũ thiếu attempt không được đoán lượt.
     */
    public String historyLink(QuestionReport report) {
        if ("OPEN".equals(report.getStatus()) || report.getQuestionId() == null) return null;
        Long id = report.getAttemptId();
        if (id != null) {
            if ("PRACTICE".equals(report.getAttemptKind())) {
                return practices.findByIdAndUserId(id, report.getUserId())
                        .filter(a -> a.getCompletedAt() != null && contains(a, report.getQuestionId()))
                        .map(a -> "/practice/result/" + a.getId() + "#question-" + report.getQuestionId()).orElse(null);
            }
            if ("EXAM".equals(report.getAttemptKind())) {
                return attempts.findById(id).filter(a -> report.getUserId().equals(a.getUserId())
                                && a.getCompletedAt() != null && contains(a, report.getQuestionId(), report.getQuestionKind()))
                        .map(a -> "/ket-qua/" + a.getId() + anchor(report)).orElse(null);
            }
        }
        // Old reports do not identify an attempt. Do not guess a different historical submission.
        return null;
    }

    private boolean contains(ExamAttempt attempt, Long questionId, String kind) {
        var exam = exams.findForComposerById(attempt.getExam().getId()).orElseThrow(this::invalid);
        return "WRITING".equals(kind) ? exam.writingPrompts().stream().anyMatch(p -> Objects.equals(p.getId(), questionId))
                : exam.getItems().stream().anyMatch(i -> Objects.equals(i.getQuestion().getId(), questionId));
    }

    private static boolean contains(PracticeAttempt attempt, Long questionId) {
        return attempt.getQuestionIds() != null && Arrays.stream(attempt.getQuestionIds().split(","))
                .anyMatch(id -> id.equals(questionId.toString()));
    }

    private static String anchor(QuestionReport report) {
        return ("WRITING".equals(report.getQuestionKind()) ? "#writing-" : "#question-") + report.getQuestionId();
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câu hỏi không thuộc lượt làm bài của bạn.");
    }
}
