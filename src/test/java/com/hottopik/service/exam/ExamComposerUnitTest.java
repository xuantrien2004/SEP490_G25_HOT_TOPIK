package com.hottopik.service.exam;

import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.ExamAttemptRepository;
import com.hottopik.repository.exam.ExamRepository;
import com.hottopik.repository.exam.QuestionRepository;
import com.hottopik.repository.exam.WritingPromptRepository;
import com.hottopik.service.AdminSystemService;
import com.hottopik.service.ApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ExamComposerUnitTest {

    @Mock
    private ExamRepository exams;

    @Mock
    private ExamAttemptRepository attempts;

    @Mock
    private QuestionRepository questions;

    @Mock
    private WritingPromptRepository writingPrompts;

    @Mock
    private ApprovalService approvals;

    @Mock
    private AdminSystemService audit;

    @InjectMocks
    private ExamComposerService composerService;

    private Actor contentActor;
    private Exam sampleExam;

    @BeforeEach
    void setUp() {
        contentActor = new Actor("Lê Thu Hà", Actor.ROLE_CONTENT);

        sampleExam = new Exam();
        sampleExam.setId(100L);
        sampleExam.setName("Đề thi thử TOPIK I số 1");
        sampleExam.setTrack(Track.TOPIK_I);
        sampleExam.setKind(ExamKind.FULL);
        sampleExam.setStatus(ExamStatus.DRAFT);
        sampleExam.setItems(new ArrayList<>());
    }

    @Test
    @DisplayName("WF10 - Get exam by ID when found")
    void testGet_Found() {
        when(exams.findForComposerById(100L)).thenReturn(Optional.of(sampleExam));

        Exam result = composerService.get(100L);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("Đề thi thử TOPIK I số 1", result.getName());
    }

    @Test
    @DisplayName("WF10 - Get exam by ID when not found throws ExamDomainException")
    void testGet_NotFound_ThrowsException() {
        when(exams.findForComposerById(999L)).thenReturn(Optional.empty());

        assertThrows(ExamDomainException.class, () -> {
            composerService.get(999L);
        });
    }

    @Test
    @DisplayName("WF10 - Delete draft exam by content manager successfully")
    void testDeleteDraft_Success() {
        when(exams.findForComposerById(100L)).thenReturn(Optional.of(sampleExam));
        when(exams.findByParentId(100L)).thenReturn(Collections.emptyList());
        doNothing().when(exams).deleteAll(any());
        doNothing().when(exams).flush();
        doNothing().when(exams).delete(sampleExam);

        composerService.deleteDraft(100L, contentActor);

        verify(exams, times(1)).delete(sampleExam);
    }

    @Test
    @DisplayName("WF10 - Delete draft throws exception if exam was already published")
    void testDeleteDraft_AlreadyPublished_ThrowsException() {
        sampleExam.setPublishedAt(LocalDateTime.now());
        when(exams.findForComposerById(100L)).thenReturn(Optional.of(sampleExam));

        assertThrows(ExamDomainException.class, () -> {
            composerService.deleteDraft(100L, contentActor);
        });
        verify(exams, never()).delete(any());
    }

    @Test
    @DisplayName("WF10 - FrameCheck isOk evaluation")
    void testFrameCheck_IsOkLogic() {
        // Case 1: Complete and valid
        ExamComposerService.FrameCheck validCheck = new ExamComposerService.FrameCheck(
                Collections.emptyList(), Collections.emptyList(), false, true);
        assertTrue(validCheck.isOk());

        // Case 2: Missing types
        ExamComposerService.FrameCheck missingCheck = new ExamComposerService.FrameCheck(
                List.of("I-D1 (thiếu 2)"), Collections.emptyList(), false, true);
        assertFalse(missingCheck.isOk());

        // Case 3: Hidden / inactive questions
        ExamComposerService.FrameCheck hiddenCheck = new ExamComposerService.FrameCheck(
                Collections.emptyList(), List.of("Q_101"), false, true);
        assertFalse(hiddenCheck.isOk());

        // Case 4: Writing required but not OK
        ExamComposerService.FrameCheck writingFailCheck = new ExamComposerService.FrameCheck(
                Collections.emptyList(), Collections.emptyList(), true, false);
        assertFalse(writingFailCheck.isOk());
    }

    @Test
    @DisplayName("WF10 - BlockView label generation for single and multi question blocks")
    void testBlockView_Label() {
        ExamComposerService.BlockView singleBlock = new ExamComposerService.BlockView(
                List.of(1), List.of(new ExamComposerService.SlotView(1, null)));
        assertEquals("Câu 1", singleBlock.label());

        ExamComposerService.BlockView multiBlock = new ExamComposerService.BlockView(
                List.of(3, 4),
                List.of(new ExamComposerService.SlotView(3, null), new ExamComposerService.SlotView(4, null)));
        assertTrue(multiBlock.label().contains("3"));
        assertTrue(multiBlock.label().contains("4"));
    }
}
