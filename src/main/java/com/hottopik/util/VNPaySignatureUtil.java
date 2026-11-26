package com.hottopik.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * Chữ ký VNPay API 2.1.0: sắp xếp tham số theo tên (tăng dần), bỏ giá trị rỗng,
 * hashData = key=URLEncode(value, US-ASCII) nối bằng '&', ký HMAC-SHA512 bằng Hash Secret (hex).
 * Đối chiếu lại với tài liệu https://sandbox.vnpaytest.vn/apis/ trước khi chạy thật.
 */
public final class VNPaySignatureUtil {

    public static final String SECURE_HASH = "vnp_SecureHash";
    public static final String SECURE_HASH_TYPE = "vnp_SecureHashType";

    private VNPaySignatureUtil() {}

    /** Tính HMAC-SHA512 (hex chữ thường). */
    public static String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được chữ ký HMAC-SHA512.", e);
        }
    }

    /** Chuỗi dữ liệu dùng để ký: chỉ gồm tham số vnp_*, bỏ vnp_SecureHash / vnp_SecureHashType và giá trị rỗng. */
    public static String buildHashData(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> e : sorted(params).entrySet()) {
            joiner.add(e.getKey() + "=" + encode(e.getValue()));
        }
        return joiner.toString();
    }

    /** Query string gửi sang VNPay (key và value đều được URL encode). */
    public static String buildQuery(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> e : sorted(params).entrySet()) {
            joiner.add(encode(e.getKey()) + "=" + encode(e.getValue()));
        }
        return joiner.toString();
    }

    /** Ký bộ tham số vnp_* bằng Hash Secret. */
    public static String sign(Map<String, String> params, String secret) {
        return hmacSHA512(secret, buildHashData(params));
    }

    /** Kiểm tra chữ ký của dữ liệu VNPay gửi về (Return URL / IPN). So sánh thời gian hằng để tránh timing attack. */
    public static boolean verify(Map<String, String> params, String secret) {
        if (params == null || secret == null || secret.isBlank()) return false;
        String received = params.get(SECURE_HASH);
        if (received == null || received.isBlank()) return false;
        String expected = sign(params, secret);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                received.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private static TreeMap<String, String> sorted(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>();
        params.forEach((k, v) -> {
            if (k != null && k.startsWith("vnp_") && !SECURE_HASH.equals(k) && !SECURE_HASH_TYPE.equals(k)
                    && v != null && !v.isEmpty()) {
                sorted.put(k, v);
            }
        });
        return sorted;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.US_ASCII);
    }

    /** SHA-256 của payload để lưu log đối soát mà không lưu toàn bộ dữ liệu. */
    public static String sha256(String data) {
        try {
            byte[] raw = MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
