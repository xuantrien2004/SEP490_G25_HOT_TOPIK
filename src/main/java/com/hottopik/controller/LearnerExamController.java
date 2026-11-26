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
import java.util.LinkedHashMap;
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

    /* =========================================================================================
     * LUỒNG CHỨC NĂNG: THI THỬ FULL MOCK TÁCH CA ĐỘC LẬP (PLAY FULL MOCK EXAM - FUNC_EXAM_02)
     * -----------------------------------------------------------------------------------------
     * 1. THAO TÁC NGƯỜI DÙNG & UI/HTML:
     *    - Học viên chọn đề thi trên Thư viện đề (exam/index.html) -> Bấm "Bắt đầu thi thử".
     * 2. HTTP REQUEST & ENDPOINT:
     *    - URL: GET /lam-bai/{id}
     * 3. CONTROLLER & SERVICE XỬ LÝ:
     *    - Controller: com.hottopik.controller.LearnerExamController.play()
     *    - Triệu gọi Service: com.hottopik.service.exam.LearnerExamService.play(userId, id):
     *      + Nạp Attempt hiện tại, xác định kỹ năng ca thi hiện tại (currentSkill: LISTENING / READING / WRITING).
     *      + Lấy số giây còn lại riêng của ca thi đó (listeningSeconds, readingSeconds, writingSeconds).
     *      + Gom nhóm câu hỏi dùng chung đoạn văn / audio theo khối (Grouped Questions) và câu đơn (Single Questions).
     * 4. TRUY VẤN CSDL:
     *    - Bảng dbo.exam_attempts (SELECT theo id và user_id).
     *    - Bảng dbo.exam_items, dbo.questions, dbo.question_groups (SELECT nội dung câu hỏi ca hiện tại).
     * 5. HIỂN THỊ KẾT QUẢ CHO NGƯỜI DÙNG:
     *    - Trả về View: "exam/play" (src/main/resources/templates/exam/play.html)
     *    - Giao diện hiển thị: Đồng hồ đếm ngược của kỹ năng hiện tại, thẻ câu hỏi kèm audio/đoạn văn,
     *      sidebar Phiếu câu hỏi highlight câu đang làm qua IntersectionObserver.
     * ========================================================================================= */
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
    public LearnerExamService.SaveResult save(@PathVariable Long id,
                                              jakarta.servlet.http.HttpServletRequest request,
                                              @RequestParam(defaultValue = "false") boolean pause, HttpSession session) {
        return service.save(userId(session), id, extractAnswers(request), pause, false);
    }

    @PostMapping("/exam/attempt/{id}/check/{key}")
    @ResponseBody
    public com.hottopik.dto.QuestionFeedback check(@PathVariable Long id, @PathVariable String key,
                                                  @RequestParam int selected, HttpSession session) {
        return service.check(userId(session), id, key, selected);
    }

    /* =========================================================================================
     * LUỒNG CHỨC NĂNG: NỘP CA THI / CHUYỂN CA / LƯU VÀ THOÁT ĐỀ THI (FINISH / PAUSE CA - FUNC_EXAM_04)
     * -----------------------------------------------------------------------------------------
     * 1. THAO TÁC NGƯỜI DÙNG & UI/HTML:
     *    - Học viên làm bài tại exam/play.html, bấm nút:
     *      + "Lưu và thoát": Form POST gửi đến /exam/attempt/{id}/pause.
     *      + "Nộp phần thi hiện tại": Form POST gửi đến /exam/attempt/{id}/submit.
     * 2. HTTP REQUEST & ENDPOINT:
     *    - URL: POST /exam/attempt/{id}/pause hoặc POST /exam/attempt/{id}/submit
     * 3. CONTROLLER & SERVICE XỬ LÝ:
     *    - Controller: com.hottopik.controller.LearnerExamController.finish()
     *    - Triệu gọi Service: com.hottopik.service.exam.LearnerExamService.save(userId, id, answers, pause, submit):
     *      + Lưu các đáp án ca hiện tại vào answers_json.
     *      + Nếu là submit: Xác định ca tiếp theo qua nextSkill().
     *        * Nếu còn ca thi: Cập nhật currentSkill = nextSkill, remainingSeconds = thời gian ca mới, completed = false.
     *        * Nếu là ca cuối cùng: completed = true, gọi finish(), tính điểm tổng toàn bài thi.
     *      + Nếu là pause: Lưu thời gian thực tế còn lại (remainingSeconds), giữ nguyên ca đang làm.
     * 4. TRUY VẤN CSDL:
     *    - Bảng dbo.exam_attempts (UPDATE current_skill, remaining_seconds, answers_json, completed_at).
     * 5. HIỂN THỊ KẾT QUẢ CHO NGƯỜI DÙNG:
     *    - Nếu chuyển ca: Chuyển hướng đến /lam-bai/{id} với đồng hồ đếm ngược và câu hỏi của ca tiếp theo.
     *    - Nếu nộp toàn bộ: redirect:/ket-qua/{id} (Xem bảng điểm kết quả tổng hợp).
     * ========================================================================================= */
    @PostMapping({"/exam/attempt/{id}/pause", "/exam/attempt/{id}/submit"})
    public String finish(@PathVariable Long id,
                         HttpSession session,
                         jakarta.servlet.http.HttpServletRequest request, RedirectAttributes flash) {
        if (AuthHelper.getLoggedInUser(session) == null) return "redirect:/login";
        boolean submit = request.getRequestURI().endsWith("/submit");
        LearnerExamService.SaveResult result = service.save(userId(session), id, extractAnswers(request), !submit, submit);
        if (result.completed()) return "redirect:/ket-qua/" + id;
        if (result.noProgress()) {
            flash.addFlashAttribute("examMessage", "Bạn chưa làm câu nào nên tiến độ sẽ không được lưu.");
        } else if (submit && !result.completed()) {
            flash.addFlashAttribute("examMessage", "Đã hoàn thành phần thi! Đang chuyển sang kỹ năng tiếp theo.");
        }
        return "redirect:" + result.backUrl();
    }

    @PostMapping({"/exam/attempt/{id}/submit-teacher", "/exam/attempt/{id}/send-teacher"})
    public String sendTeacher(@PathVariable Long id,
                              HttpSession session,
                              jakarta.servlet.http.HttpServletRequest request, RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        boolean submit = request.getRequestURI().endsWith("/submit-teacher");
        try {
            service.sendWritingToTeacher(user, id, extractAnswers(request), submit);
            flash.addFlashAttribute("successMessage", "Đã gửi bài viết cho Giáo viên. Bạn sẽ nhận thông báo khi có kết quả.");
            return "redirect:/ket-qua/" + id;
        } catch (ExamDomainException | IllegalArgumentException ex) {
            flash.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:" + (submit ? "/lam-bai/" : "/ket-qua/") + id;
        }
    }

    private static Map<String, String> extractAnswers(jakarta.servlet.http.HttpServletRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        request.getParameterMap().forEach((k, vals) -> {
            if (vals != null && vals.length > 0) {
                String chosen = "";
                for (String v : vals) {
                    if (v != null && !v.isBlank()) {
                        chosen = v;
                        break;
                    }
                }
                map.put(k, chosen);
            }
        });
        return map;
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
    /* =========================================================================================
     * LUỒNG CHỨC NĂNG: XEM KẾT QUẢ THI THỬ & BẢNG ĐIỂM CHUẨN TOPIK (EXAM RESULT - FUNC_EXAM_05)
     * -----------------------------------------------------------------------------------------
     * 1. THAO TÁC NGƯỜI DÙNG & UI/HTML:
     *    - Tự động chuyển hướng đến sau khi nộp ca thi cuối cùng hoặc học viên click từ Lịch sử thi (progress/history.html).
     * 2. HTTP REQUEST & ENDPOINT:
     *    - URL: GET /ket-qua/{id}
     * 3. CONTROLLER & SERVICE XỬ LÝ:
     *    - Controller: com.hottopik.controller.LearnerExamController.result()
     *    - Triệu gọi Service:
     *      + com.hottopik.service.exam.LearnerExamService.result(userId, id): Nạp dữ liệu bài thi.
     *      + com.hottopik.service.exam.ExamScoreCalculator.calculate(): Tính điểm chuẩn TOPIK I (200đ) / TOPIK II (300đ),
     *        phân rã điểm thành phần từng kỹ năng (Nghe, Đọc, Viết) và xác định Cấp độ TOPIK đạt được.
     * 4. TRUY VẤN CSDL:
     *    - Bảng dbo.exam_attempts (SELECT lượt thi đã hoàn thành).
     *    - Bảng dbo.exams, dbo.exam_items, dbo.questions (SELECT nội dung câu hỏi và đáp án chuẩn).
     * 5. HIỂN THỊ KẾT QUẢ CHO NGƯỜI DÙNG:
     *    - Trả về View: "exam/result" (src/main/resources/templates/exam/result.html)
     *    - Giao diện hiển thị: Bảng điểm chuẩn, thanh tab lọc kỹ năng (.rv-skill-tabs: Nghe, Đọc, Viết, Tất cả),
     *      xem lại giải thích chi tiết từng câu và nút "Gửi bài viết cho Giáo viên" (nếu là bài thi Viết).
     * ========================================================================================= */
    @GetMapping({"/ket-qua/{id}", "/exam/attempt/{id}/result"})
    public String result(@PathVariable Long id, HttpSession session, Model model) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        LearnerExamService.Paper paper = service.result(user.getId(), id);
        common(model, user, paper);
        var reviews = service.writingReviews(user.getId(), id);
        var sentByPrompt = service.sentToTeacherByPrompt(user.getId());
        // Cùng một quy tắc với lúc gửi (WritingSubmissionService.examSendBlockReason) để nút hiện đúng khi được gửi.
        java.util.Map<Long, Boolean> canSendByPrompt = new java.util.HashMap<>();
        for (var q : paper.writing())
            canSendByPrompt.put(q.id(), com.hottopik.service.WritingSubmissionService.examSendBlockReason(
                    reviews.get(q.id()), sentByPrompt.get(q.id()), q.no(), q.selected()) == null);
        model.addAttribute("writingReviews", reviews);
        model.addAttribute("sentByPrompt", sentByPrompt);
        model.addAttribute("canSendByPrompt", canSendByPrompt);
        model.addAttribute("canSendWriting", canSendByPrompt.containsValue(true));

        java.util.Map<String, String> creditedAnswers = com.hottopik.service.exam.CheckedAnswers.read(paper.attempt().getCreditedAnswersJson());
        com.hottopik.dto.ExamScoreDTO scoreSummary = com.hottopik.service.exam.ExamScoreCalculator.calculate(paper, reviews, creditedAnswers, sentByPrompt);
        var allReviews = service.allWritingReviewsByAttempt(user.getId());
        java.util.List<com.hottopik.dto.ExamScoreDTO.HistoryItem> historyItems = com.hottopik.service.exam.ExamScoreCalculator.calculateHistory(paper, scoreSummary, allReviews);
        model.addAttribute("scoreSummary", scoreSummary);
        model.addAttribute("historyItems", historyItems);
        return "exam/result";
    }

    @GetMapping({"/exam/media/{sourceId}/{filename:.+}", "/exam/media/{sourceId}/**"})
    public void media(@PathVariable Long sourceId,
                      @PathVariable(required = false) String filename,
                      @RequestHeader(value = "Range", required = false) String range,
                      jakarta.servlet.http.HttpServletRequest request,
                      HttpSession session, HttpServletResponse response) throws IOException {
        if (!service.canReadMedia(userId(session), sourceId)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (filename == null || filename.isBlank()) {
            String uri = request.getRequestURI();
            String prefix = "/exam/media/" + sourceId + "/";
            int idx = uri.indexOf(prefix);
            if (idx >= 0) {
                filename = uri.substring(idx + prefix.length());
            }
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
        Exam exam = paper.attempt() != null ? paper.attempt().getExam() : null;
        java.util.List<Skill> availableSkills = LearnerExamService.availableSkills(exam);
        model.addAttribute("availableSkills", availableSkills);
        model.addAttribute("isMultiSkill", availableSkills.size() > 1 && paper.kind() == ExamKind.FULL);
    }

    private static String back(LearnerExamService.Paper paper) {
        return "/exam?track=" + (paper.track() == Track.TOPIK_I ? "I" : "II")
                + "&kind=" + paper.kind().name().toLowerCase(java.util.Locale.ROOT);
    }
}
