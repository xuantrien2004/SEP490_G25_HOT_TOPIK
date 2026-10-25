package com.hottopik.service;

import com.hottopik.dto.ReviewDTO;
import com.hottopik.entity.Review;
import com.hottopik.entity.User;
import com.hottopik.repository.ReviewRepository;
import com.hottopik.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    // Lưu đánh giá mới từ người dùng (Trạng thái mặc định: PENDING)
    public Review saveReview(Long userId, ReviewDTO reviewDTO) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        Review review = Review.builder()
                .user(user)
                .rating((short) reviewDTO.getRating())
                .content(reviewDTO.getComment())
                .status("PENDING") // Chờ Operation Manager duyệt
                .build();

        return reviewRepository.save(review);
    }

    // Lấy danh sách các đánh giá đã được duyệt (APPROVED) và map sang ReviewDTO để trả về hiển thị công khai
    public List<ReviewDTO> getApprovedReviews() {
        List<Review> approvedReviews = reviewRepository.findByStatusOrderByCreatedAtDesc("APPROVED");

        return approvedReviews.stream().map(review -> {
            String authorName = (review.getUser() != null) ? review.getUser().getDisplayName() : "Ẩn danh";

            return new ReviewDTO(
                    review.getId(),
                    authorName,
                    review.getRating(),
                    review.getContent(),
                    true, // approved = true vì đã được duyệt
                    review.getCreatedAt() != null ? review.getCreatedAt().toString() : ""
            );
        }).collect(Collectors.toList());
    }
    // Thêm vào com.hottopik.service.ReviewService.java hiện tại của cậu:

    public double getAverageRating() {
        List<Review> approved = reviewRepository.findByStatusOrderByCreatedAtDesc("APPROVED");
        if (approved.isEmpty()) return 5.0; // Mặc định nếu chưa có đánh giá
        double sum = approved.stream().mapToInt(Review::getRating).sum();
        return Math.round((sum / approved.size()) * 10.0) / 10.0; // Làm tròn 1 chữ số thập phân
    }

    public long getTotalApprovedReviewsCount() {
        return reviewRepository.findByStatusOrderByCreatedAtDesc("APPROVED").size();
    }
}