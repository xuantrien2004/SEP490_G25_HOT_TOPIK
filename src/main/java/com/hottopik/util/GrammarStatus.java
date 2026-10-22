
package com.hottopik.util;

public enum GrammarStatus {

    DRAFT("Bản nháp"),
    ACTIVE("Đang hoạt động"),
    HIDDEN("Đã ẩn");

    private final String displayName;

    GrammarStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    // Lấy tên hiển thị theo mã trạng thái
    public static String getDisplayName(String status) {
        if (status == null || status.isBlank()) {
            return "Không xác định";
        }

        try {
            return GrammarStatus.valueOf(
                    status.trim().toUpperCase()
            ).getDisplayName();
        } catch (IllegalArgumentException e) {
            return "Không xác định";
        }
    }

    // Kiểm tra trạng thái có hợp lệ không
    public static boolean isValid(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }

        try {
            GrammarStatus.valueOf(
                    status.trim().toUpperCase()
            );
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
