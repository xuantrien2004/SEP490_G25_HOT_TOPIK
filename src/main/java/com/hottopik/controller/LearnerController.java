package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.service.UserService;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

    @Autowired
    public LearnerController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping({"/trang-chu", "/learner/home"})
    public String home(Model model) {
        model.addAttribute("pageTitle", "Trang chủ học viên · HOT TOPIK");
        model.addAttribute("activePage", "home");
        return "learner/home";
    }

    @GetMapping({"/hom-nay", "/today", "/dashboard"})
    public String today(Model model, HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user != null && user.isStudent() && (user.getTargetLevel() == null || "TOPIK_0".equals(user.getTargetLevel()))) {
            return "redirect:/onboarding";
        }
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
            @RequestParam(value = "displayName", required = false) String displayName,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "avatar", required = false) String avatar,
            @RequestParam(value = "currentLevel", required = false) String currentLevel,
            @RequestParam(value = "targetLevel", required = false) String targetLevel,
            @RequestParam(value = "dailyGoalMinutes", required = false) Integer dailyGoalMinutes,
            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) {
            resp.put("success", false);
            resp.put("message", "Vui lòng đăng nhập lại để thực hiện!");
            return ResponseEntity.status(401).body(resp);
        }

        String name = displayName != null && !displayName.trim().isEmpty() ? displayName.trim() : user.getDisplayName();
        if (name.length() < 2 || name.length() > 40) {
            resp.put("success", false);
            resp.put("message", "Tên hiển thị phải từ 2 đến 40 ký tự!");
            return ResponseEntity.badRequest().body(resp);
        }

        String phoneVal = phone != null ? phone.trim() : user.getPhone();
        String avatarVal = avatar != null ? avatar.trim() : user.getAvatar();
        String curLvl = currentLevel != null ? currentLevel.trim() : user.getCurrentLevel();
        String tgtLvl = targetLevel != null ? targetLevel.trim() : user.getTargetLevel();
        Integer minutes = (dailyGoalMinutes != null && dailyGoalMinutes > 0) ? dailyGoalMinutes : user.getDailyGoalMinutes();

        boolean ok = userService.updateLearnerProfile(user.getId(), name, phoneVal, avatarVal, curLvl, tgtLvl, minutes);
        if (ok) {
            user.setDisplayName(name);
            user.setFullName(name);
            user.setPhone(phoneVal);
            if (avatar != null) user.setAvatar(avatarVal);
            if (curLvl != null) user.setCurrentLevel(curLvl);
            if (tgtLvl != null) user.setTargetLevel(tgtLvl);
            if (minutes != null) user.setDailyGoalMinutes(minutes);

            session.setAttribute("LOGGED_IN_USER", user);
            resp.put("success", true);
            resp.put("user", user);
            resp.put("displayName", name);
            resp.put("message", "Cập nhật hồ sơ học viên thành công!");
            return ResponseEntity.ok(resp);
        } else {
            resp.put("success", false);
            resp.put("message", "Không thể cập nhật thông tin lúc này!");
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @PostMapping("/api/user/clear-field")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleClearField(
            @RequestParam("field") String field,
            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) {
            resp.put("success", false);
            resp.put("message", "Vui lòng đăng nhập lại!");
            return ResponseEntity.status(401).body(resp);
        }
        if ("phone".equalsIgnoreCase(field)) {
            userService.updateUserProfile(user.getId(), null, "", null, null, null, null);
            user.setPhone(null);
            session.setAttribute("LOGGED_IN_USER", user);
            resp.put("success", true);
            resp.put("message", "Đã xóa số điện thoại khỏi hồ sơ!");
            return ResponseEntity.ok(resp);
        } else if ("avatar".equalsIgnoreCase(field)) {
            String defaultAvatar = "https://api.dicebear.com/7.x/bottts/svg?seed=" + user.getEmail();
            userService.updateUserProfile(user.getId(), null, null, defaultAvatar, null, null, null);
            user.setAvatar(defaultAvatar);
            session.setAttribute("LOGGED_IN_USER", user);
            resp.put("success", true);
            resp.put("avatar", defaultAvatar);
            resp.put("message", "Đã khôi phục ảnh đại diện mặc định!");
            return ResponseEntity.ok(resp);
        }
        resp.put("success", false);
        resp.put("message", "Trường thông tin không hợp lệ.");
        return ResponseEntity.badRequest().body(resp);
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
}
