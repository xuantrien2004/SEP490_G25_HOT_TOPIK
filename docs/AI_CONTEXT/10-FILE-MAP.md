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
| **Luyện tập theo dạng câu hỏi (Practice Engine)** | `PracticeController.java` | `PracticeService.java`<br>`QuestionTypeCatalog.java` | `PracticeAttemptRepository.java`<br>`QuestionRepository.java`<br>`PracticeAttempt.java` | `templates/practice/index.html`<br>`templates/practice/prepare.html`<br>`templates/practice/play.html`<br>`templates/practice/result.html`<br>*(Test: `ReadingPracticeRegressionTest.java`)* |
| **Thi thử Đề chuẩn (Full Mock & Mini Test)** | `LearnerExamController.java` | `LearnerExamService.java`<br>`ExamComposerService.java`<br>`ExamScoreCalculator.java`<br>`Writing5152Evaluator.java`<br>`ExamScoreDTO.java`<br>`QuestionPlayerItem.java` | `ExamRepository.java`<br>`ExamAttemptRepository.java`<br>`ExamItemRepository.java`<br>`Exam.java`<br>`ExamAttempt.java` | `templates/exam/index.html`<br>`templates/exam/play.html`<br>`templates/exam/result.html`<br>`templates/fragments/question-player.html`<br>`templates/fragments/result-review.html`<br>`static/js/question-player.js`<br>`static/css/question-player.css`<br>*(Test: `LearnerExamSingleSkillTest.java`)* |
| **Kỹ năng Viết (Writing 51, 52, 53, 54)** | `WritingController.java` | `Writing5152Evaluator.java`<br>`WritingSubmissionService.java`<br>`WritingSubmissionDTO.java` | `WritingPromptRepository.java`<br>`WritingSubmissionRepository.java`<br>`WritingSubmission.java` | `templates/writing/index.html`<br>`templates/writing/history.html`<br>`templates/writing/feedback.html`<br>`templates/writing/grading-history.html`<br>`templates/fragments/writing-form.html`<br>`static/js/writing-form.js`<br>*(Test: `WritingSubmissionDraftUnitTest.java`)* |
| **Cổng Giảng viên: Chấm bài viết** | `TeacherPortalController.java` | `WritingSubmissionService.java`<br>`WritingSubmissionDTO.java` | `WritingSubmissionRepository.java`<br>`WritingPromptRepository.java` | `templates/teacher/dashboard.html`<br>`templates/teacher/profile.html`<br>`templates/writing/grading-history.html`<br>*(Test: `WritingSubmissionDraftUnitTest.java`)* |
| **Tiến độ học tập & Thống kê năng lực (Progress)** | `LearnerController.java` | `ProgressService.java`<br>`ExamScoreCalculator.java` | `ExamAttemptRepository.java`<br>`PracticeAttemptRepository.java`<br>`ExamAttempt.java` | `templates/progress/index.html`<br>`templates/progress/history.html` |
| **Quản lý nội dung đề thi & Nhập đề** | `ContentExamController.java`<br>`ContentPortalController.java` | `ExamImportService.java`<br>`ExamImportValidator.java`<br>`ExamTemplateBuilder.java`<br>`ExamWorkbookParser.java`<br>`MediaStorageService.java` | `ExamSourceRepository.java`<br>`ImportJobRepository.java`<br>`MediaAssetRepository.java`<br>`QuestionRepository.java` | `templates/content/exam/sources.html`<br>`templates/content/exam/source-detail.html`<br>`templates/content/dashboard.html`<br>*(Test: `SkillExamImportTest.java`)* |
| **Kho lý thuyết: Từ vựng (Vocabulary)** | `TheoryController.java`<br>`ContentVocabularyController.java` | `VocabularyService.java`<br>`VocabularySyncService.java` | `VocabularyRepository.java`<br>`TopicRepository.java`<br>`Vocabulary.java` | `templates/theory/vocabulary.html`<br>`templates/content/vocabulary/index.html` |
| **Kho lý thuyết: Ngữ pháp (Grammar)** | `TheoryController.java` | `GrammarService.java`<br>`GrammarGroupService.java`<br>`GrammarExampleService.java` | `GrammarRepository.java`<br>`GrammarGroupRepository.java`<br>`Grammar.java` | `templates/theory/grammar.html`<br>`templates/theory/grammar-detail.html` |
| **Kho lý thuyết: Hán tự (Hanja)** | `TheoryController.java`<br>`LearnerHanjaController.java`<br>`ContentHanjaController.java` | `HanjaService.java`<br>`HanjaServiceImpl.java` | `HanjaRootRepository.java`<br>`HanjaRootWordRepository.java`<br>`HanjaRoot.java` | `templates/theory/hanja.html`<br>`templates/content/hanja/index.html` |
| **Báo lỗi câu hỏi & Sửa đổi câu hỏi** | `QuestionReportController.java`<br>`QuestionCorrectionController.java`<br>`OperationPortalController.java` | `QuestionReportService.java`<br>`QuestionCorrectionService.java`<br>`OperationService.java` | `QuestionReportRepository.java`<br>`QuestionRevisionRepository.java`<br>`QuestionRevisionRecipientRepository.java` | `templates/operation/dashboard.html`<br>`templates/operation/reports.html`<br>`templates/operation/report-detail.html`<br>`templates/operation/correction.html` |
| **Quản trị người dùng & Phân quyền (Admin)** | `AdminController.java` | `UserService.java`<br>`AdminSystemService.java` | `UserRepository.java`<br>`User.java` | `templates/admin/users.html`<br>`templates/admin/user-detail.html`<br>`templates/admin/roles.html`<br>`templates/admin/dashboard.html` |
| **Gói VIP & Quyền lợi VIP** | `VipController.java` | `UserService.java` | `UserRepository.java`<br>`User.java` | `templates/vip/index.html`<br>`templates/vip/checkout.html` |
| **Hệ thống Thông báo (In-App)** | Được nhúng qua navbar fragment | `NotificationService.java` | `UserNotificationRepository.java`<br>`UserNotification.java` | `templates/fragments/navbar.html` |
| **Điều hướng & Hướng dẫn người dùng (Topbar & Tour)** | — | — | — | `templates/fragments/navbar.html`<br>`templates/fragments/head.html`<br>`templates/fragments/help-modal.html`<br>`static/css/style.css`<br>`static/css/walkthrough-tour.css`<br>`static/js/walkthrough-tour.js` |

