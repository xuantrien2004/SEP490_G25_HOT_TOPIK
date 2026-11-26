package com.hottopik.entity.subscription;

/** Trạng thái gói học. Chỉ gói ACTIVE được bán cho học viên. */
public enum SubscriptionPlanStatus {
    DRAFT("Bản nháp"),
    PENDING_APPROVAL("Chờ phê duyệt lần đầu"),
    ACTIVE("Đang bán"),
    INACTIVE("Tạm ngừng bán"),
    ARCHIVED("Ngừng kinh doanh");

    private final String label;

    SubscriptionPlanStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
