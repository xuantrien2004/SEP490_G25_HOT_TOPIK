package com.hottopik.controller;

import com.hottopik.service.HanjaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class TheoryController {

    private final HanjaService hanjaService;

    @Autowired
    public TheoryController(HanjaService hanjaService) {
        this.hanjaService = hanjaService;
    }

    @GetMapping({"/ly-thuyet", "/theory"})
    public String theory(Model model) {
        model.addAttribute("pageTitle", "Lý thuyết · Từ vựng, Ngữ pháp, Hán Việt, Đặc thù");
        model.addAttribute("activePage", "theory");
        return "theory/index";
    }

    @GetMapping({"/ly-thuyet/tu-vung", "/theory/vocabulary"})
    public String vocabulary(@RequestParam(value = "topic", defaultValue = "daily") String topic, Model model) {
        model.addAttribute("pageTitle", "Chủ đề từ vựng · HOT TOPIK");
        model.addAttribute("activePage", "theory");
        model.addAttribute("topic", topic);
        return "theory/vocabulary";
    }

    @GetMapping({"/ly-thuyet/the", "/theory/flashcard", "/theory/flashcards"})
    public String flashcard(Model model) {
        model.addAttribute("pageTitle", "Học từ vựng bằng Flashcards");
        model.addAttribute("activePage", "theory");
        return "theory/flashcard";
    }

    @GetMapping({"/ly-thuyet/quiz", "/theory/quiz"})
    public String quiz(Model model) {
        model.addAttribute("pageTitle", "Quiz kiểm tra từ vựng nhanh");
        model.addAttribute("activePage", "theory");
        return "theory/quiz";
    }

    @GetMapping({"/tu-chua-thuoc", "/theory/review", "/theory/unlearned"})
    public String unlearnedWords(Model model) {
        model.addAttribute("pageTitle", "Sổ từ chưa thuộc · HOT TOPIK");
        model.addAttribute("activePage", "theory");
        return "theory/unlearned";
    }

    @GetMapping({"/ly-thuyet/han-viet", "/theory/hanja"})
    public String hanja(Model model) {
        model.addAttribute("pageTitle", "Cầu nối Hán Việt · 32 gốc từ");
        model.addAttribute("activePage", "theory");
        // Pass initial data so the page renders without a separate AJAX call
        try {
            model.addAttribute("hanjaRoots", hanjaService.getActiveHanjaRoots(null));
            model.addAttribute("hanjaStats", hanjaService.getLearnerStats());
        } catch (Exception e) {
            model.addAttribute("hanjaRoots", java.util.Collections.emptyList());
            model.addAttribute("hanjaStats", java.util.Collections.emptyMap());
        }
        return "theory/hanja";
    }

    @GetMapping({"/ly-thuyet/dac-thu", "/theory/special"})
    public String special(Model model) {
        model.addAttribute("pageTitle", "Đặc thù tiếng Hàn");
        model.addAttribute("activePage", "theory");
        return "theory/special";
    }

    @GetMapping("/theory/grammar")
    public String grammarDetail(@RequestParam(value = "id", defaultValue = "1") String id, Model model) {
        model.addAttribute("pageTitle", "Chi tiết ngữ pháp");
        model.addAttribute("activePage", "theory");
        model.addAttribute("grammarId", id);
        return "theory/grammar";
    }
}
