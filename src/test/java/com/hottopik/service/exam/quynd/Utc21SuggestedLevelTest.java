package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.Track;
import com.hottopik.service.exam.PlacementRules;
import com.hottopik.service.exam.PlacementRules.Paper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UTC21 – PlacementRules.suggestedLevel(Paper paper, int scorePoints) và suggestedTrack(int level):
 * quy đổi điểm Placement thành mức trình độ và lộ trình gợi ý. Hàm thuần, không cần Spring.
 */
class Utc21SuggestedLevelTest {

    private static int level(int points) {
        return PlacementRules.suggestedLevel(Paper.TOPIK_I, points);
    }

    @Test @DisplayName("UTCID01 N: 50 điểm → mức 0 (chưa đạt cấp 1), gợi ý TOPIK I")
    void utcid01() {
        assertEquals(0, level(50));
        assertEquals(Track.TOPIK_I, PlacementRules.suggestedTrack(level(50)));
    }

    @Test @DisplayName("UTCID02 N: 110 điểm → mức 1, gợi ý TOPIK I")
    void utcid02() {
        assertEquals(1, level(110));
        assertEquals(Track.TOPIK_I, PlacementRules.suggestedTrack(level(110)));
    }

    @Test @DisplayName("UTCID03 N: 170 điểm → mức 2, gợi ý TOPIK II")
    void utcid03() {
        assertEquals(2, level(170));
        assertEquals(Track.TOPIK_II, PlacementRules.suggestedTrack(level(170)));
    }

    @Test @DisplayName("UTCID04 B: 79 điểm (ngay dưới mốc 80) → mức 0")
    void utcid04() {
        assertEquals(0, level(79));
    }

    @Test @DisplayName("UTCID05 B: 80 điểm (mốc dưới cấp 1) → mức 1")
    void utcid05() {
        assertEquals(1, level(80));
    }

    @Test @DisplayName("UTCID06 B: 139 điểm (ngay dưới mốc 140) → mức 1")
    void utcid06() {
        assertEquals(1, level(139));
    }

    @Test @DisplayName("UTCID07 B: 140 điểm (mốc cấp 2) → mức 2")
    void utcid07() {
        assertEquals(2, level(140));
    }

    @Test @DisplayName("UTCID08 B: 0 điểm và 200 điểm (hai đầu thang điểm)")
    void utcid08() {
        assertEquals(0, level(0));
        assertEquals(2, level(200));
    }

    @Test @DisplayName("UTCID09 A: điểm âm (-10) → mức 0, không lỗi")
    void utcid09() {
        assertEquals(0, level(-10));
    }

    @Test @DisplayName("UTCID10 N: điểm từ số câu đúng: 14 câu → 140 điểm → 70% → mức 2")
    void utcid10() {
        int points = PlacementRules.points(14);
        assertEquals(140, points);
        assertEquals(70, PlacementRules.percent(points));
        assertEquals(2, level(points));
    }
}
