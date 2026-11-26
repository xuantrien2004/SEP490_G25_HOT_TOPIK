package com.hottopik.service.exam;

import com.hottopik.entity.exam.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LearnerExamSingleSkillTest {

    @Test
    @DisplayName("Đề FULL mock chỉ có kỹ năng Viết -> availableSkills chỉ có WRITING")
    void fullMockWithOnlyWriting() {
        Exam exam = new Exam();
        exam.setKind(ExamKind.FULL);
        exam.setTrack(Track.TOPIK_II);
        exam.setItems(List.of());

        WritingPrompt wp1 = new WritingPrompt();
        wp1.setNo(51);
        WritingPrompt wp2 = new WritingPrompt();
        wp2.setNo(52);
        exam.setWriting51(wp1);
        exam.setWriting52(wp2);

        List<Skill> available = LearnerExamService.availableSkills(exam);
        assertEquals(1, available.size());
        assertEquals(Skill.WRITING, available.get(0));
    }

    @Test
    @DisplayName("Đề FULL mock chỉ có kỹ năng Đọc -> availableSkills chỉ có READING")
    void fullMockWithOnlyReading() {
        Exam exam = new Exam();
        exam.setKind(ExamKind.FULL);
        exam.setTrack(Track.TOPIK_I);

        ExamItem item = new ExamItem();
        item.setSection(Skill.READING);
        item.setNo(31);
        exam.setItems(List.of(item));

        List<Skill> available = LearnerExamService.availableSkills(exam);
        assertEquals(1, available.size());
        assertEquals(Skill.READING, available.get(0));
    }

    @Test
    @DisplayName("Đề FULL mock đầy đủ 3 kỹ năng TOPIK II -> availableSkills đủ Nghe, Đọc, Viết")
    void fullMockWithAllSkills() {
        Exam exam = new Exam();
        exam.setKind(ExamKind.FULL);
        exam.setTrack(Track.TOPIK_II);

        ExamItem listenItem = new ExamItem();
        listenItem.setSection(Skill.LISTENING);
        listenItem.setNo(1);

        ExamItem readItem = new ExamItem();
        readItem.setSection(Skill.READING);
        readItem.setNo(1);

        exam.setItems(List.of(listenItem, readItem));

        WritingPrompt wp = new WritingPrompt();
        wp.setNo(51);
        exam.setWriting51(wp);

        List<Skill> available = LearnerExamService.availableSkills(exam);
        assertEquals(3, available.size());
        assertEquals(Skill.LISTENING, available.get(0));
        assertEquals(Skill.READING, available.get(1));
        assertEquals(Skill.WRITING, available.get(2));
    }

    @Test
    @DisplayName("Đề chỉ có 1 kỹ năng thì nextSkill luôn trả về null (không chuyển ca)")
    void singleSkillExamHasNoNextSkill() {
        Exam exam = new Exam();
        exam.setId(99L);
        exam.setKind(ExamKind.FULL);
        exam.setTrack(Track.TOPIK_II);
        exam.setItems(List.of());
        WritingPrompt wp = new WritingPrompt();
        wp.setNo(51);
        exam.setWriting51(wp);

        List<Skill> avail = LearnerExamService.availableSkills(exam);
        assertEquals(1, avail.size());
        assertEquals(Skill.WRITING, avail.get(0));
    }
}
