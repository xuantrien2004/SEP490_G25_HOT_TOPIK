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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        HttpSession session = request.getSession(false);
        UserDTO user = AuthHelper.getLoggedInUser(session);

        // 1. Kiểm tra Cổng Quản trị Admin (/admin)
        if (uri.startsWith("/admin")) {
            if (user == null) {
                response.sendRedirect("/login?unauthorized=1");
                return false;
            }
            if (!user.isAdmin()) {
                response.sendRedirect("/login?unauthorized=1");
                return false;
            }
        }

        // 2. Kiểm tra Cổng Quản lý nội dung (/content)
        if (uri.startsWith("/content")) {
            if (user == null) {
                response.sendRedirect("/login?unauthorized=1");
                return false;
            }
            if (!user.isContentManager() && !user.isAdmin()) {
                response.sendRedirect("/login?unauthorized=1");
                return false;
            }
        }

        // 3. Kiểm tra Cổng Quản lý vận hành (/operation)
        if (uri.startsWith("/operation")) {
            if (user == null) {
                response.sendRedirect("/login?unauthorized=1");
                return false;
            }
            if (!user.isOperationManager() && !user.isAdmin()) {
                response.sendRedirect("/login?unauthorized=1");
                return false;
            }
        }

        // 4. Kiểm tra Cổng Giảng viên Mentor (/teacher)
        if (uri.startsWith("/teacher")) {
            if (user == null) {
                response.sendRedirect("/login?unauthorized=1");
                return false;
            }
            if (!user.isMentor() && !user.isAdmin()) {
                response.sendRedirect("/login?unauthorized=1");
                return false;
            }
        }

        return true;
    }
}
