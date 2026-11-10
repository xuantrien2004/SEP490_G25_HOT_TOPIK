# 10 - BẢN ĐỒ TẬP TIN MÃ NGUỒN (SOURCE CODE FILE MAP)

> **Trạng thái**: Đã xác minh 100% đường dẫn tệp tin thực tế trong dự án.  
> **Dành cho AI Agent**: Tra cứu bảng này để biết ngay file mã nguồn cần đọc và chỉnh sửa khi tiếp nhận yêu cầu từ người dùng, không cần quét toàn bộ thư mục.

---

## 1. Bảng Tra Cứu Nhanh Theo Tính Năng (Feature-to-File Mapping)

| Tính Năng / Nghiệp Vụ | Controller (C) | Service / Evaluator (S) | Repository / Entity (R/E) | View Template & Static Assets (V) |
|:---|:---|:---|:---|:---|
| **Đăng nhập, Đăng ký, Quên mật khẩu, OTP** | `AuthController.java`<br>`OnboardingController.java` | `UserService.java`<br>`EmailService.java`<br>`AuthTokenService.java` | `UserRepository.java`<br>`AuthTokenRepository.java`<br>`User.java` | `templates/auth/login.html`<br>`templates/auth/register.html`<br>`templates/auth/verify-otp.html`<br>`templates/auth/forgot-password.html` |
| **Đánh giá năng lực đầu vào (Placement Test)** | `PlacementController.java`<br>`ContentPlacementController.java` | `PlacementService.java`<br>`PlacementRules.java` | `PlacementAttemptRepository.java`<br>`PlacementLineRepository.java`<br>`PlacementAttempt.java` | `templates/placement/intro.html`<br>`templates/placement/play.html`<br>`templates/placement/result.html`<br>`templates/content/placement/index.html` |
| **Lộ trình học cá nhân hóa (Roadmap)** | `RoadmapController.java`<br>`ContentRoadmapController.java` | `LearnerRoadmapService.java`<br>`ProgramRoadmapService.java`<br>`ProgramRules.java` | `ProgramVersionRepository.java`<br>`ProgramVersionItemRepository.java`<br>`StudyPlanRepository.java` | `templates/roadmap/view.html`<br>`templates/roadmap/day.html`<br>`templates/content/roadmap/versions.html` |
| **Luyện tập theo dạng câu hỏi (Practice Engine)** | `PracticeController.java` | `PracticeService.java`<br>`QuestionTypeCatalog.java` | `PracticeAttemptRepository.java`<br>`QuestionRepository.java`<br>`PracticeAttempt.java` | `templates/practice/index.html`<br>`templates/practice/prepare.html`<br>`templates/practice/play.html`<br>`templates/practice/result.html` |
| **Thi thử Đề chuẩn (Full Mock & Mini Test)** | `LearnerExamController.java` | `LearnerExamService.java`<br>`ExamComposerService.java`<br>`ExamScoreCalculator.java`<br>`Writing5152Evaluator.java`<br>`ExamScoreDTO.java` | `ExamRepository.java`<br>`ExamAttemptRepository.java`<br>`ExamItemRepository.java`<br>`Exam.java` | `templates/exam/index.html`<br>`templates/exam/play.html`<br>`templates/exam/result.html`<br>`templates/fragments/question-player.html`<br>`templates/fragments/result-review.html`<br>`static/js/question-player.js`<br>`static/css/question-player.css` |
| **Kỹ năng Viết (Writing 51, 52, 53, 54)** | `WritingController.java` | `Writing5152Evaluator.java`<br>`WritingSubmissionService.java` | `WritingPromptRepository.java`<br>`WritingSubmissionRepository.java`<br>`WritingSubmission.java` | `templates/writing/index.html`<br>`templates/writing/detail.html`<br>`templates/writing/submissions.html` |
| **Cổng Giảng viên: Chấm bài viết** | `TeacherPortalController.java` | `WritingSubmissionService.java` | `WritingSubmissionRepository.java`<br>`WritingPromptRepository.java` | `templates/teacher/dashboard.html`<br>`templates/teacher/grade.html` |
| **Quản lý nội dung đề thi & Nhập đề** | `ContentExamController.java`<br>`ContentPortalController.java` | `ExamImportService.java`<br>`ExamImportValidator.java`<br>`ExamWorkbookParser.java`<br>`MediaStorageService.java` | `ExamSourceRepository.java`<br>`ImportJobRepository.java`<br>`MediaAssetRepository.java`<br>`QuestionRepository.java` | `templates/content/exam/sources.html`<br>`templates/content/exam/source-detail.html`<br>`templates/content/dashboard.html` |
| **Kho lý thuyết: Từ vựng (Vocabulary)** | `TheoryController.java`<br>`ContentVocabularyController.java` | `VocabularyService.java`<br>`VocabularySyncService.java` | `VocabularyRepository.java`<br>`TopicRepository.java`<br>`Vocabulary.java` | `templates/theory/vocabulary.html`<br>`templates/content/vocabulary/index.html` |
| **Kho lý thuyết: Ngữ pháp (Grammar)** | `TheoryController.java` | `GrammarService.java`<br>`GrammarGroupService.java`<br>`GrammarExampleService.java` | `GrammarRepository.java`<br>`GrammarGroupRepository.java`<br>`Grammar.java` | `templates/theory/grammar.html`<br>`templates/theory/grammar-detail.html` |
| **Kho lý thuyết: Hán tự (Hanja)** | `LearnerHanjaController.java`<br>`ContentHanjaController.java` | `HanjaService.java`<br>`HanjaServiceImpl.java` | `HanjaRootRepository.java`<br>`HanjaRootWordRepository.java`<br>`HanjaRoot.java` | `templates/theory/hanja.html`<br>`templates/content/hanja/index.html` |
| **Báo lỗi câu hỏi & Sửa đổi câu hỏi** | `QuestionReportController.java`<br>`QuestionCorrectionController.java`<br>`OperationPortalController.java` | `QuestionReportService.java`<br>`QuestionCorrectionService.java`<br>`OperationService.java` | `QuestionReportRepository.java`<br>`QuestionRevisionRepository.java`<br>`QuestionRevisionRecipientRepository.java` | `templates/operation/dashboard.html`<br>`templates/operation/reports.html`<br>`templates/operation/report-detail.html`<br>`templates/operation/correction.html` |
| **Quản trị người dùng & Phân quyền (Admin)** | `AdminController.java` | `UserService.java`<br>`AdminSystemService.java` | `UserRepository.java`<br>`User.java` | `templates/admin/users.html`<br>`templates/admin/user-detail.html`<br>`templates/admin/roles.html`<br>`templates/admin/dashboard.html` |
| **Gói VIP & Quyền lợi VIP** | `VipController.java` | `UserService.java` | `UserRepository.java`<br>`User.java` | `templates/vip/index.html`<br>`templates/vip/checkout.html` |
| **Hệ thống Thông báo (In-App)** | Được nhúng qua navbar fragment | `NotificationService.java` | `UserNotificationRepository.java`<br>`UserNotification.java` | `templates/fragments/navbar.html` |