---

## 2. Chi Tiết Các Tệp Thành Phần Trọng Yếu Cần Đọc Kỹ

### 2.1. Trình Làm Bài Thi & Luyện Tập (`question-player`)
Khi người dùng báo lỗi liên quan đến giao diện làm bài, chuyển câu, audio, đoạn văn, lưu nháp hoặc phiếu câu hỏi:
1. `src/main/resources/templates/fragments/question-player.html`: Cấu trúc DOM của trình làm bài:
   - Thẻ câu hỏi `.qp-question`, vùng gắn dữ liệu `data-group` để gom nhóm câu dùng chung.
   - **Bảo toàn số thứ tự câu hỏi (`q.no`)**: Luôn sử dụng `q.no` trên header câu hỏi (`.qp-pill`) và các nút ô số (`.qp-number`), không dùng biến đếm cục bộ để bảo toàn số câu khi chuyển kỹ năng (TOPIK I Đọc từ câu 31, TOPIK II Viết từ câu 51).
   - **Hiển thị ngữ liệu trực tiếp**: Đoạn văn, file audio hoặc hình ảnh dùng chung được nhúng trực tiếp và lặp lại trên từng thẻ câu hỏi trong nhóm để người học tiện theo dõi khi cuộn xuống.
   - Nút hành động từng câu: "Chưa chắc", "Báo lỗi câu hỏi", "Kiểm tra" (chỉ hiện khi luyện tập).
   - Nút điều hướng nhóm câu / câu hỏi: "Câu trước", "Câu tiếp".
