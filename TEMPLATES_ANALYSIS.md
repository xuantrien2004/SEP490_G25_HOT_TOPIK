# Báo cáo khảo sát Templates & Static Resources

**Dự án:** HOT TOPIK (SEP490_G25)  
**Phạm vi:** `src/main/resources/templates/` (69 files) + `static/` (5 files)  
**Tổng:** 245 files, ~24,900 dòng templates, ~1,000 dòng CSS/JS

---

## 1. Shared Fragments (5 files)

### `fragments/head.html` (29 lines)
- Fragment: `head(pageTitle)`
- Load: Google Fonts (Baloo 2, Nunito, Be Vietnam Pro, Noto Sans KR), Bootstrap 5.3.3, Bootstrap Icons
- CSS: `/css/variables.css`, `/css/style.css`
- JS: `/js/ssak.js`, `/js/app.js`

### `fragments/navbar.html` (194 lines)
- Fragment: `navbar(activePage)`
- **7 mục điều hướng cố định:** Hôm nay, Lộ trình, Luyện tập, Đề thi, Lý thuyết, Tiến độ, Về TOPIK
- **Quick stats:** Số ngày đã học, Xu, Chuông thông báo (dropdown 3 thông báo mẫu cố định), VIP badge
- **User dropdown:** hiển thị role (Admin/Content Manager/Operation/Mentor), lối tắt vào cổng nghiệp vụ theo role
- **Mobile bottom nav:** 6 icon (5 mục chính + Thêm)
- Dữ liệu binding: `session.LOGGED_IN_USER.{displayName, email, role, studiedDays, coinBalance, vip, admin, contentManager, operationManager, mentor}`
- URLs: `/hom-nay`, `/lo-trinh`, `/luyen-tap`, `/de-thi`, `/ly-thuyet`, `/tien-do`, `/ve-topik`, `/xu`, `/thong-bao`, `/vip`, `/goi-cua-toi`, `/ho-so`, `/cai-dat`, `/bai-cham`, `/admin`, `/content`, `/operation`, `/teacher`, `/logout`

### `fragments/footer.html` (68 lines)
- Fragment: `footer`
- 4 cột link: Góc học viên, Cẩm nang TOPIK, Hỗ trợ & Chính sách
- URLs: `/faq`, `/terms`, `/ho-tro` (chưa map controller)
- Không binding dữ liệu động

### `fragments/ssak.html` (137 lines)
- Fragment: `mascot(mood, stage, size, alt)`
- Render SVG linh vật Bé Ssak: 16 biểu cảm (4 mood × 4 stage)
- Không có logic phức tạp

### `fragments/content-shell.html` (314 lines)
- Fragment: `styles`, `sidebar(active)`, `header(title, subtitle)`, `flash`
- **Sidebar role badge:** hiển thị "📝 Content Manager" hoặc "🔒 Admin · chỉ xem"
- 8 mục điều hướng: Dashboard, Từ vựng, Ngữ pháp, Hán-Việt, Thư viện đề, Nhập đề, Ngân hàng câu hỏi, Thư viện khuôn, 42 dạng câu, Báo lỗi, Nhật ký
- URLs content: `/content`, `/content/vocabulary`, `/content/grammar`, `/content/hanja`, `/content/exams`, `/content/sources`, `/content/questions`, `/content/templates`, `/content/question-types`, `/content/profile`
- Binding: `currentUser.role`, `canEdit`, `flashOk`, `flashError`, `flashInfo`

---

## 2. Cổng nghiệp vụ (4 roles, inline sidebar)

### Admin (2 files)

**`admin/dashboard.html`** (767 lines, 85 binds)
- Sidebar inline (không dùng fragment), 7 tab: Hộp duyệt (5), Quản lý nhân sự (3), Cấu hình AI, Break-glass Mode, Hồ sơ Admin
- Actions: `POST /admin/approval/decide`, `POST /admin/staff/toggle-lock`, `POST /admin/ai-budget/update`, `POST /admin/break-glass/toggle`
- Model: `pendingApprovals` (list), `allStaff` (list), `aiConfig`, `breakGlassMode`
- **Dữ liệu mẫu cố định:** 2 approval, 4 staff
- **⚠️ Đáng ngờ:** dữ liệu in-memory, không gọi service, fetch() gọi endpoint nhưng không xử lý response

