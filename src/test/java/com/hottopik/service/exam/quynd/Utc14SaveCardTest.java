package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.ExamSource;
import com.hottopik.entity.exam.QuestionTemplate;
import com.hottopik.entity.exam.TemplateStatus;
import com.hottopik.entity.exam.Track;
import com.hottopik.repository.exam.QuestionTemplateRepository;
import com.hottopik.service.exam.ExamImportService.CardForm;
import com.hottopik.service.exam.ExamImportService.CardView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** UTC14 – ExamImportService.saveCard(Long sourceId, CardForm form, Actor actor): lưu một bài nhập tay theo khuôn. */
class Utc14SaveCardTest extends QuyndTestSupport {

    @Autowired QuestionTemplateRepository templates;

    private ExamSource manual(Track track) {
        ExamSource s = newSource(track);
        imports.startManual(s.getId(), CONTENT);
        return s;
    }

    private static CardForm grouped(List<Integer> nos) {
        List<String> two = nos.size() == 2 ? List.of("x", "y") : List.of("x");
        return new CardForm(null, null, "shared.mp3", null, null, "대본", "bản dịch", two, two, two, two, two,
                nos.size() == 2 ? List.of("1", "2") : List.of("1"), two, null, null, null, null, null, "II-N6", nos);
    }

    @Test @DisplayName("UTCID01 N: câu Đọc I-D1 ở ô 31 → lưu thành bài b1, đủ dữ liệu")
    void utcid01() {
        ExamSource s = manual(Track.TOPIK_I);
        String id = imports.saveCard(s.getId(), readingCard(31), CONTENT);
        assertEquals("b1", id);
        List<CardView> cards = imports.cards(s.getId());
        assertEquals(1, cards.size());
        assertEquals(List.of(31), cards.get(0).nos());
        assertTrue(cards.get(0).ready());
        assertEquals("EDIT_EXAM_DRAFT", lastAudit().getAction());
        assertTrue(lastAudit().getDetails().contains("Lưu bài b1"));
    }

    @Test @DisplayName("UTCID02 N: sửa lại bài b1 (gửi cardId) → không tạo bài mới")
    void utcid02() {
        ExamSource s = manual(Track.TOPIK_I);
        imports.saveCard(s.getId(), readingCard(31), CONTENT);
        String id = imports.saveCard(s.getId(), choiceCard("b1", "I-D1", 31, "giải thích đã sửa"), CONTENT);
        assertEquals("b1", id);
        assertEquals(1, imports.cards(s.getId()).size());
        assertEquals("giải thích đã sửa", imports.cards(s.getId()).get(0).asks().get(0).explanation());
    }

    @Test @DisplayName("UTCID03 N: bài Nghe dùng chung II-N6 chọn đủ ô 21–22 → lưu 2 câu")
    void utcid03() {
        ExamSource s = manual(Track.TOPIK_II);
        imports.saveCard(s.getId(), grouped(List.of(21, 22)), CONTENT);
        assertEquals(2, imports.cards(s.getId()).get(0).askCount());
    }

    @Test @DisplayName("UTCID04 N: thiếu giải thích → vẫn lưu được, bài chưa sẵn sàng")
    void utcid04() {
        ExamSource s = manual(Track.TOPIK_I);
        imports.saveCard(s.getId(), choiceCard(null, "I-D1", 32, ""), CONTENT);
        CardView card = imports.cards(s.getId()).get(0);
        assertFalse(card.ready());
    }

    @Test @DisplayName("UTCID05 A: chưa chọn dạng câu (không có typeCode, templateCode)")
    void utcid05() {
        ExamSource s = manual(Track.TOPIK_I);
        expectError("Chọn dạng câu trước khi lưu.", () -> imports.saveCard(s.getId(), choiceCard(null, null, 31, "gt"), CONTENT));
    }

    @Test @DisplayName("UTCID06 A: dạng II-N6 (TOPIK II) nhập vào nguồn TOPIK I")
    void utcid06() {
        ExamSource s = manual(Track.TOPIK_I);
        expectError("Khuôn TPL2-LIS-06 không thuộc " + Track.TOPIK_I.getLabel() + ".",
                () -> imports.saveCard(s.getId(), grouped(List.of(21, 22)), CONTENT));
    }

    @Test @DisplayName("UTCID07 A: bài dùng chung II-N6 chỉ chọn ô 21")
    void utcid07() {
        ExamSource s = manual(Track.TOPIK_II);
        expectError("Câu dùng chung phải chọn đủ các ô [21, 22].",
                () -> imports.saveCard(s.getId(), grouped(List.of(21)), CONTENT));
    }

    @Test @DisplayName("UTCID08 A: dạng I-D1 nhưng chọn ô 40")
    void utcid08() {
        ExamSource s = manual(Track.TOPIK_I);
        expectError("Ô 40 không thuộc dạng I-D1.", () -> imports.saveCard(s.getId(), readingCard(40), CONTENT));
    }

    @Test @DisplayName("UTCID09 A: ô 31 đã có bài khác → báo lỗi, tờ đề vẫn 1 bài")
    void utcid09() {
        ExamSource s = manual(Track.TOPIK_I);
        imports.saveCard(s.getId(), readingCard(31), CONTENT);
        expectError("Câu 31 đã có nội dung. Bấm ô đó để sửa.", () -> imports.saveCard(s.getId(), readingCard(31), CONTENT));
        assertEquals(1, imports.cards(s.getId()).size());
    }

    @Test @DisplayName("UTCID10 A: khuôn của dạng đang ngừng dùng")
    void utcid10() {
        ExamSource s = manual(Track.TOPIK_I);
        QuestionTemplate t = templates.findByCode("TPL1-REA-01").orElseThrow();
        t.setStatus(TemplateStatus.INACTIVE);
        templates.save(t);
        expectError("Khuôn TPL1-REA-01 đang không dùng.", () -> imports.saveCard(s.getId(), readingCard(31), CONTENT));
    }

    @Test @DisplayName("UTCID11 A: sửa bài không còn trên tờ đề (cardId b9)")
    void utcid11() {
        ExamSource s = manual(Track.TOPIK_I);
        expectError("Không còn bài này trên tờ đề.", () -> imports.saveCard(s.getId(), choiceCard("b9", "I-D1", 31, "gt"), CONTENT));
    }

    @Test @DisplayName("UTCID12 A: nguồn chưa mở tờ nhập tay")
    void utcid12() {
        ExamSource s = newSource(Track.TOPIK_I);
        expectError("Chưa có tờ đề. Hãy mở trang nhập.", () -> imports.saveCard(s.getId(), readingCard(31), CONTENT));
    }
}
