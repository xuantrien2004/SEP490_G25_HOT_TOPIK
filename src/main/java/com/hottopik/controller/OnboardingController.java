package com.hottopik.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OnboardingController {

    /**
     * Hiển thị màn hình Onboarding để học viên mới chọn cách bắt đầu và mục tiêu học tập.
     */
    @GetMapping("/onboarding")
    public String onboarding(Model model) {
        model.addAttribute("pageTitle", "Chọn cách bắt đầu");
        return "onboarding/index";
    }
}
