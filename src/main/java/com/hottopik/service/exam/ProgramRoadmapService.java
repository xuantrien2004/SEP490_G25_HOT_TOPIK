package com.hottopik.service.exam;

import com.hottopik.dto.ApprovalRequestDTO;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.ProgramVersionItemRepository;
import com.hottopik.repository.exam.ProgramVersionRepository;
import com.hottopik.repository.exam.QuestionTemplateRepository;
import com.hottopik.service.AdminSystemService;
import com.hottopik.service.ApprovalDecidedEvent;
import com.hottopik.service.ApprovalService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** Bản lộ trình Content Manager phát hành: thứ tự dạng Nghe và Đọc, mốc ôn, ba chặng. */
@Service
public class ProgramRoadmapService {

    public static final String APPROVAL_TARGET = "PROGRAM";
    public static final String REQ_PUBLISH = "PUBLISH_PROGRAM";

    private final ProgramVersionRepository programs;
    private final ProgramVersionItemRepository items;
    private final QuestionTemplateRepository templates;
    private final ApprovalService approvals;
    private final AdminSystemService audit;

    public ProgramRoadmapService(ProgramVersionRepository programs, ProgramVersionItemRepository items,
                                 QuestionTemplateRepository templates, ApprovalService approvals,
                                 AdminSystemService audit) {
        this.programs = programs;
        this.items = items;
        this.templates = templates;
        this.approvals = approvals;
        this.audit = audit;
    }

    public List<ProgramVersion> list() {
        return programs.findAllByOrderByIdDesc();
    }

    public ProgramVersion published() {
        return programs.findFirstByStatusOrderByIdDesc(ProgramStatus.PUBLISHED).orElse(null);
    }

    public ProgramVersion openCopy() {
        return programs.findFirstByStatusInOrderByIdDesc(List.of(ProgramStatus.DRAFT, ProgramStatus.PENDING_APPROVAL))
                .orElse(null);
    }

