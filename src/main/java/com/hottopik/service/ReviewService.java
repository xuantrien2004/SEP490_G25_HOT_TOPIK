package com.hottopik.service;

import com.hottopik.dto.ReviewDTO;
import com.hottopik.entity.Review;
import com.hottopik.entity.User;
import com.hottopik.repository.ReviewRepository;
import com.hottopik.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    /** Kết quả lưu: created=true khi tạo mới, false khi cập nhật đánh giá đã có. */
    public record SaveResult(Review review, boolean created) {}

    /**
     * Mỗi học viên chỉ có 1 đánh giá: chưa có thì tạo mới (đăng ngay, APPROVED như trước),
     * đã có thì cập nhật số sao + nội dung của chính đánh giá đó.
     * Cập nhật giữ nguyên trạng thái hiển thị: đánh giá đã bị Operation Manager ẩn vẫn ẩn,
     * để không lách kiểm duyệt bằng cách sửa nội dung.
     */
    @Transactional
    public SaveResult saveReview(Long userId, ReviewDTO reviewDTO) {
        if (reviewDTO == null) throw new IllegalArgumentException("Dữ liệu đánh giá không hợp lệ.");
        int rating = reviewDTO.getRating();
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("Số sao phải từ 1 đến 5.");
        String content = reviewDTO.getComment() == null ? "" : reviewDTO.getComment().trim();
        if (content.isEmpty()) throw new IllegalArgumentException("Cậu vui lòng nhập nội dung góp ý nhé!");
        if (content.length() > 1000) throw new IllegalArgumentException("Nội dung đánh giá tối đa 1000 ký tự.");

        Review existing = reviewRepository.findFirstByUser_IdOrderByCreatedAtDescIdDesc(userId).orElse(null);
        if (existing != null) {
            existing.setRating((short) rating);
            existing.setContent(content);
            return new SaveResult(reviewRepository.save(existing), false);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
        Review review = Review.builder()
                .user(user)
                .rating((short) rating)
                .content(content)
                .status("APPROVED")
                .build();
        return new SaveResult(reviewRepository.save(review), true);
    }

    /** Đánh giá hiện có của học viên (để điền sẵn vào form sửa), rỗng nếu chưa đánh giá. */
    @Transactional(readOnly = true)
    public java.util.Optional<Review> getMyReview(Long userId) {
        if (userId == null) return java.util.Optional.empty();
        return reviewRepository.findFirstByUser_IdOrderByCreatedAtDescIdDesc(userId);
    }

    // Lấy danh sách các đánh giá đã được duyệt (APPROVED) kèm JOIN FETCH để tránh LazyInitializationException
    @Transactional(readOnly = true)
    public List<ReviewDTO> getApprovedReviews() {
        List<Review> approvedReviews = reviewRepository.findByStatusWithUser("APPROVED");

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

    @Transactional(readOnly = true)
    public double getAverageRating() {
        List<Review> approved = reviewRepository.findByStatusWithUser("APPROVED");
        if (approved.isEmpty()) return 5.0; // Mặc định nếu chưa có đánh giá
        double sum = approved.stream().mapToInt(Review::getRating).sum();
        return Math.round((sum / approved.size()) * 10.0) / 10.0; // Làm tròn 1 chữ số thập phân
    }

    @Transactional(readOnly = true)
    public long getTotalApprovedReviewsCount() {
        return reviewRepository.findByStatusWithUser("APPROVED").size();
    }
}