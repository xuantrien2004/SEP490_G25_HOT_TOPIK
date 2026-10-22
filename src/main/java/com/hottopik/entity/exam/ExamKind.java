package com.hottopik.entity.exam;

public enum ExamKind {
    FULL("Full Mock"), LISTENING("Mini Nghe"), READING("Mini Đọc"), WRITING("Mini Viết");

    private final String label;

    ExamKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