2. `src/main/resources/static/js/question-player.js`: Bộ điều khiển client:
   - `show(index)`: Điều khiển hiển thị câu hỏi và nhóm câu hỏi.
   - Điều hướng `qp-prev` / `qp-next`: Câu đơn nhảy từng câu lẻ (+/- 1); câu nhóm nhảy theo khối nhóm.
   - `update()`: Đồng bộ trạng thái đáp án, số câu đã làm, cờ đánh dấu câu chưa chắc (`flags_json`).
   - **Cơ chế bỏ chọn đáp án trắc nghiệm không gây xung đột Spring MVC**: Không dùng thẻ hidden rỗng tĩnh trong form. Khi học viên bỏ chọn radio, JS tự động chèn `.qp-empty-input` để form gửi giá trị rỗng cho câu đó mà không làm mất đáp án của các câu đã chọn khác.
   - Xử lý Lưu & Thoát / Tạm dừng:
     - Đề thi (`/exam/play.html`): Ghi nhận chính xác `remainingSeconds` của kỹ năng hiện tại để khôi phục khi làm tiếp.
     - Luyện tập (`/practice/play.html`): Gửi AJAX đến `/practice/save/{attemptId}` hoặc `/practice/pause/{attemptId}` lưu lại `answers_json`, `flags_json`, `elapsedSeconds`.
   - `IntersectionObserver`: Theo dõi cuộn trang để highlight đúng câu trong sidebar phiếu câu hỏi.
   - Xử lý phím tắt (1-4 để chọn đáp án, mũi tên trái/phải để chuyển câu).
3. `src/main/resources/static/css/question-player.css`: Định dạng giao diện trình làm bài, bố cục thẻ câu hỏi, lưới phương án và thanh điều hướng.

### 2.2. Nhập Đề Thi, Sinh Template & Parser Excel (`content/exam`)
Khi người dùng yêu cầu sửa đổi liên quan đến nhập đề, template Excel, bóc tách dữ liệu:
1. `src/main/resources/templates/content/exam/sources.html`:
   - Quản lý danh sách các nguồn đề, tạo nguồn đề mới. Không đặt dropdown tải file mẫu tại đây.
2. `src/main/resources/templates/content/exam/source-detail.html`:
   - Nút cạnh "Xem như học viên": `📥 Tải mẫu toàn bộ đề` (`/content/sources/{id}/template-full`) và `📤 Import excel toàn bộ đề` (`/content/sources/{id}/excel`).
   - Dưới tab kỹ năng (`.skill-tabs`): `📥 Tải mẫu [kỹ năng]` (`/content/sources/{id}/template?skill=...`) và `📤 Import excel [kỹ năng]` (`/content/sources/{id}/excel/skill`).
   - Khung nhập đề bài: hỗ trợ tải ảnh đề bài câu này cho cả Nghe, Đọc và Viết.
   - Khung 4 phương án: hỗ trợ vừa nhập text vừa tải ảnh xem trước đồng thời cho từng phương án (`opt1Image`..`opt4Image`).
   - Kéo thả file nghe MP3 (chỉ chấp nhận định dạng `.mp3`).
   - Lưới ô số câu hỏi (`num-grid`) kết hợp form nhập câu: bảo đảm mỗi câu hỏi được gán một ô số trên lưới.
   - `#tools`: Chỉ quản lý danh sách file âm thanh (.mp3) và hình ảnh đã tải lên.
3. `src/main/java/com/hottopik/service/exam/ExamTemplateBuilder.java`:
   - `buildSkillTemplate(Track, Skill)`: Sinh file Excel mẫu cho từng kỹ năng lẻ (Nghe không có cột `Tên file nghe`, Đọc có `Đoạn văn chung`, Viết có ô chấp nhận cho `㉠` và `㉡`).
   - `buildFullTemplate(Track)`: Sinh file Excel mẫu toàn bộ đề gộp đa sheet (`NGHE`, `DOC`, `VIET`, `DANG_CAU`).
   - Tự động lấy tên dạng câu chuẩn tiếng Việt từ `QuestionTypeCatalog`.
4. `src/main/java/com/hottopik/service/exam/ExamWorkbookParser.java`:
   - Hỗ trợ phân tích cả định dạng đa sheet theo kỹ năng (`NGHE`, `DOC`, `VIET`) và định dạng 2 sheet truyền thống (`QUESTIONS`, `WRITING`), bóc tách câu hỏi, nhóm câu và đáp án.
5. `src/main/java/com/hottopik/service/exam/ExamImportValidator.java`:
   - Kiểm tra hợp lệ dữ liệu đề thi; tự động đối soát file âm thanh từ media library cho câu Nghe, chuyển cảnh báo nếu thiếu audio chứ không chặn kích hoạt đề.
6. `src/main/java/com/hottopik/service/exam/ExamImportService.java`:
   - Xử lý lưu nháp câu hỏi, bóc tách đề từ Excel, nạp vào ngân hàng câu hỏi.
   - Quản lý upload audio hàng loạt (.zip) cho kỹ năng Nghe (`preCheckBulkAudio`, `confirmBulkAudio`, `cancelBulkAudio`) với giải nén đa bảng mã.
   - Quản lý gỡ liên kết và đồng bộ xóa file media (`clearCardAudio`, `deleteMediaBatch`, `deleteAllMedia`).
   - Tự động sửa/chữa lành đường dẫn tệp audio trên đĩa trong `mediaPath`.
