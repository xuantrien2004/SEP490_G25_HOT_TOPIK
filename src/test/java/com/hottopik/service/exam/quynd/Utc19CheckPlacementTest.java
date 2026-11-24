package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.PlacementAttempt;
import com.hottopik.entity.exam.Question;
import com.hottopik.repository.exam.QuestionRepository;
import com.hottopik.service.PlacementService;
import com.hottopik.service.PlacementService.CheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** UTC19 – PlacementService.check(PlacementAttempt attempt, Long questionId, int selected): kiểm tra một câu và khóa đáp án. */
class Utc19CheckPlacementTest extends QuyndTestSupport {

    @Autowired PlacementService placement;
    @Autowired QuestionRepository questions;

    private PlacementAttempt attempt;
    private Long firstId;
    private int answer;

    @BeforeEach
    void start() {
        assessmentBank(3);
        placement.save("TOPIK_I", DEFAULT_PLACEMENT);
        attempt = placement.start(9L, "learner@hottopik.com", "TOPIK_I");
        firstId = Long.valueOf(attempt.getQuestionIds().split(",")[0]);
        Question q = questions.findById(firstId).orElseThrow();
        answer = q.getAnswerIndex();
    }

    private int wrong() {
        return answer % 4 + 1;
    }

    @Test @DisplayName("UTCID01 N: chọn đúng đáp án → correct = true, trả về lời giải")
    void utcid01() {
        CheckResult r = placement.check(attempt, firstId, answer);
        assertTrue(r.correct());
        assertEquals(answer, r.answer());
        assertNotNull(r.explanation());
    }

    @Test @DisplayName("UTCID02 N: chọn sai → correct = false, vẫn trả đáp án đúng")
    void utcid02() {
        CheckResult r = placement.check(attempt, firstId, wrong());
        assertFalse(r.correct());
        assertEquals(answer, r.answer());
        assertEquals(wrong(), r.selected());
    }

    @Test @DisplayName("UTCID03 N: kiểm tra lại câu đó với đáp án khác → giữ đáp án lần đầu")
    void utcid03() {
        placement.check(attempt, firstId, wrong());
        CheckResult again = placement.check(attempt, firstId, answer);
        assertEquals(wrong(), again.selected());
        assertFalse(again.correct());
        assertTrue(attempt.getAnswersJson().contains("\"" + firstId + "\":" + wrong()));
    }

    @Test @DisplayName("UTCID04 B: selected = 1 và selected = 4 (hai biên) được nhận")
    void utcid04() {
        Long second = Long.valueOf(attempt.getQuestionIds().split(",")[1]);
        assertEquals(1, placement.check(attempt, firstId, 1).selected());
        assertEquals(4, placement.check(attempt, second, 4).selected());
    }

    @Test @DisplayName("UTCID05 A: selected = 0 → báo lỗi, không khóa đáp án")
    void utcid05() {
        expectError("Đáp án phải từ 1 đến 4.", () -> placement.check(attempt, firstId, 0));
        assertEquals("{}", attempt.getAnswersJson());
    }

    @Test @DisplayName("UTCID06 A: selected = 5")
    void utcid06() {
        expectError("Đáp án phải từ 1 đến 4.", () -> placement.check(attempt, firstId, 5));
    }

    @Test @DisplayName("UTCID07 A: câu hỏi không nằm trong bài làm")
    void utcid07() {
        expectError("Câu hỏi không thuộc bài kiểm tra này.", () -> placement.check(attempt, -1L, 1));
    }

    @Test @DisplayName("UTCID08 A: questionId = null")
    void utcid08() {
        expectError("Câu hỏi không thuộc bài kiểm tra này.", () -> placement.check(attempt, null, 1));
    }

    @Test @DisplayName("UTCID09 A: bài đã nộp")
    void utcid09() {
        placement.submit(attempt, Map.of());
        expectError("Bài đã nộp, không kiểm tra thêm được.", () -> placement.check(attempt, firstId, answer));
    }
}
