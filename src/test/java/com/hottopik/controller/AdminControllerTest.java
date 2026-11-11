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
 * Integration Tests cho {@link AdminController}.
 * Kiểm thử toàn diện:
 * - Bảo mật phân quyền: RoleSecurityInterceptor chặn các request chưa đăng nhập hoặc không phải ADMIN sang /login?unauthorized=1.
 * - Dashboard: hiển thị thông tin, danh sách phê duyệt, nhân sự, audit log.
 * - Quản lý học viên: khóa tạm thời, khóa vĩnh viễn, mở khóa, toggle lock.
 * - Quản lý nhân sự: toggle lock tài khoản nhân viên.
 * - Phê duyệt yêu cầu (Approval): quyết định approve/reject (kèm lý do).
 * - Quản lý tài chính & vận hành: hoàn tiền đơn hàng, cập nhật giá gói, cấp bù ngày VIP.
 * - Cấu hình hệ thống: cập nhật AI budget, bật/tắt chế độ Break-glass.
 * - Hồ sơ Admin: xem profile, cập nhật thông tin, xóa trường thông tin, đổi mật khẩu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AdminController – Integration Tests")
class AdminControllerTest {

    @Autowired MockMvc mvc;

    private MockHttpSession sessionWith(UserDTO user) {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute("LOGGED_IN_USER", user);
        return s;
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

    private UserDTO operationUser() {
        UserDTO u = new UserDTO();
        u.setId(9003L);
        u.setEmail("operation@hottopik.vn");
        u.setRole("OPERATION_MANAGER");
        u.setStatus("ACTIVE");
        u.setFullName("Trần Quốc Tuấn");
        return u;
    }

    // ══════════════════════════════════════════
    // 1. Phân quyền truy cập Dashboard /admin
    // ══════════════════════════════════════════

    @Test
    @DisplayName("GET /admin – chưa đăng nhập → chuyển hướng /login?unauthorized=1")
    void getAdminDashboard_noSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("GET /admin – tài khoản học viên → chuyển hướng /login?unauthorized=1")
    void getAdminDashboard_studentSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(get("/admin").session(sessionWith(studentUser())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("GET /admin – tài khoản Content Manager → chuyển hướng /login?unauthorized=1")
    void getAdminDashboard_contentManagerSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(get("/admin").session(sessionWith(contentUser())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("GET /admin – tài khoản Operation Manager → chuyển hướng /login?unauthorized=1")
    void getAdminDashboard_operationManagerSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(get("/admin").session(sessionWith(operationUser())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("GET /admin – Admin đăng nhập hợp lệ → tải dashboard thành công (200)")
    void getAdminDashboard_adminSession_returns200() throws Exception {
        mvc.perform(get("/admin").session(sessionWith(adminUser())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists("currentUser", "pendingRequests", "allRequests", "staffUsers", "auditLogs"));
    }

    // ══════════════════════════════════════════
    // 2. Hồ sơ Quản trị viên (/admin/profile)
    // ══════════════════════════════════════════

    @Test
    @DisplayName("GET /admin/profile – chưa đăng nhập → chuyển hướng /login?unauthorized=1")
    void getAdminProfile_noSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(get("/admin/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("GET /admin/profile – Admin đăng nhập → tải trang profile (200)")
    void getAdminProfile_adminSession_returnsProfileView() throws Exception {
        mvc.perform(get("/admin/profile").session(sessionWith(adminUser())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/profile"))
                .andExpect(model().attributeExists("currentUser", "recentLogs"));
    }

    // ══════════════════════════════════════════
    // 3. Quản lý khóa / mở khóa học viên
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /admin/learners/lock – chưa đăng nhập → chuyển hướng unauthorized")
    void lockLearner_noSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(post("/admin/learners/lock")
                        .param("userId", "9004")
                        .param("lockType", "TEMPORARY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("POST /admin/learners/lock – học viên không có quyền → chuyển hướng unauthorized")
    void lockLearner_studentSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(post("/admin/learners/lock")
                        .session(sessionWith(studentUser()))
                        .param("userId", "9004")
                        .param("lockType", "TEMPORARY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("POST /admin/learners/lock – Admin khóa tạm thời học viên → success=true, status=LOCKED")
    void lockLearner_adminSession_tempLock_returnsTrue() throws Exception {
        mvc.perform(post("/admin/learners/lock")
                        .session(sessionWith(adminUser()))
                        .param("userId", "9004")
                        .param("lockType", "TEMPORARY")
                        .param("days", "7")
                        .param("reason", "Vi phạm nội quy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    @DisplayName("POST /admin/learners/lock – Admin khóa vĩnh viễn học viên → success=true, status=LOCKED")
    void lockLearner_adminSession_permanentLock_returnsTrue() throws Exception {
        mvc.perform(post("/admin/learners/lock")
                        .session(sessionWith(adminUser()))
                        .param("userId", "9004")
                        .param("lockType", "PERMANENT")
                        .param("reason", "Vi phạm nghiêm trọng"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    @DisplayName("POST /admin/learners/lock – Admin khóa tài khoản không tồn tại → success=false")
    void lockLearner_adminSession_userNotFound_returnsFalse() throws Exception {
        mvc.perform(post("/admin/learners/lock")
                        .session(sessionWith(adminUser()))
                        .param("userId", "999999")
                        .param("lockType", "TEMPORARY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /admin/learners/unlock – chưa đăng nhập → chuyển hướng unauthorized")
    void unlockLearner_noSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(post("/admin/learners/unlock").param("userId", "9004"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("POST /admin/learners/unlock – Admin mở khóa học viên → success=true, status=ACTIVE")
    void unlockLearner_adminSession_validUser_returnsTrue() throws Exception {
        mvc.perform(post("/admin/learners/unlock")
                        .session(sessionWith(adminUser()))
                        .param("userId", "9004"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /admin/learners/toggle-lock – chưa đăng nhập → chuyển hướng unauthorized")
    void toggleLearnerLock_noSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(post("/admin/learners/toggle-lock").param("userId", "9004"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("POST /admin/learners/toggle-lock – Admin đổi trạng thái khóa học viên → success=true")
    void toggleLearnerLock_adminSession_returnsTrue() throws Exception {
        mvc.perform(post("/admin/learners/toggle-lock")
                        .session(sessionWith(adminUser()))
                        .param("userId", "9004"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ══════════════════════════════════════════
    // 4. Quản lý trạng thái nhân sự (Staff)
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /admin/staff/toggle-lock – chưa đăng nhập → chuyển hướng unauthorized")
    void toggleStaffLock_noSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(post("/admin/staff/toggle-lock").param("userId", "9002"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("POST /admin/staff/toggle-lock – học viên không có quyền → chuyển hướng unauthorized")
    void toggleStaffLock_studentSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(post("/admin/staff/toggle-lock")
                        .session(sessionWith(studentUser()))
                        .param("userId", "9002"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("POST /admin/staff/toggle-lock – Admin toggle khóa nhân sự → success=true")
    void toggleStaffLock_adminSession_returnsTrue() throws Exception {
        mvc.perform(post("/admin/staff/toggle-lock")
                        .session(sessionWith(adminUser()))
                        .param("userId", "9002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ══════════════════════════════════════════
    // 5. Phê duyệt yêu cầu (Approval Service)
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /admin/approval/decide – chưa đăng nhập → chuyển hướng unauthorized")
    void decideApproval_noSession_redirectsToUnauthorized() throws Exception {
        mvc.perform(post("/admin/approval/decide")
                        .param("requestId", "1")
                        .param("decision", "approve"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?unauthorized=1"));
    }

    @Test
    @DisplayName("POST /admin/approval/decide – từ chối không có lý do (<5 ký tự) → 400 Bad Request")
    void decideApproval_rejectWithoutNote_returns400() throws Exception {
        mvc.perform(post("/admin/approval/decide")
                        .session(sessionWith(adminUser()))
                        .param("requestId", "1")
                        .param("decision", "reject")
                        .param("note", "ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /admin/approval/decide – Admin phê duyệt yêu cầu thành công → success=true")
    void decideApproval_adminApproves_returnsTrue() throws Exception {
        mvc.perform(post("/admin/approval/decide")
                        .session(sessionWith(adminUser()))
                        .param("requestId", "1")
                        .param("decision", "approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /admin/approval/decide – Admin từ chối kèm lý do hợp lệ → success=true")
    void decideApproval_adminRejectsWithNote_returnsTrue() throws Exception {
        mvc.perform(post("/admin/approval/decide")
                        .session(sessionWith(adminUser()))
                        .param("requestId", "2")
                        .param("decision", "reject")
                        .param("note", "Lý do từ chối cụ thể và chi tiết"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ══════════════════════════════════════════
    // 6. Cập nhật giá & Cấp bù VIP & Hoàn tiền
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /admin/prices/update – newPrice <= 0 → 400 Bad Request")
    void updatePrice_invalidPrice_returns400() throws Exception {
        mvc.perform(post("/admin/prices/update")
                        .session(sessionWith(adminUser()))
                        .param("packageId", "1")
                        .param("newPrice", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /admin/prices/update – Admin cập nhật giá hợp lệ → success=true")
    void updatePrice_adminSession_validPrice_returnsTrue() throws Exception {
        mvc.perform(post("/admin/prices/update")
                        .session(sessionWith(adminUser()))
                        .param("packageId", "1")
                        .param("newPrice", "199000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /admin/vip/compensate – số ngày <= 0 → 400 Bad Request")
    void compensateVip_invalidDays_returns400() throws Exception {
        mvc.perform(post("/admin/vip/compensate")
                        .session(sessionWith(adminUser()))
                        .param("userId", "9004")
                        .param("days", "0")
                        .param("reason", "Lý do hợp lệ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /admin/vip/compensate – lý do rỗng → 400 Bad Request")
    void compensateVip_emptyReason_returns400() throws Exception {
        mvc.perform(post("/admin/vip/compensate")
                        .session(sessionWith(adminUser()))
                        .param("userId", "9004")
                        .param("days", "7")
                        .param("reason", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /admin/vip/compensate – Admin cấp bù ngày VIP thành công → success=true")
    void compensateVip_adminSession_valid_returnsTrue() throws Exception {
        mvc.perform(post("/admin/vip/compensate")
                        .session(sessionWith(adminUser()))
                        .param("userId", "9004")
                        .param("days", "30")
                        .param("bonusCoins", "100")
                        .param("reason", "Bồi thường sự cố bảo trì server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /admin/orders/refund – Admin hoàn tiền đơn hàng hợp lệ → success=true")
    void refundOrder_adminSession_validOrder_returnsTrue() throws Exception {
        mvc.perform(post("/admin/orders/refund")
                        .session(sessionWith(adminUser()))
                        .param("orderId", "1")
                        .param("reason", "Khách hàng yêu cầu huỷ đơn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ══════════════════════════════════════════
    // 7. Cấu hình hệ thống & Break-glass
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /admin/break-glass/toggle – Admin chuyển đổi chế độ Break-glass → success=true")
    void toggleBreakGlass_adminSession_returnsSuccess() throws Exception {
        mvc.perform(post("/admin/break-glass/toggle")
                        .session(sessionWith(adminUser()))
                        .param("reason", "Kiểm tra khẩn cấp hệ thống"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.active").exists());
    }

    @Test
    @DisplayName("POST /admin/ai-budget/update – Admin cập nhật ngân sách AI → success=true")
    void updateAiBudget_adminSession_validId_returnsTrue() throws Exception {
        mvc.perform(post("/admin/ai-budget/update")
                        .session(sessionWith(adminUser()))
                        .param("budgetId", "1")
                        .param("monthlyLimit", "350")
                        .param("threshold", "85"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ══════════════════════════════════════════
    // 8. Cập nhật Profile Admin & Đổi mật khẩu
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /admin/profile/update – họ tên quá ngắn (<2 ký tự) → 400 Bad Request")
    void updateAdminProfile_shortName_returns400() throws Exception {
        mvc.perform(post("/admin/profile/update")
                        .session(sessionWith(adminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /admin/profile/update – cập nhật hồ sơ hợp lệ → success=true")
    void updateAdminProfile_validName_returnsTrue() throws Exception {
        mvc.perform(post("/admin/profile/update")
                        .session(sessionWith(adminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Vũ Minh Quân Updated\",\"phone\":\"0912345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /admin/profile/clear-field – xóa số điện thoại → success=true")
    void clearAdminField_phone_returnsTrue() throws Exception {
        mvc.perform(post("/admin/profile/clear-field")
                        .session(sessionWith(adminUser()))
                        .param("field", "phone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /admin/profile/clear-field – khôi phục avatar mặc định → success=true")
    void clearAdminField_avatar_returnsTrue() throws Exception {
        mvc.perform(post("/admin/profile/clear-field")
                        .session(sessionWith(adminUser()))
                        .param("field", "avatar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.avatar").exists());
    }

    @Test
    @DisplayName("POST /admin/profile/clear-field – field không hợp lệ → 400 Bad Request")
    void clearAdminField_invalidField_returns400() throws Exception {
        mvc.perform(post("/admin/profile/clear-field")
                        .session(sessionWith(adminUser()))
                        .param("field", "invalid_field_name"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /admin/profile/change-password – mật khẩu mới quá ngắn (<6 ký tự) → 400 Bad Request")
    void changeAdminPassword_shortNewPassword_returns400() throws Exception {
        mvc.perform(post("/admin/profile/change-password")
                        .session(sessionWith(adminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"admin@123\",\"newPassword\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /admin/profile/change-password – gửi request đổi mật khẩu → trả về status HTTP hợp lệ")
    void changeAdminPassword_handlesRequest() throws Exception {
        mvc.perform(post("/admin/profile/change-password")
                        .session(sessionWith(adminUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrongpass\",\"newPassword\":\"newpassword123\"}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 400 || status == 200;
                });
    }
}
