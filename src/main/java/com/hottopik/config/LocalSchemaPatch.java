package com.hottopik.config;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * File H2 cũ không có cột mới. Hibernate đọc entity trước khi kịp tự thêm cột, nên app chết
 * ngay lúc khởi động. Bản vá chạy trước lúc Hibernate mở schema.
 */
@Configuration
@Profile("local")
public class LocalSchemaPatch {

    @Bean
    HibernatePropertiesCustomizer patchLocalSchemaBeforeHibernate(DataSource dataSource) {
        return properties -> apply(dataSource);
    }

    static void apply(DataSource dataSource) {
        String[] statements = {
                "ALTER TABLE dbo.import_jobs ADD COLUMN IF NOT EXISTS draft_json CLOB",
                "ALTER TABLE dbo.question_types ADD COLUMN IF NOT EXISTS template_code VARCHAR(20)",
                "ALTER TABLE dbo.questions ADD COLUMN IF NOT EXISTS template_code VARCHAR(20)"
        };
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                try {
                    statement.execute(sql);
                } catch (SQLException ignored) {
                    // Bảng chưa có ở lần chạy đầu. Hibernate sẽ tạo đủ cột.
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không cập nhật được cơ sở dữ liệu local.", e);
        }
    }
}
