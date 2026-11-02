package com.hottopik.config;

import com.hottopik.entity.QuestionReport;
import com.hottopik.entity.UserNotification;
import jakarta.persistence.Column;
import org.hibernate.annotations.Nationalized;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.sql.DataSource;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportUnicodeSchemaPatchTest {
    private final DataSource source = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final DatabaseMetaData metadata = mock(DatabaseMetaData.class);
    private final Statement statement = mock(Statement.class);

    private void database(int textType) throws SQLException {
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("Microsoft SQL Server");
        when(metadata.getColumns(null, "dbo", "question_reports", null)).thenReturn(columns(QuestionReport.class, textType));
        when(metadata.getColumns(null, "dbo", "user_notifications", null)).thenReturn(columns(UserNotification.class, textType));
    }

    private CachedRowSet columns(Class<?> entity, int textType) throws SQLException {
        var rows = RowSetProvider.newFactory().createCachedRowSet();
        var meta = new RowSetMetaDataImpl();
        meta.setColumnCount(4);
        String[] names = {"COLUMN_NAME", "DATA_TYPE", "COLUMN_SIZE", "NULLABLE"};
        for (int i = 1; i <= names.length; i++) {
            meta.setColumnName(i, names[i - 1]);
            meta.setColumnType(i, i == 1 ? Types.VARCHAR : Types.INTEGER);
        }
        rows.setMetaData(meta);
        for (Field field : entity.getDeclaredFields()) {
            if (field.getType() != String.class) continue;
            Column column = field.getAnnotation(Column.class);
            rows.moveToInsertRow();
            rows.updateString(1, columnName(field));
            rows.updateInt(2, field.isAnnotationPresent(Nationalized.class) ? textType : Types.VARCHAR);
            rows.updateInt(3, capacity(field));
            rows.updateInt(4, column.nullable() ? DatabaseMetaData.columnNullable : DatabaseMetaData.columnNoNulls);
            rows.insertRow();
            rows.moveToCurrentRow();
        }
        rows.beforeFirst();
        return rows;
    }

    private String columnName(Field field) {
        String name = field.getAnnotation(Column.class).name();
        return name.isEmpty() ? field.getName() : name;
    }

    private int capacity(Field field) {
        // Older databases may have wider columns, including VARCHAR(MAX).
        if (field.getName().equals("details")) return 4000;
        if (field.getName().equals("questionSummary")) return Integer.MAX_VALUE;
        return field.getAnnotation(Column.class).length();
    }

    private List<String> statements() throws SQLException {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(statement, atLeastOnce()).execute(sql.capture());
        return sql.getAllValues();
    }

    @Test
    void upgradesEveryNationalizedLegacyColumnWithoutShrinkingOrChangingNullability() throws Exception {
        database(Types.VARCHAR);
        LocalSchemaPatch.apply(source);
        List<String> executed = statements();
        int expected = 0;
        for (Class<?> entity : List.of(QuestionReport.class, UserNotification.class)) {
            String table = entity == QuestionReport.class ? "question_reports" : "user_notifications";
            for (Field field : entity.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Nationalized.class)) continue;
                expected++;
                String length = capacity(field) > 4000 ? "MAX" : Integer.toString(capacity(field));
                String nullable = field.getAnnotation(Column.class).nullable() ? "NULL" : "NOT NULL";
                assertTrue(executed.contains("ALTER TABLE dbo." + table + " ALTER COLUMN " + columnName(field)
                        + " NVARCHAR(" + length + ") " + nullable), table + "." + columnName(field));
            }
        }
        assertEquals(expected, executed.stream().filter(s -> s.startsWith("ALTER TABLE dbo.question_reports ALTER COLUMN")
                || s.startsWith("ALTER TABLE dbo.user_notifications ALTER COLUMN")).count());
        assertFalse(QuestionReport.class.getDeclaredField("status").isAnnotationPresent(Nationalized.class));
        assertFalse(QuestionReport.class.getDeclaredField("questionKind").isAnnotationPresent(Nationalized.class));
        assertFalse(UserNotification.class.getDeclaredField("type").isAnnotationPresent(Nationalized.class));
        assertFalse(UserNotification.class.getDeclaredField("link").isAnnotationPresent(Nationalized.class));
    }

    @Test
    void alreadyUnicodeColumnsAreNotAlteredAgain() throws Exception {
        database(Types.NVARCHAR);
        LocalSchemaPatch.apply(source);
        assertTrue(statements().stream().noneMatch(s -> s.startsWith("ALTER TABLE dbo.question_reports ALTER COLUMN")
                || s.startsWith("ALTER TABLE dbo.user_notifications ALTER COLUMN")));
    }

    @Test
    void failedUnicodeUpgradeStopsStartupInsteadOfLeavingAnUnreadableSchema() throws Exception {
        database(Types.VARCHAR);
        when(statement.execute(startsWith("ALTER TABLE dbo.question_reports ALTER COLUMN details ")))
                .thenThrow(new SQLException("ALTER permission denied"));
        var error = assertThrows(IllegalStateException.class, () -> LocalSchemaPatch.apply(source));
        assertTrue(error.getMessage().contains("dbo.question_reports"));
        assertInstanceOf(SQLException.class, error.getCause());
    }
}
