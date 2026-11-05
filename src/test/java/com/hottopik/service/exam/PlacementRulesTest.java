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
    void scoreBandsSuggestARoadmapWithoutASecondPaper() {
        assertEquals(0, PlacementRules.suggestedLevel(Paper.TOPIK_I, 79));
        assertEquals(1, PlacementRules.suggestedLevel(Paper.TOPIK_I, 80));
        assertEquals(1, PlacementRules.suggestedLevel(Paper.TOPIK_I, 139));
        assertEquals(2, PlacementRules.suggestedLevel(Paper.TOPIK_I, 140));
        assertEquals(Track.TOPIK_I, PlacementRules.suggestedTrack(0));
        assertEquals(Track.TOPIK_I, PlacementRules.suggestedTrack(1));
        assertEquals(Track.TOPIK_II, PlacementRules.suggestedTrack(2));
        assertEquals("Nên chọn", PlacementRules.choiceLabel(Track.TOPIK_I, 0));
        assertEquals("Vượt trình độ", PlacementRules.choiceLabel(Track.TOPIK_II, 1));
        assertEquals("Học lại", PlacementRules.choiceLabel(Track.TOPIK_I, 2));
        assertEquals("Nên chọn", PlacementRules.choiceLabel(Track.TOPIK_II, 2));
        assertEquals(java.util.List.of(1, 2), PlacementRules.roadmapTargets(Track.TOPIK_I));
        assertEquals(java.util.List.of(3, 4, 5, 6), PlacementRules.roadmapTargets(Track.TOPIK_II));
        assertEquals("Hệ thống gợi ý lộ trình TOPIK I", PlacementRules.advice(70));
        assertEquals("Hệ thống gợi ý lộ trình TOPIK I", PlacementRules.advice(80));
        assertEquals("Hệ thống gợi ý lộ trình TOPIK II", PlacementRules.advice(140));
        assertEquals("Hệ thống gợi ý lộ trình TOPIK II", PlacementRules.advice(200));
        assertEquals("TOPIK 1 · Sơ cấp 1", PlacementRules.levelOption(1));
        assertEquals("TOPIK 2 · Sơ cấp 2", PlacementRules.levelOption(2));
        assertEquals("TOPIK 3 · Trung cấp 1", PlacementRules.levelOption(3));
        assertEquals("TOPIK 6 · Cao cấp 2", PlacementRules.levelOption(6));
        assertFalse(PlacementRules.levelDetail(1).isBlank());
        assertFalse(PlacementRules.levelDetail(4).isBlank());
        assertEquals(java.util.List.of(31, 60, 90, 180), PlacementRules.dayMarks().stream().map(PlacementRules.DayMark::days).toList());
        assertEquals("1 tháng", PlacementRules.dayMarks().get(0).label());
        assertEquals("2 tháng", PlacementRules.dayMarks().get(1).label());
        assertEquals("6 tháng", PlacementRules.dayMarks().get(3).label());
        assertEquals(90, PlacementRules.defaultSpan());
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
