package com.hottopik.service.exam;

import com.hottopik.dto.ApprovalRequestDTO;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.ExamItemRepository;
import com.hottopik.repository.exam.QuestionRepository;
import com.hottopik.repository.exam.WritingPromptRepository;
import com.hottopik.service.AdminSystemService;
import com.hottopik.service.ApprovalDecidedEvent;
import com.hottopik.service.ApprovalService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** Ngân hàng câu hỏi (BRD 10.2). Không xoá cứng: chỉ Ẩn / Hiện lại. */
@Service
public class QuestionBankService {

    public static final int PAGE_SIZE = 12;
    public static final String APPROVAL_TARGET = "QUESTION_BULK";
    public static final String REQ_BULK_HIDE = "BULK_HIDE_QUESTIONS";
    static final int BULK_HIDE_APPROVAL_OVER = 20;

    public record Filter(Track track, Skill skill, String typeCode, Integer zone, Long sourceId,
                         QuestionPurpose purpose, String difficulty, QuestionStatus status, String q) {
    }

    public record Usage(Long examId, String examName, String status) {
    }

    public record BulkResult(int applied, Long approvalRequestId) {
    }

    private final QuestionRepository questions;
    private final WritingPromptRepository writingPrompts;
    private final ExamItemRepository examItems;
    private final ApprovalService approvals;
    private final AdminSystemService audit;

    public QuestionBankService(QuestionRepository questions, WritingPromptRepository writingPrompts,
                               ExamItemRepository examItems, ApprovalService approvals, AdminSystemService audit) {
        this.questions = questions;
        this.writingPrompts = writingPrompts;
        this.examItems = examItems;
        this.approvals = approvals;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<Question> search(Filter f, int page) {
        Page<Question> result = questions.findAll(spec(f),
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("id").ascending()));
        result.forEach(q -> {
            if (q.getSource() != null) q.getSource().getName();
            q.getGroup().getId();
        });
        return result;
    }

