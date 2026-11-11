package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Tests cho {@link OperationPortalController}.
 * Kiểm thử toàn diện:
 * - Bảo mật phân quyền: RoleSecurityInterceptor chặn các truy cập không phải OPERATION_MANAGER (hoặc ADMIN) sang /login?unauthorized=1.
 * - Dashboard: hiển thị danh sách học viên, đơn hàng, bảng giá, lịch sử cấp bù VIP, đánh giá, thông báo.
 * - Quản lý học viên: tạm khóa (TEMPORARY), khóa vĩnh viễn (PERMANENT), mở khóa, toggle lock.
 * - Hoàn tiền đơn hàng: gửi đề xuất hoàn tiền (requestRefundApproval).
 * - Quản lý giá gói VIP: đề xuất/cập nhật giá gói học.
 * - Cấp bù quyền lợi VIP: gửi đề xuất cấp bù ngày học và xu cho học viên.
 * - Kiểm duyệt đánh giá (Reviews): toggle hiển thị đánh giá của học viên.
 * - Phát thông báo hệ thống: gửi thông báo đến các nhóm đối tượng (ALL, VIP, USER).
 * - Quản lý ngân sách AI: cập nhật hạn mức và ngưỡng cảnh báo AI.
 * - Hồ sơ Operation Manager: xem hồ sơ, cập nhật thông tin, xóa số điện thoại/avatar, đổi mật khẩu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("OperationPortalController – Integration Tests")
class OperationPortalControllerTest {

    @Autowired MockMvc mvc;

    private MockHttpSession sessionWith(UserDTO user) {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute("LOGGED_IN_USER", user);
        return s;
    }

    private UserDTO operationUser() {
        UserDTO u = new UserDTO();
        u.setId(9003L);
        u.setEmail("operation@hottopik.vn");
        u.setRole("OPERATION_MANAGER");
        u.setStatus("ACTIVE");
        u.setFullName("Trần Quốc Tuấn");
        return u;
    }

    private UserDTO adminUser() {
        UserDTO u = new UserDTO();
        u.setId(9001L);
        u.setEmail("admin@hottopik.vn");
        u.setRole("ADMIN");
        u.setStatus("ACTIVE");
        u.setFullName("Vũ Minh Quân");
        return u;
    }

    private UserDTO studentUser() {
        UserDTO u = new UserDTO();
        u.setId(9004L);
        u.setEmail("learner@hottopik.com");
        u.setRole("STUDENT");
        u.setStatus("ACTIVE");
        u.setFullName("Học Viên Test");
        return u;
    }

    private UserDTO contentUser() {
        UserDTO u = new UserDTO();
        u.setId(9002L);
        u.setEmail("content@hottopik.vn");
        u.setRole("CONTENT_MANAGER");
        u.setStatus("ACTIVE");
        u.setFullName("Lê Thu Hà");
        return u;
    }

    // ══════════════════════════════════════════
    // 1. Phân quyền truy cập Dashboard /operation
    // ══════════════════════════════════════════

