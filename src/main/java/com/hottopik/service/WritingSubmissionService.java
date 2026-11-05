package com.hottopik.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hottopik.dto.UserDTO;
import com.hottopik.dto.WritingSubmissionDTO;
import com.hottopik.dto.WritingSubmissionDTO.CriterionScore;
import com.hottopik.entity.WritingSubmission;
import com.hottopik.entity.UserNotification;
import com.hottopik.entity.exam.WritingPrompt;
import com.hottopik.repository.WritingSubmissionRepository;
import com.hottopik.repository.UserNotificationRepository;
import com.hottopik.repository.exam.WritingPromptRepository;
import com.hottopik.entity.exam.QuestionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class WritingSubmissionService {
    private final WritingSubmissionRepository submissions;
    private final WritingPromptRepository prompts;
    private final com.hottopik.repository.UserRepository users;
    private final UserNotificationRepository notifications;
    private final ObjectMapper json;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Transactional(readOnly = true)
    public List<WritingSubmissionDTO> getAllSubmissions() {
        return submissions.findAll().stream().map(this::decode).filter(s -> !"submitted".equals(s.getStatus())).toList();
    }
    @Transactional(readOnly = true)
    public List<WritingSubmissionDTO> getSubmissionsByLearner(Long userId) {
        return submissions.findByLearnerId(userId).stream().map(this::decode)
                .sorted(Comparator.comparing((WritingSubmissionDTO s) -> LocalDateTime.parse(s.getSubmittedAt(), FMT)).reversed()).toList();
    }
    @Transactional(readOnly = true)
    public List<WritingSubmissionDTO> getPracticeSubmissionsByLearner(Long userId) {
        return getSubmissionsByLearner(userId).stream()
                .filter(s -> "submitted".equalsIgnoreCase(s.getStatus())).toList();
    }
    @Transactional(readOnly = true)
    public Optional<WritingSubmissionDTO> getById(String id) {
        Long key = submissionKey(id);
        return key == null ? Optional.empty() : submissions.findById(key).map(this::decode);
    }
    public WritingSubmissionDTO owned(String id, Long learnerId) {
        WritingSubmissionDTO s = getById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!Objects.equals(s.getLearnerId(), learnerId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return s;
    }
    public WritingSubmissionDTO submit(UserDTO user, Long promptId, String answer, boolean teacher) {
        return submit(user, promptId, answer, teacher, null, null);
    }
    /** Caller khóa và xác minh lượt thi; chỉ nhận nội dung đã lưu của chính lượt đó. */
    public WritingSubmissionDTO submitExam(UserDTO user, Long promptId, String answer, Long attemptId, String title) {
        if (!user.isVip()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Gửi Giáo viên chấm là quyền lợi VIP.");
        WritingPrompt p = prompts.findById(promptId).orElseThrow(() -> new IllegalArgumentException("Đề viết không còn khả dụng."));
        String reason = examSendBlockReason(examSubmissions(user.getId(), attemptId).get(promptId),
                sentToTeacherByPrompt(user.getId()).get(promptId), p.getNo(), answer);
        if (reason != null) throw new IllegalArgumentException(reason);
        return submit(user, promptId, answer, true, attemptId, title);
    }

    /**
     * Quy tắc gửi GV cho một câu Viết của một lượt thi. Trả về lý do bị chặn, hoặc null nếu được gửi.
     * - Bài của chính lượt này đã bị từ chối: phải làm lại đề (lượt mới) mới được gửi lại.
     * - Câu đã gửi/đang chấm/đã chấm (ở bất kỳ lượt nào): không được gửi nữa.
     */
    public static String examSendBlockReason(WritingSubmissionDTO thisAttempt, WritingSubmissionDTO sentAny, int no, String answer) {
        if (isRejectedExamSubmission(thisAttempt))
            return "Giáo viên đã từ chối chấm câu này. Hãy làm lại đề để gửi lại Giáo viên chấm.";
        WritingSubmissionDTO sent = thisAttempt != null ? thisAttempt : sentAny;
        if (sent != null)
            return "graded".equalsIgnoreCase(sent.getStatus()) ? "Câu này đã được Giáo viên chấm, không thể gửi lại."
                    : "Câu này đã được gửi Giáo viên chấm.";
        if (!hasAnswer(no, answer)) return "Câu này chưa có nội dung nên không thể gửi Giáo viên chấm.";
        return null;
    }

    /** Bài đề thi chỉ ở trạng thái "submitted" sau khi GV từ chối (kể cả bản ghi cũ không lưu rejectReason). */
    public static boolean isRejectedExamSubmission(WritingSubmissionDTO s) {
        return s != null && s.getExamAttemptId() != null && "submitted".equalsIgnoreCase(s.getStatus());
    }
    @Transactional(readOnly = true)
    public Map<Long, WritingSubmissionDTO> examSubmissions(Long userId, Long attemptId) {
        Map<Long, WritingSubmissionDTO> result = new LinkedHashMap<>();
        for (var s : getSubmissionsByLearner(userId))
            if (Objects.equals(attemptId, s.getExamAttemptId())) result.put(s.getPromptId(), s);
        return result;
    }
    /** Map promptId → submission đã gửi GV (bất kể attempt nào) cho user. */
    @Transactional(readOnly = true)
    public Map<Long, WritingSubmissionDTO> sentToTeacherByPrompt(Long userId) {
        Map<Long, WritingSubmissionDTO> result = new LinkedHashMap<>();
        for (var s : getSubmissionsByLearner(userId))
            if (!"submitted".equalsIgnoreCase(s.getStatus()) && !"draft".equalsIgnoreCase(s.getStatus()))
                result.putIfAbsent(s.getPromptId(), s);
        return result;
    }
    @Transactional(readOnly = true)
    public Map<Long, WritingSubmissionDTO> rejectedByPrompt(Long userId) {
        Map<Long, WritingSubmissionDTO> result = new LinkedHashMap<>();
        for (var s : getSubmissionsByLearner(userId))
            if ("submitted".equalsIgnoreCase(s.getStatus()) && s.getRejectReason() != null && !s.getRejectReason().isBlank())
                result.put(s.getPromptId(), s);
        return result;
    }
    public static boolean canResendRejected(WritingSubmissionDTO rejected, String answer) {
        return rejected != null && !normalizeAnswer(rejected.getStudentAnswer()).equals(normalizeAnswer(answer));
    }
    private static String normalizeAnswer(String answer) {
        return Objects.toString(answer, "").replace("①", "").replace("②", "").trim();
    }
    private WritingSubmissionDTO submit(UserDTO user, Long promptId, String answer, boolean teacher, Long attemptId, String title) {
        if (user.getId() == null || users.findById(user.getId())
                .filter(account -> account.getEmail().equalsIgnoreCase(Objects.toString(user.getEmail(), ""))).isEmpty())
            throw new IllegalArgumentException("Phiên đăng nhập không khớp tài khoản đã lưu trong hệ thống. Vui lòng đăng nhập bằng tài khoản đã đăng ký trước khi nộp bài.");
        if (teacher && !user.isVip()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Gửi Giáo viên chấm là quyền lợi VIP.");
        WritingPrompt p = prompts.findById(promptId).filter(w -> w.getStatus() == QuestionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Đề viết không còn khả dụng."));
        answer = Objects.toString(answer, "").trim();
        if (p.getNo() < 51 || p.getNo() > 54 || answer.length() > 6000)
            throw new IllegalArgumentException("Bài viết tối đa 6000 ký tự.");
        if (teacher && attemptId == null && !hasAnswer(p.getNo(), answer))
            throw new IllegalArgumentException("Cần có nội dung bài làm để gửi Giáo viên chấm.");
        WritingSubmissionDTO s = new WritingSubmissionDTO();
        s.setExamAttemptId(attemptId);
        s.setLearnerId(user.getId()); s.setLearnerName(user.getFullName()); s.setLearnerEmail(user.getEmail());
        s.setPromptId(p.getId()); s.setQuestionNo(p.getNo()); s.setPrompt(p.getPromptKo());
        s.setSourceId(p.getSource()==null?null:p.getSource().getId());s.setChartFilename(p.getChartImage());
        s.setHasChart(false);
        if(p.getChartJson()!=null && !p.getChartJson().isBlank()) {
            try {var rows=json.readTree(p.getChartJson());if(rows.isArray())for(var row:rows) {
                s.getChartItems().add(new WritingSubmissionDTO.ChartItem(row.isArray()?row.path(0).asText():row.path("label").asText(),row.isArray()?row.path(1).asDouble():row.path("value").asDouble(),"100%"));
            }s.setHasChart(!s.getChartItems().isEmpty());}catch(Exception ex){throw new IllegalArgumentException("Dữ liệu biểu đồ không hợp lệ.");}
        }
        s.setStudentAnswer(answer.trim()); s.setWordCount(answer.trim().length());
        s.setModelAnswer(p.getNo() <= 52 ? "㉠ " + Objects.toString(p.getKeys1(), "") + "\n㉡ " + Objects.toString(p.getKeys2(), "") : p.getModelAnswer());
        if (p.getNo() <= 52 && (p.getKeys1() == null || p.getKeys2() == null)) s.setModelAnswer(p.getModelAnswer());
        s.setMaxScore(p.getNo() == 54 ? 50 : p.getNo() == 53 ? 30 : 10);
        if (p.getNo() <= 52) s.setCriteriaScores(new ArrayList<>(List.of(new CriterionScore("Chỗ trống ㉠", 0, 5), new CriterionScore("Chỗ trống ㉡", 0, 5))));
        s.setTitle(attemptId == null ? "Luyện tập · Viết câu " + p.getNo() : title + " · Viết câu " + p.getNo());
        s.setSource(attemptId == null ? "Luyện tập" : "Đề thi");
        s.setSubmittedAt(LocalDateTime.now().format(FMT)); s.setStatus(teacher ? "waiting" : "submitted");
        s.setDueDeadline(teacher ? "Đang chờ Giáo viên nhận chấm" : "Đã nộp");
        save(new WritingSubmission(), s);
        return s;
    }
    /** Chỉ đổi trạng thái, tuyệt đối không nhận lại nội dung bài sau khi người học đã xem đáp án. */
    public void sendToTeacher(String id, UserDTO user) {
        if (!user.isVip()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        WritingSubmission e = locked(id); WritingSubmissionDTO s = decode(e);
        if (!Objects.equals(e.getLearnerId(), user.getId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!"submitted".equals(s.getStatus())) throw new IllegalArgumentException("Bài đã được gửi chấm.");
        // Bị GV từ chối thì phải làm lại (tạo bài mới) mới được gửi; không gửi lại chính bản đã bị từ chối.
        if (isRejectedExamSubmission(s) || (s.getRejectReason() != null && !s.getRejectReason().isBlank()))
            throw new IllegalArgumentException("Giáo viên đã từ chối chấm bài này. Hãy làm lại để gửi lại Giáo viên chấm.");
        if (!hasAnswer(s.getQuestionNo(), s.getStudentAnswer()))
            throw new IllegalArgumentException("Bài chưa có nội dung nên không thể gửi Giáo viên chấm.");
        s.setStatus("waiting"); s.setDueDeadline("Đang chờ Giáo viên nhận chấm"); s.setRejectReason(null); save(e,s);
    }
    public static boolean hasAnswer(int no, String answer) {
        String content = Objects.toString(answer, "");
        if (no <= 52) content = content.replace("㉠", "").replace("㉡", "");
        return !content.isBlank();
    }
    public boolean claimSubmission(String id, UserDTO teacher) {
        requireTeacher(teacher);
        WritingSubmission e = locked(id); WritingSubmissionDTO s = decode(e);
        if (!("waiting".equals(s.getStatus()) || "appeal".equals(s.getStatus()))) return false;
        if ("appeal".equals(s.getStatus()) && !Objects.equals(e.getMentorId(), teacher.getId())) return false;
        s.setStatus("claimed"); s.setMentorId(teacher.getId()); s.setMentorName(teacher.getFullName());
        s.setDueDeadline("Đang chấm"); save(e,s); return true;
    }
    public boolean rejectSubmission(String id, UserDTO teacher, String reason) {
        requireTeacher(teacher);
        WritingSubmission e = locked(id); WritingSubmissionDTO s = decode(e);
        if (!"waiting".equalsIgnoreCase(s.getStatus())) return false;
        String rejectMsg = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Giao vien tu choi yeu cau cham bai.";
        s.setStatus("submitted"); s.setMentorId(null); s.setMentorName(null);
        s.setDueDeadline("Giao vien tu choi cham bai"); s.setRejectReason(rejectMsg); save(e, s);
        notify(s, "Yeu cau cham bai bi tu choi", "Giao vien tu choi cham bai cua ban. Ly do: " + rejectMsg);
        return true;
    }
    public boolean gradeSubmission(String id, UserDTO teacher, Map<String,Integer> criteria, Integer total,
                                   String feedback, String model, String decision, String message) {
        requireTeacher(teacher);
        WritingSubmission e = locked(id); WritingSubmissionDTO s = decode(e);
        if (!Objects.equals(e.getMentorId(), teacher.getId()) || !("claimed".equals(s.getStatus()) || "appeal".equals(s.getStatus()))) return false;
        boolean appeal = s.isAppealUsed() && !s.isAppealResolved();
        if (appeal && !Set.of("revise", "uphold").contains(Objects.toString(decision, "")))
            throw new IllegalArgumentException("Chọn sửa kết quả hoặc giữ nguyên kết quả khiếu nại.");
        if (message != null && message.length() > 2000) throw new IllegalArgumentException("Lời nhắn tối đa 2000 ký tự.");
        if (appeal && "uphold".equals(decision)) {
            if (message == null || message.isBlank()) throw new IllegalArgumentException("Vui lòng giải thích lý do giữ nguyên kết quả.");
        } else {
            if (feedback == null || feedback.isBlank() || feedback.length() > 6000) throw new IllegalArgumentException("Nhận xét bắt buộc, tối đa 6000 ký tự.");
            int score = 0;
            if (s.getQuestionNo() >= 53) {
                if (total == null || total < 0 || total > s.getMaxScore()) throw new IllegalArgumentException("Điểm phải từ 0 đến " + s.getMaxScore() + ".");
                score = total;
            } else {
                for (CriterionScore c : s.getCriteriaScores()) {
                    Integer value = criteria.get(c.getName());
                    if (value == null || value < 0 || value > c.getMax()) throw new IllegalArgumentException("Điểm mỗi chỗ trống phải từ 0 đến " + c.getMax());
                    c.setScore(value); score += value;
                }
            }
            s.setTotalScore(score); s.setOverallFeedback(feedback.trim());
            if (model != null && !model.isBlank()) s.setModelAnswer(model.trim());
            s.setGradedAt(LocalDateTime.now().format(FMT));
        }
        if (appeal) { s.setAppealResolved(true); s.setAppealDecision(decision); s.setAppealMessage(message); }
        s.setStatus("graded"); s.setDueDeadline("Đã chấm"); save(e,s);
        notify(s, appeal ? "Đã xử lý khiếu nại điểm bài Viết" : "Giáo viên đã chấm bài Viết", appeal
            ? "Kết quả: " + s.getOriginalScore() + " → " + s.getTotalScore() + "/" + s.getMaxScore() + ". Xem nhận xét và lời nhắn của Giáo viên."
            : "Bài viết câu " + s.getQuestionNo() + ": " + s.getTotalScore() + "/" + s.getMaxScore() + ".");
        return true;
    }
    public boolean appealSubmission(String id, Long learnerId, String reason) {
        WritingSubmission e = locked(id); WritingSubmissionDTO s = decode(e);
        if (!Objects.equals(e.getLearnerId(), learnerId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!"graded".equals(s.getStatus()) || s.isAppealUsed()) return false;
        if (reason == null || reason.trim().length() < 10 || reason.length() > 2000) throw new IllegalArgumentException("Lý do cần từ 10 đến 2000 ký tự.");
        s.setAppealUsed(true); s.setOriginalScore(s.getTotalScore()); s.setOriginalFeedback(s.getOverallFeedback());
        s.setOriginalModelAnswer(s.getModelAnswer()); s.setOriginalGradedAt(s.getGradedAt());
        s.setAppealReason(reason.trim()); s.setStatus("appeal"); save(e,s); return true;
    }
    public boolean askQuestion(String id, Long userId, String question) {
        WritingSubmission e=locked(id); WritingSubmissionDTO s=decode(e);
        if (!Objects.equals(e.getLearnerId(),userId) || s.getTotalScore()==null) return false;
        if(question==null || question.trim().length()<5 || question.length()>2000) return false;
        s.setStudentQuestion(question.trim());save(e,s);return true;
    }
    /** Luu nhap bai viet (chua nop). Moi user+prompt chi giu 1 nhap moi nhat. */
    @Transactional
    public WritingSubmissionDTO saveDraft(UserDTO user, Long promptId, String answer) {
        if (user == null || user.getId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        WritingPrompt p = prompts.findById(promptId).filter(w -> w.getStatus() == QuestionStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("De viet khong con kha dung."));
        Optional<WritingSubmission> existing = submissions.findByLearnerId(user.getId()).stream()
                .filter(e -> Objects.equals(e.getPromptId(), promptId))
                .filter(e -> {
                    try { return "draft".equals(json.readValue(e.getPayload(), WritingSubmissionDTO.class).getStatus()); }
                    catch (Exception ex) { return false; }
                }).findFirst();
        WritingSubmission entity = existing.orElse(new WritingSubmission());
        WritingSubmissionDTO s;
        if (existing.isPresent()) {
            s = decode(entity);
        } else {
            s = new WritingSubmissionDTO();
            s.setLearnerId(user.getId()); s.setLearnerName(user.getFullName()); s.setLearnerEmail(user.getEmail());
            s.setPromptId(p.getId()); s.setQuestionNo(p.getNo()); s.setPrompt(p.getPromptKo());
            s.setMaxScore(p.getNo() == 54 ? 50 : p.getNo() == 53 ? 30 : 10);
            s.setTitle("Nhap · Viet cau " + p.getNo()); s.setSource("Luyen tap");
        }
        s.setStudentAnswer(Objects.toString(answer, "").trim());
        s.setWordCount(s.getStudentAnswer().length());
        s.setStatus("draft");
        s.setSubmittedAt(LocalDateTime.now().format(FMT));
        s.setDueDeadline("Chua nop");
        save(entity, s);
        return s;
    }

    /** Lay nhap moi nhat cua user cho cau nay (null neu khong co). */
    @Transactional(readOnly = true)
    public WritingSubmissionDTO getDraft(Long userId, int questionNo) {
        if (userId == null) return null;
        return submissions.findByLearnerId(userId).stream()
                .filter(e -> {
                    try {
                        WritingSubmissionDTO s = json.readValue(e.getPayload(), WritingSubmissionDTO.class);
                        return "draft".equals(s.getStatus()) && s.getQuestionNo() == questionNo;
                    } catch (Exception ex) { return false; }
                })
                .max(Comparator.comparingLong(WritingSubmission::getId))
                .map(this::decode).orElse(null);
    }
    public boolean rateSubmission(String id, Long userId, int rating) {
        WritingSubmission e=locked(id); WritingSubmissionDTO s=decode(e);
        if(!Objects.equals(e.getLearnerId(),userId) || s.getTotalScore()==null || rating<1 || rating>5) return false;
        s.setRating(rating);save(e,s);return true;
    }
    public boolean replyQuestion(String id, UserDTO teacher, String reply) {
        requireTeacher(teacher);WritingSubmission e=locked(id);WritingSubmissionDTO s=decode(e);
        if(!Objects.equals(e.getMentorId(),teacher.getId()) || reply==null || reply.isBlank() || reply.length()>2000) return false;
        s.setTeacherReply(reply.trim());save(e,s);return true;
    }
    private void requireTeacher(UserDTO t) {
        if(t==null || (!t.isMentor() && !t.isAdmin())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    private static Long submissionKey(String id) {
        try { return id == null ? null : Long.valueOf(id); }
        catch (NumberFormatException ex) { return null; }
    }
    private WritingSubmission locked(String id) {
        Long key = submissionKey(id);
        if (key == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return submissions.findForUpdate(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    private WritingSubmissionDTO decode(WritingSubmission e) {
        try { return json.readValue(e.getPayload(), WritingSubmissionDTO.class); }
        catch(Exception ex) { throw new IllegalStateException("Không đọc được bài viết đã lưu.",ex); }
    }
    private void save(WritingSubmission e, WritingSubmissionDTO s) {
        e.setUserId(s.getLearnerId()); e.setLearnerId(s.getLearnerId()); e.setPromptId(s.getPromptId());
        e.setMode(s.getExamAttemptId() == null ? "PRACTICE" : "EXAM");
        e.setMentorId(s.getMentorId());
        // CHECK của bảng SQL Server dùng các trạng thái này; payload giữ trạng thái chi tiết cho UI và khiếu nại.
        e.setStatus(switch (s.getStatus()) {
            case "draft" -> "DRAFT";
            case "submitted" -> "DRAFT";
            case "waiting", "appeal" -> "WAITING_MENTOR";
            case "claimed" -> "GRADING";
            case "graded" -> "GRADED";
            default -> throw new IllegalStateException("Trạng thái bài viết không hợp lệ: " + s.getStatus());
        });
        try { e.setPayload(json.writeValueAsString(s)); } catch(Exception ex) { throw new IllegalStateException(ex); }
        e = submissions.save(e);
        s.setId(e.getId().toString());
        s.setChartImage(s.getChartFilename() == null || s.getSourceId() == null ? null : "/writing/submission/" + s.getId() + "/chart");
        try { e.setPayload(json.writeValueAsString(s)); } catch(Exception ex) { throw new IllegalStateException(ex); }
    }
    private void notify(WritingSubmissionDTO s,String title,String message) {
        UserNotification n=new UserNotification(); n.setUserId(s.getLearnerId());n.setTitle(title);n.setMessage(message);
        n.setType("WRITING_GRADED");n.setLink("/writing/feedback?id="+s.getId());notifications.save(n);
    }
}

