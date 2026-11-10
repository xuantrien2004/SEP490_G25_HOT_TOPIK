# 07 - API VÀ TÍCH HỢP HỆ THỐNG (API & INTEGRATIONS)

> **Trạng thái**: Đã xác minh từ toàn bộ 24 Controller classes trong gói `com.hottopik.controller`.

---

## 1. Danh Mục Các Cụm Controller & Endpoint

Dự án áp dụng mô hình Spring MVC Controller. Phần lớn các endpoint trả về view Thymeleaf (HTML kết xuất hoàn chỉnh), một số endpoint phục vụ AJAX/Fetch trả về JSON hoặc chuyển tiếp xử lý.

### 1.1. Phân Hệ Xác Thực & Tài Khoản (`AuthController.java`, `OnboardingController.java`)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `GET` | `/login` | Hiển thị trang đăng nhập | Public |
| `POST` | `/login` | Xác thực người dùng bằng email và mật khẩu | Public |
| `GET` | `/register` | Hiển thị trang đăng ký tài khoản | Public |
| `POST` | `/register` | Đăng ký tài khoản và gửi mã OTP qua email | Public |
| `GET` | `/verify-otp` | Trang nhập mã OTP xác thực email | Public |
| `POST` | `/verify-otp` | Kiểm tra mã OTP kích hoạt tài khoản | Public |
| `GET` | `/forgot-password` | Trang yêu cầu gửi mã đặt lại mật khẩu | Public |
| `POST` | `/forgot-password` | Gửi mã OTP đặt lại mật khẩu qua email | Public |
| `POST` | `/reset-password` | Xác thực OTP và lưu mật khẩu mới | Public |
| `POST` | `/auth/google` | Đăng nhập bằng Google Credential Token | Public |
| `GET` | `/logout` | Hủy phiên đăng nhập (`session.invalidate()`) | Đã đăng nhập |

### 1.2. Phân Hệ Đánh Giá Năng Lực Đầu Vào (`PlacementController.java`)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `GET` | `/placement` | Trang giới thiệu bài thi xếp lớp đầu vào | Public |
| `GET` | `/placement/start` | Khởi tạo lượt làm bài test (20 câu trắc nghiệm) | Public / Learner |
| `POST` | `/placement/submit` | Nộp bài kiểm tra đầu vào, chấm điểm tức thì | Public / Learner |
| `GET` | `/placement/result` | Xem bảng điểm, cấp độ đánh giá và lời khuyên lộ trình | Public / Learner |

### 1.3. Phân Hệ Thi Thử (`LearnerExamController.java`)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `GET` | `/exam` | Thư viện đề thi (lọc theo TOPIK I / II, Full / Mini) | Learner |
| `GET` | `/exam/{id}/start` | Bắt đầu làm một đề thi mới | Learner |
| `GET` | `/lam-bai/{attemptId}` | Màn hình làm bài thi (`question-player.html`) | Learner (chính chủ) |
| `POST` | `/lam-bai/{attemptId}/save` | Tự động lưu đáp án trong khi làm bài (Autosave) | Learner (chính chủ) |
| `POST` | `/lam-bai/{attemptId}/submit` | Nộp bài thi chính thức và tính điểm | Learner (chính chủ) |
| `GET` | `/exam/result/{attemptId}` | Xem kết quả bài thi, bảng điểm và giải thích câu | Learner (chính chủ) |

### 1.4. Phân Hệ Luyện Tập Dạng Câu Hỏi (`PracticeController.java`)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `GET` | `/practice` | Danh mục các dạng câu hỏi chuẩn đề thi | Learner |
| `GET` | `/practice/{typeCode}` | Phòng luyện tập tập trung theo mã dạng câu | Learner |
| `POST` | `/practice/check` | Kiểm tra tức thì đáp án của câu hỏi đang làm | Learner |
| `GET` | `/practice/history` | Lịch sử các lần luyện dạng câu hỏi | Learner |

### 1.5. Phân Hệ Kỹ Năng Viết (`WritingController.java`)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `GET` | `/writing` | Phòng luyện kỹ năng Viết (Câu 51, 52, 53, 54) | Learner |
| `POST` | `/writing/evaluate-51-52` | Đánh giá & chấm điểm tự động câu 51 và câu 52 | Learner |
| `POST` | `/writing/submit` | Nộp bài viết cho Giáo viên chấm | VIP Learner |
| `GET` | `/writing/submissions` | Danh sách lịch sử bài viết đã nộp | Learner |
| `GET` | `/writing/submissions/{id}` | Xem chi tiết bài viết, điểm và lời phê của giáo viên | Learner (chính chủ) / GV |

### 1.6. Phân Hệ Lộ Trình Học Cá Nhân Hóa (`RoadmapController.java`)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `GET` | `/roadmap` | Màn hình lộ trình học cá nhân hóa | Learner |
| `POST` | `/roadmap/activate` | Kích hoạt lộ trình theo mục tiêu đã chọn | Learner |
| `GET` | `/roadmap/day/{dayNo}` | Xem nội dung và bài tập của ngày học cụ thể | Learner |
| `POST` | `/roadmap/catch-up` | Kích hoạt cơ chế phục hồi nhịp học khi nghỉ quá 3 ngày | Learner |

