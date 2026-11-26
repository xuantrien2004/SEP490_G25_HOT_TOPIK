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
| `GET` / `POST` | `/exam/{id}/start` | Bắt đầu làm một đề thi mới (hoặc nối lượt đang dở) | Learner |
| `GET` | `/exam/{id}/retry` | Bắt đầu lượt làm bài mới cho đề thi | Learner |
| `GET` | `/lam-bai/{attemptId}` (alias `/exam/play/{attemptId}`) | Màn hình làm bài thi (`question-player.html`), hiển thị theo kỹ năng hiện tại | Learner (chính chủ) |
| `POST` | `/exam/attempt/{id}/save` | Tự động lưu đáp án (Autosave qua AJAX, bóc tách qua `extractAnswers`) | Learner (chính chủ) |
| `POST` | `/exam/attempt/{id}/check/{key}` | Kiểm tra tức thì đáp án của một câu hỏi | Learner (chính chủ) |
| `POST` | `/exam/attempt/{id}/pause` | Lưu nháp & Tạm dừng bài thi (dọn dẹp attempt rỗng nếu chưa làm câu nào) | Learner (chính chủ) |
| `POST` | `/exam/attempt/{id}/submit` | Nộp bài hoặc nộp kỹ năng hiện tại (tự động chuyển tiếp kỹ năng nếu là Full Mock, hoặc hoàn thành bài thi) | Learner (chính chủ) |
| `POST` | `/exam/attempt/{id}/submit-teacher`, `/exam/attempt/{id}/send-teacher` | Nộp bài thi Viết và gửi chấm cho Giáo viên (VIP) | VIP Learner |
| `POST` | `/exam/attempt/{id}/send-teacher-q` | Gửi chấm một câu Viết cụ thể cho Giáo viên (VIP) | VIP Learner |
| `GET` | `/ket-qua/{attemptId}` (alias `/exam/result/{attemptId}`) | Xem kết quả bài thi, bảng điểm chuẩn TOPIK và xem lại câu hỏi kèm bộ lọc tab kỹ năng | Learner (chính chủ) |

### 1.4. Phân Hệ Luyện Tập Dạng Câu Hỏi (`PracticeController.java`)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `GET` | `/practice` (alias `/luyen-tap`) | Danh mục các dạng câu hỏi chuẩn đề thi | Learner |
| `GET` | `/practice/prepare` (alias `/luyen-tap/chuan-bi`) | Trang chuẩn bị luyện dạng, nhận diện bài làm dở (`pendingAttempt`) và lịch sử | Learner |
| `POST` | `/practice/start` | Bắt đầu hoặc làm lại lượt luyện tập theo dạng (`typeCode`) | Learner |
| `GET` | `/practice/play/{attemptId}` | Màn hình luyện tập câu hỏi (`practice/play.html`), đánh số thứ tự tuần tự từ 1 | Learner (chính chủ) |
| `POST` | `/practice/save/{attemptId}` | Lưu nháp bài luyện tập (câu trả lời, cờ chưa chắc, số giây đã làm) qua AJAX | Learner (chính chủ) |
| `POST` | `/practice/pause/{attemptId}` | Tạm dừng bài luyện tập và chuyển hướng về trang chuẩn bị | Learner (chính chủ) |
| `POST` | `/practice/submit/{attemptId}` | Nộp bài luyện tập và tính điểm | Learner (chính chủ) |
| `GET` | `/practice/result/{attemptId}` | Xem kết quả luyện tập, đối chiếu câu gốc (`originalNo`) và giải thích | Learner (chính chủ) |
| `GET` | `/practice/media/{sourceId}/{filename}` | Phục vụ file audio/hình ảnh trong lúc luyện tập | Public / Session |

