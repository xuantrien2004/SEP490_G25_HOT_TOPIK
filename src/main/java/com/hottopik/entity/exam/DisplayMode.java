package com.hottopik.entity.exam;

public enum DisplayMode {
    SINGLE_CHOICE("Chọn một đáp án"),
    IMAGE_CHOICE("Chọn tranh hoặc biểu đồ"),
    ORDERING("Chọn thứ tự đúng"),
    FILL_BLANK("Điền vào chỗ trống"),
    INSERT("Chọn vị trí chèn câu"),
    TEXT_INPUT("Viết câu trả lời");

    private final String label;

    DisplayMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
