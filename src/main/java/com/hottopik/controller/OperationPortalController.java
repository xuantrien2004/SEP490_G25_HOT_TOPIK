package com.hottopik.controller;

import com.hottopik.dto.AnnouncementDTO;
import com.hottopik.dto.ApprovalRequestDTO;
import com.hottopik.dto.UserDTO;
import com.hottopik.service.AdminSystemService;
import com.hottopik.service.ApprovalService;
import com.hottopik.service.OperationService;
import com.hottopik.service.UserService;
import com.hottopik.util.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/operation")
public class OperationPortalController {

    private final UserService userService;
    private final OperationService operationService;
    private final AdminSystemService adminSystemService;
    private final ApprovalService approvalService;

    @Autowired
    public OperationPortalController(UserService userService,
                                     OperationService operationService,
                                     AdminSystemService adminSystemService,
                                     ApprovalService approvalService) {
        this.userService = userService;
        this.operationService = operationService;
        this.adminSystemService = adminSystemService;
        this.approvalService = approvalService;
    }

    @GetMapping
    public String dashboard(HttpSession session, Model model) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);

        // Security check: Only Operation Manager (or Admin supervising)
        if (currentUser == null) {
            return "redirect:/login";
        }
        if (!currentUser.isOperationManager() && !currentUser.isAdmin()) {
            if (currentUser.isContentManager()) {
                return "redirect:/content";
            }
            return "redirect:/today";
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("pageTitle", "Cổng Operation Manager · HOT TOPIK");

        java.util.List<UserDTO> learners = userService.getLearners();
        for (UserDTO l : learners) {
            l.setPendingPermanentLock(approvalService.hasPendingPermanentLockRequest(l.getId()));
        }
        model.addAttribute("learners", learners);

        model.addAttribute("orders", operationService.getOrders());
        model.addAttribute("packagePrices", operationService.getPackagePrices());
        model.addAttribute("reviews", operationService.getReviews());
        model.addAttribute("announcements", operationService.getAnnouncements());
        model.addAttribute("auditLogs", adminSystemService.getAuditLogsForRole("ROLE_OPERATION_MANAGER"));

        return "operation/dashboard";
    }

    @PostMapping("/users/lock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> lockLearner(
            @RequestParam("userId") Long userId,
            @RequestParam("lockType") String lockType,
            @RequestParam(value = "days", required = false, defaultValue = "7") Integer days,
            @RequestParam("reason") String reason,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!currentUser.isOperationManager() && !currentUser.isAdmin())) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        if (reason == null || reason.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("message", "Vui lòng nhập lý do khóa tài khoản học viên.");
            return ResponseEntity.badRequest().body(resp);
        }

        UserDTO target = userService.findById(userId).orElse(null);
        if (target == null) {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy học viên tương ứng.");
            return ResponseEntity.ok(resp);
        }

        String targetEmail = target.getEmail() != null ? target.getEmail() : ("ID #" + userId);
        String targetName = target.getFullName() != null ? target.getFullName() : "Học viên";

        if ("PERMANENT".equalsIgnoreCase(lockType)) {
            // Kiểm tra nếu đã có yêu cầu chờ duyệt
            if (approvalService.hasPendingPermanentLockRequest(userId)) {
                resp.put("success", false);
                resp.put("message", "Tài khoản " + targetEmail + " hiện đang có yêu cầu đề xuất khóa vĩnh viễn chờ Admin phê duyệt.");
                return ResponseEntity.ok(resp);
            }

            // Khóa vĩnh viễn: Gửi yêu cầu phê duyệt lên Admin, chưa khóa trực tiếp
            ApprovalRequestDTO req = approvalService.submitRequest(
                    currentUser.getFullName(),
                    "Operation Manager",
                    "PERMANENT_LOCK_USER",
                    "Đề xuất khóa vĩnh viễn tài khoản: " + targetEmail,
                    "Học viên: " + targetName + " (" + targetEmail + " - ID: #" + userId + "). Lý do đề xuất khóa vĩnh viễn: " + reason.trim(),
                    userId
            );

            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "SUBMIT_APPROVAL",
                    "LEARNER",
                    "Gửi yêu cầu đề xuất khóa vĩnh viễn tài khoản " + targetEmail + " lên Admin để phê duyệt. Lý do: " + reason.trim(),
                    "127.0.0.1"
            );

            resp.put("success", true);
            resp.put("pendingApproval", true);
            resp.put("status", target.getStatus());
            resp.put("message", "Đã gửi đề xuất khóa vĩnh viễn tài khoản " + targetEmail + " lên Quản trị viên (Admin) để thẩm định và phê duyệt!");
            return ResponseEntity.ok(resp);
        } else {
            // Tạm khóa: Có số ngày, ghi rõ lý do để admin xem được nhật ký hoạt động
            int lockDays = (days != null && days > 0) ? days : 7;
            boolean ok = userService.lockUserTemporarily(userId, lockDays, reason.trim());
            if (ok) {
                String expiryStr = LocalDateTime.now().plusDays(lockDays).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                adminSystemService.logAction(
                        currentUser.getFullName(),
                        currentUser.getRole(),
                        "LOCK_LEARNER_TEMP",
                        "LEARNER",
                        "Tạm khóa tài khoản " + targetEmail + " trong " + lockDays + " ngày (đến " + expiryStr + "). Lý do: " + reason.trim(),
                        "127.0.0.1"
                );

                resp.put("success", true);
                resp.put("status", "LOCKED");
                resp.put("lockedUntil", expiryStr);
                resp.put("lockReason", reason.trim());
                resp.put("message", "Đã tạm khóa tài khoản " + targetEmail + " trong " + lockDays + " ngày thành công!");
            } else {
                resp.put("success", false);
                resp.put("message", "Không thể tạm khóa học viên. Vui lòng thử lại.");
            }
            return ResponseEntity.ok(resp);
        }
    }

    @PostMapping("/users/unlock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> unlockLearner(
            @RequestParam("userId") Long userId,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!currentUser.isOperationManager() && !currentUser.isAdmin())) {
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
                    "Mở khóa tài khoản học viên " + targetEmail,
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

    @PostMapping("/users/toggle-lock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleUserLock(
            @RequestParam("userId") Long userId,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!currentUser.isOperationManager() && !currentUser.isAdmin())) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        UserDTO target = userService.findById(userId).orElse(null);
        if (target != null && "LOCKED".equalsIgnoreCase(target.getStatus())) {
            return unlockLearner(userId, session);
        } else {
            return lockLearner(userId, "TEMPORARY", 7, "Tạm khóa bởi ban vận hành", session);
        }
    }

    @PostMapping("/orders/request-refund")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestRefund(
            @RequestParam("orderId") Long orderId,
            @RequestParam(value = "reason", required = false) String reason,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!currentUser.isOperationManager() && !currentUser.isAdmin())) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        boolean ok = operationService.requestRefundApproval(orderId, currentUser.getFullName(), reason);
        if (ok) {
            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "SUBMIT_APPROVAL",
                    "ORDER_REFUND",
                    "Gửi yêu cầu duyệt hoàn tiền đơn #" + orderId + " lên Admin",
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("message", "Đã gửi yêu cầu duyệt hoàn tiền lên Admin thành công!");
        } else {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy đơn hàng.");
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/prices/propose")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> proposePrice(
            @RequestParam("packageId") Long packageId,
            @RequestParam("proposedPrice") double proposedPrice,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!currentUser.isOperationManager() && !currentUser.isAdmin())) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        boolean ok = operationService.proposePackagePrice(packageId, proposedPrice, currentUser.getFullName());
        if (ok) {
            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "SUBMIT_APPROVAL",
                    "PACKAGE_PRICE",
                    "Đề xuất giá mới cho gói ID #" + packageId + ": " + (int)proposedPrice + "đ gửi lên Admin",
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("message", "Đã gửi đề xuất điều chỉnh giá gói lên Admin!");
        } else {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy gói học.");
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/reviews/toggle-approve")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleReview(
            @RequestParam("reviewId") Long reviewId,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!currentUser.isOperationManager() && !currentUser.isAdmin())) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        boolean ok = operationService.toggleReviewApproval(reviewId);
        if (ok) {
            resp.put("success", true);
            resp.put("message", "Đã cập nhật hiển thị đánh giá thành công!");
        } else {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy đánh giá.");
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/announcements/send")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendAnnouncement(
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam(value = "audience", defaultValue = "ALL") String audience,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!currentUser.isOperationManager() && !currentUser.isAdmin())) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        AnnouncementDTO ann = operationService.sendAnnouncement(title, content, audience, currentUser.getFullName());
        adminSystemService.logAction(
                currentUser.getFullName(),
                currentUser.getRole(),
                "SEND_ANNOUNCEMENT",
                "ANNOUNCEMENT",
                "Phát thông báo: \"" + title + "\" tới đối tượng: " + audience,
                "127.0.0.1"
        );
        resp.put("success", true);
        resp.put("announcement", ann);
        resp.put("message", "Đã phát thông báo thành công đến học viên!");
        return ResponseEntity.ok(resp);
    }

    // ==========================================
    // OPERATION PROFILE ENDPOINTS
    // ==========================================

    @GetMapping("/profile")
    public String operationProfile(HttpSession session, Model model) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        if (currentUser == null) return "redirect:/login";
        if (!currentUser.isOperationManager() && !currentUser.isAdmin()) {
            return "redirect:/today";
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("pageTitle", "Hồ sơ Quản lý Vận hành (Operation) · HOT TOPIK");
        model.addAttribute("recentLogs", adminSystemService.getAuditLogsForRole("ROLE_OPERATION_MANAGER")
                .stream().limit(8).collect(java.util.stream.Collectors.toList()));
        return "operation/profile";
    }

    @PostMapping("/profile/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateOperationProfile(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!currentUser.isOperationManager() && !currentUser.isAdmin())) {
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
                    "UPDATE_OPERATION_PROFILE",
                    "OPERATION_PROFILE",
                    "Cập nhật thông tin hồ sơ Operation Manager",
                    "127.0.0.1"
            );

            resp.put("success", true);
            resp.put("user", currentUser);
            resp.put("message", "Đã cập nhật hồ sơ Quản lý Vận hành thành công!");
            return ResponseEntity.ok(resp);
        } else {
            resp.put("success", false);
            resp.put("message", "Không thể cập nhật hồ sơ lúc này!");
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @PostMapping("/profile/clear-field")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearOperationField(
            @RequestParam("field") String field,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();
        if (currentUser == null || (!currentUser.isOperationManager() && !currentUser.isAdmin())) {
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
    public ResponseEntity<Map<String, Object>> changeOperationPassword(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();
        if (currentUser == null || (!currentUser.isOperationManager() && !currentUser.isAdmin())) {
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
                    "CHANGE_OPERATION_PASSWORD",
                    "SECURITY",
                    "Đổi mật khẩu tài khoản Operation Manager thành công",
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("message", "Đã đổi mật khẩu Quản lý Vận hành thành công!");
            return ResponseEntity.ok(resp);
        } else {
            resp.put("success", false);
            resp.put("message", "Mật khẩu hiện tại không đúng. Vui lòng thử lại!");
            return ResponseEntity.badRequest().body(resp);
        }
    }
}
