package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.service.UserService;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import com.hottopik.entity.exam.StudyPlan;
import com.hottopik.entity.exam.Track;
import com.hottopik.service.exam.ExamDomainException;
import com.hottopik.service.exam.LearnerRoadmapService;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class LearnerController {

    private final UserService userService;
    private final LearnerRoadmapService roadmaps;
    public LearnerController(UserService userService, LearnerRoadmapService roadmaps) {
        this.userService = userService;
        this.roadmaps = roadmaps;
    }

    @GetMapping({"/trang-chu", "/learner/home"})
    public String home() {
        return "redirect:/hom-nay";
    }

    @GetMapping({"/hom-nay", "/today", "/dashboard"})
    public String today(Model model, HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user != null && user.isStudent() && (user.getTargetLevel() == null || "TOPIK_0".equals(user.getTargetLevel()))) {
            return "redirect:/onboarding";
        }
        model.addAttribute("pageTitle", "Nhiệm vụ hôm nay · HOT TOPIK");
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
    public String profile(Model model, HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user != null) {
            model.addAttribute("user", user);
        }
        model.addAttribute("pageTitle", "Hồ sơ cá nhân");
        model.addAttribute("activePage", "profile");
        return "learner/profile";
    }

    @PostMapping("/api/user/update-profile")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleUpdateProfile(
            @RequestParam("displayName") String displayName,
            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) {
            resp.put("success", false);
            resp.put("message", "Vui lòng đăng nhập lại để thực hiện!");
            return ResponseEntity.status(401).body(resp);
        }
        if (displayName == null || displayName.trim().length() < 2 || displayName.trim().length() > 40) {
            resp.put("success", false);
            resp.put("message", "Tên hiển thị phải từ 2 đến 40 ký tự!");
            return ResponseEntity.badRequest().body(resp);
        }
        boolean ok = userService.updateDisplayName(user.getId(), displayName.trim());
        if (ok) {
            user.setDisplayName(displayName.trim());
            session.setAttribute("LOGGED_IN_USER", user);
            resp.put("success", true);
            resp.put("displayName", displayName.trim());
            resp.put("message", "Cập nhật tên hiển thị thành công!");
            return ResponseEntity.ok(resp);
        } else {
            resp.put("success", false);
            resp.put("message", "Không thể cập nhật thông tin lúc này!");
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @PostMapping("/api/user/change-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleChangePassword(
            @RequestParam("oldPassword") String oldPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) {
            resp.put("success", false);
            resp.put("message", "Vui lòng đăng nhập lại để thực hiện!");
            return ResponseEntity.status(401).body(resp);
        }
        if (!newPassword.equals(confirmPassword)) {
            resp.put("success", false);
            resp.put("message", "Mật khẩu xác nhận không khớp!");
            return ResponseEntity.badRequest().body(resp);
        }
        if (newPassword.length() < 6) {
            resp.put("success", false);
            resp.put("message", "Mật khẩu mới phải từ 6 ký tự trở lên!");
            return ResponseEntity.badRequest().body(resp);
        }
        boolean ok = userService.changePassword(user.getId(), oldPassword, newPassword);
        if (ok) {
            resp.put("success", true);
            resp.put("message", "Đổi mật khẩu thành công!");
            return ResponseEntity.ok(resp);
        } else {
            resp.put("success", false);
            resp.put("message", "Mật khẩu hiện tại không đúng. Vui lòng kiểm tra lại!");
            return ResponseEntity.badRequest().body(resp);
        }
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
