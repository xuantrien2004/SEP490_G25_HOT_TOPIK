package com.hottopik.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Cấu hình VNPay Sandbox. Đọc trực tiếp từ biến môi trường (VNPAY_TMN_CODE, VNPAY_HASH_SECRET, ...),
 * không ghi credential vào source code / application.properties. Xem docs/vnpay-sandbox/HUONG_DAN_VNPAY_SANDBOX.md.
 *
 * Module này CHỈ chạy môi trường SANDBOX: URL thanh toán và URL API phải thuộc host Sandbox (sandbox.vnpaytest.vn / sandbox.vnpayment.vn),
 * nếu cấu hình nhầm sang Production thì hệ thống từ chối tạo giao dịch.
 */
@Component
public class PaymentProperties {

    public static final String ENVIRONMENT = "SANDBOX";
    public static final String PROVIDER = "VNPAY";
    public static final String CURRENCY = "VND";
    /** Host Sandbox chính thức: VNPay hiện cấp sandbox.vnpaytest.vn (email đăng ký), tài liệu cũ dùng sandbox.vnpayment.vn. */
    public static final java.util.Set<String> SANDBOX_HOSTS = java.util.Set.of("sandbox.vnpaytest.vn", "sandbox.vnpayment.vn");

    @Value("${VNPAY_TMN_CODE:}")
    private String tmnCode;

    @Value("${VNPAY_HASH_SECRET:}")
    private String hashSecret;

    // Trang thanh toán mở trên trình duyệt: sandbox.vnpayment.vn có chứng chỉ công khai hợp lệ
    // (một số mạng lọc DNS như Cisco Umbrella chặn sandbox.vnpaytest.vn trên trình duyệt).
    @Value("${VNPAY_PAYMENT_URL:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String paymentUrl;

    @Value("${VNPAY_RETURN_URL:http://localhost:8080/payment/vnpay/return}")
    private String returnUrl;

    /** Chỉ để hiển thị trong hướng dẫn: IPN URL được khai báo trên cổng merchant của VNPay Sandbox. */
    @Value("${VNPAY_IPN_URL:}")
    private String ipnUrl;

    // API QueryDR do server (Java) gọi: sandbox.vnpaytest.vn dùng được với truststore mặc định của Java 17,
    // còn chứng chỉ gốc của sandbox.vnpayment.vn (Sectigo R46) chưa có trong JDK cũ.
    @Value("${VNPAY_API_URL:https://sandbox.vnpaytest.vn/merchant_webapi/api/transaction}")
    private String apiUrl;

    @Value("${VNPAY_VERSION:2.1.0}")
    private String version;

    @Value("${PAYMENT_EXPIRATION_MINUTES:15}")
    private int expirationMinutes;

    /**
     * Mặc định TẮT. Bật (true) khi VNPay Sandbox không gửi IPN và QueryDR không trả kết quả: khi đó dữ liệu Return URL
     * có chữ ký HMAC-SHA512 hợp lệ, đúng merchant, đúng số tiền, mã 00/00 được dùng làm căn cứ xác nhận thanh toán.
     * Chỉ dùng cho demo / kiểm thử Sandbox.
     */
    @Value("${VNPAY_ACCEPT_SIGNED_RETURN:false}")
    private boolean acceptSignedReturn;

    public boolean isAcceptSignedReturn() { return acceptSignedReturn; }

    public boolean isConfigured() {
        return !isBlank(tmnCode) && !isBlank(hashSecret);
    }

    /** Ném lỗi nếu thiếu credential hoặc URL không thuộc VNPay Sandbox. */
    public void assertSandboxReady() {
        if (!isConfigured()) {
            throw new IllegalStateException("Chưa cấu hình VNPay Sandbox (VNPAY_TMN_CODE, VNPAY_HASH_SECRET). "
                    + "Vui lòng xem hướng dẫn trong docs/vnpay-sandbox.");
        }
        assertSandboxUrl(paymentUrl, "VNPAY_PAYMENT_URL");
        assertSandboxUrl(apiUrl, "VNPAY_API_URL");
    }

    static void assertSandboxUrl(String url, String name) {
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !SANDBOX_HOSTS.contains(host)) {
                throw new IllegalStateException(name + " phải là URL https của VNPay Sandbox (" + String.join(", ", SANDBOX_HOSTS)
                        + "). Hệ thống không cho phép dùng VNPay Production.");
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException(name + " không hợp lệ.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public String getTmnCode() { return tmnCode == null ? "" : tmnCode.trim(); }
    /** Không log giá trị này. */
    public String getHashSecret() { return hashSecret == null ? "" : hashSecret.trim(); }
    public String getPaymentUrl() { return paymentUrl.trim(); }
    public String getReturnUrl() { return returnUrl.trim(); }
    public String getIpnUrl() { return ipnUrl; }
    public String getApiUrl() { return apiUrl.trim(); }
    public String getVersion() { return version.trim(); }
    public int getExpirationMinutes() { return expirationMinutes > 0 ? Math.min(expirationMinutes, 60) : 15; }
}
