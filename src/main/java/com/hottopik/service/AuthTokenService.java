package com.hottopik.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
public class AuthTokenService {

    /**
     * Sinh chuỗi token ngẫu nhiên an toàn (64 ký tự hex) gửi cho người dùng qua URL
     */
    public String generateRawToken() {
        return (UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")).toLowerCase();
    }

    /**
     * Băm token bằng thuật toán SHA-256 trước khi lưu vào dbo.auth_tokens.token_hash
     */
    public String hashToken(String rawToken) {
        if (rawToken == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Lỗi tính toán băm SHA-256", e);
        }
    }
}
