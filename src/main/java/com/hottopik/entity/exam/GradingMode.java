package com.hottopik.entity.exam;

public enum GradingMode {
    AUTO("Chấm tự động"),
    MANUAL("Chấm thủ công"),
    AI_ASSISTED("AI hỗ trợ, người duyệt");

    private final String label;

    GradingMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
