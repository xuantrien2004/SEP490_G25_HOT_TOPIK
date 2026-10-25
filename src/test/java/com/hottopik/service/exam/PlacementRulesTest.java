package com.hottopik.service.exam;

import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;
import com.hottopik.service.exam.PlacementRules.Paper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlacementRulesTest {

    @Test
    void demoPapersAreTenListeningAndTenReadingSingleQuestions() {
        for (Paper paper : Paper.values()) {
            Map<String, Integer> plan = PlacementRules.demo(paper);
            PlacementRules.check(paper, plan);
            assertEquals(20, plan.values().stream().mapToInt(Integer::intValue).sum());
            int listening = 0;
            int reading = 0;
            for (Map.Entry<String, Integer> entry : plan.entrySet()) {
                QuestionTypeDef type = QuestionTypeCatalog.byCode(entry.getKey()).orElseThrow();
                assertTrue(PlacementRules.singleQuestion(type), type.code());
                if (type.skill() == Skill.LISTENING) listening += entry.getValue();
                else reading += entry.getValue();
            }
            assertEquals(10, listening);
            assertEquals(10, reading);
        }
    }

    @Test
    void groupedAndWritingTypesAreDropped() {
        assertTrue(PlacementRules.allowed(Paper.TOPIK_I).stream().noneMatch(QuestionTypeDef::isGrouped));
        assertTrue(PlacementRules.allowed(Paper.TOPIK_II).stream().noneMatch(QuestionTypeDef::isWriting));
        Map<String, Integer> mixed = new HashMap<>(PlacementRules.demo(Paper.TOPIK_I));
        mixed.put("I-N8", 4);
        mixed.put("II-V1", 2);
        Map<String, Integer> chosen = PlacementRules.normalize(Paper.TOPIK_I, mixed);
        assertFalse(chosen.containsKey("I-N8"));
        assertFalse(chosen.containsKey("II-V1"));
        PlacementRules.check(Paper.TOPIK_I, mixed);
    }

    @Test
    void scoreBandsAndTopikTwoOffer() {
        assertEquals(0, PlacementRules.suggestedLevel(Paper.TOPIK_I, 79));
        assertEquals(1, PlacementRules.suggestedLevel(Paper.TOPIK_I, 80));
        assertEquals(1, PlacementRules.suggestedLevel(Paper.TOPIK_I, 139));
        assertEquals(2, PlacementRules.suggestedLevel(Paper.TOPIK_I, 140));
        assertEquals(0, PlacementRules.suggestedLevel(Paper.TOPIK_II, 79));
        assertEquals(3, PlacementRules.suggestedLevel(Paper.TOPIK_II, 80));
        assertEquals(3, PlacementRules.suggestedLevel(Paper.TOPIK_II, 99));
        assertEquals(4, PlacementRules.suggestedLevel(Paper.TOPIK_II, 100));
        assertEquals(4, PlacementRules.suggestedLevel(Paper.TOPIK_II, 129));
        assertEquals(5, PlacementRules.suggestedLevel(Paper.TOPIK_II, 130));
        assertEquals(5, PlacementRules.suggestedLevel(Paper.TOPIK_II, 159));
        assertEquals(6, PlacementRules.suggestedLevel(Paper.TOPIK_II, 160));
        assertEquals(79, PlacementRules.percent(159));
        assertEquals(80, PlacementRules.percent(160));
        assertFalse(PlacementRules.offerTopikII(Paper.TOPIK_I, 159));
        assertTrue(PlacementRules.offerTopikII(Paper.TOPIK_I, 160));
        assertFalse(PlacementRules.offerTopikII(Paper.TOPIK_II, 200));
        assertEquals(200, PlacementRules.points(20));
    }

    @Test
    void targetsStayInsideTheTrackAndAboveTheCurrentLevel() {
        assertEquals(java.util.List.of(1, 2), PlacementRules.targets(0, Track.TOPIK_I));
        assertEquals(java.util.List.of(2), PlacementRules.targets(1, Track.TOPIK_I));
        assertEquals(java.util.List.of(2), PlacementRules.targets(2, Track.TOPIK_I));
        assertEquals(java.util.List.of(3, 4, 5, 6), PlacementRules.targets(0, Track.TOPIK_II));
        assertEquals(java.util.List.of(5, 6), PlacementRules.targets(4, Track.TOPIK_II));
        assertEquals(java.util.List.of(6), PlacementRules.targets(6, Track.TOPIK_II));
        assertEquals(Track.TOPIK_I, PlacementRules.trackForTarget(2));
        assertEquals(Track.TOPIK_II, PlacementRules.trackForTarget(3));
        assertThrows(ExamDomainException.class, () -> PlacementRules.check(Paper.TOPIK_I, Map.of("I-N1", 10)));
    }
}
