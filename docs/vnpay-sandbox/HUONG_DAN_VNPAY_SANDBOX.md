# Hướng dẫn chạy module Gói học & Thanh toán VNPay Sandbox

> Module chỉ dùng **VNPay Sandbox**. Không dùng tài khoản merchant Production, không dùng thẻ hay tiền thật.
> Mọi giao dịch được lưu với `payment_environment = SANDBOX`. Doanh thu trên hệ thống là **doanh thu mô phỏng**.

## 1. Đăng ký tài khoản VNPay Sandbox

1. Mở trang đăng ký merchant thử nghiệm chính thức: https://sandbox.vnpayment.vn/devreg/
   (tài liệu API: https://sandbox.vnpaytest.vn/apis/).
2. Điền thông tin website thử nghiệm, email nhận thông tin, và URL website (có thể ghi `http://localhost:8080`).
3. VNPay gửi email gồm:
   - **vnp_TmnCode**: mã website (Terminal ID).
   - **vnp_HashSecret**: chuỗi bí mật để tạo checksum.
   - Tài khoản đăng nhập trang quản trị merchant Sandbox: http://sandbox.vnpayment.vn/merchantv2/
   - **Thông tin thẻ thử nghiệm**: dùng đúng thông tin thẻ test trong email hoặc trên trang demo của VNPay
     (https://sandbox.vnpaytest.vn/apis/vnpay-demo/). Tài liệu này không ghi sẵn số thẻ hay OTP; hãy lấy từ nguồn chính thức.
4. Không chia sẻ Hash Secret, không commit lên GitHub.

## 2. Cấu hình biến môi trường

Ứng dụng đọc trực tiếp từ biến môi trường. Mẫu ở file `docs/vnpay-sandbox/vnpay-sandbox.env.example`:

| Biến | Ý nghĩa | Mặc định |
|---|---|---|
| `VNPAY_TMN_CODE` | Mã website Sandbox | (bắt buộc) |
| `VNPAY_HASH_SECRET` | Chuỗi bí mật checksum | (bắt buộc) |
| `VNPAY_PAYMENT_URL` | URL thanh toán (mở trên trình duyệt) | `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html` |
| `VNPAY_API_URL` | URL API QueryDR | `https://sandbox.vnpaytest.vn/merchant_webapi/api/transaction` |
| `VNPAY_VERSION` | Phiên bản API | `2.1.0` |
| `VNPAY_RETURN_URL` | Trang VNPay chuyển học viên về | `http://localhost:8080/payment/vnpay/return` |
| `VNPAY_IPN_URL` | URL IPN công khai (chỉ để tham chiếu) | trống |
| `PAYMENT_EXPIRATION_MINUTES` | Thời gian chờ thanh toán | `15` |
| `VNPAY_ACCEPT_SIGNED_RETURN` | `true` = khi VNPay Sandbox không gửi IPN và QueryDR lỗi, dùng dữ liệu Return URL **có chữ ký hợp lệ**, đúng merchant, đúng số tiền, mã 00/00 để xác nhận và kích hoạt Premium (ghi audit `PAYMENT_CONFIRMED_BY_SIGNED_RETURN`). Chỉ bật khi demo Sandbox. | `false` |

Hệ thống **từ chối** tạo thanh toán nếu thiếu `VNPAY_TMN_CODE`/`VNPAY_HASH_SECRET` hoặc nếu URL thanh toán/API không phải
`https://sandbox.vnpaytest.vn` / `https://sandbox.vnpayment.vn` (chặn cấu hình nhầm Production).

**IntelliJ IDEA:** Run ▸ Edit Configurations… ▸ chọn `HotTopikApplication` ▸ *Environment variables* ▸ thêm từng biến
(ví dụ `VNPAY_TMN_CODE=XXXX;VNPAY_HASH_SECRET=YYYY`) ▸ Apply ▸ chạy lại ứng dụng.

## 3. IPN khi chạy localhost

VNPay Server không gọi được `http://localhost`. Có hai phương án:

### Phương án A – Public HTTPS tunnel (khuyên dùng khi demo IPN)
1. Cài ngrok (https://ngrok.com/download) hoặc Cloudflare Tunnel (`cloudflared`).
2. Chạy `ngrok http 8080` (hoặc `cloudflared tunnel --url http://localhost:8080`), lấy địa chỉ dạng `https://abc.ngrok-free.app`.
3. Đăng nhập trang merchant Sandbox, khai báo **IPN URL** = `https://abc.ngrok-free.app/payment/vnpay/ipn`
   (IPN được cấu hình trên cổng merchant, không gửi kèm từng request).
4. Chỉ bật tunnel trong lúc kiểm thử và tắt ngay sau đó. Các trang `/admin`, `/operation` vẫn yêu cầu đăng nhập và đúng vai trò;
   không chia sẻ địa chỉ tunnel và không mở cổng database.

### Phương án B – Đối soát bằng API QueryDR (không cần tunnel)
- Khi VNPay chuyển học viên về Return URL, backend kiểm tra chữ ký rồi **gọi API QueryDR** (server-to-server) để lấy kết quả có chữ ký.
- Trang kết quả tự hỏi lại trạng thái mỗi 4 giây (tối đa khoảng 2 phút). Đơn quá hạn được Scheduler đối soát lại trước khi chuyển sang EXPIRED.
- Admin có nút **"Đối soát với VNPay Sandbox"** trong chi tiết giao dịch.
- Premium **không bao giờ** được kích hoạt chỉ dựa vào tham số trên Return URL.

## 4. Kiểm thử

1. Chạy Spring Boot, đăng nhập bằng tài khoản học viên.
2. Vào **Gói học** (`/subscription/plans`), chọn một gói Premium, chọn **Đăng ký ngay**.
3. Ở trang xác nhận, kiểm tra giá rồi chọn **Xác nhận và thanh toán qua VNPay Sandbox**. Hệ thống tạo đơn `PENDING_PAYMENT` và chuyển sang VNPay Sandbox.
4. Nhập thông tin thẻ thử nghiệm do VNPay cung cấp. Có thể thử các kịch bản thành công, hủy giao dịch và nhập sai OTP.
5. Sau khi quay về, trang kết quả hiển thị trạng thái do backend xác nhận: SUCCESS, FAILED, CANCELLED, VERIFYING…
6. Kiểm tra database:
   ```sql
   SELECT order_code, status, total_amount, payment_environment FROM dbo.subscription_orders ORDER BY id DESC;
   SELECT provider_txn_ref, provider_transaction_no, status, response_code, transaction_status FROM dbo.payment_transactions ORDER BY id DESC;
   SELECT user_id, status, starts_at, ends_at, plan_id FROM dbo.subscriptions ORDER BY id DESC;
   SELECT action_type, order_id, old_end_date, new_end_date FROM dbo.subscription_history ORDER BY id DESC;
   SELECT callback_type, processing_status, received_at FROM dbo.payment_callback_logs ORDER BY id DESC;
   ```
7. Vào **Gói của tôi** (`/subscription/my-plan`) để xem ngày hết hạn và số ngày còn lại. Chức năng Premium ("Gửi Giáo viên chấm") được mở ngay.

## 5. Phân quyền

| Vai trò | Đường dẫn |
|---|---|
| Học viên | `/subscription/plans`, `/subscription/my-plan`, `/subscription/transactions` |
| Operation Manager | `/operation/subscriptions`, `/operation/subscriptions/requests`, `/operation/transactions`, `/operation/premium-users`, `/operation/revenue` |
| Admin | `/admin/subscription-requests`, `/admin/subscriptions`, `/admin/transactions`, `/admin/revenue`, `/admin/subscriptions/price-history` |

## 6. Cần đối chiếu với tài liệu chính thức
Lúc viết code, không truy cập được trang tài liệu VNPay từ máy phát triển. Code được viết theo đặc tả API 2.1.0, bao gồm:
- tham số `vnp_*` và `vnp_Amount = VND × 100`;
- ngày giờ `yyyyMMddHHmmss` theo GMT+7;
- HMAC-SHA512 trên chuỗi tham số đã sắp xếp và URL-encode;
- IPN dùng GET, trả về `{"RspCode","Message"}` với các mã 00/01/02/04/97/99;
- QueryDR dùng POST JSON, checksum nối bằng dấu `|`.

Trước khi demo, hãy mở https://sandbox.vnpaytest.vn/apis/docs/ để kiểm tra lại các điểm trên, đặc biệt là thứ tự trường trong checksum
QueryDR (`VNPaySandboxPaymentGateway.queryTransaction`).
