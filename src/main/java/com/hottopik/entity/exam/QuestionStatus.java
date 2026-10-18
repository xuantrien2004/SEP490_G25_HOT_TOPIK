package com.hottopik.entity.exam;

public enum QuestionStatus {
    ACTIVE("Đang dùng"), HIDDEN("Đã ẩn"), DRAFT("Nháp");

    private final String label;

    QuestionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
