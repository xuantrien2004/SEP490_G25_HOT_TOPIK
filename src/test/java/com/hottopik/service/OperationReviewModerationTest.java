package com.hottopik.service;

import com.hottopik.entity.Review;
import com.hottopik.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** OperationService.toggleReviewApproval: Operation Manager ẩn / hiện review (APPROVED <-> PENDING). */
class OperationReviewModerationTest {
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final OperationService service = new OperationService();

    OperationReviewModerationTest() {
        ReflectionTestUtils.setField(service, "reviewRepository", reviews);
    }

    private Review stored(long id, String status) {
        Review r = Review.builder().id(id).rating((short) 4).content("ok").status(status).build();
        when(reviews.findById(id)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void hidesVisibleReview() {
        Review r = stored(1L, "APPROVED");

        assertTrue(service.toggleReviewApproval(1L));
        assertEquals("PENDING", r.getStatus());
        verify(reviews).save(r);
    }

    @Test
    void showsHiddenReviewAgain() {
        Review r = stored(2L, "PENDING");

        assertTrue(service.toggleReviewApproval(2L));
        assertEquals("APPROVED", r.getStatus());
        verify(reviews).save(r);
    }

    @Test
    void lowercaseApprovedIsTreatedAsVisible() {
        Review r = stored(3L, "approved");

        assertTrue(service.toggleReviewApproval(3L));
        assertEquals("PENDING", r.getStatus());
    }

    @Test
    void unknownReviewReturnsFalse() {
        when(reviews.findById(99L)).thenReturn(Optional.empty());

        assertFalse(service.toggleReviewApproval(99L));
        verify(reviews, never()).save(any());
    }

    @Test
    void nullIdReturnsFalseWithoutQuerying() {
        assertFalse(service.toggleReviewApproval(null));
        verifyNoInteractions(reviews);
    }
}
