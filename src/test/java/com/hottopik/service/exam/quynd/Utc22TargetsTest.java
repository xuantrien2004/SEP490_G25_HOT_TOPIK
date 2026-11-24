package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.Track;
import com.hottopik.service.exam.ExamDomainException;
import com.hottopik.service.exam.PlacementRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UTC22 – PlacementRules.targets(int current, Track track) và trackForTarget(int target):
 * danh sách cấp mục tiêu được chọn theo trình độ hiện tại và lộ trình. Hàm thuần, không cần Spring.
 */
class Utc22TargetsTest {

    @Test @DisplayName("UTCID01 N: chưa có cấp (0), TOPIK I → mục tiêu 1, 2")
    void utcid01() {
        assertEquals(List.of(1, 2), PlacementRules.targets(0, Track.TOPIK_I));
    }

    @Test @DisplayName("UTCID02 N: cấp 1, TOPIK I → chỉ còn mục tiêu 2")
    void utcid02() {
        assertEquals(List.of(2), PlacementRules.targets(1, Track.TOPIK_I));
    }

    @Test @DisplayName("UTCID03 N: cấp 2, TOPIK II → mục tiêu 3, 4, 5, 6")
    void utcid03() {
        assertEquals(List.of(3, 4, 5, 6), PlacementRules.targets(2, Track.TOPIK_II));
    }

    @Test @DisplayName("UTCID04 N: cấp 4, TOPIK II → mục tiêu 5, 6")
    void utcid04() {
        assertEquals(List.of(5, 6), PlacementRules.targets(4, Track.TOPIK_II));
    }

    @Test @DisplayName("UTCID05 B: đã ở cấp cao nhất của TOPIK I (2) → giữ mục tiêu 2")
    void utcid05() {
        assertEquals(List.of(2), PlacementRules.targets(2, Track.TOPIK_I));
    }

    @Test @DisplayName("UTCID06 B: đã ở cấp 6 → giữ mục tiêu 6")
    void utcid06() {
        assertEquals(List.of(6), PlacementRules.targets(6, Track.TOPIK_II));
    }

    @Test @DisplayName("UTCID07 A: cấp âm (-1), TOPIK I → coi như chưa có cấp: 1, 2")
    void utcid07() {
        assertEquals(List.of(1, 2), PlacementRules.targets(-1, Track.TOPIK_I));
    }

    @Test @DisplayName("UTCID08 N: trackForTarget: mục tiêu 1, 2 → TOPIK I; 3, 6 → TOPIK II")
    void utcid08() {
        assertEquals(Track.TOPIK_I, PlacementRules.trackForTarget(1));
        assertEquals(Track.TOPIK_I, PlacementRules.trackForTarget(2));
        assertEquals(Track.TOPIK_II, PlacementRules.trackForTarget(3));
        assertEquals(Track.TOPIK_II, PlacementRules.trackForTarget(6));
    }

    @Test @DisplayName("UTCID09 A: trackForTarget(0)")
    void utcid09() {
        ExamDomainException e = assertThrows(ExamDomainException.class, () -> PlacementRules.trackForTarget(0));
        assertEquals("Mục tiêu phải từ TOPIK 1 đến TOPIK 6.", e.getMessage());
    }

    @Test @DisplayName("UTCID10 A: trackForTarget(7)")
    void utcid10() {
        ExamDomainException e = assertThrows(ExamDomainException.class, () -> PlacementRules.trackForTarget(7));
        assertEquals("Mục tiêu phải từ TOPIK 1 đến TOPIK 6.", e.getMessage());
    }
}
