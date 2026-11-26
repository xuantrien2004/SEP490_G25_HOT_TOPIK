package com.hottopik.entity.subscription;

/** Trạng thái đơn hàng (khác với trạng thái từng lần thanh toán PaymentStatus). */
public enum OrderStatus {
    PENDING_PAYMENT("Chờ thanh toán"),
    PAID("Đã thanh toán"),
    FAILED("Thất bại"),
    CANCELLED("Đã hủy"),
    EXPIRED("Hết hạn"),
    REQUIRES_REVIEW("Cần đối soát");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
