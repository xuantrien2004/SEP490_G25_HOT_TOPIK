package com.hottopik.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * SQL Server có CK_vocabularies_status chỉ nhận ACTIVE/HIDDEN/DRAFT, trong khi app dùng INACTIVE
 * cho trạng thái "ẩn". Ghi INACTIVE xuống DB vi phạm ràng buộc và làm transaction bị rollback-only.
 * Converter này đổi INACTIVE thành HIDDEN khi ghi, và HIDDEN thành INACTIVE khi đọc.
 */
@Converter
public class VocabularyStatusConverter implements AttributeConverter<String, String> {

    private static final String APP_HIDDEN = "INACTIVE";
    private static final String DB_HIDDEN = "HIDDEN";

    @Override
    public String convertToDatabaseColumn(String status) {
        return APP_HIDDEN.equalsIgnoreCase(status) ? DB_HIDDEN : status;
    }

    @Override
    public String convertToEntityAttribute(String dbStatus) {
        return DB_HIDDEN.equalsIgnoreCase(dbStatus) ? APP_HIDDEN : dbStatus;
    }
}
