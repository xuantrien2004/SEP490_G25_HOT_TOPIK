# 🌿 HOT TOPIK — Korean TOPIK Learning System (SEP490_G25)

> **Đồ án tốt nghiệp Fall 2026 · Group SEP490_G25**  
> Dự án hệ thống luyện thi TOPIK thông minh với linh vật **Bé Ssak (싹 - mầm cây)** và phương pháp học ngắt quãng không áp lực.

---

## 🛠️ Công nghệ sử dụng (Tech Stack)

- **Backend:** Java 25, Spring Boot 3.5.16 (Spring MVC, Spring Data JPA, DevTools)
- **Frontend:** Thymeleaf Template Engine, HTML5, CSS3, JavaScript ES6+, Bootstrap 5.3 & Bootstrap Icons
- **Database:** Microsoft SQL Server (tương thích cơ sở dữ liệu `FALL2026_SEP490_G25` trong file `HOT_TOPIK_3.sql`)
- **Build Tool:** Maven (sẵn Maven Wrapper `./mvnw`, `mvnw.cmd`)

---

## 🎨 Design System: Concept Bé Ssak (Lấy từ prototype `HotTopik_Prototype (11)`)

Dự án thừa hưởng toàn bộ ngôn ngữ thiết kế **Bé Ssak** từ bản prototype:
- **Màu sắc chủ đạo:**
  - `Xanh lá chính (Primary)`: `#2E7D67` (nút chính, thanh tiến độ)
  - `Xanh lá đậm`: `#1F6B56`
  - `Mint nhạt`: `#E3F2EA` (thẻ bài mới, hoàn thành)
  - `Kem nền (Warm Canvas)`: `#F8F6EF` (dịu mắt, không gây căng thẳng khi nhìn lâu)
  - `Mật ong (Honey/Gold)`: `#F4C45A` / `#FBF0D3` (thưởng, bài làm bù, điểm nhấn)
  - `Xanh trời nhạt (Sky)`: `#E4EEF5` (bài ôn tập, gợi ý)
  - `Đào / Nâu đất (Peach / Terracotta)`: `#FBE6DA` / `#9C4A2E` (dùng để gợi ý sửa lỗi thay cho màu đỏ gắt)
- **Typography:**
  - Tiêu đề: `'Baloo 2', 'Nunito', sans-serif`
  - Nội dung: `'Nunito', 'Be Vietnam Pro', sans-serif`
  - Tiếng Hàn: `'Noto Sans KR', sans-serif`
- **Linh vật Bé Ssak:** 16 biểu cảm SVG vector đầy đủ theo 4 tâm trạng (`happy`, `cheer`, `think`, `sleep`) × 4 giai đoạn (`seed`, `sprout`, `young`, `bloom`).

---

## 📁 Cấu trúc thư mục dự án

