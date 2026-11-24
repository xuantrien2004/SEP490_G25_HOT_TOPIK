package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.ExamSource;
import com.hottopik.entity.exam.ImportJob;
import com.hottopik.entity.exam.ImportJobStatus;
import com.hottopik.entity.exam.SourceStatus;
import com.hottopik.entity.exam.Track;
import com.hottopik.repository.exam.ExamSourceRepository;
import com.hottopik.repository.exam.ImportJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/** UTC13 – ExamImportService.startManual(Long sourceId, Actor actor): mở tờ nhập đề trên web. */
class Utc13StartManualTest extends QuyndTestSupport {

    @Autowired ExamSourceRepository sources;
    @Autowired ImportJobRepository jobs;

    @Test @DisplayName("UTCID01 N: nguồn nháp TOPIK I → tạo tờ nhập tay, chưa có câu nên còn lỗi")
    void utcid01() {
        ExamSource s = newSource(Track.TOPIK_I);
        ImportJob job = imports.startManual(s.getId(), CONTENT);
        assertTrue(imports.isManual(job));
        assertEquals("Nhập trên web", job.getFileName());
        assertEquals(ImportJobStatus.HAS_ERRORS, job.getStatus());
        assertEquals("MANUAL_EXAM_DRAFT", lastAudit().getAction());
    }

    @Test @DisplayName("UTCID02 N: mở lại lần hai → dùng lại đúng tờ cũ, không tạo tờ mới")
    void utcid02() {
        ExamSource s = newSource(Track.TOPIK_I);
        ImportJob first = imports.startManual(s.getId(), CONTENT);
        ImportJob second = imports.startManual(s.getId(), CONTENT);
        assertEquals(first.getId(), second.getId());
        assertEquals(1, jobs.findBySourceIdOrderByIdDesc(s.getId()).size());
    }

    @Test @DisplayName("UTCID03 N: nguồn TOPIK II → tờ nhập tay được tạo")
    void utcid03() {
        ExamSource s = newSource(Track.TOPIK_II);
        assertTrue(imports.isManual(imports.startManual(s.getId(), CONTENT)));
    }

    @Test @DisplayName("UTCID04 B: tờ trước đã SUPERSEDED → tạo tờ mới")
    void utcid04() {
        ExamSource s = newSource(Track.TOPIK_I);
        ImportJob old = imports.startManual(s.getId(), CONTENT);
        old.setStatus(ImportJobStatus.SUPERSEDED);
        jobs.save(old);
        ImportJob fresh = imports.startManual(s.getId(), CONTENT);
        assertNotEquals(old.getId(), fresh.getId());
    }

    @Test @DisplayName("UTCID05 A: Admin mở tờ nhập bị chặn")
    void utcid05() {
        ExamSource s = newSource(Track.TOPIK_I);
        expectError("Chỉ Content Manager được nhập đề (Admin chỉ xem).", () -> imports.startManual(s.getId(), ADMIN));
    }

    @Test @DisplayName("UTCID06 A: nguồn đã kích hoạt")
    void utcid06() {
        ExamSource s = newSource(Track.TOPIK_I);
        s.setStatus(SourceStatus.ACTIVE);
        sources.save(s);
        expectError("Nguồn đề đã kích hoạt, không thay file được nữa. Sửa từng câu trong Ngân hàng câu hỏi.",
                () -> imports.startManual(s.getId(), CONTENT));
    }

    @Test @DisplayName("UTCID07 A: mã nguồn không tồn tại")
    void utcid07() {
        expectError("Không tìm thấy nguồn đề #999999.", () -> imports.startManual(999999L, CONTENT));
    }
}
