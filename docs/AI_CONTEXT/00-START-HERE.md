# 00 - BẮT ĐẦU TẠI ĐÂY (START HERE)
> **Tài liệu tham chiếu chuẩn cho AI Coding Agent làm việc với dự án HOT TOPIK**  
> *Dành cho các AI Agent trong các session tiếp theo: Đọc file này trước tiên để định hướng công việc và nắm bắt kiến trúc tổng thể.*

---

## 1. Mục Đích & Cách Sử Dụng Bộ Tài Liệu Này

Bộ tài liệu này được tạo ra nhằm giúp mọi AI Coding Agent khi bước vào một phiên làm việc (session) mới có thể:
1. **Hiểu ngay bức tranh tổng quan**: Nghiệp vụ, công nghệ, kiến trúc và database của HOT TOPIK mà không cần quét lại hàng trăm file mã nguồn.
2. **Xác định chính xác vị trí cần sửa**: Dựa vào bản đồ mã nguồn ([10-FILE-MAP.md](10-FILE-MAP.md)) để mở đúng file liên quan trực tiếp đến yêu cầu của người dùng.
3. **Tuân thủ đúng quy tắc nghiệp vụ**: Tránh việc sửa đổi sai lệch logic thi cử, tính điểm TOPIK, phân quyền hoặc phá vỡ các tính năng đã ổn định.
4. **Không làm hỏng mã nguồn**: Tránh các lỗi phổ biến về mã hóa ký tự (UTF-8/CRLF), PowerShell execution và Thymeleaf expression.

---

## 2. Bản Đồ Bộ Tài Liệu (Documentation Roadmap)

| Tên File | Nội Dung Chính | Khi Nào AI Cần Đọc? |
|:---|:---|:---|
| **[01-PROJECT-OVERVIEW.md](01-PROJECT-OVERVIEW.md)** | Tên, mục tiêu, đối tượng sử dụng, vấn đề giải quyết, phạm vi và giới hạn | Khi cần hiểu bài toán nghiệp vụ, bối cảnh người dùng |
| **[02-SYSTEM-ARCHITECTURE.md](02-SYSTEM-ARCHITECTURE.md)** | Kiến trúc 3 tầng Spring Boot, Thymeleaf, Session Auth, các tích hợp | Khi làm việc với luồng dữ liệu, bảo mật, cấu hình |
| **[03-FUNCTIONAL-MODULES.md](03-FUNCTIONAL-MODULES.md)** | Danh mục 14 module chức năng lớn, vai trò actor, trạng thái hiện tại | Khi tiếp nhận task phát triển chức năng mới hoặc kiểm tra tiến độ |
| **[04-BUSINESS-LOGIC.md](04-BUSINESS-LOGIC.md)** | Logic Placement Test, Lộ trình học, Đề thi, Writing 51-54, Chấm điểm | **BẮT BUỘC ĐỌC** khi sửa logic bài thi, điểm số, lộ trình, viết |
| **[05-DATABASE-DESIGN.md](05-DATABASE-DESIGN.md)** | Chi tiết 30+ thực thể JPA, quan hệ bảng, các trường JSON, Enum | Khi thao tác với Entity, Repository, truy vấn dữ liệu |
| **[06-ROLE-PERMISSION.md](06-ROLE-PERMISSION.md)** | 5 vai trò (Admin, Content, Operation, Teacher, Learner/VIP) | Khi phân quyền controller, menu giao diện, chặn truy cập |
| **[07-API-INTEGRATION.md](07-API-INTEGRATION.md)** | Danh mục Controller, Endpoint URL, tích hợp Gmail, Google OAuth, Excel | Khi kiểm tra URL route, viết API hoặc debug form submit |
| **[08-SETUP-AND-TESTING.md](08-SETUP-AND-TESTING.md)** | Hướng dẫn chạy local (H2 & SQL Server), lệnh Maven, tài khoản mẫu | Khi cần build, chạy thử hoặc kiểm tra lỗi biên dịch |
| **[09-CODING-CONVENTIONS.md](09-CODING-CONVENTIONS.md)** | Quy ước code Java, Thymeleaf, JS, CSS, Transaction, Exception | Khi viết code mới hoặc chỉnh sửa file giao diện / backend |
| **[10-FILE-MAP.md](10-FILE-MAP.md)** | Bảng tra cứu: Tính năng X nằm ở File Y nào | **ĐỌC ĐẦU TIÊN** khi nhận task cụ thể để mở đúng file |
| **[11-KNOWN-ISSUES-AND-DECISIONS.md](11-KNOWN-ISSUES-AND-DECISIONS.md)** | Các quyết định kỹ thuật, lỗi đã biết, lưu ý môi trường Windows/PowerShell | Khi gặp lỗi biên dịch lạ, lỗi Thymeleaf hoặc thắc mắc thiết kế cũ |