```text
D:\SEP490_G25\HotTopik\
├── .mvn/wrapper/                  # Cấu hình Maven Wrapper
├── mvnw, mvnw.cmd                 # Script chạy Maven không cần cài trước
├── pom.xml                        # File cấu hình dependencies & build
├── README.md                      # Hướng dẫn chi tiết
└── src/
    ├── main/
    │   ├── java/com/hottopik/
    │   │   ├── HotTopikApplication.java  # Main Spring Boot Application
    │   │   ├── config/                  # Cấu hình bảo mật, web MVC
    │   │   ├── controller/              # Spring MVC Controllers
    │   │   │   ├── HomeController.java        # Trang chủ & thông tin (/)
    │   │   │   ├── AuthController.java        # Đăng nhập, đăng ký (/login)
    │   │   │   ├── OnboardingController.java  # Chọn xuất phát điểm, test 15p (/onboarding)
    │   │   │   ├── LearnerController.java     # Dashboard hôm nay (/today, /dashboard)
    │   │   │   ├── RoadmapController.java     # Lộ trình & Lịch học (/roadmap)
    │   │   │   ├── PracticeController.java    # Luyện tập & Đề thi (/practice)
    │   │   │   ├── WritingController.java     # Luyện viết TOPIK có AI chấm (/writing)
    │   │   │   ├── TheoryController.java      # Lý thuyết, Flashcard, Ngữ pháp (/theory)
    │   │   │   ├── VipController.java         # Bảng giá & gói VIP (/vip)
    │   │   │   └── AdminController.java       # Cổng Quản trị & Hộp duyệt đề (/admin)
    │   │   ├── dto/                     # Data Transfer Objects
    │   │   ├── entity/                  # JPA Entities
    │   │   ├── repository/              # Spring Data JPA Repositories
    │   │   └── service/                 # Business Logic Services
    │   └── resources/
    │       ├── application.properties   # Cấu hình cổng, Thymeleaf, SQL Server
    │       ├── static/
    │       │   ├── css/
    │       │   │   ├── variables.css    # Biến màu, font, bo góc của Bé Ssak
    │       │   │   └── style.css        # Toàn bộ CSS các thành phần UI
    │       │   └── js/
    │       │       ├── app.js           # Xử lý thông báo, toast, âm thanh
    │       │       └── ssak.js          # Client-side render linh vật Bé Ssak
    │       └── templates/
    │           ├── fragments/           # Thành phần dùng chung (head, navbar, footer, ssak)
    │           ├── guest/home.html      # Trang chủ (MainV2)
    │           ├── auth/login.html      # Đăng nhập & Đăng ký (DangNhapV2)
    │           ├── onboarding/          # Chọn cách bắt đầu (OnboardingV2)
    │           ├── placement/           # Kiểm tra trình độ & Kết quả (KiemTraV2, PlacementKetQuaV2)
    │           ├── learner/             # Cổng Hôm nay của học viên (HomNayV2)
    │           ├── roadmap/             # Lộ trình học 4 chặng & Lưới lịch (LoTrinhV2, DieuChinhV2, NhinLaiTuanV2)
    │           ├── practice/            # Ngân hàng đề, Làm bài, Kết quả (LuyenTapV2, LamBaiV2, KetQuaV2)
    │           ├── writing/             # Luyện Viết TOPIK + AI chấm (VietV2)
    │           ├── theory/              # Lý thuyết, Flashcards, Quiz từ vựng (LyThuyetV2, TuVungV2, NguPhapV2)
    │           ├── progress/            # Báo cáo năng lực & Tiến độ (TienDoV2)
    │           ├── vip/                 # Gói VIP & Thanh toán QR (VIPV2)
    │           ├── profile/             # Hồ sơ cá nhân & Kho Xu (HoSoV2)
    │           ├── admin/               # Quản trị & Hộp duyệt nội dung (AdminV2)
    │           └── info/                # FAQ, Điều khoản & Chính sách (ThongTinV2)
```

---

## 🚀 Hướng dẫn khởi chạy dự án

### Cách 1: Chạy trực tiếp từ dòng lệnh (Terminal / PowerShell)

Trong thư mục `D:\SEP490_G25\HotTopik`:
```powershell
# Chạy với Maven Wrapper có sẵn trong project:
.\mvnw.cmd spring-boot:run
```

Sau khi ứng dụng khởi động thành công, mở trình duyệt truy cập:
👉 **`http://localhost:8080/`**

### Cách 2: Mở trong IntelliJ IDEA hoặc VS Code

1. Mở **IntelliJ IDEA** (hoặc **VS Code**).
2. Chọn **Open** và dẫn tới thư mục: `D:\SEP490_G25\HotTopik`.
3. IntelliJ sẽ tự động nhận diện file `pom.xml` và tải các thư viện Maven.
4. Mở file `src/main/java/com/hottopik/HotTopikApplication.java` và bấm nút **Run** (phím tắt `Shift + F10`).

---

## 🗄️ Cấu hình Database SQL Server

File script cơ sở dữ liệu gốc nằm tại: `D:\SEP490_G25\HOT_TOPIK_3.sql` (tên database: `FALL2026_SEP490_G25`).

1. Chạy script SQL Server Management Studio (SSMS) để tạo database:
   ```sql
   CREATE DATABASE FALL2026_SEP490_G25;
   -- Sau đó chạy toàn bộ nội dung file HOT_TOPIK_3.sql
   ```
2. Mở file `D:\SEP490_G25\HotTopik\src\main\resources\application.properties`:
   - Bỏ comment các dòng cấu hình DataSource SQL Server.
   - Nhập đúng username (`sa`) và mật khẩu SQL Server của máy bạn.
   - Bỏ dòng `spring.autoconfigure.exclude` để kích hoạt kết nối database thực tế.

---

## 🧭 Danh sách các trang và đường dẫn (Routes)

