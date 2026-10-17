package com.hottopik.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LearnerController {

    @GetMapping({"/trang-chu", "/learner/home"})
    public String home(Model model) {
        model.addAttribute("pageTitle", "Trang chủ học viên · HOT TOPIK");
        model.addAttribute("activePage", "home");
        return "learner/home";
    }

    @GetMapping({"/hom-nay", "/today", "/dashboard"})
    public String today(Model model) {
        model.addAttribute("pageTitle", "Nhiệm vụ hôm nay · HOT TOPIK");
        model.addAttribute("activePage", "today");
        return "learner/dashboard";
    }

    @GetMapping({"/tien-do", "/progress"})
    public String progress(Model model) {
        model.addAttribute("pageTitle", "Phân tích tiến độ học tập");
        model.addAttribute("activePage", "progress");
        return "progress/index";
    }

    @GetMapping({"/lich-su", "/history"})
    public String history(Model model) {
        model.addAttribute("pageTitle", "Lịch sử làm bài");
        model.addAttribute("activePage", "history");
        return "progress/history";
    }

    @GetMapping({"/ho-so", "/profile"})
    public String profile(Model model) {
        model.addAttribute("pageTitle", "Hồ sơ cá nhân");
        model.addAttribute("activePage", "profile");
        return "learner/profile";
    }

    @GetMapping({"/cai-dat", "/settings"})
    public String settings(Model model) {
        model.addAttribute("pageTitle", "Cài đặt tài khoản & học tập");
        model.addAttribute("activePage", "settings");
        return "learner/settings";
    }

    @GetMapping({"/thong-bao", "/notifications"})
    public String notifications(Model model) {
        model.addAttribute("pageTitle", "Trung tâm thông báo");
        model.addAttribute("activePage", "notifications");
        return "learner/notifications";
    }

    @GetMapping({"/xu", "/coins"})
    public String coins(Model model) {
        model.addAttribute("pageTitle", "Xu của bạn & Đổi quà");
        model.addAttribute("activePage", "coins");
        return "learner/coins";
    }

    @GetMapping({"/bai-cham", "/mentor-reviews"})
    public String mentorReviews(Model model) {
        model.addAttribute("pageTitle", "Bài Viết gửi Mentor chấm");
        model.addAttribute("activePage", "mentor-reviews");
        return "learner/mentor-reviews";
    }
}
