package com.hottopik.service;

import com.hottopik.dto.HanjaRootDTO;
import com.hottopik.entity.HanjaRoot;
import com.hottopik.repository.HanjaRootRepository;
import com.hottopik.repository.HanjaRootWordRepository;
import com.hottopik.repository.VocabularyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HanjaServiceTest {

    @Mock
    private HanjaRootRepository hanjaRootRepository;

    @Mock
    private HanjaRootWordRepository hanjaRootWordRepository;

    @Mock
    private VocabularyRepository vocabularyRepository;

    @InjectMocks
    private HanjaServiceImpl hanjaService;

    private HanjaRoot sampleRoot;

    @BeforeEach
    void setUp() {
        sampleRoot = HanjaRoot.builder()
                .id(1)
                .hanjaChar("學")
                .sinoVi("Học")
                .koReading("학")
                .meaningVi("Học tập, trường học")
                .status("ACTIVE")
                .sortOrder((short) 1)
                .build();
    }

    @Test
    @DisplayName("WF08 - Get all Hanja roots with query and status returns list")
    void testGetAllHanjaRoots_Success() {
        when(hanjaRootRepository.searchRoots("học", "ACTIVE")).thenReturn(List.of(sampleRoot));

        List<HanjaRootDTO> results = hanjaService.getAllHanjaRoots("học", "ACTIVE");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("學", results.get(0).getHanjaChar());
        assertEquals("Học", results.get(0).getSinoVi());
        assertEquals("학", results.get(0).getKoReading());
    }

    @Test
    @DisplayName("WF08 - Get Hanja root by ID when exists")
    void testGetHanjaRootById_Found() {
        when(hanjaRootRepository.findById(1)).thenReturn(Optional.of(sampleRoot));

        HanjaRootDTO dto = hanjaService.getHanjaRootById(1);

        assertNotNull(dto);
        assertEquals(1, dto.getId());
        assertEquals("Học", dto.getSinoVi());
    }

    @Test
    @DisplayName("WF08 - Get Hanja root by ID when not exists returns null")
    void testGetHanjaRootById_NotFound_ReturnsNull() {
        when(hanjaRootRepository.findById(99)).thenReturn(Optional.empty());

        HanjaRootDTO dto = hanjaService.getHanjaRootById(99);

        assertNull(dto);
    }

    @Test
    @DisplayName("WF08 - Toggle Hanja root status from ACTIVE to DRAFT")
    void testToggleRootStatus_ActiveToDraft() {
        sampleRoot.setStatus("ACTIVE");
        when(hanjaRootRepository.findById(1)).thenReturn(Optional.of(sampleRoot));
        when(hanjaRootRepository.save(any(HanjaRoot.class))).thenAnswer(i -> i.getArgument(0));

        HanjaRootDTO result = hanjaService.toggleRootStatus(1);

        assertNotNull(result);
        assertEquals("DRAFT", result.getStatus());
        verify(hanjaRootRepository, times(1)).save(sampleRoot);
    }

    @Test
    @DisplayName("WF08 - Get active Hanja roots returns active list")
    void testGetActiveHanjaRoots() {
        when(hanjaRootRepository.searchRoots(null, "ACTIVE")).thenReturn(List.of(sampleRoot));

        List<HanjaRootDTO> activeRoots = hanjaService.getActiveHanjaRoots(null);

        assertNotNull(activeRoots);
        assertEquals(1, activeRoots.size());
        assertEquals("ACTIVE", activeRoots.get(0).getStatus());
    }

    @Test
    @DisplayName("WF08 - Get learner Hanja statistics")
    void testGetLearnerStats() {
        when(hanjaRootRepository.count()).thenReturn(32L);
        when(hanjaRootRepository.countByStatus("ACTIVE")).thenReturn(30L);
        when(hanjaRootWordRepository.count()).thenReturn(150L);

        Map<String, Object> stats = hanjaService.getLearnerStats();

        assertNotNull(stats);
        assertEquals(32L, stats.get("totalRoots"));
        assertEquals(30L, stats.get("activeRoots"));
        assertEquals(150L, stats.get("totalWords"));
    }

    @Test
    @DisplayName("WF08 - Delete Hanja root deletes root and its relations")
    void testDeleteHanjaRoot_Success() {
        when(hanjaRootRepository.existsById(1)).thenReturn(true);
        doNothing().when(hanjaRootWordRepository).deleteByRootId(1);
        doNothing().when(hanjaRootRepository).deleteById(1);

        hanjaService.deleteHanjaRoot(1);

        verify(hanjaRootWordRepository, times(1)).deleteByRootId(1);
        verify(hanjaRootRepository, times(1)).deleteById(1);
    }
}
