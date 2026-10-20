package com.hottopik.entity.exam;

public enum ProgramStatus {
    DRAFT("Nháp"),
    PENDING_APPROVAL("Chờ duyệt"),
    PUBLISHED("Đã phát hành"),
    ARCHIVED("Lưu trữ");

    private final String label;

    ProgramStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
