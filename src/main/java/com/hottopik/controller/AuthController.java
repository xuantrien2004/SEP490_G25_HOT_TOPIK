package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import java.util.Optional;

@Controller
public class AuthController {

    private final UserService userService;

    @Autowired
    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login(@RequestParam(value = "tab", defaultValue = "login") String tab,
                        @RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "unauthorized", required = false) String unauthorized,
                        @RequestParam(value = "logout", required = false) String logout,
                        Model model,
                        HttpSession session) {
        model.addAttribute("pageTitle", "Đăng nhập · Đăng ký");
        model.addAttribute("activeTab", tab);

        if (error != null) {
            model.addAttribute("errorMessage", "Tài khoản hoặc mật khẩu không chính xác! Vui lòng kiểm tra lại.");
        } else if (unauthorized != null) {
            model.addAttribute("errorMessage", "Bạn không có quyền truy cập vào khu vực này. Vui lòng đăng nhập với tài khoản phù hợp.");
        } else if (logout != null) {
            model.addAttribute("successMessage", "Bạn đã đăng xuất thành công.");
        }

        // Keep current session user if any for display
        UserDTO loggedIn = (UserDTO) session.getAttribute("LOGGED_IN_USER");
        if (loggedIn != null) {
            model.addAttribute("currentUser", loggedIn);
        }

        return "auth/login";
    }

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

        if ("LOCKED".equalsIgnoreCase(user.getStatus())) {
            model.addAttribute("errorMessage", "Tài khoản " + user.getEmail() + " đang bị tạm khóa. Vui lòng liên hệ quản trị viên.");
            model.addAttribute("activeTab", "login");
            return "auth/login";
        }

        // Store user in session
        session.setAttribute("LOGGED_IN_USER", user);

        // Strictly redirect according to user role
        switch (user.getRole()) {
            case "ROLE_ADMIN":
                return "redirect:/admin";
            case "ROLE_CONTENT_MANAGER":
                return "redirect:/content";
            case "ROLE_OPERATION_MANAGER":
                return "redirect:/operation";
            case "ROLE_TEACHER":
                return "redirect:/teacher";
            case "ROLE_VIP":
            case "ROLE_USER":
            default:
                return "redirect:/today";
        }
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("pageTitle", "Đăng ký tài khoản");
        model.addAttribute("activeTab", "register");
        return "auth/login";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.removeAttribute("LOGGED_IN_USER");
        session.invalidate();
        return "redirect:/login?logout=1";
    }
}