**`admin/vocabulary.html`** (1881 lines, 102 binds)
- Controller: `@RequestMapping("/admin/vocabulary")` (cùng `/content/vocabulary`)
- **Giao diện giống hệt content/vocabulary**, chỉ khác role badge "Admin · chỉ xem"
- 11 fetch() endpoints: `/admin/vocabulary/{bulk-toggle, topic/toggle, topic/create, topic/update, topic/delete, save, toggle/{id}, bulk-create}`
- Model: `topics` (list), `vocabularies` (list), `levelFilter`, `topicFilter`
- **⚠️ Trùng lặp:** nội dung giống content manager 100%

### Content Manager (7 files, dùng `content-shell` fragment)

**`content/dashboard.html`** (758 lines, 38 binds)
- 4 tab: Tổng quan, Ngân hàng nội dung (4 metric tiles), Báo lỗi học viên (2 reports mẫu), Nhật ký thao tác (3 logs mẫu)
- Actions: `POST /content/report/resolve`, `POST /content/report/reject`
- **Dữ liệu mẫu cố định:** vocabularyCount=1482, grammarCount=342, hanjaRootsCount=32, examsCount=8
- **⚠️ Đáng ngờ:** metric không gọi service, report/log in-memory

**`content/grammar.html`** (1004 lines, 61 binds)
- 3 cột: Grammar types (tree), Grammar items (list), Detail form
- 7 fetch() endpoints: `/content/grammar/{toggle, save, delete-type, group/save, bulk-create, update}`
- Model: `grammarTypes` (tree), `grammarItems` (list), `selectedType`
- Binding: `th:each="type : ${grammarTypes}"`, `th:each="item : ${grammarItems}"`
- **✅ Gắn dữ liệu thật:** controller trả model đầy đủ

**`content/hanja.html`** (2092 lines, 104 binds) + **`content/hanja-words.html`** (1226 lines, 84 binds)
- REST API: `PUT /content/hanja/api/{id}`, `DELETE /content/hanja/api/{id}`, `POST /content/hanja/api/{id}/words`, `PUT/DELETE /content/hanja/api/{id}/words/{vocabId}`
- 9 fetch() endpoints, 2 modal import Excel
- Model: `hanjaRoots` (list), `selectedRoot`, `selectedRoot.relatedWords` (list)
- **✅ Gắn dữ liệu thật:** controller ContentHanjaController đầy đủ

**`content/profile.html`** (381 lines, 15 binds)
- Form cá nhân Content Manager: tên, email, mật khẩu
- Actions: `POST /content/profile/update`, `POST /content/profile/change-password`
- Model: `currentUser.{displayName, email}`

**Exam module (10 files trong `content/exam/`)**
- `types.html` (69 lines): bảng 42 dạng câu TOPIK I/II, read-only
- `templates.html` (67 lines) + `template-detail.html` (69 lines): thư viện khuôn câu hỏi
- `sources.html` (103 lines) + `source-new.html` (79 lines) + `source-detail.html` (497 lines): nhập đề 4 bước (tạo bộ đề, chọn dạng, điền câu, tải file, activate)
- `library.html` (90 lines) + `composer.html` (389 lines): thư viện đề và trình soạn khung đề
- `questions.html` (313 lines): ngân hàng câu hỏi, 8 bộ lọc, thao tác hàng loạt
- `preview.html` (73 lines): xem trước đề
- **Actions:** 30+ endpoints dưới `/content/{sources, exams, questions, templates}`
- **✅ Gắn dữ liệu thật:** module exam có service đầy đủ (ExamImportService, ExamComposerService, QuestionBankService)

### Operation Manager (1 file)

**`operation/dashboard.html`** (851 lines, 79 binds)
- Sidebar inline, 7 tab: Quản lý người dùng, Đơn hàng & Hoàn tiền, Đề xuất giá, Duyệt đánh giá, Gửi thông báo, Dashboard tổng, Hồ sơ Operation
- 5 fetch() endpoints: `/operation/{users/toggle-lock, orders/request-refund, prices/propose, reviews/toggle-approve, announcements/send}`
- Model: `users` (list), `orders` (list), `reviews` (list)
- **Dữ liệu mẫu cố định:** 4 users, 3 orders, 2 reviews
- **⚠️ In-memory:** không persist database

