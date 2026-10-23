package com.hottopik.service.exam;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.ExamSourceRepository;
import com.hottopik.repository.exam.QuestionRepository;
import com.hottopik.service.PracticeService;
import com.hottopik.util.AuthHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReadingPracticeRegressionTest {
    private static final Actor CM = new Actor("Content Manager", Actor.ROLE_CONTENT);
    private static final long LEARNER = 903L;
    @Autowired ExamImportService imports;
    @Autowired ExamSourceRepository sources;
    @Autowired QuestionRepository questions;
    @Autowired PracticeService practice;
    @Autowired MockMvc mvc;

    @BeforeEach
    void keepSeededReadingSourcesInDraft() {
        sources.findAll().stream().filter(s -> s.getTrack() == Track.TOPIK_I).forEach(s -> {
            s.setStatus(SourceStatus.DRAFT);
            sources.save(s);
        });
    }

    private ExamSource source() {
        ExamSource source = imports.createSource(new ExamImportService.SourceForm(
                "Đọc TOPIK I " + UUID.randomUUID(), Track.TOPIK_I, 2026, null,
                null, false, "Đề nội bộ", null), CM);
        imports.startManual(source.getId(), CM);
        return source;
    }

    private void card(ExamSource source, String template, List<Integer> numbers) {
        imports.saveCard(source.getId(), new ExamImportService.CardForm(null, template,
                null, null, "지문 " + source.getId() + " — " + numbers.get(0), null,
                numbers.stream().map(n -> "질문 " + n).toList(),
                numbers.stream().map(n -> "답 1 — " + n).toList(),
                numbers.stream().map(n -> "답 2 — " + n).toList(),
                numbers.stream().map(n -> "답 3 — " + n).toList(),
                numbers.stream().map(n -> "답 4 — " + n).toList(),
                numbers.stream().map(n -> "2").toList(),
                numbers.stream().map(n -> "Giải thích câu " + n).toList(),
                null, null, null, null, null, null, numbers), CM);
    }

    @Test
    void everyReadingTypeUsesTheActualManualContentForItsPositions() throws Exception {
        ExamSource source = source();
        // Reproduce the current bank: all 40 positions were entered using the first template.
        for (int no = 31; no <= 70; no++) card(source, "TPL1-REA-01", List.of(no));
        assertEquals(40, imports.activate(source.getId(), CM).questionCount());
        Map<Integer, Question> expected = imports.questionsOf(source.getId()).stream()
                .collect(Collectors.toMap(Question::getOriginalNo, q -> q));
        assertTrue(expected.values().stream().allMatch(q -> "I-D1".equals(q.getTypeCode())));

        for (PracticeService.TypeSummary summary : practice.summaries(LEARNER, Track.TOPIK_I, Skill.READING)) {
            QuestionTypeDef type = summary.type();
            assertEquals(type.requiredCount(), summary.available(), type.code());
            PracticeAttempt attempt = practice.start(LEARNER, "reading@example.com", type.code());
            List<PracticeService.QuestionView> selected = practice.questions(attempt);
            assertEquals(type.numbers(), selected.stream().map(PracticeService.QuestionView::no).toList());
            for (PracticeService.QuestionView view : selected) {
                Question original = expected.get(view.no());
                assertEquals(original.getId(), view.id());
                assertEquals(original.getGroup().getPassageKo(), view.passage());
                assertEquals(original.getPromptKo(), view.prompt());
                assertEquals(original.options(), view.options());
                assertEquals(original.getAnswerIndex(), view.answer());
                assertEquals(original.getExplanationVi(), view.explanation());
            }
            if ("I-D1".equals(type.code())) {
                UserDTO learner = new UserDTO();
                learner.setId(LEARNER);
                learner.setEmail("reading@example.com");
                learner.setRole("STUDENT");
                mvc.perform(get("/practice/play/{id}", attempt.getId())
                                .sessionAttr(AuthHelper.SESSION_KEY, learner))
                        .andExpect(status().isOk())
                        .andExpect(content().string(containsString("지문 " + source.getId() + " — 31")))
                        .andExpect(content().string(containsString("질문 31")))
                        .andExpect(content().string(containsString("답 2 — 31")))
                        .andExpect(content().string(not(containsString("샘플"))));
            }
        }
    }

    @Test
    void draftSourcesHiddenQuestionsAndAssessmentContentAreNotPracticeCandidates() {
        ExamSource source = source();
        for (int no = 31; no <= 33; no++) card(source, "TPL1-REA-01", List.of(no));
        imports.activate(source.getId(), CM);
        assertEquals(3, practice.summaries(LEARNER, Track.TOPIK_I, Skill.READING).get(0).available());
        source.setStatus(SourceStatus.DRAFT);
        sources.save(source);
        assertEquals(0, practice.summaries(LEARNER, Track.TOPIK_I, Skill.READING).get(0).available());
        source.setStatus(SourceStatus.ACTIVE);
        sources.save(source);
        Question first = imports.questionsOf(source.getId()).get(0);
        first.setStatus(QuestionStatus.HIDDEN);
        questions.save(first);
        assertEquals(2, practice.summaries(LEARNER, Track.TOPIK_I, Skill.READING).get(0).available());
        assertThrows(IllegalStateException.class, () -> practice.start(LEARNER, "reading@example.com", "I-D1"));
        first.setStatus(QuestionStatus.ACTIVE);
        first.setPurpose(QuestionPurpose.ASSESSMENT);
        questions.save(first);
        assertEquals(2, practice.summaries(LEARNER, Track.TOPIK_I, Skill.READING).get(0).available());
        source.setAssessment(true);
        sources.save(source);
        assertEquals(0, practice.summaries(LEARNER, Track.TOPIK_I, Skill.READING).get(0).available());
    }

    @Test
    void separateCardsFromDifferentSourcesCannotFormOneReadingBlock() {
        for (int no : List.of(59, 60)) {
            ExamSource source = source();
            card(source, "TPL1-REA-01", List.of(no));
            imports.activate(source.getId(), CM);
        }
        assertThrows(IllegalStateException.class, () -> practice.start(LEARNER, "reading@example.com", "I-D8"));
    }

    @Test
    void sharedPassagesStayTogetherAndNextAttemptPrefersUnseenQuestions() {
        for (int i = 0; i < 2; i++) {
            ExamSource source = source();
            card(source, "TPL1-REA-09", List.of(59, 60));
            imports.activate(source.getId(), CM);
        }
        PracticeAttempt first = practice.start(LEARNER, "reading@example.com", "I-D8");
        List<PracticeService.QuestionView> selected = practice.questions(first);
        assertEquals(2, selected.size());
        assertEquals(selected.get(0).passage(), selected.get(1).passage());
        practice.submit(LEARNER, first.getId(), selected.stream().collect(Collectors.toMap(
                q -> q.id().toString(), q -> Integer.toString(q.answer()))));
        assertTrue(practice.result(LEARNER, first.getId()).answers().stream().allMatch(PracticeService.AnswerView::correct));
        PracticeAttempt next = practice.start(LEARNER, "reading@example.com", "I-D8");
        assertTrue(practice.questions(next).stream().noneMatch(q -> selected.stream().anyMatch(old -> old.id().equals(q.id()))));
        assertEquals(first.getQuestionIds(), practice.retry(LEARNER, "reading@example.com", first.getId()).getQuestionIds());
    }
}
