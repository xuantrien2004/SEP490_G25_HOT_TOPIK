package com.hottopik.util;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;

/** Mã đơn hàng / mã tham chiếu VNPay chỉ gồm chữ và số (vnp_TxnRef không nên có ký tự đặc biệt). */
public final class OrderCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private OrderCodeGenerator() {}

    /** Ví dụ: HT2611251430228391. Cột order_code có unique constraint, service thử lại nếu trùng. */
    public static String newOrderCode() {
        return "HT" + PaymentDateTimeUtil.now().format(TIME) + String.format("%04d", RANDOM.nextInt(10_000));
    }

    /** Mỗi lần thử thanh toán có một vnp_TxnRef riêng: mã đơn + "P" + số lần thử. */
    public static String txnRef(String orderCode, int attempt) {
        return orderCode + "P" + attempt;
    }

    /** Mã yêu cầu duy nhất cho API QueryDR (vnp_RequestId). */
    public static String requestId() {
        return PaymentDateTimeUtil.now().format(TIME) + String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
