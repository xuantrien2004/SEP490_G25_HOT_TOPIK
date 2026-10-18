package com.hottopik.entity.exam;

public enum Skill {
    LISTENING("Nghe"),
    READING("Đọc"),
    WRITING("Viết");

    private final String label;

    Skill(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
