package com.hottopik.config;

import com.hottopik.dto.UserDTO;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RoleSecurityInterceptor implements HandlerInterceptor {

    /**
     * Kiểm tra phiên đăng nhập và phân quyền theo tiền tố URL (/admin, /content, /operation, /teacher) trước khi vào Controller.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI(); // Lấy đường dẫn URI hiện tại của request
        HttpSession session = request.getSession(false); // Lấy session hiện có (không tạo mới nếu chưa có)
        UserDTO user = AuthHelper.getLoggedInUser(session); // Đọc thông tin người dùng đang đăng nhập từ session

        // 1. Kiểm tra Cổng Quản trị Admin (/admin)
        if (uri.startsWith("/admin")) {
            if (user == null) {
                response.sendRedirect("/login?unauthorized=1"); // Chưa đăng nhập -> chuyển về trang login
                return false;
            }
            // Cho phép Content Manager truy cập kho từ vựng nếu vào bằng URL /admin/vocabulary
            if (uri.startsWith("/admin/vocabulary")) {
                if (!user.isContentManager() && !user.isAdmin()) {
                    response.sendRedirect("/login?unauthorized=1"); // Không phải Content Manager hoặc Admin -> từ chối
                    return false;
                }
                return true; // Hợp lệ -> cho phép đi tiếp vào Controller
            }
            if (!user.isAdmin()) {
                response.sendRedirect("/login?unauthorized=1"); // Không phải Admin -> từ chối
                return false;
            }
        }

        // 2. Kiểm tra Cổng Quản lý nội dung (/content)
        if (uri.startsWith("/content")) {
            if (user == null) {
                response.sendRedirect("/login?unauthorized=1"); // Chưa đăng nhập -> chuyển về trang login
                return false;
            }
            if (!user.isContentManager() && !user.isAdmin()) {
                response.sendRedirect("/login?unauthorized=1"); // Không có quyền Content Manager/Admin -> từ chối
                return false;
            }
        }

        // 3. Kiểm tra Cổng Quản lý vận hành (/operation)
        if (uri.startsWith("/operation")) {
            if (user == null) {
                response.sendRedirect("/login?unauthorized=1"); // Chưa đăng nhập -> chuyển về trang login
                return false;
            }
            if (!user.isOperationManager() && !user.isAdmin()) {
                response.sendRedirect("/login?unauthorized=1"); // Không có quyền Operation Manager/Admin -> từ chối
                return false;
            }
        }

        // 4. Kiểm tra Cổng Giảng viên Mentor (/teacher)
        if (uri.startsWith("/teacher")) {
            if (user == null) {
                response.sendRedirect("/login?unauthorized=1"); // Chưa đăng nhập -> chuyển về trang login
                return false;
            }
            if (!user.isMentor() && !user.isAdmin()) {
                response.sendRedirect("/login?unauthorized=1"); // Không có quyền Mentor/Admin -> từ chối
                return false;
            }
        }

        return true; // Tất cả kiểm tra hợp lệ -> cho phép request đi tiếp
    }
}