| Đường dẫn (URL) | Chức năng | Tương ứng trong Prototype |
| :--- | :--- | :--- |
| `/` | Trang chủ (Landing page cho khách) | `MainV2` |
| `/login` | Đăng nhập tài khoản | `DangNhapV2` |
| `/register` | Đăng ký tài khoản mới | `DangNhapV2` |
| `/onboarding` | Khởi tạo mục tiêu & cách bắt đầu | `OnboardingV2` |
| `/placement` | Kiểm tra trình độ có bấm giờ 15 phút | `KiemTraV2` |
| `/placement/result` | Báo cáo kết quả kiểm tra & đề xuất cấp độ | `PlacementKetQuaV2` |
| `/lo-trinh/tao` | Tạo lộ trình cá nhân từ bản Content Manager đang phát hành | `TaoLoTrinhV2` |
| `/hom-nay` (hoặc `/today`, `/dashboard`) | Hôm nay: bài trong ngày, làm bù, ôn tuần, tuần đang học | `HomNayV2` |
| `/lo-trinh` | Lịch học theo ngày: dạng của Content Manager, số bài trong ngày theo số ngày hoàn thành, mini test và full test ở ô trống cuối | `LoTrinhV2` |
| `/roadmap/adjust` | Điều chỉnh lộ trình & thời gian ôn | `DieuChinhV2` |
| `/roadmap/retrospective` | Bảng nhìn lại tuần (Weekly Retrospective) | `NhinLaiTuanV2` |
| `/practice` | Ngân hàng câu hỏi & Luyện thi theo kỹ năng | `LuyenTapV2` |
| `/practice/prepare` | Màn chuẩn bị trước khi vào bài tập | `ChuanBiV2` |
| `/practice/play` | Trình làm bài trắc nghiệm (Audio player, Timer, Phiếu câu hỏi, Chấm tức thì, Báo lỗi) | `LamBaiV2` |
| `/practice/result` | Kết quả bài làm chi tiết & lời giải | `KetQuaV2` |
| `/practice/mini-test` | Thi thử rút gọn TOPIK 15 phút | `ThiThuV2` / `ThiNgheV2` |
| `/writing` | Luyện Viết TOPIK (Câu 51-54) có AI chấm và giấy kẻ ô Wonkuji | `VietV2` |
| `/theory` | Trung tâm lý thuyết ngữ pháp & từ vựng | `LyThuyetV2` |
| `/theory/vocabulary` | Học từ vựng với Flashcard 3D tương tác | `TuVungV2` |
| `/theory/grammar` | Chi tiết điểm ngữ pháp, bẫy đề & liên kết bài tập | `NguPhapV2` |
| `/theory/quiz` | Quiz trắc nghiệm từ vựng nhanh | `QuizTuVungV2` |
| `/theory/review` | Sổ tay lưu các từ vựng chưa thuộc | `TuChuaThuocV2` |
| `/progress` | Biểu đồ tiến độ, năng lực kỹ năng & hành trình Bé Ssak | `TienDoV2` |
| `/vip` | Bảng so sánh tính năng gói Free vs VIP & thanh toán QR | `VIPV2` |
| `/profile` | Thông tin tài khoản, cài đặt & đổi quà bằng Xu | `HoSoV2` |
| `/admin` | Cổng Quản trị (Duyệt câu hỏi, phân quyền, thống kê) | `AdminV2` |
| `/faq` & `/terms` | Câu hỏi thường gặp & Điều khoản bảo mật | `ThongTinV2` |
| `/content/sources` | Nhập đề: tạo bộ đề, chọn khuôn, điền câu, tải file nghe/ảnh, đưa vào ngân hàng | BRD 8 |
| `/content/templates` | Thư viện khuôn câu hỏi (cách hiển thị, không gắn số câu chuẩn) | — |
| `/content/exams` | Thư viện đề (Đã phát hành / Nháp và chờ duyệt / Lưu trữ) | BRD 9 |
| `/content/exams/{id}` | Trình soạn khung đề theo 42 dạng câu, gửi duyệt, nhân bản, lưu trữ | BRD 9 |
| `/content/questions` | Ngân hàng câu hỏi: 8 bộ lọc, sửa, ẩn/hiện, đổi mục đích, thao tác hàng loạt | BRD 10 |
| `/content/question-types` | Bảng 42 dạng câu và cấu trúc đề TOPIK I/II | BRD 7.1 |
| `/content/roadmap` | Lộ trình học: thứ tự dạng Nghe/Đọc, mốc ôn, ba chặng, gửi duyệt phát hành | — |

---

## 📥 Module nhập đề, quản lý đề và ngân hàng câu hỏi

Đây là lớp dữ liệu gốc: mọi câu hỏi đều gắn **mã dạng câu** (I-N1 … II-V4), **kỹ năng**, **số câu gốc** và **vùng cấp**,
để sau này luyện theo dạng, tự sinh Mini Test và dựng lộ trình đều tra trên cùng một nguồn.

### Chạy thử không cần SQL Server (profile `local`, H2 lưu trong `./data`)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# Windows: .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Mở `http://localhost:8490/login`, đăng nhập Content Manager `content@hottopik.com` / `content@123`
(Admin chỉ xem và duyệt: `admin@hottopik.com` / `admin@123`). Xoá thư mục `data/` để làm lại từ đầu.

