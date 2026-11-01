package com.hottopik.controller;

import com.hottopik.entity.Review;
import com.hottopik.repository.ReviewRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    @Autowired
    private ReviewRepository reviewRepository; // Tiêm ReviewRepository vào đây

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("pageTitle", "HOT TOPIK — Nền tảng luyện thi TOPIK chuẩn Hàn Quốc");
        model.addAttribute("activePage", "home");
        return "guest/home";
    }

    @Transactional(readOnly = true) // Thêm dòng này để giữ transaction cho phép Lazy Loading User
    @GetMapping({"/ve-topik", "/about-topik"})
    public String aboutTopik(Model model) {
        // 1. Lấy danh sách review kèm sẵn User bằng JOIN FETCH
        List<Review> approvedReviews = reviewRepository.findByStatusWithUser("APPROVED");

        // 2. Tính toán điểm trung bình (avgRating) và tổng số lượng (totalReviews)
        double avgRating = 5.0;
        int totalReviews = approvedReviews.size();

        if (totalReviews > 0) {
            double sum = 0;
            for (Review r : approvedReviews) {
                sum += (r.getRating() != null ? r.getRating() : 5);
            }
            avgRating = Math.round((sum / totalReviews) * 10.0) / 10.0;
        }

        // 3. Đưa vào Model để trang topik/index hiển thị
        model.addAttribute("approvedReviews", approvedReviews);
        model.addAttribute("avgRating", avgRating);
        model.addAttribute("totalReviews", totalReviews);

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