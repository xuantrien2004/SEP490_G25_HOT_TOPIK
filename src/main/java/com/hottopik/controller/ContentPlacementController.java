package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.service.PlacementService;
import com.hottopik.service.exam.Actor;
import com.hottopik.service.exam.ExamDomainException;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/** Content Manager chốt 10 Nghe + 10 Đọc cho từng bài kiểm tra trình độ. */
@Controller
@RequestMapping("/content/placement")
public class ContentPlacementController {

    private final PlacementService placement;

    public ContentPlacementController(PlacementService placement) {
        this.placement = placement;
    }

    @GetMapping
    public String editor(HttpSession session, Model model) {
        UserDTO user = viewer(session);
        if (user == null) return denied(session);
        model.addAttribute("currentUser", user);
        model.addAttribute("canEdit", Actor.ROLE_CONTENT.equalsIgnoreCase(user.getRole()));
        model.addAttribute("pageTitle", "Đề kiểm tra trình độ");
        model.addAttribute("papers", placement.editor());
        return "content/placement/editor";
    }

    @PostMapping
    public String save(@RequestParam String paper, HttpServletRequest request, HttpSession session, RedirectAttributes flash) {
        UserDTO user = viewer(session);
        if (user == null) return denied(session);
        if (!Actor.ROLE_CONTENT.equalsIgnoreCase(user.getRole())) {
            flash.addFlashAttribute("flashError", "Chỉ Content Manager sửa được cấu trúc đề.");
            return "redirect:/content/placement";
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if ("paper".equals(key) || values.length == 0 || values[0].isBlank()) return;
            try {
                counts.put(key, Integer.parseInt(values[0]));
            } catch (NumberFormatException ignored) {
                counts.put(key, -1);
            }
        });
        try {
            placement.save(paper, counts);
            flash.addFlashAttribute("flashOk", "Đã lưu cấu trúc 10 Nghe + 10 Đọc.");
        } catch (ExamDomainException e) {
            flash.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/content/placement";
    }

    private static UserDTO viewer(HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return null;
        return Actor.ROLE_CONTENT.equalsIgnoreCase(user.getRole()) || Actor.ROLE_ADMIN.equalsIgnoreCase(user.getRole()) ? user : null;
    }

    private static String denied(HttpSession session) {
        return AuthHelper.getLoggedInUser(session) == null ? "redirect:/login" : "redirect:/today";
    }
}
