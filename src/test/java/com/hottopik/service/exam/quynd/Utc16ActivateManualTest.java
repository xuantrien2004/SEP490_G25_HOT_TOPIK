package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.ExamSource;
import com.hottopik.entity.exam.ImportJobStatus;
import com.hottopik.entity.exam.SourceStatus;
import com.hottopik.entity.exam.Track;
import com.hottopik.service.exam.ExamImportService.ActivationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/** UTC16 – ExamImportService.activate(Long sourceId, Actor actor): đưa tờ nhập tay vào ngân hàng câu hỏi. */
class Utc16ActivateManualTest extends QuyndTestSupport {


    private ExamSource manual(Track track) {
        ExamSource s = newSource(track);
        imports.startManual(s.getId(), CONTENT);
        return s;
    }

    @Test @DisplayName("UTCID01 N: TOPIK I, 1 câu Đọc hợp lệ → 1 câu vào ngân hàng, nguồn ACTIVE, tạo đề nháp")
    void utcid01() {
        ExamSource s = manual(Track.TOPIK_I);
        imports.saveCard(s.getId(), readingCard(31), CONTENT);
        ActivationResult r = imports.activate(s.getId(), CONTENT);
        assertEquals(1, r.questionCount());
        assertEquals(0, r.writingCount());
        assertNotNull(r.examId());
        assertEquals(SourceStatus.ACTIVE, imports.getSource(s.getId()).getStatus());
        assertEquals(ImportJobStatus.ACTIVATED, imports.latestJob(s.getId()).orElseThrow().getStatus());
        assertEquals(1, imports.questionsOf(s.getId()).size());
        assertEquals("ACTIVATE_EXAM_SOURCE", lastAudit().getAction());
    }

    @Test @DisplayName("UTCID02 N: TOPIK II, 1 câu Đọc + 1 đề Viết 51 có đoạn văn → 1 câu, 1 đề Viết")
    void utcid02() {
        ExamSource s = manual(Track.TOPIK_II);
        imports.saveCard(s.getId(), choiceCard(null, "II-D1", 1, "giải thích"), CONTENT);
        imports.saveCard(s.getId(), writing51("안내문: 다음 주 금요일에 ( ㉠ ). 참가를 원하시는 분은 ( ㉡ )."), CONTENT);
        ActivationResult r = imports.activate(s.getId(), CONTENT);
        assertEquals(1, r.questionCount());
        assertEquals(1, r.writingCount());
        assertEquals(1, imports.writingOf(s.getId()).size());
    }

    @Test @DisplayName("UTCID03 B: 3 câu I-D1 (ô 31, 32, 33 – đủ cả dạng) → 3 câu vào ngân hàng")
    void utcid03() {
        ExamSource s = manual(Track.TOPIK_I);
        for (int no : new int[]{31, 32, 33}) imports.saveCard(s.getId(), readingCard(no), CONTENT);
        assertEquals(3, imports.activate(s.getId(), CONTENT).questionCount());
    }

    @Test @DisplayName("UTCID04 A: kích hoạt lần thứ hai")
    void utcid04() {
        ExamSource s = manual(Track.TOPIK_I);
        imports.saveCard(s.getId(), readingCard(31), CONTENT);
        imports.activate(s.getId(), CONTENT);
        expectError("Nguồn đề đã được đưa vào ngân hàng. Không thể kích hoạt lần nữa.", () -> imports.activate(s.getId(), CONTENT));
    }

    @Test @DisplayName("UTCID05 A: câu thiếu giải thích → không kích hoạt, nguồn vẫn DRAFT")
    void utcid05() {
        ExamSource s = manual(Track.TOPIK_I);
        imports.saveCard(s.getId(), choiceCard(null, "I-D1", 31, ""), CONTENT);
        expectError("Còn lỗi, chưa kích hoạt được (1 lỗi).", () -> imports.activate(s.getId(), CONTENT));
        assertEquals(SourceStatus.DRAFT, imports.getSource(s.getId()).getStatus());
    }

    @Test @DisplayName("UTCID06 A: tờ nhập tay chưa có câu nào")
    void utcid06() {
        ExamSource s = manual(Track.TOPIK_I);
        expectError("Còn lỗi, chưa kích hoạt được (1 lỗi).", () -> imports.activate(s.getId(), CONTENT));
    }

    @Test @DisplayName("UTCID07 A: đề Viết 51 thiếu đoạn văn dùng chung")
    void utcid07() {
        ExamSource s = manual(Track.TOPIK_II);
        imports.saveCard(s.getId(), writing51(null), CONTENT);
        expectError("Còn lỗi, chưa kích hoạt được (1 lỗi).", () -> imports.activate(s.getId(), CONTENT));
    }

    @Test @DisplayName("UTCID08 A: nguồn chưa có nội dung đề (chưa mở tờ, chưa tải Excel)")
    void utcid08() {
        ExamSource s = newSource(Track.TOPIK_I);
        expectError("Chưa có nội dung đề. Hãy nhập trên web hoặc tải Excel.", () -> imports.activate(s.getId(), CONTENT));
    }

    @Test @DisplayName("UTCID09 A: Admin kích hoạt")
    void utcid09() {
        ExamSource s = manual(Track.TOPIK_I);
        imports.saveCard(s.getId(), readingCard(31), CONTENT);
        expectError("Chỉ Content Manager được nhập đề (Admin chỉ xem).", () -> imports.activate(s.getId(), ADMIN));
    }

    @Test @DisplayName("UTCID10 A: mã nguồn không tồn tại (999999)")
    void utcid10() {
        expectError("Không tìm thấy nguồn đề #999999.", () -> imports.activate(999999L, CONTENT));
    }
}
