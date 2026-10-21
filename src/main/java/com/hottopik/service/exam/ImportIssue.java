package com.hottopik.service.exam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Một dòng trong bảng "Kiểm tra lỗi" của bước 3 nhập đề. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportIssue(Level level, String sheet, Integer row, String skill, Integer no, String message) {

    public enum Level { ERROR, WARNING }

    public static ImportIssue error(String sheet, Integer row, String skill, Integer no, String message) {
        return new ImportIssue(Level.ERROR, sheet, row, skill, no, message);
    }

    public static ImportIssue warning(String sheet, Integer row, String skill, Integer no, String message) {
        return new ImportIssue(Level.WARNING, sheet, row, skill, no, message);
    }

    public boolean isError() {
        return level == Level.ERROR;
    }

    public String questionLabel() {
        if (no == null) return "—";
        if (skill == null) return "Câu " + no;
        String s = switch (skill) {
            case "LISTENING" -> "Nghe";
            case "READING" -> "Đọc";
            case "WRITING" -> "Viết";
            default -> skill;
        };
        return s + " " + no;
    }
}
