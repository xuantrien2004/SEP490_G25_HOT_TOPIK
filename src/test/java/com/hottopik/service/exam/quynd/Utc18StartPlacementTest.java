package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.PlacementAttempt;
import com.hottopik.entity.exam.Question;
import com.hottopik.entity.exam.QuestionStatus;
import com.hottopik.repository.exam.PlacementLineRepository;
import com.hottopik.repository.exam.QuestionRepository;
import com.hottopik.service.PlacementService;
import com.hottopik.service.exam.PlacementRules.Paper;
import com.hottopik.service.exam.QuestionTypeCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** UTC18 – PlacementService.start(Long userId, String email, String paperCode): tạo đề kiểm tra trình độ cho học viên. */
class Utc18StartPlacementTest extends QuyndTestSupport {

    @Autowired PlacementService placement;
    @Autowired PlacementLineRepository lines;
    @Autowired QuestionRepository questions;

    @BeforeEach
    void structure() {
        assessmentBank(3);
        placement.save("TOPIK_I", DEFAULT_PLACEMENT);
    }

    /** Giữ lại đúng {@code keep} câu ASSESSMENT đang dùng của dạng, ẩn phần còn lại. */
    private void shrinkPool(String typeCode, int keep) {
        List<Question> pool = questions.findAssessmentPool(typeCode);
        for (int i = keep; i < pool.size(); i++) {
            pool.get(i).setStatus(QuestionStatus.HIDDEN);
            questions.save(pool.get(i));
        }
    }

    @Test @DisplayName("UTCID01 N: TOPIK_I → 20 câu khác nhau, 10 Nghe + 10 Đọc, chưa có đáp án")
    void utcid01() {
        PlacementAttempt a = placement.start(9L, "learner@hottopik.com", "TOPIK_I");
        assertNotNull(a.getId());
        assertEquals(20, a.getTotal());
        assertEquals(10, a.getListenTotal());
        assertEquals(10, a.getReadTotal());
        assertEquals(20, new HashSet<>(Arrays.asList(a.getQuestionIds().split(","))).size());
        assertEquals("{}", a.getAnswersJson());
        assertNull(a.getCompletedAt());
    }

    @Test @DisplayName("UTCID02 N: mã đề viết thường có khoảng trắng (' topik_i ') vẫn nhận")
    void utcid02() {
        assertEquals(20, placement.start(9L, "learner@hottopik.com", " topik_i ").getTotal());
    }

    @Test @DisplayName("UTCID03 N: bấm Bắt đầu hai lần → hai lượt làm bài khác nhau")
    void utcid03() {
        PlacementAttempt a = placement.start(9L, "learner@hottopik.com", "TOPIK_I");
        PlacementAttempt b = placement.start(9L, "learner@hottopik.com", "TOPIK_I");
        assertNotEquals(a.getId(), b.getId());
    }

    @Test @DisplayName("UTCID04 B: ngân hàng của I-N1 còn đúng 2 câu (bằng số câu cần) → vẫn tạo được")
    void utcid04() {
        shrinkPool("I-N1", 2);
        assertEquals(20, placement.start(9L, "learner@hottopik.com", "TOPIK_I").getTotal());
    }

    @Test @DisplayName("UTCID05 A: ngân hàng của I-N1 chỉ còn 1 câu")
    void utcid05() {
        shrinkPool("I-N1", 1);
        String name = QuestionTypeCatalog.byCode("I-N1").orElseThrow().name();
        expectError("Dạng " + name + " cần 2 câu trong ngân hàng đánh giá, hiện có 1.",
                () -> placement.start(9L, "learner@hottopik.com", "TOPIK_I"));
    }

    @Test @DisplayName("UTCID06 A: học viên chọn đề TOPIK_II")
    void utcid06() {
        expectError("Chỉ có một bài kiểm tra trình độ.", () -> placement.start(9L, "learner@hottopik.com", "TOPIK_II"));
    }

    @Test @DisplayName("UTCID07 A: mã đề rỗng (\" \")")
    void utcid07() {
        expectError("Bài kiểm tra không hợp lệ.", () -> placement.start(9L, "learner@hottopik.com", " "));
    }

    @Test @DisplayName("UTCID08 A: mã đề null")
    void utcid08() {
        expectError("Chưa chọn bài kiểm tra.", () -> placement.start(9L, "learner@hottopik.com", null));
    }

    @Test @DisplayName("UTCID09 A: Content Manager chưa cấu hình đề (không có dòng nào)")
    void utcid09() {
        lines.deleteByPaperCode("TOPIK_I");
        lines.flush();
        expectError("Phần Nghe của " + Paper.TOPIK_I.title() + " phải đủ đúng 10 câu. Đang chọn 0 câu.",
                () -> placement.start(9L, "learner@hottopik.com", "TOPIK_I"));
    }
}
