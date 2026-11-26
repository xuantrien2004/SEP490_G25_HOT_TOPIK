package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.service.subscription.PaymentService;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Endpoint VNPay Sandbox.
 * - Return URL: VNPay chuyển trình duyệt học viên về; chỉ dùng để hiển thị (không kích hoạt Premium từ query parameter).
 * - IPN: VNPay gọi server-to-server bằng GET (theo tài liệu VNPay 2.1.0), không cần đăng nhập nhưng bắt buộc chữ ký hợp lệ,
 *   trả JSON {"RspCode","Message"}.
 * - Trạng thái đơn: cần đăng nhập và là chủ đơn hàng.
 */
@Controller
public class VNPayPaymentController {

    private final PaymentService paymentService;

    public VNPayPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** GET /payment/vnpay/return – VNPay đưa học viên quay về: kiểm tra chữ ký rồi chuyển sang trang kết quả. */
    @GetMapping("/payment/vnpay/return")
    public String vnpayReturn(@RequestParam Map<String, String> params, HttpServletRequest request) {
        String orderCode = paymentService.handleReturn(params, request.getRemoteAddr());
        if (orderCode == null) return "redirect:/subscription/payment/result?invalid=1";
        return "redirect:/subscription/payment/result?orderCode=" + URLEncoder.encode(orderCode, StandardCharsets.UTF_8);
    }

    /** GET /payment/vnpay/ipn – VNPay gọi server-to-server; trả {"RspCode","Message"} theo quy định VNPay. */
    @GetMapping(value = "/payment/vnpay/ipn", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        PaymentService.IpnResponse r = paymentService.handleIpn(params);
        return Map.of("RspCode", r.rspCode(), "Message", r.message());
    }

    /** GET /api/payments/{orderCode}/status – trạng thái đơn cho polling (cần đăng nhập và là chủ đơn). */
    @GetMapping(value = "/api/payments/{orderCode}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> status(@PathVariable String orderCode, HttpSession session, HttpServletRequest request) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "Vui lòng đăng nhập."));
        try {
            return ResponseEntity.ok(paymentService.getPaymentStatus(orderCode, user.getId(), request.getRemoteAddr()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("message", "Không tìm thấy đơn hàng."));
        }
    }
}
