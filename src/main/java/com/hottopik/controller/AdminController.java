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
    private final com.hottopik.service.OperationService operationService;

    @Autowired
    public AdminController(UserService userService,
                           ApprovalService approvalService,
                           AdminSystemService adminSystemService,
                           com.hottopik.service.OperationService operationService) {
        this.userService = userService;
        this.approvalService = approvalService;
        this.adminSystemService = adminSystemService;
        this.operationService = operationService;
    }

    /**
     * Hiển thị trang tổng quan Cổng Quản trị viên (yêu cầu duyệt, nhân sự, audit log, Break-glass).
     */
    @GetMapping
    public String adminDashboard(HttpSession session, Model model) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session); // Lấy user đang đăng nhập từ session

        // Security check: chỉ Admin mới được vào /admin
        if (currentUser == null) {
            return "redirect:/login"; // Chưa đăng nhập -> về trang login
        }
        if (!currentUser.isAdmin()) {
            // Điều hướng đúng cổng nếu là vai trò khác
            if (currentUser.isContentManager()) {
                return "redirect:/content";
            } else if (currentUser.isOperationManager()) {
                return "redirect:/operation";
            }
            return "redirect:/today";
        }

        // Nạp dữ liệu tổng quan cho Dashboard Admin
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("pageTitle", "Cổng Quản trị viên (Administrator) · HOT TOPIK");
        model.addAttribute("pendingRequests", approvalService.getPendingRequests()); // Yêu cầu đang chờ duyệt
        model.addAttribute("allRequests", approvalService.getAllRequests()); // Toàn bộ lịch sử yêu cầu
        model.addAttribute("staffUsers", userService.getStaffUsers()); // Danh sách nhân sự nội bộ
        model.addAttribute("auditLogs", adminSystemService.getAuditLogs()); // Nhật ký kiểm toán hệ thống
        model.addAttribute("breakGlassActive", adminSystemService.isBreakGlassActive()); // Trạng thái chế độ khẩn cấp

        return "admin/dashboard"; // Render template admin/dashboard.html
    }

    /**
     * Khóa tạm thời hoặc khóa vĩnh viễn tài khoản học viên và ghi nhận audit log.
     */
    @PostMapping("/learners/lock")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> lockLearner(
            @RequestParam("userId") Long userId,
            @RequestParam("lockType") String lockType,
            @RequestParam(value = "days", required = false, defaultValue = "7") Integer days,
            @RequestParam(value = "reason", required = false) String reason,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session); // Lấy user hiện tại
        Map<String, Object> resp = new HashMap<>(); // Map JSON trả về cho client

        // Kiểm tra quyền Admin
        if (currentUser == null || !currentUser.isAdmin()) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        UserDTO target = userService.findById(userId).orElse(null); // Tìm học viên cần khóa
        if (target == null) {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy học viên.");
            return ResponseEntity.ok(resp);
        }

        String targetEmail = target.getEmail() != null ? target.getEmail() : ("ID #" + userId);
        String lockRsn = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Khóa bởi Admin";

        if ("PERMANENT".equalsIgnoreCase(lockType)) {
            boolean ok = userService.lockUserPermanently(userId, lockRsn); // Khóa vĩnh viễn
            if (ok) {
                adminSystemService.logAction( // Ghi audit log khóa vĩnh viễn
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
            int lockDays = (days != null && days > 0) ? days : 7; // Mặc định tạm khóa 7 ngày
            boolean ok = userService.lockUserTemporarily(userId, lockDays, lockRsn); // Khóa tạm thời
            if (ok) {
                String expiryStr = java.time.LocalDateTime.now().plusDays(lockDays).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                adminSystemService.logAction( // Ghi audit log tạm khóa
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

    /**
     * Mở khóa tài khoản học viên đang bị khóa và ghi nhận audit log.
     */
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

    /**
     * Đảo trạng thái khóa/mở khóa học viên (mặc định tạm khóa 7 ngày nếu đang hoạt động).
     */
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

    /* =========================================================================================
     * LUỒNG CHỨC NĂNG: ADMIN PHÊ DUYỆT CÁC YÊU CẦU ĐỀ NGHỊ TỪ BAN VẬN HÀNH (FUNC_ADMIN_01)
     * -----------------------------------------------------------------------------------------
     * 1. THAO TÁC NGƯỜI DÙNG & UI/HTML:
     *    - Admin truy cập trang Quản trị (/admin), tab "Yêu cầu cần phê duyệt".
     *    - Xem danh sách yêu cầu (Khóa vĩnh viễn, Hoàn tiền đơn hàng, Đổi giá gói, Đền bù VIP...).
     *    - Nhấn nút "Chấp thuận" (Approve) hoặc "Từ chối" (Reject) kèm lý do ghi chú (note).
     * 2. HTTP REQUEST & ENDPOINT:
     *    - URL: POST /admin/approval/decide
     *    - Params: requestId, decision ("approve" / "reject"), note
     * 3. CONTROLLER & SERVICE XỬ LÝ:
     *    - Controller: com.hottopik.controller.AdminController.decideApproval()
     *    - Kiểm tra quyền: Bắt buộc currentUser.isAdmin() == true.
     *    - Triệu gọi Service:
     *      + com.hottopik.service.ApprovalService.decideRequest(requestId, approve, note, approver):
     *        * Thực hiện hành động nghiệp vụ tương ứng (khóa user vĩnh viễn, cập nhật trạng thái đơn hàng REFUNDED,
     *          áp dụng giá gói mới, hoặc cộng thêm ngày VIP cho học viên).
     *        * Đổi trạng thái đơn phê duyệt sang APPROVED hoặc REJECTED.
     *      + com.hottopik.service.AdminSystemService.logAction(): Ghi nhận lịch sử kiểm toán của Admin.
     * 4. TRUY VẤN CSDL:
     *    - UPDATE dbo.approval_requests SET status = :status, approver = :admin, decided_at = :now, note = :note
     *    - UPDATE bảng tương ứng: dbo.users (status='LOCKED'), dbo.orders (status='REFUNDED'), dbo.packages...
     *    - INSERT INTO dbo.audit_logs (...)
     * 5. HIỂN THỊ KẾT QUẢ CHO QUẢN TRỊ VIÊN:
     *    - Trả về JSON: {"success": true, "message": "...", "action": "..."}
     *    - Giao diện Admin loại bỏ thẻ yêu cầu khỏi danh sách chờ xử lý và chuyển sang tab "Lịch sử đã duyệt".
     * ========================================================================================= */
    /**
     * Phê duyệt hoặc từ chối yêu cầu (khóa vĩnh viễn, hoàn tiền, đổi giá gói, cấp bù VIP) và thực thi hành động tương ứng.
     */
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

        java.util.Optional<com.hottopik.dto.ApprovalRequestDTO> reqOpt = approvalService.getRequestById(requestId); // Tìm yêu cầu theo ID
        if (reqOpt.isEmpty()) {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy yêu cầu.");
            return ResponseEntity.ok(resp);
        }

        com.hottopik.dto.ApprovalRequestDTO req = reqOpt.get();
        boolean approve = "approve".equalsIgnoreCase(decision); // Xác định quyết định: duyệt hay từ chối
        if (!approve && (note == null || note.trim().length() < 5)) {
            resp.put("success", false);
            resp.put("message", "Từ chối cần nhập lý do ít nhất 5 ký tự."); // Bắt buộc có lý do khi từ chối
            return ResponseEntity.badRequest().body(resp);
        }
        boolean success;
        try {
            success = approvalService.decideRequest(requestId, approve, note, currentUser.getFullName()); // Chốt trạng thái yêu cầu
        } catch (IllegalStateException | com.hottopik.service.exam.ExamDomainException e) {
            resp.put("success", false);
            resp.put("message", e.getMessage()); // Trả lỗi nghiệp vụ (vd: tự duyệt yêu cầu của mình)
            return ResponseEntity.badRequest().body(resp);
        }

        if (success) {
            if (approve) {
                // 1. Phê duyệt yêu cầu khóa vĩnh viễn học viên
                if ("PERMANENT_LOCK_USER".equalsIgnoreCase(req.getRequestType()) && req.getTargetId() != null) {
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
                // 2. Phê duyệt hoàn tiền đơn hàng
                else if ("REFUND_ORDER".equalsIgnoreCase(req.getRequestType()) && req.getTargetId() != null) {
                    operationService.directRefundOrder(req.getTargetId(), (note != null ? note : req.getDescription()), currentUser.getFullName());
                    adminSystemService.logAction(
                            currentUser.getFullName(),
                            currentUser.getRole(),
                            "APPROVE_REFUND_ORDER",
                            "ORDER",
                            "Phê duyệt hoàn tiền cho đơn hàng ID #" + req.getTargetId() + (note != null ? " - Ghi chú: " + note : ""),
                            "127.0.0.1"
                    );
                }
                // 3. Phê duyệt điều chỉnh giá gói học
                else if ("CHANGE_PACKAGE_PRICE".equalsIgnoreCase(req.getRequestType()) && req.getTargetId() != null) {
                    try {
                        double newPrice = Double.parseDouble(req.getPayload());
                        operationService.updatePackagePrice(req.getTargetId(), newPrice, currentUser.getFullName());
                        adminSystemService.logAction(
                                currentUser.getFullName(),
                                currentUser.getRole(),
                                "APPROVE_PACKAGE_PRICE",
                                "PACKAGE_PRICE",
                                "Phê duyệt điều chỉnh giá mới cho gói ID #" + req.getTargetId() + " mức " + (int)newPrice + "đ",
                                "127.0.0.1"
                        );
                    } catch (Exception ignored) {}
                }
                // 4. Phê duyệt cấp bù / gia hạn quyền lợi VIP
                else if ("VIP_COMPENSATION".equalsIgnoreCase(req.getRequestType()) && req.getTargetId() != null) {
                    try {
                        String payload = req.getPayload();
                        int days = 30;
                        int coins = 0;
                        String pkgName = "VIP";
                        String compReason = req.getDescription();

                        if (payload != null && payload.contains(";")) {
                            String[] parts = payload.split(";");
                            if (parts.length > 0) days = Integer.parseInt(parts[0]);
                            if (parts.length > 1) coins = Integer.parseInt(parts[1]);
                            if (parts.length > 2) pkgName = parts[2];
                            if (parts.length > 3) compReason = parts[3];
                        }

                        UserDTO target = userService.findById(req.getTargetId()).orElse(null);
                        String tName = target != null ? target.getFullName() : "Học viên";
                        String tEmail = target != null ? target.getEmail() : "";

                        operationService.applyApprovedVipCompensation(
                                req.getTargetId(), tName, tEmail, days, coins, pkgName, compReason, currentUser.getFullName(), "Admin"
                        );

                        adminSystemService.logAction(
                                currentUser.getFullName(),
                                currentUser.getRole(),
                                "APPROVE_VIP_COMPENSATION",
                                "LEARNER_VIP",
                                "Phê duyệt cấp bù " + days + " ngày VIP (" + coins + " Xu) cho học viên " + tEmail + (note != null ? " - Ghi chú: " + note : ""),
                                "127.0.0.1"
                        );
                    } catch (Exception ignored) {}
                }
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

    /**
     * Khóa hoặc mở khóa tài khoản nhân sự nội bộ và ghi audit log.
     */
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

    /**
     * Kiểm tra dữ liệu đầu vào và tạo tài khoản nhân sự nội bộ mới (Content, Operation, Teacher, Admin).
     */
    @PostMapping("/staff/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createStaff(
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("role") String role,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || !currentUser.isAdmin()) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        if (fullName == null || fullName.trim().length() < 2 || fullName.trim().length() > 40) {
            resp.put("success", false);
            resp.put("message", "Họ và tên nhân sự phải từ 2 đến 40 ký tự.");
            return ResponseEntity.badRequest().body(resp);
        }

        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            resp.put("success", false);
            resp.put("message", "Email không hợp lệ.");
            return ResponseEntity.badRequest().body(resp);
        }

        if (password == null || password.trim().length() < 6) {
            resp.put("success", false);
            resp.put("message", "Mật khẩu ban đầu phải từ 6 ký tự trở lên.");
            return ResponseEntity.badRequest().body(resp);
        }

        boolean created = userService.createStaffUser(fullName.trim(), email.trim(), password.trim(), role.trim());
        if (created) {
            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "CREATE_STAFF_USER",
                    "STAFF",
                    "Admin tạo tài khoản nhân sự mới: " + email.trim() + " (" + role.trim() + ")",
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("message", "Tạo tài khoản nhân sự " + email.trim() + " thành công!");
            return ResponseEntity.ok(resp);
        } else {
            resp.put("success", false);
            resp.put("message", "Email " + email.trim() + " đã tồn tại trên hệ thống.");
            return ResponseEntity.badRequest().body(resp);
        }
    }

    /**
     * Admin thực hiện hoàn tiền trực tiếp cho đơn hàng và ghi nhận audit log.
     */
    @PostMapping("/orders/refund")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refundOrder(
            @RequestParam("orderId") Long orderId,
            @RequestParam(value = "reason", required = false) String reason,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || !currentUser.isAdmin()) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        boolean ok = operationService.directRefundOrder(orderId, reason, currentUser.getFullName());
        if (ok) {
            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "REFUND_ORDER_DIRECT",
                    "ORDER",
                    "Admin hoàn tiền trực tiếp cho đơn #" + orderId + ". Lý do: " + (reason != null ? reason : "Không nêu"),
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("message", "Đã hoàn tiền đơn hàng #" + orderId + " thành công!");
        } else {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy đơn hàng.");
        }
        return ResponseEntity.ok(resp);
    }

    /**
     * Admin cập nhật trực tiếp giá mới cho gói VIP.
     */
    @PostMapping("/prices/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updatePrice(
            @RequestParam("packageId") Long packageId,
            @RequestParam("newPrice") double newPrice,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || !currentUser.isAdmin()) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        if (newPrice <= 0) {
            resp.put("success", false);
            resp.put("message", "Mức giá phải lớn hơn 0đ.");
            return ResponseEntity.badRequest().body(resp);
        }

        boolean ok = operationService.updatePackagePrice(packageId, newPrice, currentUser.getFullName());
        if (ok) {
            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "UPDATE_PACKAGE_PRICE",
                    "PACKAGE_PRICE",
                    "Admin cập nhật giá mới cho gói ID #" + packageId + ": " + (int)newPrice + "đ",
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("message", "Đã cập nhật giá mới cho gói VIP thành công!");
        } else {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy gói học.");
        }
        return ResponseEntity.ok(resp);
    }

    /**
     * Admin cấp bù hoặc gia hạn trực tiếp số ngày VIP và Xu thưởng cho học viên.
     */
    @PostMapping("/vip/compensate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> compensateVip(
            @RequestParam("userId") Long userId,
            @RequestParam("days") int days,
            @RequestParam(value = "bonusCoins", required = false, defaultValue = "0") int bonusCoins,
            @RequestParam(value = "packageName", required = false) String packageName,
            @RequestParam("reason") String reason,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || !currentUser.isAdmin()) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        if (days <= 0) {
            resp.put("success", false);
            resp.put("message", "Số ngày cấp bù/gia hạn phải lớn hơn 0.");
            return ResponseEntity.badRequest().body(resp);
        }

        if (reason == null || reason.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("message", "Vui lòng nhập lý do cấp bù hoặc gia hạn quyền lợi.");
            return ResponseEntity.badRequest().body(resp);
        }

        UserDTO target = userService.findById(userId).orElse(null);
        if (target == null) {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy học viên.");
            return ResponseEntity.ok(resp);
        }

        boolean ok = userService.applyVipCompensation(userId, days, bonusCoins, packageName, reason.trim());
        if (ok) {
            operationService.addCompensationRecord(
                    userId, target.getFullName(), target.getEmail(),
                    days, bonusCoins, reason.trim(), currentUser.getFullName(), "Admin"
            );
            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "VIP_COMPENSATION",
                    "LEARNER_VIP",
                    "Admin cấp bù " + days + " ngày VIP (" + bonusCoins + " Xu) cho học viên " + target.getEmail() + ". Lý do: " + reason.trim(),
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("message", "Đã cấp bù/gia hạn " + days + " ngày VIP cho học viên " + target.getFullName() + " thành công!");
        } else {
            resp.put("success", false);
            resp.put("message", "Cấp bù quyền lợi thất bại.");
        }
        return ResponseEntity.ok(resp);
    }

    /**
     * Cập nhật hạn mức chi tiêu hàng tháng và ngưỡng cảnh báo cho dịch vụ AI.
     */
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

    /**
     * Bật hoặc tắt chế độ khẩn cấp Break-glass của hệ thống.
     */
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

    /**
     * Hiển thị trang hồ sơ cá nhân của Admin kèm 8 nhật ký thao tác gần nhất.
     */
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

    /**
     * Cập nhật thông tin hồ sơ cá nhân của Admin và đồng bộ lại session.
     */
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

    /**
     * Xóa số điện thoại hoặc khôi phục ảnh đại diện mặc định trong hồ sơ Admin.
     */
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

    /**
     * Đổi mật khẩu tài khoản Admin hiện tại và ghi nhận audit log bảo mật.
     */
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
