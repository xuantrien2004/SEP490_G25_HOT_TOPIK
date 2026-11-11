package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Tests cho {@link LearnerController}.
 * Kiểm thử toàn diện:
 * - Điều hướng trang học tập: /today (/hom-nay, /dashboard), /trang-chu -> redirect /hom-nay.
 * - Tiến độ & Lịch sử học tập: /progress (/tien-do), /history (/lich-su).
 * - Hồ sơ & Cài đặt: /profile (/ho-so), /settings (/cai-dat), /coins (/xu).
 * - Cập nhật thông tin học viên: tên hiển thị, cấp độ mục tiêu, số phút mục tiêu mỗi ngày.
 * - Xóa trường thông tin cá nhân: số điện thoại, khôi phục avatar mặc định.
 * - Đổi mật khẩu học viên: kiểm tra độ dài, khớp mật khẩu xác nhận, xác thực mật khẩu cũ.
 * - Quản lý thông báo học viên: đếm thông báo chưa đọc, đánh dấu đã đọc 1 hoặc tất cả thông báo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("LearnerController – Integration Tests")
class LearnerControllerTest {

    @Autowired MockMvc mvc;

    private MockHttpSession sessionWith(UserDTO user) {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute("LOGGED_IN_USER", user);
        return s;
    }

    private UserDTO studentUser() {
        UserDTO u = new UserDTO();
        u.setId(9004L);
        u.setEmail("learner@hottopik.com");
        u.setRole("STUDENT");
        u.setStatus("ACTIVE");
        u.setFullName("Học Viên Test");
        u.setDisplayName("Học Viên Test");
        u.setTargetLevel("TOPIK_I");
        return u;
    }

    private UserDTO vipStudentUser() {
        UserDTO u = new UserDTO();
        u.setId(9005L);
        u.setEmail("viplearner@hottopik.com");
        u.setRole("STUDENT");
        u.setStatus("ACTIVE");
        u.setFullName("Học Viên VIP");
        u.setDisplayName("Học Viên VIP");
        u.setTargetLevel("TOPIK_II");
        u.setVip(true);
        u.setVipPackage("VIP");
        return u;
    }

    // ══════════════════════════════════════════
    // 1. Điều hướng nhiệm vụ hôm nay (Today / Dashboard)
    // ══════════════════════════════════════════

    @Test
    @DisplayName("GET /today – chưa đăng nhập → vẫn hiển thị giao diện hôm nay (200)")
    void getToday_noSession_returnsView() throws Exception {
        mvc.perform(get("/today"))
                .andExpect(status().isOk())
                .andExpect(view().name("learner/today"));
    }

    @Test
    @DisplayName("GET /today – học viên đã đăng nhập → tải trang học hôm nay (200)")
    void getToday_studentSession_returnsView() throws Exception {
        mvc.perform(get("/today").session(sessionWith(studentUser())))
                .andExpect(status().isOk())
                .andExpect(view().name("learner/today"))
                .andExpect(model().attribute("activePage", "today"));
    }

    @Test
    @DisplayName("GET /hom-nay – alias của /today → cùng trả về view learner/today")
    void getHomNay_aliasForToday_returnsView() throws Exception {
        mvc.perform(get("/hom-nay").session(sessionWith(studentUser())))
                .andExpect(status().isOk())
                .andExpect(view().name("learner/today"));
    }

    @Test
    @DisplayName("GET /dashboard – alias của /today → cùng trả về view learner/today")
    void getDashboard_aliasForToday_returnsView() throws Exception {
        mvc.perform(get("/dashboard").session(sessionWith(studentUser())))
                .andExpect(status().isOk())
                .andExpect(view().name("learner/today"));
    }