Lần chạy đầu với profile `local` tự thêm bốn đề mẫu: mỗi dạng của TOPIK I và TOPIK II có mặt trong hai đề.
**Đề mẫu 1** nằm ở tab Đã phát hành, kèm Mini Test của những phần có câu. **Đề mẫu 2** là nháp để bấm Gửi duyệt.
Ngân hàng lọc theo một dạng sẽ thấy câu của cả hai đề (câu dùng chung hiện từng câu con). Chạy lại không tạo trùng.

Lần chạy đầu cũng tạo bản lộ trình `v1` tại `/content/roadmap`: mọi dạng Nghe và Đọc, Viết không vào lịch ngày.
Content Manager tạo bản nháp, đổi thứ tự, rồi gửi Admin duyệt. Học viên mới lấy bản đang phát hành.

Thư mục `samples/` có sẵn 3 bộ đề mẫu hợp lệ (TOPIK II × 2, TOPIK I × 1), mỗi bộ gồm file `.xlsx` và file `_media.zip`
(mp3/ảnh giả). Sinh lại bằng: `./mvnw test -Dtest=SampleFilesGenerator -Dsamples.out=samples`.

### Chạy với SQL Server của nhóm

DB nhóm dùng `ddl-auto=none`, nên chạy script `db/exam_module_sqlserver.sql` một lần để tạo 9 bảng mới
(`question_types`, `exam_sources`, `import_jobs`, `media_assets`, `question_groups`, `questions`, `writing_prompts`,
`exams`, `exam_items`). File upload lưu ở `hottopik.storage.dir` (mặc định `./data/storage`).

### Luồng nghiệp vụ

1. **Tạo bộ đề**: tên, track, kỳ, năm, ghi chú bản quyền. Số câu ghi trên form không khóa số câu lúc nhập.
2. **Chọn phần rồi chọn dạng câu**: Nghe, Đọc hoặc Viết, rồi chọn một mẫu đã chốt (TPL1-LIS-01, TPL1-REA-01, TPL2-WRI-01…). Bấm ô số trên lưới, không cần theo thứ tự. Ô xám là chưa nhập, ô xanh là đã nhập. Câu dùng chung thì bấm đủ số ô của dạng đó. File nghe, ảnh, đoạn văn và phụ đề nhập một lần cho các câu đó.
3. **Đưa vào ngân hàng và thư viện đề**: khi không còn lỗi (thiếu câu hỏi, đáp án, file nghe bắt buộc…). Mỗi câu là một mã `E0001…`, đề Viết là `W0001…`. Cùng lúc mở một đề nháp mang tên bộ đề. Tải mp3 hoặc ảnh trước khi gõ tên file. Giải thích tiếng Việt điền sau cũng được.
4. **Gửi duyệt** từ đề trong thư viện. Admin duyệt ở Hộp duyệt thì đề chuyển "Đã phát hành" và hệ thống sinh Mini Test cho những phần có câu. Từ chối thì đề về nháp.
5. **Ngân hàng câu hỏi**: lọc theo track, kỹ năng (kể cả Viết), dạng câu TPL, nguồn, mục đích và trạng thái. Đổi đáp án phải ghi lý do; ẩn câu cần lý do; ẩn hàng loạt hơn 20 câu cần Admin duyệt.

Quyền: Content Manager toàn quyền; Admin chỉ xem các trang này (và duyệt ở Hộp duyệt); vai trò khác bị chuyển hướng.
Mọi thao tác ghi đều vào Nhật ký thao tác.

### Mã nguồn chính

| Thành phần | Vị trí |
| :--- | :--- |
| 42 dạng câu, block nhóm câu, cấu trúc phần thi | `service/exam/QuestionTypeCatalog.java` |
| Đọc Excel / kiểm tra lỗi | `service/exam/ExamWorkbookParser.java`, `ExamImportValidator.java` |
| Nhập đề 4 bước | `service/exam/ExamImportService.java` |
| Soạn đề, duyệt, tự sinh Mini Test | `service/exam/ExamComposerService.java` |
| Ngân hàng câu hỏi | `service/exam/QuestionBankService.java` |
| Controller + giao diện | `controller/ContentExamController.java`, `templates/content/exam/*`, `static/css/content-admin.css` |
| Test | `src/test/java/com/hottopik/service/exam/*` (`./mvnw test`) |

Giới hạn hiện tại: kiểm tra file chạy đồng bộ khi tải lên (trạng thái job vẫn được lưu: VALIDATING → HAS_ERRORS/VALID → ACTIVATED);
Hộp duyệt và Nhật ký vẫn là bộ nhớ trong như phần còn lại của dự án, nên khởi động lại app sẽ mất yêu cầu đang chờ.
