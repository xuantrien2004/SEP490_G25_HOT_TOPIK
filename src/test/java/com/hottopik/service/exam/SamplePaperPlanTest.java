package com.hottopik.service.exam;

import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplePaperPlanTest {

    @Test
    void everyTemplateAppearsOncePerPaperInsideItsSection() {
        for (Track track : Track.values()) {
            List<String> codes = QuestionTemplateCatalog.all().stream()
                    .filter(s -> s.track() == track)
                    .map(QuestionTemplateCatalog.Seed::code)
                    .toList();
            for (int paper = 1; paper <= 2; paper++) {
                List<SamplePaperSeeder.Slot> slots = SamplePaperSeeder.plan(track, paper);
                assertEquals(codes, slots.stream().map(SamplePaperSeeder.Slot::templateCode).toList());
                int questions = slots.stream().mapToInt(s -> s.numbers().size()).sum();
                assertTrue(questions != 70 && questions != 104, track + " đề " + paper + " có " + questions + " câu");
                for (Skill skill : Skill.values()) {
                    List<Integer> nos = slots.stream()
                            .filter(s -> s.skill() == skill)
                            .flatMap(s -> s.numbers().stream())
                            .toList();
                    assertEquals(nos.size(), new HashSet<>(nos).size(), track + " " + skill + " bị trùng số");
                }
            }
        }
    }

    @Test
    void groupedTemplatesUseChildMinOnPaperOneAndChildMaxOnPaperTwo() {
        assertNumbers(Track.TOPIK_I, "TPL1-LIS-08", 1, 2);
        assertNumbers(Track.TOPIK_I, "TPL1-LIS-08", 2, 2);
        assertNumbers(Track.TOPIK_I, "TPL1-LIS-01", 1, 1);
        assertNumbers(Track.TOPIK_II, "TPL2-LIS-06", 1, 2);
        assertNumbers(Track.TOPIK_II, "TPL2-REA-13", 1, 2);
        assertNumbers(Track.TOPIK_II, "TPL2-REA-13", 2, 3);
        assertEquals(List.of(51), numbers(Track.TOPIK_II, "TPL2-WRI-01", 2));
        assertEquals(List.of(54), numbers(Track.TOPIK_II, "TPL2-WRI-04", 1));
        assertTrue(SamplePaperSeeder.plan(Track.TOPIK_I, 1).stream().noneMatch(s -> s.skill() == Skill.WRITING));
    }

    private static void assertNumbers(Track track, String code, int paper, int count) {
        assertEquals(count, numbers(track, code, paper).size(), code + " đề " + paper);
    }

    private static List<Integer> numbers(Track track, String code, int paper) {
        return SamplePaperSeeder.plan(track, paper).stream()
                .filter(s -> s.templateCode().equals(code))
                .findFirst()
                .orElseThrow()
                .numbers();
    }

    @Test
    void listeningAndReadingNumbersStayInsideTheOfficialSection() {
        Set<Integer> topikIListen = new HashSet<>();
        SamplePaperSeeder.plan(Track.TOPIK_I, 1).stream()
                .filter(s -> s.skill() == Skill.LISTENING)
                .forEach(s -> topikIListen.addAll(s.numbers()));
        assertTrue(topikIListen.stream().allMatch(n -> n >= 1 && n <= 30));
        Set<Integer> topikIRead = new HashSet<>();
        SamplePaperSeeder.plan(Track.TOPIK_I, 2).stream()
                .filter(s -> s.skill() == Skill.READING)
                .forEach(s -> topikIRead.addAll(s.numbers()));
        assertTrue(topikIRead.stream().allMatch(n -> n >= 31 && n <= 70));
    }
}
