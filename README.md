# 🌿 HOT TOPIK — Korean TOPIK Learning System (SEP490_G25)

> **Đồ án tốt nghiệp Fall 2026 · Group SEP490_G25**  
> Dự án hệ thống luyện thi TOPIK thông minh với linh vật **Bé Ssak (싹 - mầm cây)** và phương pháp học ngắt quãng không áp lực.

---

## 🛠️ Công nghệ sử dụng (Tech Stack)

- **Backend:** Java 17, Spring Boot 3.3.4 (Spring MVC, Spring Data JPA, DevTools)
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
| `/roadmap/create` | Thiết lập lộ trình học cá nhân hóa | `TaoLoTrinhV2` |
| `/today` (hoặc `/dashboard`) | Cổng Hôm nay của học viên (Mầm cây Ssak, bài tập ngày) | `HomNayV2` |
| `/roadmap` | Lộ trình học 4 chặng & Lịch trình học tập | `LoTrinhV2` / `LoTrinhChangV2` |
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
>>>>>>> 61a8df0 (Initial project setup)
