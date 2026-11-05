package com.hottopik.service.exam;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoadmapSketchTest {

    private static final int[] GAPS = {2, 7, 14};
    private static final int[] PHASES = {40, 40, 20};

    @Test
    void newLessonLocksTheExactReviewDaysAndTheLastFiveDaysAreTests() {
        List<RoadmapSketch.TypeRef> types = List.of(
                new RoadmapSketch.TypeRef("TPL-A", "Dạng A"),
                new RoadmapSketch.TypeRef("TPL-B", "Dạng B"));
        RoadmapSketch.Result result = RoadmapSketch.build(types, 31, GAPS, 4, PHASES);

        assertEquals(List.of("NEW"), kinds(result, 1));
        assertEquals("TPL-A", result.days().get(0).slots().get(0).code());
        assertEquals(List.of("NEW"), kinds(result, 2));
        assertEquals("TPL-B", result.days().get(1).slots().get(0).code());
        assertEquals(List.of("REVIEW"), kinds(result, 3));
        assertEquals(2, slot(result, 3, 0).repetition());
        assertEquals("TPL-A", slot(result, 3, 0).code());
        assertEquals(List.of("REVIEW"), kinds(result, 8));
        assertEquals(3, slot(result, 8, 0).repetition());
        assertTrue(kinds(result, 7).isEmpty());
        assertEquals(List.of("REVIEW"), kinds(result, 15));
        assertEquals(4, slot(result, 15, 0).repetition());
        assertEquals(List.of("MINI", "MINI", "MINI", "MINI", "FULL"), List.of(
                kind(result, 27), kind(result, 28), kind(result, 29), kind(result, 30), kind(result, 31)));
        assertEquals("Mini test Đọc", slot(result, 27, 0).name());
        assertEquals("Mini test Nghe", slot(result, 28, 0).name());
        assertEquals("Full test", slot(result, 31, 0).name());
        assertEquals(2, result.newCount());
        assertEquals(6, result.reviewCount());
        assertEquals(0, result.weeklyCount());
        assertEquals(4, result.miniCount());
        assertEquals(1, result.fullCount());
        assertEquals(1, result.baseQuota());
        assertTrue(result.unplaced().isEmpty());
        assertTrue(RoadmapSketch.incomplete(result, 4).isEmpty());
        assertEquals("Làm quen", result.days().get(0).phase());
        assertEquals(31, result.phases().stream().mapToInt(RoadmapSketch.Phase::days).sum());
        assertExactGaps(result);
    }

    @Test
    void listeningAndReadingAlternateInsteadOfFinishingOneSkillFirst() {
        List<RoadmapSketch.TypeRef> types = List.of(
                new RoadmapSketch.TypeRef("TPL1-LIS-01", "Nghe câu hỏi"),
                new RoadmapSketch.TypeRef("TPL1-LIS-02", "Nghe lời tiếp"),
                new RoadmapSketch.TypeRef("TPL1-REA-01", "Đọc câu ngắn"),
                new RoadmapSketch.TypeRef("TPL1-REA-02", "Đọc điền từ"));
        RoadmapSketch.Result result = RoadmapSketch.build(types, 66, GAPS, 4, PHASES);

        assertEquals("TPL1-LIS-01", slot(result, 1, 0).code());
        assertEquals("TPL1-REA-01", slot(result, 2, 0).code());
        assertEquals("TPL1-LIS-02", slot(result, 5, 0).code());
        assertEquals("TPL1-REA-02", slot(result, 6, 0).code());
        assertEquals(List.of("REVIEW", "REVIEW"), kinds(result, 8));
        assertEquals("TPL1-LIS-01", slot(result, 8, 0).code());
        assertEquals(3, slot(result, 8, 0).repetition());
        assertEquals("TPL1-REA-02", slot(result, 8, 1).code());
        assertEquals(2, slot(result, 8, 1).repetition());
        assertExactGaps(result);
    }

    @Test
    void topikCalendarsFollowTheSharedFormula() {
        RoadmapSketch.Result topikI = RoadmapSketch.build(topikI(), 66, GAPS, 4, PHASES);
        assertEquals(List.of(1, 2, 5, 6, 10, 11, 14, 22, 23, 26, 27, 31, 32, 35, 43, 44, 47), newsDays(topikI));
        assertEquals("Điền từ", slot(topikI, 6, 0).name());
        assertEquals(1, topikI.baseQuota());
        assertEquals(0, topikI.heavyNewDays());
        assertEquals(31, RoadmapSketch.minimumDays(topikI(), GAPS, 4, PHASES));
        assertExactGaps(topikI);

        RoadmapSketch.Result oneMonth = RoadmapSketch.build(topikI(), 31, GAPS, 4, PHASES);
        assertEquals(List.of(1, 2, 5, 6, 10, 11), distinctNewsDays(oneMonth));
        assertEquals(List.of(1, 1, 2, 2, 2, 5, 5, 5, 6, 6, 6, 10, 10, 10, 11, 11, 11), newsDays(oneMonth));
        assertEquals(2, oneMonth.baseQuota());
        assertEquals(5, oneMonth.heavyNewDays());
        assertEquals(17, oneMonth.newCount());
        assertTrue(kinds(oneMonth, 26).isEmpty());
        assertTrue(RoadmapSketch.incomplete(oneMonth, 4).isEmpty());
        assertExactGaps(oneMonth);

        RoadmapSketch.Result twoMonths = RoadmapSketch.build(topikII(), 60, GAPS, 4, PHASES);
        assertEquals(List.of(1, 2, 5, 6, 10, 11, 14, 22, 23, 26, 27, 31, 32, 35), distinctNewsDays(twoMonths));
        assertEquals(1, newsOn(twoMonths, 1));
        assertEquals(2, newsOn(twoMonths, 26));
        assertEquals(2, newsOn(twoMonths, 35));
        assertEquals(1, twoMonths.baseQuota());
        assertEquals(5, twoMonths.heavyNewDays());
        assertEquals(19, twoMonths.newCount());
        assertTrue(kinds(twoMonths, 50).isEmpty());
        assertTrue(kinds(twoMonths, 55).isEmpty());
        assertEquals("MINI", kind(twoMonths, 56));
        assertTrue(RoadmapSketch.incomplete(twoMonths, 4).isEmpty());
        assertExactGaps(twoMonths);

        RoadmapSketch.Result longPlan = RoadmapSketch.build(topikII(), 90, GAPS, 4, PHASES);
        assertEquals(19, longPlan.newCount());
        assertEquals(1, longPlan.baseQuota());
        assertEquals(0, longPlan.heavyNewDays());
        assertEquals(52, distinctNewsDays(longPlan).get(distinctNewsDays(longPlan).size() - 1));
        assertTrue(kinds(longPlan, 53).isEmpty());
        assertExactGaps(longPlan);
        assertEquals(31, RoadmapSketch.minimumDays(topikII(), GAPS, 4, PHASES));
    }

    @Test
    void rulesRejectAShortReviewListAndAPhaseSplitThatIsNotOneHundred() {
        ExamDomainException shortReviews = assertThrows(ExamDomainException.class,
                () -> ProgramRules.checkCounts(4, 8, 20, ProgramRules.reviews("2,7")));
        assertTrue(shortReviews.getMessage().contains("mốc ôn"));
        ExamDomainException phases = assertThrows(ExamDomainException.class, () -> ProgramRules.phases("40,40,30"));
        assertTrue(phases.getMessage().contains("100"));
        assertThrows(ExamDomainException.class, () -> ProgramRules.reviews("7, 2"));
        assertThrows(ExamDomainException.class, () -> ProgramRules.checkCounts(4, 4, 20, new int[]{2, 7, 14}));
    }

    @Test
    void rulesAcceptLegacyJsonArraysAsWellAsCommaSeparatedValues() {
        assertArrayEquals(ProgramRules.reviews("2,7,14"), ProgramRules.reviews("[2, 7, 14]"));
        assertArrayEquals(ProgramRules.phases("40,40,20"), ProgramRules.phases("[40,40,20]"));
        assertThrows(ExamDomainException.class, () -> ProgramRules.reviews("[7,2]"));
        assertThrows(ExamDomainException.class, () -> ProgramRules.phases("[40,40,30]"));
    }

    private static void assertExactGaps(RoadmapSketch.Result result) {
        java.util.Map<String, java.util.Map<Integer, Integer>> at = new java.util.LinkedHashMap<>();
        for (RoadmapSketch.DayPlan day : result.days()) {
            for (RoadmapSketch.Slot slot : day.slots()) {
                assertNotEquals("WEEKLY", slot.kind());
                if (slot.code() == null) continue;
                at.computeIfAbsent(slot.code(), key -> new java.util.LinkedHashMap<>()).put(slot.repetition(), day.day());
            }
        }
        for (java.util.Map<Integer, Integer> reps : at.values()) {
            int neu = reps.get(1);
            assertEquals(neu + 2, reps.get(2));
            assertEquals(neu + 7, reps.get(3));
            assertEquals(neu + 14, reps.get(4));
        }
    }

    private static List<Integer> distinctNewsDays(RoadmapSketch.Result result) {
        List<Integer> days = new ArrayList<>();
        for (int day : newsDays(result)) {
            if (days.isEmpty() || days.get(days.size() - 1) != day) days.add(day);
        }
        return days;
    }

    private static List<Integer> newsDays(RoadmapSketch.Result result) {
        List<Integer> days = new ArrayList<>();
        for (RoadmapSketch.DayPlan day : result.days()) {
            for (RoadmapSketch.Slot slot : day.slots()) {
                if ("NEW".equals(slot.kind())) days.add(day.day());
            }
        }
        return days;
    }

    private static int newsOn(RoadmapSketch.Result result, int day) {
        return (int) result.days().get(day - 1).slots().stream().filter(slot -> "NEW".equals(slot.kind())).count();
    }

    private static List<RoadmapSketch.TypeRef> topikI() {
        String[] listening = {"Câu hỏi", "Lời tiếp", "Địa điểm", "Chủ đề", "Chọn tranh", "Nội dung", "Ý chính", "Đoạn dài"};
        String[] reading = {"Câu ngắn", "Điền từ", "Nội dung sai", "Nội dung đúng", "Ý chính", "Đoạn văn", "Sắp xếp câu", "Văn dài", "Chèn câu"};
        return catalog("TPL1", listening, reading);
    }

    private static List<RoadmapSketch.TypeRef> topikII() {
        String[] listening = {"Tranh", "Lời tiếp", "Hành động", "Nội dung đúng", "Ý chính", "Đoạn dài"};
        String[] reading = {"Ngữ pháp", "Đồng nghĩa", "Chủ đề", "Biểu đồ", "Sắp xếp", "Điền đoạn", "Đoạn văn",
                "Tiêu đề", "Ngữ cảnh", "Nội dung đúng", "Ý chính", "Chèn câu", "Bài dài"};
        return catalog("TPL2", listening, reading);
    }

    private static List<RoadmapSketch.TypeRef> catalog(String prefix, String[] listening, String[] reading) {
        List<RoadmapSketch.TypeRef> types = new ArrayList<>();
        for (int i = 0; i < listening.length; i++) {
            types.add(new RoadmapSketch.TypeRef(prefix + "-LIS-" + (i + 1), listening[i]));
        }
        for (int i = 0; i < reading.length; i++) {
            types.add(new RoadmapSketch.TypeRef(prefix + "-REA-" + (i + 1), reading[i]));
        }
        return types;
    }

    private static String kind(RoadmapSketch.Result result, int day) {
        List<String> kinds = kinds(result, day);
        assertEquals(1, kinds.size());
        return kinds.get(0);
    }

    private static RoadmapSketch.Slot slot(RoadmapSketch.Result result, int day, int index) {
        return result.days().get(day - 1).slots().get(index);
    }

    private static List<String> kinds(RoadmapSketch.Result result, int day) {
        return result.days().get(day - 1).slots().stream().map(RoadmapSketch.Slot::kind).toList();
    }
}