### 1.5. Phân Hệ Kỹ Năng Viết (`WritingController.java`)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `GET` | `/writing` | Phòng luyện kỹ năng Viết (Câu 51, 52, 53, 54) | Learner |
| `POST` | `/writing/draft` | Tự động lưu nháp câu viết kèm thời gian làm bài (`durationSeconds`, hỗ trợ beacon/fetch) | Learner |
| `POST` | `/writing/submit` (alias `/writing/submit-mentor`) | Nộp bài luyện viết hoặc gửi trực tiếp cho Giáo viên chấm (`teacher=true`) | Learner / VIP Learner |
| `GET` | `/writing/history` | Chi tiết bài luyện Viết đã nộp chưa gửi GV (tự chuyển hướng `/writing/feedback` nếu đã gửi GV) | Learner (chính chủ) |
| `GET` | `/writing/feedback` (alias `/mentor-reviews`, `/bai-cham`) | Danh sách & chi tiết bài viết gửi Giáo viên (`?id=&page=`), xem điểm, nhận xét, từ chối và lịch sử khiếu nại | Learner (chính chủ) |
| `POST` | `/writing/send-teacher` | Gửi bài luyện viết đã nộp sang hàng chờ Giáo viên chấm | VIP Learner |
| `POST` | `/writing/appeal` | Gửi yêu cầu khiếu nại điểm số (tối đa 1 lần/bài, lưu `appealedAt`) | Learner (chính chủ) |
| `POST` | `/writing/ask` | Gửi câu hỏi trao đổi thêm tới Giáo viên chấm bài | Learner (chính chủ) |
| `POST` | `/writing/rate` | Đánh giá mức độ hài lòng với phần chấm bài của Giáo viên | Learner (chính chủ) |
| `GET` | `/writing/prompt/{id}/chart`, `/writing/submission/{id}/chart` | Phục vụ ảnh biểu đồ đề bài viết câu 53 | Đã đăng nhập |

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
| `GET` | `/ly-thuyet/han-viet`, `/theory/hanja` | Giao diện Cầu nối Hán Việt (hỗ trợ `?tab=learn\|guess\|learned\|mistakes`) | Learner |
| `GET` | `/api/learner/hanja/roots` | API nạp danh sách gốc Hán tự ACTIVE (hỗ trợ query tìm kiếm) | Learner |
| `GET` | `/api/learner/hanja/roots/{id}` | API nạp chi tiết gốc Hán tự và danh sách từ vựng liên quan | Learner |
| `GET` | `/api/learner/hanja/quiz` | API tạo ngẫu nhiên câu hỏi trắc nghiệm 4 đáp án cho quiz Hán tự | Learner |
| `GET` | `/api/learner/hanja/stats` | Thống kê số lượng gốc Hán và từ vựng liên quan | Learner |

### 1.8. Cổng Giảng Viên (`TeacherPortalController.java`)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `GET` / `POST` | `/teacher` (alias `/mentor`) | Dashboard giảng viên (`tab`: `queue`, `mine`, `done`, `appeal`, `calib`; `sel` mở bàn chấm) | Teacher / Admin |
| `POST` | `/teacher/claim` | Tiếp nhận bài viết từ hàng chờ để chấm | Teacher / Admin |
| `POST` | `/teacher/save-draft` | Lưu tạm điểm tiêu chí/tổng điểm và nhận xét đang chấm dở; giữ nguyên trạng thái `claimed`/`appeal` và không thông báo cho học viên | Teacher / Admin |
| `POST` | `/teacher/reject` | Từ chối chấm bài kèm lý do bắt buộc (`reason`), trả trạng thái về `submitted` | Teacher / Admin |
| `POST` | `/teacher/grade` | Lưu điểm chi tiết, nhận xét, đáp án mẫu hoặc xử lý khiếu nại (`revise` / `uphold` kèm lời nhắn) | Teacher / Admin |
| `POST` | `/teacher/reply-ask` | Trả lời thắc mắc thêm của học viên sau khi chấm | Teacher / Admin |
| `GET` / `POST` | `/teacher/profile`, `/teacher/profile/*` | Xem, cập nhật hồ sơ cá nhân và đổi mật khẩu Giảng viên | Teacher / Admin |

