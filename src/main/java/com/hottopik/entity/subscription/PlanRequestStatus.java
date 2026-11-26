package com.hottopik.entity.subscription;

/** Trạng thái yêu cầu phê duyệt. */
public enum PlanRequestStatus {
    PENDING("Chờ duyệt"),
    APPROVED("Đã duyệt"),
    REJECTED("Bị từ chối"),
    CANCELLED("Đã hủy");

    private final String label;

    PlanRequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
