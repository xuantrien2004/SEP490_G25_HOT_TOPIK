package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.StudyPlan;
import com.hottopik.entity.exam.Track;
import com.hottopik.service.exam.ExamDomainException;
import com.hottopik.service.exam.LearnerRoadmapService;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LearnerController {

    private final LearnerRoadmapService roadmaps;

    public LearnerController(LearnerRoadmapService roadmaps) {
        this.roadmaps = roadmaps;
    }

    @GetMapping({"/trang-chu", "/learner/home"})
    public String home() {
        return "redirect:/hom-nay";
    }

    @GetMapping({"/hom-nay", "/today", "/dashboard"})
    public String today(HttpSession session, Model model) {
        model.addAttribute("pageTitle", "Hôm nay");
        model.addAttribute("activePage", "today");
        try {
            StudyPlan plan = roadmaps.ensure(owner(session), trackFor(session));
            model.addAttribute("board", roadmaps.todayBoard(plan, LearnerRoadmapService.givenName(displayName(session))));
        } catch (ExamDomainException e) {
            model.addAttribute("loadError", e.getMessage());
        }
        return "learner/today";
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

    private static Track trackFor(HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        String level = user == null || user.getTargetLevel() == null ? "" : user.getTargetLevel().toUpperCase();
        if (level.equals("TOPIK_I") || level.equals("TOPIK_1") || level.equals("TOPIK_2")
                || level.endsWith("_1") || level.endsWith("_2")) {
            return Track.TOPIK_I;
        }
        return Track.TOPIK_II;
    }

    private static String displayName(HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) return user.getFullName();
        return "bạn";
    }

    private static String owner(HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail().trim().toLowerCase();
        }
        return "guest";
    }
}
