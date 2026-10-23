package com.hottopik.service.exam;

import com.hottopik.dto.UserDTO;

/** Người thực hiện thao tác, dùng cho nhật ký và quy tắc bốn mắt. */
public record Actor(String name, String role, Long userId) {

    public Actor(String name, String role) {
        this(name, role, null);
    }

    public static final String ROLE_CONTENT = "ROLE_CONTENT_MANAGER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    public static Actor of(UserDTO u) {
        return new Actor(u.getFullName(), u.getRole(), u.getId());
    }

    public boolean isContentManager() {
        return ROLE_CONTENT.equalsIgnoreCase(role);
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equalsIgnoreCase(role);
    }
}
