package com.hottopik.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Ràng buộc database cho module gói học & thanh toán Sandbox. Chạy SAU khi Hibernate tạo bảng (ddl-auto=update),
 * mọi câu lệnh đều idempotent (IF NOT EXISTS) và không xóa dữ liệu / bảng nào.
 * Script SQL tương đương để chạy tay: docs/vnpay-sandbox/subscription_constraints.sql
 */
@Slf4j
@Component
public class SubscriptionSchemaPatch {

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public SubscriptionSchemaPatch(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void apply() {
        boolean sqlServer = isSqlServer();
        // Bảng dbo.subscriptions có sẵn từ trước: bổ sung cột mới (cho phép NULL) nếu Hibernate chưa thêm được.
        String[][] columns = {
                {"plan_id", "BIGINT"}, {"plan_version_id", "BIGINT"}, {"version", "BIGINT"},
                {"created_at", sqlServer ? "DATETIME2" : "TIMESTAMP"}, {"updated_at", sqlServer ? "DATETIME2" : "TIMESTAMP"}
        };
        for (String[] c : columns) {
            run(sqlServer
                    ? "IF OBJECT_ID('dbo.subscriptions','U') IS NOT NULL AND COL_LENGTH('dbo.subscriptions','" + c[0] + "') IS NULL "
                      + "ALTER TABLE dbo.subscriptions ADD " + c[0] + " " + c[1] + " NULL"
                    : "ALTER TABLE dbo.subscriptions ADD COLUMN IF NOT EXISTS " + c[0] + " " + c[1]);
        }
        // Cột bổ sung sau cho payment_transactions (ghi nhận kết quả Return URL đã ký).
        for (String col : new String[]{"return_response_code", "return_transaction_status"}) {
            run(sqlServer
                    ? "IF OBJECT_ID('dbo.payment_transactions','U') IS NOT NULL AND COL_LENGTH('dbo.payment_transactions','" + col + "') IS NULL "
                      + "ALTER TABLE dbo.payment_transactions ADD " + col + " VARCHAR(10) NULL"
                    : "ALTER TABLE dbo.payment_transactions ADD COLUMN IF NOT EXISTS " + col + " VARCHAR(10)");
        }
        // Bản ghi Premium cũ chưa có giá trị version (optimistic locking) -> gán 0.
        run("UPDATE dbo.subscriptions SET version = 0 WHERE version IS NULL");
        if (!sqlServer) return;
        String[] statements = {
                // CHECK constraints
                check("subscription_plans", "CK_subscription_plans_price", "price >= 0"),
                check("subscription_plans", "CK_subscription_plans_duration", "duration_days IS NULL OR duration_days > 0"),
                check("subscription_plans", "CK_subscription_plans_premium", "tier <> 'PREMIUM' OR (price > 0 AND duration_days > 0)"),
                check("subscription_plan_versions", "CK_plan_versions_price", "price >= 0"),
                check("subscription_orders", "CK_subscription_orders_amount", "total_amount >= 0 AND plan_price_snapshot >= 0 AND duration_days_snapshot > 0"),
                check("subscription_orders", "CK_subscription_orders_env", "payment_environment = 'SANDBOX'"),
                check("payment_transactions", "CK_payment_transactions_env", "payment_environment = 'SANDBOX'"),
                check("payment_transactions", "CK_payment_transactions_amount", "expected_amount >= 0 AND (paid_amount IS NULL OR paid_amount >= 0)"),
                check("plan_price_history", "CK_plan_price_history_price", "new_price >= 0 AND (old_price IS NULL OR old_price >= 0)"),
                // Dữ liệu cũ có thể không thỏa -> WITH NOCHECK: chỉ áp dụng cho dữ liệu mới.
                "IF OBJECT_ID('dbo.subscriptions','U') IS NOT NULL AND OBJECT_ID('dbo.CK_subscriptions_dates','C') IS NULL "
                        + "ALTER TABLE dbo.subscriptions WITH NOCHECK ADD CONSTRAINT CK_subscriptions_dates CHECK (ends_at IS NULL OR ends_at > starts_at)",
                // Filtered unique indexes
                "IF OBJECT_ID('dbo.plan_change_requests','U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='UQ_plan_change_requests_pending') "
                        + "AND NOT EXISTS (SELECT plan_id FROM dbo.plan_change_requests WHERE status='PENDING' GROUP BY plan_id HAVING COUNT(*) > 1) "
                        + "CREATE UNIQUE INDEX UQ_plan_change_requests_pending ON dbo.plan_change_requests(plan_id) WHERE status = 'PENDING'",
                "IF OBJECT_ID('dbo.payment_transactions','U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='UQ_payment_transactions_txn_no') "
                        + "CREATE UNIQUE INDEX UQ_payment_transactions_txn_no ON dbo.payment_transactions(payment_provider, payment_environment, provider_transaction_no) "
                        + "WHERE provider_transaction_no IS NOT NULL",
                "IF OBJECT_ID('dbo.subscription_history','U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='UQ_subscription_history_order_grant') "
                        + "CREATE UNIQUE INDEX UQ_subscription_history_order_grant ON dbo.subscription_history(order_id) "
                        + "WHERE order_id IS NOT NULL AND action_type IN ('ACTIVATED','RENEWED')",
                "IF OBJECT_ID('dbo.subscription_orders','U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='UQ_subscription_orders_pending') "
                        + "AND NOT EXISTS (SELECT user_id FROM dbo.subscription_orders WHERE status='PENDING_PAYMENT' GROUP BY user_id, plan_id HAVING COUNT(*) > 1) "
                        + "CREATE UNIQUE INDEX UQ_subscription_orders_pending ON dbo.subscription_orders(user_id, plan_id) WHERE status = 'PENDING_PAYMENT'",
                "IF OBJECT_ID('dbo.subscriptions','U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='UQ_subscriptions_user_active') "
                        + "AND NOT EXISTS (SELECT user_id FROM dbo.subscriptions WHERE status='ACTIVE' GROUP BY user_id HAVING COUNT(*) > 1) "
                        + "CREATE UNIQUE INDEX UQ_subscriptions_user_active ON dbo.subscriptions(user_id) WHERE status = 'ACTIVE'",
                // Khóa ngoại tới users (không ON DELETE CASCADE để không mất lịch sử giao dịch)
                "IF OBJECT_ID('dbo.subscription_orders','U') IS NOT NULL AND OBJECT_ID('dbo.FK_subscription_orders_user','F') IS NULL "
                        + "ALTER TABLE dbo.subscription_orders WITH NOCHECK ADD CONSTRAINT FK_subscription_orders_user FOREIGN KEY (user_id) REFERENCES dbo.users(id)"
        };
        for (String sql : statements) run(sql);
    }

    private static String check(String table, String name, String condition) {
        return "IF OBJECT_ID('dbo." + table + "','U') IS NOT NULL AND OBJECT_ID('dbo." + name + "','C') IS NULL "
                + "ALTER TABLE dbo." + table + " ADD CONSTRAINT " + name + " CHECK (" + condition + ")";
    }

    private void run(String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception e) {
            log.debug("Bỏ qua ràng buộc gói học ({}): {}", sql.length() > 80 ? sql.substring(0, 80) : sql, e.getMessage());
        }
    }

    private boolean isSqlServer() {
        try (Connection c = dataSource.getConnection()) {
            return c.getMetaData().getDatabaseProductName().toLowerCase().contains("microsoft");
        } catch (Exception e) {
            return false;
        }
    }
}
