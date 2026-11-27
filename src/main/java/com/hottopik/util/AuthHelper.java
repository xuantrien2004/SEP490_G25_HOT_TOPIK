package com.hottopik.util;

import com.hottopik.dto.UserDTO;

import jakarta.servlet.http.HttpSession;

public class AuthHelper {

    public static final String SESSION_KEY = "LOGGED_IN_USER";

    /**
     * Lấy thông tin người dùng đang đăng nhập từ HttpSession.
     */
    public static UserDTO getLoggedInUser(HttpSession session) {
        if (session == null) return null; // Chưa có session thì trả về null
        Object obj = session.getAttribute(SESSION_KEY); // Lấy attribute LOGGED_IN_USER từ session
        if (obj instanceof UserDTO) {
            return (UserDTO) obj; // Ép kiểu về UserDTO nếu đúng loại
        }
        return null;
    }

    /**
     * Kiểm tra người dùng trong session có thuộc một trong các vai trò được phép hay không.
     */
    public static boolean hasRole(HttpSession session, String... allowedRoles) {
        UserDTO user = getLoggedInUser(session); // Lấy user hiện tại
        if (user == null || user.getRole() == null) {
            return false; // Chưa đăng nhập hoặc chưa có role
        }
        String uRole = normalizeRole(user.getRole()); // Chuẩn hóa role của user
        for (String r : allowedRoles) {
            if (uRole.equalsIgnoreCase(normalizeRole(r))) {
                return true; // Khớp với ít nhất 1 role được phép
            }
        }
        return false;
    }

    /**
     * Chuẩn hóa tên vai trò (bỏ tiền tố ROLE_, quy đổi TEACHER -> MENTOR và USER/VIP -> STUDENT).
     */
    private static String normalizeRole(String role) {
        if (role == null) return "";
        String r = role.trim().toUpperCase(); // Xóa khoảng trắng và viết hoa
        if (r.startsWith("ROLE_")) {
            r = r.substring(5); // Cắt bỏ tiền tố "ROLE_"
        }
        if ("TEACHER".equals(r)) return "MENTOR"; // Quy đổi TEACHER về MENTOR
        if ("USER".equals(r) || "VIP".equals(r)) return "STUDENT"; // Quy đổi USER/VIP về STUDENT
        return r;
    }
}
