package com.hottopik.entity.exam;

public enum TemplateStatus {
    DRAFT("Nháp"),
    ACTIVE("Đang dùng"),
    INACTIVE("Ngừng dùng");

    private final String label;

    TemplateStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