### Teacher/Mentor (1 file)

**`teacher/dashboard.html`** (583 lines, 104 binds)
- 3 tab: Hàng chờ (queue), Đang chấm (grading), Đã hoàn tất (completed)
- Actions: `POST /teacher/claim`, `POST /teacher/grade`, `POST /teacher/reply-ask`
- Model: `currentTab`, `queueSubmissions` (list), `gradingSubmissions` (list), `completedReviews` (list)
- **Dữ liệu mẫu cố định:** 2 queue, 1 grading, 3 completed
- **⚠️ In-memory:** WritingSubmissionService lưu danh sách trong bộ nhớ

---

## 3. Guest (chưa đăng nhập, 5 files)

**`guest/home.html`** (244 lines, 0 binds)
- Landing page: DEBUT banner, 4 sections (Cách học, Dạng câu, Bảng giá, CTA), footer
- **100% static mockup**, không binding dữ liệu
- URLs: `/#s-cachhoc`, `/#s-dangcau`, `/#s-banggia`, `/ve-topik`, `/lam-thu`, `/login`, `/register`

**`guest/trial.html`** (199 lines, 0 binds)
- Làm thử 8 câu TOPIK miễn phí (không cần đăng nhập)
- **100% static mockup**, câu hỏi mẫu hard-coded
- **⚠️ URL `/lam-thu`:** controller trả view nhưng không có attempt thật

**`info/faq.html`, `info/terms.html`** (40 lines mỗi file)
- FAQ và Điều khoản dịch vụ
- **100% static mockup**, dùng navbar

**`auth/login.html`** (339 lines)
- 3 tab: Đăng nhập, Đăng ký, Quên mật khẩu
- Actions: `POST /login`, `POST /register`, `POST /forgot-password`
- 2 fetch(): `/auth/google-client-id`, `POST /auth/google` (Google OAuth)
- Model: `email`, `password`, `fullName`, `errorMessage`
- **✅ Gắn dữ liệu thật:** AuthController xử lý đầy đủ

**`auth/verify-otp.html`, `auth/reset-password-otp.html`, `auth/reset-password.html`** (77–120 lines)
- Xác thực OTP và đặt lại mật khẩu
- Actions: `POST /verify-otp`, `POST /reset-password-otp`, `POST /reset-password`
- **✅ Gắn dữ liệu thật**

---

## 4. Learner (học viên đã đăng nhập, 39 files)

### Onboarding & Placement (3 files)

**`onboarding/index.html`** (181 lines, 0 binds)
- Chọn cách bắt đầu: Tự khai cấp độ hoặc Làm bài kiểm tra 15 phút
- Action: `GET /placement/start`
- **100% static mockup**

**`placement/test.html`** (225 lines, 0 binds)
- Bài kiểm tra trình độ thích ứng 15 phút, đồng hồ đếm ngược, audio tự phát 1 lần
- **100% static mockup**, câu hỏi mẫu hard-coded, không submit thật

**`placement/result.html`** (198 lines, 0 binds)
- Báo cáo kết quả kiểm tra: điểm, cấp độ đề xuất, nút tạo lộ trình
- Action: `GET /lo-trinh/tao`
- **100% static mockup**

### Dashboard & Profile (7 files trong `learner/`)

**`learner/home.html`** (94 lines, 1 bind)
- Trang chủ học viên `/trang-chu`: chào mừng, nút vào Dashboard
- **Dữ liệu mẫu:** `session.LOGGED_IN_USER.fullName`
- **⚠️ Thiếu logic:** không hiển thị bài tập hôm nay

**`learner/dashboard.html`** (265 lines, 2 binds)
- Dashboard `/hom-nay`: linh vật Ssak, 2 bài tập ngày (hard-coded), tiến độ tuần
- **Dữ liệu mẫu:** `session.LOGGED_IN_USER.{displayName, studiedDays}`
- **⚠️ Static mockup:** bài tập không lấy từ roadmap

