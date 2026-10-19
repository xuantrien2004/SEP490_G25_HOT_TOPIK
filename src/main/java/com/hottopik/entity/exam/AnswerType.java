package com.hottopik.entity.exam;

public enum AnswerType {
    CHOICE_INDEX("Chỉ số phương án đúng"),
    ORDER("Thứ tự các câu"),
    ACCEPT_KEYS("Danh sách đáp án chấp nhận"),
    FREE_TEXT("Bài viết tự do");

    private final String label;

    AnswerType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