    static Specification<Question> spec(Filter f) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.track() != null) ps.add(cb.equal(root.get("track"), f.track()));
            if (f.skill() != null) ps.add(cb.equal(root.get("skill"), f.skill()));
            if (f.typeCode() != null && !f.typeCode().isBlank()) {
                String code = f.typeCode().trim().toUpperCase(Locale.ROOT);
                if (code.startsWith("TPL")) {
                    List<String> official = QuestionTemplateCatalog.typeCodes(code);
                    List<Predicate> either = new ArrayList<>();
                    either.add(cb.equal(root.get("templateCode"), code));
                    if (!official.isEmpty()) either.add(root.get("typeCode").in(official));
                    ps.add(cb.or(either.toArray(Predicate[]::new)));
                } else {
                    ps.add(cb.equal(root.get("typeCode"), code));
                }
            }
            if (f.sourceId() != null) ps.add(cb.equal(root.get("source").get("id"), f.sourceId()));
            if (f.purpose() != null) ps.add(cb.equal(root.get("purpose"), f.purpose()));
            if (f.status() != null) ps.add(cb.equal(root.get("status"), f.status()));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("promptKo")), like),
                        cb.like(cb.lower(root.get("option1")), like),
                        cb.like(cb.lower(root.get("option2")), like),
                        cb.like(cb.lower(root.get("option3")), like),
                        cb.like(cb.lower(root.get("option4")), like),
                        cb.like(cb.lower(root.get("explanationVi")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        Question q = get(id);
        QuestionGroup g = q.getGroup();
        String mold = displayTemplate(q.getTemplateCode(), q.getTypeCode());
        String moldName = templateName(mold);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", q.getId());
        m.put("code", q.getCode());
        m.put("track", q.getTrack().getLabel());
        m.put("skill", q.getSkill().getLabel());
        m.put("typeCode", mold);
        m.put("typeName", moldName);
        m.put("originalNo", q.getOriginalNo());
        m.put("source", q.getSource() != null ? q.getSource().getName() : null);
        m.put("sourceId", q.getSource() != null ? q.getSource().getId() : null);
        m.put("purpose", q.getPurpose().name());
        m.put("status", q.getStatus().name());
        m.put("hiddenReason", q.getHiddenReason());
        m.put("promptKo", q.getPromptKo());
        m.put("options", q.options());
        m.put("answer", q.getAnswerIndex());
        m.put("explanationVi", q.getExplanationVi());
        m.put("causeHint", q.getCauseHint());
        m.put("correctRate", q.correctRate());
        m.put("attempts", q.getStatsAttempts());
        m.put("groupSize", g.getSize());
        m.put("groupCodes", g.getQuestions().stream().map(Question::getCode).toList());
        m.put("scriptKo", g.getScriptKo());
        m.put("passageKo", g.getPassageKo());
        m.put("audioFile", g.getAudioFile());
        m.put("imageFile", g.getImageFile());
        m.put("usages", usages(id));
        return m;
    }

    public List<Usage> usages(Long questionId) {
        return examItems.findExamUsages(questionId).stream()
                .map(r -> new Usage((Long) r[0], (String) r[1], String.valueOf(r[2]))).distinct().toList();
    }

    public Question get(Long id) {
        return questions.findById(id).orElseThrow(() -> new ExamDomainException("Không tìm thấy câu hỏi #" + id + "."));
    }

    @Transactional
    public void update(Long id, String prompt, List<String> options, Integer answer, String explanation, String reason, Actor actor) {
        requireContent(actor);
        Question q = get(id);
        if (prompt == null || prompt.isBlank()) throw new ExamDomainException("Câu hỏi không được để trống.");
        if (options == null || options.size() != 4 || options.stream().anyMatch(o -> o == null || o.isBlank())) {
            throw new ExamDomainException("Cần đủ 4 phương án.");
        }
        if (options.stream().map(ExamImportValidator::normalize).distinct().count() < 4) {
            throw new ExamDomainException("Các phương án không được trùng nhau.");
        }
        if (answer == null || answer < 1 || answer > 4) throw new ExamDomainException("Đáp án phải là 1–4.");
        boolean answerChanged = answer != q.getAnswerIndex();
        if (answerChanged && (reason == null || reason.trim().length() < 5)) {
            throw new ExamDomainException("Đổi đáp án cần nhập lý do (ít nhất 5 ký tự).");
        }
        q.setPromptKo(prompt.trim());
        q.setOption1(options.get(0).trim());
        q.setOption2(options.get(1).trim());
        q.setOption3(options.get(2).trim());
        q.setOption4(options.get(3).trim());
        q.setAnswerIndex(answer);
        q.setExplanationVi(explanation == null || explanation.isBlank() ? null : explanation.trim());
        q.setContentHash(ExamImportValidator.contentHash(q.getPromptKo(), q.getGroup().getPassageKo(),
                q.getGroup().getScriptKo(), q.options().toArray(String[]::new)));
        log(actor, "UPDATE_QUESTION", "Sửa câu " + q.getCode() + (answerChanged ? " (đổi đáp án → " + answer + ": " + reason.trim() + ")" : ""));
    }

    @Transactional
    public void setHidden(Long id, boolean hidden, String reason, Actor actor) {
        requireContent(actor);
        Question q = get(id);
        if (hidden) {
            requireReason(reason);
            q.setStatus(QuestionStatus.HIDDEN);
            q.setHiddenReason(reason.trim());
        } else {
            q.setStatus(QuestionStatus.ACTIVE);
            q.setHiddenReason(null);
        }
        log(actor, hidden ? "HIDE_QUESTION" : "SHOW_QUESTION", (hidden ? "Ẩn câu " : "Hiện lại câu ") + q.getCode()
                + (hidden ? " — " + reason.trim() : ""));
    }

    @Transactional
    public void setPurpose(Long id, QuestionPurpose purpose, Actor actor) {
        requireContent(actor);
        Question q = get(id);
        if (q.getPurpose() == QuestionPurpose.ASSESSMENT || purpose == QuestionPurpose.ASSESSMENT) {
            throw new ExamDomainException("Câu dành cho kiểm tra trình độ không chuyển mục đích được.");
        }
        q.setPurpose(purpose);
        log(actor, "SET_QUESTION_PURPOSE", "Đổi mục đích câu " + q.getCode() + " → " + (purpose == QuestionPurpose.EXAM ? "Thi" : "Luyện"));
    }

    @Transactional
    public BulkResult bulk(List<Long> ids, String action, String reason, Actor actor) {
        requireContent(actor);
        if (ids == null || ids.isEmpty()) throw new ExamDomainException("Chưa chọn câu nào.");
        List<Question> list = questions.findAllById(ids);
        switch (action) {
            case "hide" -> {
                requireReason(reason);
                if (list.size() > BULK_HIDE_APPROVAL_OVER) {
                    String payload = list.stream().map(q -> String.valueOf(q.getId())).collect(Collectors.joining(","));
                    ApprovalRequestDTO req;
                    try {
                        req = approvals.submitTargetedRequest(actor.name(), "Content Manager", REQ_BULK_HIDE,
                                "Ẩn hàng loạt " + list.size() + " câu hỏi", "Lý do: " + reason.trim(),
                                APPROVAL_TARGET, System.currentTimeMillis(), payload + "|" + reason.trim());
                    } catch (IllegalStateException e) {
                        throw new ExamDomainException(e.getMessage());
                    }
                    log(actor, "SUBMIT_BULK_HIDE", "Gửi duyệt ẩn " + list.size() + " câu (yêu cầu #" + req.getId() + ")");
                    return new BulkResult(0, req.getId());
                }
                list.forEach(q -> {
                    q.setStatus(QuestionStatus.HIDDEN);
                    q.setHiddenReason(reason.trim());
                });
            }
            case "show" -> list.forEach(q -> {
                q.setStatus(QuestionStatus.ACTIVE);
                q.setHiddenReason(null);
            });
            case "practice", "exam" -> {
                QuestionPurpose p = action.equals("practice") ? QuestionPurpose.PRACTICE : QuestionPurpose.EXAM;
                list = list.stream().filter(q -> q.getPurpose() != QuestionPurpose.ASSESSMENT).toList();
                list.forEach(q -> q.setPurpose(p));
            }
            default -> throw new ExamDomainException("Thao tác không hợp lệ.");
        }
        log(actor, "BULK_" + action.toUpperCase(Locale.ROOT) + "_QUESTIONS", "Thao tác hàng loạt \"" + action + "\" cho "
                + list.size() + " câu" + (reason != null && !reason.isBlank() ? " — " + reason.trim() : ""));
        return new BulkResult(list.size(), null);
    }

    @EventListener
    @Transactional
    public void onApprovalDecided(ApprovalDecidedEvent ev) {
        ApprovalRequestDTO req = ev.request();
        if (!APPROVAL_TARGET.equals(req.getTargetType()) || !ev.approved() || req.getPayload() == null) return;
        String[] parts = req.getPayload().split("\\|", 2);
        List<Long> ids = Arrays.stream(parts[0].split(",")).filter(s -> !s.isBlank()).map(Long::valueOf).toList();
        String reason = parts.length > 1 ? parts[1] : "Duyệt ẩn hàng loạt";
        List<Question> list = questions.findAllById(ids);
        list.forEach(q -> {
            q.setStatus(QuestionStatus.HIDDEN);
            q.setHiddenReason(reason);
        });
        audit.logAction(ev.decidedBy(), Actor.ROLE_ADMIN, "BULK_HIDE_QUESTIONS", "QUESTION",
                "Duyệt và ẩn " + list.size() + " câu — " + reason, "127.0.0.1");
    }

    public Map<String, Long> counts() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("total", questions.count());
        m.put("active", questions.countByStatus(QuestionStatus.ACTIVE));
        m.put("hidden", questions.countByStatus(QuestionStatus.HIDDEN));
        return m;
    }

    public Map<String, Long> activeByType() {
        Map<String, Long> m = new HashMap<>();
        for (Object[] r : questions.countActiveByType()) m.put((String) r[0], (Long) r[1]);
        return m;
    }

    /** Số câu đang dùng theo mã dạng TPL. Câu Excel cũ không có template_code thì quy về dạng đã chốt. */
    public Map<String, Long> activeByTemplate() {
        Map<String, Long> m = new HashMap<>();
        for (Object[] row : questions.activeTemplateCodes()) {
            String code = displayTemplate(row[0] == null ? null : row[0].toString(), row[1] == null ? null : row[1].toString());
            if (code != null) m.merge(code, 1L, Long::sum);
        }
        for (WritingPrompt prompt : writingPrompts.findAllByOrderByNoAscIdDesc()) {
            if (prompt.getStatus() != QuestionStatus.ACTIVE) continue;
            String code = writingTemplate(prompt.getNo());
            if (code != null) m.merge(code, 1L, Long::sum);
        }
        return m;
    }

    public List<WritingPrompt> writings(Filter f) {
        if (f.skill() != null && f.skill() != Skill.WRITING) return List.of();
        String needle = f.q() == null ? "" : f.q().trim().toLowerCase(Locale.ROOT);
        return writingPrompts.findAllByOrderByNoAscIdDesc().stream()
                .filter(p -> f.track() == null || (p.getSource() != null && p.getSource().getTrack() == f.track()))
                .filter(p -> f.sourceId() == null || (p.getSource() != null && f.sourceId().equals(p.getSource().getId())))
                .filter(p -> f.status() == null || p.getStatus() == f.status())
                .filter(p -> f.purpose() == null || f.purpose() == QuestionPurpose.EXAM)
                .filter(p -> matchesWritingTemplate(p, f.typeCode()))
                .filter(p -> needle.isBlank()
                        || (p.getCode() != null && p.getCode().toLowerCase(Locale.ROOT).contains(needle))
                        || (p.getPromptKo() != null && p.getPromptKo().toLowerCase(Locale.ROOT).contains(needle)))
                .toList();
    }

    public static String displayTemplate(String templateCode, String typeCode) {
        if (templateCode != null && !templateCode.isBlank()) return templateCode;
        String mapped = typeCode == null ? null : QuestionTemplateCatalog.templateCode(typeCode);
        return mapped != null ? mapped : typeCode;
    }

    public static String templateName(String code) {
        if (code == null) return "";
        return QuestionTemplateCatalog.all().stream()
                .filter(s -> s.code().equals(code)).map(QuestionTemplateCatalog.Seed::name).findFirst().orElse("");
    }

    public static String writingTemplate(int no) {
        return QuestionTypeCatalog.resolve(Track.TOPIK_II, Skill.WRITING, no)
                .map(t -> QuestionTemplateCatalog.templateCode(t.code())).orElse(null);
    }

    private static boolean matchesWritingTemplate(WritingPrompt prompt, String typeCode) {
        if (typeCode == null || typeCode.isBlank()) return true;
        String code = typeCode.trim().toUpperCase(Locale.ROOT);
        String mold = writingTemplate(prompt.getNo());
        if (code.startsWith("TPL")) return code.equals(mold);
        String mapped = QuestionTemplateCatalog.templateCode(code);
        return code.equals(mold) || (mapped != null && mapped.equals(mold));
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.trim().length() < 5) throw new ExamDomainException("Nhập lý do (ít nhất 5 ký tự).");
    }

    private static void requireContent(Actor actor) {
        if (actor == null || !actor.isContentManager()) {
            throw new ExamDomainException("Chỉ Content Manager được sửa ngân hàng câu hỏi (Admin chỉ xem).");
        }
    }

    private void log(Actor actor, String action, String details) {
        audit.logAction(actor.name(), actor.role(), action, "QUESTION", details, "127.0.0.1");
    }
}