---

## 2. Chi Tiết Các Tệp Thành Phần Trọng Yếu Cần Đọc Kỹ

### 2.1. Trình Làm Bài Thi & Luyện Tập (`question-player`)
Khi người dùng báo lỗi liên quan đến giao diện làm bài, chuyển câu, audio, đoạn văn, sticky hoặc phiếu câu hỏi:
1. `src/main/resources/templates/fragments/question-player.html`: Cấu trúc DOM của trình làm bài:
   - Thẻ câu hỏi `.qp-question`, vùng gắn dữ liệu `data-group` để gom nhóm câu dùng chung.
   - Khung cố định `.qp-group-sticky` ở đầu danh sách câu hỏi: chứa audio, đoạn văn, ảnh dùng chung cho cả nhóm kèm nút Thu gọn/Mở rộng.
   - Nút hành động từng câu: "Chưa chắc", "Báo lỗi câu hỏi", "Kiểm tra" (chỉ hiện khi luyện tập).
   - Nút điều hướng nhóm câu / câu hỏi: "Câu trước", "Câu tiếp".
2. `src/main/resources/static/js/question-player.js`: Bộ điều khiển client:
   - `show(index)`: Kiểm tra số lượng câu trong nhóm (`matchingCards.length`).
     - Nếu $\ge 2$ câu (câu nhóm dùng chung): Bật chế độ `qp-group-mode`, đồng bộ audio, đoạn văn, ảnh đề bài lên `.qp-group-sticky`, ẩn các phần tử trùng lặp trong từng câu con.
     - Nếu $< 2$ câu (câu đơn): Tắt chế độ `qp-group-mode`, ẩn `.qp-group-sticky`, hiển thị âm thanh/đoạn văn/hình ảnh trực tiếp bên trong thẻ của chính câu hỏi đó.
   - Điều hướng `qp-prev` / `qp-next`: Câu đơn nhảy từng câu lẻ (+/- 1); câu nhóm nhảy theo khối nhóm.
   - `update()`: Đồng bộ trạng thái đáp án, số câu đã làm.
   - `IntersectionObserver`: Theo dõi cuộn trang để highlight đúng câu trong sidebar phiếu câu hỏi.
   - Xử lý phím tắt (1-4 để chọn đáp án, mũi tên trái/phải để chuyển câu).
