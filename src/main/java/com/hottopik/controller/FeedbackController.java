package com.hottopik.controller;

import com.hottopik.dto.ReviewDTO;
import com.hottopik.entity.User; // <-- Dùng đúng entity User của dự án
import com.hottopik.repository.UserRepository; // <-- Thêm repository để tìm user theo email
import com.hottopik.service.ReviewService;
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

import java.security.Principal; // <-- Thêm import Principal

@Controller
@RequiredArgsConstructor
public class FeedbackController {

    private final ReviewService reviewService;
    private final UserRepository userRepository; // <-- Tiêm UserRepository vào đây

    @PostMapping("/api/feedback/submit")
    @ResponseBody
    public ResponseEntity<?> submitFeedback(@RequestBody ReviewDTO reviewDTO, Principal principal) {
        try {
            if (principal == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiResponse(false, "Cậu cần đăng nhập để gửi đánh giá nhé!"));
            }

            // Lấy email từ principal (Spring Security mặc định dùng username/email làm principal name)
            String email = principal.getName();
            User currentUser = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin người dùng"));

            reviewService.saveReview(currentUser.getId(), reviewDTO);

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