**`learner/profile.html`** (449 lines, 12 binds)
- Hồ sơ cá nhân `/ho-so`: form thông tin, đổi mật khẩu, VIP badge
- Actions: `POST /api/user/update-profile`, `POST /api/user/change-password` (fetch)
- Model: `user.{displayName, email, currentLevel, targetLevel, vip, vipPackage}`
- **✅ Gắn dữ liệu thật:** LearnerController xử lý

**`learner/settings.html`** (109 lines), **`learner/notifications.html`** (233 lines), **`learner/coins.html`** (153 lines), **`learner/mentor-reviews.html`** (119 lines)
- **100% static mockup**, không binding dữ liệu
- URLs: `/cai-dat`, `/thong-bao`, `/xu`, `/bai-cham`

### Roadmap (4 files)

**`roadmap/create.html`** (179 lines, 4 binds)
- Form tạo lộ trình: chọn cấp độ xuất phát, target level, thời gian
- Action: `POST /lo-trinh/tao`
- Model: `entryMode`, `startLevel`
- **⚠️ Form không submit:** action trả về view roadmap/index nhưng không persist lộ trình

**`roadmap/index.html`** (642 lines, 19 binds)
- Lộ trình 4 chặng (84 ngày), lưới lịch tuần, bài tập mỗi ngày
- **Dữ liệu mẫu hard-coded:** 19 biến JS (`${data.week}`, `${task.label}`, `${dayNum}`, v.v.)
- **⚠️ Static mockup:** không lấy từ service

**`roadmap/adjust.html`** (135 lines), **`roadmap/retrospective.html`** (165 lines)
- Điều chỉnh lộ trình và nhìn lại tuần
- **100% static mockup**

### Practice & Exam (6 files)

**`practice/index.html`** (466 lines, 0 binds)
- Ngân hàng luyện tập: 4 kỹ năng, lọc theo dạng câu, nút Bắt đầu
- **100% static mockup**, danh sách bài hard-coded

**`practice/prepare.html`** (44 lines), **`practice/play.html`** (380 lines), **`practice/result.html`** (148 lines)
- Màn chuẩn bị, làm bài, kết quả
- **100% static mockup**, câu hỏi mẫu hard-coded
- **⚠️ URLs không map:** `/practice/prepare`, `/lam-bai/{attemptId}`, `/ket-qua/{attemptId}` trả view nhưng không có attempt thật

**`practice/mini-test.html`** (380 lines, 0 binds)
- Thi thử rút gọn 15 phút
- **100% static mockup**, trùng lặp với `play.html`

**`practice/writing.html`** (214 lines)
- Luyện viết TOPIK (câu 51–54), giấy kẻ ô Wonkuji
- **Redirect đến `/writing`**

**`exam/index.html`** (474 lines, 13 binds)
- Thư viện đề thi: Full Mock, Mini Test, lọc theo track
- **Dữ liệu mẫu:** 13 biến JS inline (`${item.title}`, `${item.badge}`, `${buttonsHtml}`, v.v.)
- **⚠️ Static mockup:** danh sách đề hard-coded

### Theory (9 files)

**`theory/index.html`** (447 lines, 12 binds)
- Trung tâm lý thuyết: ngữ pháp, từ vựng, flashcard, quiz
- **Dữ liệu mẫu:** 12 biến JS (`${currentLevel}`, `${item.name}`, `${item.count}`, v.v.)
- **⚠️ Static mockup:** danh mục hard-coded

**`theory/vocabulary.html`** (178 lines), **`theory/grammar.html`** (91 lines), **`theory/flashcard.html`** (114 lines), **`theory/quiz.html`** (116 lines), **`theory/review.html`** (128 lines), **`theory/unlearned.html`** (101 lines)
- **100% static mockup**, không binding dữ liệu

**`theory/hanja.html`** (683 lines, 32 binds)
- Học Hán-Việt: danh sách bộ thủ, quiz interactive
- 1 fetch(): `GET /api/learner/hanja/quiz?count=10`
- **✅ Gắn dữ liệu thật:** LearnerHanjaController trả JSON quiz

