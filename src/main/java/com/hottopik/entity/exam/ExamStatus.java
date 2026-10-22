package com.hottopik.entity.exam;

public enum ExamStatus {
    DRAFT("Nháp"), PENDING_APPROVAL("Chờ duyệt"), PUBLISHED("Đã phát hành"), ARCHIVED("Lưu trữ");

    private final String label;

    ExamStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
