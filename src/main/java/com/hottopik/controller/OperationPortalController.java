package com.hottopik.controller;

import com.hottopik.dto.AnnouncementDTO;
import com.hottopik.dto.UserDTO;
import com.hottopik.service.AdminSystemService;
import com.hottopik.service.OperationService;
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
@RequestMapping("/operation")
public class OperationPortalController {

    private final UserService userService;
    private final OperationService operationService;
    private final AdminSystemService adminSystemService;

    @Autowired
    public OperationPortalController(UserService userService,
                                     OperationService operationService,
                                     AdminSystemService adminSystemService) {
        this.userService = userService;
        this.operationService = operationService;
        this.adminSystemService = adminSystemService;
    }

    @GetMapping
    public String dashboard(HttpSession session, Model model) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);

        // Security check: Only Operation Manager (or Admin supervising)
        if (currentUser == null) {
            return "redirect:/login";
        }
        if (!"ROLE_OPERATION_MANAGER".equalsIgnoreCase(currentUser.getRole())
                && !"ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            if ("ROLE_CONTENT_MANAGER".equalsIgnoreCase(currentUser.getRole())) {
                return "redirect:/content";
            }
            return "redirect:/today";
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("pageTitle", "Cổng Operation Manager · HOT TOPIK");
        model.addAttribute("learners", userService.getLearners());
        model.addAttribute("orders", operationService.getOrders());
        model.addAttribute("packagePrices", operationService.getPackagePrices());
        model.addAttribute("reviews", operationService.getReviews());
        model.addAttribute("announcements", operationService.getAnnouncements());
        model.addAttribute("auditLogs", adminSystemService.getAuditLogsForRole("ROLE_OPERATION_MANAGER"));

        return "operation/dashboard";
    }

    @PostMapping("/users/toggle-lock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleUserLock(
            @RequestParam("userId") Long userId,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!"ROLE_OPERATION_MANAGER".equalsIgnoreCase(currentUser.getRole()) && !"ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole()))) {
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
                    "LOCK_LEARNER",
                    "LEARNER",
                    "Cập nhật trạng thái học viên " + (target != null ? target.getEmail() : "") + " sang " + st,
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("status", st);
            resp.put("message", "Đã cập nhật trạng thái học viên thành công!");
        } else {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy học viên.");
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/orders/request-refund")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> requestRefund(
            @RequestParam("orderId") Long orderId,
            @RequestParam(value = "reason", required = false) String reason,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!"ROLE_OPERATION_MANAGER".equalsIgnoreCase(currentUser.getRole()) && !"ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole()))) {
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

        if (currentUser == null || (!"ROLE_OPERATION_MANAGER".equalsIgnoreCase(currentUser.getRole()) && !"ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole()))) {
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

        if (currentUser == null || (!"ROLE_OPERATION_MANAGER".equalsIgnoreCase(currentUser.getRole()) && !"ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole()))) {
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

        if (currentUser == null || (!"ROLE_OPERATION_MANAGER".equalsIgnoreCase(currentUser.getRole()) && !"ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole()))) {
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
}
