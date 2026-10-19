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
        model.addAttribute("aiBudgets", adminSystemService.getAiBudgets());
        model.addAttribute("auditLogs", adminSystemService.getAuditLogs());
        model.addAttribute("breakGlassActive", adminSystemService.isBreakGlassActive());

        return "admin/dashboard";
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
            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    approve ? "APPROVE_REQUEST" : "REJECT_REQUEST",
                    "APPROVAL_REQUEST",
                    "Quyết định: " + (approve ? "PHÊ DUYỆT" : "TỪ CHỐI") + " yêu cầu #" + requestId + (note != null ? " - Ghi chú: " + note : ""),
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("message", approve ? "Đã phê duyệt yêu cầu thành công!" : "Đã từ chối yêu cầu.");
        } else {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy yêu cầu.");
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
}
