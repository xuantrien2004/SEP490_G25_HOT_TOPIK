package com.hottopik.entity.exam;

public enum QuestionPurpose {
    EXAM("Thi"), PRACTICE("Luyện"), ASSESSMENT("Đánh giá đầu vào");

    private final String label;

    QuestionPurpose(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
