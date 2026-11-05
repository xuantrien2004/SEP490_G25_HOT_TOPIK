package com.hottopik.config;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;

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
                        "IF OBJECT_ID('dbo.question_reports') IS NOT NULL AND COL_LENGTH('dbo.question_reports', 'question_kind') IS NULL ALTER TABLE dbo.question_reports ADD question_kind VARCHAR(20) NULL",
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
                        "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'question_reports') CREATE TABLE dbo.question_reports (id BIGINT IDENTITY(1,1) PRIMARY KEY, user_id BIGINT NOT NULL, reporter_name NVARCHAR(100) NOT NULL, reporter_email NVARCHAR(255) NOT NULL, question_id BIGINT NULL, question_kind VARCHAR(20) NULL, question_code NVARCHAR(50) NULL, question_summary NVARCHAR(1000) NULL, exam_id BIGINT NULL, exam_title NVARCHAR(255) NULL, question_no INT NULL, issue_type NVARCHAR(100) NOT NULL, details NVARCHAR(2000) NOT NULL, status NVARCHAR(20) NOT NULL DEFAULT 'OPEN', resolution_note NVARCHAR(2000) NULL, rejection_reason NVARCHAR(2000) NULL, reward_coins INT NOT NULL DEFAULT 5, resolved_by NVARCHAR(100) NULL, created_at DATETIME2 NOT NULL DEFAULT GETDATE(), resolved_at DATETIME2 NULL)",
                        "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'user_notifications') CREATE TABLE dbo.user_notifications (id BIGINT IDENTITY(1,1) PRIMARY KEY, user_id BIGINT NOT NULL, title NVARCHAR(200) NOT NULL, message NVARCHAR(1000) NOT NULL, type NVARCHAR(50) NULL, link NVARCHAR(500) NULL, is_read BIT NOT NULL DEFAULT 0, created_at DATETIME2 NOT NULL DEFAULT GETDATE())",
                        "IF COL_LENGTH('dbo.vocabularies', 'topic') IS NULL ALTER TABLE dbo.vocabularies ADD topic NVARCHAR(100) NULL",
                        "IF COL_LENGTH('dbo.vocabularies', 'example_kr') IS NULL ALTER TABLE dbo.vocabularies ADD example_kr NVARCHAR(500) NULL",
                        "IF COL_LENGTH('dbo.vocabularies', 'example_vi') IS NULL ALTER TABLE dbo.vocabularies ADD example_vi NVARCHAR(500) NULL",
                        "IF OBJECT_ID('dbo.FK_vocabularies_topic_id', 'F') IS NOT NULL ALTER TABLE dbo.vocabularies NOCHECK CONSTRAINT FK_vocabularies_topic_id",
                        "ALTER TABLE dbo.vocabularies ALTER COLUMN topic_id INT NULL",
                        "IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'vocab_topics') AND NOT EXISTS (SELECT 1 FROM dbo.vocab_topics WHERE id = 1) BEGIN SET IDENTITY_INSERT dbo.vocab_topics ON; INSERT INTO dbo.vocab_topics (id, level, name_vi, name_ko, icon_char, sort_order) VALUES (1, 1, N'Chung', N'일반', N'📚', 1); SET IDENTITY_INSERT dbo.vocab_topics OFF; END",
                        "IF OBJECT_ID('dbo.topics', 'U') IS NULL BEGIN CREATE TABLE dbo.topics (id BIGINT IDENTITY(1,1) PRIMARY KEY, level INT NOT NULL DEFAULT 1, name NVARCHAR(100) NOT NULL, description NVARCHAR(500) NULL, status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE', created_at DATETIME2 NOT NULL DEFAULT GETDATE(), updated_at DATETIME2 NOT NULL DEFAULT GETDATE()); END"
                };
                for (String sql : sqlServerStatements) {
                    try {
                        statement.execute(sql);
                    } catch (SQLException ignored) {
                        // Bỏ qua nếu bảng chưa tồn tại hoặc cột đã được sửa
                    }
                }
                upgradeReportUnicodeColumns(meta, statement);
            } else {
                String[] h2Statements = {
                        "ALTER TABLE dbo.import_jobs ADD COLUMN IF NOT EXISTS draft_json CLOB",
                        "ALTER TABLE dbo.question_types ADD COLUMN IF NOT EXISTS template_code VARCHAR(20)",
                        "ALTER TABLE dbo.questions ADD COLUMN IF NOT EXISTS template_code VARCHAR(20)",
                        "ALTER TABLE dbo.vocabularies ADD COLUMN IF NOT EXISTS topic VARCHAR(100)",
                        "ALTER TABLE dbo.vocabularies ADD COLUMN IF NOT EXISTS example_kr VARCHAR(500)",
                        "ALTER TABLE dbo.vocabularies ADD COLUMN IF NOT EXISTS example_vi VARCHAR(500)",
                        "CREATE TABLE IF NOT EXISTS dbo.topics (id BIGINT AUTO_INCREMENT PRIMARY KEY, level INT NOT NULL DEFAULT 1, name VARCHAR(100) NOT NULL, description VARCHAR(500), status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
                        "CREATE TABLE IF NOT EXISTS dbo.placement_lines (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, paper_code VARCHAR(20) NOT NULL, type_code VARCHAR(10) NOT NULL, question_count INT NOT NULL, sort_order INT NOT NULL)",
                        "CREATE TABLE IF NOT EXISTS dbo.placement_attempts (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, user_id BIGINT, user_email VARCHAR(200), paper_code VARCHAR(20) NOT NULL, question_ids VARCHAR(2000) NOT NULL, total INT NOT NULL, answers_json VARCHAR(4000) NOT NULL, correct_count INT NOT NULL DEFAULT 0, listen_correct INT NOT NULL DEFAULT 0, listen_total INT NOT NULL DEFAULT 0, read_correct INT NOT NULL DEFAULT 0, read_total INT NOT NULL DEFAULT 0, points INT NOT NULL DEFAULT 0, completed_at TIMESTAMP, started_at TIMESTAMP NOT NULL)"
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

    private static void upgradeReportUnicodeColumns(DatabaseMetaData meta, Statement statement) {
        // Hibernate does not reliably change existing VARCHAR columns when @Nationalized is added.
        // The SQL Server driver rejects getNString() against those legacy columns.
        Map<String, Set<String>> columns = Map.of(
                "question_reports", Set.of("reporter_name", "reporter_email", "question_code", "question_summary",
                        "exam_title", "issue_type", "details", "resolution_note", "rejection_reason", "resolved_by"),
                "user_notifications", Set.of("title", "message"));
        for (var table : columns.entrySet()) {
            try {
                List<String> upgrades = new ArrayList<>();
                try (ResultSet fields = meta.getColumns(null, "dbo", table.getKey(), null)) {
                    while (fields.next()) {
                        String name = fields.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                        int type = fields.getInt("DATA_TYPE");
                        if (!table.getValue().contains(name)
                                || (type != Types.VARCHAR && type != Types.LONGVARCHAR && type != Types.CHAR)) continue;
                        int length = fields.getInt("COLUMN_SIZE");
                        String size = length <= 0 || length > 4000 ? "MAX" : Integer.toString(length);
                        String nullable = fields.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls ? "NOT NULL" : "NULL";
                        upgrades.add("ALTER TABLE dbo." + table.getKey() + " ALTER COLUMN " + name
                                + " NVARCHAR(" + size + ") " + nullable);
                    }
                }
                for (String sql : upgrades) statement.execute(sql);
            } catch (SQLException e) {
                throw new IllegalStateException("Không thể nâng cấp cột Unicode của dbo." + table.getKey(), e);
            }
        }
    }
}