    public Map<Long, Long> itemCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : items.countByProgram()) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    public ProgramVersion get(Long id) {
        return programs.findById(id).orElseThrow(() -> new ExamDomainException("Không tìm thấy bản lộ trình."));
    }

    public List<ProgramVersionItem> itemsOf(Long programId) {
        return items.findWithTemplate(programId);
    }

    public List<QuestionTemplate> addable(Track track, List<ProgramVersionItem> current) {
        Set<Long> used = new HashSet<>();
        for (ProgramVersionItem item : current) {
            if (item.getTemplate().getTrack() == track) used.add(item.getTemplate().getId());
        }
        Map<String, Integer> rank = catalogRank();
        return templates.findAll().stream()
                .filter(t -> t.getTrack() == track && t.getSkill() != Skill.WRITING && t.isAllowRoadmap())
                .filter(t -> t.getStatus() == TemplateStatus.ACTIVE)
                .filter(t -> !used.contains(t.getId()))
                .sorted(Comparator.comparingInt(t -> rank.getOrDefault(t.getCode(), 1000)))
                .toList();
    }

    public RoadmapSketch.Result preview(ProgramVersion program, Track track, int days, int perDay) {
        List<RoadmapSketch.TypeRef> types = itemsOf(program.getId()).stream()
                .filter(i -> i.getTemplate().getTrack() == track)
                .map(i -> new RoadmapSketch.TypeRef(i.getTemplate().getCode(), i.getTemplate().getName()))
                .toList();
        if (types.isEmpty()) {
            throw new ExamDomainException("Bản lộ trình " + program.getVersionCode() + " chưa có dạng câu "
                    + track.getLabel() + ". Vui lòng liên hệ quản lý nội dung để kiểm tra bản đã phát hành.");
        }
        return RoadmapSketch.build(types, days, perDay, ProgramRules.reviews(program.getReviewOffsets()),
                program.getSessionsPerType(), ProgramRules.phases(program.getPhaseSplit()));
    }

    @Transactional
    public ProgramVersion createDraft(Actor actor) {
        requireContent(actor);
        if (programs.existsByStatusIn(List.of(ProgramStatus.DRAFT, ProgramStatus.PENDING_APPROVAL))) {
            throw new ExamDomainException("Đã có một bản nháp hoặc bản đang chờ duyệt. Hãy sửa bản đó trước.");
        }
        ProgramVersion src = programs.findFirstByStatusOrderByIdDesc(ProgramStatus.PUBLISHED)
                .orElseThrow(() -> new ExamDomainException("Chưa có bản đang phát hành để nhân ra bản nháp."));
        ProgramVersion copy = copyOf(src, actor.userId());
        for (ProgramVersionItem item : itemsOf(src.getId())) {
            ProgramVersionItem row = new ProgramVersionItem();
            row.setProgram(copy);
            row.setTemplate(item.getTemplate());
            row.setOrderNo(item.getOrderNo());
            items.save(row);
        }
        log(actor, "CREATE_PROGRAM_DRAFT", "Tạo bản nháp " + copy.getVersionCode() + " từ " + src.getVersionCode());
        return copy;
    }

    @Transactional
    public void saveSettings(Long id, String note, String reviewOffsets, String phaseSplit,
                             int sessions, int questions, int weekly, Actor actor) {
        ProgramVersion program = editable(id, actor);
        int[] reviews = ProgramRules.reviews(reviewOffsets);
        int[] phases = ProgramRules.phases(phaseSplit);
        ProgramRules.checkCounts(sessions, questions, weekly, reviews);
        program.setNote(blankToNull(note));
        program.setReviewOffsets(ProgramRules.join(reviews));
        program.setPhaseSplit(ProgramRules.join(phases));
        program.setSessionsPerType(sessions);
        program.setQuestionsPerSession(questions);
        program.setWeeklyReviewQuestions(weekly);
        log(actor, "UPDATE_PROGRAM", program.getVersionCode() + ": mốc ôn " + program.getReviewOffsets()
                + ", chặng " + program.getPhaseSplit() + ", " + sessions + " lần/dạng, " + questions + " câu/bài");
    }

    @Transactional
    public void move(Long programId, Long itemId, int delta, Actor actor) {
        editable(programId, actor);
        if (delta != -1 && delta != 1) return;
        List<ProgramVersionItem> all = itemsOf(programId);
        ProgramVersionItem item = findItem(all, itemId);
        Track track = item.getTemplate().getTrack();
        List<ProgramVersionItem> column = all.stream()
                .filter(i -> i.getTemplate().getTrack() == track)
                .sorted(Comparator.comparingInt(ProgramVersionItem::getOrderNo))
                .toList();
        int index = column.indexOf(item);
        int target = index + delta;
        if (index < 0 || target < 0 || target >= column.size()) return;
        ProgramVersionItem neighbor = column.get(target);
        int order = item.getOrderNo();
        item.setOrderNo(neighbor.getOrderNo());
        neighbor.setOrderNo(order);
        log(actor, "MOVE_PROGRAM_ITEM", item.getTemplate().getCode() + (delta < 0 ? " lên" : " xuống"));
    }

    @Transactional
    public void remove(Long programId, Long itemId, Actor actor) {
        editable(programId, actor);
        ProgramVersionItem item = findItem(itemsOf(programId), itemId);
        String code = item.getTemplate().getCode();
        items.delete(item);
        log(actor, "REMOVE_PROGRAM_ITEM", "Gỡ " + code + " khỏi lộ trình");
    }

    @Transactional
    public void add(Long programId, Long templateId, Actor actor) {
        ProgramVersion program = editable(programId, actor);
        QuestionTemplate template = templates.findById(templateId)
                .orElseThrow(() -> new ExamDomainException("Không tìm thấy dạng câu."));
        if (template.getSkill() == Skill.WRITING || !template.isAllowRoadmap()) {
            throw new ExamDomainException("Dạng Viết không xếp vào lịch học theo ngày.");
        }
        if (template.getStatus() != TemplateStatus.ACTIVE) {
            throw new ExamDomainException("Chỉ thêm được dạng đang dùng.");
        }
        List<ProgramVersionItem> current = itemsOf(programId);
        boolean exists = current.stream().anyMatch(i -> i.getTemplate().getId().equals(templateId));
        if (exists) throw new ExamDomainException("Dạng này đã có trong lộ trình.");
        int next = current.stream().mapToInt(ProgramVersionItem::getOrderNo).max().orElse(0) + 1;
        ProgramVersionItem row = new ProgramVersionItem();
        row.setProgram(program);
        row.setTemplate(template);
        row.setOrderNo(next);
        items.save(row);
        log(actor, "ADD_PROGRAM_ITEM", "Thêm " + template.getCode() + " vào " + program.getVersionCode());
    }

    @Transactional
    public void submitForPublish(Long id, Actor actor) {
        ProgramVersion program = editable(id, actor);
        List<ProgramVersionItem> rows = itemsOf(id);
        if (rows.isEmpty()) throw new ExamDomainException("Lộ trình chưa có dạng nào.");
        for (Track track : Track.values()) {
            boolean any = rows.stream().anyMatch(i -> i.getTemplate().getTrack() == track && i.getTemplate().getSkill() != Skill.WRITING);
            if (!any) throw new ExamDomainException(track.getLabel() + " chưa có dạng nào trong lộ trình.");
        }
        ProgramRules.checkCounts(program.getSessionsPerType(), program.getQuestionsPerSession(),
                program.getWeeklyReviewQuestions(), ProgramRules.reviews(program.getReviewOffsets()));
        ProgramRules.phases(program.getPhaseSplit());
        ApprovalRequestDTO req;
        try {
            req = approvals.submitTargetedRequest(actor.name(), "Content Manager", REQ_PUBLISH,
                    "Phát hành lộ trình " + program.getVersionCode(),
                    rows.size() + " dạng Nghe và Đọc. Ôn sau " + program.getReviewOffsets()
                            + " ngày. Ba chặng " + program.getPhaseSplit() + ".",
                    APPROVAL_TARGET, program.getId(), null);
        } catch (IllegalStateException ex) {
            throw new ExamDomainException(ex.getMessage());
        }
        program.setStatus(ProgramStatus.PENDING_APPROVAL);
        program.setPendingAction("PUBLISH");
        program.setApprovalRequestId(req.getId());
        log(actor, "SUBMIT_PROGRAM_PUBLISH", "Gửi duyệt " + program.getVersionCode() + " (yêu cầu #" + req.getId() + ")");
    }

    @EventListener
    @Transactional
    public void onApprovalDecided(ApprovalDecidedEvent ev) {
        ApprovalRequestDTO req = ev.request();
        if (!APPROVAL_TARGET.equals(req.getTargetType()) || req.getTargetId() == null) return;
        ProgramVersion program = get(req.getTargetId());
        if (program.getStatus() != ProgramStatus.PENDING_APPROVAL) {
            throw new ExamDomainException("Bản " + program.getVersionCode() + " không còn chờ duyệt.");
        }
        Actor admin = new Actor(ev.decidedBy() == null ? "Admin" : ev.decidedBy(), Actor.ROLE_ADMIN);
        program.setPendingAction(null);
        program.setApprovalRequestId(null);
        if (!ev.approved()) {
            program.setStatus(ProgramStatus.DRAFT);
            log(admin, "REJECT_PROGRAM_PUBLISH", "Từ chối phát hành " + program.getVersionCode()
                    + (ev.note() != null ? " — " + ev.note() : ""));
            return;
        }
        for (ProgramVersion other : programs.findByStatus(ProgramStatus.PUBLISHED)) {
            if (!other.getId().equals(program.getId())) other.setStatus(ProgramStatus.ARCHIVED);
        }
        program.setStatus(ProgramStatus.PUBLISHED);
        program.setPublishedAt(LocalDateTime.now());
        log(admin, "PUBLISH_PROGRAM", "Phát hành " + program.getVersionCode());
    }

    private ProgramVersion copyOf(ProgramVersion src, Long creatorId) {
        ProgramVersion copy = new ProgramVersion();
        copy.setVersionCode(nextCode());
        copy.setNote(src.getNote());
        copy.setStatus(ProgramStatus.DRAFT);
        copy.setReviewOffsets(src.getReviewOffsets());
        copy.setWeeklyReviewQuestions(src.getWeeklyReviewQuestions());
        copy.setPhaseSplit(src.getPhaseSplit());
        copy.setSessionsPerType(src.getSessionsPerType());
        copy.setQuestionsPerSession(src.getQuestionsPerSession());
        copy.setCreatedBy(creatorId);
        return programs.save(copy);
    }

    private String nextCode() {
        int max = 0;
        for (ProgramVersion program : programs.findAll()) {
            String code = program.getVersionCode();
            if (code != null && code.matches("v\\d+")) {
                max = Math.max(max, Integer.parseInt(code.substring(1)));
            }
        }
        return "v" + (max + 1);
    }

    private ProgramVersion editable(Long id, Actor actor) {
        requireContent(actor);
        ProgramVersion program = get(id);
        if (program.getStatus() == ProgramStatus.PENDING_APPROVAL) {
            throw new ExamDomainException("Bản này đang chờ duyệt, chưa sửa được.");
        }
        if (program.getStatus() != ProgramStatus.DRAFT) {
            throw new ExamDomainException("Chỉ sửa được bản nháp. Hãy tạo bản nháp từ bản đang phát hành.");
        }
        return program;
    }

    private static ProgramVersionItem findItem(List<ProgramVersionItem> rows, Long itemId) {
        return rows.stream().filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new ExamDomainException("Không tìm thấy dạng trong lộ trình."));
    }

    private static void requireContent(Actor actor) {
        if (actor == null || !actor.isContentManager()) {
            throw new ExamDomainException("Chỉ Content Manager được sửa lộ trình (Admin chỉ xem và duyệt).");
        }
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Map<String, Integer> catalogRank() {
        Map<String, Integer> rank = new HashMap<>();
        int index = 0;
        for (QuestionTemplateCatalog.Seed seed : QuestionTemplateCatalog.all()) {
            rank.put(seed.code(), index++);
        }
        return rank;
    }

    private void log(Actor actor, String action, String details) {
        audit.logAction(actor.name(), actor.role(), action, "PROGRAM", details, "127.0.0.1");
    }
}
