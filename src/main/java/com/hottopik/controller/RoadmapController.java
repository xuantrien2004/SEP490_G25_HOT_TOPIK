package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.service.UserService;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RoadmapController {

    private final UserService userService;

    @Autowired
    public RoadmapController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping({"/lo-trinh", "/roadmap"})
    public String roadmap(Model model, HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user != null && user.isStudent() && (user.getTargetLevel() == null || "TOPIK_0".equals(user.getTargetLevel()))) {
            return "redirect:/onboarding";
        }
        model.addAttribute("pageTitle", "Lộ trình học theo ngày · HOT TOPIK");
        model.addAttribute("activePage", "roadmap");
        return "roadmap/index";
    }

    @GetMapping({"/lo-trinh/tao", "/roadmap/create"})
    public String createRoadmap(
            @RequestParam(value = "startLevel", required = false) String startLevel,
            @RequestParam(value = "declaredLevel", required = false) String declaredLevel,
            @RequestParam(value = "entryMode", required = false) String entryMode,
            Model model, HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        String level = startLevel != null ? startLevel : (declaredLevel != null ? declaredLevel : "TOPIK_3");
        model.addAttribute("pageTitle", "Tạo lộ trình học cá nhân");
        model.addAttribute("activePage", "roadmap");
        model.addAttribute("startLevel", level);
        model.addAttribute("entryMode", entryMode != null ? entryMode : "SELF");
        return "roadmap/create";
    }

    @PostMapping({"/lo-trinh/tao", "/roadmap/create"})
    public String handleCreateRoadmap(
            @RequestParam(value = "startLevel", required = false, defaultValue = "TOPIK_3") String startLevel,
            @RequestParam(value = "targetLevel", required = false, defaultValue = "TOPIK_4") String targetLevel,
            @RequestParam(value = "minutesPerDay", required = false, defaultValue = "20") Integer minutesPerDay,
            @RequestParam(value = "durationDays", required = false, defaultValue = "60") Integer durationDays,
            @RequestParam(value = "levelSource", required = false, defaultValue = "SELF") String levelSource,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        userService.saveRoadmapSettings(user.getId(), startLevel, targetLevel, minutesPerDay, levelSource);

        // Cập nhật session user
        user.setCurrentLevel(startLevel.startsWith("TOPIK_") ? startLevel : "TOPIK_" + startLevel);
        user.setTargetLevel(targetLevel.startsWith("TOPIK_") ? targetLevel : "TOPIK_" + targetLevel);
        session.setAttribute("LOGGED_IN_USER", user);

        redirectAttributes.addFlashAttribute("successMessage", "🎉 Chúc mừng bạn đã thiết lập lộ trình học thành công!");
        return "redirect:/hom-nay";
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
