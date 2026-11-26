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

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static String toVnp(LocalDateTime time) {
        return time.format(VNP);
    }

    public static LocalDateTime fromVnp(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value.trim(), VNP);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static String display(LocalDateTime time) {
        return time == null ? "—" : time.format(DISPLAY);
    }
}
