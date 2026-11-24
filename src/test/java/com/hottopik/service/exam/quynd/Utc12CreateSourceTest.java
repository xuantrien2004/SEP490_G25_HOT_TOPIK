package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.ExamSource;
import com.hottopik.entity.exam.SourceStatus;
import com.hottopik.entity.exam.Track;
import com.hottopik.service.exam.ExamImportService.SourceForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** UTC12 – ExamImportService.createSource(SourceForm f, Actor actor): tạo nguồn đề (bộ đề) mới. */
class Utc12CreateSourceTest extends QuyndTestSupport {

    private static SourceForm form(String name, Track track, Integer year, Integer expected, boolean assessment, String copyright) {
        return new SourceForm(name, track, year, 1, expected, assessment, copyright, "ghi chú");
    }

    @Test @DisplayName("UTCID01 N: TOPIK I hợp lệ, không ghi số câu → mặc định 70, trạng thái DRAFT")
    void utcid01() {
        ExamSource s = imports.createSource(form("Đề TOPIK I kỳ 91", Track.TOPIK_I, 2024, null, false, "Đề mẫu nội bộ"), CONTENT);
        assertNotNull(s.getId());
        assertEquals(70, s.getExpectedCount());
        assertEquals(SourceStatus.DRAFT, s.getStatus());
        assertEquals("Lê Thu Hà", s.getCreatedBy());
        assertEquals("CREATE_EXAM_SOURCE", lastAudit().getAction());
        assertEquals("Lê Thu Hà", lastAudit().getActorName());
    }

    @Test @DisplayName("UTCID02 N: TOPIK II, 104 câu, là bài đánh giá đầu vào")
    void utcid02() {
        ExamSource s = imports.createSource(form("Đề TOPIK II kỳ 83", Track.TOPIK_II, 2022, 104, true, "Đề mẫu nội bộ"), CONTENT);
        assertEquals(104, s.getExpectedCount());
        assertTrue(s.isAssessment());
        assertEquals(Track.TOPIK_II, s.getTrack());
    }

    @Test @DisplayName("UTCID03 B: tên đúng 3 ký tự, số câu dự kiến = 1")
    void utcid03() {
        ExamSource s = imports.createSource(form("ABC", Track.TOPIK_I, null, 1, false, "12345"), CONTENT);
        assertEquals("ABC", s.getName());
        assertEquals(1, s.getExpectedCount());
    }

    @Test @DisplayName("UTCID04 B: tên đúng 200 ký tự, năm 2100, ghi chú bản quyền đúng 5 ký tự")
    void utcid04() {
        ExamSource s = imports.createSource(form("A".repeat(200), Track.TOPIK_II, 2100, null, false, "abcde"), CONTENT);
        assertEquals(200, s.getName().length());
        assertEquals(2100, s.getYear());
    }

    @Test @DisplayName("UTCID05 A: Admin tạo nguồn đề bị chặn")
    void utcid05() {
        expectError("Chỉ Content Manager được nhập đề (Admin chỉ xem).",
                () -> imports.createSource(form("Đề TOPIK I", Track.TOPIK_I, 2024, null, false, "Đề mẫu nội bộ"), ADMIN));
    }

    @Test @DisplayName("UTCID06 A: tên 2 ký tự → báo lỗi, không tạo nguồn, không ghi nhật ký")
    void utcid06() {
        long sourcesBefore = imports.listSources().size();
        int logsBefore = auditCount();
        expectError("Tên nguồn đề dài từ 3 đến 200 ký tự.",
                () -> imports.createSource(form("AB", Track.TOPIK_I, 2024, null, false, "Đề mẫu nội bộ"), CONTENT));
        assertEquals(sourcesBefore, imports.listSources().size());
        assertEquals(logsBefore, auditCount());
    }

    @Test @DisplayName("UTCID07 A: chưa chọn track")
    void utcid07() {
        expectError("Chọn track TOPIK I hoặc TOPIK II.",
                () -> imports.createSource(form("Đề không track", null, 2024, null, false, "Đề mẫu nội bộ"), CONTENT));
    }

    @Test @DisplayName("UTCID08 A: TOPIK I nhưng số câu dự kiến 71")
    void utcid08() {
        expectError("Số câu dự kiến của " + Track.TOPIK_I.getLabel() + " từ 1 đến 70.",
                () -> imports.createSource(form("Đề TOPIK I", Track.TOPIK_I, 2024, 71, false, "Đề mẫu nội bộ"), CONTENT));
    }

    @Test @DisplayName("UTCID09 A: năm 1996")
    void utcid09() {
        expectError("Năm không hợp lệ.",
                () -> imports.createSource(form("Đề TOPIK I", Track.TOPIK_I, 1996, null, false, "Đề mẫu nội bộ"), CONTENT));
    }

    @Test @DisplayName("UTCID10 A: ghi chú bản quyền 4 ký tự")
    void utcid10() {
        expectError("Ghi chú bản quyền là bắt buộc (nguồn, quyền sử dụng), ít nhất 5 ký tự.",
                () -> imports.createSource(form("Đề TOPIK I", Track.TOPIK_I, 2024, null, false, "abcd"), CONTENT));
    }
}
