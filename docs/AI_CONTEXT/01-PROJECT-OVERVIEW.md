# 01 - TỔNG QUAN DỰ ÁN (PROJECT OVERVIEW)

> **Trạng thái**: Đã xác minh trong mã nguồn thực tế & yêu cầu nghiệp vụ (pom.xml, controllers, services).

---

## 1. Tên Dự Án & Mục Đích Hệ Thống

- **Tên dự án**: **HOT TOPIK** (Tên mã đề tài: *Korean TOPIK Learning System - SEP490_G25 - BAc Ssak Concept*).
- **Mục đích**: Xây dựng một nền tảng trực tuyến toàn diện hỗ trợ người học tiếng Hàn ôn luyện và chuẩn bị cho kỳ thi Năng lực Tiếng Hàn (TOPIK - Test of Proficiency in Korean), bao gồm cả hai cấp độ **TOPIK I** (Cấp 1 & 2 - Sơ cấp) và **TOPIK II** (Cấp 3, 4, 5, 6 - Trung & Cao cấp).
- **Ý tưởng cốt lõi (BAc Ssak Concept)**: Học có định hướng, cá nhân hóa lộ trình theo năng lực thực tế, rèn luyện có hệ thống từ lý thuyết nền tảng (từ vựng, ngữ pháp, Hán tự) đến dạng bài thi chuẩn mực, đồng thời giải quyết triệt để rào cản kỹ năng Viết (Writing) thông qua công nghệ đánh giá tự động kết hợp sự hỗ trợ từ giáo viên thực thụ.

---

## 2. Đối Tượng Sử Dụng (Actors)

Hệ thống phục vụ 6 nhóm người dùng chính:

| Actor | Vai Trò | Nhiệm Vụ & Quyền Hạn Trong Hệ Thống |
|:---|:---|:---|
| **Guest (Khách)** | Người dùng chưa đăng nhập | Xem trang chủ, tìm hiểu thông tin kỳ thi TOPIK, thi thử bài đánh giá năng lực đầu vào (Placement Test) để xem gợi ý trình độ |
| **Learner (Học viên thường)** | Người học đã đăng ký tài khoản | Làm bài kiểm tra năng lực, nhận lộ trình học cá nhân hóa, học từ vựng, ngữ pháp, Hanja, làm bài tập dạng câu hỏi, thi đề mô phỏng (Full Mock & Mini Test), xem giải thích chi tiết, báo cáo lỗi câu hỏi |
| **VIP Learner (Học viên VIP)** | Học viên nâng cấp gói VIP | Toàn bộ quyền lợi của Learner + **Quyền gửi bài thi Viết (Câu 51, 52, 53, 54) cho Giáo viên chấm điểm và nhận xét chi tiết theo Rubric** |
| **Teacher / Mentor (Giáo viên)** | Giảng viên chấm bài viết | Truy cập cổng Giảng viên (`/teacher`), tiếp nhận các bài thi Viết của học viên VIP, chấm điểm chi tiết từng tiêu chí (Nội dung, Ngôn ngữ, Bố cục), ghi chú nhận xét và gửi phản hồi |
| **Content Manager (Quản lý nội dung)** | Chuyên viên quản lý học liệu | Truy cập cổng Nội dung (`/content`), tạo và nhập đề thi từ file Excel/MP3/Ảnh, quản lý ngân hàng câu hỏi, phân loại dạng câu chuẩn, quản lý kho từ vựng, ngữ pháp, Hanja, cấu hình đề xếp lớp và lộ trình học |
| **Operation Manager (Quản lý vận hành)** | Chuyên viên hỗ trợ & vận hành | Truy cập cổng Vận hành (`/operation`), tiếp nhận phản ánh/báo lỗi câu hỏi từ học viên, phối hợp sửa chữa nội dung sai sót và thông báo kết quả xử lý |
| **Admin (Quản trị viên hệ thống)** | Quản trị toàn hệ thống | Truy cập cổng Quản trị (`/admin`), quản lý danh sách tài khoản, khóa/mở khóa tài khoản, phân quyền vai trò, quản trị giao dịch VIP, giám sát nhật ký thao tác và cấu hình hệ thống |

---

## 3. Các Vấn Đề Hệ Thống Giải Quyết

1. **Thiếu định hướng lộ trình học tiếng Hàn**: Đa số người tự học TOPIK không biết bắt đầu từ đâu. Hệ thống cung cấp bài kiểm tra phân loại (10 câu Nghe + 10 câu Đọc) để xác định trình độ ngay lập tức và đề xuất lộ trình với mục tiêu rõ ràng (từ 31 đến 200 ngày).
2. **Khó khăn lớn nhất ở kỹ năng Viết (Writing TOPIK II)**:
   - Các nền tảng hiện nay chỉ chấm trắc nghiệm (Nghe/Đọc).
   - HOT TOPIK xây dựng công cụ chấm tự động câu 51 & câu 52 theo tiêu chuẩn khắt khe của Viện Giáo dục Quốc tế Hàn Quốc (NIIED): phân tích đuôi câu kính ngữ trang trọng ở câu 51, đuôi văn viết cấm kính ngữ ở câu 52.
   - Đối với câu 53 (biểu đồ) và câu 54 (nghị luận), hệ thống kết nối trực tiếp với đội ngũ giáo viên để chấm bài theo khung rubric chuẩn.
