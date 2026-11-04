package com.hottopik.controller;

import com.hottopik.entity.Review;
import com.hottopik.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    @Transactional(readOnly = true)
    @GetMapping("/")
    public String index(
            @RequestParam(value = "rating", required = false) Integer rating,
            Model model
    ) {
        List<Review> approvedReviews;

        // Lọc theo số sao hoặc lấy tất cả
        if (rating != null) {
            approvedReviews = reviewRepository.findByStatusAndRating("APPROVED", rating.shortValue(), Sort.by("createdAt").descending());
        } else {
            approvedReviews = reviewRepository.findByStatusWithUser("APPROVED");
        }

        double avgRating = 5.0;
        int totalReviews = approvedReviews.size();

        if (totalReviews > 0) {
            double sum = 0;
            for (Review r : approvedReviews) {
                sum += (r.getRating() != null ? r.getRating() : 5);
            }
            avgRating = Math.round((sum / totalReviews) * 10.0) / 10.0;
        }

        // Truyền toàn bộ danh sách sang view để JS tự phân trang client
        model.addAttribute("approvedReviews", approvedReviews);
        model.addAttribute("selectedRating", rating);
        model.addAttribute("avgRating", avgRating);
        model.addAttribute("totalReviews", totalReviews);

        model.addAttribute("pageTitle", "HOT TOPIK — Nền tảng luyện thi TOPIK chuẩn Hàn Quốc");
        model.addAttribute("activePage", "home");
        return "guest/home";
    }

    @Transactional(readOnly = true) // Giữ transaction cho phép Lazy Loading User cho trang Về TOPIK
    @GetMapping({"/ve-topik", "/about-topik"})
    public String aboutTopik(Model model) {
        // Lấy toàn bộ danh sách review đã duyệt kèm theo thông tin User để hiển thị ở trang Về TOPIK
        List<Review> approvedReviews = reviewRepository.findByStatusWithUser("APPROVED");

        double avgRating = 5.0;
        int totalReviews = approvedReviews.size();

        if (totalReviews > 0) {
            double sum = 0;
            for (Review r : approvedReviews) {
                sum += (r.getRating() != null ? r.getRating() : 5);
            }
            avgRating = Math.round((sum / totalReviews) * 10.0) / 10.0;
        }

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