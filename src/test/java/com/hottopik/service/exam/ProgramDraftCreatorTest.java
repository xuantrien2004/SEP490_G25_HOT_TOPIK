package com.hottopik.service.exam;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.ProgramStatus;
import com.hottopik.entity.exam.ProgramVersion;
import com.hottopik.repository.exam.ProgramVersionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProgramDraftCreatorTest {
    @Autowired ProgramRoadmapService service;
    @Autowired ProgramVersionRepository programs;
    @Autowired EntityManager em;

    @Test
    void draftPersistsAccountIdInsteadOfDisplayNameAndCopiesPublishedItems() {
        ProgramVersion source = service.published();
        assertNotNull(source);
        assertNull(source.getCreatedBy(), "System seed must not use a fictitious user ID");
        var sourceItems = service.itemsOf(source.getId()).stream()
                .map(item -> item.getTemplate().getId() + ":" + item.getOrderNo()).toList();
        assertFalse(sourceItems.isEmpty());
        UserDTO user = new UserDTO();
        user.setId(42L);
        user.setFullName("Nguyễn Đức Quý");
        user.setRole(Actor.ROLE_CONTENT);

        Long draftId = service.createDraft(Actor.of(user)).getId();
        em.flush();
        em.clear();

        ProgramVersion draft = programs.findById(draftId).orElseThrow();
        assertEquals(42L, draft.getCreatedBy());
        assertEquals(ProgramStatus.DRAFT, draft.getStatus());
        assertEquals(ProgramStatus.PUBLISHED, programs.findById(source.getId()).orElseThrow().getStatus());
        assertEquals(source.getSessionsPerType(), draft.getSessionsPerType());
        assertEquals(source.getQuestionsPerSession(), draft.getQuestionsPerSession());
        assertEquals(sourceItems, service.itemsOf(draftId).stream()
                .map(item -> item.getTemplate().getId() + ":" + item.getOrderNo()).toList());
    }
}
