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
    @Autowired private com.hottopik.service.exam.ExamImportService imports;

    @Autowired
    public WritingController(WritingSubmissionService writingSubmissionService,
                             UserService userService,
                             WritingPromptRepository writingPromptRepository) {
        this.writingSubmissionService = writingSubmissionService;
        this.userService = userService;
        this.writingPromptRepository = writingPromptRepository;
    }

    /**
     * Luyện Viết câu 51–54: nộp bài, đối chiếu đáp án mẫu và gửi Giáo viên.
     */
    /*
     * Luồng Viết riêng: lấy WritingPrompt ACTIVE theo số 51..54; ưu tiên promptId hợp lệ, sau đó random hoặc đề mới nhất.
     * Truyền currentPrompt/bankPrompts sang writing/index; không có đề thì template chỉ hiện cảnh báo.
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
        if(currentPrompt!=null && currentPrompt.getChartJson()!=null && !currentPrompt.getChartJson().isBlank()) {
            try {
                var rows = new com.fasterxml.jackson.databind.ObjectMapper().readTree(currentPrompt.getChartJson());
                List<Map<String,String>> chartRows=new ArrayList<>();
                for(var row:rows)chartRows.add(Map.of("label",row.isArray()?row.path(0).asText():row.path("label").asText(),"value",row.isArray()?row.path(1).asText():row.path("value").asText()));
                model.addAttribute("writingChart",chartRows);
            }
            catch(Exception ex){model.addAttribute("writingChart",null);}
        }

        // Submissions count for current user
        String email = currentUser != null ? currentUser.getEmail() : "learner@hottopik.com";
        List<WritingSubmissionDTO> mySubs = currentUser == null ? List.of() : writingSubmissionService.getSubmissionsByLearner(currentUser.getId());
        long gradedCount = mySubs.stream().filter(s -> "graded".equalsIgnoreCase(s.getStatus())).count();
        model.addAttribute("mySubmissionsCount", mySubs.size());
        model.addAttribute("myGradedCount", gradedCount);

        // Load draft for this question (if any)
        if (currentUser != null && currentPrompt != null) {
            WritingSubmissionDTO draft = writingSubmissionService.getDraft(currentUser.getId(), question);
            if (draft != null) {
                model.addAttribute("draftAnswer", draft.getStudentAnswer());
                model.addAttribute("draftId", draft.getId());
            }
        }

        return "writing/index";
    }

    /**
     * Auto-save draft: AJAX POST /writing/draft
     */
    @PostMapping("/writing/draft")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> saveDraft(
            @RequestParam Long promptId,
            @RequestParam(defaultValue = "") String answer,
            HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return org.springframework.http.ResponseEntity.status(401).build();
        try {
            WritingSubmissionDTO draft = writingSubmissionService.saveDraft(user, promptId, answer);
            return org.springframework.http.ResponseEntity.ok(Map.of("id", draft.getId(), "status", "draft"));
        } catch (Exception ex) {
            return org.springframework.http.ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Xem chi tiết bài luyện Viết đã nộp (chưa gửi GV) — route /writing/history?id=XX
     */
    @GetMapping("/writing/history")
    public String history(@RequestParam String id, Model model, HttpSession session,
                          RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        WritingSubmissionDTO sub = writingSubmissionService.owned(id, user.getId());
        // Nếu bài đã được gửi GV → chuyển sang trang feedback
        if (!"submitted".equalsIgnoreCase(sub.getStatus()) && !"draft".equalsIgnoreCase(sub.getStatus())) {
            return "redirect:/writing/feedback?id=" + id;
        }
        model.addAttribute("currentUser", user);
        model.addAttribute("pageTitle", "Chi tiết bài luyện Viết · HOT TOPIK");
        model.addAttribute("activePage", "mentor-reviews");
        model.addAttribute("sub", sub);
        model.addAttribute("canSendTeacher", WritingSubmissionService.hasAnswer(sub.getQuestionNo(), sub.getStudentAnswer()));
        return "writing/history";
    }

    /**
     * BaiChamV2: Student viewing their submissions and graded feedback from Teacher / Mentor
     */

    @GetMapping({"/writing/feedback", "/mentor-reviews", "/bai-cham"})
    public String feedback(@RequestParam(value = "id", required = false) String id,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           Model model,
                           HttpSession session) {

        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        if (currentUser == null) return "redirect:/login";
        // Chỉ hiện bài đã gửi GV (waiting/claimed/graded/appeal), mới nhất trước
        List<WritingSubmissionDTO> allSubs = writingSubmissionService.getSubmissionsByLearner(currentUser.getId()).stream()
                .filter(s -> (!"submitted".equalsIgnoreCase(s.getStatus()) || (s.getRejectReason() != null && !s.getRejectReason().isBlank())) && !"draft".equalsIgnoreCase(s.getStatus())).toList();

        // Group by examAttemptId (null = luyện tập riêng lẻ)
        // Key: examAttemptId (null → dùng id bài), Value: list câu trong cùng đề
        java.util.LinkedHashMap<String, List<WritingSubmissionDTO>> grouped = new java.util.LinkedHashMap<>();
        for (WritingSubmissionDTO s : allSubs) {
            String key = s.getExamAttemptId() != null ? "exam-" + s.getExamAttemptId() : "solo-" + s.getId();
            grouped.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(s);
        }
        List<java.util.Map.Entry<String, List<WritingSubmissionDTO>>> groups = new java.util.ArrayList<>(grouped.entrySet());

        // Phân trang: 10 group mỗi trang
        int pageSize = 10;
        int totalPages = Math.max(1, (int) Math.ceil((double) groups.size() / pageSize));
        page = Math.max(0, Math.min(page, totalPages - 1));
        List<java.util.Map.Entry<String, List<WritingSubmissionDTO>>> pageGroups =
                groups.subList(page * pageSize, Math.min((page + 1) * pageSize, groups.size()));

        WritingSubmissionDTO currentSub = null;
        if (id != null && !id.trim().isEmpty()) {
            currentSub = writingSubmissionService.owned(id, currentUser.getId());
            if (("submitted".equalsIgnoreCase(currentSub.getStatus()) && (currentSub.getRejectReason() == null || currentSub.getRejectReason().isBlank())) || "draft".equalsIgnoreCase(currentSub.getStatus())) {
                return "redirect:/writing/history?id=" + id;
            }
        } else if (!allSubs.isEmpty()) {
            currentSub = allSubs.get(0);
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("pageTitle", "Bài viết gửi Giáo viên · HOT TOPIK");
        model.addAttribute("activePage", "mentor-reviews");
        model.addAttribute("mySubmissions", allSubs);
        model.addAttribute("groupedSubs", pageGroups);
        model.addAttribute("sub", currentSub);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("canSendTeacher", currentSub != null && WritingSubmissionService.hasAnswer(currentSub.getQuestionNo(), currentSub.getStudentAnswer()));

        return "writing/feedback";
    }


    @GetMapping("/writing/prompt/{id}/chart")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> promptChart(@PathVariable Long id,HttpSession session) throws java.io.IOException {
        if(AuthHelper.getLoggedInUser(session)==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        var p=writingPromptRepository.findById(id).filter(w->w.getStatus()==QuestionStatus.ACTIVE).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        return chart(p);
    }
    @GetMapping("/writing/submission/{id}/chart")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> submissionChart(@PathVariable String id,HttpSession session) throws java.io.IOException {
        UserDTO user=AuthHelper.getLoggedInUser(session);if(user==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        var sub=user.isMentor()||user.isAdmin()?writingSubmissionService.getById(id).orElseThrow():writingSubmissionService.owned(id,user.getId());
        return chart(sub.getSourceId(),sub.getChartFilename());
    }
    private org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> chart(WritingPrompt p) throws java.io.IOException {
        return chart(p.getSource()==null?null:p.getSource().getId(),p.getChartImage());
    }
    private org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> chart(Long sourceId,String filename) throws java.io.IOException {
        if(sourceId==null || filename==null)return org.springframework.http.ResponseEntity.notFound().build();
        var asset=imports.findMedia(sourceId,filename);if(asset.isEmpty())return org.springframework.http.ResponseEntity.notFound().build();
        var file=imports.mediaPath(asset.get());if(!java.nio.file.Files.exists(file))return org.springframework.http.ResponseEntity.notFound().build();
        var type=java.nio.file.Files.probeContentType(file);
        return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(type==null?"application/octet-stream":type)).body(new org.springframework.core.io.FileSystemResource(file));
    }
    @PostMapping({"/writing/submit", "/writing/submit-mentor"})
    public String submit(@RequestParam Long promptId, @RequestParam String answer,
                         @RequestParam(defaultValue="false") boolean teacher,
                         jakarta.servlet.http.HttpServletRequest request, HttpSession session, RedirectAttributes flash) {
        UserDTO user=AuthHelper.getLoggedInUser(session);
        if(user==null) return "redirect:/login";
        boolean toTeacher=teacher || request.getRequestURI().endsWith("/submit-mentor");
        try {
            WritingSubmissionDTO sub=writingSubmissionService.submit(user,promptId,answer,toTeacher);
            flash.addFlashAttribute("successMessage",toTeacher ? "Đã gửi bài cho Giáo viên. Bạn sẽ nhận thông báo khi có kết quả; bài đã khóa chỉnh sửa." : "Đã nộp bài. Bạn có thể đối chiếu đáp án tham khảo; bài đã khóa chỉnh sửa.");
            return "redirect:/writing/feedback?id="+sub.getId();
        } catch(IllegalArgumentException ex) {
            flash.addFlashAttribute("errorMessage",ex.getMessage());
            flash.addFlashAttribute("draftAnswer",answer);
            int no=writingPromptRepository.findById(promptId).map(WritingPrompt::getNo).orElse(53);
            return "redirect:/writing?question="+no+"&promptId="+promptId;
        }
    }
    @PostMapping("/writing/send-teacher")
    public String sendTeacher(@RequestParam String id,HttpSession session,RedirectAttributes flash) {
        UserDTO user=AuthHelper.getLoggedInUser(session);if(user==null)return "redirect:/login";
        try {
            writingSubmissionService.sendToTeacher(id,user);
            flash.addFlashAttribute("successMessage","Đã gửi bài cho Giáo viên. Vui lòng chờ thông báo kết quả.");
        } catch(IllegalArgumentException ex) { flash.addFlashAttribute("errorMessage",ex.getMessage()); }
        return "redirect:/writing/feedback?id="+id;
    }
    @PostMapping("/writing/ask")
    public String ask(@RequestParam String id,@RequestParam String question,HttpSession session,RedirectAttributes flash) {
        UserDTO user=AuthHelper.getLoggedInUser(session);if(user==null)return "redirect:/login";
        boolean ok=writingSubmissionService.askQuestion(id,user.getId(),question);
        flash.addFlashAttribute(ok?"successMessage":"errorMessage",ok?"Đã gửi câu hỏi.":"Không thể gửi câu hỏi.");
        return "redirect:/writing/feedback?id="+id;
    }
    @PostMapping("/writing/rate")
    public String rate(@RequestParam String id,@RequestParam int rating,HttpSession session,RedirectAttributes flash) {
        UserDTO user=AuthHelper.getLoggedInUser(session);if(user==null)return "redirect:/login";
        boolean ok=writingSubmissionService.rateSubmission(id,user.getId(),rating);
        flash.addFlashAttribute(ok?"successMessage":"errorMessage",ok?"Đã gửi đánh giá.":"Không thể gửi đánh giá.");
        return "redirect:/writing/feedback?id="+id;
    }
    @PostMapping("/writing/appeal")
    public String appeal(@RequestParam String id,@RequestParam String reason,HttpSession session,RedirectAttributes flash) {
        UserDTO user=AuthHelper.getLoggedInUser(session);if(user==null)return "redirect:/login";
        try {
            boolean ok=writingSubmissionService.appealSubmission(id,user.getId(),reason);
            flash.addFlashAttribute(ok?"successMessage":"errorMessage",ok?"Đã gửi khiếu nại. Mỗi bài chỉ được khiếu nại một lần.":"Bài chưa được chấm hoặc đã gửi khiếu nại.");
        } catch(IllegalArgumentException ex) { flash.addFlashAttribute("errorMessage",ex.getMessage()); }
        return "redirect:/writing/feedback?id="+id;
    }
}

