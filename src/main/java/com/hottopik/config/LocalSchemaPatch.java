package com.hottopik.config;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Bản vá tự động tương thích CSDL trước khi Hibernate mở Schema:
 * Hỗ trợ SQL Server và H2, tự động nới lỏng các ràng buộc cột cũ (như question_type_id, zone_level...)
 * để module đề thi mới và Seeder hoạt động trơn tru.
 */
@Configuration
public class LocalSchemaPatch {

    @Bean
    HibernatePropertiesCustomizer patchLocalSchemaBeforeHibernate(DataSource dataSource) {
        return properties -> apply(dataSource);
    }

    static void apply(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            DatabaseMetaData meta = connection.getMetaData();
            String dbProduct = meta.getDatabaseProductName().toLowerCase();

            if (dbProduct.contains("microsoft") || dbProduct.contains("sql server")) {
                String[] sqlServerStatements = {
                        "ALTER TABLE dbo.questions ALTER COLUMN question_type_id INT NULL",
                        "ALTER TABLE dbo.questions ALTER COLUMN zone_level TINYINT NULL",
                        "ALTER TABLE dbo.questions ALTER COLUMN content_html NVARCHAR(MAX) NULL",
                        "ALTER TABLE dbo.questions ALTER COLUMN points TINYINT NULL",
                        "ALTER TABLE dbo.questions ALTER COLUMN times_answered INT NULL",
                        "ALTER TABLE dbo.questions ALTER COLUMN times_correct INT NULL",
                        "ALTER TABLE dbo.question_groups ALTER COLUMN created_at DATETIME2 NULL",
                        "ALTER TABLE dbo.question_groups ALTER COLUMN updated_at DATETIME2 NULL",
                        "ALTER TABLE dbo.writing_prompts ALTER COLUMN title NVARCHAR(200) NULL",
                        "ALTER TABLE dbo.writing_prompts ALTER COLUMN prompt_html NVARCHAR(MAX) NULL",
                        "ALTER TABLE dbo.writing_prompts ALTER COLUMN standard_answer NVARCHAR(MAX) NULL",
                        "ALTER TABLE dbo.writing_prompts ALTER COLUMN max_score TINYINT NULL",
                        "ALTER TABLE dbo.writing_prompts ALTER COLUMN purpose NVARCHAR(10) NULL",
                        "ALTER TABLE dbo.writing_prompts ALTER COLUMN updated_at DATETIME2 NULL"
                };
                for (String sql : sqlServerStatements) {
                    try {
                        statement.execute(sql);
                    } catch (SQLException ignored) {
                        // Bỏ qua nếu bảng chưa tồn tại hoặc cột đã được sửa
                    }
                }
            } else {
                String[] h2Statements = {
                        "ALTER TABLE dbo.import_jobs ADD COLUMN IF NOT EXISTS draft_json CLOB",
                        "ALTER TABLE dbo.question_types ADD COLUMN IF NOT EXISTS template_code VARCHAR(20)",
                        "ALTER TABLE dbo.questions ADD COLUMN IF NOT EXISTS template_code VARCHAR(20)"
                };
                for (String sql : h2Statements) {
                    try {
                        statement.execute(sql);
                    } catch (SQLException ignored) {}
                }
            }
        } catch (SQLException ignored) {
        }
    }
}
