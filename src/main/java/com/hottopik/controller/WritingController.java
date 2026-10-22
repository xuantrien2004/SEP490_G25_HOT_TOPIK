package com.hottopik.controller;

import com.hottopik.entity.exam.QuestionStatus;
import com.hottopik.entity.exam.WritingPrompt;
import com.hottopik.repository.exam.WritingPromptRepository;
import com.hottopik.dto.UserDTO;
import com.hottopik.dto.WritingSubmissionDTO;
import com.hottopik.dto.WritingSubmissionDTO.ChartItem;
import com.hottopik.service.UserService;
import com.hottopik.service.WritingSubmissionService;
import com.hottopik.util.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.util.*;

@Controller
public class WritingController {

    private final WritingSubmissionService writingSubmissionService;
    private final UserService userService;
    private final WritingPromptRepository writingPromptRepository;

    @Autowired
    public WritingController(WritingSubmissionService writingSubmissionService,
                             UserService userService,
                             WritingPromptRepository writingPromptRepository) {
        this.writingSubmissionService = writingSubmissionService;
        this.userService = userService;
        this.writingPromptRepository = writingPromptRepository;
    }

    /**
     * VietV2: Writing practice for Câu 51, 52, 53, 54 with AI evaluation & send to Mentor
     */
    @GetMapping("/writing")
    public String writing(@RequestParam(value = "question", defaultValue = "53") int question,
                          @RequestParam(value = "promptId", required = false) Long promptId,
                          @RequestParam(value = "random", required = false, defaultValue = "false") boolean random,
                          Model model,
                          HttpSession session) {

        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("pageTitle", "Luyện Viết TOPIK II · Câu " + question);
        model.addAttribute("activePage", "writing");
        model.addAttribute("currentQuestion", question);

        // Lấy đề từ ngân hàng câu hỏi do Content Manager tạo
        List<WritingPrompt> bankPrompts = writingPromptRepository.findByNoAndStatusOrderByIdDesc(question, QuestionStatus.ACTIVE);
        WritingPrompt currentPrompt = null;
        if (!bankPrompts.isEmpty()) {
            if (promptId != null) {
                currentPrompt = bankPrompts.stream().filter(p -> p.getId().equals(promptId)).findFirst().orElse(null);
            }
            if (currentPrompt == null) {
                if (random && bankPrompts.size() > 1) {
                    currentPrompt = bankPrompts.get(new Random().nextInt(bankPrompts.size()));
                } else {
                    currentPrompt = bankPrompts.get(0);
                }
            }
        }
        model.addAttribute("bankPrompts", bankPrompts);
        model.addAttribute("currentPrompt", currentPrompt);

        // Submissions count for current user
        String email = currentUser != null ? currentUser.getEmail() : "learner@hottopik.com";
        List<WritingSubmissionDTO> mySubs = writingSubmissionService.getSubmissionsByLearner(email);
        long gradedCount = mySubs.stream().filter(s -> "graded".equalsIgnoreCase(s.getStatus())).count();
        model.addAttribute("mySubmissionsCount", mySubs.size());
        model.addAttribute("myGradedCount", gradedCount);

        return "writing/index";
    }

