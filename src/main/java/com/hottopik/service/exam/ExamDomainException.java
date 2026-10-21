package com.hottopik.service.exam;

/** Vi phạm quy tắc nghiệp vụ; thông báo hiển thị thẳng cho người dùng. */
public class ExamDomainException extends RuntimeException {
    public ExamDomainException(String message) {
        super(message);
    }
}
