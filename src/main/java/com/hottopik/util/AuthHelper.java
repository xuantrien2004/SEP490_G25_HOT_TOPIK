package com.hottopik.util;

import com.hottopik.dto.UserDTO;

import jakarta.servlet.http.HttpSession;

public class AuthHelper {

    public static final String SESSION_KEY = "LOGGED_IN_USER";

    public static UserDTO getLoggedInUser(HttpSession session) {
        if (session == null) return null;
        Object obj = session.getAttribute(SESSION_KEY);
        if (obj instanceof UserDTO) {
            return (UserDTO) obj;
        }
        return null;
    }

    public static boolean hasRole(HttpSession session, String... allowedRoles) {
        UserDTO user = getLoggedInUser(session);
        if (user == null || user.getRole() == null) {
            return false;
        }
        String uRole = normalizeRole(user.getRole());
        for (String r : allowedRoles) {
            if (uRole.equalsIgnoreCase(normalizeRole(r))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeRole(String role) {
        if (role == null) return "";
        String r = role.trim().toUpperCase();
        if (r.startsWith("ROLE_")) {
            r = r.substring(5);
        }
        if ("TEACHER".equals(r)) return "MENTOR";
        if ("USER".equals(r) || "VIP".equals(r)) return "STUDENT";
        return r;
    }
}
