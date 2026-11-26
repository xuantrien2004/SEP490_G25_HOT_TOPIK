package com.hottopik.service.subscription;

import java.util.Map;

/**
 * Tách nghiệp vụ thanh toán khỏi nhà cung cấp. Bản triển khai duy nhất trong luồng chính: VNPaySandboxPaymentGateway.
 * (Unit test có thể mock interface này; luồng demo chính không dùng mock.)
 */
public interface PaymentGateway {

    String provider();

    boolean isConfigured();

    /** Ném IllegalStateException nếu chưa cấu hình hoặc URL không phải Sandbox. */
    void assertReady();

    String merchantCode();

    String createPaymentUrl(PaymentUrlRequest request);

    /** Kiểm tra chữ ký dữ liệu VNPay gửi về (Return URL / IPN). */
    boolean verifyCallback(Map<String, String> params);

    /** Đối soát server-to-server (QueryDR). Trả kết quả đã xác minh chữ ký, hoặc queryOk = false nếu không xác minh được. */
    GatewayResult queryTransaction(String txnRef, String transactionDate, String orderInfo, String ipAddr);

    record PaymentUrlRequest(String txnRef, long amountVnd, String orderInfo, String ipAddr,
                             String createDate, String expireDate) {}

    /**
     * Kết quả giao dịch phía nhà cung cấp.
     * responseCode: mã kết quả thanh toán (vnp_ResponseCode); transactionStatus: vnp_TransactionStatus.
     */
    record GatewayResult(boolean queryOk, String apiCode, String message, String txnRef, String tmnCode,
                         String responseCode, String transactionStatus, String transactionNo, String bankCode,
                         Long amountVnd, String payDate) {

        /** Thanh toán thành công khi cả mã phản hồi và trạng thái giao dịch đều là 00. */
        public boolean isSuccess() {
            return "00".equals(responseCode) && "00".equals(transactionStatus);
        }
    }
}
