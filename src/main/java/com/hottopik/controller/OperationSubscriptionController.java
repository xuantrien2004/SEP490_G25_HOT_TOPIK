package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.dto.subscription.ChangePriceRequest;
import com.hottopik.dto.subscription.CreatePlanRequest;
import com.hottopik.dto.subscription.SubscriptionPlanDTO;
import com.hottopik.dto.subscription.UpdatePlanRequest;
import com.hottopik.entity.subscription.PlanRequestStatus;
import com.hottopik.entity.subscription.PlanRequestType;
import com.hottopik.entity.subscription.SubscriptionPlanStatus;
import com.hottopik.service.subscription.*;
import com.hottopik.util.CsrfGuard;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * Subscription Management cho Operation Manager (RoleSecurityInterceptor đã chặn /operation/** cho OM/Admin).
 * Mọi thay đổi trên gói đang kinh doanh chỉ tạo yêu cầu phê duyệt; không sửa trực tiếp giá chính thức,
 * không có thao tác đánh dấu giao dịch thành công / sửa số tiền / xóa lịch sử / cấp Premium.
 */
@Controller
@RequestMapping("/operation")
public class OperationSubscriptionController {

    private static final String PORTAL = "operation";

    private final SubscriptionPlanService planService;
    private final PlanApprovalService approvalService;
    private final UserSubscriptionService userSubscriptionService;
    private final BillingPageSupport support;

    public OperationSubscriptionController(SubscriptionPlanService planService, PlanApprovalService approvalService,
                                           UserSubscriptionService userSubscriptionService, BillingPageSupport support) {
        this.planService = planService;
        this.approvalService = approvalService;
        this.userSubscriptionService = userSubscriptionService;
        this.support = support;
    }

    // ------------------------------------------------------------------ danh sách gói

    /** GET /operation/subscriptions – danh sách gói (tìm kiếm, lọc, phân trang) và thống kê nhanh. */
    @GetMapping("/subscriptions")
    public String list(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "0") int page, HttpSession session, Model model) {
        support.prepare(session, model, PORTAL, "plans", "Quản lý gói học");
        SubscriptionPlanStatus st = parsePlanStatus(status);
        model.addAttribute("plans", planService.search(q, st, page, 10));
        model.addAttribute("summary", planService.summary());
        model.addAttribute("planStatuses", SubscriptionPlanStatus.values());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("statusFilter", st == null ? "" : st.name());
        model.addAttribute("readOnly", false);
        return "operation/subscriptions";
    }

    /** GET /operation/subscriptions/create – form tạo bản nháp gói Premium. */
    @GetMapping("/subscriptions/create")
    public String createForm(HttpSession session, Model model) {
        support.prepare(session, model, PORTAL, "plans", "Tạo bản nháp gói học");
        if (!model.containsAttribute("form")) model.addAttribute("form", new CreatePlanRequest());
        model.addAttribute("mode", "create");
        return "operation/subscription-form";
    }

    /** POST /operation/subscriptions/create – lưu bản nháp (chưa bán cho đến khi Admin duyệt). */
    @PostMapping("/subscriptions/create")
    public String create(@Valid @ModelAttribute("form") CreatePlanRequest form, BindingResult binding,
                         @RequestParam(value = CsrfGuard.PARAM, required = false) String csrf,
                         HttpSession session, Model model, RedirectAttributes ra) {
        UserDTO user = support.prepare(session, model, PORTAL, "plans", "Tạo bản nháp gói học");
        if (!CsrfGuard.isValid(session, csrf)) return csrfFailed(ra, "/operation/subscriptions");
        if (binding.hasErrors()) {
            model.addAttribute("mode", "create");
            model.addAttribute("errorMessage", binding.getAllErrors().get(0).getDefaultMessage());
            return "operation/subscription-form";
        }
        try {
            planService.createDraft(form, BillingActor.of(user));
            ra.addFlashAttribute("successMessage", "Đã tạo bản nháp gói \"" + form.getName() + "\". Gói chưa được bán cho đến khi Admin phê duyệt.");
            return "redirect:/operation/subscriptions?status=DRAFT";
        } catch (IllegalArgumentException | DataIntegrityViolationException e) {
            model.addAttribute("mode", "create");
            model.addAttribute("errorMessage", e instanceof DataIntegrityViolationException ? "Mã gói đã tồn tại." : e.getMessage());
            return "operation/subscription-form";
        }
    }

    /** GET /operation/subscriptions/{id}/edit – form sửa gói. */
    @GetMapping("/subscriptions/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        support.prepare(session, model, PORTAL, "plans", "Chỉnh sửa gói học");
        try {
            SubscriptionPlanDTO plan = planService.getForManagement(id);
            if (!model.containsAttribute("form")) {
                UpdatePlanRequest form = new UpdatePlanRequest();
                form.setName(plan.name());
                form.setDescription(plan.description());
                form.setPrice(plan.price());
                form.setDurationDays(plan.durationDays());
                form.setFeatured(plan.featured());
                form.setSortOrder(plan.sortOrder());
                form.setFeatureCodes(plan.features().stream().map(f -> f.code()).toList());
                model.addAttribute("form", form);
            }
            model.addAttribute("plan", plan);
            model.addAttribute("premiumFeatures", planService.getActivePremiumFeatures());
            model.addAttribute("mode", "edit");
            return "operation/subscription-form";
        } catch (NoSuchElementException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/operation/subscriptions";
        }
    }

    /** POST /operation/subscriptions/{id}/update – bản nháp lưu thẳng; gói đã duyệt thì tạo yêu cầu phê duyệt. */
    @PostMapping("/subscriptions/{id}/update")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") UpdatePlanRequest form, BindingResult binding,
                         @RequestParam(value = CsrfGuard.PARAM, required = false) String csrf,
                         HttpSession session, Model model, RedirectAttributes ra) {
        UserDTO user = support.prepare(session, model, PORTAL, "plans", "Chỉnh sửa gói học");
        if (!CsrfGuard.isValid(session, csrf)) return csrfFailed(ra, "/operation/subscriptions");
        if (binding.hasErrors()) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errorMessage", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/operation/subscriptions/" + id + "/edit";
        }
        try {
            boolean requested = planService.updatePlan(id, form, BillingActor.of(user));
            ra.addFlashAttribute("successMessage", requested
                    ? "Đã gửi yêu cầu thay đổi cho Admin phê duyệt. Gói vẫn được bán theo thông tin hiện tại cho đến khi được duyệt."
                    : "Đã lưu bản nháp.");
            return "redirect:/operation/subscriptions";
        } catch (RuntimeException e) {
            ra.addFlashAttribute("form", form);
            ra.addFlashAttribute("errorMessage", friendly(e));
            return "redirect:/operation/subscriptions/" + id + "/edit";
        }
    }

    /** POST /operation/subscriptions/{id}/submit – gửi bản nháp cho Admin duyệt mở bán (CREATE_PLAN). */
    @PostMapping("/subscriptions/{id}/submit")
    public String submitDraft(@PathVariable Long id, @RequestParam(required = false) String reason,
                              @RequestParam(value = CsrfGuard.PARAM, required = false) String csrf,
                              HttpSession session, Model model, RedirectAttributes ra) {
        return act(session, model, ra, csrf, () -> {
            planService.submitDraft(id, reason, actor(session, model));
            return "Đã gửi yêu cầu tạo gói mới cho Admin phê duyệt.";
        });
    }

    /** POST /operation/subscriptions/{id}/change-price – đề xuất giá mới (UPDATE_PRICE), giá chính thức chưa đổi. */
    @PostMapping("/subscriptions/{id}/change-price")
    public String changePrice(@PathVariable Long id, @Valid @ModelAttribute ChangePriceRequest form, BindingResult binding,
                              @RequestParam(value = CsrfGuard.PARAM, required = false) String csrf,
                              HttpSession session, Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            ra.addFlashAttribute("errorMessage", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/operation/subscriptions";
        }
        return act(session, model, ra, csrf, () -> {
            planService.requestPriceChange(id, form, actor(session, model));
            return "Đã gửi yêu cầu thay đổi giá. Giá hiện tại vẫn được áp dụng cho đến khi Admin phê duyệt.";
        });
    }

    /** POST /operation/subscriptions/{id}/activate – đề xuất mở bán lại gói đang tạm ngừng. */
    @PostMapping("/subscriptions/{id}/activate")
    public String activate(@PathVariable Long id, @RequestParam(required = false) String reason,
                           @RequestParam(value = CsrfGuard.PARAM, required = false) String csrf,
                           HttpSession session, Model model, RedirectAttributes ra) {
        return statusRequest(id, PlanRequestType.ACTIVATE_PLAN, reason, csrf, session, model, ra);
    }

    /** POST /operation/subscriptions/{id}/deactivate – đề xuất ngừng bán gói. */
    @PostMapping("/subscriptions/{id}/deactivate")
    public String deactivate(@PathVariable Long id, @RequestParam(required = false) String reason,
                             @RequestParam(value = CsrfGuard.PARAM, required = false) String csrf,
                             HttpSession session, Model model, RedirectAttributes ra) {
        return statusRequest(id, PlanRequestType.DEACTIVATE_PLAN, reason, csrf, session, model, ra);
    }

    /** POST /operation/subscriptions/{id}/archive – đề xuất lưu trữ (ngừng kinh doanh) gói. */
    @PostMapping("/subscriptions/{id}/archive")
    public String archive(@PathVariable Long id, @RequestParam(required = false) String reason,
                          @RequestParam(value = CsrfGuard.PARAM, required = false) String csrf,
                          HttpSession session, Model model, RedirectAttributes ra) {
        return statusRequest(id, PlanRequestType.ARCHIVE_PLAN, reason, csrf, session, model, ra);
    }

    private String statusRequest(Long id, PlanRequestType type, String reason, String csrf,
                                 HttpSession session, Model model, RedirectAttributes ra) {
        return act(session, model, ra, csrf, () -> {
            planService.requestStatusChange(id, type, reason, actor(session, model));
            return "Đã gửi yêu cầu \"" + type.getLabel() + "\" cho Admin phê duyệt.";
        });
    }

    /** GET /operation/subscriptions/{id}/price-history – lịch sử giá của một gói. */
    @GetMapping("/subscriptions/{id}/price-history")
    public String priceHistory(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, HttpSession session, Model model) {
        support.prepare(session, model, PORTAL, "priceHistory", "Lịch sử thay đổi giá");
        support.priceHistory(model, id, page);
        return "operation/price-history";
    }

    /** GET /operation/subscriptions/price-history – lịch sử giá của tất cả gói. */
    @GetMapping("/subscriptions/price-history")
    public String allPriceHistory(@RequestParam(required = false) Long planId, @RequestParam(defaultValue = "0") int page,
                                  HttpSession session, Model model) {
        support.prepare(session, model, PORTAL, "priceHistory", "Lịch sử thay đổi giá");
        support.priceHistory(model, planId, page);
        return "operation/price-history";
    }

    // ------------------------------------------------------------------ yêu cầu đã gửi

    /** GET /operation/subscriptions/requests – các yêu cầu đã gửi, kết quả và lý do từ chối. */
    @GetMapping("/subscriptions/requests")
    public String requests(@RequestParam(required = false) String status, @RequestParam(defaultValue = "false") boolean mine,
                           @RequestParam(defaultValue = "0") int page, HttpSession session, Model model) {
        UserDTO user = support.prepare(session, model, PORTAL, "requests", "Yêu cầu phê duyệt gói học");
        PlanRequestStatus st = parseRequestStatus(status);
        model.addAttribute("requests", approvalService.search(st, mine ? user.getId() : null, page, 10));
        model.addAttribute("requestStatuses", PlanRequestStatus.values());
        model.addAttribute("statusFilter", st == null ? "" : st.name());
        model.addAttribute("mine", mine);
        return "operation/subscription-requests";
    }

    /** POST /operation/subscriptions/requests/{id}/cancel – người gửi hủy yêu cầu đang chờ duyệt. */
    @PostMapping("/subscriptions/requests/{id}/cancel")
    public String cancelRequest(@PathVariable Long id, @RequestParam(value = CsrfGuard.PARAM, required = false) String csrf,
                                HttpSession session, Model model, RedirectAttributes ra) {
        if (!CsrfGuard.isValid(session, csrf)) return csrfFailed(ra, "/operation/subscriptions/requests");
        try {
            approvalService.cancel(id, actor(session, model));
            ra.addFlashAttribute("successMessage", "Đã hủy yêu cầu #" + id + ".");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", friendly(e));
        }
        return "redirect:/operation/subscriptions/requests";
    }

    // ------------------------------------------------------------------ giao dịch, Premium, doanh thu

    /** GET /operation/transactions – danh sách giao dịch Sandbox (tìm kiếm, lọc, phân trang). */
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

    /** GET /operation/transactions/{id} – chi tiết giao dịch và nhật ký callback. */
    @GetMapping("/transactions/{id}")
    public String transactionDetail(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        support.prepare(session, model, PORTAL, "transactions", "Chi tiết giao dịch Sandbox");
        if (!support.transactionDetail(model, id)) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy giao dịch.");
            return "redirect:/operation/transactions";
        }
        return "operation/transaction-detail";
    }

    /** GET /operation/premium-users – học viên đang dùng Premium. */
    @GetMapping("/premium-users")
    public String premiumUsers(@RequestParam(defaultValue = "0") int page, HttpSession session, Model model) {
        support.prepare(session, model, PORTAL, "premiumUsers", "Học viên Premium");
        model.addAttribute("users", userSubscriptionService.getActivePremiumUsers(page, 15));
        return "operation/premium-users";
    }

    /** GET /operation/revenue – báo cáo doanh thu thử nghiệm (Sandbox). */
    @GetMapping("/revenue")
    public String revenue(@RequestParam(required = false) String range,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          HttpSession session, Model model) {
        support.prepare(session, model, PORTAL, "revenue", "Báo cáo doanh thu thử nghiệm – Sandbox");
        support.revenue(model, range, from, to);
        return "operation/revenue";
    }

    /** GET /operation/revenue/export – xuất báo cáo doanh thu Sandbox ra Excel. */
    @GetMapping("/revenue/export")
    public ResponseEntity<byte[]> exportRevenue(@RequestParam(required = false) String range,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return support.exportRevenue(range, from, to);
    }

    // ------------------------------------------------------------------ helpers

    /** Người thực hiện thao tác, lấy từ session. */
    private BillingActor actor(HttpSession session, Model model) {
        return BillingActor.of(support.prepare(session, model, PORTAL, "plans", null));
    }

    /** Chạy một thao tác gửi yêu cầu: kiểm tra CSRF, gắn thông báo thành công / lỗi rồi quay về danh sách gói. */
    private String act(HttpSession session, Model model, RedirectAttributes ra, String csrf, Supplier<String> action) {
        if (!CsrfGuard.isValid(session, csrf)) return csrfFailed(ra, "/operation/subscriptions");
        try {
            ra.addFlashAttribute("successMessage", action.get());
        } catch (RuntimeException e) {
            ra.addFlashAttribute("errorMessage", friendly(e));
        }
        return "redirect:/operation/subscriptions";
    }

    /** Đổi exception thành thông báo dễ hiểu cho người dùng (không lộ stack trace). */
    static String friendly(RuntimeException e) {
        if (e instanceof DataIntegrityViolationException) {
            return "Gói này đang có yêu cầu chờ phê duyệt hoặc dữ liệu bị trùng. Vui lòng tải lại trang.";
        }
        if (e instanceof org.springframework.orm.ObjectOptimisticLockingFailureException) {
            return "Dữ liệu vừa được người khác cập nhật. Vui lòng tải lại trang và thử lại.";
        }
        if (e instanceof IllegalArgumentException || e instanceof IllegalStateException
                || e instanceof NoSuchElementException || e instanceof SecurityException) {
            return e.getMessage();
        }
        return "Có lỗi xảy ra, vui lòng thử lại.";
    }

    static String csrfFailed(RedirectAttributes ra, String back) {
        ra.addFlashAttribute("errorMessage", "Phiên làm việc đã hết hạn. Vui lòng tải lại trang và thử lại.");
        return "redirect:" + back;
    }

    private static SubscriptionPlanStatus parsePlanStatus(String s) {
        try {
            return s == null || s.isBlank() ? null : SubscriptionPlanStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static PlanRequestStatus parseRequestStatus(String s) {
        try {
            return s == null || s.isBlank() ? null : PlanRequestStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
