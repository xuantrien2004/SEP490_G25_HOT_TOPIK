package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.*;
import com.hottopik.service.exam.ExamDomainException;
import com.hottopik.service.exam.ExamImportService;
import com.hottopik.service.exam.LearnerExamService;
import com.hottopik.service.exam.MediaResponses;
import com.hottopik.service.exam.QuestionTypeCatalog;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

@Controller
public class LearnerExamController {
    private final LearnerExamService service;
    private final ExamImportService imports;

    public LearnerExamController(LearnerExamService service, ExamImportService imports) {
        this.service = service;
        this.imports = imports;
    }

    /*
     * Đưa examData (cấp độ -> loại đề -> danh sách Card) vào exam/index.html; JavaScript render thẻ và trạng thái trống.
     */
    @GetMapping({"/de-thi", "/exam"})
    public String library(Model model, HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        model.addAttribute("examData", service.library(user == null ? null : user.getId()));
        model.addAttribute("currentUser", user);
        model.addAttribute("pageTitle", "Đề thi TOPIK · HOT TOPIK");
        model.addAttribute("activePage", "exam");
        return "exam/index";
    }

    /*
     * Bắt đầu/làm lại đều là POST có đăng nhập; service quyết định dùng lượt dở hay tạo lượt mới rồi chuyển /lam-bai/{attemptId}.
     */
    @PostMapping({"/exam/{id}/start", "/exam/{id}/retry"})
    public String start(@PathVariable Long id, HttpSession session,
                        jakarta.servlet.http.HttpServletRequest request, RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        try {
            ExamAttempt attempt = service.start(user.getId(), id, request.getRequestURI().endsWith("/retry"));
            return "redirect:/lam-bai/" + attempt.getId();
        } catch (ExamDomainException e) {
            flash.addFlashAttribute("examError", e.getMessage());
            return "redirect:/exam";
        }
    }

