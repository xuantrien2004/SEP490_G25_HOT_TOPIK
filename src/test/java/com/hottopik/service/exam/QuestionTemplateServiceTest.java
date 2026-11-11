package com.hottopik.service.exam;

import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.QuestionTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class QuestionTemplateServiceTest {

    @Mock
    private QuestionTemplateRepository templateRepository;

    @InjectMocks
    private QuestionTemplateService templateService;

    private QuestionTemplate sampleTemplate;
    private Actor contentActor;

    @BeforeEach
    void setUp() {
        contentActor = new Actor("Lê Thu Hà", Actor.ROLE_CONTENT);

        sampleTemplate = new QuestionTemplate();
        sampleTemplate.setId(1L);
        sampleTemplate.setCode("I-D1");
        sampleTemplate.setName("Câu văn nói về chủ đề gì");
        sampleTemplate.setDescription("Chọn chủ đề phù hợp nhất với câu văn ngắn");
        sampleTemplate.setTrack(Track.TOPIK_I);
        sampleTemplate.setSkill(Skill.READING);
        sampleTemplate.setStatus(TemplateStatus.ACTIVE);
        sampleTemplate.setPromptRequired(true);
        sampleTemplate.setAudioScope(ResourceScope.NONE);
        sampleTemplate.setImageScope(ResourceScope.NONE);
        sampleTemplate.setPassageScope(ResourceScope.QUESTION);
        sampleTemplate.setAnswerType(AnswerType.CHOICE_INDEX);
        sampleTemplate.setOptionCount(4);
    }

    @Test
    @DisplayName("WF10 - List templates by track and skill")
    void testList_ByTrackAndSkill() {
        when(templateRepository.findByTrackAndSkillOrderByCodeAsc(Track.TOPIK_I, Skill.READING))
                .thenReturn(List.of(sampleTemplate));

        List<QuestionTemplate> results = templateService.list(Track.TOPIK_I, Skill.READING);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("I-D1", results.get(0).getCode());
        verify(templateRepository, times(1)).findByTrackAndSkillOrderByCodeAsc(Track.TOPIK_I, Skill.READING);
    }

    @Test
    @DisplayName("WF10 - List all templates with null filter")
    void testList_NullFilters_ReturnsAll() {
        when(templateRepository.findAllByOrderByCodeAsc())
                .thenReturn(List.of(sampleTemplate));

        List<QuestionTemplate> results = templateService.list(null, null);

        assertNotNull(results);
        assertEquals(1, results.size());
        verify(templateRepository, times(1)).findAllByOrderByCodeAsc();
    }

    @Test
    @DisplayName("WF10 - Get template by code when found")
    void testGet_Found() {
        when(templateRepository.findByCode("I-D1")).thenReturn(Optional.of(sampleTemplate));

        QuestionTemplate result = templateService.get("I-D1");

        assertNotNull(result);
        assertEquals("I-D1", result.getCode());
        assertEquals("Câu văn nói về chủ đề gì", result.getName());
    }

    @Test
    @DisplayName("WF10 - Get template by code when not found throws ExamDomainException")
    void testGet_NotFound_ThrowsException() {
        when(templateRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(ExamDomainException.class, () -> {
            templateService.get("UNKNOWN");
        });
    }

    @Test
    @DisplayName("WF10 - Fields generation for question template")
    void testFields_GeneratesExpectedGuidanceLines() {
        List<String> fields = templateService.fields(sampleTemplate);

        assertNotNull(fields);
        assertFalse(fields.isEmpty());
        assertTrue(fields.stream().anyMatch(f -> f.contains("4 lựa chọn")));
        assertTrue(fields.stream().anyMatch(f -> f.contains("Đoạn văn")));
    }

    @Test
    @DisplayName("WF10 - Update template info and status successfully")
    void testUpdate_Success() {
        when(templateRepository.findByCode("I-D1")).thenReturn(Optional.of(sampleTemplate));

        templateService.update("I-D1", "Tên mới", "Mô tả mới dài hơn", TemplateStatus.INACTIVE, false, false, false, contentActor);

        assertEquals("Tên mới", sampleTemplate.getName());
        assertEquals("Mô tả mới dài hơn", sampleTemplate.getDescription());
        assertEquals(TemplateStatus.INACTIVE, sampleTemplate.getStatus());
    }
}
