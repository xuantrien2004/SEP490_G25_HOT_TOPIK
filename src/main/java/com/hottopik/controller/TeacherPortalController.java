package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.dto.WritingSubmissionDTO;
import com.hottopik.dto.WritingSubmissionDTO.CriterionScore;
import com.hottopik.service.UserService;
import com.hottopik.service.WritingSubmissionService;
import com.hottopik.util.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class TeacherPortalController {

    private final WritingSubmissionService writingSubmissionService;
    private final UserService userService;

    @Autowired
    public TeacherPortalController(WritingSubmissionService writingSubmissionService, UserService userService) {
        this.writingSubmissionService = writingSubmissionService;
        this.userService = userService;
    }

    @RequestMapping(value = {"/teacher", "/mentor"}, method = {RequestMethod.GET, RequestMethod.POST})
    public String dashboard(@RequestParam(value = "tab", defaultValue = "queue") String tab,
                            @RequestParam(value = "qf", defaultValue = "0") int qf,
                            @RequestParam(value = "sel", required = false) String sel,
                            Model model,
                            HttpSession session) {

        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        if (currentUser == null) {
            // For smooth development & demo, auto-mock as Teacher "Cô Mai"
            currentUser = userService.findByEmail("teacher@hottopik.com").orElse(new UserDTO(
                    6L, "teacher", "teacher@hottopik.com", "teacher@123",
                    "Cô Mai (Giáo viên TOPIK)", "ROLE_TEACHER", "Giáo viên (Mentor)",
                    "ACTIVE", "https://api.dicebear.com/7.x/bottts/svg?seed=teacher",
                    "0967890123", "TOPIK_6", "TOPIK_6", false, null
            ));
            session.setAttribute(AuthHelper.SESSION_KEY, currentUser);
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("currentTab", tab);
        model.addAttribute("currentQf", qf);
        model.addAttribute("pageTitle", "Khu Giáo viên · Chấm bài Viết TOPIK");

        // Tab counts
        List<WritingSubmissionDTO> all = writingSubmissionService.getAllSubmissions();
        long queueCount = all.stream().filter(s -> "waiting".equalsIgnoreCase(s.getStatus())).count();
        long mineCount = all.stream().filter(s -> "claimed".equalsIgnoreCase(s.getStatus())).count();
        long doneCount = all.stream().filter(s -> "graded".equalsIgnoreCase(s.getStatus())).count();
        long appealCount = all.stream().filter(s -> "appeal".equalsIgnoreCase(s.getStatus())).count();

        model.addAttribute("queueCount", queueCount);
        model.addAttribute("mineCount", mineCount);
        model.addAttribute("doneCount", doneCount);
        model.addAttribute("appealCount", appealCount);

        // If sel is provided, show the grading desk
        if (sel != null && !sel.trim().isEmpty()) {
            Optional<WritingSubmissionDTO> selectedOpt = writingSubmissionService.getById(sel);
            if (selectedOpt.isPresent()) {
                WritingSubmissionDTO selected = selectedOpt.get();
                model.addAttribute("showDesk", true);
                model.addAttribute("deskItem", selected);
                model.addAttribute("isClaimedByMe", "claimed".equalsIgnoreCase(selected.getStatus()) || "appeal".equalsIgnoreCase(selected.getStatus()));
                return "teacher/dashboard";
            }
        }

        // Otherwise show submission list for current tab
        model.addAttribute("showDesk", false);
        List<WritingSubmissionDTO> tabList;
        switch (tab.toLowerCase()) {
            case "mine":
                tabList = all.stream()
                        .filter(s -> "claimed".equalsIgnoreCase(s.getStatus()))
                        .collect(Collectors.toList());
                break;
            case "done":
                tabList = all.stream()
                        .filter(s -> "graded".equalsIgnoreCase(s.getStatus()))
                        .collect(Collectors.toList());
                break;
            case "appeal":
                tabList = all.stream()
                        .filter(s -> "appeal".equalsIgnoreCase(s.getStatus()))
                        .collect(Collectors.toList());
                break;
            case "calib":
                tabList = Collections.emptyList();
                break;
            case "queue":
            default:
                tabList = all.stream()
                        .filter(s -> "waiting".equalsIgnoreCase(s.getStatus()))
                        .collect(Collectors.toList());
                break;
        }

        // Apply question filter
        if (qf > 0) {
            tabList = tabList.stream()
                    .filter(s -> s.getQuestionNo() == qf)
                    .collect(Collectors.toList());
        }

        model.addAttribute("submissionList", tabList);
        return "teacher/dashboard";
    }

    @PostMapping("/teacher/claim")
    public String claim(@RequestParam("id") String id,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        String teacherName = user != null ? user.getFullName() : "Cô Mai";
        boolean ok = writingSubmissionService.claimSubmission(id, teacherName);
        if (ok) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã nhận bài thành công! Bạn có 48 giờ để hoàn tất chấm.");
            return "redirect:/teacher?tab=mine&sel=" + id;
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể nhận bài này hoặc bài đã có giáo viên khác nhận.");
            return "redirect:/teacher?tab=queue";
        }
    }

    @PostMapping("/teacher/grade")
    public String grade(@RequestParam("id") String id,
                        @RequestParam("overallFeedback") String overallFeedback,
                        @RequestParam(value = "modelAnswer", required = false) String modelAnswer,
                        HttpServletRequest request,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {

        UserDTO user = AuthHelper.getLoggedInUser(session);
        String teacherName = user != null ? user.getFullName() : "Cô Mai";

        Optional<WritingSubmissionDTO> subOpt = writingSubmissionService.getById(id);
        if (!subOpt.isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy bài nộp.");
            return "redirect:/teacher?tab=mine";
        }

        WritingSubmissionDTO sub = subOpt.get();
        Map<String, Integer> criteriaScoresMap = new HashMap<>();

        // Read criteria scores from request params rb-0, rb-1...
        for (int i = 0; i < sub.getCriteriaScores().size(); i++) {
            CriterionScore cs = sub.getCriteriaScores().get(i);
            String valStr = request.getParameter("criterion_" + i);
            if (valStr != null && !valStr.trim().isEmpty()) {
                try {
                    int sc = Integer.parseInt(valStr.trim());
                    criteriaScoresMap.put(cs.getName(), sc);
                } catch (NumberFormatException ignored) {}
            }
        }

        // Model answer is optional as requested by user!
        boolean ok = writingSubmissionService.gradeSubmission(id, teacherName, criteriaScoresMap, overallFeedback, modelAnswer);
        if (ok) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi kết quả chấm bài thành công cho học viên " + sub.getLearnerName() + "!");
            return "redirect:/teacher?tab=done&sel=" + id;
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Có lỗi xảy ra khi lưu kết quả chấm bài.");
            return "redirect:/teacher?tab=mine&sel=" + id;
        }
    }

    @PostMapping("/teacher/reply-ask")
    public String replyAsk(@RequestParam("id") String id,
                           @RequestParam("reply") String reply,
                           RedirectAttributes redirectAttributes) {
        if (reply == null || reply.trim().length() < 3) {
            redirectAttributes.addFlashAttribute("errorMessage", "Câu trả lời cần ít nhất 3 ký tự.");
            return "redirect:/teacher?tab=done&sel=" + id;
        }
        boolean ok = writingSubmissionService.replyQuestion(id, reply);
        if (ok) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi câu trả lời giải đáp cho học viên!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể gửi câu trả lời.");
        }
        return "redirect:/teacher?tab=done&sel=" + id;
    }
}
