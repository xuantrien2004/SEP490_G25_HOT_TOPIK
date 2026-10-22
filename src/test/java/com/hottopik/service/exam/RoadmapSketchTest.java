package com.hottopik.service.exam;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoadmapSketchTest {

    @Test
    void newLessonThenReviewsSkipTheWeeklySlot() {
        List<RoadmapSketch.TypeRef> types = List.of(
                new RoadmapSketch.TypeRef("TPL-A", "Dạng A"),
                new RoadmapSketch.TypeRef("TPL-B", "Dạng B"));
        RoadmapSketch.Result result = RoadmapSketch.build(types, 31, 2, new int[]{2, 7, 14}, 4, new int[]{40, 40, 20});

        assertEquals(List.of("NEW", "NEW"), kinds(result, 1));
        assertEquals("TPL-A", result.days().get(0).slots().get(0).code());
        assertEquals("TPL-B", result.days().get(0).slots().get(1).code());
        assertEquals(List.of("REVIEW", "REVIEW"), kinds(result, 3));
        assertEquals(2, result.days().get(2).slots().get(0).repetition());
        assertEquals(List.of("MINI", "WEEKLY"), kinds(result, 7));
        assertTrue(result.days().get(6).slots().get(0).name().startsWith("Mini test"));
        assertEquals(List.of("REVIEW", "REVIEW"), kinds(result, 8));
        assertEquals(3, result.days().get(7).slots().get(0).repetition());
        assertEquals(List.of("REVIEW", "REVIEW"), kinds(result, 15));
        assertEquals(4, result.days().get(14).slots().get(0).repetition());
        assertEquals(List.of("FULL", "FULL"), kinds(result, 31));
        assertEquals(2, result.newCount());
        assertEquals(6, result.reviewCount());
        assertEquals(4, result.weeklyCount());
        assertEquals(2, result.fullCount());
        assertTrue(result.miniCount() > 0);
        assertTrue(result.unplaced().isEmpty());
        assertTrue(RoadmapSketch.incomplete(result, 4).isEmpty());
        assertEquals("Làm quen", result.days().get(0).phase());
        assertEquals(31, result.phases().stream().mapToInt(RoadmapSketch.Phase::days).sum());
    }

    @Test
    void fourLessonsOnADayAndMiniTestsAlternateBeforeTheFullTests() {
        List<RoadmapSketch.TypeRef> types = List.of(
                new RoadmapSketch.TypeRef("TPL-A", "Dạng A"),
                new RoadmapSketch.TypeRef("TPL-B", "Dạng B"),
                new RoadmapSketch.TypeRef("TPL-C", "Dạng C"),
                new RoadmapSketch.TypeRef("TPL-D", "Dạng D"));
        RoadmapSketch.Result result = RoadmapSketch.build(types, 31, 4, new int[]{2, 7, 14}, 4, new int[]{40, 40, 20});

        assertEquals(List.of("NEW", "NEW", "NEW", "NEW"), kinds(result, 1));
        assertEquals("Dạng A", result.days().get(0).slots().get(0).name());
        assertEquals("Dạng D", result.days().get(0).slots().get(3).name());
        List<RoadmapSketch.Slot> last = result.days().get(30).slots();
        assertEquals("FULL", last.get(last.size() - 1).kind());
        assertEquals("FULL", last.get(last.size() - 2).kind());
        String previous = "";
        int alternated = 0;
        for (RoadmapSketch.DayPlan day : result.days()) {
            for (RoadmapSketch.Slot slot : day.slots()) {
                if (!"MINI".equals(slot.kind())) continue;
                assertNotEquals(previous, slot.name());
                previous = slot.name();
                alternated++;
            }
        }
        assertTrue(alternated >= 2);
        assertTrue(RoadmapSketch.incomplete(result, 4).isEmpty());
    }

    @Test
    void reviewMovesToTheNextFreeDayWhenTheTargetIsWeekly() {
        RoadmapSketch.Result result = RoadmapSketch.build(
                List.of(new RoadmapSketch.TypeRef("TPL-A", "Dạng A")),
                31, 1, new int[]{6}, 2, new int[]{40, 40, 20});
        assertEquals(List.of("NEW"), kinds(result, 1));
        assertEquals(List.of("WEEKLY"), kinds(result, 7));
        assertEquals(List.of("REVIEW"), kinds(result, 8));
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

    private static List<String> kinds(RoadmapSketch.Result result, int day) {
        return result.days().get(day - 1).slots().stream().map(RoadmapSketch.Slot::kind).toList();
    }
}
