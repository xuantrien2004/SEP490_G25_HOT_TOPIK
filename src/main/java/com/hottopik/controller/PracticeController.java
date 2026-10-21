package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.service.exam.ExamDomainException;
import com.hottopik.service.exam.LearnerRoadmapService;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PracticeController {

    private final LearnerRoadmapService roadmaps;

    public PracticeController(LearnerRoadmapService roadmaps) {
        this.roadmaps = roadmaps;
    }

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
    public String preparePractice(@RequestParam(value = "type", defaultValue = "listening") String type,
                                  @RequestParam(value = "name", required = false) String name,
                                  @RequestParam(value = "questions", required = false) Integer questions,
                                  @RequestParam(value = "day", required = false) Integer day,
                                  @RequestParam(value = "slot", required = false) Integer slot,
                                  Model model) {
        model.addAttribute("pageTitle", "Chuẩn bị bài luyện");
        model.addAttribute("activePage", "practice");
        model.addAttribute("practiceType", type);
        model.addAttribute("lessonName", name == null || name.isBlank() ? "Bài luyện" : name);
        model.addAttribute("questionCount", questions == null ? 6 : questions);
        model.addAttribute("trailSession", type.startsWith("mini-") || "full".equals(type));
        model.addAttribute("day", day);
        model.addAttribute("slot", slot);
        model.addAttribute("playHref", withLesson("/practice/play?mode=practice", day, slot));
        model.addAttribute("backHref", roadmapBack(day, slot));
        return "practice/prepare";
    }

    @GetMapping({"/lam-bai/{attemptId}", "/practice/play"})
    public String playPractice(@PathVariable(value = "attemptId", required = false) String attemptId,
                               @RequestParam(value = "mode", defaultValue = "practice") String mode,
                               @RequestParam(value = "day", required = false) Integer day,
                               @RequestParam(value = "slot", required = false) Integer slot,
                               Model model) {
        model.addAttribute("pageTitle", "Làm bài · HOT TOPIK");
        model.addAttribute("activePage", "practice");
        model.addAttribute("attemptId", attemptId != null ? attemptId : "ATT-8921");
        model.addAttribute("mode", mode);
        model.addAttribute("day", day);
        model.addAttribute("slot", slot);
        model.addAttribute("resultHref", withLesson("/practice/result", day, slot));
        return "practice/play";
    }

    @GetMapping({"/ket-qua/{attemptId}", "/practice/result"})
    public String practiceResult(@PathVariable(value = "attemptId", required = false) String attemptId,
                                 @RequestParam(value = "day", required = false) Integer day,
                                 @RequestParam(value = "slot", required = false) Integer slot,
                                 HttpSession session,
                                 Model model) {
        model.addAttribute("pageTitle", "Kết quả và giải thích bài làm");
        model.addAttribute("activePage", "practice");
        model.addAttribute("attemptId", attemptId != null ? attemptId : "ATT-8921");
        model.addAttribute("day", day);
        model.addAttribute("slot", slot);
        model.addAttribute("backHref", roadmapBack(day, slot));
        if (day != null && slot != null) {
            try {
                roadmaps.setDone(owner(session), day, slot, true);
                model.addAttribute("counted", true);
            } catch (ExamDomainException ignored) {
                model.addAttribute("counted", false);
            }
        }
        return "practice/result";
    }

    private static String owner(HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail().trim().toLowerCase();
        }
        return "guest";
    }
    private static String withLesson(String path, Integer day, Integer slot) {
        if (day == null || slot == null) return path;
        return path + (path.contains("?") ? "&" : "?") + "day=" + day + "&slot=" + slot;
    }

    private static String roadmapBack(Integer day, Integer slot) {
        if (day == null) return "/hom-nay";
        return "/lo-trinh?day=" + day + (slot == null ? "" : "&slot=" + slot);
    }

    @GetMapping("/practice/mini-test")
    public String miniTest(@RequestParam(value = "skill", defaultValue = "reading") String skill, Model model) {
        model.addAttribute("pageTitle", "Mini Test TOPIK");
        model.addAttribute("activePage", "exam");
        model.addAttribute("skill", skill);
        return "practice/mini-test";
    }
}
