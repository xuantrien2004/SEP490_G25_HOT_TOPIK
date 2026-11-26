package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.subscription.PlanRequestStatus;
import com.hottopik.entity.subscription.SubscriptionPlanStatus;
import com.hottopik.service.subscription.*;
import com.hottopik.util.CsrfGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/**
 * Admin: phê duyệt yêu cầu gói học, xem gói / giao dịch / doanh thu Sandbox, đối soát giao dịch bằng QueryDR.
 * Admin KHÔNG có thao tác đánh dấu giao dịch thành công thủ công: "Đối soát" chỉ ghi nhận kết quả xác thực từ VNPay Sandbox.
 * Yêu cầu gói học cũng xuất hiện ở hộp duyệt chung trên /admin (ApprovalService) – duyệt ở đâu cũng cùng một luồng.
 */
@Controller
@RequestMapping("/admin")
public class AdminSubscriptionController {

    private static final String PORTAL = "admin";

    private final PlanApprovalService approvalService;
    private final SubscriptionPlanService planService;
    private final PaymentService paymentService;
    private final BillingPageSupport support;

    public AdminSubscriptionController(PlanApprovalService approvalService, SubscriptionPlanService planService,
                                       PaymentService paymentService, BillingPageSupport support) {
        this.approvalService = approvalService;
        this.planService = planService;
        this.paymentService = paymentService;
        this.support = support;
    }

    @GetMapping("/subscription-requests")
    public String requests(@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
                           HttpSession session, Model model) {
        support.prepare(session, model, PORTAL, "requests", "Phê duyệt gói học");
        PlanRequestStatus st = status == null ? PlanRequestStatus.PENDING : OperationSubscriptionController.parseRequestStatus(status);
        model.addAttribute("requests", approvalService.search(st, null, page, 10));
        model.addAttribute("requestStatuses", PlanRequestStatus.values());
        model.addAttribute("statusFilter", st == null ? "" : st.name());
        model.addAttribute("pendingCount", approvalService.countPending());
        return "admin/subscription-requests";
    }

    @GetMapping("/subscription-requests/{id}")
    public String requestDetail(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        UserDTO user = support.prepare(session, model, PORTAL, "requests", "Chi tiết yêu cầu #" + id);
        try {
            var req = approvalService.getDetail(id);
            model.addAttribute("req", req);
            model.addAttribute("ownRequest", user != null && user.getId() != null && user.getId().equals(req.requestedBy()));
            return "admin/subscription-request-detail";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/subscription-requests";
        }
    }

    @PostMapping("/subscription-requests/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(value = CsrfGuard.PARAM, required = false) String csrf,
                          HttpSession session, Model model, RedirectAttributes ra) {
        return decide(id, true, null, csrf, session, model, ra);
    }

    @PostMapping("/subscription-requests/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String note,
                         @RequestParam(value = CsrfGuard.PARAM, required = false) String csrf,
                         HttpSession session, Model model, RedirectAttributes ra) {
        return decide(id, false, note, csrf, session, model, ra);
    }

    private String decide(Long id, boolean approve, String note, String csrf, HttpSession session, Model model, RedirectAttributes ra) {
        UserDTO user = support.prepare(session, model, PORTAL, "requests", null);
        if (!CsrfGuard.isValid(session, csrf)) return OperationSubscriptionController.csrfFailed(ra, "/admin/subscription-requests/" + id);
        try {
            approvalService.decide(id, approve, note, BillingActor.of(user));
            ra.addFlashAttribute("successMessage", approve ? "Đã phê duyệt yêu cầu #" + id + ". Thay đổi có hiệu lực ngay cho đơn hàng mới."
                    : "Đã từ chối yêu cầu #" + id + ". Dữ liệu gói học được giữ nguyên.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", OperationSubscriptionController.friendly(e));
        }
        return "redirect:/admin/subscription-requests/" + id;
    }

    @GetMapping("/subscriptions")
    public String plans(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                        @RequestParam(defaultValue = "0") int page, HttpSession session, Model model) {
        support.prepare(session, model, PORTAL, "plans", "Gói học");
        SubscriptionPlanStatus st = null;
        try {
            st = status == null || status.isBlank() ? null : SubscriptionPlanStatus.valueOf(status);
        } catch (IllegalArgumentException ignored) {
        }
        model.addAttribute("plans", planService.search(q, st, page, 10));
        model.addAttribute("summary", planService.summary());
        model.addAttribute("planStatuses", SubscriptionPlanStatus.values());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("statusFilter", st == null ? "" : st.name());
        model.addAttribute("readOnly", true);
        return "operation/subscriptions";
    }

    @GetMapping("/subscriptions/price-history")
    public String priceHistory(@RequestParam(required = false) Long planId, @RequestParam(defaultValue = "0") int page,
                               HttpSession session, Model model) {
        support.prepare(session, model, PORTAL, "priceHistory", "Lịch sử thay đổi giá");
        support.priceHistory(model, planId, page);
        return "operation/price-history";
    }

    @GetMapping("/transactions")
    public String transactions(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                               @RequestParam(required = false) Long planId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(defaultValue = "0") int page, HttpSession session, Model model) {
        support.prepare(session, model, PORTAL, "transactions", "Giao dịch Sandbox");
        support.transactions(model, q, status, planId, from, to, page);
        return "operation/transactions";
    }

    @GetMapping("/transactions/{id}")
    public String transactionDetail(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        support.prepare(session, model, PORTAL, "transactions", "Chi tiết giao dịch Sandbox");
        if (!support.transactionDetail(model, id)) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy giao dịch.");
            return "redirect:/admin/transactions";
        }
        return "operation/transaction-detail";
    }

    /** Đối soát với VNPay Sandbox (QueryDR). Chỉ ghi nhận kết quả có chữ ký hợp lệ từ VNPay; không tự đánh dấu thành công. */
    @PostMapping("/transactions/{id}/reconcile")
    public String reconcile(@PathVariable Long id, @RequestParam(value = CsrfGuard.PARAM, required = false) String csrf,
                            HttpSession session, HttpServletRequest request, Model model, RedirectAttributes ra) {
        UserDTO user = support.prepare(session, model, PORTAL, "transactions", null);
        if (!CsrfGuard.isValid(session, csrf)) return OperationSubscriptionController.csrfFailed(ra, "/admin/transactions/" + id);
        try {
            PaymentGateway.GatewayResult r = paymentService.reconcile(id, request.getRemoteAddr(), null, BillingActor.of(user), true);
            if (r == null || !r.queryOk()) {
                ra.addFlashAttribute("warningMessage", "Chưa đối soát được: " + (r == null ? "vui lòng thử lại sau." : r.message()
                        + (r.apiCode() != null ? " (mã " + r.apiCode() + ")" : "")));
            } else {
                ra.addFlashAttribute("successMessage", "Đã đối soát với VNPay Sandbox: trạng thái giao dịch "
                        + r.transactionStatus() + " – " + PaymentService.describe(r.transactionStatus()));
            }
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", OperationSubscriptionController.friendly(e));
        }
        return "redirect:/admin/transactions/" + id;
    }

    @GetMapping("/revenue")
    public String revenue(@RequestParam(required = false) String range,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          HttpSession session, Model model) {
        support.prepare(session, model, PORTAL, "revenue", "Báo cáo doanh thu thử nghiệm – Sandbox");
        support.revenue(model, range, from, to);
        return "operation/revenue";
    }

    @GetMapping("/revenue/export")
    public ResponseEntity<byte[]> exportRevenue(@RequestParam(required = false) String range,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return support.exportRevenue(range, from, to);
    }
}