---

## 3. Các Nguyên Tắc "Sống Còn" Cho AI Agent Khi Làm Việc

> [!CAUTION]
> **TUÂN THỦ NGHIÊM NGẶT CÁC QUY TẮC SAU ĐÂY:**

1. **Không tự ý refactor ngoài phạm vi**: Chỉ đọc và chỉnh sửa các file liên quan trực tiếp đến yêu cầu của người dùng.
2. **Không ghi đè (overwrite) mù quáng**: Luôn kiểm tra nội dung file trước khi sửa; bảo tồn các comment tiếng Việt và mã nguồn hiện có.
3. **Cẩn trọng với mã hóa (UTF-8 & CRLF) trên Windows**:
   - Các file HTML/JS/CSS chứa tiếng Việt và tiếng Hàn (Hangul). Tránh dùng các lệnh PowerShell regex thay thế trực tiếp làm hỏng encoding (mojibake).
   - Nếu cần can thiệp file phức tạp, hãy tạo file script `.js` trong thư mục scratch rồi thực thi bằng `node <script.js>`.
4. **Không chạy inline PowerShell `node -e "..."` đa dòng**: PowerShell sẽ phân tích sai cú pháp dấu ngoặc nhọn `{}` và biến `$`. Luôn ghi ra file script rồi chạy.
5. **Thymeleaf expression syntax**: Không lồng link `@{} ` trực tiếp bên trong biểu thức `${}` dạng `${(cond) ? @{...} : null}` vì Thymeleaf sẽ văng `TemplateInputException`. Cú pháp đúng là `th:src="${cond} ? @{...} : null"`.
6. **Không lưu trữ bí mật (Secret/Credential)**: Tuyệt đối không hardcode mật khẩu, token thật vào tài liệu hoặc commit git.
7. **Phân biệt rõ trạng thái thông tin**:
   - `[ĐÃ XÁC MINH SOURCE CODE]`: Tính năng đã đọc trực tiếp từ mã nguồn thực tế.
   - `[THEO YÊU CẦU NGHIỆP VỤ]`: Quy tắc thống nhất với người dùng.
   - `[CHƯA TRIỂN KHAI / ĐANG DỞ DANG]`: Cần lưu ý khi đụng vào.

---

## 4. Tóm Tắt Nhanh Hệ Thống (Cheat Sheet)

- **Tên dự án**: HOT TOPIK (Hệ thống luyện thi chứng chỉ năng lực tiếng Hàn TOPIK I & II).
- **Tech Stack**: Spring Boot 3.5.16, Java 17, Spring Data JPA, Thymeleaf, MSSQL Server / H2 Database, Apache POI.
- **Mô hình**: Server-Side Rendering (SSR) với Spring Boot + Thymeleaf. Client-side tương tác bằng Vanilla JavaScript hiện đại (không dùng React/Vue/Angular).
- **Hệ thống phân quyền**: 
  - `/admin/**`: Quản trị viên hệ thống (Admin).
  - `/content/**`: Quản lý nội dung (Content Manager - Quản lý kho câu hỏi, đề thi, từ vựng, ngữ pháp, bài test xếp lớp, lộ trình).
  - `/operation/**`: Quản lý vận hành (Operation Manager - Tiếp nhận báo cáo lỗi câu hỏi, duyệt sửa câu hỏi).
  - `/teacher/**`: Giảng viên / Chấm bài (Teacher/Mentor - Chấm bài viết Writing câu 51-54).
  - `/learner/**`, `/exam/**`, `/practice/**`, `/roadmap/**`: Dành cho Học viên (Learner / VIP).
