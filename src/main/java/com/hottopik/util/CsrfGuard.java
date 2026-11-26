package com.hottopik.util;

import jakarta.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Dự án không dùng Spring Security (phân quyền bằng RoleSecurityInterceptor), nên module gói học/thanh toán
 * tự chống CSRF bằng token gắn với session: form POST gửi kèm field "_csrf" (hoặc header X-CSRF-TOKEN).
 * Không ảnh hưởng các module khác.
 */
public final class CsrfGuard {

    public static final String SESSION_KEY = "BILLING_CSRF_TOKEN";
    public static final String PARAM = "_csrf";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CsrfGuard() {}

    /** Lấy (hoặc tạo mới) CSRF token gắn với session. */
    public static String token(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof String s) return s;
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        session.setAttribute(SESSION_KEY, token);
        return token;
    }

    /** So sánh token gửi lên với token trong session (so sánh thời gian hằng). */
    public static boolean isValid(HttpSession session, String provided) {
        if (session == null || provided == null) return false;
        Object expected = session.getAttribute(SESSION_KEY);
        return expected instanceof String s
                && MessageDigest.isEqual(s.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }

    /** Ném SecurityException nếu token không hợp lệ. */
    public static void require(HttpSession session, String provided) {
        if (!isValid(session, provided)) {
            throw new SecurityException("Phiên làm việc đã hết hạn hoặc yêu cầu không hợp lệ. Vui lòng tải lại trang và thử lại.");
        }
    }
}