3. `src/main/resources/static/css/question-player.css`: Định dạng giao diện trình làm bài, thiết lập sticky container `.qp-group-sticky`, điều chỉnh `scroll-margin-top` cho câu nhóm vs câu đơn.

### 2.2. Nhập Đề Thi & Quản Lý Câu Hỏi (`source-detail`)
Khi người dùng yêu cầu sửa đổi liên quan đến nhập đề, tải ảnh, tải audio:
1. `src/main/resources/templates/content/exam/source-detail.html`:
   - Khung nhập đề bài: hỗ trợ tải ảnh đề bài câu này cho cả Nghe, Đọc và Viết.
   - Khung 4 phương án: hỗ trợ vừa nhập text vừa tải ảnh xem trước đồng thời cho từng phương án (`opt1Image`..`opt4Image`).
   - Kéo thả file nghe MP3 (chỉ chấp nhận định dạng `.mp3`).
   - Lưới ô số câu hỏi (`num-grid`) kết hợp form nhập câu: bảo đảm mỗi câu hỏi được gán một ô số trên lưới.
2. `src/main/java/com/hottopik/service/exam/ExamImportService.java`:
   - Xử lý `saveCard`, `CardForm` (hỗ trợ overloaded constructor tương thích ngược), `AskView`.
   - Ánh xạ các phương án và ảnh phương án (`opt1Img`..`opt4Img`).
   - Hàm `askCount(form)`: Ưu tiên tính số câu theo `form.qno()`, ngăn ngừa lỗi sai lệch đếm gây thông báo `Mỗi câu cần một ô số trên lưới.`.
   - Xử lý hiển thị ảnh câu hỏi trong `baisFromCards` cho cả Nghe, Đọc, Viết.
3. `src/main/java/com/hottopik/controller/ContentExamController.java`:
   - Endpoint `/content/sources/{id}/cards` tiếp nhận form submit, xử lý upload ảnh đề bài `questionImage` và danh sách ảnh 4 phương án `processOptionImages`.

### 2.3. Chấm Điểm Chuẩn & Đánh Giá Bài Viết (`exam` / `writing`)
Khi người dùng yêu cầu chỉnh sửa liên quan đến chấm điểm Đề thi và Writing câu 51-54:
1. `src/main/java/com/hottopik/service/exam/ExamScoreCalculator.java`:
   - Tính điểm chuẩn TOPIK I (Nghe 30 câu / 100đ, Đọc 40 câu / 100đ $\rightarrow$ 200đ) và TOPIK II (Nghe 50 câu / 100đ, Đọc 50 câu / 100đ, Viết 100đ $\rightarrow$ 300đ).
   - Tích hợp tính điểm lịch sử làm bài và xếp loại kết quả (Xuất sắc, Rất tốt, Đạt, Cần cố gắng).
2. `src/main/java/com/hottopik/dto/ExamScoreDTO.java`:
   - DTO truyền tải toàn bộ dữ liệu điểm số, điểm thành phần từng kỹ năng, phản hồi chi tiết các câu Viết 51-54 lên view kết quả.
3. `src/main/java/com/hottopik/service/exam/Writing5152Evaluator.java`:
   - Thuật toán chấm điểm và đánh giá tự động câu 51 và câu 52 theo tiêu chuẩn TOPIK II (2.5đ nội dung + 2.5đ ngôn ngữ/đuôi câu kính ngữ hoặc văn viết cho từng ý `㉠`, `㉡`).
4. `src/main/java/com/hottopik/service/WritingSubmissionService.java`:
   - Quản lý nộp bài viết, tách biệt giữa bài thi và bài luyện tập, quản lý chấm điểm của giáo viên.
5. `src/main/resources/templates/fragments/result-review.html` & `src/main/resources/templates/exam/result.html`:
   - Giao diện xem lại bài thi: tự động ẩn bộ lọc "Câu đúng / Câu sai" khi đề thi là bài thi thuần kỹ năng Viết.
