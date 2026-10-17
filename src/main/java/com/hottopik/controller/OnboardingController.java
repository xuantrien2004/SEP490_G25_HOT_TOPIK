package com.hottopik.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OnboardingController {

    @GetMapping("/onboarding")
    public String onboarding(Model model) {
        model.addAttribute("pageTitle", "Chọn cách bắt đầu");
        return "onboarding/index";
    }

    @GetMapping("/placement")
    public String placementTest(Model model) {
        model.addAttribute("pageTitle", "Kiểm tra trình độ (15 phút)");
        return "placement/test";
    }

    @GetMapping({"/placement/result", "/placement/ket-qua"})
    public String placementResult(Model model) {
        model.addAttribute("pageTitle", "Kết quả kiểm tra trình độ");
        return "placement/result";
    }

    @GetMapping("/roadmap/create")
    public String createRoadmap(Model model) {
        model.addAttribute("pageTitle", "Tạo lộ trình học cá nhân");
        return "roadmap/create";
    }
}
