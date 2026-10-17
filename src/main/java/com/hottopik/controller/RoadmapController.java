package com.hottopik.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RoadmapController {

    @GetMapping({"/lo-trinh", "/roadmap"})
    public String roadmap(Model model) {
        model.addAttribute("pageTitle", "Lộ trình học theo ngày · HOT TOPIK");
        model.addAttribute("activePage", "roadmap");
        return "roadmap/index";
    }

    @GetMapping({"/lo-trinh/tao", "/roadmap/create"})
    public String createRoadmap(Model model) {
        model.addAttribute("pageTitle", "Tạo lộ trình học cá nhân");
        model.addAttribute("activePage", "roadmap");
        return "roadmap/create";
    }

    @GetMapping({"/lo-trinh/dieu-chinh", "/roadmap/adjust"})
    public String adjustRoadmap(Model model) {
        model.addAttribute("pageTitle", "Điều chỉnh lộ trình học");
        model.addAttribute("activePage", "roadmap");
        return "roadmap/adjust";
    }

    @GetMapping({"/lo-trinh/nhin-lai-tuan", "/roadmap/retrospective"})
    public String retrospective(Model model) {
        model.addAttribute("pageTitle", "Nhìn lại tuần học · HOT TOPIK");
        model.addAttribute("activePage", "roadmap");
        return "roadmap/retrospective";
    }
}
