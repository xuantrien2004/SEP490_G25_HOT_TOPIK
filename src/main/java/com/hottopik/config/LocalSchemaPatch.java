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
                        "ALTER TABLE dbo.writing_prompts ALTER COLUMN updated_at DATETIME2 NULL",
                        "IF COL_LENGTH('dbo.vocabularies', 'topic') IS NULL ALTER TABLE dbo.vocabularies ADD topic NVARCHAR(100) NULL",
                        "IF COL_LENGTH('dbo.vocabularies', 'example_kr') IS NULL ALTER TABLE dbo.vocabularies ADD example_kr NVARCHAR(500) NULL",
                        "IF COL_LENGTH('dbo.vocabularies', 'example_vi') IS NULL ALTER TABLE dbo.vocabularies ADD example_vi NVARCHAR(500) NULL",
                        "IF OBJECT_ID('dbo.FK_vocabularies_topic_id', 'F') IS NOT NULL ALTER TABLE dbo.vocabularies NOCHECK CONSTRAINT FK_vocabularies_topic_id",
                        "ALTER TABLE dbo.vocabularies ALTER COLUMN topic_id INT NULL",
                        "IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'vocab_topics') AND NOT EXISTS (SELECT 1 FROM dbo.vocab_topics WHERE id = 1) BEGIN SET IDENTITY_INSERT dbo.vocab_topics ON; INSERT INTO dbo.vocab_topics (id, level, name_vi, name_ko, icon_char, sort_order) VALUES (1, 1, N'Chung', N'일반', N'📚', 1); SET IDENTITY_INSERT dbo.vocab_topics OFF; END"
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
                        "ALTER TABLE dbo.questions ADD COLUMN IF NOT EXISTS template_code VARCHAR(20)",
                        "ALTER TABLE dbo.vocabularies ADD COLUMN IF NOT EXISTS topic VARCHAR(100)",
                        "ALTER TABLE dbo.vocabularies ADD COLUMN IF NOT EXISTS example_kr VARCHAR(500)",
                        "ALTER TABLE dbo.vocabularies ADD COLUMN IF NOT EXISTS example_vi VARCHAR(500)"
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