**`theory/special.html`** (388 lines, 18 binds)
- Học phát âm đặc thù (consonant, linking, v.v.)
- **Dữ liệu mẫu:** 18 biến JS inline
- **⚠️ Static mockup**

### Writing (2 files)

**`writing/index.html`** (486 lines, 8 binds)
- Luyện viết TOPIK: chọn câu 51–54, giấy kẻ ô, nút Gửi AI chấm/Gửi Mentor
- Action: `POST /writing/submit-mentor`
- Model: `currentQuestion`, `mySubmissionsCount`, `pageTitle`
- **⚠️ Inline style dài:** 4 biến `${currentQuestion == 51 ? 'style...' : 'style...'}` (mỗi biến ~300 ký tự)

**`writing/feedback.html`** (326 lines, 69 binds)
- Xem kết quả chấm: điểm, nhận xét AI/Mentor, form hỏi lại
- Actions: `POST /writing/ask`, `POST /writing/rate`, `POST /writing/appeal`
- Model: `submission.{questionNumber, text, aiScore, aiAnalysis, mentorFeedback, status}`, `th:each="star : ${#numbers.sequence(1, 5)}"`
- **✅ Gắn dữ liệu thật:** WritingController xử lý

### Progress & VIP (6 files)

**`progress/index.html`** (434 lines), **`progress/history.html`** (118 lines)
- Biểu đồ tiến độ, lịch sử làm bài
- **100% static mockup**, biểu đồ mẫu hard-coded
- **⚠️ URL `/viet/sub-101`:** không map controller

**`vip/index.html`** (214 lines), **`vip/checkout.html`** (261 lines), **`vip/result.html`** (99 lines), **`vip/my-package.html`** (135 lines)
- Bảng giá VIP, thanh toán QR, kết quả, gói của tôi
- **100% static mockup**

**`topik/index.html`** (414 lines)
- Cẩm nang TOPIK: cấu trúc bài thi, thang điểm, tips
- **100% static mockup**

**`profile/index.html`** (531 lines, 8 binds)
- Hồ sơ (trùng `learner/profile.html`)
- **⚠️ Trùng lặp:** 2 file giống nhau

---

## 5. Static Assets (5 files, 1172 lines)

### CSS (3 files, 1007 lines)

**`static/css/variables.css`** (54 lines)
- Biến màu Bé Ssak: `--ssak-primary: #2E7D67`, `--ssak-mint: #E3F2EA`, `--ssak-honey: #F4C45A`, `--ssak-bg: #F8F6EF`, v.v.
- Typography: `--font-heading: 'Baloo 2'`, `--font-body: 'Nunito'`, `--font-korean: 'Noto Sans KR'`
- Spacing, radius, shadow

**`static/css/style.css`** (770 lines)
- Global styles: body, buttons, badges, cards, navbar, footer, mobile nav
- Learner-specific: dashboard tiles, question palette, audio player, writing grid
- **10 sections:** Base, Topbar/Navbar, Buttons & Badges, Cards, Dashboard, User Profile, Question Palette, Audio Player, Footer, Mobile Nav

