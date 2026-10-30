package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.service.AdminSystemService;
import com.hottopik.service.ApprovalService;
import com.hottopik.service.UserService;
import com.hottopik.util.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final ApprovalService approvalService;
    private final AdminSystemService adminSystemService;

    @Autowired
    public AdminController(UserService userService,
                           ApprovalService approvalService,
                           AdminSystemService adminSystemService) {
        this.userService = userService;
        this.approvalService = approvalService;
        this.adminSystemService = adminSystemService;
    }

    @GetMapping
    public String adminDashboard(HttpSession session, Model model) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);

        // Security check
        if (currentUser == null) {
            return "redirect:/login";
        }
        if (!currentUser.isAdmin()) {
            if (currentUser.isContentManager()) {
                return "redirect:/content";
            } else if (currentUser.isOperationManager()) {
                return "redirect:/operation";
            }
            return "redirect:/today";
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("pageTitle", "Cổng Quản trị viên (Administrator) · HOT TOPIK");
        model.addAttribute("pendingRequests", approvalService.getPendingRequests());
        model.addAttribute("allRequests", approvalService.getAllRequests());
        model.addAttribute("staffUsers", userService.getStaffUsers());
        java.util.List<UserDTO> learners = userService.getLearners();
        for (UserDTO l : learners) {
            l.setPendingPermanentLock(approvalService.hasPendingPermanentLockRequest(l.getId()));
        }
        model.addAttribute("learners", learners);
        model.addAttribute("aiBudgets", adminSystemService.getAiBudgets());
        model.addAttribute("auditLogs", adminSystemService.getAuditLogs());
        model.addAttribute("breakGlassActive", adminSystemService.isBreakGlassActive());

        return "admin/dashboard";
    }

    @PostMapping("/learners/lock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> lockLearner(
            @RequestParam("userId") Long userId,
            @RequestParam("lockType") String lockType,
            @RequestParam(value = "days", required = false, defaultValue = "7") Integer days,
            @RequestParam(value = "reason", required = false) String reason,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || !currentUser.isAdmin()) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        UserDTO target = userService.findById(userId).orElse(null);
        if (target == null) {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy học viên.");
            return ResponseEntity.ok(resp);
        }

        String targetEmail = target.getEmail() != null ? target.getEmail() : ("ID #" + userId);
        String lockRsn = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Khóa bởi Admin";

        if ("PERMANENT".equalsIgnoreCase(lockType)) {
            boolean ok = userService.lockUserPermanently(userId, lockRsn);
            if (ok) {
                adminSystemService.logAction(
                        currentUser.getFullName(),
                        currentUser.getRole(),
                        "LOCK_LEARNER_PERMANENT",
                        "LEARNER",
                        "Admin khóa vĩnh viễn tài khoản " + targetEmail + ". Lý do: " + lockRsn,
                        "127.0.0.1"
                );
                resp.put("success", true);
                resp.put("status", "LOCKED");
                resp.put("message", "Đã khóa vĩnh viễn tài khoản " + targetEmail + " thành công!");
            } else {
                resp.put("success", false);
                resp.put("message", "Không thể khóa tài khoản.");
            }
        } else {
            int lockDays = (days != null && days > 0) ? days : 7;
            boolean ok = userService.lockUserTemporarily(userId, lockDays, lockRsn);
            if (ok) {
                String expiryStr = java.time.LocalDateTime.now().plusDays(lockDays).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                adminSystemService.logAction(
                        currentUser.getFullName(),
                        currentUser.getRole(),
                        "LOCK_LEARNER_TEMP",
                        "LEARNER",
                        "Admin tạm khóa tài khoản " + targetEmail + " trong " + lockDays + " ngày (đến " + expiryStr + "). Lý do: " + lockRsn,
                        "127.0.0.1"
                );
                resp.put("success", true);
                resp.put("status", "LOCKED");
                resp.put("message", "Đã tạm khóa tài khoản " + targetEmail + " trong " + lockDays + " ngày thành công!");
            } else {
                resp.put("success", false);
                resp.put("message", "Không thể tạm khóa tài khoản.");
            }
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/learners/unlock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> unlockLearner(
            @RequestParam("userId") Long userId,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || !currentUser.isAdmin()) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        UserDTO target = userService.findById(userId).orElse(null);
        if (target == null) {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy học viên.");
            return ResponseEntity.ok(resp);
        }

        boolean ok = userService.unlockUser(userId);
        if (ok) {
            String targetEmail = target.getEmail() != null ? target.getEmail() : ("ID #" + userId);
            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "UNLOCK_LEARNER",
                    "LEARNER",
                    "Admin mở khóa tài khoản học viên " + targetEmail,
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("status", "ACTIVE");
            resp.put("message", "Đã mở khóa tài khoản học viên " + targetEmail + " thành công!");
        } else {
            resp.put("success", false);
            resp.put("message", "Không thể mở khóa tài khoản.");
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/learners/toggle-lock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleLearnerLock(
            @RequestParam("userId") Long userId,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || !currentUser.isAdmin()) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        UserDTO target = userService.findById(userId).orElse(null);
        if (target != null && "LOCKED".equalsIgnoreCase(target.getStatus())) {
            return unlockLearner(userId, session);
        } else {
            return lockLearner(userId, "TEMPORARY", 7, "Tạm khóa bởi Admin", session);
        }
    }

    @PostMapping("/approval/decide")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> decideApproval(
            @RequestParam("requestId") Long requestId,
            @RequestParam("decision") String decision,
            @RequestParam(value = "note", required = false) String note,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || !currentUser.isAdmin()) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        java.util.Optional<com.hottopik.dto.ApprovalRequestDTO> reqOpt = approvalService.getRequestById(requestId);
        if (reqOpt.isEmpty()) {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy yêu cầu.");
            return ResponseEntity.ok(resp);
        }

        com.hottopik.dto.ApprovalRequestDTO req = reqOpt.get();
        boolean approve = "approve".equalsIgnoreCase(decision);
        if (!approve && (note == null || note.trim().length() < 5)) {
            resp.put("success", false);
            resp.put("message", "Từ chối cần nhập lý do ít nhất 5 ký tự.");
            return ResponseEntity.badRequest().body(resp);
        }
        boolean success;
        try {
            success = approvalService.decideRequest(requestId, approve, note, currentUser.getFullName());
        } catch (IllegalStateException | com.hottopik.service.exam.ExamDomainException e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }

        if (success) {
            //YÊU CẦU KHÓA
            // Nếu phê duyệt yêu cầu khóa vĩnh viễn học viên -> Thực hiện khóa vĩnh viễn ngay
            if (approve && "PERMANENT_LOCK_USER".equalsIgnoreCase(req.getRequestType()) && req.getTargetId() != null) {
                userService.lockUserPermanently(req.getTargetId(), "Khóa vĩnh viễn theo phê duyệt của Admin: " + (note != null ? note : req.getDescription()));
                adminSystemService.logAction(
                        currentUser.getFullName(),
                        currentUser.getRole(),
                        "LOCK_LEARNER_PERMANENT",
                        "LEARNER",
                        "Phê duyệt khóa vĩnh viễn tài khoản học viên ID #" + req.getTargetId() + (note != null ? " - Ghi chú: " + note : ""),
                        "127.0.0.1"
                );
            }

            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    approve ? "APPROVE_REQUEST" : "REJECT_REQUEST",
                    "APPROVAL_REQUEST",
                    "Quyết định: " + (approve ? "PHÊ DUYỆT" : "TỪ CHỐI") + " yêu cầu #" + requestId + " (" + req.getTitle() + ")" + (note != null ? " - Ghi chú: " + note : ""),
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("message", approve ? "Đã phê duyệt yêu cầu thành công!" : "Đã từ chối yêu cầu.");
        } else {
            resp.put("success", false);
            resp.put("message", "Không thể xử lý yêu cầu.");
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/staff/toggle-lock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleStaffLock(
            @RequestParam("userId") Long userId,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || !currentUser.isAdmin()) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        boolean ok = userService.toggleUserLock(userId);
        if (ok) {
            UserDTO target = userService.findById(userId).orElse(null);
            String st = target != null ? target.getStatus() : "UNKNOWN";
            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "TOGGLE_USER_STATUS",
                    "USER",
                    "Đổi trạng thái tài khoản ID " + userId + " (" + (target != null ? target.getEmail() : "") + ") sang " + st,
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("status", st);
            resp.put("message", "Đã cập nhật trạng thái nhân sự thành công!");
        } else {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy người dùng.");
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/ai-budget/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateAiBudget(
            @RequestParam("budgetId") Long budgetId,
            @RequestParam("monthlyLimit") double monthlyLimit,
            @RequestParam("threshold") int threshold,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || !currentUser.isAdmin()) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        boolean ok = adminSystemService.updateAiBudget(budgetId, monthlyLimit, threshold);
        if (ok) {
            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "UPDATE_AI_BUDGET",
                    "AI_CONFIG",
                    "Cập nhật ngân sách mục #" + budgetId + ": Hạn mức $" + monthlyLimit + ", Ngưỡng " + threshold + "%",
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("message", "Đã lưu cấu hình ngân sách AI thành công!");
        } else {
            resp.put("success", false);
            resp.put("message", "Cập nhật thất bại.");
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/break-glass/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleBreakGlass(
            @RequestParam(value = "reason", required = false) String reason,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || !currentUser.isAdmin()) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        boolean active = adminSystemService.toggleBreakGlass(reason);
        resp.put("success", true);
        resp.put("active", active);
        resp.put("message", active ? "Đã KÍCH HOẠT chế độ khẩn cấp Break-glass!" : "Đã TẮT chế độ khẩn cấp Break-glass.");
        return ResponseEntity.ok(resp);
    }

    // ==========================================
    // ADMIN PROFILE ENDPOINTS
    // ==========================================

    @GetMapping("/profile")
    public String adminProfile(HttpSession session, Model model) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        if (currentUser == null) return "redirect:/login";
        if (!currentUser.isAdmin()) {
            return "redirect:/today";
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("pageTitle", "Hồ sơ Quản trị viên (Administrator) · HOT TOPIK");
        model.addAttribute("recentLogs", adminSystemService.getAuditLogsForRole("ROLE_ADMIN")
                .stream().limit(8).collect(java.util.stream.Collectors.toList()));
        return "admin/profile";
    }

    @PostMapping("/profile/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateAdminProfile(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || !currentUser.isAdmin()) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        String fullName = (String) payload.get("fullName");
        String phone = (String) payload.get("phone");
        String avatar = (String) payload.get("avatar");
        String bio = (String) payload.get("bio");
        String specialty = (String) payload.get("specialty");
        String timezone = (String) payload.get("timezone");

        if (fullName == null || fullName.trim().length() < 2 || fullName.trim().length() > 40) {
            resp.put("success", false);
            resp.put("message", "Họ và tên phải từ 2 đến 40 ký tự!");
            return ResponseEntity.badRequest().body(resp);
        }

        boolean ok = userService.updateUserProfile(currentUser.getId(), fullName, phone, avatar, bio, specialty, timezone);
        if (ok) {
            currentUser.setFullName(fullName.trim());
            currentUser.setDisplayName(fullName.trim());
            if (phone != null) currentUser.setPhone(phone.trim().isEmpty() ? null : phone.trim());
            if (avatar != null) currentUser.setAvatar(avatar.trim().isEmpty() ? null : avatar.trim());
            if (bio != null) currentUser.setBio(bio.trim().isEmpty() ? null : bio.trim());
            if (specialty != null) currentUser.setSpecialty(specialty.trim().isEmpty() ? null : specialty.trim());
            if (timezone != null) currentUser.setTimezone(timezone.trim().isEmpty() ? null : timezone.trim());

            session.setAttribute("LOGGED_IN_USER", currentUser);

            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "UPDATE_ADMIN_PROFILE",
                    "ADMIN_PROFILE",
                    "Cập nhật thông tin hồ sơ Quản trị viên",
                    "127.0.0.1"
            );

            resp.put("success", true);
            resp.put("user", currentUser);
            resp.put("message", "Đã cập nhật hồ sơ Quản trị viên thành công!");
            return ResponseEntity.ok(resp);
        } else {
            resp.put("success", false);
            resp.put("message", "Không thể cập nhật hồ sơ lúc này!");
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @PostMapping("/profile/clear-field")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearAdminField(
            @RequestParam("field") String field,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();
        if (currentUser == null || !currentUser.isAdmin()) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }
        if ("phone".equalsIgnoreCase(field)) {
            userService.updateUserProfile(currentUser.getId(), null, "", null, null, null, null);
            currentUser.setPhone(null);
            session.setAttribute("LOGGED_IN_USER", currentUser);
            resp.put("success", true);
            resp.put("message", "Đã xóa số điện thoại khỏi hồ sơ!");
            return ResponseEntity.ok(resp);
        } else if ("avatar".equalsIgnoreCase(field)) {
            String defaultAvatar = "https://api.dicebear.com/7.x/bottts/svg?seed=" + currentUser.getEmail();
            userService.updateUserProfile(currentUser.getId(), null, null, defaultAvatar, null, null, null);
            currentUser.setAvatar(defaultAvatar);
            session.setAttribute("LOGGED_IN_USER", currentUser);
            resp.put("success", true);
            resp.put("avatar", defaultAvatar);
            resp.put("message", "Đã khôi phục ảnh đại diện mặc định!");
            return ResponseEntity.ok(resp);
        }
        resp.put("success", false);
        resp.put("message", "Trường thông tin không hợp lệ.");
        return ResponseEntity.badRequest().body(resp);
    }

    @PostMapping("/profile/change-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changeAdminPassword(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();
        if (currentUser == null || !currentUser.isAdmin()) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        String oldPass = (String) payload.get("oldPassword");
        String newPass = (String) payload.get("newPassword");
        if (newPass == null || newPass.trim().length() < 6) {
            resp.put("success", false);
            resp.put("message", "Mật khẩu mới phải từ 6 ký tự trở lên!");
            return ResponseEntity.badRequest().body(resp);
        }

        boolean ok = userService.changePassword(currentUser.getId(), oldPass, newPass);
        if (ok) {
            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "CHANGE_ADMIN_PASSWORD",
                    "SECURITY",
                    "Đổi mật khẩu tài khoản Administrator thành công",
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("message", "Đã đổi mật khẩu Quản trị viên thành công!");
            return ResponseEntity.ok(resp);
        } else {
            resp.put("success", false);
            resp.put("message", "Mật khẩu hiện tại không đúng. Vui lòng thử lại!");
            return ResponseEntity.badRequest().body(resp);
        }
    }
}
