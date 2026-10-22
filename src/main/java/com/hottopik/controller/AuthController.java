package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;

    @Value("${google.client-id:}")
    private String googleClientId;

    @Autowired
    public AuthController(UserService userService) {
        this.userService = userService;
    }


    // ================= 1. TRANG ĐĂNG NHẬP / ĐĂNG KÝ =================
    @GetMapping("/login")
    public String login(@RequestParam(value = "tab", defaultValue = "login") String tab,
                        @RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "unauthorized", required = false) String unauthorized,
                        @RequestParam(value = "logout", required = false) String logout,
                        @RequestParam(value = "verified", required = false) String verified,
                        @RequestParam(value = "verifyError", required = false) String verifyError,
                        @RequestParam(value = "resetSuccess", required = false) String resetSuccess,
                        Model model,
                        HttpSession session) {
        model.addAttribute("pageTitle", "Đăng nhập · Đăng ký · HOT TOPIK");
        model.addAttribute("activeTab", tab);

        if (error != null) {
            model.addAttribute("errorMessage", "Tài khoản hoặc mật khẩu không chính xác! Vui lòng kiểm tra lại.");
        } else if (unauthorized != null) {
            model.addAttribute("errorMessage", "Bạn không có quyền truy cập vào khu vực này. Vui lòng đăng nhập với tài khoản phù hợp.");
        } else if (logout != null) {
            model.addAttribute("successMessage", "Bạn đã đăng xuất thành công khỏi hệ thống.");
        } else if (verified != null) {
            model.addAttribute("successMessage", "Chúc mừng! Tài khoản của bạn đã được kích hoạt thành công.");
        } else if (verifyError != null) {
            model.addAttribute("errorMessage", "Liên kết xác thực không hợp lệ hoặc đã hết hạn.");
        } else if (resetSuccess != null) {
            model.addAttribute("successMessage", "Đặt lại mật khẩu thành công! Bạn có thể đăng nhập bằng mật khẩu mới ngay.");
        }

        UserDTO loggedIn = (UserDTO) session.getAttribute("LOGGED_IN_USER");
        if (loggedIn != null) {
            model.addAttribute("currentUser", loggedIn);
        }

        return "auth/login";
    }

    // ================= 2. XỬ LÝ ĐĂNG NHẬP PHÂN QUYỀN =================
    @PostMapping("/login")
    public String handleLogin(@RequestParam(value = "email", required = false) String email,
                              @RequestParam(value = "password", required = false) String password,
                              HttpSession session,
                              Model model) {
        if (email == null || password == null || email.trim().isEmpty() || password.trim().isEmpty()) {
            model.addAttribute("errorMessage", "Vui lòng nhập đầy đủ thông tin đăng nhập.");
            model.addAttribute("activeTab", "login");
            return "auth/login";
        }

        Optional<UserDTO> authResult = userService.authenticate(email, password);

        if (!authResult.isPresent()) {
            model.addAttribute("errorMessage", "Tài khoản hoặc mật khẩu không chính xác! Vui lòng kiểm tra lại.");
            model.addAttribute("activeTab", "login");
            model.addAttribute("inputEmail", email);
            return "auth/login";
        }

        UserDTO user = authResult.get();

        // Kiểm tra nếu tài khoản chưa xác thực OTP
        if ("PENDING_VERIFY".equalsIgnoreCase(user.getStatus())) {
            return "redirect:/verify-otp?email=" + URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8);
        }

        if ("LOCKED".equalsIgnoreCase(user.getStatus())) {
            model.addAttribute("errorMessage", "Tài khoản " + user.getEmail() + " đang bị tạm khóa (do sai mật khẩu 5 lần hoặc vi phạm). Vui lòng thử lại sau.");
            model.addAttribute("activeTab", "login");
            return "auth/login";
        }

        // Lưu thông tin người dùng vào Session
        session.setAttribute("LOGGED_IN_USER", user);

        // PHÂN QUYỀN VÀ ĐIỀU HƯỚNG CHUẨN XÁC THEO ROLE
        if (user.isAdmin()) {
            return "redirect:/admin";
        } else if (user.isContentManager()) {
            return redirectContent(session);
        } else if (user.isOperationManager()) {
            return "redirect:/operation";
        } else if (user.isMentor()) {
            return "redirect:/teacher";
        } else {
            // Học viên
            if (user.getTargetLevel() == null || "TOPIK_0".equals(user.getTargetLevel())) {
                return "redirect:/onboarding";
            }
            return "redirect:/today";
        }
    }

    // ================= 3. ĐIỀU HƯỚNG TAB ĐĂNG KÝ =================
    private String redirectContent(HttpSession session) {
        Object back = session.getAttribute("RETURN_AFTER_LOGIN");
        session.removeAttribute("RETURN_AFTER_LOGIN");
        if (back instanceof String path && path.startsWith("/content/") && !path.contains("://") && !path.startsWith("//")) {
            return "redirect:" + path;
        }
        return "redirect:/content";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("pageTitle", "Đăng ký tài khoản · HOT TOPIK");
        model.addAttribute("activeTab", "register");
        return "auth/login";
    }

    // ================= 4. XỬ LÝ ĐĂNG KÝ & GỬI MÃ OTP THẬT =================
    @PostMapping("/register")
    public String handleRegister(@RequestParam("displayName") String displayName,
                                 @RequestParam("email") String email,
                                 @RequestParam("password") String password,
                                 @RequestParam("confirmPassword") String confirmPassword,
                                 RedirectAttributes redirectAttributes) {
        if (displayName == null || email == null || password == null || confirmPassword == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng điền đầy đủ các trường thông tin!");
            return "redirect:/login?tab=register";
        }

        if (password.length() < 6) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mật khẩu phải có tối thiểu 6 ký tự!");
            return "redirect:/login?tab=register";
        }

        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Mật khẩu xác nhận không khớp! Vui lòng nhập lại.");
            return "redirect:/login?tab=register";
        }

        boolean registered = userService.registerStudent(displayName, email, password);
        if (!registered) {
            redirectAttributes.addFlashAttribute("errorMessage", "Email " + email + " đã được đăng ký và kích hoạt trước đó!");
            return "redirect:/login?tab=login";
        }

        // Chuyển hướng sang màn hình nhập mã OTP 6 số
        return "redirect:/verify-otp?email=" + URLEncoder.encode(email.trim().toLowerCase(), StandardCharsets.UTF_8);
    }

    // ================= 5. MÀN HÌNH & XỬ LÝ XÁC THỰC OTP ĐĂNG KÝ =================
    @GetMapping("/verify-otp")
    public String verifyOtpPage(@RequestParam("email") String email, Model model) {
        model.addAttribute("pageTitle", "Nhập mã OTP kích hoạt tài khoản · HOT TOPIK");
        model.addAttribute("email", email);
        return "auth/verify-otp";
    }

    @PostMapping("/verify-otp")
    public String handleVerifyOtp(@RequestParam("email") String email,
                                  @RequestParam("otpCode") String otpCode,
                                  HttpSession session,
                                  Model model) {
        Optional<UserDTO> userOpt = userService.verifyRegistrationOtp(email, otpCode);

        if (userOpt.isPresent()) {
            UserDTO activeUser = userOpt.get();
            session.setAttribute("LOGGED_IN_USER", activeUser);
            // Kích hoạt thành công -> chuyển ngay sang Onboarding để học viên chọn mục tiêu
            return "redirect:/onboarding";
        } else {
            model.addAttribute("errorMessage", "Mã OTP không chính xác hoặc đã hết hạn (quá 5 phút). Vui lòng thử lại!");
            model.addAttribute("email", email);
            model.addAttribute("pageTitle", "Nhập mã OTP kích hoạt tài khoản · HOT TOPIK");
            return "auth/verify-otp";
        }
    }

    @GetMapping("/resend-verify-otp")
    public String resendVerifyOtp(@RequestParam("email") String email, RedirectAttributes redirectAttributes) {
        userService.resendRegistrationOtp(email);
        redirectAttributes.addFlashAttribute("successMessage", "Đã gửi lại mã OTP mới đến email của bạn. Vui lòng kiểm tra hộp thư!");
        return "redirect:/verify-otp?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
    }

    // ================= 6. LUỒNG QUÊN MẬT KHẨU BẰNG MÃ OTP THẬT =================
    @PostMapping("/forgot-password")
    public String handleForgotPassword(@RequestParam("email") String email, RedirectAttributes redirectAttributes) {
        if (email == null || email.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng nhập địa chỉ email của bạn!");
            return "redirect:/login?tab=login";
        }

        userService.sendPasswordResetOtp(email.trim());
        // Chuyển hướng sang trang nhập OTP và mật khẩu mới
        return "redirect:/reset-password-otp?email=" + URLEncoder.encode(email.trim().toLowerCase(), StandardCharsets.UTF_8);
    }

    @GetMapping("/reset-password-otp")
    public String resetPasswordOtpPage(@RequestParam("email") String email, Model model) {
        model.addAttribute("pageTitle", "Đặt lại mật khẩu bằng mã OTP · HOT TOPIK");
        model.addAttribute("email", email);
        return "auth/reset-password-otp";
    }

    @PostMapping("/reset-password-otp")
    public String handleResetPasswordOtp(@RequestParam("email") String email,
                                         @RequestParam("otpCode") String otpCode,
                                         @RequestParam("newPassword") String newPassword,
                                         @RequestParam("confirmPassword") String confirmPassword,
                                         Model model,
                                         RedirectAttributes redirectAttributes) {
        if (newPassword == null || newPassword.length() < 6) {
            model.addAttribute("errorMessage", "Mật khẩu mới phải có tối thiểu 6 ký tự!");
            model.addAttribute("email", email);
            return "auth/reset-password-otp";
        }

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "Mật khẩu xác nhận không khớp!");
            model.addAttribute("email", email);
            return "auth/reset-password-otp";
        }

        boolean success = userService.resetPasswordWithOtp(email, otpCode, newPassword);
        if (success) {
            redirectAttributes.addFlashAttribute("resetSuccess", "1");
            return "redirect:/login?tab=login";
        } else {
            model.addAttribute("errorMessage", "Mã OTP không đúng hoặc đã hết hạn (quá 5 phút). Vui lòng thử lại!");
            model.addAttribute("email", email);
            return "auth/reset-password-otp";
        }
    }

    @GetMapping("/resend-reset-otp")
    public String resendResetOtp(@RequestParam("email") String email, RedirectAttributes redirectAttributes) {
        userService.sendPasswordResetOtp(email);
        redirectAttributes.addFlashAttribute("successMessage", "Đã gửi lại mã OTP khôi phục mật khẩu đến email của bạn!");
        return "redirect:/reset-password-otp?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
    }

    // ================= 7. HỖ TRỢ LINK XÁC THỰC EMAIL CŨ =================
    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam(value = "token", required = false) String token,
                              RedirectAttributes redirectAttributes) {
        if (token == null || token.trim().isEmpty()) {
            return "redirect:/login?verifyError=1";
        }
        boolean success = userService.verifyEmailByToken(token);
        return success ? "redirect:/login?verified=1" : "redirect:/login?verifyError=1";
    }

    // ================= 8. ĐĂNG XUẤT =================
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.removeAttribute("LOGGED_IN_USER");
        session.invalidate();
        return "redirect:/login?logout=1";
    }

    // ================= 9. ĐĂNG NHẬP BẰNG GOOGLE (OAuth2 Sign-In) =================

    /**
     * Trả về Google Client ID cho frontend
     */
    @GetMapping("/auth/google-client-id")
    @ResponseBody
    public ResponseEntity<Map<String, String>> getGoogleClientId() {
        if (googleClientId == null || googleClientId.isEmpty()) {
            return ResponseEntity.ok(Map.of("clientId", "", "configured", "false"));
        }
        return ResponseEntity.ok(Map.of("clientId", googleClientId, "configured", "true"));
    }

    /**
     * Nhận credential token từ Google Sign-In JavaScript, xác thực server-side,
     * tìm hoặc tạo user, lưu session, trả về URL redirect.
     */
    @PostMapping("/auth/google")
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleGoogleSignIn(
            @RequestBody Map<String, String> payload,
            HttpSession session) {

        String credential = payload.get("credential");
        if (credential == null || credential.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Thiếu credential token từ Google."));
        }

        try {
            // Xác thực ID token qua Google OAuth2 tokeninfo API
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + credential))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Google token verification failed: HTTP {}", response.statusCode());
                return ResponseEntity.badRequest().body(Map.of("error", "Token Google không hợp lệ hoặc đã hết hạn."));
            }

            String body = response.body();

            // Parse JSON response thủ công (tránh dependency thêm)
            String email = extractJsonValue(body, "email");
            String name = extractJsonValue(body, "name");
            String picture = extractJsonValue(body, "picture");
            String emailVerified = extractJsonValue(body, "email_verified");
            String aud = extractJsonValue(body, "aud");

            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Không lấy được email từ tài khoản Google."));
            }

            // Kiểm tra audience khớp với client ID đã cấu hình
            if (googleClientId != null && !googleClientId.isEmpty() && !googleClientId.equals(aud)) {
                log.warn("Google token audience mismatch: expected={}, got={}", googleClientId, aud);
                return ResponseEntity.badRequest().body(Map.of("error", "Token Google không khớp với ứng dụng."));
            }

            // Tìm hoặc tạo user từ thông tin Google
            UserDTO user = userService.findOrCreateGoogleUser(email, name, picture);
            if (user == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Không thể tạo hoặc tìm tài khoản."));
            }

            if ("LOCKED".equalsIgnoreCase(user.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Tài khoản " + email + " đang bị tạm khóa."));
            }

            // Lưu vào session
            session.setAttribute("LOGGED_IN_USER", user);
            log.info(">>> [HOT TOPIK] Đăng nhập Google thành công: {} ({})", user.getDisplayName(), email);

            // Xác định redirect URL theo role
            String redirectUrl;
            if (user.isAdmin()) {
                redirectUrl = "/admin";
            } else if (user.isContentManager()) {
                redirectUrl = "/content";
            } else if (user.isOperationManager()) {
                redirectUrl = "/operation";
            } else if (user.isMentor()) {
                redirectUrl = "/teacher";
            } else {
                // Học viên - kiểm tra nếu chưa chọn mục tiêu
                if (user.getTargetLevel() == null || "TOPIK_0".equals(user.getTargetLevel())) {
                    redirectUrl = "/onboarding";
                } else {
                    redirectUrl = "/today";
                }
            }

            return ResponseEntity.ok(Map.of("redirect", redirectUrl, "success", "true"));

        } catch (Exception ex) {
            log.error("Lỗi xử lý Google Sign-In: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError().body(Map.of("error", "Lỗi hệ thống khi xử lý đăng nhập Google."));
        }
    }

    /**
     * Helper: trích xuất giá trị từ JSON string đơn giản (tránh dependency Jackson/Gson)
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx == -1) return null;

        int colonIdx = json.indexOf(":", keyIdx + searchKey.length());
        if (colonIdx == -1) return null;

        // Tìm giá trị sau dấu :
        int start = colonIdx + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) {
            start++;
        }

        if (start >= json.length()) return null;

        if (json.charAt(start) == '"') {
            // Giá trị chuỗi
            int end = json.indexOf('"', start + 1);
            if (end == -1) return null;
            return json.substring(start + 1, end);
        } else {
            // Giá trị không phải chuỗi (boolean, number)
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
                end++;
            }
            return json.substring(start, end).trim();
        }
    }
}
