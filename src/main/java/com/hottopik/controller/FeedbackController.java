package com.hottopik.controller;

import com.hottopik.dto.ReviewDTO;
import com.hottopik.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class FeedbackController {

    private final ReviewService reviewService;

    @PostMapping("/api/feedback/submit")
    @ResponseBody
    public ResponseEntity<?> submitFeedback(@RequestBody ReviewDTO reviewDTO) {
        try {
            // Tạm thời gán user mẫu ID = 1 (Sau này tích hợp Spring Security sẽ lấy user đang đăng nhập)
            Long currentUserId = 1L;

            reviewService.saveReview(currentUserId, reviewDTO);

            return ResponseEntity.ok().body(new ApiResponse(true, "Cảm ơn cậu đã gửi đánh giá! Ý kiến đang chờ kiểm duyệt."));
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