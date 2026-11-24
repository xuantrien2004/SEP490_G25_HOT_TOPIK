package com.hottopik.service.exam.quynd;

import com.hottopik.service.exam.ExamDomainException;
import com.hottopik.service.exam.RoadmapSketch;
import com.hottopik.service.exam.RoadmapSketch.DayPlan;
import com.hottopik.service.exam.RoadmapSketch.Result;
import com.hottopik.service.exam.RoadmapSketch.Slot;
import com.hottopik.service.exam.RoadmapSketch.TypeRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UTC24 – RoadmapSketch.build(List&lt;TypeRef&gt; types, int totalDays, int[] reviewGaps, int sessionsPerType, int[] phaseSplit):
 * xếp lịch học theo ngày (bài mới, bài ôn +2/+7/+14, ba chặng, năm ngày thi cuối). Hàm thuần, không cần Spring.
 */
class Utc24BuildScheduleTest {

    private static final int[] GAPS = {2, 7, 14};
    private static final int[] PHASES = {40, 40, 20};

    private static List<TypeRef> types(int n) {
        List<TypeRef> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            String code = (i % 2 == 1 ? "TPL1-LIS-" : "TPL1-REA-") + String.format("%02d", i);
            list.add(new TypeRef(code, "Dạng " + i));
        }
        return list;
    }

    private static List<String> kinds(Result r, int day) {
        return r.days().get(day - 1).slots().stream().map(Slot::kind).toList();
    }

    @Test @DisplayName("UTCID01 N: 2 dạng, 31 ngày → đủ 31 ngày, mỗi dạng học đủ 4 lần, không dạng nào thiếu chỗ")
    void utcid01() {
        Result r = RoadmapSketch.build(types(2), 31, GAPS, 4, PHASES);
        assertEquals(31, r.days().size());
        assertEquals(2, r.newCount());
        assertTrue(r.unplaced().isEmpty());
        assertTrue(RoadmapSketch.incomplete(r, 4).isEmpty());
    }

    @Test @DisplayName("UTCID02 N: bài học ngày 1 có bài ôn đúng vào ngày 3, 8, 15 (mốc +2, +7, +14)")
    void utcid02() {
        Result r = RoadmapSketch.build(types(2), 31, GAPS, 4, PHASES);
        String first = r.days().get(0).slots().get(0).code();
        for (int day : new int[]{3, 8, 15}) {
            assertTrue(r.days().get(day - 1).slots().stream()
                    .anyMatch(s -> "REVIEW".equals(s.kind()) && first.equals(s.code())), "ngày " + day);
        }
    }

    @Test @DisplayName("UTCID03 N: năm ngày cuối là 4 Mini test rồi 1 Full test")
    void utcid03() {
        Result r = RoadmapSketch.build(types(4), 66, GAPS, 4, PHASES);
        assertEquals(List.of("MINI"), kinds(r, 62));
        assertEquals(List.of("MINI"), kinds(r, 65));
        assertEquals(List.of("FULL"), kinds(r, 66));
        assertEquals(4, r.miniCount());
        assertEquals(1, r.fullCount());
    }

    @Test @DisplayName("UTCID04 N: ba chặng Làm quen – Tăng tốc – Nước rút phủ kín toàn bộ số ngày")
    void utcid04() {
        Result r = RoadmapSketch.build(types(4), 90, GAPS, 4, PHASES);
        assertEquals(List.of("Làm quen", "Tăng tốc", "Nước rút"), r.phases().stream().map(RoadmapSketch.Phase::name).toList());
        assertEquals(90, r.phases().stream().mapToInt(RoadmapSketch.Phase::days).sum());
        assertEquals("Làm quen", r.days().get(0).phase());
    }

    @Test @DisplayName("UTCID05 N: mỗi dạng xuất hiện đúng 4 lần (1 bài mới + 3 bài ôn)")
    void utcid05() {
        Result r = RoadmapSketch.build(types(6), 90, GAPS, 4, PHASES);
        Map<String, Integer> count = new HashMap<>();
        for (DayPlan d : r.days()) for (Slot s : d.slots()) if (s.code() != null) count.merge(s.code(), 1, Integer::sum);
        assertEquals(6, count.size());
        count.values().forEach(v -> assertEquals(4, v));
    }

    @Test @DisplayName("UTCID06 B: đúng số ngày tối thiểu do minimumDays trả về → xếp được")
    void utcid06() {
        List<TypeRef> t = types(17);
        int least = RoadmapSketch.minimumDays(t, GAPS, 4, PHASES);
        Result r = RoadmapSketch.build(t, least, GAPS, 4, PHASES);
        assertEquals(least, r.days().size());
        assertTrue(RoadmapSketch.incomplete(r, 4).isEmpty());
    }

    @Test @DisplayName("UTCID07 B: 200 ngày (mức cao nhất) → xếp được")
    void utcid07() {
        assertEquals(200, RoadmapSketch.build(types(17), 200, GAPS, 4, PHASES).days().size());
    }

    @Test @DisplayName("UTCID08 A: mốc ôn +2/+7/+30, 31 ngày → mốc +30 rơi vào năm ngày thi, báo cần ít nhất N ngày")
    void utcid08() {
        List<TypeRef> t = types(2);
        int[] farGaps = {2, 7, 30};
        int least = RoadmapSketch.minimumDays(t, farGaps, 4, PHASES);
        assertTrue(least > 31, "mốc +30 cần hơn 31 ngày, thực tế " + least);
        ExamDomainException e = assertThrows(ExamDomainException.class,
                () -> RoadmapSketch.build(t, least - 1, farGaps, 4, PHASES));
        assertEquals("Với " + (least - 1) + " ngày, mốc ôn chưa kịp trước năm ngày thi. Cần ít nhất " + least + " ngày.", e.getMessage());
    }

    @Test @DisplayName("UTCID09 N: chặng Nước rút nằm cuối lịch, ngày thi cuối cùng thuộc Nước rút")
    void utcid09() {
        Result r = RoadmapSketch.build(types(4), 90, GAPS, 4, PHASES);
        assertEquals("Nước rút", r.days().get(89).phase());
        assertEquals("Nước rút", r.phases().get(r.phases().size() - 1).name());
    }

    @Test @DisplayName("UTCID10 B: chỉ 1 dạng, 31 ngày → dạng đó đủ 4 lần học")
    void utcid10() {
        Result r = RoadmapSketch.build(types(1), 31, GAPS, 4, PHASES);
        assertEquals(1, r.newCount());
        assertTrue(RoadmapSketch.incomplete(r, 4).isEmpty());
    }

    @Test @DisplayName("UTCID11 A: mốc ôn +200 → kéo đến 200 ngày vẫn không đủ chỗ")
    void utcid11() {
        ExamDomainException e = assertThrows(ExamDomainException.class,
                () -> RoadmapSketch.build(types(2), 90, new int[]{2, 7, 200}, 4, PHASES));
        assertEquals("Kéo đến 200 ngày vẫn chưa đủ chỗ cho 4 lần học mỗi dạng.", e.getMessage());
    }
}
