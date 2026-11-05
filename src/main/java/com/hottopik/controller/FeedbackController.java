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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class FeedbackController {

    private final ReviewService reviewService;
    private final UserRepository userRepository;

    @PostMapping("/api/feedback/submit")
    @ResponseBody
    public ResponseEntity<?> submitFeedback(@RequestBody ReviewDTO reviewDTO, HttpSession session, Principal principal) {
        try {
            Long currentUserId = null;
            if (principal != null) {
                String email = principal.getName();
                User currentUser = userRepository.findByEmail(email).orElse(null);
                if (currentUser != null) {
                    currentUserId = currentUser.getId();
                }
            }
            if (currentUserId == null && session != null) {
                UserDTO loggedIn = AuthHelper.getLoggedInUser(session);
                if (loggedIn != null) {
                    currentUserId = loggedIn.getId();
                }
            }

            if (currentUserId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "Cậu cần đăng nhập để gửi đánh giá nhé!"));
            }

            reviewService.saveReview(currentUserId, reviewDTO);

            return ResponseEntity.ok().body(new ApiResponse(true, "Cảm ơn cậu đã gửi đánh giá! Đánh giá của cậu đã được đăng tải thành công."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Lỗi: " + e.getMessage()));
        }
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
