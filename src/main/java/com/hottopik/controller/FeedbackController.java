package com.hottopik.controller;

import com.hottopik.dto.ReviewDTO;
import com.hottopik.dto.UserDTO;
import com.hottopik.entity.User;
import com.hottopik.repository.UserRepository;
import com.hottopik.service.ReviewService;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;

/** API đánh giá website cho học viên: gửi / sửa đánh giá và lấy đánh giá hiện có của chính mình. */
@Controller
@RequiredArgsConstructor
public class FeedbackController {

    private final ReviewService reviewService;
    private final UserRepository userRepository;

    private Long currentUserId(HttpSession session, Principal principal) {
        if (principal != null) {
            User currentUser = userRepository.findByEmail(principal.getName()).orElse(null);
            if (currentUser != null) return currentUser.getId();
        }
        UserDTO loggedIn = session != null ? AuthHelper.getLoggedInUser(session) : null;
        return loggedIn != null ? loggedIn.getId() : null;
    }

    /** Mỗi học viên 1 đánh giá: lần đầu tạo mới, các lần sau cập nhật chính đánh giá đó. */
    @PostMapping("/api/feedback/submit")
    @ResponseBody
    public ResponseEntity<?> submitFeedback(@RequestBody ReviewDTO reviewDTO, HttpSession session, Principal principal) {
        Long currentUserId = currentUserId(session, principal);
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse(false, "Cậu cần đăng nhập để gửi đánh giá nhé!"));
        }
        try {
            ReviewService.SaveResult result = reviewService.saveReview(currentUserId, reviewDTO);
            return ResponseEntity.ok().body(new ApiResponse(true, result.created()
                    ? "Cảm ơn cậu đã gửi đánh giá! Đánh giá của cậu đã được đăng tải thành công."
                    : "Đã cập nhật đánh giá của cậu!"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Không thể lưu đánh giá lúc này, cậu thử lại sau nhé."));
        }
    }

    /** Đánh giá hiện có của người đăng nhập, để form tự điền sẵn khi mở lại. */
    @GetMapping("/api/feedback/mine")
    @ResponseBody
    public ResponseEntity<?> myFeedback(HttpSession session, Principal principal) {
        Long currentUserId = currentUserId(session, principal);
        if (currentUserId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(java.util.Map.of("exists", false));
        return reviewService.getMyReview(currentUserId)
                .<ResponseEntity<?>>map(r -> ResponseEntity.ok(java.util.Map.of(
                        "exists", true,
                        "rating", (int) r.getRating(),
                        "comment", r.getContent(),
                        "visible", "APPROVED".equalsIgnoreCase(r.getStatus()))))
                .orElseGet(() -> ResponseEntity.ok(java.util.Map.of("exists", false)));
    }

    // Class phụ trợ trả về JSON
    @AllArgsConstructor
    @Getter
    @Setter
    public static class ApiResponse {
        private boolean success;
        private String message;
    }
}
