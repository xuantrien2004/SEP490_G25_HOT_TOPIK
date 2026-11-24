package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.Track;
import com.hottopik.repository.exam.StudyPlanRepository;
import com.hottopik.service.exam.LearnerRoadmapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** UTC26 – LearnerRoadmapService.setDone(String owner, int day, int slot, boolean done): đánh dấu / bỏ đánh dấu một bài trên lịch. */
class Utc26SetDoneTest extends QuyndTestSupport {

    static final String OWNER = "qa.done@example.com";

    @Autowired LearnerRoadmapService roadmaps;
    @Autowired StudyPlanRepository plans;

    @BeforeEach
    void plan() {
        roadmaps.open(OWNER, Track.TOPIK_I, 90, 20);
    }

    private String doneKeys() {
        return plans.findByOwnerEmail(OWNER).orElseThrow().getDoneKeys();
    }

    @Test @DisplayName("UTCID01 N: đánh dấu ngày 1 ô 0 xong → doneKeys = \"1:0\"")
    void utcid01() {
        assertEquals(List.of("1:0"), roadmaps.setDone(OWNER, 1, 0, true));
        assertEquals("1:0", doneKeys());
    }

    @Test @DisplayName("UTCID02 N: bỏ đánh dấu → doneKeys = null")
    void utcid02() {
        roadmaps.setDone(OWNER, 1, 0, true);
        assertTrue(roadmaps.setDone(OWNER, 1, 0, false).isEmpty());
        assertNull(doneKeys());
    }

    @Test @DisplayName("UTCID03 N: đánh dấu cùng ô hai lần → không bị trùng")
    void utcid03() {
        roadmaps.setDone(OWNER, 1, 0, true);
        assertEquals(List.of("1:0"), roadmaps.setDone(OWNER, 1, 0, true));
    }

    @Test @DisplayName("UTCID04 N: đánh dấu hai ngày khác nhau → giữ cả hai")
    void utcid04() {
        roadmaps.setDone(OWNER, 1, 0, true);
        assertEquals(List.of("1:0", "2:0"), roadmaps.setDone(OWNER, 2, 0, true));
    }

    @Test @DisplayName("UTCID05 B: ngày cuối cùng (ngày 90, ô Full test) → đánh dấu được")
    void utcid05() {
        assertTrue(roadmaps.setDone(OWNER, 90, 0, true).contains("90:0"));
    }

    @Test @DisplayName("UTCID06 A: ngày 0")
    void utcid06() {
        expectError("Không có ngày này trong lộ trình.", () -> roadmaps.setDone(OWNER, 0, 0, true));
    }

    @Test @DisplayName("UTCID07 A: ngày 91 (sau ngày cuối)")
    void utcid07() {
        expectError("Không có ngày này trong lộ trình.", () -> roadmaps.setDone(OWNER, 91, 0, true));
    }

    @Test @DisplayName("UTCID08 A: ô 99 không có trong ngày 1")
    void utcid08() {
        expectError("Ngày này không có bài ở vị trí đó.", () -> roadmaps.setDone(OWNER, 1, 99, true));
    }

    @Test @DisplayName("UTCID09 A: ô -1")
    void utcid09() {
        expectError("Ngày này không có bài ở vị trí đó.", () -> roadmaps.setDone(OWNER, 1, -1, true));
    }

    @Test @DisplayName("UTCID10 A: học viên chưa có lộ trình")
    void utcid10() {
        expectError("Bạn chưa có lộ trình.", () -> roadmaps.setDone("nobody@example.com", 1, 0, true));
    }
}
