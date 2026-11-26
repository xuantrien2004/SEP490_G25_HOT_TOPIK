-- Ràng buộc cho module Gói học & Thanh toán VNPay Sandbox (SQL Server).
-- Ứng dụng tự chạy các lệnh này khi khởi động (config/SubscriptionSchemaPatch.java); file này để chạy tay / nộp báo cáo.
-- Tất cả đều idempotent, KHÔNG xóa bảng / dữ liệu. Bảng được Hibernate tạo (ddl-auto=update) trước khi chạy script.

-- Bảng dbo.subscriptions có sẵn: thêm cột mới (cho phép NULL)
IF COL_LENGTH('dbo.subscriptions','plan_id') IS NULL ALTER TABLE dbo.subscriptions ADD plan_id BIGINT NULL;
IF COL_LENGTH('dbo.subscriptions','plan_version_id') IS NULL ALTER TABLE dbo.subscriptions ADD plan_version_id BIGINT NULL;
IF COL_LENGTH('dbo.subscriptions','version') IS NULL ALTER TABLE dbo.subscriptions ADD version BIGINT NULL;
IF COL_LENGTH('dbo.subscriptions','created_at') IS NULL ALTER TABLE dbo.subscriptions ADD created_at DATETIME2 NULL;
IF COL_LENGTH('dbo.subscriptions','updated_at') IS NULL ALTER TABLE dbo.subscriptions ADD updated_at DATETIME2 NULL;
GO
UPDATE dbo.subscriptions SET version = 0 WHERE version IS NULL;
GO

-- CHECK constraints
IF OBJECT_ID('dbo.CK_subscription_plans_price','C') IS NULL ALTER TABLE dbo.subscription_plans ADD CONSTRAINT CK_subscription_plans_price CHECK (price >= 0);
IF OBJECT_ID('dbo.CK_subscription_plans_duration','C') IS NULL ALTER TABLE dbo.subscription_plans ADD CONSTRAINT CK_subscription_plans_duration CHECK (duration_days IS NULL OR duration_days > 0);
IF OBJECT_ID('dbo.CK_subscription_plans_premium','C') IS NULL ALTER TABLE dbo.subscription_plans ADD CONSTRAINT CK_subscription_plans_premium CHECK (tier <> 'PREMIUM' OR (price > 0 AND duration_days > 0));
IF OBJECT_ID('dbo.CK_plan_versions_price','C') IS NULL ALTER TABLE dbo.subscription_plan_versions ADD CONSTRAINT CK_plan_versions_price CHECK (price >= 0);
IF OBJECT_ID('dbo.CK_subscription_orders_amount','C') IS NULL ALTER TABLE dbo.subscription_orders ADD CONSTRAINT CK_subscription_orders_amount CHECK (total_amount >= 0 AND plan_price_snapshot >= 0 AND duration_days_snapshot > 0);
IF OBJECT_ID('dbo.CK_subscription_orders_env','C') IS NULL ALTER TABLE dbo.subscription_orders ADD CONSTRAINT CK_subscription_orders_env CHECK (payment_environment = 'SANDBOX');
IF OBJECT_ID('dbo.CK_payment_transactions_env','C') IS NULL ALTER TABLE dbo.payment_transactions ADD CONSTRAINT CK_payment_transactions_env CHECK (payment_environment = 'SANDBOX');
IF OBJECT_ID('dbo.CK_payment_transactions_amount','C') IS NULL ALTER TABLE dbo.payment_transactions ADD CONSTRAINT CK_payment_transactions_amount CHECK (expected_amount >= 0 AND (paid_amount IS NULL OR paid_amount >= 0));
IF OBJECT_ID('dbo.CK_plan_price_history_price','C') IS NULL ALTER TABLE dbo.plan_price_history ADD CONSTRAINT CK_plan_price_history_price CHECK (new_price >= 0 AND (old_price IS NULL OR old_price >= 0));
-- Dữ liệu cũ có thể không thỏa -> WITH NOCHECK (chỉ kiểm tra dữ liệu mới)
IF OBJECT_ID('dbo.CK_subscriptions_dates','C') IS NULL ALTER TABLE dbo.subscriptions WITH NOCHECK ADD CONSTRAINT CK_subscriptions_dates CHECK (ends_at IS NULL OR ends_at > starts_at);
GO

-- Filtered unique indexes
-- Mỗi gói tối đa 1 yêu cầu PENDING
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_plan_change_requests_pending')
    CREATE UNIQUE INDEX UQ_plan_change_requests_pending ON dbo.plan_change_requests(plan_id) WHERE status = 'PENDING';
-- Mã giao dịch VNPay duy nhất theo nhà cung cấp + môi trường (khi có giá trị)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_payment_transactions_txn_no')
    CREATE UNIQUE INDEX UQ_payment_transactions_txn_no ON dbo.payment_transactions(payment_provider, payment_environment, provider_transaction_no) WHERE provider_transaction_no IS NOT NULL;
-- Một đơn hàng chỉ cấp / gia hạn Premium một lần
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_subscription_history_order_grant')
    CREATE UNIQUE INDEX UQ_subscription_history_order_grant ON dbo.subscription_history(order_id) WHERE order_id IS NOT NULL AND action_type IN ('ACTIVATED','RENEWED');
-- Mỗi học viên tối đa 1 đơn chờ thanh toán cho cùng một gói (chống bấm thanh toán nhiều lần)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_subscription_orders_pending')
    CREATE UNIQUE INDEX UQ_subscription_orders_pending ON dbo.subscription_orders(user_id, plan_id) WHERE status = 'PENDING_PAYMENT';
-- Mỗi học viên tối đa 1 subscription ACTIVE (chỉ tạo khi dữ liệu cũ không bị trùng)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_subscriptions_user_active')
   AND NOT EXISTS (SELECT user_id FROM dbo.subscriptions WHERE status = 'ACTIVE' GROUP BY user_id HAVING COUNT(*) > 1)
    CREATE UNIQUE INDEX UQ_subscriptions_user_active ON dbo.subscriptions(user_id) WHERE status = 'ACTIVE';
GO

-- Khóa ngoại tới users (không ON DELETE CASCADE để không mất lịch sử giao dịch)
IF OBJECT_ID('dbo.FK_subscription_orders_user','F') IS NULL
    ALTER TABLE dbo.subscription_orders WITH NOCHECK ADD CONSTRAINT FK_subscription_orders_user FOREIGN KEY (user_id) REFERENCES dbo.users(id);
GO
