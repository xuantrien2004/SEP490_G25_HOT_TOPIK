# 09 - QUY ƯỚC PHÁT TRIỂN MÃ NGUỒN (CODING CONVENTIONS)

> **Trạng thái**: Đã chuẩn hóa dựa trên các quy ước thực tế trong mã nguồn dự án.

---

## 1. Quy Tắc Đặt Tên (Naming Conventions)

### 1.1. Mã Nguồn Java
- **Tên Lớp (Classes & Interfaces)**: Sử dụng `PascalCase`.
  - Controller: Đuôi `Controller` (Ví dụ: `LearnerExamController`, `ContentExamController`).
  - Service: Đuôi `Service` hoặc `Evaluator` (Ví dụ: `ExamImportService`, `Writing5152Evaluator`).
  - Repository: Đuôi `Repository` (Ví dụ: `QuestionRepository`, `ExamAttemptRepository`).
  - Entity: Danh từ số ít (Ví dụ: `Question`, `Exam`, `WritingSubmission`).
  - DTO: Đuôi `DTO` hoặc dùng `Record` Java 17 (Ví dụ: `UserDTO`, `WritingSubmissionDTO`, `CardForm`).
- **Tên Gói (Packages)**: Viết thường toàn bộ, không có ký tự gạch dưới hay hoa (Ví dụ: `com.hottopik.service.exam`).
- **Tên Phương Thức & Biến (Methods & Variables)**: Sử dụng `camelCase` (Ví dụ: `remainingSeconds`, `calculateTotalScore()`).
- **Tên Hằng Số (Constants)**: Sử dụng `UPPER_SNAKE_CASE` (Ví dụ: `MAX_POINTS`, `SKILL_QUOTA`).

### 1.2. Cơ Sở Dữ Liệu
- **Tên Bảng (Tables)**: Viết thường dạng số nhiều, ngăn cách bằng gạch dưới `snake_case` (Ví dụ: `exams`, `questions`, `writing_submissions`, `exam_attempts`).
- **Tên Cột (Columns)**: Viết thường `snake_case` (Ví dụ: `prompt_ko`, `explanation_vi`, `opt1_img`, `checked_answers_json`).
- **Khóa chính**: Luôn đặt tên là `id` kiểu `BIGINT IDENTITY(1,1)` (ngoại trừ bảng liên kết có composite key).

### 1.3. Giao Diện Thymeleaf & Tài Nguyên Tĩnh
- **Tệp HTML Templates**: Đặt tên dạng `kebab-case` theo ngữ cảnh (Ví dụ: `source-detail.html`, `question-player.html`, `result.html`).
- **Tệp CSS / JS**: Đặt tên dạng `kebab-case` (Ví dụ: `question-player.css`, `question-player.js`).
- **CSS Class**: Bắt đầu bằng tiền tố module dạng viết tắt để tránh xung đột (Ví dụ: `.qp-*` cho Question Player, `.rv-*` cho Result Review, `.cm-*` cho Content Management).

---

## 2. Quy Tắc Tổ Chức & Trách Nhiệm Các Tầng (Layer Patterns)

### 2.1. Controller Layer
- Controller chỉ đóng vai trò đón nhận HTTP Request, giải mã tham số, gọi Service và trả về tên view hoặc JSON.
- **Không nhồi nhét Business Logic vào Controller**.
- Luôn kiểm tra quyền truy cập thông qua `AuthHelper.getLoggedInUser(session)` hoặc dựa vào `RoleSecurityInterceptor`.
- Khi chuyển hướng sau khi xử lý thành công (POST-Redirect-GET), sử dụng `RedirectAttributes.addFlashAttribute("flashOk", "...")` để hiển thị thông báo.

### 2.2. Service Layer
- Toàn bộ nghiệp vụ tính toán, kiểm tra hợp lệ, điều phối dữ liệu phải nằm ở Service.
- Các hàm làm thay đổi dữ liệu (Insert/Update/Delete) **bắt buộc có annotation `@Transactional`**.
- Với các hàm chỉ đọc dữ liệu, ưu tiên đánh dấu `@Transactional(readOnly = true)` để tối ưu bộ đệm Hibernate.
- Sử dụng Java 17 `Record` cho các cấu trúc dữ liệu trung gian (form submit, view object) để mã nguồn tinh gọn và bất biến (Immutable).

### 2.3. Repository Layer
- Kế thừa `JpaRepository<Entity, Long>`.
- Ưu tiên phương thức sinh tự động theo quy ước Spring Data (`findByEmail`, `existsByEmail`).
- Với các truy vấn phức tạp hoặc cần tránh N+1 Query, viết JPQL tường minh với `join fetch`:
  ```java
  @Query("select a from ExamAttempt a join fetch a.exam e where a.userId = :userId and a.completedAt is not null")
  List<ExamAttempt> findCompletedAttempts(@Param("userId") Long userId);
  ```

---

## 3. Quy Tắc Xử Lý Ngoại Lệ (Exception Handling)

1. **Lỗi vi phạm quy tắc nghiệp vụ**:
   - Sử dụng `ExamDomainException` (kế thừa `RuntimeException`).
   - Ném ngoại lệ kèm thông điệp tiếng Việt rõ ràng, dễ hiểu cho người dùng:
     ```java
     if (listening != SKILL_QUOTA) {
         throw new ExamDomainException("Phần Nghe phải đủ đúng 10 câu. Đang chọn " + listening + " câu.");
     }
     ```
2. **Lỗi từ chối quyền truy cập hoặc không tìm thấy tài nguyên**:
   - Sử dụng `ResponseStatusException` của Spring:
     ```java
     if (!user.isVip()) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Gửi Giáo viên chấm là quyền lợi VIP.");
     }
     ```
3. **Tuyệt đối không để lọt ngoại lệ kỹ thuật thô**:
   - Không để `NullPointerException` hoặc `SQLException` văng thẳng ra giao diện làm sập trang HTML (lỗi 500). Luôn bọc bắt và log cảnh báo.

---

## 4. Quy Tắc Làm Việc Với Thymeleaf & JavaScript

> [!WARNING]
> **Các lỗi cú pháp Thymeleaf nghiêm cấm:**

1. **Lỗi lồng biểu thức link `@{} ` vào trong `${}`**:
   - ❌ **SAI**: `th:src="${(cond) ? @{/media/{f}(f=${name})} : null}"` $\rightarrow$ Gây lỗi nghiêm trọng `TemplateInputException`.
   - ✅ **ĐÚNG**: `th:src="${cond} ? @{/media/{f}(f=${name})} : null"` hoặc dùng `th:if`.
2. **Bảo toàn bảng mã UTF-8 & CRLF**:
   - Toàn bộ mã nguồn chứa tiếng Việt có dấu và ký tự tiếng Hàn (Hangul).
   - Khi chỉnh sửa file bằng Node.js script, luôn chỉ định rõ encoding `utf8`.
   - Không dùng PowerShell lệnh `Set-Content` thô bạo làm mất BOM hoặc biến tiếng Việt thành ký tự lạ (mojibake).
3. **Tương tác Client-side (Vanilla JavaScript)**:
   - Viết JS đóng gói trong IIFE `(() => { 'use strict'; ... })();` để không gây ô nhiễm biến toàn cục.
   - Luôn kiểm tra sự tồn tại của phần tử DOM trước khi gán sự kiện (`if (element) element.addEventListener(...)`).
   - Sử dụng `IntersectionObserver` thay vì bắt sự kiện cuộn chuột `scroll` liên tục để tối ưu hiệu năng trình duyệt.
