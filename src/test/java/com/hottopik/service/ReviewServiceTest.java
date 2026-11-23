package com.hottopik.service;

import com.hottopik.dto.ReviewDTO;
import com.hottopik.entity.Review;
import com.hottopik.entity.User;
import com.hottopik.repository.ReviewRepository;
import com.hottopik.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewServiceTest {
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ReviewService service = new ReviewService(reviews, users);

    ReviewServiceTest() {
        when(reviews.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ReviewDTO dto(int rating, String comment) {
        ReviewDTO d = new ReviewDTO();
        d.setRating(rating);
        d.setComment(comment);
        return d;
    }

    @Test
    void firstReviewIsCreatedAndPublished() {
        when(reviews.findFirstByUser_IdOrderByCreatedAtDescIdDesc(7L)).thenReturn(Optional.empty());
        when(users.findById(7L)).thenReturn(Optional.of(new User()));

        ReviewService.SaveResult result = service.saveReview(7L, dto(4, "  Rất hữu ích  "));

        assertTrue(result.created());
        assertEquals((short) 4, result.review().getRating());
        assertEquals("Rất hữu ích", result.review().getContent());
        assertEquals("APPROVED", result.review().getStatus());
    }

    @Test
    void secondReviewUpdatesTheExistingOneInsteadOfCreating() {
        Review existing = Review.builder().id(3L).rating((short) 2).content("cũ").status("APPROVED").build();
        when(reviews.findFirstByUser_IdOrderByCreatedAtDescIdDesc(7L)).thenReturn(Optional.of(existing));

        ReviewService.SaveResult result = service.saveReview(7L, dto(5, "mới"));

        assertFalse(result.created());
        assertSame(existing, result.review());
        assertEquals((short) 5, existing.getRating());
        assertEquals("mới", existing.getContent());
        verify(reviews, times(1)).save(existing);
        verifyNoInteractions(users);
    }

    @Test
    void editingAHiddenReviewKeepsItHidden() {
        Review hidden = Review.builder().id(3L).rating((short) 1).content("spam").status("PENDING").build();
        when(reviews.findFirstByUser_IdOrderByCreatedAtDescIdDesc(7L)).thenReturn(Optional.of(hidden));

        service.saveReview(7L, dto(5, "đã sửa"));

        assertEquals("PENDING", hidden.getStatus());
    }

    @Test
    void rejectsInvalidRatingOrEmptyOrTooLongComment() {
        assertThrows(IllegalArgumentException.class, () -> service.saveReview(7L, dto(0, "ok")));
        assertThrows(IllegalArgumentException.class, () -> service.saveReview(7L, dto(6, "ok")));
        assertThrows(IllegalArgumentException.class, () -> service.saveReview(7L, dto(5, "   ")));
        assertThrows(IllegalArgumentException.class, () -> service.saveReview(7L, dto(5, "a".repeat(1001))));
        verify(reviews, never()).save(any());
    }
}
