package com.hottopik.entity.subscription;

/** Loại yêu cầu thay đổi gói học gửi Admin phê duyệt. */
public enum PlanRequestType {
    CREATE_PLAN("Tạo gói mới"),
    UPDATE_PRICE("Thay đổi giá"),
    UPDATE_DURATION("Thay đổi thời hạn"),
    UPDATE_BENEFITS("Thay đổi quyền lợi"),
    UPDATE_PLAN("Cập nhật thông tin gói"),
    ACTIVATE_PLAN("Kích hoạt bán"),
    DEACTIVATE_PLAN("Ngừng bán"),
    ARCHIVE_PLAN("Lưu trữ gói");

    private final String label;

    PlanRequestType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
