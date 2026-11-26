package com.hottopik.entity.subscription;

/** Trạng thái một lần thanh toán VNPay Sandbox. */
public enum PaymentStatus {
    PENDING("Đang chờ"),
    SUCCESS("Thành công"),
    FAILED("Thất bại"),
    CANCELLED("Đã hủy"),
    EXPIRED("Hết hạn"),
    REQUIRES_REVIEW("Cần đối soát");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
