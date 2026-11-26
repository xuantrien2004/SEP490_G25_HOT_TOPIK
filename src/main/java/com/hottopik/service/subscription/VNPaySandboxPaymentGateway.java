package com.hottopik.service.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hottopik.config.PaymentProperties;
import com.hottopik.util.OrderCodeGenerator;
import com.hottopik.util.PaymentAmountUtil;
import com.hottopik.util.PaymentDateTimeUtil;
import com.hottopik.util.VNPaySignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tích hợp VNPay SANDBOX (API 2.1.0). Không có cấu hình Production: URL bị kiểm tra phải là sandbox.vnpayment.vn.
 *
 * Tham số và quy tắc ký theo tài liệu VNPay 2.1.0 (https://sandbox.vnpaytest.vn/apis/docs/):
 * - Tạo URL: vnp_Version, vnp_Command=pay, vnp_TmnCode, vnp_Amount (VND x 100), vnp_CurrCode=VND, vnp_TxnRef,
 *   vnp_OrderInfo, vnp_OrderType, vnp_Locale, vnp_ReturnUrl, vnp_IpAddr, vnp_CreateDate, vnp_ExpireDate (yyyyMMddHHmmss, GMT+7),
 *   vnp_SecureHash = HMAC-SHA512 trên chuỗi tham số đã sắp xếp.
 * - QueryDR: POST JSON, checksum nối bằng '|'.
 * Hãy đối chiếu lại tài liệu chính thức trước khi demo (VNPay có thể cập nhật).
 */
@Slf4j
@Service
public class VNPaySandboxPaymentGateway implements PaymentGateway {

    private final PaymentProperties props;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public VNPaySandboxPaymentGateway(PaymentProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
    }

    @Override
    public String provider() {
        return PaymentProperties.PROVIDER;
    }

    @Override
    public boolean isConfigured() {
        return props.isConfigured();
    }

    @Override
    public void assertReady() {
        props.assertSandboxReady();
    }

    @Override
    public String merchantCode() {
        return props.getTmnCode();
    }

    @Override
    public String createPaymentUrl(PaymentUrlRequest r) {
        assertReady();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("vnp_Version", props.getVersion());
        p.put("vnp_Command", "pay");
        p.put("vnp_TmnCode", props.getTmnCode());
        p.put("vnp_Amount", PaymentAmountUtil.toVnpAmount(r.amountVnd()));
        p.put("vnp_CurrCode", PaymentProperties.CURRENCY);
        p.put("vnp_TxnRef", r.txnRef());
        p.put("vnp_OrderInfo", r.orderInfo());
        p.put("vnp_OrderType", "other");
        p.put("vnp_Locale", "vn");
        p.put("vnp_ReturnUrl", props.getReturnUrl());
        p.put("vnp_IpAddr", r.ipAddr());
        p.put("vnp_CreateDate", r.createDate());
        p.put("vnp_ExpireDate", r.expireDate());
        String query = VNPaySignatureUtil.buildQuery(p);
        String hash = VNPaySignatureUtil.sign(p, props.getHashSecret());
        return props.getPaymentUrl() + "?" + query + "&" + VNPaySignatureUtil.SECURE_HASH + "=" + hash;
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        return props.isConfigured() && VNPaySignatureUtil.verify(params, props.getHashSecret());
    }

    @Override
    public GatewayResult queryTransaction(String txnRef, String transactionDate, String orderInfo, String ipAddr) {
        try {
            assertReady();
        } catch (IllegalStateException e) {
            return failed("Chưa cấu hình VNPay Sandbox nên chưa thể đối soát.");
        }
        String requestId = OrderCodeGenerator.requestId();
        String createDate = PaymentDateTimeUtil.toVnp(PaymentDateTimeUtil.now());
        String command = "querydr";
        String data = String.join("|", requestId, props.getVersion(), command, props.getTmnCode(), txnRef,
                transactionDate, createDate, ipAddr, orderInfo);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", props.getVersion());
        body.put("vnp_Command", command);
        body.put("vnp_TmnCode", props.getTmnCode());
        body.put("vnp_TxnRef", txnRef);
        body.put("vnp_OrderInfo", orderInfo);
        body.put("vnp_TransactionDate", transactionDate);
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", ipAddr);
        body.put("vnp_SecureHash", VNPaySignatureUtil.hmacSHA512(props.getHashSecret(), data));
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(props.getApiUrl()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) return failed("VNPay QueryDR trả về HTTP " + resp.statusCode());
            JsonNode n = json.readTree(resp.body());
            String apiCode = text(n, "vnp_ResponseCode");
            // Phản hồi lỗi của VNPay (ví dụ 91 không tìm thấy, 99 giao dịch chưa hoàn tất / lỗi khác) có thể không kèm chữ ký.
            // Chỉ dùng để hiển thị lý do, KHÔNG dùng để cập nhật trạng thái giao dịch.
            if (text(n, "vnp_SecureHash").isEmpty() && !"00".equals(apiCode)) {
                return new GatewayResult(false, apiCode, "VNPay chưa trả kết quả giao dịch (mã " + apiCode + ": "
                        + text(n, "vnp_Message") + "). Có thể học viên chưa hoàn tất thanh toán.", txnRef, null,
                        null, null, null, null, null, null);
            }
            String checksumData = String.join("|", text(n, "vnp_ResponseId"), text(n, "vnp_Command"), apiCode,
                    text(n, "vnp_Message"), text(n, "vnp_TmnCode"), text(n, "vnp_TxnRef"), text(n, "vnp_Amount"),
                    text(n, "vnp_BankCode"), text(n, "vnp_PayDate"), text(n, "vnp_TransactionNo"),
                    text(n, "vnp_TransactionType"), text(n, "vnp_TransactionStatus"), text(n, "vnp_OrderInfo"),
                    text(n, "vnp_PromotionCode"), text(n, "vnp_PromotionAmount"));
            String expected = VNPaySignatureUtil.hmacSHA512(props.getHashSecret(), checksumData);
            if (!expected.equalsIgnoreCase(text(n, "vnp_SecureHash"))) {
                log.warn("[VNPay Sandbox] Chữ ký phản hồi QueryDR không hợp lệ cho txnRef={}", txnRef);
                return failed("Chữ ký phản hồi đối soát không hợp lệ.");
            }
            if (!"00".equals(apiCode)) {
                return new GatewayResult(false, apiCode, text(n, "vnp_Message"), txnRef, text(n, "vnp_TmnCode"),
                        null, null, null, null, null, null);
            }
            // Kết quả truy vấn hợp lệ: transactionStatus = 00 nghĩa là thanh toán thành công.
            String status = text(n, "vnp_TransactionStatus");
            return new GatewayResult(true, apiCode, text(n, "vnp_Message"), text(n, "vnp_TxnRef"), text(n, "vnp_TmnCode"),
                    "00".equals(status) ? "00" : status, status, blankToNull(text(n, "vnp_TransactionNo")),
                    blankToNull(text(n, "vnp_BankCode")), PaymentAmountUtil.fromVnpAmount(text(n, "vnp_Amount")),
                    blankToNull(text(n, "vnp_PayDate")));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed("Đối soát bị gián đoạn.");
        } catch (Exception e) {
            log.warn("[VNPay Sandbox] Không gọi được QueryDR cho txnRef={}: {}", txnRef, e.getMessage());
            return failed("Không kết nối được API đối soát VNPay Sandbox.");
        }
    }

    private static GatewayResult failed(String message) {
        return new GatewayResult(false, null, message, null, null, null, null, null, null, null, null);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
