package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.QuestionReport;
import com.hottopik.entity.UserNotification;
import com.hottopik.service.exam.ExamImportService;
import com.hottopik.entity.exam.WritingPrompt;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.hottopik.entity.exam.MediaAsset;
import com.hottopik.entity.exam.MediaKind;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import com.hottopik.service.QuestionReportService;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class QuestionReportController {

    private final QuestionReportService reportService;
    private final ExamImportService imports;

    @PostMapping("/api/question-reports")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitReport(
            @RequestParam(required = false) Long questionId,
            @RequestParam(required = false) String questionCode,
            @RequestParam(required = false) String questionSummary,
            @RequestParam(required = false) Long examId,
            @RequestParam(required = false) String examTitle,
            @RequestParam(required = false) Integer questionNo,
            @RequestParam(defaultValue = "MC") String questionKind,
            @RequestParam String issueType,
            @RequestParam String details,
            HttpSession session) {

        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return ResponseEntity.status(401).body(Map.of("success", false, "message", "Vui lòng đăng nhập để báo lỗi."));
        Long userId = user.getId();
        String name = user.getFullName();
        String email = user.getEmail();

        if (details == null || details.trim().length() < 3 || details.length() > 2000) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Vui lòng mô tả chi tiết lỗi từ 3 đến 2000 ký tự để ban Quản lý nội dung kiểm tra."
            ));
        }

        QuestionReport report = reportService.createReport(
                userId, name, email, questionId, questionCode, questionSummary,
                examId, examTitle, questionNo, issueType, details, questionKind
        );

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("reportId", report.getId());
        resp.put("message", "Đã gửi báo lỗi thành công! Cảm ơn bạn đã đóng góp giúp hoàn thiện đề thi HOT TOPIK.");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/bao-loi/{id}")
    public String viewReportDetail(@PathVariable Long id, Model model, HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        QuestionReport report = ownedReport(id, user);
        var question = reportService.reportedQuestion(report);
        reportService.getUserNotifications(user.getId()).stream()
                .filter(n -> ("/bao-loi/" + id).equals(n.getLink()) && !n.isRead())
                .forEach(n -> reportService.markNotificationAsRead(n.getId(), user.getId()));

        model.addAttribute("report", report);
        model.addAttribute("question", question);
        model.addAttribute("showAnswers", !"OPEN".equals(report.getStatus()) || user.isContentManager() || user.isAdmin());
        model.addAttribute("currentUser", user);
        model.addAttribute("pageTitle", "Chi tiết phản hồi Báo lỗi #" + report.getId() + " · HOT TOPIK");
        return "report/detail";
    }

    @GetMapping({"/thong-bao", "/notifications"})
    public String viewNotifications(Model model, HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        Long userId = user.getId();

        List<UserNotification> notifications = reportService.getUserNotifications(userId);
        model.addAttribute("notifications", notifications);
        model.addAttribute("currentUser", user);
        model.addAttribute("pageTitle", "Thông báo của tôi · HOT TOPIK");
        return "report/notifications";
    }

    @GetMapping("/api/notifications/summary")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getNotificationsSummary(HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return ResponseEntity.status(401).build();
        Long userId = user.getId();

        long unread = reportService.getUnreadNotificationCount(userId);
        List<UserNotification> top = reportService.getTopUserNotifications(userId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("unreadCount", unread);
        resp.put("notifications", top);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable Long id, HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return ResponseEntity.status(401).build();
        Long userId = user.getId();
        reportService.markNotificationAsRead(id, userId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/content/reports/{id}/question")
    public String contentQuestion(@PathVariable Long id, Model model, HttpSession session) {
        QuestionReport report = ownedReport(id, AuthHelper.getLoggedInUser(session));
        model.addAttribute("question", reportService.reportedQuestion(report));
        model.addAttribute("report", report);
        model.addAttribute("showAnswers", true);
        return "report/question :: content";
    }

    @GetMapping("/content/reports/{id}/edit")
    public String editQuestion(@PathVariable Long id, Model model, HttpSession session, RedirectAttributes flash) {
        UserDTO user = contentEditor(session);
        QuestionReport report = ownedReport(id, user);
        if (!"OPEN".equals(report.getStatus()) || reportService.reportedQuestion(report) == null) {
            flash.addFlashAttribute("flashError", "Báo lỗi đã xử lý hoặc không còn liên kết câu hỏi gốc để sửa.");
            return "redirect:/content?tab=reports";
        }
        if (!reportService.isWriting(report)) {
            return "redirect:/content/questions?openQuestionId=" + report.getQuestionId() + "&reportId=" + id;
        }
        model.addAttribute("report", report);
        if (!model.containsAttribute("writing")) model.addAttribute("writing", reportService.writingToEdit(report));
        model.addAttribute("pageTitle", "Sửa câu hỏi báo lỗi · HOT TOPIK");
        return "report/edit-writing";
    }

    @PostMapping("/content/reports/{id}/writing")
    public String saveWriting(@PathVariable Long id, @ModelAttribute("writing") WritingPrompt form,
                              @RequestParam String resolutionNote, HttpSession session, RedirectAttributes flash) {
        UserDTO user = contentEditor(session);
        ownedReport(id, user);
        try {
            reportService.updateWritingAndResolve(id, form, resolutionNote, user.getFullName());
            flash.addFlashAttribute("flashOk", "Đã lưu câu hỏi, xử lý báo lỗi và gửi thông báo cho người học.");
            return "redirect:/content?tab=reports";
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            flash.addFlashAttribute("writing", form);
            return "redirect:/content/reports/" + id + "/edit";
        }
    }

    private UserDTO contentEditor(HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!user.isContentManager()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }

    @GetMapping("/bao-loi/{id}/media/{filename:.+}")
    public ResponseEntity<Resource> media(@PathVariable Long id, @PathVariable String filename,
                                          HttpSession session) throws IOException {
        QuestionReport report = ownedReport(id, AuthHelper.getLoggedInUser(session));
        var q = reportService.reportedQuestion(report);
        if (q == null || q.sourceId() == null) return ResponseEntity.notFound().build();
        String suffix = "/" + org.springframework.web.util.UriUtils.encodePathSegment(filename, java.nio.charset.StandardCharsets.UTF_8);
        boolean allowed = java.util.stream.Stream.concat(java.util.stream.Stream.of(q.image(), q.audio()),
                        q.options().stream().map(com.hottopik.service.exam.LearnerExamService.Choice::image))
                .anyMatch(url -> url != null && url.endsWith(suffix));
        if (!allowed) return ResponseEntity.notFound().build();
        Optional<MediaAsset> asset = imports.findMedia(q.sourceId(), filename);
        if (asset.isEmpty()) return ResponseEntity.notFound().build();
        Path path = imports.mediaPath(asset.get());
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        String type = Optional.ofNullable(Files.probeContentType(path))
                .orElse(asset.get().getKind() == MediaKind.AUDIO ? "audio/mpeg" : "application/octet-stream");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).body(new FileSystemResource(path));
    }

    private QuestionReport ownedReport(Long id, UserDTO user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return reportService.getReportById(id).filter(r -> user.getId().equals(r.getUserId())
                || user.isContentManager() || user.isAdmin())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
