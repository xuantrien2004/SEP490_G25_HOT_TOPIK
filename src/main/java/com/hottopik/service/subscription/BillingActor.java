package com.hottopik.service.subscription;

import com.hottopik.dto.UserDTO;

/** Người thực hiện thao tác (lấy từ session phía server, không tin dữ liệu frontend gửi lên). */
public record BillingActor(Long id, String name, String role) {

    public static final BillingActor VNPAY_IPN = new BillingActor(null, "VNPay Sandbox (IPN)", "SYSTEM");
    public static final BillingActor SYSTEM = new BillingActor(null, "Hệ thống", "SYSTEM");

    /** Tạo người thực hiện từ tài khoản đang đăng nhập. */
    public static BillingActor of(UserDTO user) {
        String name = user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getEmail();
        return new BillingActor(user.getId(), name, user.getRole());
    }
}