3. **Quản lý học liệu cồng kềnh**: Hỗ trợ đội ngũ nội dung nhập đề nhanh chóng từ file Excel kèm file âm thanh MP3 và hình ảnh, tự động bóc tách vào ngân hàng câu hỏi.
4. **Học từ vựng thông minh qua âm Hán Việt (Hanja)**: Tiếng Hàn có hơn 60% từ gốc Hán. Module Hanja của hệ thống giúp người học Việt Nam tăng tốc ghi nhớ từ vựng thông qua liên kết Hán - Hàn - Việt.
5. **Đảm bảo độ chính xác của câu hỏi**: Cơ chế tiếp nhận và sửa lỗi câu hỏi minh bạch, lưu lại lịch sử sửa đổi (Revision) và tự động cộng điểm bù cho học viên nếu đề bài ban đầu có sai sót.

---

## 4. Các Chức Năng Chính Của Hệ Thống

- **Onboarding & Đánh giá năng lực (Placement Test)**: Kiểm tra 20 câu trắc nghiệm (10 Nghe, 10 Đọc), cho điểm và xếp lớp (Cấp 0, 1, 2) cùng đề xuất mục tiêu TOPIK I hoặc II.
- **Lộ trình học tập cá nhân hóa (Personalized Roadmap)**: Kế hoạch phân bổ từng ngày học lý thuyết và rèn luyện kỹ năng, có cơ chế bắt nhịp lại nếu gián đoạn (missed days).
- **Luyện tập theo dạng câu hỏi (Practice Engine)**: Bám sát danh mục chuẩn TOPIK I (11 dạng) và TOPIK II (25 dạng), hiển thị giải thích chi tiết và bản dịch phụ đề.
- **Thi thử đề thi chuẩn (Mock Exam)**: Chế độ thi Full Mock bấm giờ nghiêm ngặt và Mini Test theo từng kỹ năng, hỗ trợ làm 2-3 câu hỏi cùng lúc chung ngữ cảnh (đoạn văn, file nghe) với cơ chế neo nội dung (sticky).
- **Phân hệ Kỹ năng Viết (Writing Module)**: Luyện viết câu 51-54, chấm tự động câu 51-52, gửi bài cho giáo viên chấm đối với tài khoản VIP.
- **Kho lý thuyết (Theory Vault)**: Tra cứu và học Từ vựng (TOPIK 1-6), Ngữ pháp tiếng Hàn (chia nhóm, ví dụ), Gốc từ Hán tự (Hanja Roots).
- **Cổng Quản lý Nội dung (Content Portal)**: Nhập đề, chỉnh sửa câu hỏi, quản lý media âm thanh/hình ảnh, kiểm duyệt câu hỏi.
- **Cổng Giảng viên (Teacher Portal)**: Giao diện nhận bài, chấm điểm theo rubric, phản hồi học viên.
- **Cổng Vận hành (Operation Portal)**: Tiếp nhận báo lỗi câu hỏi, xử lý và gửi thông báo phản hồi.
- **Cổng Quản trị (Admin Portal)**: Quản lý người dùng, phân quyền, quản lý đơn hàng và gói VIP.

---

## 5. Phạm Vi và Giới Hạn Của Hệ Thống

### 5.1. Phạm Vi (In Scope)
- Chuẩn hóa theo format đề thi TOPIK thực tế mới nhất của Viện Giáo dục Quốc tế Quốc gia Hàn Quốc (NIIED).
- Hỗ trợ thi và luyện tập trên nền tảng Web máy tính và thiết bị di động (Responsive UI).
- Nhập đề thủ công hoặc thông qua file mẫu Excel `.xlsx`, nén âm thanh `.mp3` và hình ảnh `.png/.jpg`.
- Lưu trữ cục bộ các file media tải lên trong thư mục `./data/storage`.
- Xác thực tài khoản qua Email/Mật khẩu (mã hóa BCrypt), xác minh OTP qua Gmail SMTP và đăng nhập Google OAuth2.

### 5.2. Giới Hạn Hiện Tại (Out of Scope / Limitations)
- Không chấm bài trực tiếp bằng giọng nói (Voice Recognition) cho kỹ năng Nói (TOPIK Speaking chưa nằm trong phạm vi phiên bản này).
- Chấm bài câu 53 và 54 dựa trên con người (Human Teacher/Mentor), chưa tích hợp LLM/AI sinh phản hồi tự động cho bài viết dài (câu 51-52 chấm tự động bằng thuật toán Rule-based Regex và từ khóa).
- Chưa tích hợp cổng thanh toán trực tuyến tự động (VNPAY/Momo) — quy trình nâng cấp VIP hiện tại được thực hiện thủ công hoặc quản trị viên duyệt qua Admin portal.
