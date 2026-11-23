package com.hottopik.service;

import com.hottopik.entity.Grammar;
import com.hottopik.entity.GrammarGroup;
import com.hottopik.entity.UserSavedGrammar;
import com.hottopik.repository.GrammarRepository;
import com.hottopik.repository.UserSavedGrammarRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SavedGrammarServiceTest {
    private final UserSavedGrammarRepository saved = mock(UserSavedGrammarRepository.class);
    private final GrammarRepository grammars = mock(GrammarRepository.class);
    private final SavedGrammarService service = new SavedGrammarService(saved, grammars);

    private static Grammar grammar(long id, String status, String groupStatus) {
        GrammarGroup group = new GrammarGroup();
        group.setId(3);
        group.setStatus(groupStatus);
        Grammar g = new Grammar();
        g.setId(id);
        g.setStatus(status);
        g.setGrammarGroup(group);
        return g;
    }

    @Test
    void savesActiveGrammarForUser() {
        when(saved.findByUserIdAndGrammar_Id(9L, 5L)).thenReturn(Optional.empty());
        when(grammars.findDetailById(5L)).thenReturn(Optional.of(grammar(5, "ACTIVE", "ACTIVE")));

        assertTrue(service.toggle(9L, 5L));
        ArgumentCaptor<UserSavedGrammar> captor = ArgumentCaptor.forClass(UserSavedGrammar.class);
        verify(saved).save(captor.capture());
        assertEquals(9L, captor.getValue().getUserId());
        assertEquals(5L, captor.getValue().getGrammar().getId());
    }

    @Test
    void secondToggleRemovesSavedGrammar() {
        UserSavedGrammar row = new UserSavedGrammar();
        when(saved.findByUserIdAndGrammar_Id(9L, 5L)).thenReturn(Optional.of(row));

        assertFalse(service.toggle(9L, 5L));
        verify(saved).delete(row);
        verify(saved, never()).save(any());
    }

    @Test
    void cannotSaveHiddenGrammarOrGrammarInHiddenGroup() {
        when(saved.findByUserIdAndGrammar_Id(any(), any())).thenReturn(Optional.empty());
        when(grammars.findDetailById(1L)).thenReturn(Optional.of(grammar(1, "DRAFT", "ACTIVE")));
        when(grammars.findDetailById(2L)).thenReturn(Optional.of(grammar(2, "ACTIVE", "HIDDEN")));
        when(grammars.findDetailById(3L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.toggle(9L, 1L));
        assertThrows(IllegalArgumentException.class, () -> service.toggle(9L, 2L));
        assertThrows(IllegalArgumentException.class, () -> service.toggle(9L, 3L));
        verify(saved, never()).save(any());
    }

    @Test
    void canStillUnsaveAfterGrammarIsHidden() {
        UserSavedGrammar row = new UserSavedGrammar();
        row.setGrammar(grammar(5, "HIDDEN", "ACTIVE"));
        when(saved.findByUserIdAndGrammar_Id(9L, 5L)).thenReturn(Optional.of(row));

        assertFalse(service.toggle(9L, 5L));
        verify(saved).delete(row);
        verifyNoInteractions(grammars);
    }

    @Test
    void guestCannotSave() {
        assertThrows(IllegalArgumentException.class, () -> service.toggle(null, 5L));
        assertTrue(service.getSavedGrammarIds(null).isEmpty());
        assertFalse(service.isSaved(null, 5L));
    }
}
