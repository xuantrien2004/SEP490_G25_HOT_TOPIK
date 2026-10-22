package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.*;
import com.hottopik.service.PracticeService;
import com.hottopik.service.PracticeService.QuestionView;
import com.hottopik.service.PracticeService.TypeSummary;
import com.hottopik.service.exam.QuestionTypeCatalog;
import com.hottopik.service.exam.QuestionTypeDef;
import com.hottopik.entity.exam.PracticeAttempt;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class PracticeController {
    private final PracticeService practice;

    public PracticeController(PracticeService practice) {
        this.practice = practice;
    }

    @GetMapping({"/luyen-tap", "/practice"})
    public String practiceIndex(@RequestParam(value = "skill", required = false) String initialSkill,
                                  @RequestParam(value = "track", required = false) String initialTrack,
                                  Model model, HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        Long userId = user == null ? null : user.getId();
        String email = user == null ? null : user.getEmail();
        List<TypeSummary> summaries = practice.allSummaries(userId, email);

        // Tách riêng theo kỹ năng cho 3 tabs: NGHE, ĐỌC, VIẾT
        List<TypeSummary> listeningI = summaries.stream()
                .filter(s -> s.type().skill() == Skill.LISTENING && s.type().track() == Track.TOPIK_I).toList();
        List<TypeSummary> listeningII = summaries.stream()
                .filter(s -> s.type().skill() == Skill.LISTENING && s.type().track() == Track.TOPIK_II).toList();

        List<TypeSummary> readingI = summaries.stream()
                .filter(s -> s.type().skill() == Skill.READING && s.type().track() == Track.TOPIK_I).toList();
        List<TypeSummary> readingII = summaries.stream()
                .filter(s -> s.type().skill() == Skill.READING && s.type().track() == Track.TOPIK_II).toList();

        List<TypeSummary> writingII = summaries.stream()
                .filter(s -> s.type().skill() == Skill.WRITING).toList();

        model.addAttribute("pageTitle", "Luyện tập theo dạng câu · HOT TOPIK");
        model.addAttribute("activePage", "practice");
        model.addAttribute("listeningI", listeningI);
        model.addAttribute("listeningII", listeningII);
        model.addAttribute("readingI", readingI);
        model.addAttribute("readingII", readingII);
        model.addAttribute("writingII", writingII);
        model.addAttribute("currentUser", user);
        model.addAttribute("initialSkill", initialSkill);
        model.addAttribute("initialTrack", initialTrack);
        return "practice/index";
    }

    @GetMapping({"/practice/prepare", "/luyen-tap/chuan-bi"})
    public String preparePractice(@RequestParam("typeCode") String typeCode, Model model, HttpSession session) {
        QuestionTypeDef type = QuestionTypeCatalog.byCode(typeCode).orElseThrow(() -> new IllegalArgumentException("Dạng câu không hợp lệ: " + typeCode));
        UserDTO user = AuthHelper.getLoggedInUser(session);
        Long userId = user != null ? user.getId() : 1L;
        String email = user != null ? user.getEmail() : null;

        TypeSummary summary = practice.allSummaries(userId, email).stream()
                .filter(s -> s.type().code().equals(type.code())).findFirst()
                .orElse(new TypeSummary(type, 0, 0, null, null));

        List<PracticeAttempt> history = practice.historyOfType(userId, typeCode);

        model.addAttribute("pageTitle", "Chuẩn bị bài luyện · HOT TOPIK");
        model.addAttribute("activePage", "practice");
        model.addAttribute("type", type);
        model.addAttribute("summary", summary);
        model.addAttribute("history", history);
        model.addAttribute("currentUser", user);
        return "practice/prepare";
    }

    @PostMapping("/practice/start")
    public String startPractice(@RequestParam("typeCode") String typeCode, HttpSession session, RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        Long userId = user != null ? user.getId() : 1L;
        String email = user != null ? user.getEmail() : "learner@hottopik.com";

        QuestionTypeDef type = QuestionTypeCatalog.byCode(typeCode).orElseThrow(() -> new IllegalArgumentException("Dạng câu không hợp lệ: " + typeCode));
        if (type.isWriting()) {
            PracticeService.TypeSummary summary = practice.allSummaries(userId, email).stream()
                    .filter(s -> s.type().code().equals(typeCode)).findFirst().orElse(null);
            if (summary == null || summary.available() == 0) {
                flash.addFlashAttribute("practiceError", "Dạng câu này hiện chưa có bài luyện. Bạn có thể chọn dạng câu khác.");
                return "redirect:/practice/prepare?typeCode=" + typeCode;
            }
            return "redirect:/writing?question=" + type.numbers().get(0);
        }
        try {
            PracticeAttempt attempt = practice.start(userId, email, typeCode);
            return "redirect:/practice/play/" + attempt.getId();
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("practiceError", e.getMessage());
            return "redirect:/practice/prepare?typeCode=" + typeCode;
        }
    }

    /**
     * Làm lại bài luyện tập (giữ nguyên bộ câu hỏi của attempt cũ)
     */
    @GetMapping("/practice/retry/{attemptId}")
    public String retryPractice(@PathVariable Long attemptId, HttpSession session, RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        Long userId = user != null ? user.getId() : 1L;
        String email = user != null ? user.getEmail() : "learner@hottopik.com";

        try {
            PracticeAttempt newAttempt = practice.retry(userId, email, attemptId);
            return "redirect:/practice/play/" + newAttempt.getId();
        } catch (Exception e) {
            flash.addFlashAttribute("practiceError", "Không thể làm lại bài này: " + e.getMessage());
            return "redirect:/practice";
        }
    }

    /**
     * Luyện tập tiếp dạng câu này (hệ thống random câu hỏi mới)
     */
    @GetMapping("/practice/next")
    public String nextPractice(@RequestParam("typeCode") String typeCode, HttpSession session, RedirectAttributes flash) {
        return startPractice(typeCode, session, flash);
    }

    @GetMapping("/practice/play/{attemptId}")
    public String playPractice(@PathVariable Long attemptId, Model model, HttpSession session, RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        Long userId = user != null ? user.getId() : 1L;

        try {
            PracticeService.ResultView result = practice.result(userId, attemptId);
            if (result.attempt().getCompletedAt() != null) {
                return "redirect:/practice/result/" + attemptId;
            }
            List<QuestionView> questions = practice.questions(result.attempt());
            if (questions.isEmpty()) {
                flash.addFlashAttribute("practiceError", "Không tìm thấy câu hỏi trong bài làm này.");
                return "redirect:/practice";
            }
            model.addAttribute("attempt", result.attempt());
            model.addAttribute("type", result.type());
            model.addAttribute("questions", questions);
            model.addAttribute("pageTitle", "Làm bài luyện: " + result.type().name() + " · HOT TOPIK");
            model.addAttribute("activePage", "practice");
            model.addAttribute("currentUser", user);
            return "practice/play";
        } catch (Exception e) {
            flash.addFlashAttribute("practiceError", "Không tìm thấy lượt làm bài: " + e.getMessage());
            return "redirect:/practice";
        }
    }

    @PostMapping("/practice/submit/{attemptId}")
    public String submitPractice(@PathVariable Long attemptId, @RequestParam MultiValueMap<String, String> form,
                                 HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        Long userId = user != null ? user.getId() : 1L;

        Map<String, String> answers = form.entrySet().stream()
                .filter(e -> e.getKey().startsWith("answer_"))
                .collect(Collectors.toMap(e -> e.getKey().substring("answer_".length()), e -> e.getValue().get(0)));
        practice.submit(userId, attemptId, answers);
        return "redirect:/practice/result/" + attemptId;
    }

    @GetMapping("/practice/result/{attemptId}")
    public String practiceResult(@PathVariable Long attemptId, Model model, HttpSession session, RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        Long userId = user != null ? user.getId() : 1L;

        try {
            PracticeService.ResultView result = practice.result(userId, attemptId);
            if (result.attempt().getCompletedAt() == null) {
                return "redirect:/practice/play/" + attemptId;
            }
            model.addAttribute("result", result);
            model.addAttribute("pageTitle", "Kết quả luyện tập: " + result.type().name() + " · HOT TOPIK");
            model.addAttribute("activePage", "practice");
            model.addAttribute("currentUser", user);
            return "practice/result";
        } catch (Exception e) {
            flash.addFlashAttribute("practiceError", "Không tìm thấy kết quả lượt làm bài.");
            return "redirect:/practice";
        }
    }

    @GetMapping({"/luyen-tap/viet/{cau}", "/practice/writing/{cau}"})
    public String practiceWriting(@PathVariable("cau") int cau) {
        return "redirect:/writing?question=" + cau;
    }

    @GetMapping({"/de-thi", "/exam"})
    public String examIndex(Model model) {
        model.addAttribute("pageTitle", "Đề thi · Full Mock & Mini Test · HOT TOPIK");
        model.addAttribute("activePage", "exam");
        return "exam/index";
    }

    @GetMapping("/practice/mini-test")
    public String miniTest(@RequestParam(value = "skill", defaultValue = "reading") String skill, Model model) {
        model.addAttribute("pageTitle", "Mini Test TOPIK");
        model.addAttribute("activePage", "exam");
        model.addAttribute("skill", skill);
        return "practice/mini-test";
    }
}