    /**
     * BaiChamV2: Student viewing their submissions and graded feedback from Teacher / Mentor
     */
    @GetMapping("/writing/feedback")
    public String feedback(@RequestParam(value = "id", required = false) String id,
                           Model model,
                           HttpSession session) {

        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        String email = currentUser != null ? currentUser.getEmail() : "learner@hottopik.com";
        List<WritingSubmissionDTO> mySubs = writingSubmissionService.getSubmissionsByLearner(email);

        // If no submissions for learner yet, also include mock sub-105 for demo display
        if (mySubs.isEmpty()) {
            Optional<WritingSubmissionDTO> demoSub = writingSubmissionService.getById("sub-105");
            demoSub.ifPresent(mySubs::add);
        }

        WritingSubmissionDTO currentSub = null;
        if (id != null && !id.trim().isEmpty()) {
            currentSub = writingSubmissionService.getById(id).orElse(null);
        }
        if (currentSub == null && !mySubs.isEmpty()) {
            // Prefer graded first, or the latest
            currentSub = mySubs.stream()
                    .filter(s -> "graded".equalsIgnoreCase(s.getStatus()))
                    .findFirst()
                    .orElse(mySubs.get(0));
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("pageTitle", "Bài Viết Giáo viên chấm · HOT TOPIK");
        model.addAttribute("activePage", "writing");
        model.addAttribute("mySubmissions", mySubs);
        model.addAttribute("sub", currentSub);

        return "writing/feedback";
    }

    /**
     * Learner submits essay to Teacher / Mentor
     */
    @PostMapping("/writing/submit-mentor")
    public String submitToMentor(@RequestParam("questionNo") int questionNo,
                                 @RequestParam("prompt") String prompt,
                                 @RequestParam("answer") String answer,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {

        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        String name = currentUser != null ? currentUser.getFullName() : "Phạm Minh Anh";
        String email = currentUser != null ? currentUser.getEmail() : "learner@hottopik.com";

        int wordCount = answer != null ? answer.trim().length() : 0;
        boolean hasChart = (questionNo == 53);
        List<ChartItem> chartItems = null;
        if (hasChart) {
            chartItems = Arrays.asList(
                    new ChartItem("운동", 35, "105px"),
                    new ChartItem("여행", 26, "78px"),
                    new ChartItem("게임", 24, "72px"),
                    new ChartItem("독서", 9, "27px")
            );
        }

        WritingSubmissionDTO newSub = writingSubmissionService.submitNewEssay(
                name, email, questionNo, prompt, answer, wordCount, hasChart, chartItems
        );

        redirectAttributes.addFlashAttribute("successMessage",
                "Đã gửi bài viết câu " + questionNo + " thành công cho Giáo viên chấm! Giáo viên sẽ hoàn tất chấm và nhận xét trong vòng 48 giờ.");

        return "redirect:/writing/feedback?id=" + newSub.getId();
    }

    /**
     * Learner asks a follow-up question to the teacher
     */
    @PostMapping("/writing/ask")
    public String askTeacher(@RequestParam("id") String id,
                             @RequestParam("question") String question,
                             RedirectAttributes redirectAttributes) {
        if (question == null || question.trim().length() < 5) {
            redirectAttributes.addFlashAttribute("errorMessage", "Câu hỏi cần ít nhất 5 ký tự.");
            return "redirect:/writing/feedback?id=" + id;
        }

        boolean ok = writingSubmissionService.askQuestion(id, question);
        if (ok) {
            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi câu hỏi thắc mắc tới Giáo viên. Giáo viên sẽ phản hồi sớm nhất!");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể gửi câu hỏi.");
        }
        return "redirect:/writing/feedback?id=" + id;
    }

    /**
     * Learner rates the grading quality (1 to 5 stars)
     */
    @PostMapping("/writing/rate")
    public String rateTeacher(@RequestParam("id") String id,
                              @RequestParam("rating") int rating,
                              RedirectAttributes redirectAttributes) {
        writingSubmissionService.rateSubmission(id, rating);
        redirectAttributes.addFlashAttribute("successMessage", "Cảm ơn bạn đã đánh giá " + rating + " sao cho bài chữa của Giáo viên!");
        return "redirect:/writing/feedback?id=" + id;
    }

    /**
     * Learner appeals the grading score
     */
    @PostMapping("/writing/appeal")
    public String appealGrade(@RequestParam("id") String id,
                              @RequestParam("reason") String reason,
                              RedirectAttributes redirectAttributes) {
        if (reason == null || reason.trim().length() < 10) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng nêu rõ lý do khiếu nại (tối thiểu 10 ký tự).");
            return "redirect:/writing/feedback?id=" + id;
        }

        boolean ok = writingSubmissionService.appealSubmission(id, reason);
        if (ok) {
            redirectAttributes.addFlashAttribute("successMessage", "Yêu cầu khiếu nại đã được gửi tới hội đồng Giáo viên để chấm thẩm định lại.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể gửi yêu cầu khiếu nại.");
        }
        return "redirect:/writing/feedback?id=" + id;
    }
}
