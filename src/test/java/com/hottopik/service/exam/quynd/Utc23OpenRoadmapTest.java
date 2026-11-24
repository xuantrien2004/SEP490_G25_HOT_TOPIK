package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.ProgramStatus;
import com.hottopik.entity.exam.ProgramVersion;
import com.hottopik.entity.exam.StudyPlan;
import com.hottopik.entity.exam.Track;
import com.hottopik.repository.exam.ProgramVersionRepository;
import com.hottopik.repository.exam.StudyPlanRepository;
import com.hottopik.service.exam.LearnerRoadmapService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** UTC23 – LearnerRoadmapService.open(String owner, Track track, int days, int minutes): tạo (hoặc tạo lại) lộ trình. */
class Utc23OpenRoadmapTest extends QuyndTestSupport {

    static final String OWNER = "qa.roadmap@example.com";

    @Autowired LearnerRoadmapService roadmaps;
    @Autowired StudyPlanRepository plans;
    @Autowired ProgramVersionRepository programs;

    @Test @DisplayName("UTCID01 N: TOPIK I, 90 ngày, 20 phút → lịch bắt đầu hôm nay theo bản đang phát hành")
    void utcid01() {
        StudyPlan p = roadmaps.open(OWNER, Track.TOPIK_I, 90, 20);
        assertEquals(Track.TOPIK_I, p.getTrack());
        assertEquals(90, p.getTotalDays());
        assertEquals(20, p.getMinutesPerDay());
        assertEquals(LocalDate.now(), p.getStartDate());
        assertEquals(roadmaps.published().getVersionCode(), p.getVersionCode());
        assertNull(p.getDoneKeys());
        assertEquals(roadmaps.preview(Track.TOPIK_I, 90).baseQuota(), p.getItemsPerDay());
    }

    @Test @DisplayName("UTCID02 N: tạo lại → vẫn một lịch, đổi sang TOPIK II, tiến độ cũ bị xóa")
    void utcid02() {
        roadmaps.open(OWNER, Track.TOPIK_I, 90, 20);
        roadmaps.setDone(OWNER, 1, 0, true);
        StudyPlan p = roadmaps.open(OWNER, Track.TOPIK_II, 120, 30);
        assertEquals(Track.TOPIK_II, p.getTrack());
        assertEquals(120, p.getTotalDays());
        assertNull(p.getDoneKeys());
        assertEquals(1, plans.findAll().stream().filter(x -> OWNER.equals(x.getOwnerEmail())).count());
    }

    @Test @DisplayName("UTCID03 B: 31 ngày (mức thấp nhất)")
    void utcid03() {
        assertEquals(31, roadmaps.open(OWNER, Track.TOPIK_I, 31, 20).getTotalDays());
    }

    @Test @DisplayName("UTCID04 B: 200 ngày (mức cao nhất)")
    void utcid04() {
        assertEquals(200, roadmaps.open(OWNER, Track.TOPIK_II, 200, 20).getTotalDays());
    }

    @Test @DisplayName("UTCID05 B: 10 phút và 60 phút (hai biên số phút)")
    void utcid05() {
        assertEquals(10, roadmaps.open(OWNER, Track.TOPIK_I, 90, 10).getMinutesPerDay());
        assertEquals(60, roadmaps.open(OWNER, Track.TOPIK_I, 90, 60).getMinutesPerDay());
    }

    @Test @DisplayName("UTCID06 A: 30 ngày")
    void utcid06() {
        expectError("Lộ trình từ 31 đến 200 ngày.", () -> roadmaps.open(OWNER, Track.TOPIK_I, 30, 20));
        assertTrue(plans.findByOwnerEmail(OWNER).isEmpty());
    }

    @Test @DisplayName("UTCID07 A: 201 ngày")
    void utcid07() {
        expectError("Lộ trình từ 31 đến 200 ngày.", () -> roadmaps.open(OWNER, Track.TOPIK_I, 201, 20));
    }

    @Test @DisplayName("UTCID08 A: 25 phút (không thuộc 10, 15, 20, 30, 45, 60)")
    void utcid08() {
        expectError("Chọn 10, 15, 20, 30, 45 hoặc 60 phút mỗi ngày.", () -> roadmaps.open(OWNER, Track.TOPIK_I, 90, 25));
    }

    @Test @DisplayName("UTCID09 A: không xác định được học viên (owner rỗng)")
    void utcid09() {
        expectError("Chưa xác định được tài khoản học viên.", () -> roadmaps.open(" ", Track.TOPIK_I, 90, 20));
    }

    @Test @DisplayName("UTCID10 A: Content Manager chưa phát hành bản lộ trình nào")
    void utcid10() {
        for (ProgramVersion v : programs.findByStatus(ProgramStatus.PUBLISHED)) {
            v.setStatus(ProgramStatus.ARCHIVED);
            programs.save(v);
        }
        expectError("Content Manager chưa phát hành lộ trình. Học viên chưa tạo được lịch.",
                () -> roadmaps.open(OWNER, Track.TOPIK_I, 90, 20));
    }
}
