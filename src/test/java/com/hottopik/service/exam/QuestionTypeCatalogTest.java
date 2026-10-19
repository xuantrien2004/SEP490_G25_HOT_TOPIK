package com.hottopik.service.exam;

import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class QuestionTypeCatalogTest {

    @Test
    void has42TypesWithUniqueCodes() {
        assertEquals(42, QuestionTypeCatalog.all().size());
        assertEquals(42, new HashSet<>(QuestionTypeCatalog.all().stream().map(QuestionTypeDef::code).toList()).size());
    }

    @Test
    void fullExamCounts() {
        assertEquals(70, QuestionTypeCatalog.mcqCount(Track.TOPIK_I));
        assertEquals(100, QuestionTypeCatalog.mcqCount(Track.TOPIK_II));
        assertEquals(70, QuestionTypeCatalog.fullExamCount(Track.TOPIK_I));
        assertEquals(104, QuestionTypeCatalog.fullExamCount(Track.TOPIK_II));
        assertEquals(100, QuestionTypeCatalog.totalMinutes(Track.TOPIK_I));
        assertEquals(180, QuestionTypeCatalog.totalMinutes(Track.TOPIK_II));
    }

    @Test
    void everyQuestionNumberBelongsToExactlyOneTypeAndBlock() {
        for (Track track : Track.values()) {
            for (QuestionTypeCatalog.Section sec : QuestionTypeCatalog.sections(track)) {
                List<QuestionTypeDef> types = QuestionTypeCatalog.forTrackAndSkill(track, sec.skill());
                Map<Integer, String> owner = new HashMap<>();
                for (QuestionTypeDef t : types) {
                    List<Integer> fromBlocks = t.blocks().stream().flatMap(List::stream).sorted().toList();
                    assertEquals(t.numbers().stream().sorted().toList(), fromBlocks, t.code() + ": blocks phải phủ đúng các câu của dạng");
                    for (int n : t.numbers()) {
                        assertNull(owner.put(n, t.code()), track + " " + sec.skill() + " câu " + n + " thuộc hai dạng");
                    }
                }
                for (int n = sec.firstNo(); n <= sec.lastNo(); n++) {
                    assertTrue(owner.containsKey(n), track + " " + sec.skill() + " câu " + n + " chưa có dạng");
                    assertEquals(owner.get(n), QuestionTypeCatalog.resolve(track, sec.skill(), n).orElseThrow().code());
                }
                assertEquals(sec.count(), owner.size());
            }
        }
    }

    @Test
    void resolveExamples() {
        assertEquals("I-N1", QuestionTypeCatalog.resolve(Track.TOPIK_I, Skill.LISTENING, 3).orElseThrow().code());
        assertEquals("II-D12", QuestionTypeCatalog.resolve(Track.TOPIK_II, Skill.READING, 49).orElseThrow().code());
        assertEquals(List.of(48, 49, 50), QuestionTypeCatalog.byCode("II-D12").orElseThrow().blockOf(49));
        assertTrue(QuestionTypeCatalog.resolve(Track.TOPIK_I, Skill.READING, 12).isEmpty());
        assertTrue(QuestionTypeCatalog.byCode("I-N5").orElseThrow().imageRequired());
    }
}