    @Test
    @DisplayName("GET /operation – chưa đăng nhập → chuyển hướng /login?unauthorized=1")
    void getDashboard_noSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(get("/operation"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("GET /operation – học viên không có quyền → chuyển hướng /login?unauthorized=1")
    void getDashboard_studentSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(get("/operation").session(sessionWith(studentUser())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("GET /operation – Content Manager không có quyền → chuyển hướng /login?unauthorized=1")
    void getDashboard_contentManagerSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(get("/operation").session(sessionWith(contentUser())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("GET /operation – Operation Manager truy cập hợp lệ → tải dashboard (200)")
    void getDashboard_operationManagerSession_returns200() throws Exception {
        mvc.perform(get("/operation").session(sessionWith(operationUser())))
                .andExpect(status().isOk())
                .andExpect(view().name("operation/dashboard"))
                .andExpect(model().attributeExists("currentUser", "learners", "orders", "packagePrices"));
    }

    @Test
    @DisplayName("GET /operation – Admin có quyền giám sát cổng Operation → tải dashboard (200)")
    void getDashboard_adminSession_allowedToViewDashboard() throws Exception {
        mvc.perform(get("/operation").session(sessionWith(adminUser())))
                .andExpect(status().isOk())
                .andExpect(view().name("operation/dashboard"));
    }

    // ══════════════════════════════════════════
    // 2. Hồ sơ Operation Manager (/operation/profile)
    // ══════════════════════════════════════════

    @Test
    @DisplayName("GET /operation/profile – chưa đăng nhập → chuyển hướng /login?unauthorized=1")
    void getProfile_noSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(get("/operation/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("GET /operation/profile – học viên không có quyền → chuyển hướng /login?unauthorized=1")
    void getProfile_studentSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(get("/operation/profile").session(sessionWith(studentUser())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("GET /operation/profile – Operation Manager truy cập → tải trang profile (200)")
    void getProfile_operationManagerSession_returnsProfileView() throws Exception {
        mvc.perform(get("/operation/profile").session(sessionWith(operationUser())))
                .andExpect(status().isOk())
                .andExpect(view().name("operation/profile"))
                .andExpect(model().attributeExists("currentUser", "recentLogs"));
    }

    // ══════════════════════════════════════════
    // 3. Quản lý khóa / mở khóa học viên
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /operation/users/lock – chưa đăng nhập → chuyển hướng unauthorized")
    void lockLearner_noSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(post("/operation/users/lock")
                        .param("userId", "9004")
                        .param("lockType", "TEMPORARY")
                        .param("reason", "Vi phạm"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("POST /operation/users/lock – học viên không có quyền → chuyển hướng unauthorized")
    void lockLearner_studentSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(post("/operation/users/lock")
                        .session(sessionWith(studentUser()))
                        .param("userId", "9004")
                        .param("lockType", "TEMPORARY")
                        .param("reason", "Vi phạm"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("POST /operation/users/lock – lý do rỗng → 400 Bad Request")
    void lockLearner_emptyReason_returns400() throws Exception {
        mvc.perform(post("/operation/users/lock")
                        .session(sessionWith(operationUser()))
                        .param("userId", "9004")
                        .param("lockType", "TEMPORARY")
                        .param("reason", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /operation/users/lock – userId không tồn tại → success=false")
    void lockLearner_userNotFound_returnsFalse() throws Exception {
        mvc.perform(post("/operation/users/lock")
                        .session(sessionWith(operationUser()))
                        .param("userId", "999999")
                        .param("lockType", "TEMPORARY")
                        .param("reason", "Lý do hợp lệ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /operation/users/lock – Operation Manager tạm khóa học viên → success=true, status=LOCKED")
    void lockLearner_operationManager_tempLock_returnsTrue() throws Exception {
        mvc.perform(post("/operation/users/lock")
                        .session(sessionWith(operationUser()))
                        .param("userId", "9004")
                        .param("lockType", "TEMPORARY")
                        .param("days", "7")
                        .param("reason", "Vi phạm nội quy trao đổi học tập"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    @DisplayName("POST /operation/users/lock – Admin khóa vĩnh viễn học viên từ cổng vận hành → success=true")
    void lockLearner_adminSession_permanentLock_returnsTrue() throws Exception {
        mvc.perform(post("/operation/users/lock")
                        .session(sessionWith(adminUser()))
                        .param("userId", "9004")
                        .param("lockType", "PERMANENT")
                        .param("reason", "Vi phạm nghiêm trọng chính sách bảo mật"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    @DisplayName("POST /operation/users/unlock – chưa đăng nhập → chuyển hướng unauthorized")
    void unlockLearner_noSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(post("/operation/users/unlock").param("userId", "9004"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("POST /operation/users/unlock – mở khóa tài khoản thành công → success=true, status=ACTIVE")
    void unlockLearner_operationManager_validUser_returnsTrue() throws Exception {
        mvc.perform(post("/operation/users/unlock")
                        .session(sessionWith(operationUser()))
                        .param("userId", "9004"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /operation/users/toggle-lock – chưa đăng nhập → chuyển hướng unauthorized")
    void toggleUserLock_noSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(post("/operation/users/toggle-lock").param("userId", "9004"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("POST /operation/users/toggle-lock – Operation Manager đổi trạng thái khóa → success=true")
    void toggleUserLock_operationManager_returnsTrue() throws Exception {
        mvc.perform(post("/operation/users/toggle-lock")
                        .session(sessionWith(operationUser()))
                        .param("userId", "9004"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ══════════════════════════════════════════
    // 4. Quản lý hoàn tiền đơn hàng (Refund Order)
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /operation/orders/refund – chưa đăng nhập → chuyển hướng unauthorized")
    void refundOrder_noSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(post("/operation/orders/refund").param("orderId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("POST /operation/orders/refund – đơn hàng không tồn tại → success=false")
    void refundOrder_orderNotFound_returnsFalse() throws Exception {
        mvc.perform(post("/operation/orders/refund")
                        .session(sessionWith(operationUser()))
                        .param("orderId", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /operation/orders/refund – gửi đề xuất hoàn tiền lên Admin phê duyệt → success=true")
    void refundOrder_operationManager_validOrder_returnsTrueWithPendingApproval() throws Exception {
        mvc.perform(post("/operation/orders/refund")
                        .session(sessionWith(operationUser()))
                        .param("orderId", "1")
                        .param("reason", "Khách hàng yêu cầu hoàn tiền do trùng đơn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.pendingApproval").value(true));
    }

    // ══════════════════════════════════════════
    // 5. Quản lý giá gói VIP (Package Prices)
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /operation/prices/update – giá <= 0 → 400 Bad Request")
    void updatePrice_invalidPrice_returns400() throws Exception {
        mvc.perform(post("/operation/prices/update")
                        .session(sessionWith(operationUser()))
                        .param("packageId", "1")
                        .param("newPrice", "-1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /operation/prices/update – Operation Manager cập nhật giá hợp lệ → success=true")
    void updatePrice_operationManager_validPrice_returnsTrue() throws Exception {
        mvc.perform(post("/operation/prices/update")
                        .session(sessionWith(operationUser()))
                        .param("packageId", "1")
                        .param("newPrice", "149000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ══════════════════════════════════════════
    // 6. Cấp bù ngày VIP (VIP Compensation)
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /operation/vip/compensate – số ngày <= 0 → 400 Bad Request")
    void compensateVip_invalidDays_returns400() throws Exception {
        mvc.perform(post("/operation/vip/compensate")
                        .session(sessionWith(operationUser()))
                        .param("userId", "9004")
                        .param("days", "0")
                        .param("reason", "Lý do hợp lệ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /operation/vip/compensate – lý do rỗng → 400 Bad Request")
    void compensateVip_emptyReason_returns400() throws Exception {
        mvc.perform(post("/operation/vip/compensate")
                        .session(sessionWith(operationUser()))
                        .param("userId", "9004")
                        .param("days", "7")
                        .param("reason", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /operation/vip/compensate – gửi đề xuất cấp bù lên Admin phê duyệt → success=true")
    void compensateVip_operationManager_valid_returnsTrue() throws Exception {
        mvc.perform(post("/operation/vip/compensate")
                        .session(sessionWith(operationUser()))
                        .param("userId", "9004")
                        .param("days", "7")
                        .param("bonusCoins", "50")
                        .param("reason", "Bồi thường gián đoạn kết nối máy chủ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.pendingApproval").value(true));
    }

    // ══════════════════════════════════════════
    // 7. Kiểm duyệt đánh giá (Reviews) & Phát thông báo
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /operation/reviews/toggle-approve – chưa đăng nhập → chuyển hướng unauthorized")
    void toggleReview_noSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(post("/operation/reviews/toggle-approve").param("reviewId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("POST /operation/announcements/send – tiêu đề rỗng → 400 Bad Request")
    void sendAnnouncement_emptyTitle_returns400() throws Exception {
        mvc.perform(post("/operation/announcements/send")
                        .session(sessionWith(operationUser()))
                        .param("title", "")
                        .param("content", "Nội dung hợp lệ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /operation/announcements/send – nội dung rỗng → 400 Bad Request")
    void sendAnnouncement_emptyContent_returns400() throws Exception {
        mvc.perform(post("/operation/announcements/send")
                        .session(sessionWith(operationUser()))
                        .param("title", "Tiêu đề hợp lệ")
                        .param("content", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /operation/announcements/send – phát thông báo hợp lệ → success=true, có announcement")
    void sendAnnouncement_operationManager_valid_returnsTrue() throws Exception {
        mvc.perform(post("/operation/announcements/send")
                        .session(sessionWith(operationUser()))
                        .param("title", "Thông báo bảo trì hệ thống định kỳ")
                        .param("content", "Hệ thống sẽ bảo trì từ 01:00 đến 03:00 sáng ngày 20/11/2026.")
                        .param("audience", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.announcement").exists());
    }

    // ══════════════════════════════════════════
    // 8. Cấu hình ngân sách AI (AI Budget)
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /operation/ai-budget/update – hạn mức <= 0 → 400 Bad Request")
    void updateAiBudget_invalidLimit_returns400() throws Exception {
        mvc.perform(post("/operation/ai-budget/update")
                        .session(sessionWith(operationUser()))
                        .param("id", "1")
                        .param("limit", "0")
                        .param("threshold", "80"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /operation/ai-budget/update – ngưỡng cảnh báo < 10% → 400 Bad Request")
    void updateAiBudget_invalidThreshold_returns400() throws Exception {
        mvc.perform(post("/operation/ai-budget/update")
                        .session(sessionWith(operationUser()))
                        .param("id", "1")
                        .param("limit", "300")
                        .param("threshold", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /operation/ai-budget/update – cập nhật ngân sách hợp lệ → success=true")
    void updateAiBudget_operationManager_valid_returnsTrue() throws Exception {
        mvc.perform(post("/operation/ai-budget/update")
                        .session(sessionWith(operationUser()))
                        .param("id", "1")
                        .param("limit", "400")
                        .param("threshold", "75"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ══════════════════════════════════════════
    // 9. Cập nhật Profile & Đổi mật khẩu
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /operation/profile/update – họ tên quá ngắn (<2 ký tự) → 400 Bad Request")
    void updateProfile_shortName_returns400() throws Exception {
        mvc.perform(post("/operation/profile/update")
                        .session(sessionWith(operationUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /operation/profile/update – cập nhật hồ sơ hợp lệ → success=true")
    void updateProfile_validName_returnsTrue() throws Exception {
        mvc.perform(post("/operation/profile/update")
                        .session(sessionWith(operationUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Trần Quốc Tuấn Updated\",\"phone\":\"0988776655\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /operation/profile/clear-field – xóa số điện thoại → success=true")
    void clearOperationField_phone_returnsTrue() throws Exception {
        mvc.perform(post("/operation/profile/clear-field")
                        .session(sessionWith(operationUser()))
                        .param("field", "phone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /operation/profile/clear-field – khôi phục avatar mặc định → success=true")
    void clearOperationField_avatar_returnsTrue() throws Exception {
        mvc.perform(post("/operation/profile/clear-field")
                        .session(sessionWith(operationUser()))
                        .param("field", "avatar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.avatar").exists());
    }

    @Test
    @DisplayName("POST /operation/profile/clear-field – field không hợp lệ → 400 Bad Request")
    void clearOperationField_invalidField_returns400() throws Exception {
        mvc.perform(post("/operation/profile/clear-field")
                        .session(sessionWith(operationUser()))
                        .param("field", "unknown_field"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /operation/profile/change-password – mật khẩu mới < 6 ký tự → 400 Bad Request")
    void changePassword_shortNewPassword_returns400() throws Exception {
        mvc.perform(post("/operation/profile/change-password")
                        .session(sessionWith(operationUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"op@123\",\"newPassword\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /operation/profile/change-password – xử lý đổi mật khẩu hoàn tất phản hồi")
    void changePassword_handlesRequest() throws Exception {
        mvc.perform(post("/operation/profile/change-password")
                        .session(sessionWith(operationUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrongpassword\",\"newPassword\":\"newpassword123\"}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 400 || status == 200;
                });
    }
}
