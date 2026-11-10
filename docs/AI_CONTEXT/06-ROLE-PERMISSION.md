# 06 - PHÂN QUYỀN VÀ VAI TRÒ (ROLE & PERMISSIONS)

> **Trạng thái**: Đã xác minh từ `RoleSecurityInterceptor.java`, `UserDTO.java`, `Actor.java` và các controllers.

---

## 1. Danh Sách Các Vai Trò Trong Hệ Thống (Roles)

Hệ thống quản lý 6 vai trò chính:

| Mã Vai Trò (Role Code) | Tên Hiển Thị (Display Name) | Mô Tả Trách Nhiệm |
|:---|:---|:---|
| `ROLE_ADMIN` | Quản trị viên hệ thống | Toàn quyền kiểm soát hệ thống, quản lý người dùng, phân quyền, cấu hình và xem nhật ký hoạt động |
| `ROLE_CONTENT_MANAGER` | Quản lý nội dung | Quản lý đề thi, ngân hàng câu hỏi, khuôn dạng câu hỏi, kho từ vựng, ngữ pháp, Hanja, bài test xếp lớp, chương trình lộ trình |
| `ROLE_OPERATION_MANAGER` | Quản lý vận hành | Tiếp nhận và xử lý báo cáo lỗi câu hỏi từ học viên, phối hợp sửa chữa dữ liệu và hoàn điểm |
| `ROLE_TEACHER` / `ROLE_MENTOR` | Giảng viên / Chấm bài | Tiếp nhận các bài nộp phần Viết (câu 51-54) của học viên VIP, chấm điểm theo rubric, viết nhận xét và phản hồi |
| `ROLE_VIP` | Học viên VIP | Đầy đủ quyền lợi học viên + **quyền gửi bài thi Viết câu 51-54 cho Giáo viên chấm trực tiếp** + mở khóa toàn bộ đề thi đặc biệt |
| `ROLE_USER` / `ROLE_STUDENT` | Học viên thường | Thi thử, làm bài kiểm tra năng lực, nhận lộ trình học, học từ vựng, ngữ pháp, Hanja, luyện tập dạng câu hỏi |
| *GUEST* | Khách vãng lai | Xem trang chủ, tra cứu thông tin kỳ thi TOPIK, thi thử bài kiểm tra năng lực đầu vào (Placement Test) |

---

## 2. Ma Trận Quyền Hạn (Permission Matrix)

| Chức Năng / Phân Hệ | Guest | Learner | VIP | Teacher | Content | Operation | Admin |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Xem Trang chủ, Thông tin TOPIK** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Làm bài Test Xếp lớp (Placement)** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Nhận Lộ trình học cá nhân hóa** | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ |
| **Học Từ vựng, Ngữ pháp, Hanja** | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Luyện tập theo dạng câu hỏi (Practice)** | ❌ | ✅ | ✅ | ❌ | ✅ | ❌ | ✅ |
| **Làm Đề thi thử (Full Mock & Mini Test)** | ❌ | ✅ | ✅ | ❌ | ✅ | ❌ | ✅ |
| **Luyện viết tự động câu 51 & 52** | ❌ | ✅ | ✅ | ❌ | ✅ | ❌ | ✅ |
| **Gửi bài Viết cho Giáo viên chấm** | ❌ | ❌ | **✅** | ❌ | ❌ | ❌ | ✅ |
| **Xem điểm & nhận xét bài viết của GV** | ❌ | ❌ | **✅** | ✅ | ❌ | ❌ | ✅ |
| **Báo lỗi câu hỏi trên bài thi/luyện tập**| ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ |
| **Cổng Giảng viên: Chấm bài viết** (`/teacher/**`) | ❌ | ❌ | ❌ | **✅** | ❌ | ❌ | **✅** |
| **Cổng Nội dung: Nhập đề, Sửa câu hỏi** (`/content/**`) | ❌ | ❌ | ❌ | ❌ | **✅** | ❌ | **✅** |
| **Cổng Vận hành: Xử lý báo lỗi** (`/operation/**`) | ❌ | ❌ | ❌ | ❌ | ❌ | **✅** | **✅** |
| **Cổng Quản trị: Quản lý User, Phân quyền** (`/admin/**`) | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | **✅** |

---

## 3. Cơ Chế Kiểm Soát Quyền Trong Mã Nguồn

### 3.1. Tầng HTTP Routing (`RoleSecurityInterceptor.java`)
Được đăng ký tại `WebMvcConfig.java` để đánh chặn mọi HTTP Request:
```java
// URL /admin/**
if (uri.startsWith("/admin")) {
    if (user == null || (!user.isAdmin() && !user.isContentManager())) {
        response.sendRedirect("/login?unauthorized=1");
        return false;
    }
}
// URL /content/**
if (uri.startsWith("/content")) {
    if (user == null || (!user.isContentManager() && !user.isAdmin())) {
        response.sendRedirect("/login?unauthorized=1");
        return false;
    }
}
// URL /operation/**
if (uri.startsWith("/operation")) {
    if (user == null || (!user.isOperationManager() && !user.isAdmin())) {
        response.sendRedirect("/login?unauthorized=1");
        return false;
    }
}
// URL /teacher/**
if (uri.startsWith("/teacher")) {
    if (user == null || (!user.isMentor() && !user.isAdmin())) {
        response.sendRedirect("/login?unauthorized=1");
        return false;
    }
}
```

### 3.2. Tầng Nghiệp Vụ (`Actor.java` & Service Methods)
Các hàm nghiệp vụ thực hiện kiểm tra quyền chủ động thông qua đối tượng `Actor`:
```java
public static void requireContent(Actor actor) {
    if (actor == null || (!actor.isContentManager() && !actor.isAdmin())) {
        throw new ExamDomainException("Bạn không có quyền quản lý nội dung đề thi.");
    }
}

public static void requireAdmin(Actor actor) {
    if (actor == null || !actor.isAdmin()) {
        throw new ExamDomainException("Chỉ Quản trị viên mới được thực hiện thao tác này.");
    }
}
```

### 3.3. Tầng Giao Diện Người Dùng (Thymeleaf Templates)
Giao diện ẩn/hiện các nút thao tác hoặc menu điều hướng thông qua các thuộc tính của `currentUser`:
```html
<!-- Chỉ hiển thị nút gửi Giáo viên chấm cho tài khoản VIP -->
<button th:if="${currentUser != null and currentUser.vip}" 
        type="button" class="btn btn-ssak-primary" onclick="submitToTeacher()">
    Gửi Giáo viên chấm
</button>

<!-- Hiển thị liên kết vào Cổng Giảng viên -->
<li th:if="${currentUser != null and (currentUser.teacher or currentUser.admin)}">
    <a th:href="@{/teacher}">Cổng Giảng Viên</a>
</li>

<!-- Hiển thị liên kết vào Cổng Quản lý nội dung -->
<li th:if="${currentUser != null and (currentUser.contentManager or currentUser.admin)}">
    <a th:href="@{/content/sources}">Quản Lý Đề Thi</a>
</li>
```
