package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.PlacementAttempt;
import com.hottopik.entity.exam.Question;
import com.hottopik.entity.exam.Skill;
import com.hottopik.repository.exam.QuestionRepository;
import com.hottopik.service.PlacementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** UTC20 – PlacementService.submit(PlacementAttempt attempt, Map&lt;String,String&gt; submitted): chấm bài kiểm tra trình độ. */
class Utc20SubmitPlacementTest extends QuyndTestSupport {

    @Autowired PlacementService placement;
    @Autowired QuestionRepository questions;

    private PlacementAttempt attempt;
    private final List<Question> listen = new ArrayList<>();
    private final List<Question> read = new ArrayList<>();

    @BeforeEach
    void start() {
        assessmentBank(3);
        placement.save("TOPIK_I", DEFAULT_PLACEMENT);
        attempt = placement.start(9L, "learner@hottopik.com", "TOPIK_I");
        for (String id : attempt.getQuestionIds().split(",")) {
            Question q = questions.findById(Long.valueOf(id)).orElseThrow();
            (q.getSkill() == Skill.LISTENING ? listen : read).add(q);
        }
    }

    /** Đúng {@code l} câu Nghe và {@code r} câu Đọc; các câu còn lại chọn sai. */
    private Map<String, String> answers(int l, int r) {
        Map<String, String> form = new HashMap<>();
        for (int i = 0; i < listen.size(); i++) put(form, listen.get(i), i < l);
        for (int i = 0; i < read.size(); i++) put(form, read.get(i), i < r);
        return form;
    }

    private static void put(Map<String, String> form, Question q, boolean right) {
        form.put(q.getId().toString(), String.valueOf(right ? q.getAnswerIndex() : q.getAnswerIndex() % 4 + 1));
    }

    @Test @DisplayName("UTCID01 N: đúng hết 20 câu → 200 điểm, Nghe 10, Đọc 10, đã nộp")
    void utcid01() {
        PlacementAttempt done = placement.submit(attempt, answers(10, 10));
        assertEquals(20, done.getCorrectCount());
        assertEquals(200, done.getPoints());
        assertEquals(10, done.getListenCorrect());
        assertEquals(10, done.getReadCorrect());
        assertNotNull(done.getCompletedAt());
    }

    @Test @DisplayName("UTCID02 N: đúng 4 Nghe + 3 Đọc → 70 điểm")
    void utcid02() {
        PlacementAttempt done = placement.submit(attempt, answers(4, 3));
        assertEquals(7, done.getCorrectCount());
        assertEquals(70, done.getPoints());
        assertEquals(4, done.getListenCorrect());
        assertEquals(3, done.getReadCorrect());
    }

    @Test @DisplayName("UTCID03 N: câu đã bấm Kiểm tra (sai) tính theo đáp án đã khóa dù form gửi đáp án đúng")
    void utcid03() {
        Question q = listen.get(0);
        placement.check(attempt, q.getId(), q.getAnswerIndex() % 4 + 1);
        PlacementAttempt done = placement.submit(attempt, answers(10, 10));
        assertEquals(19, done.getCorrectCount());
        assertEquals(9, done.getListenCorrect());
    }

    @Test @DisplayName("UTCID04 B: bỏ trống cả 20 câu → 0 điểm, answersJson rỗng")
    void utcid04() {
        PlacementAttempt done = placement.submit(attempt, Map.of());
        assertEquals(0, done.getCorrectCount());
        assertEquals(0, done.getPoints());
        assertEquals("{}", done.getAnswersJson());
    }

    @Test @DisplayName("UTCID05 B: đúng 14 câu → 140 điểm (mốc gợi ý cấp 2)")
    void utcid05() {
        assertEquals(140, placement.submit(attempt, answers(7, 7)).getPoints());
    }

    @Test @DisplayName("UTCID06 A: giá trị lạ ('0', '5', 'abc') bị bỏ qua như câu trống")
    void utcid06() {
        Map<String, String> form = new HashMap<>();
        form.put(listen.get(0).getId().toString(), "0");
        form.put(listen.get(1).getId().toString(), "5");
        form.put(read.get(0).getId().toString(), "abc");
        PlacementAttempt done = placement.submit(attempt, form);
        assertEquals(0, done.getCorrectCount());
        assertEquals("{}", done.getAnswersJson());
    }

    @Test @DisplayName("UTCID07 A: nộp lần hai → giữ nguyên kết quả lần đầu")
    void utcid07() {
        placement.submit(attempt, answers(1, 1));
        PlacementAttempt again = placement.submit(attempt, answers(10, 10));
        assertEquals(2, again.getCorrectCount());
        assertEquals(20, again.getPoints());
    }

    @Test @DisplayName("UTCID08 A: submitted = null → coi như bỏ trống, 0 điểm, đã nộp")
    void utcid08() {
        PlacementAttempt done = placement.submit(attempt, null);
        assertEquals(0, done.getPoints());
        assertNotNull(done.getCompletedAt());
    }
}
