package com.hottopik.entity.subscription;

/** Loại sự kiện trong lịch sử Premium. */
public enum SubscriptionAction {
    ACTIVATED("Kích hoạt"),
    RENEWED("Gia hạn"),
    EXPIRED("Hết hạn"),
    CANCELLED("Hủy"),
    SUSPENDED("Tạm khóa");

    private final String label;

    SubscriptionAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
