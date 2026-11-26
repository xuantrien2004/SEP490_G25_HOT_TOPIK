package com.hottopik.util;

/** Tiền VND lưu dạng Long (không dùng float/double). VNPay yêu cầu vnp_Amount = số tiền VND x 100. */
public final class PaymentAmountUtil {

    private PaymentAmountUtil() {}

    public static String toVnpAmount(long vnd) {
        if (vnd <= 0) throw new IllegalArgumentException("Số tiền thanh toán phải lớn hơn 0.");
        return String.valueOf(Math.multiplyExact(vnd, 100L));
    }

    /** Đổi vnp_Amount về VND; trả null nếu dữ liệu không hợp lệ (không phải số, âm, hoặc không chia hết cho 100). */
    public static Long fromVnpAmount(String vnpAmount) {
        if (vnpAmount == null || vnpAmount.isBlank()) return null;
        try {
            long raw = Long.parseLong(vnpAmount.trim());
            if (raw < 0 || raw % 100 != 0) return null;
            return raw / 100;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String format(Long vnd) {
        if (vnd == null) return "—";
        return String.format("%,d", vnd).replace(',', '.') + "đ";
    }
}
