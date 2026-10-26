-- Profile local dùng H2. ddl-auto=update không thêm cột vào bảng đã có,
-- nên database tạo từ bản trước thiếu script_vi và ứng dụng tắt lúc mở.
ALTER TABLE IF EXISTS dbo.question_groups ADD COLUMN IF NOT EXISTS script_vi VARCHAR(4000);
