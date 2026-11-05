package com.hottopik.service;

import com.hottopik.dto.GrammarGroupForm;
import com.hottopik.entity.GrammarGroup;
import com.hottopik.repository.GrammarGroupRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GrammarGroupServiceTest {
    private final GrammarGroupRepository repo = mock(GrammarGroupRepository.class);
    private final GrammarGroupService service = new GrammarGroupService(repo);

    private static GrammarGroup group(int id, int level, int no, String name) {
        GrammarGroup g = new GrammarGroup();
        g.setId(id);
        g.setLevel((short) level);
        g.setGroupNo((short) no);
        g.setNameVi(name);
        g.setStatus("ACTIVE");
        return g;
    }

    private static GrammarGroupForm form(Integer id, int level, String name) {
        GrammarGroupForm f = new GrammarGroupForm();
        f.setId(id);
        f.setLevel((short) level);
        f.setNameVi(name);
        return f;
    }

    private void levelHas(int level, GrammarGroup... groups) {
        when(repo.findByLevelOrderByGroupNoAsc((short) level)).thenReturn(new ArrayList<>(List.of(groups)));
    }

    private GrammarGroup savedEntity() {
        ArgumentCaptor<GrammarGroup> captor = ArgumentCaptor.forClass(GrammarGroup.class);
        verify(repo).save(captor.capture());
        return captor.getValue();
    }

    // Giống DB: bản ghi mới được gán id khi lưu.
    private void stubSaveAssigningId() {
        when(repo.save(any())).thenAnswer(i -> {
            GrammarGroup g = i.getArgument(0);
            if (g.getId() == null) g.setId(100);
            return g;
        });
    }

    @Test
    void createsWithoutGroupNoAndPicksSmallestFreeNumber() {
        levelHas(6, group(1, 6, 1, "A"), group(2, 6, 2, "B"), group(3, 6, 4, "C"));
        stubSaveAssigningId();

        service.saveGroup(form(null, 6, "  Nhóm mới  "));

        GrammarGroup saved = savedEntity();
        assertEquals((short) 3, saved.getGroupNo());
        assertEquals((short) 6, saved.getLevel());
        assertEquals("Nhóm mới", saved.getNameVi());
        assertEquals("ACTIVE", saved.getStatus());
    }

    @Test
    void rejectsDuplicateNameInSameLevelIgnoringCaseAndSpaces() {
        levelHas(6, group(1, 6, 1, "Nối mệnh đề"));

        var error = assertThrows(IllegalArgumentException.class,
                () -> service.saveGroup(form(null, 6, "  nối MỆNH đề ")));

        assertTrue(error.getMessage().contains("đã tồn tại trong TOPIK 6"));
        verify(repo, never()).save(any());
    }

    @Test
    void allowsSameNameInAnotherLevel() {
        levelHas(5);
        stubSaveAssigningId();

        service.saveGroup(form(null, 5, "Nối mệnh đề"));

        assertEquals((short) 1, savedEntity().getGroupNo());
        verify(repo).findByLevelOrderByGroupNoAsc((short) 5);
        verify(repo, never()).findByLevelOrderByGroupNoAsc((short) 6);
    }

    @Test
    void renameKeepsGroupNoAndAllowsSameNameForItself() {
        GrammarGroup existing = group(7, 6, 3, "Cũ");
        levelHas(6, existing, group(8, 6, 1, "Khác"));
        when(repo.findById(7)).thenReturn(Optional.of(existing));
        stubSaveAssigningId();

        service.saveGroup(form(7, 6, "cũ"));

        GrammarGroup saved = savedEntity();
        assertEquals((short) 3, saved.getGroupNo());
        assertEquals("cũ", saved.getNameVi());
    }

    @Test
    void renameToAnotherGroupNameInSameLevelIsRejected() {
        GrammarGroup existing = group(7, 6, 3, "Cũ");
        levelHas(6, existing, group(8, 6, 1, "Khác"));

        assertThrows(IllegalArgumentException.class, () -> service.saveGroup(form(7, 6, "Khác")));
        verify(repo, never()).save(any());
    }

    @Test
    void rejectsWhenLevelAlreadyHasFourteenGroups() {
        GrammarGroup[] full = new GrammarGroup[14];
        for (int i = 0; i < 14; i++) full[i] = group(i + 1, 1, i + 1, "N" + i);
        levelHas(1, full);

        var error = assertThrows(IllegalArgumentException.class, () -> service.saveGroup(form(null, 1, "Mới")));

        assertTrue(error.getMessage().contains("tối đa 14"));
        verify(repo, never()).save(any());
    }
}
