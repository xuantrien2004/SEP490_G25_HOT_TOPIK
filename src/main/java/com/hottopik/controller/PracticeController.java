package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.*;
import com.hottopik.service.PracticeService;
import com.hottopik.service.PracticeService.QuestionView;
import com.hottopik.service.PracticeService.TypeSummary;
import com.hottopik.service.exam.QuestionTypeCatalog;
import com.hottopik.service.exam.QuestionTypeDef;
import com.hottopik.service.exam.LearnerRoadmapService;
import com.hottopik.service.exam.ExamDomainException;
import com.hottopik.service.exam.ExamImportService;
import com.hottopik.service.exam.MediaResponses;
import com.hottopik.entity.exam.PracticeAttempt;
import com.hottopik.entity.exam.MediaAsset;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class PracticeController {
    private final PracticeService practice;
    private final LearnerRoadmapService roadmaps;
    private final com.hottopik.service.WritingSubmissionService writingSubmissions;
    private final ExamImportService imports;

    private record RoadmapPractice(String owner, int day, int slot) {}

    public PracticeController(PracticeService practice, LearnerRoadmapService roadmaps, com.hottopik.service.WritingSubmissionService writingSubmissions, ExamImportService imports) {
        this.practice = practice;
        this.roadmaps = roadmaps;
        this.writingSubmissions = writingSubmissions;
        this.imports = imports;
    }

    /*
     * Điểm vào Luyện tập: lấy người dùng từ session, lấy thống kê rồi chia theo kỹ năng/cấp độ.
     * Các thuộc tính listeningI/II, readingI/II, writingII được practice/index.html render thành tab.
     * initialSkill/initialTrack là ngữ cảnh URL khi quay lại từ một dạng, không phải bộ lọc câu trong DB.
     */
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

    /* =========================================================================================
     * LUỒNG CHỨC NĂNG: CHUẨN BỊ BÀI LUYỆN TẬP (PREPARE PRACTICE - FUNC_PRAC_02)
     * -----------------------------------------------------------------------------------------
     * 1. THAO TÁC NGƯỜI DÙNG & UI/HTML:
     *    - Bắt đầu từ: Người học bấm vào 1 thẻ dạng bài trên trang Danh mục Luyện tập (practice/index.html)
     *      hoặc bấm vào ô bài học trên Lộ trình / Hôm nay (learner/today.html).
     *    - Thao tác: Click liên kết thẻ bài <a th:href="@{/practice/prepare(typeCode=...)}">.
     * 2. HTTP REQUEST & ENDPOINT:
     *    - URL: GET /practice/prepare?typeCode={typeCode}&day={day}&slot={slot}
     * 3. CONTROLLER & SERVICE XỬ LÝ:
     *    - Controller: com.hottopik.controller.PracticeController.preparePractice()
     *    - Triệu gọi Service:
     *      + com.hottopik.service.exam.QuestionTypeCatalog.byCode(typeCode): Lấy cấu hình chuẩn NIIED.
     *      + com.hottopik.service.PracticeService.findPendingAttempt(userId, typeCode): Kiểm tra có bài làm dở không.
     *      + com.hottopik.service.PracticeService.allHistoryOfType(userId, typeCode): Lấy lịch sử các lần luyện trước.
     * 4. TRUY VẤN CSDL:
     *    - Bảng dbo.practice_attempts (SELECT các attempt chưa hoàn thành completed_at IS NULL và lịch sử đã nộp).
     * 5. HIỂN THỊ KẾT QUẢ CHO NGƯỜI DÙNG:
     *    - Trả về View: "practice/prepare" (src/main/resources/templates/practice/prepare.html)
     *    - Giao diện hiển thị: Thông tin dạng bài, số câu dự kiến, điểm cao nhất (Best Score),
     *      nếu có bài làm dở thì hiển thị banner "Làm tiếp bài dở" kèm số câu đã làm, nếu không thì hiện nút "Bắt đầu".
     * ========================================================================================= */
    @GetMapping({"/practice/prepare", "/luyen-tap/chuan-bi"})
    public String preparePractice(@RequestParam("typeCode") String typeCode,
                                  @RequestParam(required = false) Integer day,
                                  @RequestParam(required = false) Integer slot,
                                  Model model, HttpSession session) {
        QuestionTypeDef type = QuestionTypeCatalog.byCode(typeCode).orElseThrow(() -> new IllegalArgumentException("Dạng câu không hợp lệ: " + typeCode));
        UserDTO user = AuthHelper.getLoggedInUser(session);
        Long userId = user != null ? user.getId() : 1L;
        String email = user != null ? user.getEmail() : null;

        TypeSummary summary = practice.allSummaries(userId, email).stream()
                .filter(s -> s.type().code().equals(type.code())).findFirst()
                .orElse(new TypeSummary(type, 0, 0, null, null));

        Optional<PracticeAttempt> pendingAttempt = practice.findPendingAttempt(userId, typeCode);
        List<PracticeAttempt> history = practice.allHistoryOfType(userId, typeCode);

        model.addAttribute("pageTitle", "Chuẩn bị bài luyện · HOT TOPIK");
        model.addAttribute("activePage", "practice");
        model.addAttribute("type", type);
        model.addAttribute("summary", summary);
        model.addAttribute("pendingAttempt", pendingAttempt.orElse(null));
        model.addAttribute("history", history);
        if (type.isWriting() && user != null) {
            int questionNo = type.numbers().get(0);
            List<com.hottopik.dto.WritingSubmissionDTO> submitted = writingSubmissions.getSubmissionsByLearner(user.getId())
                    .stream()
                    .filter(s -> s.getQuestionNo() == questionNo
                            && !"draft".equalsIgnoreCase(s.getStatus())
                            && s.getExamAttemptId() == null)
                    .toList();
            // Thêm draft nếu có
            com.hottopik.dto.WritingSubmissionDTO draft = writingSubmissions.getDraft(user.getId(), questionNo);
            List<com.hottopik.dto.WritingSubmissionDTO> all = new java.util.ArrayList<>(submitted);
            if (draft != null) all.add(0, draft); // draft lên đầu
            model.addAttribute("writingHistory", all);
        } else {
            model.addAttribute("writingHistory", List.of());
        }
        model.addAttribute("currentUser", user);
        model.addAttribute("day", day);
        model.addAttribute("slot", slot);
        return "practice/prepare";
    }

    /* =========================================================================================
     * LUỒNG CHỨC NĂNG: BẮT ĐẦU BÀI LUYỆN TẬP & BỐC CÂU HỎI ĐỘNG (START PRACTICE - FUNC_PRAC_03)
     * -----------------------------------------------------------------------------------------
     * 1. THAO TÁC NGƯỜI DÙNG & UI/HTML:
     *    - Bắt đầu từ: Màn hình Chuẩn bị (src/main/resources/templates/practice/prepare.html).
     *    - Thao tác: Học viên bấm nút submit form: <button type="submit" class="btn-ssak-primary">Bắt đầu làm bài</button>.
     * 2. HTTP REQUEST & ENDPOINT:
     *    - URL: POST /practice/start (Form Data: typeCode, day, slot, questionCount)
     * 3. CONTROLLER & SERVICE XỬ LÝ:
     *    - Controller: com.hottopik.controller.PracticeController.startPractice()
     *    - Triệu gọi Service:
     *      + com.hottopik.service.exam.LearnerRoadmapService.matchesPracticeSlot(): Kiểm tra tính hợp lệ nếu mở từ lộ trình.
     *      + com.hottopik.service.PracticeService.start(userId, email, typeCode, questionCount):
     *        * Xác định số câu động theo dạng (requiredCount).
     *        * Lọc kho câu hỏi ứng viên (candidatePool), loại trừ các câu trong bài làm dở (pendingAttempts).
     *        * Tạo bản ghi PracticeAttempt mới và lưu danh sách ID câu hỏi (questionIds).
     * 4. TRUY VẤN CSDL:
     *    - Bảng dbo.questions & dbo.question_groups (SELECT câu hỏi thuộc dạng typeCode).
     *    - Bảng dbo.practice_attempts (INSERT bản ghi lượt làm mới: status dở dang, answers_json trống).
     * 5. HIỂN THỊ KẾT QUẢ CHO NGƯỜI DÙNG:
     *    - Controller trả về: redirect:/practice/play/{attempt.getId()}
     *    - Trình duyệt chuyển hướng đến màn hình phòng luyện tập (src/main/resources/templates/practice/play.html)
     *      nạp bộ câu hỏi vào player UI để người học bắt đầu chọn đáp án.
     * ========================================================================================= */
    @PostMapping("/practice/start")
    public String startPractice(@RequestParam("typeCode") String typeCode,
                                @RequestParam(required = false) Integer day,
                                @RequestParam(required = false) Integer slot,
                                @RequestParam(required = false, defaultValue = "0") int questionCount,
                                HttpSession session, RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        Long userId = user != null ? user.getId() : 1L;
        String email = user != null ? user.getEmail() : "learner@hottopik.com";

        QuestionTypeDef type = QuestionTypeCatalog.byCode(typeCode).orElseThrow(() -> new IllegalArgumentException("Dạng câu không hợp lệ: " + typeCode));
        if ((day == null) != (slot == null) || (day != null && (user == null || user.getEmail() == null
                || !roadmaps.matchesPracticeSlot(user.getEmail(), day, slot, typeCode)))) {
            flash.addFlashAttribute("practiceError", "Bài luyện không khớp với lộ trình hiện tại. Hãy chọn lại bài trong lộ trình.");
            return "redirect:/practice/prepare?typeCode=" + typeCode;
        }
        if (type.isWriting()) {
            PracticeService.TypeSummary summary = practice.allSummaries(userId, email).stream()
                    .filter(s -> s.type().code().equals(typeCode)).findFirst().orElse(null);
            if (summary == null || summary.available() == 0) {
                flash.addFlashAttribute("practiceError", "Dạng câu này hiện chưa có bài luyện. Bạn có thể chọn dạng câu khác.");
                return "redirect:/practice/prepare?typeCode=" + typeCode;
            }
            return "redirect:/writing?question=" + type.numbers().get(0) + "&newAttempt=true";
        }
        try {
            PracticeAttempt attempt = practice.start(userId, email, typeCode, questionCount);
            if (day != null) session.setAttribute("ROADMAP_PRACTICE_" + attempt.getId(), new RoadmapPractice(email, day, slot));
            return "redirect:/practice/play/" + attempt.getId();
        } catch (IllegalStateException e) {
            flash.addFlashAttribute("practiceError", e.getMessage());
            return "redirect:/practice/prepare?typeCode=" + typeCode;
        }
    }

    /**
     * Làm lại bài luyện tập (giữ nguyên bộ câu hỏi của attempt cũ)
     */
    /*
     * Tạo lượt mới từ lượt cũ thuộc người học: giữ nguyên questionIds; không ghi đè kết quả cũ.
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
    /*
     * Luyện tiếp gọi lại startPractice, nên được chọn bộ câu theo cơ chế ngẫu nhiên; khác với retry giữ bộ cũ.
     */
    @GetMapping("/practice/next")
    public String nextPractice(@RequestParam("typeCode") String typeCode, HttpSession session, RedirectAttributes flash) {
        return startPractice(typeCode, null, null, 0, session, flash);
    }

    /*
     * Nạp lượt làm của người học; lượt đã nộp chuyển thẳng đến kết quả.
     * QuestionPlayerItem chỉ chuyển dữ liệu sang form chung; key answer_<questionId> vẫn giữ cho backend nộp bài.
     */
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
            Map<String, Integer> savedAnswers = PracticeService.readAnswers(result.attempt().getAnswersJson());
            int blockSize = result.type().blocks().isEmpty() ? 1 : result.type().blocks().get(0).size();
            List<com.hottopik.dto.QuestionPlayerItem> playerQuestions = new ArrayList<>();
            if (!result.type().isGrouped() || blockSize <= 1) {
                for (QuestionView q : questions) {
                    Integer sel = savedAnswers.get(q.id().toString());
                    playerQuestions.add(com.hottopik.dto.QuestionPlayerItem.practice(
                            q, result.type().skill(), result.type().learnerInstructionKo(), attemptId,
                            sel != null ? String.valueOf(sel) : null, null));
                }
            } else {
                for (int i = 0; i < questions.size(); i += blockSize) {
                    int toIndex = Math.min(i + blockSize, questions.size());
                    List<QuestionView> blockSlice = questions.subList(i, toIndex);
                    String sharedPassage = blockSlice.stream().map(QuestionView::passage).filter(p -> p != null && !p.isBlank()).findFirst().orElse(null);
                    String sharedAudio = blockSlice.stream().map(QuestionView::audio).filter(a -> a != null && !a.isBlank()).findFirst().orElse(null);
                    String sharedImage = blockSlice.stream().map(QuestionView::image).filter(img -> img != null && !img.isBlank()).findFirst().orElse(null);
                    String blockGroupKey = "grp_block_" + (i / blockSize);

                    for (QuestionView q : blockSlice) {
                        Integer sel = savedAnswers.get(q.id().toString());
                        QuestionView enriched = new QuestionView(q.id(), q.no(), q.prompt(), q.options(), q.answer(),
                                q.explanation(), (q.passage() != null && !q.passage().isBlank()) ? q.passage() : sharedPassage,
                                q.script(), (q.audio() != null && !q.audio().isBlank()) ? q.audio() : sharedAudio,
                                (q.image() != null && !q.image().isBlank()) ? q.image() : sharedImage, q.mediaBase(), q.originalNo());
                        playerQuestions.add(com.hottopik.dto.QuestionPlayerItem.practice(
                                enriched, result.type().skill(), result.type().learnerInstructionKo(), attemptId,
                                sel != null ? String.valueOf(sel) : null, blockGroupKey));
                    }
                }
            }

            model.addAttribute("attempt", result.attempt());
            model.addAttribute("type", result.type());
            model.addAttribute("questions", questions);
            model.addAttribute("checkedAnswersJson", result.attempt().getCheckedAnswersJson());
            model.addAttribute("flagsJson", result.attempt().getFlagsJson() != null ? result.attempt().getFlagsJson() : "[]");
            model.addAttribute("elapsedSeconds", result.attempt().getElapsedSeconds() != null ? result.attempt().getElapsedSeconds() : 0);
            model.addAttribute("playerQuestions", playerQuestions);
            model.addAttribute("pageTitle", "Làm bài luyện: " + result.type().name() + " · HOT TOPIK");
            model.addAttribute("activePage", "practice");
            model.addAttribute("currentUser", user);
            return "practice/play";
        } catch (Exception e) {
            flash.addFlashAttribute("practiceError", "Không tìm thấy lượt làm bài: " + e.getMessage());
            return "redirect:/practice";
        }
    }

    @PostMapping("/practice/save/{attemptId}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> savePractice(
            @PathVariable Long attemptId,
            @RequestParam(required = false, defaultValue = "false") boolean pause,
            @RequestParam MultiValueMap<String, String> form,
            HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) {
            return org.springframework.http.ResponseEntity.status(401).body(Map.of("status", "unauthenticated"));
        }
        Long userId = user.getId();

        Map<String, String> answers = form.entrySet().stream()
                .filter(e -> e.getKey().startsWith("answer_") && !e.getValue().isEmpty())
                .collect(Collectors.toMap(
                        e -> e.getKey().substring("answer_".length()),
                        e -> e.getValue().stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse("")
                ));
        List<String> flags = form.entrySet().stream()
                .filter(e -> e.getKey().startsWith("flag_") && !e.getValue().isEmpty() && "1".equals(e.getValue().get(0)))
                .map(e -> e.getKey().substring("flag_".length()))
                .toList();
        int elapsedSeconds = 0;
        try {
            if (form.containsKey("elapsedSeconds")) {
                elapsedSeconds = Integer.parseInt(form.getFirst("elapsedSeconds"));
            }
        } catch (Exception ignored) {}

        boolean isPause = pause || "true".equalsIgnoreCase(form.getFirst("pause"));
        try {
            PracticeAttempt saved = practice.save(userId, attemptId, answers, flags, elapsedSeconds, isPause);
            if (saved == null) {
                return org.springframework.http.ResponseEntity.ok(Map.of("status", "discarded"));
            }
            return org.springframework.http.ResponseEntity.ok(Map.of("status", "saved", "elapsedSeconds", elapsedSeconds));
        } catch (IllegalArgumentException ex) {
            return org.springframework.http.ResponseEntity.status(422).body(Map.of("status", "not_found", "message", ex.getMessage()));
        }
    }

    /* =========================================================================================
     * LUỒNG CHỨC NĂNG: TẠM DỪNG / LƯU VÀ THOÁT BÀI LUYỆN (PAUSE PRACTICE - FUNC_PRAC_05)
     * -----------------------------------------------------------------------------------------
     * 1. THAO TÁC NGƯỜI DÙNG & UI/HTML:
     *    - Học viên đang làm bài tại practice/play.html, bấm nút "Lưu và thoát" trên thanh header.
     *    - Form action POST gửi toàn bộ đáp án đang chọn dở và số giây làm bài đã trôi qua.
     * 2. HTTP REQUEST & ENDPOINT:
     *    - URL: POST /practice/pause/{attemptId}
     * 3. CONTROLLER & SERVICE XỬ LÝ:
     *    - Controller: com.hottopik.controller.PracticeController.pausePractice()
     *    - Triệu gọi Service: com.hottopik.service.PracticeService.save(userId, attemptId, answers, flags, elapsedSeconds, true)
     *      + Cập nhật answers_json, flags_json, elapsed_seconds vào CSDL.
     *      + Nếu bài làm không có bất kỳ đáp án hay cờ nào (!hasActivity), tự động dọn rác bản ghi.
     * 4. TRUY VẤN CSDL:
     *    - Bảng dbo.practice_attempts (UPDATE answers_json, flags_json, elapsed_seconds).
     * 5. HIỂN THỊ KẾT QUẢ CHO NGƯỜI DÙNG:
     *    - Controller trả về: redirect:/practice/prepare?typeCode={typeCode}
     *    - Màn hình Chuẩn bị hiển thị trạng thái "Bạn có 1 bài làm dở" cho phép học viên quay lại làm tiếp bất cứ lúc nào.
     * ========================================================================================= */
    @PostMapping("/practice/pause/{attemptId}")
    public String pausePractice(
            @PathVariable Long attemptId,
            @RequestParam MultiValueMap<String, String> form,
            HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        Long userId = user != null ? user.getId() : 1L;

        Map<String, String> answers = form.entrySet().stream()
                .filter(e -> e.getKey().startsWith("answer_") && !e.getValue().isEmpty())
                .collect(Collectors.toMap(
                        e -> e.getKey().substring("answer_".length()),
                        e -> e.getValue().stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse("")
                ));
        List<String> flags = form.entrySet().stream()
                .filter(e -> e.getKey().startsWith("flag_") && !e.getValue().isEmpty() && "1".equals(e.getValue().get(0)))
                .map(e -> e.getKey().substring("flag_".length()))
                .toList();
        int elapsedSeconds = 0;
        try {
            if (form.containsKey("elapsedSeconds")) {
                elapsedSeconds = Integer.parseInt(form.getFirst("elapsedSeconds"));
            }
        } catch (Exception ignored) {}

        PracticeAttempt existing = practice.findAttempt(userId, attemptId).orElse(null);
        String typeCode = existing != null ? existing.getTypeCode() : "";

        practice.save(userId, attemptId, answers, flags, elapsedSeconds, true);
        return "redirect:/practice/prepare?typeCode=" + typeCode;
    }

    /* =========================================================================================
     * LUỒNG CHỨC NĂNG: NỘP BÀI LUYỆN TẬP & TÍCH Ô TIẾN ĐỘ LỘ TRÌNH (SUBMIT PRACTICE - FUNC_PRAC_06)
     * -----------------------------------------------------------------------------------------
     * 1. THAO TÁC NGƯỜI DÙNG & UI/HTML:
     *    - Học viên làm xong tất cả các câu trong phòng luyện (src/main/resources/templates/practice/play.html).
     *    - Bấm nút "Nộp bài" trên thanh công cụ: <button type="submit" class="btn-ssak-primary">Nộp bài</button>.
     *    - Form POST gửi danh sách các cặp answer_{questionId} và elapsedSeconds.
     * 2. HTTP REQUEST & ENDPOINT:
     *    - URL: POST /practice/submit/{attemptId}
     * 3. CONTROLLER & SERVICE XỬ LÝ:
     *    - Controller: com.hottopik.controller.PracticeController.submitPractice()
     *    - Triệu gọi Service:
     *      + com.hottopik.service.PracticeService.submit(userId, attemptId, answers, elapsedSeconds):
     *        * Đối chiếu đáp án với QuestionRepository.findAllById().
     *        * Ưu tiên lấy đáp án đã khóa từ checked_answers_json.
     *        * Tính điểm số (score), cập nhật completed_at = LocalDateTime.now().
     *      + com.hottopik.service.exam.LearnerRoadmapService.matchesPracticeSlot():
     *        * Nếu bài mở từ lộ trình (session có RoadmapPractice): Kiểm tra đúng buổi và dạng câu.
     *        * Gọi com.hottopik.service.exam.LearnerRoadmapService.setDone(owner, day, slot, true)
     *          để thêm "day:slot" vào done_keys của StudyPlan, cập nhật chu kỳ 4 lần lặp lại ngắt quãng.
     * 4. TRUY VẤN CSDL:
     *    - Bảng dbo.practice_attempts (UPDATE score, completed_at, elapsed_seconds).
     *    - Bảng dbo.study_plans (UPDATE done_keys = "1:0,1:1...").
     * 5. HIỂN THỊ KẾT QUẢ CHO NGƯỜI DÙNG:
     *    - Controller trả về: redirect:/practice/result/{attemptId}
     *    - Chuyển hướng người học sang trang Kết quả chi tiết (src/main/resources/templates/practice/result.html).
     * ========================================================================================= */
    @PostMapping("/practice/submit/{attemptId}")
    public String submitPractice(@PathVariable Long attemptId, @RequestParam MultiValueMap<String, String> form,
                                 HttpSession session, RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        Long userId = user != null ? user.getId() : 1L;

        Map<String, String> answers = form.entrySet().stream()
                .filter(e -> e.getKey().startsWith("answer_") && !e.getValue().isEmpty())
                .collect(Collectors.toMap(
                        e -> e.getKey().substring("answer_".length()),
                        e -> e.getValue().stream().filter(v -> v != null && !v.isBlank()).findFirst().orElse("")
                ));
        int elapsedSeconds = 0;
        try {
            if (form.containsKey("elapsedSeconds")) {
                elapsedSeconds = Integer.parseInt(form.getFirst("elapsedSeconds"));
            }
        } catch (Exception ignored) {}

        PracticeAttempt attempt = practice.submit(userId, attemptId, answers, elapsedSeconds);
        String key = "ROADMAP_PRACTICE_" + attemptId;
        if (session.getAttribute(key) instanceof RoadmapPractice linked && user != null
                && linked.owner().equals(user.getEmail())) {
            try {
                if (roadmaps.matchesPracticeSlot(linked.owner(), linked.day(), linked.slot(), attempt.getTypeCode())) {
                    roadmaps.setDone(linked.owner(), linked.day(), linked.slot(), true);
                    flash.addFlashAttribute("roadmapLinked", true);
                }
            } catch (ExamDomainException e) {
                flash.addFlashAttribute("practiceError", "Bài làm đã được lưu nhưng lộ trình đã thay đổi. Hãy kiểm tra lại lộ trình.");
            }
            session.removeAttribute(key);
        }
        return "redirect:/practice/result/" + attemptId;
    }

    /* =========================================================================================
     * LUỒNG CHỨC NĂNG: KIỂM TRA ĐÁP ÁN TỨC THÌ (INSTANT CHECK - FUNC_PRAC_04)
     * -----------------------------------------------------------------------------------------
     * 1. THAO TÁC NGƯỜI DÙNG & UI/HTML:
     *    - Học viên đang làm bài tại practice/play.html, chọn 1 phương án trắc nghiệm (1..4).
     *    - Bấm nút "Kiểm tra" trên thẻ câu hỏi con: <button type="button" class="btn-check">Kiểm tra</button>.
     *    - JavaScript (src/main/resources/static/js/question-player.js) gửi request AJAX POST.
     * 2. HTTP REQUEST & ENDPOINT:
     *    - URL: POST /practice/check/{attemptId}/{questionId}?selected={1..4}
     * 3. CONTROLLER & SERVICE XỬ LÝ:
     *    - Controller: com.hottopik.controller.PracticeController.check()
     *    - Triệu gọi Service: com.hottopik.service.PracticeService.check(userId, attemptId, questionId, selected)
     *      + Dùng khóa bi quan: attempts.findOwnedForUpdate(attemptId, userId) chống mở 2 tab thử đáp án.
     *      + Khóa vĩnh viễn lựa chọn đầu tiên vào checked_answers_json trong CSDL.
     *      + Lấy Question từ QuestionRepository, đối chiếu getAnswerIndex() == selected.
     *      + Tạo đối tượng QuestionFeedback chứa: answerIndex, isCorrect, explanationVi, scriptKo (nếu là bài Nghe).
     * 4. TRUY VẤN CSDL:
     *    - Bảng dbo.practice_attempts (SELECT WITH UPDLOCK, UPDATE checked_answers_json).
     *    - Bảng dbo.questions (SELECT answer_index, explanation_vi, group_id).
     * 5. HIỂN THỊ KẾT QUẢ CHO NGƯỜI DÙNG:
     *    - Controller trả về @ResponseBody: com.hottopik.dto.QuestionFeedback (JSON).
     *    - JavaScript question-player.js nhận JSON:
     *      + Đổi màu phương án: Xanh lục nếu đúng, Đỏ nếu sai và highlight đáp án đúng màu xanh.
     *      + Mở hộp giải thích chi tiết (.qp-explanation) và lời thoại tiếng Hàn (.qp-script).
     * ========================================================================================= */
    @PostMapping("/practice/check/{attemptId}/{questionId}")
    @ResponseBody
    public com.hottopik.dto.QuestionFeedback check(@PathVariable Long attemptId, @PathVariable Long questionId,
                                                   @RequestParam int selected, HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        return practice.check(user == null ? null : user.getId(), attemptId, questionId, selected);
    }

    /* =========================================================================================
     * LUỒNG CHỨC NĂNG: XEM KẾT QUẢ LUYỆN TẬP & DANH SÁCH XEM LẠI (RESULT REVIEW - FUNC_PRAC_07)
     * -----------------------------------------------------------------------------------------
     * 1. THAO TÁC NGƯỜI DÙNG & UI/HTML:
     *    - Tự động chuyển hướng đến sau khi nộp bài hoặc học viên click từ Lịch sử luyện tập (practice/prepare.html).
     * 2. HTTP REQUEST & ENDPOINT:
     *    - URL: GET /practice/result/{attemptId}
     * 3. CONTROLLER & SERVICE XỬ LÝ:
     *    - Controller: com.hottopik.controller.PracticeController.practiceResult()
     *    - Triệu gọi Service: com.hottopik.service.PracticeService.result(userId, attemptId):
     *      + Nạp dữ liệu attempt, tính toán số câu đúng, tỷ lệ phần trăm, thời gian thực hiện.
     *      + Phân loại danh sách: "Câu đúng" vs "Câu cần xem lại" (câu làm sai, bỏ trống hoặc chọn đúng nhưng tích Chưa chắc).
     * 4. TRUY VẤN CSDL:
     *    - Bảng dbo.practice_attempts (SELECT lượt làm).
     *    - Bảng dbo.questions & dbo.question_groups (SELECT chi tiết câu hỏi).
     * 5. HIỂN THỊ KẾT QUẢ CHO NGƯỜI DÙNG:
     *    - Trả về View: "practice/result" (src/main/resources/templates/practice/result.html)
     *    - Giao diện hiển thị: Vòng tròn điểm số Ssak, bảng phân tích đúng/sai, danh sách câu cần xem lại
     *      kèm nút "Làm lại câu sai", "Luyện tiếp dạng này" hoặc "Quay về lộ trình".
     * ========================================================================================= */
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
            model.addAttribute("creditedAnswers",com.hottopik.service.exam.CheckedAnswers.read(result.attempt().getCreditedAnswersJson()));
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

    @GetMapping("/practice/mini-test")
    public String miniTest(@RequestParam(value = "skill", defaultValue = "reading") String skill, Model model) {
        model.addAttribute("pageTitle", "Mini Test TOPIK");
        model.addAttribute("activePage", "exam");
        model.addAttribute("skill", skill);
        return "practice/mini-test";
    }

    /** Phục vụ media (audio/image) cho học viên luyện tập */
    @GetMapping({"/practice/media/{sourceId}/{filename:.+}", "/practice/media/{sourceId}/**"})
    public void media(@PathVariable Long sourceId,
                      @PathVariable(required = false) String filename,
                      @RequestHeader(value = "Range", required = false) String range,
                      jakarta.servlet.http.HttpServletRequest request,
                      HttpServletResponse response) throws IOException {
        if (filename == null || filename.isBlank()) {
            String uri = request.getRequestURI();
            String prefix = "/practice/media/" + sourceId + "/";
            int idx = uri.indexOf(prefix);
            if (idx >= 0) filename = uri.substring(idx + prefix.length());
        }
        Optional<MediaAsset> asset = imports.findMedia(sourceId, filename);
        if (asset.isEmpty()) { response.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
        Path path = imports.mediaPath(asset.get());
        if (!Files.exists(path)) { response.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
        MediaResponses.play(path, filename, asset.get().getKind(), range, response);
    }
}
