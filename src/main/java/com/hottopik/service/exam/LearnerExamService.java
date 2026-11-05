package com.hottopik.service.exam;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.ExamAttemptRepository;
import com.hottopik.repository.exam.ExamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class LearnerExamService {
    public record Card(Long id, String title, String badge, String badgeColor, String badgeTextColor,
                       String sub, String history, boolean isDone, boolean inProgress,
                       String btnOutline, String btnPrimary, String outlineUrl, String primaryUrl,
                       boolean primaryPost) {}
    // Answers/explanations are included only in the result DTO, never in the play model.
    public record Choice(String text, String image) {}
    public record QuestionView(Long questionId, int no, String key, Skill skill, String label, String instruction, String prompt, String passage,
                               String audio, String image, List<Choice> options, String selected,
                               Integer answer, String explanation, String script) {
        public Long id() { return questionId; }
    }
    public record WritingView(Long promptId, String key, int no, String prompt, String image, List<Map<String, Object>> chart,
                              String selected, String modelAnswer, String rubric) {
        public Long id() { return promptId; }
    }
    public record Paper(ExamAttempt attempt, String title, Track track, ExamKind kind,
                        int remainingSeconds, List<QuestionView> questions, List<WritingView> writing,
                        List<ExamAttempt> history) {}
    public record SaveResult(int remainingSeconds, boolean completed, String backUrl) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private final ExamRepository exams;
    private final ExamAttemptRepository attempts;
    private final com.hottopik.service.WritingSubmissionService writingSubmissions;

    public LearnerExamService(ExamRepository exams, ExamAttemptRepository attempts,
                              com.hottopik.service.WritingSubmissionService writingSubmissions) {
        this.exams = exams;
        this.attempts = attempts;
        this.writingSubmissions = writingSubmissions;
    }

    @Transactional(readOnly = true)
    /*
     * Tạo sẵn các ngăn I/II và full/listening/reading/writing để frontend vẫn render được khi ngăn rỗng.
     * Chỉ giữ đề đã phát hành (hoặc đang chờ duyệt lưu trữ) và còn dữ liệu dùng được.
     * Nhóm lịch sử theo exam.id: full mock và mini có ID khác nhau nên không chia sẻ trạng thái đang làm/đã làm.
     * Ưu tiên lượt còn tiếp tục được, sau đó kết quả đã nộp; Card chứa luôn nhãn/nút/URL cho giao diện.
     */
    public Map<String, Map<String, List<Card>>> library(Long userId) {
        Map<String, Map<String, List<Card>>> data = new LinkedHashMap<>();
        for (String track : List.of("I", "II")) {
            Map<String, List<Card>> kinds = new LinkedHashMap<>();
            for (String kind : List.of("full", "listening", "reading", "writing")) kinds.put(kind, new ArrayList<>());
            data.put(track, kinds);
        }
        Map<Long, List<ExamAttempt>> history = userId == null ? Map.of()
                : attempts.findByUserIdOrderByStartedAtDescIdDesc(userId).stream()
                .collect(Collectors.groupingBy(a -> a.getExam().getId()));
        for (Exam listed : exams.findAllForLibrary()) {
            if (!published(listed)) continue;
            Exam exam = exams.findForComposerById(listed.getId()).orElseThrow();
            if (!usable(exam)) continue;
            List<ExamAttempt> mine = history.getOrDefault(exam.getId(), List.of());
            ExamAttempt pending = mine.stream().filter(this::resumable).findFirst().orElse(null);
            ExamAttempt done = mine.stream().filter(a -> a.getCompletedAt() != null).findFirst().orElse(null);
            String state = pending != null ? "Đang làm dở" : done != null ? "Đã làm" : "Chưa làm";
            long completed = mine.stream().filter(a -> a.getCompletedAt() != null).count();
            String recent = done == null ? "Chưa nộp bài" : "Lần gần nhất: "
                    + (done.getTotal() == 0 ? "Đã lưu bài Viết" : done.getCorrect() + "/" + done.getTotal() + " câu trắc nghiệm đúng")
                    + " · đã làm " + completed + " lần";
            if (pending != null) recent = "Đã lưu " + read(pending).size() + " câu trả lời · " + recent;
            Card card = new Card(exam.getId(), exam.getName(), state,
                    pending != null ? "#FEF3C7" : done != null ? "#E6F4EA" : "#ECEAE4",
                    pending != null ? "#B45309" : done != null ? "#137333" : "#5F6368",
                    sectionLabels(exam) + " · " + exam.questionCount() + " câu · " + minutes(exam) + " phút",
                    recent, done != null && pending == null, pending != null,
                    pending != null && done != null ? "Xem kết quả" : done != null ? "Làm lại" : null,
                    pending != null ? "Làm tiếp" : done != null ? "Xem kết quả" : "Bắt đầu",
                    pending != null && done != null ? "/ket-qua/" + done.getId() : "/exam/" + exam.getId() + "/retry",
                    pending != null ? "/lam-bai/" + pending.getId() : done != null ? "/ket-qua/" + done.getId()
                            : "/exam/" + exam.getId() + "/start",
                    pending == null && done == null);
            data.get(exam.getTrack() == Track.TOPIK_I ? "I" : "II")
                    .get(exam.getKind().name().toLowerCase(Locale.ROOT)).add(card);
        }
        return data;
    }

    /*
     * Khóa đề trong transaction, xác minh đề được phép làm và lấy đúng người dùng.
     * Start thường nối lượt dở còn hợp lệ của cùng examId; retry luôn tạo ExamAttempt mới.
     * Khởi tạo số giây từ thời lượng đề và total là số câu MC; câu Viết không được chấm đúng/sai tự động ở đây.
     */
    public ExamAttempt start(Long userId, Long examId, boolean retry) {
        requireUser(userId);
        exams.lockById(examId).orElseThrow(this::notFound);
        Exam exam = exams.findForComposerById(examId).orElseThrow(this::notFound);
        if (!published(exam) || !usable(exam)) throw new ExamDomainException("Đề này hiện chưa được phát hành hoặc chưa đủ dữ liệu để làm bài.");
        if (!retry) {
            Optional<ExamAttempt> pending = attempts.findByUserIdOrderByStartedAtDescIdDesc(userId).stream()
                    .filter(a -> a.getExam().getId().equals(examId) && resumable(a)).findFirst();
            if (pending.isPresent()) return pending.get();
        }
        ExamAttempt attempt = new ExamAttempt();
        attempt.setExam(exam);
        attempt.setUserId(userId);
        attempt.setRemainingSeconds(minutes(exam) * 60);
        attempt.setLastActiveAt(LocalDateTime.now());
        attempt.setTotal(exam.getItems().size());
        return attempts.save(attempt);
    }

    /*
     * Khóa lượt thuộc chủ sở hữu; hết hạn/hết giờ thì hoàn tất, còn hợp lệ thì chốt thời gian và bắt đầu tính tiếp.
     */
    public Paper play(Long userId, Long attemptId) {
        ExamAttempt attempt = owned(userId, attemptId);
        if (attempt.getCompletedAt() == null) {
            if (!resumable(attempt)) finish(attempt);
            else {
                checkpoint(attempt);
                attempt.setLastActiveAt(LocalDateTime.now());
            }
        }
        return paper(attempt, false);
    }

    public Paper result(Long userId, Long attemptId) {
        ExamAttempt attempt = owned(userId, attemptId);
        if (attempt.getCompletedAt() == null) throw new ExamDomainException("Bạn chưa nộp bài này.");
        return paper(attempt, true);
    }

    /** Khóa lượt thi để nộp và gửi chấm nguyên tử, tránh tạo bài chấm trùng khi gửi lại request. */
    public void sendWritingToTeacher(com.hottopik.dto.UserDTO user, Long attemptId, Map<String, String> answers, boolean submit) {
        if (!user.isVip()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Gửi Giáo viên chấm là quyền lợi VIP.");
        ExamAttempt attempt = owned(user.getId(), attemptId);
        if (submit) save(user.getId(), attemptId, answers, false, true);
        if (attempt.getCompletedAt() == null) throw new ExamDomainException("Vui lòng nộp bài trước khi gửi Giáo viên chấm.");
        Map<String, String> saved = read(attempt);
        List<WritingPrompt> writing = exam(attempt).writingPrompts();
        if (writing.stream().noneMatch(p -> com.hottopik.service.WritingSubmissionService.hasAnswer(p.getNo(), saved.get("w" + p.getId()))))
            throw new ExamDomainException("Cần có nội dung bài viết để gửi Giáo viên chấm.");
        // Chỉ gửi những câu được phép (có nội dung, chưa gửi/chấm, không bị từ chối ở lượt này).
        var thisAttempt = writingSubmissions.examSubmissions(user.getId(), attemptId);
        var sentAny = writingSubmissions.sentToTeacherByPrompt(user.getId());
        int sent = 0;
        String rejected = null;
        for (WritingPrompt p : writing) {
            String ans = saved.get("w" + p.getId());
            String reason = com.hottopik.service.WritingSubmissionService.examSendBlockReason(
                    thisAttempt.get(p.getId()), sentAny.get(p.getId()), p.getNo(), ans);
            if (reason == null) {
                writingSubmissions.submitExam(user, p.getId(), ans, attemptId, attempt.getExam().getName());
                sent++;
            } else if (com.hottopik.service.WritingSubmissionService.isRejectedExamSubmission(thisAttempt.get(p.getId()))) {
                rejected = reason;
            }
        }
        if (sent == 0 && rejected != null) throw new ExamDomainException(rejected);
    }

    public Map<Long, com.hottopik.dto.WritingSubmissionDTO> writingReviews(Long userId, Long attemptId) {
        owned(userId, attemptId);
        return writingSubmissions.examSubmissions(userId, attemptId);
    }

    @Transactional(readOnly = true)
    public Map<Long, com.hottopik.dto.WritingSubmissionDTO> sentToTeacherByPrompt(Long userId) {
        return writingSubmissions.sentToTeacherByPrompt(userId);
    }
    public Map<Long, com.hottopik.dto.WritingSubmissionDTO> rejectedByPrompt(Long userId) {
        return writingSubmissions.rejectedByPrompt(userId);
    }

    /*
     * Đã nộp thì không sửa nữa; lượt không còn hợp lệ được kết thúc bằng đáp án đã lưu, không nhận bài đến muộn.
     * Duyệt câu thuộc đề để whitelist key: q<ExamItem.id> nhận 1..4, w<WritingPrompt.id> nhận bài tối đa 6000 ký tự.
     * Gộp vào answersJson hiện tại; chốt giờ ở server, pause ngừng đồng hồ, submit gọi finish().
     * Không dùng questionId làm key MC vì cùng câu ngân hàng có thể xuất hiện trong các đề khác nhau.
     */
    public void sendSingleWritingToTeacher(com.hottopik.dto.UserDTO user, Long attemptId, Long promptId) {
        if (!user.isVip()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Gui Giao vien cham la quyen loi VIP.");
        ExamAttempt attempt = owned(user.getId(), attemptId);
        if (attempt.getCompletedAt() == null) throw new ExamDomainException("Vui long nop bai truoc khi gui Giao vien cham.");
        Map<String, String> saved = read(attempt);
        WritingPrompt p = exam(attempt).writingPrompts().stream()
                .filter(wp -> wp.getId().equals(promptId)).findFirst()
                .orElseThrow(() -> new ExamDomainException("Khong tim thay cau viet."));
        String ans = saved.get("w" + p.getId());
        if (!com.hottopik.service.WritingSubmissionService.hasAnswer(p.getNo(), ans))
            throw new ExamDomainException("Cau nay chua co noi dung nen khong the gui Giao vien cham.");
        writingSubmissions.submitExam(user, p.getId(), ans, attemptId, attempt.getExam().getName());
    }

    public SaveResult save(Long userId, Long attemptId, Map<String, String> submitted, boolean pause, boolean submit) {
        ExamAttempt attempt = owned(userId, attemptId);
        if (attempt.getCompletedAt() != null) return saved(attempt);
        if (!resumable(attempt)) {
            finish(attempt);
            return saved(attempt);
        }
        boolean running = attempt.getLastActiveAt() != null;
        checkpoint(attempt);
        Map<String, String> answers = read(attempt);
        Exam exam = exam(attempt);
        Map<String,String> lockedAnswers = CheckedAnswers.read(attempt.getCheckedAnswersJson());
        for (ExamItem item : exam.getItems()) {
            String key = "q" + item.getId();
            if (lockedAnswers.containsKey(key)) { answers.put(key, lockedAnswers.get(key)); continue; }
            if (!submitted.containsKey(key)) continue;
            String value = submitted.get(key);
            if (value != null && value.matches("[1-4]")) answers.put(key, value);
            else answers.remove(key);
        }
        for (WritingPrompt prompt : exam.writingPrompts()) {
            String key = "w" + prompt.getId();
            if (!submitted.containsKey(key)) continue;
            String value = Optional.ofNullable(submitted.get(key)).orElse("").trim();
            if (value.length() > 6000) throw new ExamDomainException("Bài Viết không được vượt quá 6000 ký tự mỗi câu.");
            if (value.isEmpty()) answers.remove(key);
            else answers.put(key, value);
        }
        try { attempt.setAnswersJson(JSON.writeValueAsString(answers)); }
        catch (Exception e) { throw new IllegalStateException("Không lưu được câu trả lời.", e); }
        attempt.setLastActiveAt(!pause && running ? LocalDateTime.now() : null);
        if (submit) finish(attempt);
        return saved(attempt);
    }

    /*
     * API Kiểm tra xác minh quyền/key và giờ; lưu rồi khóa lựa chọn, trả lời giải. Điểm chỉ chốt khi nộp bài.
     */
    public com.hottopik.dto.QuestionFeedback check(Long userId, Long attemptId, String key, int selected) {
        ExamAttempt attempt = owned(userId, attemptId);
        if (selected < 1 || selected > 4) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        ExamItem item = exam(attempt).getItems().stream().filter(i -> ("q" + i.getId()).equals(key))
                .findFirst().orElseThrow(this::notFound);
        if (attempt.getCompletedAt() != null || !resumable(attempt)) throw new ExamDomainException("Bài đã kết thúc.");
        checkpoint(attempt);
        Map<String,String> locked = CheckedAnswers.read(attempt.getCheckedAnswersJson());
        locked.putIfAbsent(key, String.valueOf(selected));
        selected = Integer.parseInt(locked.get(key));
        attempt.setCheckedAnswersJson(CheckedAnswers.write(locked));
        Map<String,String> answers = read(attempt); answers.put(key, String.valueOf(selected));
        attempt.setAnswersJson(CheckedAnswers.write(answers));
        Question q = item.getQuestion();
        return new com.hottopik.dto.QuestionFeedback(q.getAnswerIndex(), q.getAnswerIndex() == selected,
                q.getExplanationVi(), item.getSection() == Skill.LISTENING ? q.getGroup().getScriptKo() : null, null, selected);
    }

    private SaveResult saved(ExamAttempt attempt) {
        Exam exam = attempt.getExam();
        return new SaveResult(attempt.getRemainingSeconds(), attempt.getCompletedAt() != null,
                "/exam?track=" + (exam.getTrack() == Track.TOPIK_I ? "I" : "II")
                        + "&kind=" + exam.getKind().name().toLowerCase(Locale.ROOT));
    }

    /*
     * Dựng DTO cho template theo thứ tự kỹ năng và số câu; phục hồi lựa chọn từ answersJson.
     * result=false che đáp án/giải thích/lời thoại trong dữ liệu trang làm bài; nút Kiểm tra lấy riêng qua API.
     * result=true bổ sung lời giải/bài mẫu và lịch sử đã nộp của chính user + examId.
     */
    private Paper paper(ExamAttempt attempt, boolean result) {
        Exam exam = exam(attempt);
        Map<String, String> answers = read(attempt);
        List<QuestionView> questions = exam.getItems().stream()
                .sorted(Comparator.comparingInt((ExamItem i) -> sectionOrder(exam, i.getSection())).thenComparingInt(ExamItem::getNo))
                .map(i -> {
                    Question q = i.getQuestion();
                    QuestionGroup g = q.getGroup();
                    if (g == null) {
                        throw new ExamDomainException("Đề này còn câu chưa gắn nhóm nghe hoặc đọc, nên chưa mở được.");
                    }
                    String key = "q" + i.getId();
                    return new QuestionView(q.getId(), i.getNo(), key, i.getSection(), i.getSection().getLabel() + " · Câu " + i.getNo(),
                            QuestionTypeCatalog.resolve(exam.getTrack(), i.getSection(), i.getNo()).map(QuestionTypeDef::instructionKo).orElse(""),
                            q.getPromptKo(), g.getPassageKo(), media(g.getSource(), g.getAudioFile()), media(g.getSource(), g.getImageFile()),
                            q.options().stream().map(o -> new Choice(o, isImage(o) ? media(q.getSource(), o) : null)).toList(),
                            answers.get(key), result ? q.getAnswerIndex() : null,
                            result ? q.getExplanationVi() : null, result ? g.getScriptKo() : null);
                }).toList();
        var reviews = result ? writingSubmissions.examSubmissions(attempt.getUserId(), attempt.getId())
                : Map.<Long, com.hottopik.dto.WritingSubmissionDTO>of();
        List<WritingView> writing = exam.writingPrompts().stream().map(p -> {
            var review = reviews.get(p.getId());
            boolean graded = review != null && review.getTotalScore() != null;
            return new WritingView(p.getId(), "w" + p.getId(), p.getNo(), p.getPromptKo(),
                    media(p.getSource(), p.getChartImage()), chart(p.getChartJson()), answers.get("w" + p.getId()),
                    graded ? review.getModelAnswer() : null, graded ? p.getRubricNote() : null);
        }).toList();
        // Do not expose the entity's question associations (or their answer keys) to the browser.
        List<ExamAttempt> history = result ? attempts.findByUserIdOrderByStartedAtDescIdDesc(attempt.getUserId()).stream()
                .filter(a -> a.getExam().getId().equals(exam.getId()) && a.getCompletedAt() != null).toList() : List.of();
        return new Paper(attempt, exam.getName(), exam.getTrack(), exam.getKind(), remaining(attempt), questions, writing, history);
    }

    /*
     * Tính số MC đúng từ đáp án ngân hàng tại server, ghi completedAt và dừng giờ; bài Viết chỉ được giữ nội dung.
     */
    private void finish(ExamAttempt attempt) {
        checkpoint(attempt);
        Map<String, String> answers = read(attempt);
        Exam exam = exam(attempt);
        attempt.setCorrect((int) exam.getItems().stream()
                .filter(i -> Integer.toString(i.getQuestion().getAnswerIndex()).equals(answers.get("q" + i.getId()))).count());
        attempt.setCompletedAt(LocalDateTime.now());
        attempt.setLastActiveAt(null);
    }

    @Transactional(readOnly = true)
    public boolean canReadMedia(Long userId, Long sourceId) {
        if (userId == null) return false;
        Set<Long> allowed = exams.findAllForLibrary().stream().filter(LearnerExamService::published)
                .map(Exam::getId).collect(Collectors.toSet());
        attempts.findByUserIdOrderByStartedAtDescIdDesc(userId).forEach(a -> allowed.add(a.getExam().getId()));
        return allowed.stream().map(id -> exams.findForComposerById(id).orElseThrow()).anyMatch(e ->
                e.getItems().stream().anyMatch(i -> sameSource(i.getQuestion().getSource(), sourceId)
                        || sameSource(i.getQuestion().getGroup().getSource(), sourceId))
                || e.writingPrompts().stream().anyMatch(p -> sameSource(p.getSource(), sourceId)));
    }

    private static boolean sameSource(ExamSource source, Long sourceId) {
        return source != null && sourceId.equals(source.getId());
    }

    /*
     * remaining() trừ thời gian server từ lastActiveAt; checkpoint lưu số giây còn lại rồi xóa mốc chạy, tránh trừ hai lần.
     */
    private void checkpoint(ExamAttempt attempt) {
        attempt.setRemainingSeconds(remaining(attempt));
        attempt.setLastActiveAt(null);
    }

    private int remaining(ExamAttempt attempt) {
        long elapsed = attempt.getLastActiveAt() == null ? 0 : Math.max(0, Duration.between(attempt.getLastActiveAt(), LocalDateTime.now()).getSeconds());
        return (int) Math.max(0, attempt.getRemainingSeconds() - elapsed);
    }

    /*
     * Làm tiếp được khi chưa nộp, còn giây và chưa quá 7 ngày từ lúc bắt đầu; đồng hồ trình duyệt không quyết định quyền này.
     */
    private boolean resumable(ExamAttempt attempt) {
        return attempt.getCompletedAt() == null && attempt.getStartedAt().plusDays(7).isAfter(LocalDateTime.now()) && remaining(attempt) > 0;
    }

    private ExamAttempt owned(Long userId, Long id) {
        requireUser(userId);
        return attempts.findOwnedForUpdate(id, userId).orElseThrow(this::notFound);
    }

    private Exam exam(ExamAttempt attempt) {
        return exams.findForComposerById(attempt.getExam().getId()).orElseThrow(this::notFound);
    }

    private static boolean published(Exam e) {
        return e.getStatus() == ExamStatus.PUBLISHED
                || (e.getStatus() == ExamStatus.PENDING_APPROVAL && "ARCHIVE".equals(e.getPendingAction()));
    }

    private static boolean usable(Exam e) {
        return e.questionCount() > 0 && (e.getSource() == null || (!e.getSource().isAssessment() && e.getSource().getStatus() == SourceStatus.ACTIVE))
                && e.getItems().stream().allMatch(i -> i.getQuestion().getStatus() == QuestionStatus.ACTIVE
                && i.getQuestion().getPurpose() != QuestionPurpose.ASSESSMENT)
                && e.writingPrompts().stream().allMatch(p -> p.getStatus() == QuestionStatus.ACTIVE);
    }

    private static int minutes(Exam e) {
        if (e.getKind() != ExamKind.FULL) return Math.max(1, e.totalMinutes());
        return Math.max(1, (e.getItems().stream().anyMatch(i -> i.getSection() == Skill.LISTENING) ? e.getListeningMinutes() : 0)
                + (e.getItems().stream().anyMatch(i -> i.getSection() == Skill.READING) ? e.getReadingMinutes() : 0)
                + (!e.writingPrompts().isEmpty() ? e.getWritingMinutes() : 0));
    }

    private static String sectionLabels(Exam e) {
        return QuestionTypeCatalog.sections(e.getTrack()).stream().map(QuestionTypeCatalog.Section::skill)
                .filter(s -> s == Skill.WRITING ? !e.writingPrompts().isEmpty() : e.getItems().stream().anyMatch(i -> i.getSection() == s))
                .map(Skill::getLabel).collect(Collectors.joining(" · "));
    }

    private static int sectionOrder(Exam exam, Skill skill) {
        return QuestionTypeCatalog.sections(exam.getTrack()).stream().map(QuestionTypeCatalog.Section::skill).toList().indexOf(skill);
    }

    private Map<String, String> read(ExamAttempt attempt) {
        try { return JSON.readValue(attempt.getAnswersJson(), new TypeReference<LinkedHashMap<String, String>>() {}); }
        catch (Exception e) { throw new IllegalStateException("Không đọc được câu trả lời đã lưu.", e); }
    }

    private static String media(ExamSource source, String filename) {
        if (source == null || filename == null || filename.isBlank()) return null;
        String encoded = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return "/exam/media/" + source.getId() + "/" + encoded;
    }

    private static boolean isImage(String option) {
        return option != null && option.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|gif|webp)$");
    }

    private static List<Map<String, Object>> chart(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode row : JSON.readTree(json)) {
                if (row.isArray() && row.size() >= 2) rows.add(Map.of("label", row.get(0).asText(), "value", row.get(1).asText()));
                else if (row.isObject()) rows.add(Map.of("label", row.path("label").asText(), "value", row.path("value").asText()));
            }
            return rows;
        }
        catch (Exception e) { return List.of(); }
    }

    private static void requireUser(Long userId) {
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để làm bài.");
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài làm.");
    }
}
