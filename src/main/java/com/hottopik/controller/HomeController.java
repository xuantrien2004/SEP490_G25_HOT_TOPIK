package com.hottopik.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("pageTitle", "HOT TOPIK — Nền tảng luyện thi TOPIK chuẩn Hàn Quốc");
        model.addAttribute("activePage", "home");
        return "guest/home";
    }

    @GetMapping({"/ve-topik", "/about-topik"})
    public String aboutTopik(Model model) {
        model.addAttribute("pageTitle", "Về kỳ thi TOPIK · HOT TOPIK");
        model.addAttribute("activePage", "ve-topik");
        return "topik/index";
    }

    @GetMapping({"/lam-thu", "/trial"})
    public String trial(Model model) {
        model.addAttribute("pageTitle", "Làm thử 8 câu miễn phí · HOT TOPIK");
        model.addAttribute("activePage", "trial");
        return "guest/trial";
    }

    @GetMapping("/faq")
    public String faq(Model model) {
        model.addAttribute("pageTitle", "Câu hỏi thường gặp");
        model.addAttribute("activePage", "faq");
        return "info/faq";
    }

    @GetMapping("/terms")
    public String terms(Model model) {
        model.addAttribute("pageTitle", "Điều khoản & Chính sách bảo mật");
        model.addAttribute("activePage", "terms");
        return "info/terms";
    }
}
