package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.dto.WritingSubmissionDTO;
import com.hottopik.dto.WritingSubmissionDTO.CriterionScore;
import com.hottopik.service.UserService;
import com.hottopik.service.WritingSubmissionService;
import com.hottopik.util.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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
            return "redirect:/login";
        }
        if (!currentUser.isMentor() && !currentUser.isAdmin()) {
            return "redirect:/today";
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

    // ==========================================
    // TEACHER / MENTOR PROFILE ENDPOINTS
    // ==========================================

    @GetMapping("/teacher/profile")
    public String teacherProfile(HttpSession session, Model model) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        if (currentUser == null) return "redirect:/login";
        if (!currentUser.isMentor() && !currentUser.isAdmin()) {
            return "redirect:/today";
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("pageTitle", "Hồ sơ Giáo viên (Mentor Profile) · HOT TOPIK");

        // Compute teacher stats
        List<WritingSubmissionDTO> all = writingSubmissionService.getAllSubmissions();
        long queueCount = all.stream().filter(s -> "waiting".equalsIgnoreCase(s.getStatus())).count();
        long mineCount = all.stream().filter(s -> "claimed".equalsIgnoreCase(s.getStatus())).count();
        long doneCount = all.stream().filter(s -> "graded".equalsIgnoreCase(s.getStatus())).count();

        model.addAttribute("queueCount", queueCount);
        model.addAttribute("mineCount", mineCount);
        model.addAttribute("doneCount", doneCount);

        return "teacher/profile";
    }

    @PostMapping("/teacher/profile/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateTeacherProfile(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!currentUser.isMentor() && !currentUser.isAdmin())) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        String fullName = (String) payload.get("fullName");
        String phone = (String) payload.get("phone");
        String avatar = (String) payload.get("avatar");
        String bio = (String) payload.get("bio");
        String specialty = (String) payload.get("specialty");
        String timezone = (String) payload.get("timezone");

        if (fullName == null || fullName.trim().length() < 2 || fullName.trim().length() > 40) {
            resp.put("success", false);
            resp.put("message", "Họ và tên phải từ 2 đến 40 ký tự!");
            return ResponseEntity.badRequest().body(resp);
        }

        boolean ok = userService.updateUserProfile(currentUser.getId(), fullName, phone, avatar, bio, specialty, timezone);
        if (ok) {
            currentUser.setFullName(fullName.trim());
            currentUser.setDisplayName(fullName.trim());
            if (phone != null) currentUser.setPhone(phone.trim().isEmpty() ? null : phone.trim());
            if (avatar != null) currentUser.setAvatar(avatar.trim().isEmpty() ? null : avatar.trim());
            if (bio != null) currentUser.setBio(bio.trim().isEmpty() ? null : bio.trim());
            if (specialty != null) currentUser.setSpecialty(specialty.trim().isEmpty() ? null : specialty.trim());
            if (timezone != null) currentUser.setTimezone(timezone.trim().isEmpty() ? null : timezone.trim());

            session.setAttribute("LOGGED_IN_USER", currentUser);

            resp.put("success", true);
            resp.put("user", currentUser);
            resp.put("message", "Đã cập nhật hồ sơ Giáo viên thành công!");
            return ResponseEntity.ok(resp);
        } else {
            resp.put("success", false);
            resp.put("message", "Không thể cập nhật hồ sơ lúc này!");
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @PostMapping("/teacher/profile/clear-field")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearTeacherField(
            @RequestParam("field") String field,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();
        if (currentUser == null || (!currentUser.isMentor() && !currentUser.isAdmin())) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }
        if ("phone".equalsIgnoreCase(field)) {
            userService.updateUserProfile(currentUser.getId(), null, "", null, null, null, null);
            currentUser.setPhone(null);
            session.setAttribute("LOGGED_IN_USER", currentUser);
            resp.put("success", true);
            resp.put("message", "Đã xóa số điện thoại khỏi hồ sơ!");
            return ResponseEntity.ok(resp);
        } else if ("avatar".equalsIgnoreCase(field)) {
            String defaultAvatar = "https://api.dicebear.com/7.x/bottts/svg?seed=" + currentUser.getEmail();
            userService.updateUserProfile(currentUser.getId(), null, null, defaultAvatar, null, null, null);
            currentUser.setAvatar(defaultAvatar);
            session.setAttribute("LOGGED_IN_USER", currentUser);
            resp.put("success", true);
            resp.put("avatar", defaultAvatar);
            resp.put("message", "Đã khôi phục ảnh đại diện mặc định!");
            return ResponseEntity.ok(resp);
        }
        resp.put("success", false);
        resp.put("message", "Trường thông tin không hợp lệ.");
        return ResponseEntity.badRequest().body(resp);
    }

    @PostMapping("/teacher/profile/change-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changeTeacherPassword(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();
        if (currentUser == null || (!currentUser.isMentor() && !currentUser.isAdmin())) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        String oldPass = (String) payload.get("oldPassword");
        String newPass = (String) payload.get("newPassword");
        if (newPass == null || newPass.trim().length() < 6) {
            resp.put("success", false);
            resp.put("message", "Mật khẩu mới phải từ 6 ký tự trở lên!");
            return ResponseEntity.badRequest().body(resp);
        }

        boolean ok = userService.changePassword(currentUser.getId(), oldPass, newPass);
        if (ok) {
            resp.put("success", true);
            resp.put("message", "Đã đổi mật khẩu Giáo viên thành công!");
            return ResponseEntity.ok(resp);
        } else {
            resp.put("success", false);
            resp.put("message", "Mật khẩu hiện tại không đúng. Vui lòng thử lại!");
            return ResponseEntity.badRequest().body(resp);
        }
    }
}