7. `src/main/java/com/hottopik/service/exam/MediaStorageService.java`:
   - Lưu trữ và phân giải đường dẫn media cục bộ (`sources/{id}/media`, `sources/{id}/excel`).
   - Sanitize ký tự cấm trên Windows (`[?*:"<>|]`) và cung cấp phương thức xóa tệp an toàn (`deleteStoredSafe`).
8. `src/main/java/com/hottopik/service/exam/MediaResponses.java`:
   - Cấu hình streaming media, hỗ trợ Byte-Range request (HTTP 206 Partial Content) và thiết lập chuẩn xác header `Content-Type: audio/mpeg` không bị chèn charset.
9. `src/main/java/com/hottopik/service/exam/SamplePaperSeeder.java`:
   - Khởi tạo 4 đề thi mẫu cho môi trường H2 local profile (`@Profile("local")`).
10. `src/main/java/com/hottopik/controller/ContentExamController.java`:
    - Cung cấp các endpoint tải template full, tải template theo skill, import full và import skill.
    - Cung cấp API upload audio hàng loạt, upload inline media, xóa media theo mẻ và xóa gỡ liên kết audio câu hỏi.
11. `src/test/java/com/hottopik/service/exam/SkillExamImportTest.java`:
    - Bộ test kiểm thử tự động cho template Excel, import kỹ năng, upload zip đa bảng mã, streaming audio header và xử lý ký tự cấm.

### 2.3. Chấm Điểm Chuẩn & Đánh Giá Bài Viết (`exam` / `writing`)
Khi người dùng yêu cầu chỉnh sửa liên quan đến chấm điểm Đề thi và Writing câu 51-54:
1. `src/main/java/com/hottopik/service/exam/ExamScoreCalculator.java`:
   - Tính điểm chuẩn TOPIK I (Nghe 30 câu / 100đ, Đọc 40 câu / 100đ $\rightarrow$ 200đ) và TOPIK II (Nghe 50 câu / 100đ, Đọc 50 câu / 100đ, Viết 100đ $\rightarrow$ 300đ).
   - Tích hợp tính điểm lịch sử làm bài và xếp loại kết quả (Xuất sắc, Rất tốt, Đạt, Cần cố gắng).
2. `src/main/java/com/hottopik/dto/ExamScoreDTO.java`:
   - DTO truyền tải toàn bộ dữ liệu điểm số, điểm thành phần từng kỹ năng, phản hồi chi tiết các câu Viết 51-54 lên view kết quả.
3. `src/main/java/com/hottopik/service/exam/Writing5152Evaluator.java`:
   - Thuật toán chấm điểm và đánh giá tự động câu 51 và câu 52 theo tiêu chuẩn TOPIK II (2.5đ nội dung + 2.5đ ngôn ngữ/đuôi câu kính ngữ hoặc văn viết cho từng ý `㉠`, `㉡`).
4. `src/main/java/com/hottopik/service/WritingSubmissionService.java` & `src/main/java/com/hottopik/dto/WritingSubmissionDTO.java`:
   - Quản lý nộp bài viết, lưu nháp tự động, tách biệt bài thi và bài luyện tập, chấm điểm của giáo viên, lưu nháp chấm dở (`saveGradingDraft`), từ chối chấm và xử lý khiếu nại (bao gồm 3 mốc thời gian `submittedAt`, `appealedAt`, `appealResolvedAt`).
5. `src/main/resources/templates/writing/feedback.html`, `history.html`, `grading-history.html` & `src/main/resources/templates/teacher/dashboard.html`:
   - `feedback.html`: Giao diện 2 cột đồng nhất (`writing-feedback-grid`) cho toàn bộ trạng thái bài gửi Giáo viên; banner Từ chối chấm & Lịch sử khiếu nại đặt ở trên cùng; phân trang sidebar 10 nhóm/trang giữ nguyên `page` qua `WritingController.feedback`.
   - `teacher/dashboard.html`: Nút "Lưu và thoát", cảnh báo `beforeunload` khi rời trang, modal xác nhận `#unsavedGradingModal` khi nhấn link chuyển trang nếu có dữ liệu chấm dở chưa lưu.
   - Ngắt dòng an toàn (`word-break: break-word; overflow-wrap: anywhere; white-space: pre-wrap;` + `min-width: 0; overflow: hidden;`) trên toàn bộ khung đề bài, bài làm và nhận xét ở cả trang học viên và khu Giáo viên.