**`static/css/content-admin.css`** (183 lines)
- Content Manager & Admin sidebar: `.cm-shell`, `.cm-side`, `.cm-nav`, `.cm-card`, `.cm-table`
- Dark sidebar (#24363D), active link (#2E7D67)

### JavaScript (2 files, 165 lines)

**`static/js/ssak.js`** (30 lines)
- Khai báo `SSAK_SVGS` object với 16 function return SVG string
- Mỗi function: `SSAK_SVGS["happy_seed"] = function(w, h, alt) { return "<svg>...</svg>"; }`
- **⚠️ Không có render logic:** fragment `ssak.html` không gọi JS này

**`static/js/app.js`** (135 lines)
- 6 functions: `toggleBellDropdown()`, `toggleUserDropdown()`, `markAllNotificationsRead()`, `showSsakToast(message, duration)`, `playSound(type)`
- Event listener: click outside đóng dropdown
- Toast: hiển thị thông báo tạm thời (3.5s)
- **⚠️ Sound không implement:** `playSound('correct')` chỉ có `console.log`

---

## 6. Binding Data vs Static Mockup

### ✅ Gắn dữ liệu thật (controller + service)

| Template | Controller | Model binds | Service |
|---|---|---|---|
| `auth/login.html` | AuthController | `email`, `password`, `errorMessage` | UserService |
| `content/grammar.html` | ContentPortalController | `grammarTypes`, `grammarItems` | GrammarService |
| `content/hanja.html` | ContentHanjaController | `hanjaRoots`, `selectedRoot.relatedWords` | HanjaServiceImpl |
| `content/exam/*` | ContentExamController | `sources`, `exams`, `questions`, `templates` | ExamImportService, ExamComposerService, QuestionBankService |
| `learner/profile.html` | LearnerController | `user.{displayName, email, currentLevel}` | UserService |
| `theory/hanja.html` | LearnerHanjaController | quiz JSON (fetch) | HanjaServiceImpl |
| `writing/feedback.html` | WritingController | `submission.{text, aiScore, mentorFeedback}` | WritingSubmissionService |
| `teacher/dashboard.html` | TeacherPortalController | `queueSubmissions`, `gradingSubmissions` | WritingSubmissionService (in-memory) |
| `admin/vocabulary.html` | ContentVocabularyController | `topics`, `vocabularies` | VocabularyService |

### ⚠️ Static mockup (controller trả view nhưng không có dữ liệu thật)

| Template | Controller mapping | Mockup data | Issue |
|---|---|---|---|
| `guest/home.html` | HomeController `/` | 0 binds | Landing page OK |
| `guest/trial.html` | HomeController `/lam-thu` | 8 câu hard-coded | Không có attempt thật |
| `learner/dashboard.html` | LearnerController `/hom-nay` | 2 bài mẫu cố định | Không lấy từ roadmap service |
| `roadmap/index.html` | RoadmapController `/lo-trinh` | 19 biến JS inline | Không lấy từ service |
| `practice/index.html` | PracticeController `/luyen-tap` | Danh sách bài hard-coded | Không lấy từ question bank |
| `practice/play.html` | PracticeController `/lam-bai/{attemptId}` | Câu hỏi mẫu | attemptId không tồn tại |
| `practice/result.html` | PracticeController `/ket-qua/{attemptId}` | Kết quả mẫu | attemptId không tồn tại |
| `exam/index.html` | PracticeController `/de-thi` | 13 biến JS inline | Không lấy từ exam service |
| `placement/test.html` | OnboardingController `/placement` | Câu hỏi mẫu | Không submit thật |
| `theory/index.html` | TheoryController `/ly-thuyet` | Danh mục hard-coded | Không lấy từ service |
| `admin/dashboard.html` | AdminController `/admin` | 2 approval mẫu | In-memory, không persist |
| `operation/dashboard.html` | OperationPortalController `/operation` | 4 users mẫu | In-memory |

### ⚠️ Không map controller

| URL trong template | Issue |
|---|---|
| `/ho-tro` | Footer link, không có @GetMapping |
| `/placement/start` | Onboarding form action, không có mapping |
| `/viet/sub-101` | Progress history link, không có mapping |
| `/ly-thuyet/tu-vung/moi-truong` | Theory quiz link, không có mapping |

---

## 7. Vấn đề đáng ngờ (Suspicious Issues)

### 7.1. Trùng lặp code

1. **`learner/profile.html` vs `profile/index.html`:** 2 file giống nhau, URL `/ho-so` map đến cả 2
2. **`admin/vocabulary.html` vs `content/vocabulary.html`:** Admin dùng chung controller `/admin/vocabulary` và `/content/vocabulary`, giao diện 100% giống nhau
3. **`practice/play.html` vs `practice/mini-test.html`:** Cấu trúc làm bài trùng lặp

### 7.2. URL không khớp controller

- **navbar:** `/xu`, `/thong-bao`, `/bai-cham`, `/goi-cua-toi`, `/cai-dat` → controller chỉ map `/ho-so`, `/today`, `/progress`
- **footer:** `/ho-tro` → không có mapping
- **form:** `/placement/start` → không có mapping

### 7.3. Dữ liệu in-memory không persist

- **Admin:** approval queue, staff list (AdminController)
- **Operation:** users, orders, reviews (OperationPortalController)
- **Teacher:** writing submissions (WritingSubmissionService)
- **Roadmap:** lộ trình không lưu database

### 7.4. Inline JavaScript dài trong template

- **`writing/index.html`:** 4 biến `${currentQuestion == 51 ? 'style...' : 'style...'}`, mỗi biến ~300 ký tự (nên tách ra CSS class)
- **`theory/index.html`, `theory/special.html`, `roadmap/index.html`, `exam/index.html`:** 12–19 biến JS inline (`${...}`)

### 7.5. CSRF protection

- **Không có:** template không có `<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">` trong form POST
- **Spring Security:** nếu enable CSRF, form sẽ fail 403

### 7.6. Fragment `ssak.html` không dùng `ssak.js`

- **`ssak.js`:** khai báo 16 function return SVG string
- **`ssak.html`:** render SVG trực tiếp trong fragment, không gọi JS
- **→ `ssak.js` thừa**

### 7.7. Navbar thông báo hard-coded

- **navbar:** dropdown bell có 3 thông báo mẫu cố định, không lấy từ service

---

## 8. Thiết kế UI consistency

### ✅ Điểm tốt

1. **Design system nhất quán:** biến CSS Bé Ssak (`variables.css`) dùng xuyên suốt
2. **Typography scale:** `clamp()` responsive font size
3. **Component tokens:** buttons, badges, cards đều dùng class `.ssak-*`
4. **Fragment reuse:** navbar, footer, head dùng chung
5. **Sidebar consistency:** content-shell fragment cho 7 trang content manager

### ⚠️ Điểm cần cải thiện

1. **Inline style nhiều:** `writing/index.html`, `roadmap/index.html` có style dài trong template thay vì CSS class
2. **Sidebar không dùng fragment:** admin, operation, teacher tự viết inline sidebar (275 lines mỗi file)
3. **Mobile nav:** chỉ có navbar có mobile bottom nav, các trang khác không responsive
4. **Color palette:** không theo design_sense (dark tonal ladder), vẫn dùng flat colors

---

## 9. Tổng kết

### Phân bổ templates theo mức độ hoàn thiện

| Nhóm | Files | Gắn dữ liệu thật | Static mockup | Không map |
|---|---|---|---|---|
| **Auth** | 4 | 4 | 0 | 0 |
| **Guest** | 5 | 0 | 5 | 1 (/ho-tro) |
| **Learner** | 39 | 4 | 35 | 3 |
| **Content Manager** | 13 | 13 | 0 | 0 |
| **Admin** | 2 | 1 | 1 | 0 |
| **Operation** | 1 | 0 | 1 | 0 |
| **Teacher** | 1 | 1 (in-memory) | 0 | 0 |
| **Fragments** | 5 | — | — | — |
| **Tổng** | **70** | **23** | **42** | **4** |

### Tỷ lệ hoàn thiện

- **Content Manager:** 100% hoàn thiện (exam module có service đầy đủ)
- **Auth:** 100% hoàn thiện
- **Learner:** ~10% hoàn thiện (chỉ profile, writing feedback, theory hanja)
- **Admin/Operation/Teacher:** ~30% hoàn thiện (in-memory, không persist)

### Ưu tiên phát triển tiếp

1. **Roadmap service:** persist lộ trình, generate bài tập hàng ngày
2. **Practice service:** lấy câu hỏi từ question bank, tạo attempt, chấm tức thì
3. **Exam service:** mini test, full mock từ exam composer
4. **Dashboard service:** lấy bài tập hôm nay từ roadmap
5. **Notification service:** thay thế 3 thông báo mẫu trong navbar
6. **Profile service:** map URLs `/xu`, `/thong-bao`, `/bai-cham`, `/cai-dat`

---

**Kết luận:** Dự án có 70 template files, trong đó 23 files gắn dữ liệu thật (33%), 42 files static mockup (60%), 4 URLs không map controller (6%). Module exam (Content Manager) hoàn thiện nhất, learner features còn thiếu nhiều service logic.
