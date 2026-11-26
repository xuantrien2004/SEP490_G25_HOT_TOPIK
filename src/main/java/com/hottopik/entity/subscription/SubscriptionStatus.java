package com.hottopik.entity.subscription;

/** Trạng thái Premium của học viên. Không có bản ghi ACTIVE hợp lệ = dùng quyền Free. */
public enum SubscriptionStatus {
    ACTIVE("Đang hoạt động"),
    EXPIRED("Đã hết hạn"),
    CANCELLED("Đã hủy"),
    SUSPENDED("Tạm khóa");

    private final String label;

    SubscriptionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
