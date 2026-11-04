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
    private final LearnerRoadmapService roadmaps;
    private final com.hottopik.service.WritingSubmissionService writingSubmissions;

    private record RoadmapPractice(String owner, int day, int slot) {}

    public PracticeController(PracticeService practice, LearnerRoadmapService roadmaps, com.hottopik.service.WritingSubmissionService writingSubmissions) {
        this.practice = practice;
        this.roadmaps = roadmaps;
        this.writingSubmissions = writingSubmissions;
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

    /*
     * typeCode tra cứu cấu hình dạng trong QuestionTypeCatalog, sau đó lấy thống kê và lịch sử đã nộp.
     * Trang prepare hiển thị cảnh báo khi chưa có dữ liệu; có dữ liệu thì POST /practice/start.
     * Một số route Luyện tập hiện còn fallback userId=1 khi session trống; đây là hành vi sẵn có.
     */
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

        List<PracticeAttempt> history = practice.historyOfType(userId, typeCode);

        model.addAttribute("pageTitle", "Chuẩn bị bài luyện · HOT TOPIK");
        model.addAttribute("activePage", "practice");
        model.addAttribute("type", type);
        model.addAttribute("summary", summary);
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

    /*
     * Khởi tạo bài luyện: kiểm tra day/slot nếu bài xuất phát từ lộ trình.
     * Viết chuyển sang /writing khi có đề ACTIVE; Nghe/Đọc tạo PracticeAttempt qua practice.start().
     * Lưu liên kết lộ trình trong session theo attemptId để khi nộp mới đánh dấu đúng buổi hoàn thành.
     * Thiếu bộ câu đầy đủ sẽ quay lại prepare với flash practiceError, không tạo bài luyện thiếu câu.
     */
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
            return "redirect:/writing?question=" + type.numbers().get(0);
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
            model.addAttribute("attempt", result.attempt());
            model.addAttribute("type", result.type());
            model.addAttribute("questions", questions);
            model.addAttribute("checkedAnswersJson",result.attempt().getCheckedAnswersJson());
            model.addAttribute("playerQuestions", questions.stream().map(q -> com.hottopik.dto.QuestionPlayerItem.practice(
                    q, result.type().skill(), result.type().learnerInstructionKo(), attemptId)).toList());
            model.addAttribute("pageTitle", "Làm bài luyện: " + result.type().name() + " · HOT TOPIK");
            model.addAttribute("activePage", "practice");
            model.addAttribute("currentUser", user);
            return "practice/play";
        } catch (Exception e) {
            flash.addFlashAttribute("practiceError", "Không tìm thấy lượt làm bài: " + e.getMessage());
            return "redirect:/practice";
        }
    }

    /*
     * Chỉ lấy các trường answer_<questionId>, bỏ tiền tố rồi giao service kiểm tra và chấm.
     * Sau khi lưu bài mới thử cập nhật buổi lộ trình đã liên kết; lỗi lộ trình không xóa kết quả đã lưu.
     * Redirect GET kết quả để tải lại trang không nộp lại form POST.
     */
    @PostMapping("/practice/submit/{attemptId}")
    public String submitPractice(@PathVariable Long attemptId, @RequestParam MultiValueMap<String, String> form,
                                 HttpSession session, RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        Long userId = user != null ? user.getId() : 1L;

        Map<String, String> answers = form.entrySet().stream()
                .filter(e -> e.getKey().startsWith("answer_"))
                .collect(Collectors.toMap(e -> e.getKey().substring("answer_".length()), e -> e.getValue().get(0)));
        PracticeAttempt attempt = practice.submit(userId, attemptId, answers);
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

    /*
     * API Kiểm tra trả JSON phản hồi cho từng câu; bắt buộc có người dùng thật từ session, không dùng fallback ID.
     */
    @PostMapping("/practice/check/{attemptId}/{questionId}")
    @ResponseBody
    public com.hottopik.dto.QuestionFeedback check(@PathVariable Long attemptId, @PathVariable Long questionId,
                                                   @RequestParam int selected, HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        return practice.check(user == null ? null : user.getId(), attemptId, questionId, selected);
    }

    /*
     * Dựng result cho trang chi tiết và lịch sử cùng dạng; lượt chưa nộp được trả về màn làm bài.
     */
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
}