### 1.9. Cổng Quản Lý Nội Dung (`ContentExamController.java`, `ContentPortalController.java`...)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `GET` | `/content/sources` | Danh sách các nguồn đề thi | Content / Admin |
| `GET` | `/content/sources/{id}` | Chi tiết nguồn đề, soạn câu hỏi thủ công hoặc Excel | Content / Admin |
| `GET` | `/content/sources/{id}/template-full` | Tải file Excel mẫu toàn bộ đề thi (đa sheet: `NGHE`, `DOC`, `VIET`, `DANG_CAU`) | Content / Admin |
| `GET` | `/content/sources/{id}/template` | Tải file Excel mẫu theo từng kỹ năng lẻ (`?skill=...`) | Content / Admin |
| `POST` | `/content/sources/{id}/cards` | Lưu câu hỏi thủ công (audio, passage, 4 options, ảnh) | Content / Admin |
| `POST` | `/content/sources/{id}/excel` | Đổ nhanh câu hỏi toàn bộ đề từ file Excel mẫu gộp `.xlsx` | Content / Admin |
| `POST` | `/content/sources/{id}/excel/skill` | Import câu hỏi từ file Excel của từng kỹ năng lẻ | Content / Admin |
| `GET` | `/content/media/{sourceId}/{filename}` | Stream file audio MP3 (hỗ trợ Byte-Range 206, Content-Type `audio/mpeg`) hoặc hiển thị hình ảnh | Public / Session |
| `POST` | `/content/sources/{id}/media/inline` | Tải lên file ảnh hoặc MP3 đơn lẻ/inline cho câu hỏi qua AJAX | Content / Admin |
| `POST` | `/content/sources/{id}/media` | Tải lên danh sách file media vào nguồn đề qua form multipart | Content / Admin |
| `POST` | `/content/sources/{id}/media/{mediaId}/delete` | Xóa 1 file media khỏi nguồn đề, tự động gỡ liên kết khỏi câu hỏi | Content / Admin |
| `POST` | `/content/sources/{id}/media/delete-batch` | Xóa danh sách file media được chọn theo checkbox (`mediaIds`) | Content / Admin |
| `POST` | `/content/sources/{id}/media/delete-all` | Xóa toàn bộ file media của nguồn đề | Content / Admin |
| `POST` | `/content/sources/{id}/media/clear-card-audio` | Gỡ file audio khỏi câu hỏi (`QuestionGroup`), tự động xóa `MediaAsset` nếu không còn câu nào khác dùng chung | Content / Admin |
| `POST` | `/content/sources/{id}/listening/bulk-audio/pre-check` | Upload file `.zip` audio kỹ năng Nghe, giải nén tạm thời và trả về báo cáo đối soát khớp câu | Content / Admin |
| `POST` | `/content/sources/{id}/listening/bulk-audio/confirm` | Xác nhận lưu các file audio hợp lệ từ token tạm vào đề và gán vào các câu hỏi | Content / Admin |
| `POST` | `/content/sources/{id}/listening/bulk-audio/cancel` | Hủy phiên upload audio hàng loạt và dọn dẹp thư mục tạm | Content / Admin |

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

### 1.12. Phân Hệ Tiến Độ Học Tập & Lịch Sử (`LearnerController.java`)

| HTTP Method | Đường Dẫn (Endpoint) | Mục Đích | Quyền Hạn |
|:---|:---|:---|:---|
| `GET` | `/tien-do` (alias `/progress`) | Màn hình tiến độ học tập: nạp dữ liệu thống kê qua `ProgressService.load(userId)`, biểu đồ cột chồng Full Mock (Best Attempt), phân tích tỷ lệ đúng theo dạng câu hỏi | Learner |
| `GET` | `/tien-do/lich-su` (alias `/progress/history`) | Lịch sử học tập chi tiết (các bài thi và bài luyện tập đã làm) | Learner |

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

### 2.4. Apache POI (Excel Parser & Generator)
- **Thư viện**: `poi 5.2.5`, `poi-ooxml 5.2.5`.
- **Mục đích**:
  - Sinh template Excel mẫu và phân tích nhập đề thi từ file `.xlsx`.
  - Hỗ trợ định dạng file mẫu theo từng kỹ năng lẻ (`NGHE`, `DOC`, `VIET`) và file toàn bộ đề gộp đa sheet (`NGHE`, `DOC`, `VIET`, `DANG_CAU`).
  - Duy trì khả năng tương thích ngược với định dạng 2 sheet truyền thống (`QUESTIONS` và `WRITING`).

### 2.5. Local Media Storage Service
- **Lưu trữ**: Thư mục cục bộ `./data/storage`.
- **Hỗ trợ định dạng**: `.mp3` (bắt buộc cho audio), `.png, .jpg, .jpeg, .webp` (cho ảnh biểu đồ và ảnh phương án).
