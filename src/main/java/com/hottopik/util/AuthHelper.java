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
        for (String r : allowedRoles) {
            if (user.getRole().equalsIgnoreCase(r)) {
                return true;
            }
        }
        return false;
    }
}
