package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.dto.subscription.PaymentStatusDTO;
import com.hottopik.dto.subscription.SubscriptionPlanDTO;
import com.hottopik.entity.subscription.OrderStatus;
import com.hottopik.entity.subscription.SubscriptionOrder;
import com.hottopik.service.subscription.*;
import com.hottopik.util.AuthHelper;
import com.hottopik.util.CsrfGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Trang gói học của học viên: bảng giá, checkout, chuyển sang VNPay Sandbox, kết quả, gói của tôi, lịch sử giao dịch.
 * Người dùng hiện tại luôn lấy từ session; không nhận userId / giá từ frontend để tính tiền.
 */
@Slf4j
@Controller
@RequestMapping("/subscription")
public class SubscriptionController {

    private final SubscriptionPlanService planService;
    private final SubscriptionOrderService orderService;
    private final PaymentService paymentService;
    private final UserSubscriptionService userSubscriptionService;
    private final PaymentGateway gateway;

    public SubscriptionController(SubscriptionPlanService planService, SubscriptionOrderService orderService,
                                  PaymentService paymentService, UserSubscriptionService userSubscriptionService,
                                  PaymentGateway gateway) {
        this.planService = planService;
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.userSubscriptionService = userSubscriptionService;
        this.gateway = gateway;
    }

    /** Thêm CSRF token, mục menu đang chọn và người dùng hiện tại cho mọi trang gói học. */
    @ModelAttribute
    public void common(HttpSession session, Model model) {
        model.addAttribute("csrfToken", CsrfGuard.token(session));
        model.addAttribute("activePage", "vip");
        model.addAttribute("currentUser", AuthHelper.getLoggedInUser(session));
    }

    // ------------------------------------------------------------------ bảng giá

