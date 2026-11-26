package com.hottopik.config;

import com.hottopik.dto.UserDTO;
import com.hottopik.service.UserService;
import com.hottopik.service.subscription.SubscriptionAccessService;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Đồng bộ cờ Premium (UserDTO.vip) trong session với database ở MỖI request của học viên.
 * Các chỗ đang kiểm tra user.isVip() ở backend/template (ví dụ "Gửi Giáo viên chấm") vì vậy luôn dùng
 * trạng thái thật: mua xong có quyền ngay, hết hạn là mất quyền ngay – không cần đăng nhập lại và
 * không phụ thuộc Scheduler. Role của học viên không thay đổi.
 */
@Slf4j
@Component
public class SubscriptionSessionInterceptor implements HandlerInterceptor {

    private final SubscriptionAccessService access;
    private final UserService userService;

    public SubscriptionSessionInterceptor(SubscriptionAccessService access, UserService userService) {
        this.access = access;
        this.userService = userService;
    }

    /** Đánh dấu cờ VIP trong session đang do module gói học quản lý (đã từng khớp với subscription trong DB). */
    static final String SYNCED_KEY = "PREMIUM_SYNCED_FROM_SUBSCRIPTION";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null || user.getId() == null || !user.isStudent()) return true;
        try {
            Optional<SubscriptionAccessService.PremiumSnapshot> premium = access.premiumSnapshot(user.getId());
            if (premium.isPresent()) {
                user.setVip(true);
                user.setVipPackage(premium.get().planName());
                user.setVipExpireAt(premium.get().endsAt());
                session.setAttribute(SYNCED_KEY, Boolean.TRUE);
            } else if (Boolean.TRUE.equals(session.getAttribute(SYNCED_KEY)) && !userService.hasLegacyVipGrant(user.getId())) {
                // Premium đã hết hạn / không còn hợp lệ: hạ về Free ngay, không chờ đăng nhập lại.
                // Chỉ hạ cờ do chính module này bật, không động tới cờ VIP do chức năng khác đặt.
                user.setVip(false);
                user.setVipPackage(null);
                user.setVipExpireAt(null);
                session.removeAttribute(SYNCED_KEY);
            }
        } catch (Exception e) {
            // DB chưa sẵn sàng (chế độ fallback): giữ nguyên trạng thái trong session.
            log.debug("Không đồng bộ được Premium cho user #{}: {}", user.getId(), e.getMessage());
        }
        return true;
    }
}
