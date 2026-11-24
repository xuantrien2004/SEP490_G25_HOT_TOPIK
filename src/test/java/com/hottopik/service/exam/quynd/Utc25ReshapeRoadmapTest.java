package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.StudyPlan;
import com.hottopik.entity.exam.Track;
import com.hottopik.repository.exam.StudyPlanRepository;
import com.hottopik.service.exam.LearnerRoadmapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** UTC25 – LearnerRoadmapService.reshape(String owner, Track track, int days, int minutes): điều chỉnh lộ trình đang học. */
class Utc25ReshapeRoadmapTest extends QuyndTestSupport {

    static final String OWNER = "qa.reshape@example.com";

    @Autowired LearnerRoadmapService roadmaps;
    @Autowired StudyPlanRepository plans;

    private LocalDate start;

    @BeforeEach
    void plan() {
        StudyPlan p = roadmaps.open(OWNER, Track.TOPIK_II, 90, 20);
        p.setStartDate(LocalDate.now().minusDays(2));
        plans.save(p);
        start = p.getStartDate();
        roadmaps.setDone(OWNER, 1, 0, true);
    }

    @Test @DisplayName("UTCID01 N: 90 → 120 ngày cùng TOPIK II → giữ bài đã học, ngày bắt đầu không đổi")
    void utcid01() {
        StudyPlan p = roadmaps.reshape(OWNER, Track.TOPIK_II, 120, 20);
        assertEquals(120, p.getTotalDays());
        assertEquals(start, p.getStartDate());
        assertNotNull(p.getDoneKeys());
        assertTrue(p.getDoneKeys().contains("1:0"));
    }

    @Test @DisplayName("UTCID02 N: chỉ đổi số phút 20 → 45")
    void utcid02() {
        StudyPlan p = roadmaps.reshape(OWNER, Track.TOPIK_II, 90, 45);
        assertEquals(45, p.getMinutesPerDay());
        assertEquals(90, p.getTotalDays());
        assertTrue(p.getDoneKeys().contains("1:0"));
    }

    @Test @DisplayName("UTCID03 N: đổi TOPIK II → TOPIK I → bài TOPIK II đã học không còn trên lịch mới")
    void utcid03() {
        StudyPlan p = roadmaps.reshape(OWNER, Track.TOPIK_I, 90, 20);
        assertEquals(Track.TOPIK_I, p.getTrack());
        assertNull(p.getDoneKeys());
        assertEquals(start, p.getStartDate());
    }

    @Test @DisplayName("UTCID04 B: thu về 31 ngày (mức thấp nhất)")
    void utcid04() {
        assertEquals(31, roadmaps.reshape(OWNER, Track.TOPIK_II, 31, 20).getTotalDays());
    }

    @Test @DisplayName("UTCID05 B: kéo dài 200 ngày (mức cao nhất)")
    void utcid05() {
        assertEquals(200, roadmaps.reshape(OWNER, Track.TOPIK_II, 200, 20).getTotalDays());
    }

    @Test @DisplayName("UTCID06 A: học viên chưa có lộ trình")
    void utcid06() {
        expectError("Bạn chưa có lộ trình để điều chỉnh.", () -> roadmaps.reshape("nobody@example.com", Track.TOPIK_II, 90, 20));
    }

    @Test @DisplayName("UTCID07 A: số phút 0 → báo lỗi, lịch giữ nguyên")
    void utcid07() {
        expectError("Chọn 10, 15, 20, 30, 45 hoặc 60 phút mỗi ngày.", () -> roadmaps.reshape(OWNER, Track.TOPIK_II, 90, 0));
        assertEquals(20, plans.findByOwnerEmail(OWNER).orElseThrow().getMinutesPerDay());
    }

    @Test @DisplayName("UTCID08 A: 30 ngày")
    void utcid08() {
        expectError("Lộ trình từ 31 đến 200 ngày.", () -> roadmaps.reshape(OWNER, Track.TOPIK_II, 30, 20));
        assertEquals(90, plans.findByOwnerEmail(OWNER).orElseThrow().getTotalDays());
    }
}
