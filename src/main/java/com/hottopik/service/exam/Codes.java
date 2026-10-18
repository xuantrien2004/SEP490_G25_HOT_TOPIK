package com.hottopik.service.exam;

import java.util.UUID;

final class Codes {

    private Codes() {
    }

    /**
     * Mã tạm duy nhất trước khi có id (SQL Server chỉ cho một NULL trong cột UNIQUE);
     * ngay sau khi lưu sẽ được thay bằng mã nghiệp vụ E0001 / W0001 / EX0001.
     */
    static String temporary() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
    }
}