### 1.7. Phân Hệ Kho Lý Thuyết (`TheoryController.java`, `LearnerHanjaController.java`)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `GET` | `/theory/vocabulary` | Tra cứu kho từ vựng TOPIK 1 - 6 theo chủ đề | Learner |
| `GET` | `/theory/grammar` | Tra cứu kho ngữ pháp tiếng Hàn theo nhóm ý nghĩa | Learner |
| `GET` | `/hanja` | Tra cứu kho Hán tự Hanja và từ phái sinh âm Hán Việt | Learner |

### 1.8. Cổng Giảng Viên (`TeacherPortalController.java`)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `GET` | `/teacher` | Dashboard giảng viên, danh sách bài chờ chấm | Teacher / Admin |
| `GET` | `/teacher/submissions/{id}` | Màn hình đọc bài và chấm điểm theo Rubric | Teacher / Admin |
| `POST` | `/teacher/grade/{id}` | Lưu điểm chi tiết từng tiêu chí và nhận xét | Teacher / Admin |

### 1.9. Cổng Quản Lý Nội Dung (`ContentExamController.java`, `ContentPortalController.java`...)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `GET` | `/content/sources` | Danh sách các nguồn đề thi | Content / Admin |
| `GET` | `/content/sources/{id}` | Chi tiết nguồn đề, soạn câu hỏi thủ công hoặc Excel | Content / Admin |
| `POST` | `/content/sources/{id}/cards` | Lưu câu hỏi thủ công (audio, passage, 4 options, ảnh) | Content / Admin |
| `POST` | `/content/sources/{id}/excel` | Đổ nhanh câu hỏi từ file Excel mẫu `.xlsx` | Content / Admin |
| `GET` | `/content/media/{sourceId}/{filename}` | Stream file audio MP3 hoặc tải hình ảnh câu hỏi | Public / Session |
| `POST` | `/content/media/upload` | Tải lên file ảnh hoặc MP3 đơn lẻ qua AJAX | Content / Admin |

### 1.10. Cổng Quản Lý Vận Hành & Báo Lỗi (`OperationPortalController.java`, `QuestionReportController.java`)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `POST` | `/report/question` | Học viên gửi báo lỗi câu hỏi từ giao diện thi | Learner |
| `GET` | `/operation` | Bảng điều khiển quản lý vận hành | Operation / Admin |
| `GET` | `/operation/reports` | Danh sách các câu hỏi bị báo lỗi | Operation / Admin |
| `POST` | `/operation/reports/{id}/resolve` | Sửa câu hỏi, lưu bản sửa đổi và hoàn điểm học viên | Operation / Admin |

### 1.11. Cổng Quản Trị Hệ Thống (`AdminController.java`)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `GET` | `/admin` | Thống kê tổng quan hệ thống (User, Đề thi, Doanh thu) | Admin |
| `GET` | `/admin/users` | Danh sách người dùng, tìm kiếm và lọc | Admin |
| `POST` | `/admin/users/{id}/lock` | Khóa tài khoản tạm thời hoặc vĩnh viễn | Admin |
| `POST` | `/admin/users/{id}/role` | Phân quyền vai trò người dùng | Admin |

---

## 2. Các Dịch Vụ Tích Hợp Bên Ngoài

### 2.1. Microsoft SQL Server & H2 Database
- **Thư viện**: `mssql-jdbc` (chính), `h2` (local profile).
- **Cấu hình HikariCP**: Timeout kết nối 3000ms, tự động cập nhật schema (`ddl-auto=update`).

### 2.2. Dịch vụ Email Google SMTP
- **Thư viện**: `spring-boot-starter-mail`.
- **Cấu hình**: `smtp.gmail.com:587`, STARTTLS.
- **Mục đích**: Gửi mã OTP xác thực đăng ký tài khoản và mã OTP đặt lại mật khẩu.

### 2.3. Google Identity Services (OAuth2 Login)
- **Cơ chế**: Người dùng đăng nhập bằng tài khoản Google trên giao diện, lấy `credential` (JWT Token) gửi về endpoint `/auth/google`. Server giải mã token và đồng bộ tài khoản vào bảng `users`.

### 2.4. Apache POI (Excel Parser)
- **Thư viện**: `poi 5.2.5`, `poi-ooxml 5.2.5`.
- **Mục đích**: Phân tích file bảng tính Excel `.xlsx` gồm 2 sheet chuẩn: `QUESTIONS` (câu trắc nghiệm Nghe/Đọc) và `WRITING` (câu tự luận Viết).

### 2.5. Local Media Storage Service
- **Lưu trữ**: Thư mục cục bộ `./data/storage`.
- **Hỗ trợ định dạng**: `.mp3` (bắt buộc cho audio), `.png, .jpg, .jpeg, .webp` (cho ảnh biểu đồ và ảnh phương án).