    /*
     * Nạp bài và thời gian còn lại từ server, chuyển MC sang player chung; phần Viết trong paper vẫn dùng form riêng.
     */
    @GetMapping({"/lam-bai/{id}", "/exam/attempt/{id}"})
    public String play(@PathVariable Long id, HttpSession session, Model model) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        LearnerExamService.Paper paper = service.play(user.getId(), id);
        if (paper.attempt().getCompletedAt() != null) return "redirect:/ket-qua/" + id;
        common(model, user, paper);
        model.addAttribute("checkedAnswersJson",paper.attempt().getCheckedAnswersJson());
        model.addAttribute("creditedAnswers",com.hottopik.service.exam.CheckedAnswers.read(paper.attempt().getCreditedAnswersJson()));
        model.addAttribute("playerQuestions", paper.questions().stream()
                .map(q -> com.hottopik.dto.QuestionPlayerItem.exam(q, id)).toList());
        return "exam/play";
    }

    /*
     * Autosave nhận map key q<examItemId>/w<promptId>; trả thời gian và trạng thái để trình duyệt đồng bộ.
     */
    @PostMapping("/exam/attempt/{id}/save")
    @ResponseBody
    public LearnerExamService.SaveResult save(@PathVariable Long id, @RequestParam Map<String, String> answers,
                                              @RequestParam(defaultValue = "false") boolean pause, HttpSession session) {
        return service.save(userId(session), id, answers, pause, false);
    }

    @PostMapping("/exam/attempt/{id}/check/{key}")
    @ResponseBody
    public com.hottopik.dto.QuestionFeedback check(@PathVariable Long id, @PathVariable String key,
                                                  @RequestParam int selected, HttpSession session) {
        return service.check(userId(session), id, key, selected);
    }

    /*
     * Phân biệt Lưu và thoát với Nộp bài qua URL; cùng service.save nhưng pause/submit khác nhau, nên dừng không đồng nghĩa đã nộp.
     */
    @PostMapping({"/exam/attempt/{id}/pause", "/exam/attempt/{id}/submit"})
    public String finish(@PathVariable Long id, @RequestParam Map<String, String> answers, HttpSession session,
                         jakarta.servlet.http.HttpServletRequest request) {
        if (AuthHelper.getLoggedInUser(session) == null) return "redirect:/login";
        boolean submit = request.getRequestURI().endsWith("/submit");
        LearnerExamService.SaveResult result = service.save(userId(session), id, answers, !submit, submit);
        if (result.completed()) return "redirect:/ket-qua/" + id;
        return "redirect:" + result.backUrl();
    }

    @PostMapping({"/exam/attempt/{id}/submit-teacher", "/exam/attempt/{id}/send-teacher"})
    public String sendTeacher(@PathVariable Long id, @RequestParam Map<String, String> answers, HttpSession session,
                              jakarta.servlet.http.HttpServletRequest request, RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        boolean submit = request.getRequestURI().endsWith("/submit-teacher");
        try {
            service.sendWritingToTeacher(user, id, answers, submit);
            flash.addFlashAttribute("successMessage", "Đã gửi bài viết cho Giáo viên. Bạn sẽ nhận thông báo khi có kết quả.");
            return "redirect:/ket-qua/" + id;
        } catch (ExamDomainException | IllegalArgumentException ex) {
            flash.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:" + (submit ? "/lam-bai/" : "/ket-qua/") + id;
        }
    }

    /*
     * Chỉ nạp kết quả lượt đã hoàn thành của chính người dùng; common() giữ URL quay lại đúng cấp độ/loại đề.
     */
    
    @PostMapping("/exam/attempt/{id}/send-teacher-q")
    public String sendTeacherQ(@PathVariable Long id,
                               @RequestParam Long promptId,
                               HttpSession session,
                               RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        try {
            service.sendSingleWritingToTeacher(user, id, promptId);
            flash.addFlashAttribute("successMessage", "Da gui cau viet cho Giao vien.");
        } catch (ExamDomainException | IllegalArgumentException ex) {
            flash.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/ket-qua/" + id;
    }
@GetMapping({"/ket-qua/{id}", "/exam/attempt/{id}/result"})
    public String result(@PathVariable Long id, HttpSession session, Model model) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        LearnerExamService.Paper paper = service.result(user.getId(), id);
        common(model, user, paper);
        var reviews = service.writingReviews(user.getId(), id);
        var sentByPrompt = service.sentToTeacherByPrompt(user.getId());
        model.addAttribute("writingReviews", reviews);
        model.addAttribute("sentByPrompt", sentByPrompt);
        model.addAttribute("canSendWriting", paper.writing().stream()
                .anyMatch(q -> com.hottopik.service.WritingSubmissionService.hasAnswer(q.no(), q.selected())
                        && !sentByPrompt.containsKey(q.id())));
        return "exam/result";
    }

    @GetMapping("/exam/media/{sourceId}/{filename:.+}")
    public void media(@PathVariable Long sourceId, @PathVariable String filename,
                      @RequestHeader(value = "Range", required = false) String range,
                      HttpSession session, HttpServletResponse response) throws IOException {
        if (!service.canReadMedia(userId(session), sourceId)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Optional<MediaAsset> asset = imports.findMedia(sourceId, filename);
        if (asset.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path path = imports.mediaPath(asset.get());
        if (!Files.exists(path)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        MediaResponses.play(path, filename, asset.get().getKind(), range, response);
    }

    @ExceptionHandler(ExamDomainException.class)
    public String domainError(ExamDomainException e, RedirectAttributes flash) {
        flash.addFlashAttribute("examError", e.getMessage());
        return "redirect:/exam";
    }

    /** Nút mẫu cũ trỏ tới mã kiểu mock-91-resume. Spring không đổi được sang số nên trước đây ra trang lỗi. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public String unknownExam(RedirectAttributes flash) {
        flash.addFlashAttribute("examError", "Đề này không mở được vì mã bài không còn trong hệ thống. Hãy chọn một đề trong thư viện.");
        return "redirect:/exam";
    }

    private static Long userId(HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        return user == null ? null : user.getId();
    }

    private static void common(Model model, UserDTO user, LearnerExamService.Paper paper) {
        model.addAttribute("paper", paper);
        model.addAttribute("checkedAnswersJson", paper.attempt().getCheckedAnswersJson());
        model.addAttribute("creditedAnswers",com.hottopik.service.exam.CheckedAnswers.read(paper.attempt().getCreditedAnswersJson()));
        model.addAttribute("currentUser", user);
        model.addAttribute("pageTitle", paper.title() + " · HOT TOPIK");
        model.addAttribute("activePage", "exam");
        model.addAttribute("sections", QuestionTypeCatalog.sections(paper.track()));
        model.addAttribute("backUrl", back(paper));
    }

    private static String back(LearnerExamService.Paper paper) {
        return "/exam?track=" + (paper.track() == Track.TOPIK_I ? "I" : "II")
                + "&kind=" + paper.kind().name().toLowerCase(java.util.Locale.ROOT);
    }
}
