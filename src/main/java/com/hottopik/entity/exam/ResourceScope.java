package com.hottopik.entity.exam;

/** Tài nguyên nằm ở nhóm dùng chung, ở từng câu, ở từng lựa chọn, hoặc không dùng. */
public enum ResourceScope {
    NONE("Không dùng"),
    GROUP("Dùng chung cho cả nhóm"),
    QUESTION("Riêng từng câu"),
    OPTIONS("Gắn từng lựa chọn");

    private final String label;

    ResourceScope(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