    /** GET /subscription/plans – bảng giá các gói đang bán, bảng so sánh Free / Premium và gói hiện tại của học viên. */
    @GetMapping("/plans")
    public String plans(HttpSession session, Model model) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        var all = planService.getActivePlans();
        model.addAttribute("pageTitle", "Gói học Premium");
        model.addAttribute("freePlan", all.stream().filter(p -> !p.isPremium()).findFirst().orElse(null));
        model.addAttribute("premiumPlans", all.stream().filter(SubscriptionPlanDTO::isPremium).toList());
        model.addAttribute("features", planService.getActiveFeatures());
        if (user != null && user.isStudent()) {
            model.addAttribute("currentSub", userSubscriptionService.getCurrentSubscription(user.getId()));
        }
        return "subscription/plans";
    }

    /** GET /subscription/plans/{id} – chi tiết một gói đang bán. */
    @GetMapping("/plans/{id}")
    public String planDetail(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        try {
            SubscriptionPlanDTO plan = planService.getPlanDetail(id);
            UserDTO user = AuthHelper.getLoggedInUser(session);
            model.addAttribute("pageTitle", plan.name());
            model.addAttribute("plan", plan);
            if (user != null && user.isStudent()) {
                model.addAttribute("currentSub", userSubscriptionService.getCurrentSubscription(user.getId()));
            }
            return "subscription/plan-detail";
        } catch (NoSuchElementException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/subscription/plans";
        }
    }

    // ------------------------------------------------------------------ gói của tôi

    /** GET /subscription/my-plan – gói hiện tại, số ngày còn lại, quyền lợi và lịch sử kích hoạt / gia hạn. */
    @GetMapping({"/my-plan", "/history"})
    public String myPlan(HttpSession session, Model model, RedirectAttributes ra) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        if (!user.isStudent()) return staffNotAllowed(ra);
        model.addAttribute("pageTitle", "Gói của tôi");
        model.addAttribute("currentSub", userSubscriptionService.getCurrentSubscription(user.getId()));
        model.addAttribute("history", userSubscriptionService.getSubscriptionHistory(user.getId()));
        model.addAttribute("recentOrders", orderService.getUserOrders(user.getId(), null, 0, 5).getContent());
        return "subscription/my-plan";
    }

    // ------------------------------------------------------------------ checkout

    /** GET /subscription/checkout/{planId} – trang xác nhận đơn hàng (giá lấy từ database). */
    @GetMapping("/checkout/{planId}")
    public String checkout(@PathVariable Long planId, HttpSession session, Model model, RedirectAttributes ra) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        if (!user.isStudent()) return staffNotAllowed(ra);
        SubscriptionPlanDTO plan;
        try {
            plan = planService.getPlanDetail(planId);
        } catch (NoSuchElementException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/subscription/plans";
        }
        if (!plan.isPremium()) {
            ra.addFlashAttribute("infoMessage", "Gói Free không cần thanh toán – bạn đang được dùng các quyền lợi Free.");
            return "redirect:/subscription/plans";
        }
        model.addAttribute("pageTitle", "Xác nhận đơn hàng");
        model.addAttribute("plan", plan);
        model.addAttribute("buyer", user);
        model.addAttribute("currentSub", userSubscriptionService.getCurrentSubscription(user.getId()));
        model.addAttribute("pendingOrder", orderService.findReusablePendingOrder(user.getId(), planId).orElse(null));
        model.addAttribute("gatewayReady", gateway.isConfigured());
        return "subscription/checkout";
    }

    /** POST /subscription/checkout – tạo đơn (chốt giá) rồi chuyển sang VNPay Sandbox; giá đổi thì hiển thị lại để xác nhận. */
    @PostMapping("/checkout")
    public String confirmCheckout(@RequestParam Long planId, @RequestParam(required = false) Long expectedPrice,
                                  @RequestParam(value = CsrfGuard.PARAM, required = false) String csrf,
                                  HttpSession session, HttpServletRequest request, Model model, RedirectAttributes ra) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        if (!user.isStudent()) return staffNotAllowed(ra);
        if (!CsrfGuard.isValid(session, csrf)) {
            ra.addFlashAttribute("errorMessage", "Phiên làm việc đã hết hạn. Vui lòng tải lại trang và thử lại.");
            return "redirect:/subscription/checkout/" + planId;
        }
        try {
            gateway.assertReady(); // chưa cấu hình / cấu hình sai môi trường thì không tạo đơn
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/subscription/checkout/" + planId;
        }
        SubscriptionOrder order;
        try {
            order = orderService.createOrder(user.getId(), planId, expectedPrice, BillingActor.of(user));
        } catch (PriceChangedException e) {
            ra.addFlashAttribute("warningMessage", e.getMessage());
            return "redirect:/subscription/checkout/" + planId;
        } catch (DataIntegrityViolationException e) {
            // Bấm xác nhận nhiều lần cùng lúc: dùng lại đơn đang chờ.
            Optional<SubscriptionOrder> existing = orderService.findReusablePendingOrder(user.getId(), planId);
            if (existing.isEmpty()) {
                ra.addFlashAttribute("errorMessage", "Không tạo được đơn hàng, vui lòng thử lại.");
                return "redirect:/subscription/checkout/" + planId;
            }
            order = existing.get();
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/subscription/checkout/" + planId;
        }
        return redirectToGateway(order.getOrderCode(), user, request, model, ra);
    }

    // ------------------------------------------------------------------ đơn hàng / thanh toán

    /** GET /subscription/payment/{orderCode} – chi tiết đơn hàng và các lần thanh toán (chỉ chủ đơn xem được). */
    @GetMapping({"/payment/{orderCode}", "/transactions/{orderCode}"})
    public String orderDetail(@PathVariable String orderCode, HttpSession session, Model model, RedirectAttributes ra) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        try {
            SubscriptionOrder order = orderService.getOrderForUser(orderCode, user.getId());
            var attempts = orderService.getAttempts(order.getId());
            model.addAttribute("pageTitle", "Đơn hàng " + order.getOrderCode());
            model.addAttribute("order", order);
            model.addAttribute("attempts", attempts);
            model.addAttribute("status", paymentService.toStatus(order, attempts));
            model.addAttribute("gatewayReady", gateway.isConfigured());
            return "subscription/order-detail";
        } catch (NoSuchElementException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/subscription/transactions";
        }
    }

    /** POST /subscription/payment/{orderCode}/retry – tạo lần thanh toán mới cho đơn còn hiệu lực. */
    @PostMapping("/payment/{orderCode}/retry")
    public String retry(@PathVariable String orderCode, @RequestParam(value = CsrfGuard.PARAM, required = false) String csrf,
                        HttpSession session, HttpServletRequest request, Model model, RedirectAttributes ra) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        if (!CsrfGuard.isValid(session, csrf)) {
            ra.addFlashAttribute("errorMessage", "Phiên làm việc đã hết hạn. Vui lòng tải lại trang và thử lại.");
            return "redirect:/subscription/payment/" + orderCode;
        }
        return redirectToGateway(orderCode, user, request, model, ra);
    }

    /** POST /subscription/payment/{orderCode}/cancel – học viên hủy đơn chưa thanh toán (đối soát trước khi hủy). */
    @PostMapping("/payment/{orderCode}/cancel")
    public String cancel(@PathVariable String orderCode, @RequestParam(value = CsrfGuard.PARAM, required = false) String csrf,
                         HttpSession session, HttpServletRequest request, RedirectAttributes ra) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        try {
            CsrfGuard.require(session, csrf);
            paymentService.cancelOrder(orderCode, user.getId(), request.getRemoteAddr());
            ra.addFlashAttribute("successMessage", "Đã hủy đơn hàng " + orderCode + ".");
        } catch (NoSuchElementException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/subscription/transactions";
        } catch (IllegalStateException | SecurityException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/subscription/payment/" + orderCode;
    }

    /** Kết quả thanh toán: chỉ hiển thị trạng thái đã được backend xác nhận trong database. */
    @GetMapping("/payment/result")
    public String result(@RequestParam(required = false) String orderCode, @RequestParam(required = false) String invalid,
                         HttpSession session, HttpServletRequest request, Model model) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        model.addAttribute("pageTitle", "Kết quả thanh toán");
        if (invalid != null || orderCode == null) {
            model.addAttribute("invalidReturn", true);
            return "subscription/payment-result";
        }
        try {
            PaymentStatusDTO status = paymentService.getPaymentStatus(orderCode, user.getId(), request.getRemoteAddr());
            model.addAttribute("status", status);
        } catch (NoSuchElementException e) {
            model.addAttribute("invalidReturn", true);
        }
        return "subscription/payment-result";
    }

    /** GET /subscription/transactions – lịch sử đơn hàng của học viên (lọc trạng thái, phân trang). */
    @GetMapping("/transactions")
    public String transactions(@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
                               HttpSession session, Model model, RedirectAttributes ra) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        if (!user.isStudent()) return staffNotAllowed(ra);
        OrderStatus filter = parse(status);
        model.addAttribute("pageTitle", "Lịch sử giao dịch");
        model.addAttribute("orders", orderService.getUserOrders(user.getId(), filter, page, 10));
        model.addAttribute("statusFilter", filter == null ? "" : filter.name());
        model.addAttribute("orderStatuses", OrderStatus.values());
        return "subscription/transactions";
    }

    // ------------------------------------------------------------------ helpers

    private String redirectToGateway(String orderCode, UserDTO user, HttpServletRequest request, Model model, RedirectAttributes ra) {
        try {
            String url = paymentService.createPayment(orderCode, user.getId(), request.getRemoteAddr());
            SubscriptionOrder order = orderService.getOrderForUser(orderCode, user.getId());
            model.addAttribute("pageTitle", "Chuyển đến VNPay Sandbox");
            model.addAttribute("order", order);
            model.addAttribute("paymentUrl", url);
            return "subscription/redirect";
        } catch (NoSuchElementException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/subscription/transactions";
        } catch (IllegalStateException | IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/subscription/payment/" + orderCode;
        }
    }

    private static String staffNotAllowed(RedirectAttributes ra) {
        ra.addFlashAttribute("errorMessage", "Chỉ tài khoản học viên mới mua và sử dụng gói học.");
        return "redirect:/subscription/plans";
    }

    private static OrderStatus parse(String status) {
        try {
            return status == null || status.isBlank() ? null : OrderStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
