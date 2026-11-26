package com.hottopik.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Thời gian của module gói học/thanh toán thống nhất theo Asia/Ho_Chi_Minh (VNPay dùng GMT+7). */
public final class PaymentDateTimeUtil {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private PaymentDateTimeUtil() {}

    /** Thời điểm hiện tại theo giờ Việt Nam. */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    /** Định dạng yyyyMMddHHmmss mà VNPay yêu cầu. */
    public static String toVnp(LocalDateTime time) {
        return time.format(VNP);
    }

    /** Đọc ngày giờ dạng yyyyMMddHHmmss từ VNPay; trả null nếu sai định dạng. */
    public static LocalDateTime fromVnp(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value.trim(), VNP);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Định dạng dd/MM/yyyy HH:mm để hiển thị. */
    public static String display(LocalDateTime time) {
        return time == null ? "—" : time.format(DISPLAY);
    }
}