6. `src/main/resources/templates/fragments/result-review.html` & `src/main/resources/templates/exam/result.html`:
   - Giao diện xem lại bài thi: tự động ẩn bộ lọc "Câu đúng / Câu sai" khi đề thi là bài thi thuần kỹ năng Viết.
   - **Bộ lọc kỹ năng (`.rv-skill-tabs`)**: Cung cấp thanh tab kỹ năng (Nghe, Đọc, Viết, Tất cả) để lọc danh sách câu hỏi xem lại và danh sách chip số câu tương ứng.

### 2.4. Tiến Độ Học Tập & Biểu Đồ Cột Chồng Full Mock (`progress`)
Khi người dùng yêu cầu chỉnh sửa liên quan đến trang Tiến độ học tập và biểu đồ thi thử:
1. `src/main/java/com/hottopik/service/ProgressService.java`:
   - `buildMockBarStats(userId)`: Quét danh sách bài thi Full Mock đã nộp, nhóm theo `exam.id`, tính điểm qua `ExamScoreCalculator`, chọn ra **Best Attempt** (tổng điểm cao nhất, nộp mới nhất nếu hòa), xuất danh sách `MockBarStat` (tách điểm Nghe, Đọc, Viết, số lần thử `attemptCount`).
   - Phân tích thống kê dạng câu hỏi (`buildTypeStats`) và tính tỷ lệ chính xác.
2. `src/main/resources/templates/progress/index.html`:
   - Render biểu đồ Cột Chồng (Chart.js `stacked: true`) phân chia tỷ trọng điểm từng kỹ năng: Nghe (`#1F6B56`), Đọc (`#2563EB`), Viết (`#D97706`).
   - Nút chuyển Track TOPIK I (trục Y max 200) vs TOPIK II (trục Y max 300).
   - Custom Tooltip hiển thị: Tên đề thi, ngày làm, tổng số lần thử, Điểm tổng (`x/200 đ` hoặc `x/300 đ`), Điểm chi tiết từng kỹ năng và gợi ý click.
   - Bắt sự kiện click trên biểu đồ để điều hướng trực tiếp sang `/ket-qua/{attemptId}` của Best Attempt.
3. `src/main/java/com/hottopik/controller/LearnerController.java`:
   - Mapping `/tien-do`, `/progress` gọi `progressService.load(user.getId())` đẩy `progressData` vào model.

### 2.5. Cầu Nối Hán Việt & Ôn Luyện Từ Vựng (`theory/hanja`)
Khi người dùng yêu cầu chỉnh sửa liên quan đến trang Cầu nối Hán Việt:
1. `src/main/resources/templates/theory/hanja.html`:
   - Cấu trúc layout 4 cột gốc Hán (.hanja-main-layout) đặt `grid-template-columns: 340px minmax(0, 1fr)` kết hợp `minmax(0, 1fr)` cho các ô cell và cuộn nội bộ mượt, chống tràn đè lên bảng chi tiết bên phải.
   - 4 tab chuyên biệt: `Học theo gốc`, `Đoán nghĩa từ mới`, `Từ đã học`, `Cần luyện tập` kèm huy hiệu số lượng thời gian thực.
   - Quản lý từ vựng đã học (`hanjaLearnedWords`) và danh sách cần luyện (`hanjaMistakes`).
   - Bộ sinh đề quiz trắc nghiệm 4 đáp án tùy biến (`generateQuizFromList`) dùng chung cho cả luyện tập từ đã học và từ cần nhớ; tự động gỡ từ làm sai khi người học đánh dấu đã học hoặc luyện tập chọn đúng đáp án.
2. `src/main/java/com/hottopik/controller/TheoryController.java` & `LearnerHanjaController.java`:
   - Mapping `/ly-thuyet/han-viet`, `/theory/hanja` hỗ trợ query param `?tab=learn|guess|learned|mistakes`.
   - Cung cấp API REST `/api/learner/hanja/*` phục vụ nạp danh sách gốc, chi tiết gốc và tạo quiz ngẫu nhiên.