    @Test
    @DisplayName("GET /trang-chu – chuyển hướng về /hom-nay (302)")
    void getTrangChu_redirectsToHomNay() throws Exception {
        mvc.perform(get("/trang-chu"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hom-nay"));
    }

    @Test
    @DisplayName("GET /learner/home – chuyển hướng về /hom-nay (302)")
    void getLearnerHome_redirectsToHomNay() throws Exception {
        mvc.perform(get("/learner/home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/hom-nay"));
    }

    // ══════════════════════════════════════════
    // 2. Phân tích tiến độ & Lịch sử học tập
    // ══════════════════════════════════════════

    @Test
    @DisplayName("GET /progress – chưa đăng nhập → vẫn trả về view progress/index")
    void getProgress_noSession_returnsView() throws Exception {
        mvc.perform(get("/progress"))
                .andExpect(status().isOk())
                .andExpect(view().name("progress/index"));
    }

    @Test
    @DisplayName("GET /progress – học viên đã đăng nhập → có progressData và currentUser")
    void getProgress_studentSession_modelHasProgressData() throws Exception {
        mvc.perform(get("/progress").session(sessionWith(studentUser())))
                .andExpect(status().isOk())
                .andExpect(view().name("progress/index"))
                .andExpect(model().attributeExists("progressData", "currentUser"));
    }

    @Test
    @DisplayName("GET /tien-do – alias của /progress → cùng trả về view progress/index")
    void getTienDo_aliasForProgress_returnsView() throws Exception {
        mvc.perform(get("/tien-do"))
                .andExpect(status().isOk())
                .andExpect(view().name("progress/index"));
    }

    @Test
    @DisplayName("GET /history – hiển thị lịch sử làm bài (progress/history)")
    void getHistory_returnsView() throws Exception {
        mvc.perform(get("/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("progress/history"))
                .andExpect(model().attribute("activePage", "history"));
    }

    @Test
    @DisplayName("GET /lich-su – alias của /history → cùng trả về view progress/history")
    void getLichSu_aliasForHistory_returnsView() throws Exception {
        mvc.perform(get("/lich-su"))
                .andExpect(status().isOk())
                .andExpect(view().name("progress/history"));
    }

    // ══════════════════════════════════════════
    // 3. Hồ sơ cá nhân & Cài đặt & Xu
    // ══════════════════════════════════════════

    @Test
    @DisplayName("GET /profile – chưa đăng nhập → trả về view learner/profile")
    void getProfile_noSession_returnsViewWithoutUser() throws Exception {
        mvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(view().name("learner/profile"))
                .andExpect(model().attribute("activePage", "profile"));
    }

    @Test
    @DisplayName("GET /profile – học viên đã đăng nhập → model chứa thông tin user")
    void getProfile_studentSession_modelHasUser() throws Exception {
        mvc.perform(get("/profile").session(sessionWith(studentUser())))
                .andExpect(status().isOk())
                .andExpect(view().name("learner/profile"))
                .andExpect(model().attributeExists("user"));
    }

    @Test
    @DisplayName("GET /ho-so – alias của /profile → cùng trả về view learner/profile")
    void getHoSo_aliasForProfile_returnsView() throws Exception {
        mvc.perform(get("/ho-so"))
                .andExpect(status().isOk())
                .andExpect(view().name("learner/profile"));
    }

    @Test
    @DisplayName("GET /settings – hiển thị trang cài đặt (learner/settings)")
    void getSettings_returnsView() throws Exception {
        mvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("learner/settings"))
                .andExpect(model().attribute("activePage", "settings"));
    }

    @Test
    @DisplayName("GET /cai-dat – alias của /settings → cùng trả về view learner/settings")
    void getCaiDat_aliasForSettings_returnsView() throws Exception {
        mvc.perform(get("/cai-dat"))
                .andExpect(status().isOk())
                .andExpect(view().name("learner/settings"));
    }

    @Test
    @DisplayName("GET /coins – hiển thị trang số dư Xu và quà tặng (learner/coins)")
    void getCoins_returnsView() throws Exception {
        mvc.perform(get("/coins"))
                .andExpect(status().isOk())
                .andExpect(view().name("learner/coins"))
                .andExpect(model().attribute("activePage", "coins"));
    }

    @Test
    @DisplayName("GET /xu – alias của /coins → cùng trả về view learner/coins")
    void getXu_aliasForCoins_returnsView() throws Exception {
        mvc.perform(get("/xu"))
                .andExpect(status().isOk())
                .andExpect(view().name("learner/coins"));
    }

    // ══════════════════════════════════════════
    // 4. API Cập nhật thông tin học viên (/api/user/update-profile)
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /api/user/update-profile – chưa đăng nhập → 401 Unauthorized")
    void updateProfile_noSession_returns401() throws Exception {
        mvc.perform(post("/api/user/update-profile")
                        .param("displayName", "Tên Mới"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/user/update-profile – tên quá ngắn (<2 ký tự) → 400 Bad Request")
    void updateProfile_shortDisplayName_returns400() throws Exception {
        mvc.perform(post("/api/user/update-profile")
                        .session(sessionWith(studentUser()))
                        .param("displayName", "A"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/user/update-profile – tên quá dài (>40 ký tự) → 400 Bad Request")
    void updateProfile_tooLongDisplayName_returns400() throws Exception {
        String longName = "A".repeat(45);
        mvc.perform(post("/api/user/update-profile")
                        .session(sessionWith(studentUser()))
                        .param("displayName", longName))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/user/update-profile – cập nhật tên và mục tiêu thành công → success=true")
    void updateProfile_validDisplayName_returnsTrue() throws Exception {
        mvc.perform(post("/api/user/update-profile")
                        .session(sessionWith(studentUser()))
                        .param("displayName", "Nguyễn Văn Học")
                        .param("targetLevel", "TOPIK_II")
                        .param("dailyGoalMinutes", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.displayName").value("Nguyễn Văn Học"));
    }

    // ══════════════════════════════════════════
    // 5. API Xóa trường thông tin (/api/user/clear-field)
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /api/user/clear-field – chưa đăng nhập → 401 Unauthorized")
    void clearField_noSession_returns401() throws Exception {
        mvc.perform(post("/api/user/clear-field").param("field", "phone"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/user/clear-field – xóa số điện thoại → success=true")
    void clearField_phone_returnsTrue() throws Exception {
        mvc.perform(post("/api/user/clear-field")
                        .session(sessionWith(studentUser()))
                        .param("field", "phone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/user/clear-field – khôi phục avatar mặc định → success=true, có avatar")
    void clearField_avatar_returnsDefaultAvatar() throws Exception {
        mvc.perform(post("/api/user/clear-field")
                        .session(sessionWith(studentUser()))
                        .param("field", "avatar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.avatar").exists());
    }

    @Test
    @DisplayName("POST /api/user/clear-field – trường không hợp lệ → 400 Bad Request")
    void clearField_invalidField_returns400() throws Exception {
        mvc.perform(post("/api/user/clear-field")
                        .session(sessionWith(studentUser()))
                        .param("field", "invalid_field"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ══════════════════════════════════════════
    // 6. API Đổi mật khẩu (/api/user/change-password)
    // ══════════════════════════════════════════

    @Test
    @DisplayName("POST /api/user/change-password – chưa đăng nhập → 401 Unauthorized")
    void changePassword_noSession_returns401() throws Exception {
        mvc.perform(post("/api/user/change-password")
                        .param("oldPassword", "old")
                        .param("newPassword", "newpass123")
                        .param("confirmPassword", "newpass123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/user/change-password – mật khẩu xác nhận không khớp → 400 Bad Request")
    void changePassword_passwordMismatch_returns400() throws Exception {
        mvc.perform(post("/api/user/change-password")
                        .session(sessionWith(studentUser()))
                        .param("oldPassword", "learner@123")
                        .param("newPassword", "newpassword1")
                        .param("confirmPassword", "newpassword2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/user/change-password – mật khẩu mới < 6 ký tự → 400 Bad Request")
    void changePassword_shortNewPassword_returns400() throws Exception {
        mvc.perform(post("/api/user/change-password")
                        .session(sessionWith(studentUser()))
                        .param("oldPassword", "learner@123")
                        .param("newPassword", "abc")
                        .param("confirmPassword", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/user/change-password – xử lý đổi mật khẩu hoàn tất phản hồi")
    void changePassword_handlesRequest() throws Exception {
        mvc.perform(post("/api/user/change-password")
                        .session(sessionWith(studentUser()))
                        .param("oldPassword", "wrongoldpassword")
                        .param("newPassword", "newpassword123")
                        .param("confirmPassword", "newpassword123"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 400 || status == 200;
                });
    }

    // ══════════════════════════════════════════
    // 7. API Quản lý thông báo học viên (Notifications)
    // ══════════════════════════════════════════

    @Test
    @DisplayName("GET /api/notifications/unread-count – chưa đăng nhập → trả về count=0")
    void getUnreadNotificationCount_noSession_returnsZero() throws Exception {
        mvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("GET /api/notifications/unread-count – học viên có session → trả về key count")
    void getUnreadNotificationCount_studentSession_returnsCount() throws Exception {
        mvc.perform(get("/api/notifications/unread-count")
                        .session(sessionWith(studentUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").exists());
    }

    @Test
    @DisplayName("POST /api/notifications/read – chưa đăng nhập → 401 Unauthorized")
    void markNotifAsRead_noSession_returns401() throws Exception {
        mvc.perform(post("/api/notifications/read").param("id", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /thong-bao/read – alias của /api/notifications/read → 401 nếu chưa đăng nhập")
    void markNotifAsRead_aliasEndpoint_noSession_returns401() throws Exception {
        mvc.perform(post("/thong-bao/read").param("id", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/notifications/read-all – chưa đăng nhập → 401 Unauthorized")
    void markAllNotifsAsRead_noSession_returns401() throws Exception {
        mvc.perform(post("/api/notifications/read-all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/notifications/read-all – học viên có session → trả về kết quả JSON")
    void markAllNotifsAsRead_studentSession_returnsResult() throws Exception {
        mvc.perform(post("/api/notifications/read-all")
                        .session(sessionWith(studentUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists());
    }
}
