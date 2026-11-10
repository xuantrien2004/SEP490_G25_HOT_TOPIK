# 08 - THIẾT LẬP MÔI TRƯỜNG VÀ KIỂM THỬ (SETUP & TESTING)

> **Trạng thái**: Đã xác minh trong mã nguồn thực tế và kiểm chứng qua lệnh chạy Maven.

---

## 1. Yêu Cầu Môi Trường (System Prerequisites)

Để phát triển, biên dịch và chạy dự án HOT TOPIK, môi trường máy tính cần đáp ứng:

1. **Java Development Kit (JDK)**:
   - Phiên bản: **JDK 17 LTS** (Bắt buộc; dự án sử dụng Spring Boot 3.5.16 và các tính năng Record của Java 17).
   - Biến môi trường: `JAVA_HOME` phải trỏ chính xác vào thư mục cài đặt JDK 17 (Ví dụ trên Windows: `C:\Program Files\Java\jdk-17`).
2. **Maven**:
   - Dự án đã tích hợp sẵn **Maven Wrapper** (`mvnw` cho Linux/macOS, `mvnw.cmd` cho Windows) ở thư mục gốc. Không bắt buộc phải cài Maven toàn cục.
3. **Node.js**:
   - Cần thiết cho các tác vụ can thiệp mã nguồn phức tạp hoặc kiểm thử kịch bản tự động trên Windows nhằm tránh lỗi encoding của PowerShell.
4. **Hệ điều hành**:
   - Windows 10/11, macOS hoặc Linux.

---

## 2. Các Chế Độ Chạy Ứng Dụng (Run Profiles)

Hệ thống hỗ trợ 2 chế độ khởi chạy linh hoạt:

### Chế Độ 1: Chạy Offline Không Cần SQL Server (Profile `local` - Khuyên dùng khi dev nhanh)
- **Cơ chế**: Sử dụng cơ sở dữ liệu nhúng **H2 Database**, lưu trữ dữ liệu bền vững dưới dạng tệp tin cục bộ tại `./data/hottopik-local.mv.db`.
- **Cổng hoạt động**: `8490` (Tránh xung đột với cổng 8080).
- **Lệnh thực thi (PowerShell trên Windows)**:
  ```powershell
  cd d:\SEP490_G25\HotTopik
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
  .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
  ```
- **URL truy cập**: `http://localhost:8490`

### Chế Độ 2: Chạy Với Microsoft SQL Server (Profile mặc định `default`)
- **Cơ chế**: Kết nối trực tiếp vào cơ sở dữ liệu `HotTopik_2` trên Microsoft SQL Server.
- **Cổng hoạt động**: `8080`.
- **Yêu cầu trước khi chạy**:
  - Dịch vụ SQL Server đang hoạt động trên cổng `1433`.
  - Cơ sở dữ liệu `HotTopik_2` đã được tạo sẵn trong SQL Server.
  - Cấu hình tài khoản đăng nhập trong `src/main/resources/application.properties`:
    ```properties
    spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=HotTopik_2;encrypt=true;trustServerCertificate=true;loginTimeout=3;
    spring.datasource.username=sa
    spring.datasource.password=123
    ```
- **Lệnh thực thi (PowerShell trên Windows)**:
  ```powershell
  cd d:\SEP490_G25\HotTopik
  $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
  .\mvnw.cmd spring-boot:run
  ```
- **URL truy cập**: `http://localhost:8080`

---

## 3. Lệnh Biên Dịch & Chạy Kiểm Thử (Build & Test Commands)

### 3.1. Biên dịch dự án (Compile Check)
Kiểm tra cú pháp toàn bộ mã nguồn Java:
```powershell
cd d:\SEP490_G25\HotTopik
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\mvnw.cmd compile
```
- **Kết quả kỳ vọng**: `BUILD SUCCESS` (Exit code: 0).

### 3.2. Chạy toàn bộ Unit & Integration Test
```powershell
cd d:\SEP490_G25\HotTopik
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\mvnw.cmd test
```

### 3.3. Đóng gói ứng dụng thành file JAR
```powershell
cd d:\SEP490_G25\HotTopik
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\mvnw.cmd package -DskipTests
```
- File kết quả được tạo tại: `target/hottopik-0.0.1-SNAPSHOT.jar`.

---

## 4. Danh Sách Tài Khoản Thử Nghiệm Mẫu (Seed Accounts)

Hệ thống có cơ chế tự động nạp các tài khoản mẫu vào CSDL thông qua `UserSeeder.java` khi ứng dụng khởi động (nếu tài khoản chưa tồn tại).

| Vai Trò | Email Đăng Nhập | Mục Đích Thử Nghiệm | Ghi Chú Tra Cứu Mật Khẩu |
|:---|:---|:---|:---|
| **Quản trị viên (Admin)** | `admin@hottopik.com` | Kiểm tra Cổng Quản trị (`/admin`), phân quyền, khóa tài khoản | Xem quy ước mật khẩu seed trong `UserSeeder.java` |
| **Quản lý nội dung (Content)** | `content@hottopik.com` | Kiểm tra Cổng Nội dung (`/content`), nhập đề, sửa câu hỏi | Xem quy ước mật khẩu seed trong `UserSeeder.java` |
| **Quản lý vận hành (Operation)**| `operation@hottopik.com` | Kiểm tra Cổng Vận hành (`/operation`), xử lý báo lỗi câu hỏi | Xem quy ước mật khẩu seed trong `UserSeeder.java` |
| **Giảng viên (Teacher/Mentor)** | `teacher@hottopik.com` | Kiểm tra Cổng Giảng viên (`/teacher`), chấm bài viết 51-54 | Xem quy ước mật khẩu seed trong `UserSeeder.java` |
| **Học viên thường (Student)** | `learner@hottopik.com` | Kiểm tra giao diện làm bài thi, luyện tập, lộ trình | Xem quy ước mật khẩu seed trong `UserSeeder.java` |

> [!NOTE]
> Mật khẩu khởi tạo mẫu cho các tài khoản trên tuân theo quy ước chung dạng `<tên_vai_trò>@123` (ví dụ: `admin@123`, `user@123`). Chi tiết xem tại `UserSeeder.java`.

---

## 5. Các Lưu Ý Kỹ Thuật Đặc Biệt Trên Môi Trường Windows

1. **Dấu phân cách lệnh trong PowerShell**:
   - PowerShell **không hỗ trợ** toán tử `&&` để nối lệnh như Linux bash nếu phiên bản PS cũ hơn 7.0.
   - Luôn sử dụng dấu chấm phẩy `;` để ngăn cách các lệnh (Ví dụ: `cd d:\...; $env:JAVA_HOME=...; .\mvnw.cmd ...`).
2. **Khắc phục lỗi "JAVA_HOME is not set"**:
   - Nếu chạy `mvnw.cmd` báo lỗi thiếu `JAVA_HOME`, hãy gán biến môi trường ngay trong phiên PowerShell hiện tại:
     `$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"`
3. **Thư mục lưu trữ media**:
   - Ứng dụng tự động tạo thư mục `./data/storage` để lưu trữ các file tải lên. Đảm bảo tiến trình có quyền ghi (Write Permission) vào thư mục này.
