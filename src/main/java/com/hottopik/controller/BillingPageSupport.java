package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.subscription.PaymentStatus;
import com.hottopik.repository.subscription.PaymentCallbackLogRepository;
import com.hottopik.service.subscription.*;
import com.hottopik.util.AuthHelper;
import com.hottopik.util.CsrfGuard;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.time.LocalDate;

/** Phần dùng chung giữa trang quản lý của Operation Manager và Admin (giao dịch, doanh thu, lịch sử giá). */
@Component
public class BillingPageSupport {

    private final PaymentService paymentService;
    private final RevenueService revenueService;
    private final SubscriptionPlanService planService;
    private final PaymentCallbackLogRepository callbackLogs;

    public BillingPageSupport(PaymentService paymentService, RevenueService revenueService,
                              SubscriptionPlanService planService, PaymentCallbackLogRepository callbackLogs) {
        this.paymentService = paymentService;
        this.revenueService = revenueService;
        this.planService = planService;
        this.callbackLogs = callbackLogs;
    }

    public UserDTO prepare(HttpSession session, Model model, String portal, String active, String title) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        model.addAttribute("currentUser", user);
        model.addAttribute("portal", portal);
        model.addAttribute("activeMenu", active);
        model.addAttribute("pageTitle", title);
        model.addAttribute("csrfToken", CsrfGuard.token(session));
        return user;
    }

    public void transactions(Model model, String q, String status, Long planId,
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, int page) {
        PaymentStatus st = parseStatus(status);
        model.addAttribute("transactions", paymentService.search(q, st, planId,
                from == null ? null : from.atStartOfDay(), to == null ? null : to.plusDays(1).atStartOfDay(), page, 15));
        model.addAttribute("summary", paymentService.transactionSummary());
        model.addAttribute("plans", planService.getAllForManagement());
        model.addAttribute("paymentStatuses", PaymentStatus.values());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("statusFilter", st == null ? "" : st.name());
        model.addAttribute("planFilter", planId);
        model.addAttribute("fromFilter", from);
        model.addAttribute("toFilter", to);
    }

    public boolean transactionDetail(Model model, Long id) {
        var txn = paymentService.getTransaction(id);
        if (txn.isEmpty()) return false;
        model.addAttribute("txn", txn.get());
        model.addAttribute("callbackLogs", callbackLogs.findTop20ByOrderCodeOrderByReceivedAtDesc(txn.get().txnRef()));
        return true;
    }

    public void revenue(Model model, String range, LocalDate from, LocalDate to) {
        RevenueService.Range r = revenueService.resolveRange(range, from, to);
        model.addAttribute("range", r);
        model.addAttribute("dashboard", revenueService.getDashboard(r));
        model.addAttribute("fromFilter", from);
        model.addAttribute("toFilter", to);
    }

    public ResponseEntity<byte[]> exportRevenue(String range, LocalDate from, LocalDate to) {
        byte[] data = revenueService.exportExcel(revenueService.resolveRange(range, from, to));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bao-cao-doanh-thu-sandbox.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    public void priceHistory(Model model, Long planId, int page) {
        model.addAttribute("history", planService.getPriceHistory(planId, page, 20));
        model.addAttribute("plans", planService.getAllForManagement());
        model.addAttribute("planFilter", planId);
    }

    private static PaymentStatus parseStatus(String status) {
        try {
            return status == null || status.isBlank() ? null : PaymentStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
