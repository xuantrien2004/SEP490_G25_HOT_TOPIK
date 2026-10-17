package com.hottopik.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PracticeController {

    @GetMapping({"/luyen-tap", "/practice"})
    public String practiceIndex(Model model) {
        model.addAttribute("pageTitle", "Luyện tập theo dạng câu · HOT TOPIK");
        model.addAttribute("activePage", "practice");
        return "practice/index";
    }

    @GetMapping({"/luyen-tap/viet/{cau}", "/practice/writing/{cau}"})
    public String practiceWriting(@PathVariable("cau") int cau, Model model) {
        model.addAttribute("pageTitle", "Luyện Viết · Câu " + cau);
        model.addAttribute("activePage", "practice");
        model.addAttribute("questionNo", cau);
        return "practice/writing";
    }

    @GetMapping({"/de-thi", "/exam"})
    public String examIndex(Model model) {
        model.addAttribute("pageTitle", "Đề thi · Full Mock & Mini Test · HOT TOPIK");
        model.addAttribute("activePage", "exam");
        return "exam/index";
    }

    @GetMapping({"/practice/prepare", "/luyen-tap/chuan-bi"})
    public String preparePractice(@RequestParam(value = "type", defaultValue = "listening") String type, Model model) {
        model.addAttribute("pageTitle", "Chuẩn bị bài luyện");
        model.addAttribute("activePage", "practice");
        model.addAttribute("practiceType", type);
        return "practice/prepare";
    }

    @GetMapping({"/lam-bai/{attemptId}", "/practice/play"})
    public String playPractice(@PathVariable(value = "attemptId", required = false) String attemptId,
                               @RequestParam(value = "mode", defaultValue = "practice") String mode,
                               Model model) {
        model.addAttribute("pageTitle", "Làm bài · HOT TOPIK");
        model.addAttribute("activePage", "practice");
        model.addAttribute("attemptId", attemptId != null ? attemptId : "ATT-8921");
        model.addAttribute("mode", mode);
        return "practice/play";
    }

    @GetMapping({"/ket-qua/{attemptId}", "/practice/result"})
    public String practiceResult(@PathVariable(value = "attemptId", required = false) String attemptId,
                                 Model model) {
        model.addAttribute("pageTitle", "Kết quả và giải thích bài làm");
        model.addAttribute("activePage", "practice");
        model.addAttribute("attemptId", attemptId != null ? attemptId : "ATT-8921");
        return "practice/result";
    }

    @GetMapping("/practice/mini-test")
    public String miniTest(@RequestParam(value = "skill", defaultValue = "reading") String skill, Model model) {
        model.addAttribute("pageTitle", "Mini Test TOPIK");
        model.addAttribute("activePage", "exam");
        model.addAttribute("skill", skill);
        return "practice/mini-test";
    }
}
