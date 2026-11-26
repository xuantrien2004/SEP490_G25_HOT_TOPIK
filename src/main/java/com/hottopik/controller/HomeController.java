package com.hottopik.controller;

import com.hottopik.entity.Review;
import com.hottopik.repository.ReviewRepository;
import com.hottopik.service.ReviewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class HomeController {

    @Autowired
    private ReviewRepository reviewRepository; // Tiêm ReviewRepository vào đây

    @Autowired
    private ReviewService reviewService;

    @Autowired(required = false)
    private com.hottopik.service.subscription.SubscriptionPlanService planService;

    // Danh sách bình luận chỉ gồm review đang hiển thị, nhưng điểm trung bình + tổng số tính trên TẤT CẢ review
    // (kể cả review bị ẩn) và không phụ thuộc bộ lọc sao, để ẩn review không làm điểm trung bình đẹp hơn.
    private void addRatingSummary(Model model) {
        ReviewService.RatingSummary summary = reviewService.getRatingSummary();
        model.addAttribute("avgRating", summary.average());
        model.addAttribute("totalReviews", summary.total());
    }

    /** GET / – trang chủ; hiển thị đánh giá đang công khai (lọc theo số sao nếu có) và điểm trung bình đánh giá. */
    @Transactional(readOnly = true)
    @GetMapping("/")
    public String index(
            @RequestParam(value = "rating", required = false) Integer rating,
            Model model
    ) {
        List<Review> approvedReviews;

        // Lọc theo số sao hoặc lấy tất cả
        if (rating != null) {
            approvedReviews = reviewRepository.findByStatusAndRatingWithUser("APPROVED", rating.shortValue());
        } else {
            approvedReviews = reviewRepository.findByStatusWithUser("APPROVED");
        }

        // Truyền toàn bộ danh sách sang view để JS tự phân trang client
        model.addAttribute("approvedReviews", approvedReviews);
        model.addAttribute("selectedRating", rating);
        addRatingSummary(model);

        try {
            if (planService != null) {
                var activePlans = planService.getActivePlans();
                var vipPlans = activePlans.stream()
                        .filter(com.hottopik.dto.subscription.SubscriptionPlanDTO::isPremium)
                        .toList();
                model.addAttribute("vipPlans", vipPlans);
            }
        } catch (Exception ignored) {}

        model.addAttribute("pageTitle", "HOT TOPIK — Nền tảng luyện thi TOPIK chuẩn Hàn Quốc");
        model.addAttribute("activePage", "home");
        return "guest/home";
    }

    /** GET /ve-topik – trang Về TOPIK, kèm danh sách đánh giá công khai và điểm trung bình. */
    @Transactional(readOnly = true) // Giữ transaction cho phép Lazy Loading User cho trang Về TOPIK
    @GetMapping({"/ve-topik", "/about-topik"})
    public String aboutTopik(Model model) {
        // Lấy toàn bộ danh sách review đã duyệt kèm theo thông tin User để hiển thị ở trang Về TOPIK
        List<Review> approvedReviews = reviewRepository.findByStatusWithUser("APPROVED");

        model.addAttribute("approvedReviews", approvedReviews);
        addRatingSummary(model);

        model.addAttribute("pageTitle", "Về kỳ thi TOPIK · HOT TOPIK");
        model.addAttribute("activePage", "ve-topik");
        return "topik/index";
    }

    @GetMapping({"/lam-thu", "/trial"})
    public String trial(Model model) {
        model.addAttribute("pageTitle", "Làm thử 8 câu miễn phí · HOT TOPIK");
        model.addAttribute("activePage", "trial");
        return "guest/trial";
    }

    @GetMapping("/faq")
    public String faq(Model model) {
        model.addAttribute("pageTitle", "Câu hỏi thường gặp");
        model.addAttribute("activePage", "faq");
        return "info/faq";
    }

    @GetMapping("/terms")
    public String terms(Model model) {
        model.addAttribute("pageTitle", "Điều khoản & Chính sách bảo mật");
        model.addAttribute("activePage", "terms");
        return "info/terms";
    }
}