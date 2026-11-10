package com.hottopik.service.exam;

import com.hottopik.dto.ExamScoreDTO;
import com.hottopik.dto.WritingSubmissionDTO;
import com.hottopik.entity.exam.ExamAttempt;
import com.hottopik.entity.exam.ExamKind;
import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ExamScoreCalculatorTest {

    @Test
    void topik1Listening30Questions100PointsMax() {
        List<LearnerExamService.QuestionView> questions = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            questions.add(new LearnerExamService.QuestionView(
                    (long) i, i, "q" + i, Skill.LISTENING, "Nghe · Câu " + i,
                    "Hướng dẫn", "Câu hỏi " + i, "Bài đọc", null, null,
                    List.of(), "1", 1, "Giải thích", null
            ));
        }

        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(100L);
        attempt.setCompletedAt(LocalDateTime.now());
        attempt.setTotal(30);
        attempt.setCorrect(30);

        LearnerExamService.Paper paper = new LearnerExamService.Paper(
                attempt, "Đề thi thử TOPIK I Nghe", Track.TOPIK_I, ExamKind.LISTENING,
                0, questions, List.of(), List.of()
        );

        ExamScoreDTO score = ExamScoreCalculator.calculate(paper, Map.of(), Map.of());

        assertEquals(100.0, score.getTotalEarnedScore(), 0.001);
        assertEquals(100.0, score.getTotalMaxScore(), 0.001);
        assertEquals(3.33, score.getListenPointPerQuestion(), 0.001);
        assertEquals(30, score.getListenCorrect());
        assertEquals("100", score.getFormattedTotalScore());
        assertEquals("Xuất sắc", score.getGradeBadge());
    }

    @Test
    void topik1Reading40Questions100PointsMax() {
        List<LearnerExamService.QuestionView> questions = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            String selected = i <= 30 ? "2" : "1";
            questions.add(new LearnerExamService.QuestionView(
                    (long) i, i, "q" + i, Skill.READING, "Đọc · Câu " + i,
                    "Hướng dẫn", "Câu hỏi " + i, "Bài đọc", null, null,
                    List.of(), selected, 2, "Giải thích", null
            ));
        }

        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(101L);
        attempt.setCompletedAt(LocalDateTime.now());
        attempt.setTotal(40);
        attempt.setCorrect(30);

        LearnerExamService.Paper paper = new LearnerExamService.Paper(
                attempt, "Đề thi thử TOPIK I Đọc", Track.TOPIK_I, ExamKind.READING,
                0, questions, List.of(), List.of()
        );

        ExamScoreDTO score = ExamScoreCalculator.calculate(paper, Map.of(), Map.of());

        assertEquals(75.0, score.getTotalEarnedScore(), 0.001);
        assertEquals(100.0, score.getTotalMaxScore(), 0.001);
        assertEquals(2.50, score.getReadPointPerQuestion(), 0.001);
        assertEquals(30, score.getReadCorrect());
        assertEquals("75", score.getFormattedTotalScore());
        assertEquals("Đạt", score.getGradeBadge());
    }

    @Test
    void topik2FullMockWithTeacherGradedWriting() {
        List<LearnerExamService.QuestionView> questions = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            questions.add(new LearnerExamService.QuestionView(
                    (long) i, i, "q" + i, Skill.LISTENING, "Nghe · Câu " + i,
                    "HD", "Prompt " + i, null, null, null,
                    List.of(), i <= 40 ? "1" : "2", 1, "Giải thích", null
            ));
        }
        for (int i = 51; i <= 100; i++) {
            questions.add(new LearnerExamService.QuestionView(
                    (long) i, i, "q" + i, Skill.READING, "Đọc · Câu " + i,
                    "HD", "Prompt " + i, null, null, null,
                    List.of(), i <= 95 ? "1" : "2", 1, "Giải thích", null
            ));
        }

        List<LearnerExamService.WritingView> writing = List.of(
                new LearnerExamService.WritingView(201L, "w201", 51, "P51", null, List.of(), "ans51", null, null, "연락해 주십시오", "시간을 변경하고 싶습니다"),
                new LearnerExamService.WritingView(202L, "w202", 52, "P52", null, List.of(), "ans52", null, null, "변화가 일어난다", "영향을 준다"),
                new LearnerExamService.WritingView(203L, "w203", 53, "P53", null, List.of(), "ans53", null, null),
                new LearnerExamService.WritingView(204L, "w204", 54, "P54", null, List.of(), "ans54", null, null)
        );

        Map<Long, WritingSubmissionDTO> reviews = new HashMap<>();
        WritingSubmissionDTO sub51 = new WritingSubmissionDTO();
        sub51.setStatus("graded"); sub51.setTotalScore(8); reviews.put(201L, sub51);
        WritingSubmissionDTO sub52 = new WritingSubmissionDTO();
        sub52.setStatus("graded"); sub52.setTotalScore(9); reviews.put(202L, sub52);
        WritingSubmissionDTO sub53 = new WritingSubmissionDTO();
        sub53.setStatus("graded"); sub53.setTotalScore(25); reviews.put(203L, sub53);
        WritingSubmissionDTO sub54 = new WritingSubmissionDTO();
        sub54.setStatus("graded"); sub54.setTotalScore(42); reviews.put(204L, sub54);

        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(102L);
        attempt.setCompletedAt(LocalDateTime.now());
        attempt.setTotal(100);
        attempt.setCorrect(85);

        LearnerExamService.Paper paper = new LearnerExamService.Paper(
                attempt, "Đề thi TOPIK II Full Mock", Track.TOPIK_II, ExamKind.FULL,
                0, questions, writing, List.of(attempt)
        );

        ExamScoreDTO score = ExamScoreCalculator.calculate(paper, reviews, Map.of());

        assertEquals(80.0, score.getListenScore(), 0.001);
        assertEquals(90.0, score.getReadScore(), 0.001);
        assertEquals(84.0, score.getWritingTotalScore(), 0.001);
        assertEquals(254.0, score.getTotalEarnedScore(), 0.001);
        assertEquals(300.0, score.getTotalMaxScore(), 0.001);
        assertEquals("Xuất sắc", score.getGradeBadge());
    }

    @Test
    void topik2Writing51And52AutoEvaluationWithoutSendingToTeacher() {
        // Học viên nộp bài nhưng KHÔNG gửi Giáo viên chấm: câu 51, 52 chấm tự động theo đáp án tham khảo
        String ans51 = "㉠ 연락해 주십시오\n㉡ 시간을 변경하고 싶습니다";
        String ans52 = "㉠ 변화가 일어난다\n㉡ 영향을 준다";

        List<LearnerExamService.WritingView> writing = List.of(
                new LearnerExamService.WritingView(201L, "w201", 51, "P51", null, List.of(), ans51, null, null,
                        "연락해 주십시오|연락 바랍니다", "시간을 변경하고 싶습니다|시간을 바꾸려고 합니다"),
                new LearnerExamService.WritingView(202L, "w202", 52, "P52", null, List.of(), ans52, null, null,
                        "변화가 일어난다", "영향을 준다"),
                new LearnerExamService.WritingView(203L, "w203", 53, "P53", null, List.of(), "Bài luận biểu đồ", null, null),
                new LearnerExamService.WritingView(204L, "w204", 54, "P54", null, List.of(), "", null, null)
        );

        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(103L);
        attempt.setCompletedAt(LocalDateTime.now());
        attempt.setTotal(0);

        LearnerExamService.Paper paper = new LearnerExamService.Paper(
                attempt, "TOPIK II Viết", Track.TOPIK_II, ExamKind.FULL,
                0, List.of(), writing, List.of(attempt)
        );

        // Chưa gửi GV chấm (reviews rỗng)
        ExamScoreDTO score = ExamScoreCalculator.calculate(paper, Map.of(), Map.of());

        // C51 và C52 đạt điểm chuẩn theo đáp án tham khảo
        assertEquals(10.0, score.getWriting51Score(), 0.001);
        assertEquals("AUTO", score.getWriting51Status());
        assertEquals(10.0, score.getWriting52Score(), 0.001);
        assertEquals("AUTO", score.getWriting52Status());

        // C53 có làm nhưng chưa gửi GV chấm -> null điểm
        assertNull(score.getWriting53Score());
        assertEquals("NOT_SUBMITTED", score.getWriting53Status());

        // C54 để trống -> null điểm
        assertNull(score.getWriting54Score());
        assertEquals("EMPTY", score.getWriting54Status());

        // Tổng điểm Viết tính từ 51 + 52 = 20.0đ
        assertEquals(20.0, score.getWritingTotalScore(), 0.001);
        assertEquals(20.0, score.getTotalEarnedScore(), 0.001);
    }

    @Test
    void topik2WritingEndingDeductionsFor51And52() {
        // C51 dùng đuôi thân mật -아요/어요 -> bị trừ đuôi câu
        String ans51 = "㉠ 연락해 주세요\n㉡ 시간을 변경하고 싶어요";
        // C52 dùng kính ngữ -습니다 -> bị trừ đuôi câu vì câu 52 bắt buộc phải là -ㄴ/는다, -다
        String ans52 = "㉠ 변화가 일어납니다\n㉡ 영향을 미칩니다";

        Writing5152Evaluator.EvaluationResult res51 = Writing5152Evaluator.evaluate(
                51, ans51, "연락해 주십시오|연락 바랍니다", "시간을 변경하고 싶습니다", null
        );
        // Mỗi ý bị trừ 1.5đ đuôi câu, còn tối đa 3.5đ/ý -> tổng 7.0đ/10đ
        assertEquals(7.0, res51.totalScore(), 0.001);

        Writing5152Evaluator.EvaluationResult res52 = Writing5152Evaluator.evaluate(
                52, ans52, "변화가 일어난다", "영향을 준다|영향을 미친다", null
        );
        // Dùng kính ngữ ở câu 52 bị trừ đuôi câu -> 7.0đ/10đ
        assertEquals(7.0, res52.totalScore(), 0.001);
    }

    @Test
    void topik2WritingTeacherGradingOverridesAndWaitingHidesScore() {
        // C51: Đã gửi GV và GV đã chấm 9.5đ
        // C52: Đã gửi GV nhưng ĐANG CHỜ CHẤM -> KHÔNG ĐƯỢC HIỂN THỊ ĐIỂM (score = null)
        // C53: Đã gửi GV và GV đã chấm 26đ
        // C54: Chưa gửi GV
        List<LearnerExamService.WritingView> writing = List.of(
                new LearnerExamService.WritingView(201L, "w201", 51, "P51", null, List.of(), "ans51", null, null, "연락해 주십시오", "시간"),
                new LearnerExamService.WritingView(202L, "w202", 52, "P52", null, List.of(), "ans52", null, null, "변화가 일어난다", "영향"),
                new LearnerExamService.WritingView(203L, "w203", 53, "P53", null, List.of(), "ans53", null, null),
                new LearnerExamService.WritingView(204L, "w204", 54, "P54", null, List.of(), "ans54", null, null)
        );

        Map<Long, WritingSubmissionDTO> reviews = new HashMap<>();
        WritingSubmissionDTO sub51 = new WritingSubmissionDTO();
        sub51.setStatus("graded"); sub51.setTotalScore(9); reviews.put(201L, sub51);

        WritingSubmissionDTO sub52 = new WritingSubmissionDTO();
        sub52.setStatus("waiting"); sub52.setTotalScore(null); reviews.put(202L, sub52); // Đang chờ chấm!

        WritingSubmissionDTO sub53 = new WritingSubmissionDTO();
        sub53.setStatus("graded"); sub53.setTotalScore(26); reviews.put(203L, sub53);

        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(104L);
        attempt.setCompletedAt(LocalDateTime.now());
        attempt.setTotal(0);

        LearnerExamService.Paper paper = new LearnerExamService.Paper(
                attempt, "TOPIK II Viết", Track.TOPIK_II, ExamKind.FULL,
                0, List.of(), writing, List.of(attempt)
        );

        ExamScoreDTO score = ExamScoreCalculator.calculate(paper, reviews, Map.of());

        // C51 lấy điểm GV chấm
        assertEquals(9.0, score.getWriting51Score(), 0.001);
        assertEquals("GRADED", score.getWriting51Status());

        // C52 Đang chờ GV chấm -> PHẢI LÀ NULL, KHÔNG HIỂN THỊ ĐIỂM
        assertNull(score.getWriting52Score());
        assertEquals("WAITING", score.getWriting52Status());
        assertTrue(score.isWriting52Waiting());

        // C53 lấy điểm GV chấm
        assertEquals(26.0, score.getWriting53Score(), 0.001);
        assertEquals("GRADED", score.getWriting53Status());

        // C54 Chưa gửi
        assertNull(score.getWriting54Score());

        // Điểm Viết đã có = 9 + 26 = 35.0đ (không cộng C52 vì đang chờ)
        assertEquals(35.0, score.getWritingTotalScore(), 0.001);
        assertEquals(35.0, score.getTotalEarnedScore(), 0.001);
        assertTrue(score.hasAnyWritingWaiting());
    }

    @Test
    void calculateHistoryShowsScoreDirectlyForPastAttemptsWithoutOpeningThem() {
        // Kiểm tra tính toán điểm trực tiếp cho cả lượt hiện tại và các lượt làm trước đó trong bảng Lịch sử
        List<LearnerExamService.WritingView> writing = List.of(
                new LearnerExamService.WritingView(201L, "w201", 51, "P51", null, List.of(), "", null, null, "연락해 주십시오", "시간을 변경하고 싶습니다"),
                new LearnerExamService.WritingView(202L, "w202", 52, "P52", null, List.of(), "", null, null, "변화가 일어난다", "영향을 준다")
        );

        // Lượt 1 trong quá khứ (id=101): MC 40/50 đúng, C51 điền đúng đáp án mẫu
        ExamAttempt pastAttempt = new ExamAttempt();
        pastAttempt.setId(101L);
        pastAttempt.setCompletedAt(LocalDateTime.now().minusDays(1));
        pastAttempt.setTotal(50);
        pastAttempt.setCorrect(40); // 40 * 2.0 = 80đ
        pastAttempt.setAnswersJson("{\"w201\":\"㉠ 연락해 주십시오\\n㉡ 시간을 변경하고 싶습니다\"}");

        // Lượt 2 hiện tại (id=102): MC 45/50 đúng
        ExamAttempt currentAttempt = new ExamAttempt();
        currentAttempt.setId(102L);
        currentAttempt.setCompletedAt(LocalDateTime.now());
        currentAttempt.setTotal(50);
        currentAttempt.setCorrect(45); // 45 * 2.0 = 90đ

        LearnerExamService.Paper paper = new LearnerExamService.Paper(
                currentAttempt, "Đề TOPIK II", Track.TOPIK_II, ExamKind.READING,
                0, List.of(), writing, List.of(currentAttempt, pastAttempt)
        );

        ExamScoreDTO currentScore = new ExamScoreDTO();
        currentScore.setTotalEarnedScore(90.0);
        currentScore.setTotalMaxScore(200.0);
        currentScore.setGradeBadge("Xuất sắc");
        currentScore.setGradeCss("success");

        List<ExamScoreDTO.HistoryItem> history = ExamScoreCalculator.calculateHistory(paper, currentScore, Map.of());

        assertEquals(2, history.size());

        // Lượt hiện tại
        ExamScoreDTO.HistoryItem hCur = history.get(0);
        assertEquals(102L, hCur.getId());
        assertTrue(hCur.isCurrent());
        assertEquals("90 / 200", hCur.getFormattedScore());

        // Lượt quá khứ: tính luôn điểm tự động cho C51 (10đ) + MC 80đ = 90 / 200đ mà không cần bấm "Xem lại"
        ExamScoreDTO.HistoryItem hPast = history.get(1);
        assertEquals(101L, hPast.getId());
        assertFalse(hPast.isCurrent());
        assertEquals("90 / 200", hPast.getFormattedScore());
        assertEquals(90.0, hPast.getScore(), 0.001);
        assertEquals(200.0, hPast.getMaxScore(), 0.001);
    }
}
