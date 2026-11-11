package com.hottopik.service;

import com.hottopik.dto.GrammarExampleForm;
import com.hottopik.dto.GrammarForm;
import com.hottopik.entity.Grammar;
import com.hottopik.entity.GrammarExample;
import com.hottopik.entity.GrammarGroup;
import com.hottopik.repository.GrammarGroupRepository;
import com.hottopik.repository.GrammarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GrammarServiceTest {

    @Mock
    private GrammarRepository grammarRepository;

    @Mock
    private GrammarGroupRepository grammarGroupRepository;

    @Mock
    private VocabularySyncService syncService;

    @InjectMocks
    private GrammarService grammarService;

    private GrammarGroup sampleGroup;
    private Grammar sampleGrammar;

    @BeforeEach
    void setUp() {
        sampleGroup = new GrammarGroup();
        sampleGroup.setId(1);
        sampleGroup.setLevel((short) 1);
        sampleGroup.setNameVi("Nguyên nhân & Kết quả");
        sampleGroup.setStatus("ACTIVE");

        sampleGrammar = new Grammar();
        sampleGrammar.setId(10L);
        sampleGrammar.setGrammarGroup(sampleGroup);
        sampleGrammar.setLevel((short) 1);
        sampleGrammar.setPattern("-아/어서");
        sampleGrammar.setTranslation("Vì... nên...");
        sampleGrammar.setStatus("ACTIVE");
        sampleGrammar.setIsFrequent(true);
        sampleGrammar.setExamples(new ArrayList<>());
    }

    @Test
    @DisplayName("WF08 - Get grammar by valid ID should return grammar")
    void testGetGrammarById_Success() {
        when(grammarRepository.findById(10L)).thenReturn(Optional.of(sampleGrammar));

        Grammar result = grammarService.getGrammarById(10L);

        assertNotNull(result);
        assertEquals("-아/어서", result.getPattern());
        assertEquals("Vì... nên...", result.getTranslation());
        verify(grammarRepository, times(1)).findById(10L);
    }

    @Test
    @DisplayName("WF08 - Get grammar by null ID should throw IllegalArgumentException")
    void testGetGrammarById_NullId_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            grammarService.getGrammarById(null);
        });
        assertTrue(ex.getMessage().contains("không được để trống"));
        verify(grammarRepository, never()).findById(any());
    }

    @Test
    @DisplayName("WF08 - Get grammar by non-existent ID should throw IllegalArgumentException")
    void testGetGrammarById_NotFound_ThrowsException() {
        when(grammarRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            grammarService.getGrammarById(999L);
        });
        assertTrue(ex.getMessage().contains("Không tìm thấy"));
    }

    @Test
    @DisplayName("WF08 - Search grammars with keyword returns matching list")
    void testSearchGrammars_WithKeyword() {
        when(grammarRepository.searchByKeyword("vì")).thenReturn(List.of(sampleGrammar));

        List<Grammar> result = grammarService.searchGrammars(" vì ");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("-아/어서", result.get(0).getPattern());
        verify(grammarRepository, times(1)).searchByKeyword("vì");
    }

    @Test
    @DisplayName("WF08 - Search grammars with empty keyword returns all")
    void testSearchGrammars_EmptyKeyword_ReturnsAll() {
        when(grammarRepository.findAll()).thenReturn(List.of(sampleGrammar));

        List<Grammar> result = grammarService.searchGrammars("   ");

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(grammarRepository, times(1)).findAll();
        verify(grammarRepository, never()).searchByKeyword(anyString());
    }

    @Test
    @DisplayName("WF08 - Filter grammars with invalid level should throw exception")
    void testFilterGrammars_InvalidLevel_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            grammarService.filterGrammars((short) 7, 1, "ACTIVE", null);
        });
    }

    @Test
    @DisplayName("WF08 - Update grammar status from ACTIVE to HIDDEN")
    void testUpdateStatus_ActiveToHidden() {
        sampleGrammar.setStatus("ACTIVE");
        when(grammarRepository.findById(10L)).thenReturn(Optional.of(sampleGrammar));
        when(grammarRepository.save(any(Grammar.class))).thenAnswer(i -> i.getArgument(0));

        Grammar updated = grammarService.updateStatus(10L, "HIDDEN");

        assertNotNull(updated);
        assertEquals("HIDDEN", updated.getStatus());
        verify(grammarRepository, times(1)).save(sampleGrammar);
    }

    @Test
    @DisplayName("WF08 - Update grammar status from HIDDEN to ACTIVE")
    void testUpdateStatus_HiddenToActive() {
        sampleGrammar.setStatus("HIDDEN");
        when(grammarRepository.findById(10L)).thenReturn(Optional.of(sampleGrammar));
        when(grammarRepository.save(any(Grammar.class))).thenAnswer(i -> i.getArgument(0));

        Grammar updated = grammarService.updateStatus(10L, "ACTIVE");

        assertNotNull(updated);
        assertEquals("ACTIVE", updated.getStatus());
    }

    @Test
    @DisplayName("WF08 - Update frequent grammar flag")
    void testUpdateFrequent() {
        when(grammarRepository.findById(10L)).thenReturn(Optional.of(sampleGrammar));
        when(grammarRepository.save(any(Grammar.class))).thenAnswer(i -> i.getArgument(0));

        Grammar updated = grammarService.updateFrequent(10L, false);

        assertNotNull(updated);
        assertFalse(updated.getIsFrequent());
        verify(grammarRepository, times(1)).save(sampleGrammar);
    }

    @Test
    @DisplayName("WF08 - Save grammar with valid form successfully")
    void testSaveGrammar_Success() {
        GrammarForm form = new GrammarForm();
        form.setGroupId(1);
        form.setLevel((short) 1);
        form.setPattern("-(으)니까");
        form.setTranslation("Bởi vì...");
        form.setStatus("ACTIVE");
        form.setIsFrequent(true);

        GrammarExampleForm exForm = new GrammarExampleForm();
        exForm.setSentenceKo("날씨가 좋으니까 산책해요.");
        exForm.setSentenceVi("Vì thời tiết đẹp nên đi dạo nhé.");
        form.setExamples(List.of(exForm));

        when(grammarGroupRepository.findById(1)).thenReturn(Optional.of(sampleGroup));
        when(grammarRepository.save(any(Grammar.class))).thenAnswer(invocation -> {
            Grammar g = invocation.getArgument(0);
            g.setId(20L);
            return g;
        });

        Grammar saved = grammarService.saveGrammar(form);

        assertNotNull(saved);
        assertEquals("-(으)니까", saved.getPattern());
        assertEquals("Bởi vì...", saved.getTranslation());
        assertEquals(1, saved.getExamples().size());
        verify(grammarRepository, times(1)).save(any(Grammar.class));
    }

    @Test
    @DisplayName("WF08 - Save grammar with mismatched level should throw exception")
    void testSaveGrammar_MismatchedLevel_ThrowsException() {
        GrammarForm form = new GrammarForm();
        form.setGroupId(1); // Group is Level 1
        form.setLevel((short) 2); // Form specifies Level 2
        form.setPattern("-(으)니까");
        form.setTranslation("Bởi vì...");
        form.setStatus("ACTIVE");

        when(grammarGroupRepository.findById(1)).thenReturn(Optional.of(sampleGroup));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            grammarService.saveGrammar(form);
        });
        assertTrue(ex.getMessage().contains("phải trùng với cấp TOPIK của nhóm"));
        verify(grammarRepository, never()).save(any());
    }

    @Test
    @DisplayName("WF08 - Delete grammar with valid ID calls repository delete")
    void testDeleteGrammar_Success() {
        when(grammarRepository.findById(10L)).thenReturn(Optional.of(sampleGrammar));
        doNothing().when(grammarRepository).delete(sampleGrammar);

        grammarService.deleteGrammar(10L);

        verify(grammarRepository, times(1)).delete(sampleGrammar);
    }
}
