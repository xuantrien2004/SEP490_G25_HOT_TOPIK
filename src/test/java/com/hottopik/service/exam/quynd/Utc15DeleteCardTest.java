package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.ExamSource;
import com.hottopik.entity.exam.SourceStatus;
import com.hottopik.entity.exam.Track;
import com.hottopik.repository.exam.ExamSourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/** UTC15 – ExamImportService.deleteCard(Long sourceId, String cardId, Actor actor): xóa một bài trên tờ nhập tay. */
class Utc15DeleteCardTest extends QuyndTestSupport {

    @Autowired ExamSourceRepository sources;

    private ExamSource withOneCard() {
        ExamSource s = newSource(Track.TOPIK_I);
        imports.startManual(s.getId(), CONTENT);
        imports.saveCard(s.getId(), readingCard(31), CONTENT);
        return s;
    }

    @Test @DisplayName("UTCID01 N: xóa bài b1 → tờ đề không còn bài nào")
    void utcid01() {
        ExamSource s = withOneCard();
        imports.deleteCard(s.getId(), "b1", CONTENT);
        assertTrue(imports.cards(s.getId()).isEmpty());
        assertEquals("EDIT_EXAM_DRAFT", lastAudit().getAction());
        assertTrue(lastAudit().getDetails().contains("Xóa bài b1"));
    }

    @Test @DisplayName("UTCID02 N: có 2 bài, xóa b1 → b2 còn nguyên, ô 31 nhập lại được")
    void utcid02() {
        ExamSource s = withOneCard();
        imports.saveCard(s.getId(), readingCard(32), CONTENT);
        imports.deleteCard(s.getId(), "b1", CONTENT);
        assertEquals(1, imports.cards(s.getId()).size());
        assertEquals("b2", imports.cards(s.getId()).get(0).id());
        assertDoesNotThrow(() -> imports.saveCard(s.getId(), readingCard(31), CONTENT));
    }

    @Test @DisplayName("UTCID03 A: xóa bài b1 lần thứ hai")
    void utcid03() {
        ExamSource s = withOneCard();
        imports.deleteCard(s.getId(), "b1", CONTENT);
        expectError("Không còn bài này trên tờ đề.", () -> imports.deleteCard(s.getId(), "b1", CONTENT));
    }

    @Test @DisplayName("UTCID04 A: cardId = null")
    void utcid04() {
        ExamSource s = withOneCard();
        expectError("Không còn bài này trên tờ đề.", () -> imports.deleteCard(s.getId(), null, CONTENT));
    }

    @Test @DisplayName("UTCID05 A: Admin xóa bài")
    void utcid05() {
        ExamSource s = withOneCard();
        expectError("Chỉ Content Manager được nhập đề (Admin chỉ xem).", () -> imports.deleteCard(s.getId(), "b1", ADMIN));
        assertEquals(1, imports.cards(s.getId()).size());
    }

    @Test @DisplayName("UTCID06 A: nguồn đã kích hoạt")
    void utcid06() {
        ExamSource s = withOneCard();
        s.setStatus(SourceStatus.ACTIVE);
        sources.save(s);
        expectError("Nguồn đề đã kích hoạt, không thay file được nữa. Sửa từng câu trong Ngân hàng câu hỏi.",
                () -> imports.deleteCard(s.getId(), "b1", CONTENT));
    }

    @Test @DisplayName("UTCID07 A: nguồn chưa mở tờ nhập tay")
    void utcid07() {
        ExamSource s = newSource(Track.TOPIK_I);
        expectError("Chưa có tờ đề. Hãy mở trang nhập.", () -> imports.deleteCard(s.getId(), "b1", CONTENT));
    }
}
