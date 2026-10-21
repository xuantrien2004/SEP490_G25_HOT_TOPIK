package com.hottopik.service.exam;

import com.hottopik.dto.ApprovalRequestDTO;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.ExamRepository;
import com.hottopik.repository.exam.QuestionRepository;
import com.hottopik.repository.exam.WritingPromptRepository;
import com.hottopik.service.AdminSystemService;
import com.hottopik.service.ApprovalDecidedEvent;
import com.hottopik.service.ApprovalService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Thư viện đề, trình soạn khung đề và tự sinh Mini Test khi phát hành (BRD 9). */
@Service
public class ExamComposerService {

    public static final String APPROVAL_TARGET = "EXAM";
    public static final String REQ_PUBLISH = "PUBLISH_EXAM";
    public static final String REQ_ARCHIVE = "ARCHIVE_EXAM";

    // ------------------------------------------------------------------ view models

    public record SlotView(int no, Question question) {
    }

    public record BlockView(List<Integer> numbers, List<SlotView> slots) {
        public int firstNo() {
            return numbers.get(0);
        }

        public boolean isFilled() {
            return slots.stream().allMatch(s -> s.question() != null);
        }

        public boolean isEmpty() {
            return slots.stream().allMatch(s -> s.question() == null);
        }

        public String label() {
            return numbers.size() == 1 ? "Câu " + numbers.get(0) : "Câu " + numbers.get(0) + "–" + numbers.get(numbers.size() - 1);
        }
    }

    public record TypeRow(QuestionTypeDef type, int filled, List<BlockView> blocks, int bankAvailable) {
        public int required() {
            return type.requiredCount();
        }

        public boolean isComplete() {
            return filled == type.requiredCount();
        }
    }

    public record FrameCheck(List<String> missingTypes, List<String> hiddenQuestions, boolean writingRequired,
                             boolean writingOk) {
        public boolean isOk() {
            return missingTypes.isEmpty() && hiddenQuestions.isEmpty() && (!writingRequired || writingOk);
        }
    }

    public record EnteredGroup(String templateCode, String name, String skillLabel, String share, String refs) {
    }

    public record ExamFrame(Exam exam, List<TypeRow> rows, int filled, int required, FrameCheck check,
                            boolean enteredPaper, List<EnteredGroup> entered) {
    }

    public record Candidate(Long groupId, List<String> codes, String sourceName, String preview, String purpose,
                            int size) {
    }

    // ------------------------------------------------------------------

    private final ExamRepository exams;
    private final QuestionRepository questions;
    private final WritingPromptRepository writingPrompts;
    private final ApprovalService approvals;
    private final AdminSystemService audit;
    private final Random random = new Random();

    public ExamComposerService(ExamRepository exams, QuestionRepository questions,
                               WritingPromptRepository writingPrompts, ApprovalService approvals,
                               AdminSystemService audit) {
        this.exams = exams;
        this.questions = questions;
        this.writingPrompts = writingPrompts;
        this.approvals = approvals;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ thư viện đề

    public List<Exam> library(String tab, ExamKind kind, Track track) {
        return exams.findAllForLibrary().stream()
                .filter(e -> kind == null || e.getKind() == kind)
                .filter(e -> track == null || e.getTrack() == track)
                .filter(e -> switch (tab == null ? "PUBLISHED" : tab) {
                    case "DRAFT" -> e.getStatus() == ExamStatus.DRAFT
                            || (e.getStatus() == ExamStatus.PENDING_APPROVAL && "PUBLISH".equals(e.getPendingAction()));
                    case "ARCHIVED" -> e.getStatus() == ExamStatus.ARCHIVED;
                    default -> e.getStatus() == ExamStatus.PUBLISHED
                            || (e.getStatus() == ExamStatus.PENDING_APPROVAL && "ARCHIVE".equals(e.getPendingAction()));
                })
                .sorted(Comparator.comparing(Exam::getTrack).thenComparing(Exam::getKind).thenComparing(Exam::getSeqNo))
                .toList();
    }

    public Map<String, Long> libraryCounts() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String tab : List.of("PUBLISHED", "DRAFT", "ARCHIVED")) m.put(tab, (long) library(tab, null, null).size());
        return m;
    }

    /** Đề học viên được thấy trong Thư viện đề. */
    public List<Exam> publishedFor(Track track, ExamKind kind) {
        return exams.findAllByOrderByIdDesc().stream()
                .filter(e -> e.getStatus() == ExamStatus.PUBLISHED
                        || (e.getStatus() == ExamStatus.PENDING_APPROVAL && "ARCHIVE".equals(e.getPendingAction())))
                .filter(e -> track == null || e.getTrack() == track)
                .filter(e -> kind == null || e.getKind() == kind)
                .sorted(Comparator.comparing(Exam::getSeqNo))
                .toList();
    }

    public Exam get(Long id) {
        return exams.findForComposerById(id).orElseThrow(() -> new ExamDomainException("Không tìm thấy đề #" + id + "."));
    }

    public List<Exam> children(Long id) {
        return exams.findByParentId(id);
    }

    // ------------------------------------------------------------------ tạo đề

    @Transactional
    public Exam createDraft(Track track, Actor actor) {
        requireContent(actor);
        Exam e = newFullMock(track, actor);
        exams.save(e);
        e.setCode(String.format("EX%04d", e.getId()));
        log(actor, "CREATE_EXAM", "Tạo bản nháp " + e.getName());
        return e;
    }

    @Transactional
    public Exam createFullMockFromSource(ExamSource source, List<Question> created, List<WritingPrompt> writing, Actor actor) {
        Exam e = newFullMock(source.getTrack(), actor);
        e.setSource(source);
        exams.save(e);
        e.setCode(String.format("EX%04d", e.getId()));
        for (Question q : created) {
            e.getItems().add(new ExamItem(e, q.getSkill(), q.getOriginalNo(), q.getTypeCode(), q));
        }
        for (WritingPrompt p : writing) setWritingSlot(e, p);
        log(actor, "CREATE_EXAM", "Tạo " + e.getName() + " (nháp) từ nguồn đề \"" + source.getName() + "\" theo đúng thứ tự câu của file");
        return e;
    }

    private Exam newFullMock(Track track, Actor actor) {
        Exam e = new Exam();
        int seq = exams.maxSeqNo(track, ExamKind.FULL) + 1;
        e.setCode(Codes.temporary());
        e.setTrack(track);
        e.setKind(ExamKind.FULL);
        e.setSeqNo(seq);
        e.setName("Full Mock " + track.getLabel() + " · Đề " + seq);
        e.setStatus(ExamStatus.DRAFT);
        for (QuestionTypeCatalog.Section s : QuestionTypeCatalog.sections(track)) {
            switch (s.skill()) {
                case LISTENING -> e.setListeningMinutes(s.minutes());
                case READING -> e.setReadingMinutes(s.minutes());
                case WRITING -> e.setWritingMinutes(s.minutes());
            }
        }
        e.setCreatedBy(actor.name());
        return e;
    }

    @Transactional
    public Exam cloneExam(Long id, Actor actor) {
        requireContent(actor);
        Exam src = get(id);
        if (src.getKind() != ExamKind.FULL) throw new ExamDomainException("Chỉ nhân bản được Full Mock; Mini Test tự sinh khi phát hành.");
        Exam copy = newFullMock(src.getTrack(), actor);
        copy.setSource(src.getSource());
        copy.setListeningMinutes(src.getListeningMinutes());
        copy.setReadingMinutes(src.getReadingMinutes());
        copy.setWritingMinutes(src.getWritingMinutes());
        exams.save(copy);
        copy.setCode(String.format("EX%04d", copy.getId()));
        for (ExamItem it : src.getItems()) {
            copy.getItems().add(new ExamItem(copy, it.getSection(), it.getNo(), it.getTypeCode(), it.getQuestion()));
        }
        copy.setWriting51(src.getWriting51());
        copy.setWriting52(src.getWriting52());
        copy.setWriting53(src.getWriting53());
        copy.setWriting54(src.getWriting54());
        log(actor, "CLONE_EXAM", "Nhân bản " + src.getName() + " → " + copy.getName() + " (nháp)");
        return copy;
    }

    @Transactional
    public void deleteDraft(Long id, Actor actor) {
        requireContent(actor);
        Exam e = editable(id);
        if (e.getPublishedAt() != null) throw new ExamDomainException("Đề đã từng phát hành, chỉ lưu trữ được.");
        exams.delete(e);
        log(actor, "DELETE_EXAM", "Xoá bản nháp " + e.getName());
    }

    // ------------------------------------------------------------------ trình soạn khung đề

    @Transactional(readOnly = true)
    public ExamFrame frame(Long id) {
        Exam e = get(id);
        Map<String, Question> bySlot = new HashMap<>();
        for (ExamItem it : e.getItems()) bySlot.put(it.getSection() + "#" + it.getNo(), it.getQuestion());

        Map<String, Long> available = new HashMap<>();
        for (Object[] row : questions.countActiveByType()) available.put((String) row[0], (Long) row[1]);

        List<TypeRow> rows = new ArrayList<>();
        int filled = 0;
        int required = 0;
        for (QuestionTypeDef t : QuestionTypeCatalog.mcqTypes(e.getTrack())) {
            if (!sectionIncluded(e, t.skill())) continue;
            List<BlockView> blocks = new ArrayList<>();
            int f = 0;
            for (List<Integer> b : t.blocks()) {
                List<SlotView> slots = new ArrayList<>();
                for (int n : b) {
                    Question q = bySlot.get(t.skill() + "#" + n);
                    if (q != null) {
                        q.getCode();
                        f++;
                    }
                    slots.add(new SlotView(n, q));
                }
                blocks.add(new BlockView(b, slots));
            }
            filled += f;
            required += t.requiredCount();
            rows.add(new TypeRow(t, f, blocks, available.getOrDefault(t.code(), 0L).intValue()));
        }
        e.writingPrompts().forEach(WritingPrompt::getCode);
        boolean entered = enteredPaper(e);
        List<EnteredGroup> groups = entered ? enteredGroups(e) : List.of();
        FrameCheck verdict = entered ? checkEntered(e) : check(e, rows);
        int shownFilled = entered ? e.getItems().size() : filled;
        int shownRequired = entered ? e.getItems().size() : required;
        return new ExamFrame(e, rows, shownFilled, shownRequired, verdict, entered, groups);
    }

    /** Đề dựng từ tờ nhập tay: mã dạng là TPL, không bắt đủ 70 hoặc 104 ô chuẩn. */
    private static boolean enteredPaper(Exam e) {
        boolean templated = e.getItems().stream().anyMatch(i -> {
            String code = i.getQuestion().getTemplateCode();
            return code != null && !code.isBlank();
        });
        if (templated) return true;
        return e.getItems().isEmpty() && !e.writingPrompts().isEmpty() && e.getSource() != null;
    }

    private static FrameCheck checkEntered(Exam e) {
        List<String> missing = e.getItems().isEmpty() && e.writingPrompts().isEmpty()
                ? List.of("Chưa có câu nào") : List.of();
        List<String> hidden = new ArrayList<>();
        e.getItems().stream().map(ExamItem::getQuestion)
                .filter(q -> q.getStatus() != QuestionStatus.ACTIVE).map(Question::getCode).sorted().forEach(hidden::add);
        e.writingPrompts().stream().filter(p -> p.getStatus() != QuestionStatus.ACTIVE).map(WritingPrompt::getCode).forEach(hidden::add);
        return new FrameCheck(missing, hidden, false, true);
    }

    private static List<EnteredGroup> enteredGroups(Exam e) {
        List<EnteredGroup> out = new ArrayList<>();
        Map<Long, List<Question>> byGroup = new LinkedHashMap<>();
        e.getItems().stream()
                .sorted(Comparator.comparingInt(ExamItem::getNo))
                .forEach(it -> byGroup.computeIfAbsent(it.getQuestion().getGroup().getId(), k -> new ArrayList<>()).add(it.getQuestion()));
        for (List<Question> qs : byGroup.values()) {
            Question head = qs.get(0);
            String code = QuestionBankService.displayTemplate(head.getTemplateCode(), head.getTypeCode());
            String refs = qs.stream().map(q -> "câu " + q.getOriginalNo() + " · " + q.getCode()).collect(Collectors.joining(", "));
            out.add(new EnteredGroup(code, QuestionBankService.templateName(code), head.getSkill().getLabel(), shareLabel(code, head.getSkill()), refs));
        }
        for (WritingPrompt prompt : e.writingPrompts()) {
            String code = QuestionBankService.writingTemplate(prompt.getNo());
            String label = code == null ? "Viết" : code;
            out.add(new EnteredGroup(label, QuestionBankService.templateName(code), Skill.WRITING.getLabel(), "1 câu",
                    "câu " + prompt.getNo() + " · " + prompt.getCode()));
        }
        return out;
    }

    private static String shareLabel(String templateCode, Skill skill) {
        return QuestionTemplateCatalog.all().stream()
                .filter(s -> s.code().equals(templateCode))
                .findFirst()
                .map(s -> s.childMax() > 1
                        ? (s.skill() == Skill.LISTENING ? "1 đoạn nghe, nhiều câu"
                        : s.skill() == Skill.READING ? "1 đoạn văn, nhiều câu" : "nhiều câu")
                        : "1 câu")
                .orElse(skill == Skill.LISTENING ? "1 câu" : "1 câu");
    }

    private static boolean sectionIncluded(Exam e, Skill skill) {
        return switch (e.getKind()) {
            case FULL -> true;
            case LISTENING -> skill == Skill.LISTENING;
            case READING -> skill == Skill.READING;
            case WRITING -> skill == Skill.WRITING;
        };
    }

    private FrameCheck check(Exam e, List<TypeRow> rows) {
        List<String> missing = rows.stream().filter(r -> !r.isComplete())
                .map(r -> r.type().code() + " (thiếu " + (r.required() - r.filled()) + ")").toList();
        List<String> hidden = e.getItems().stream().map(ExamItem::getQuestion)
                .filter(q -> q.getStatus() != QuestionStatus.ACTIVE).map(Question::getCode).sorted().toList();
        boolean writingRequired = e.getTrack() == Track.TOPIK_II && (e.getKind() == ExamKind.FULL || e.getKind() == ExamKind.WRITING);
        boolean writingOk = e.hasFullWritingSet() && e.writingPrompts().stream().allMatch(p -> p.getStatus() == QuestionStatus.ACTIVE);
        return new FrameCheck(missing, hidden, writingRequired, writingOk);
    }

    @Transactional(readOnly = true)
    public List<Candidate> candidates(Long examId, String typeCode, int firstNo) {
        Exam e = get(examId);
        QuestionTypeDef t = typeOf(e, typeCode);
        List<Integer> block = t.blockOf(firstNo);
        if (block.isEmpty()) throw new ExamDomainException("Câu " + firstNo + " không thuộc dạng " + typeCode + ".");
        Set<Long> used = usedGroupIds(e);
        return candidateGroups(t, block.size(), used).stream().map(qs -> {
            Question head = qs.get(0);
            String preview = Optional.ofNullable(head.getGroup().getPassageKo())
                    .or(() -> Optional.ofNullable(head.getGroup().getScriptKo())).orElse(head.getPromptKo());
            return new Candidate(head.getGroup().getId(), qs.stream().map(Question::getCode).toList(),
                    head.getSource() != null ? head.getSource().getName() : "Biên soạn",
                    preview.length() > 140 ? preview.substring(0, 140) + "…" : preview,
                    head.getPurpose().name(), qs.size());
        }).toList();
    }

    /** Nhóm câu (trọn group) phù hợp một block: cùng dạng, đúng kích thước, mọi câu ACTIVE, không dành cho đánh giá. */
    private List<List<Question>> candidateGroups(QuestionTypeDef t, int size, Set<Long> usedGroups) {
        Map<Long, List<Question>> byGroup = questions.findByTypeCodeAndStatus(t.code(), QuestionStatus.ACTIVE).stream()
                .filter(q -> q.getPurpose() != QuestionPurpose.ASSESSMENT)
                .collect(Collectors.groupingBy(q -> q.getGroup().getId(), LinkedHashMap::new, Collectors.toList()));
        return byGroup.entrySet().stream()
                .filter(en -> !usedGroups.contains(en.getKey()))
                .map(Map.Entry::getValue)
                .filter(qs -> qs.size() == size && qs.get(0).getGroup().getSize() == size)
                .map(qs -> qs.stream().sorted(Comparator.comparingInt(Question::getOriginalNo)).toList())
                .toList();
    }

    private static Set<Long> usedGroupIds(Exam e) {
        return e.getItems().stream().map(i -> i.getQuestion().getGroup().getId()).collect(Collectors.toSet());
    }

    @Transactional
    public int autofillType(Long examId, String typeCode, Actor actor) {
        requireContent(actor);
        Exam e = editable(examId);
        int added = fillType(e, typeOf(e, typeCode));
        log(actor, "AUTOFILL_EXAM_TYPE", e.getName() + ": tự chọn " + added + " câu dạng " + typeCode);
        return added;
    }

    @Transactional
    public int autofillAll(Long examId, Actor actor) {
        requireContent(actor);
        Exam e = editable(examId);
        int added = 0;
        for (QuestionTypeDef t : QuestionTypeCatalog.mcqTypes(e.getTrack())) {
            if (sectionIncluded(e, t.skill())) added += fillType(e, t);
        }
        log(actor, "AUTOFILL_EXAM", e.getName() + ": tự điền " + added + " câu cho các dạng còn thiếu");
        return added;
    }

    private int fillType(Exam e, QuestionTypeDef t) {
        Set<String> filledSlots = e.getItems().stream().map(i -> i.getSection() + "#" + i.getNo()).collect(Collectors.toSet());
        int added = 0;
        for (List<Integer> block : t.blocks()) {
            boolean anyFilled = block.stream().anyMatch(n -> filledSlots.contains(t.skill() + "#" + n));
            if (anyFilled) continue;
            List<List<Question>> pool = new ArrayList<>(candidateGroups(t, block.size(), usedGroupIds(e)));
            if (pool.isEmpty()) continue;
            List<Question> pick = pool.get(random.nextInt(pool.size()));
            for (int i = 0; i < block.size(); i++) {
                e.getItems().add(new ExamItem(e, t.skill(), block.get(i), t.code(), pick.get(i)));
                added++;
            }
        }
        return added;
    }

    @Transactional
    public void clearType(Long examId, String typeCode, Actor actor) {
        requireContent(actor);
        Exam e = editable(examId);
        QuestionTypeDef t = typeOf(e, typeCode);
        e.getItems().removeIf(i -> i.getSection() == t.skill() && t.numbers().contains(i.getNo()));
        log(actor, "CLEAR_EXAM_TYPE", e.getName() + ": xoá các câu dạng " + typeCode);
    }

    @Transactional
    public void clearBlock(Long examId, String typeCode, int firstNo, Actor actor) {
        requireContent(actor);
        Exam e = editable(examId);
        QuestionTypeDef t = typeOf(e, typeCode);
        List<Integer> block = t.blockOf(firstNo);
        e.getItems().removeIf(i -> i.getSection() == t.skill() && block.contains(i.getNo()));
    }

    /** Chọn tay: gán trọn một group vào block (thay câu cũ nếu có). */
    @Transactional
    public void assignBlock(Long examId, String typeCode, int firstNo, Long groupId, Actor actor) {
        requireContent(actor);
        Exam e = editable(examId);
        QuestionTypeDef t = typeOf(e, typeCode);
        List<Integer> block = t.blockOf(firstNo);
        if (block.isEmpty()) throw new ExamDomainException("Câu " + firstNo + " không thuộc dạng " + typeCode + ".");
        e.getItems().removeIf(i -> i.getSection() == t.skill() && block.contains(i.getNo()));
        List<Question> pick = candidateGroups(t, block.size(), usedGroupIds(e)).stream()
                .filter(qs -> qs.get(0).getGroup().getId().equals(groupId)).findFirst()
                .orElseThrow(() -> new ExamDomainException("Nhóm câu này không dùng được cho " + typeCode
                        + " câu " + block.get(0) + " (sai dạng, sai số câu, đã bị ẩn hoặc đã dùng trong đề)."));
        for (int i = 0; i < block.size(); i++) {
            e.getItems().add(new ExamItem(e, t.skill(), block.get(i), t.code(), pick.get(i)));
        }
        log(actor, "ASSIGN_EXAM_SLOT", e.getName() + ": chọn " + pick.stream().map(Question::getCode).collect(Collectors.joining(", "))
                + " cho " + typeCode + " câu " + block.get(0));
    }

    public List<WritingPrompt> writingOptions(int no) {
        return writingPrompts.findByNoAndStatusOrderByIdDesc(no, QuestionStatus.ACTIVE);
    }

    @Transactional
    public void setWriting(Long examId, Map<Integer, Long> promptIds, Actor actor) {
        requireContent(actor);
        Exam e = editable(examId);
        if (e.getTrack() != Track.TOPIK_II) throw new ExamDomainException("TOPIK I không có phần Viết.");
        for (int no = 51; no <= 54; no++) {
            Long id = promptIds.get(no);
            if (id == null) continue;
            WritingPrompt p = writingPrompts.findById(id).orElseThrow(() -> new ExamDomainException("Không tìm thấy đề Viết."));
            if (p.getNo() != no) throw new ExamDomainException("Đề " + p.getCode() + " là câu " + p.getNo() + ", không dùng cho câu " + no + ".");
            if (p.getStatus() != QuestionStatus.ACTIVE) throw new ExamDomainException("Đề Viết " + p.getCode() + " đang bị ẩn.");
            setWritingSlot(e, p);
        }
        log(actor, "SET_EXAM_WRITING", e.getName() + ": chọn bộ đề Viết 51–54");
    }

    private static void setWritingSlot(Exam e, WritingPrompt p) {
        switch (p.getNo()) {
            case 51 -> e.setWriting51(p);
            case 52 -> e.setWriting52(p);
            case 53 -> e.setWriting53(p);
            case 54 -> e.setWriting54(p);
            default -> {
            }
        }
    }

    @Transactional
    public void updateSettings(Long examId, String name, Integer listening, Integer writing, Integer reading, Actor actor) {
        requireContent(actor);
        Exam e = editable(examId);
        if (name != null && !name.isBlank()) {
            if (name.trim().length() > 200) throw new ExamDomainException("Tên đề tối đa 200 ký tự.");
            e.setName(name.trim());
        }
        if (listening != null) e.setListeningMinutes(minutes(listening));
        if (reading != null) e.setReadingMinutes(minutes(reading));
        if (writing != null && e.getTrack() == Track.TOPIK_II) e.setWritingMinutes(minutes(writing));
        log(actor, "UPDATE_EXAM", e.getName() + ": cập nhật tên/thời gian (Nghe " + e.getListeningMinutes()
                + "′, Viết " + e.getWritingMinutes() + "′, Đọc " + e.getReadingMinutes() + "′)");
    }

    private static int minutes(int v) {
        if (v < 5 || v > 240) throw new ExamDomainException("Thời gian mỗi phần từ 5 đến 240 phút.");
        return v;
    }

    // ------------------------------------------------------------------ duyệt, phát hành, lưu trữ

    @Transactional
    public void submitForPublish(Long examId, Actor actor) {
        requireContent(actor);
        Exam e = editable(examId);
        if (e.getKind() != ExamKind.FULL) throw new ExamDomainException("Chỉ Full Mock được gửi duyệt phát hành.");
        ExamFrame f = frame(examId);
        if (!f.check().isOk()) {
            throw new ExamDomainException("Khung đề chưa đạt, chưa gửi duyệt được. Xem mục Kiểm tra khung đề.");
        }
        ApprovalRequestDTO req = submitApproval(actor, REQ_PUBLISH, "Phát hành " + e.getName(),
                "Đề " + e.getCode() + " gồm " + e.questionCount() + " câu, " + e.totalMinutes() + " phút. Khi duyệt, hệ thống tự sinh Mini "
                        + (e.getTrack() == Track.TOPIK_II ? "Nghe, Viết và Đọc." : "Nghe và Đọc."), e);
        e.setStatus(ExamStatus.PENDING_APPROVAL);
        e.setPendingAction("PUBLISH");
        e.setApprovalRequestId(req.getId());
        log(actor, "SUBMIT_EXAM_PUBLISH", "Gửi duyệt phát hành " + e.getName() + " (yêu cầu #" + req.getId() + ")");
    }

    @Transactional
    public void requestArchive(Long examId, String reason, Actor actor) {
        requireContent(actor);
        Exam e = get(examId);
        if (e.getKind() != ExamKind.FULL || e.isAuto()) {
            throw new ExamDomainException("Mini Test tự sinh không lưu trữ riêng được; hãy lưu trữ Full Mock gốc.");
        }
        if (e.getStatus() != ExamStatus.PUBLISHED) throw new ExamDomainException("Chỉ lưu trữ được đề đang phát hành.");
        if (reason == null || reason.trim().length() < 5) throw new ExamDomainException("Nhập lý do lưu trữ (ít nhất 5 ký tự).");
        ApprovalRequestDTO req = submitApproval(actor, REQ_ARCHIVE, "Lưu trữ " + e.getName(),
                "Lý do: " + reason.trim() + ". Các Mini Test con cũng được lưu trữ theo.", e);
        e.setStatus(ExamStatus.PENDING_APPROVAL);
        e.setPendingAction("ARCHIVE");
        e.setApprovalRequestId(req.getId());
        log(actor, "SUBMIT_EXAM_ARCHIVE", "Gửi duyệt lưu trữ " + e.getName() + " — " + reason.trim());
    }

    private ApprovalRequestDTO submitApproval(Actor actor, String type, String title, String desc, Exam e) {
        try {
            return approvals.submitTargetedRequest(actor.name(), "Content Manager", type, title, desc,
                    APPROVAL_TARGET, e.getId(), null);
        } catch (IllegalStateException ex) {
            throw new ExamDomainException(ex.getMessage());
        }
    }

    @EventListener
    @Transactional
    public void onApprovalDecided(ApprovalDecidedEvent ev) {
        ApprovalRequestDTO req = ev.request();
        if (!APPROVAL_TARGET.equals(req.getTargetType()) || req.getTargetId() == null) return;
        Exam e = get(req.getTargetId());
        if (e.getStatus() != ExamStatus.PENDING_APPROVAL) {
            throw new ExamDomainException("Đề " + e.getName() + " không còn ở trạng thái chờ duyệt.");
        }
        Actor admin = new Actor(ev.decidedBy() == null ? "Admin" : ev.decidedBy(), Actor.ROLE_ADMIN);
        boolean publish = REQ_PUBLISH.equals(req.getRequestType());
        e.setPendingAction(null);
        e.setApprovalRequestId(null);
        if (!ev.approved()) {
            e.setStatus(publish ? ExamStatus.DRAFT : ExamStatus.PUBLISHED);
            log(admin, "REJECT_EXAM_" + (publish ? "PUBLISH" : "ARCHIVE"), "Từ chối " + (publish ? "phát hành " : "lưu trữ ") + e.getName()
                    + (ev.note() != null ? " — " + ev.note() : ""));
            return;
        }
        if (publish) publish(e, admin);
        else archive(e, admin);
    }

    /**
     * Hộp duyệt đang nằm trong bộ nhớ: sau khi khởi động lại, đề "chờ duyệt" có thể trỏ tới yêu cầu không còn
     * (hoặc trùng id với yêu cầu khác). Đưa các đề đó về trạng thái trước khi gửi để CM gửi lại.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void releaseOrphanedApprovals() {
        for (Exam e : exams.findAllByOrderByIdDesc()) {
            if (e.getStatus() != ExamStatus.PENDING_APPROVAL) continue;
            boolean alive = e.getApprovalRequestId() != null && approvals.findById(e.getApprovalRequestId())
                    .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()))
                    .filter(r -> APPROVAL_TARGET.equals(r.getTargetType()) && e.getId().equals(r.getTargetId()))
                    .isPresent();
            if (alive) continue;
            boolean wasPublish = "PUBLISH".equals(e.getPendingAction());
            e.setStatus(wasPublish ? ExamStatus.DRAFT : ExamStatus.PUBLISHED);
            e.setPendingAction(null);
            e.setApprovalRequestId(null);
            audit.logAction("Hệ thống", "SYSTEM", "RELEASE_EXAM_APPROVAL", "EXAM",
                    e.getName() + ": yêu cầu duyệt không còn trong Hộp duyệt, đưa về " + e.getStatus().getLabel() + " để gửi lại", "127.0.0.1");
        }
    }

    private void publish(Exam e, Actor admin) {
        FrameCheck check = frame(e.getId()).check();
        if (!check.isOk()) {
            throw new ExamDomainException("Khung đề không còn hợp lệ (có câu bị ẩn hoặc thiếu), chưa phát hành được.");
        }
        e.setStatus(ExamStatus.PUBLISHED);
        e.setPublishedAt(LocalDateTime.now());
        List<String> created = new ArrayList<>();
        boolean entered = enteredPaper(e);
        for (QuestionTypeCatalog.Section s : QuestionTypeCatalog.sections(e.getTrack())) {
            ExamKind kind = ExamKind.valueOf(s.skill().name());
            if (entered && kind == ExamKind.WRITING && e.writingPrompts().isEmpty()) continue;
            if (entered && kind != ExamKind.WRITING && e.getItems().stream().noneMatch(it -> it.getSection() == s.skill())) continue;
            Exam mini = new Exam();
            mini.setKind(kind);
            mini.setTrack(e.getTrack());
            mini.setParent(e);
            mini.setAuto(true);
            mini.setSource(e.getSource());
            mini.setSeqNo(e.getSeqNo());
            mini.setName("Mini " + s.skill().getLabel() + " " + e.getTrack().getLabel() + " · Đề " + e.getSeqNo());
            mini.setCode(e.getCode() + "-" + s.skill().name().charAt(0));
            mini.setStatus(ExamStatus.PUBLISHED);
            mini.setPublishedAt(e.getPublishedAt());
            mini.setListeningMinutes(e.getListeningMinutes());
            mini.setReadingMinutes(e.getReadingMinutes());
            mini.setWritingMinutes(e.getWritingMinutes());
            mini.setCreatedBy("Hệ thống");
            if (kind == ExamKind.WRITING) {
                mini.setWriting51(e.getWriting51());
                mini.setWriting52(e.getWriting52());
                mini.setWriting53(e.getWriting53());
                mini.setWriting54(e.getWriting54());
            } else {
                for (ExamItem it : e.getItems()) {
                    if (it.getSection() == s.skill()) {
                        mini.getItems().add(new ExamItem(mini, it.getSection(), it.getNo(), it.getTypeCode(), it.getQuestion()));
                    }
                }
            }
            exams.save(mini);
            created.add(mini.getName());
        }
        log(admin, "PUBLISH_EXAM", "Phát hành " + e.getName());
        log(admin, "AUTO_MINI_TEST", "Tự sinh Mini Test từ " + e.getName() + ": " + String.join(", ", created));
    }

    private void archive(Exam e, Actor admin) {
        e.setStatus(ExamStatus.ARCHIVED);
        for (Exam c : exams.findByParentId(e.getId())) c.setStatus(ExamStatus.ARCHIVED);
        log(admin, "ARCHIVE_EXAM", "Lưu trữ " + e.getName() + " và các Mini Test con");
    }

    // ------------------------------------------------------------------ helpers

    private Exam editable(Long id) {
        Exam e = get(id);
        if (!e.isEditable()) {
            throw new ExamDomainException(e.getStatus() == ExamStatus.PENDING_APPROVAL
                    ? "Đề đang chờ duyệt, không sửa được."
                    : "Đề đã phát hành bị khoá danh sách câu và thời gian; muốn đổi thì Nhân bản rồi soạn đề mới.");
        }
        return e;
    }

    private static QuestionTypeDef typeOf(Exam e, String code) {
        QuestionTypeDef t = QuestionTypeCatalog.byCode(code)
                .orElseThrow(() -> new ExamDomainException("Không có dạng " + code + "."));
        if (t.track() != e.getTrack() || t.isWriting()) {
            throw new ExamDomainException("Dạng " + code + " không thuộc phần trắc nghiệm " + e.getTrack().getLabel() + ".");
        }
        return t;
    }

    private static void requireContent(Actor actor) {
        if (actor == null || !actor.isContentManager()) {
            throw new ExamDomainException("Chỉ Content Manager được soạn đề (Admin chỉ xem và duyệt).");
        }
    }

    private void log(Actor actor, String action, String details) {
        audit.logAction(actor.name(), actor.role(), action, "EXAM", details, "127.0.0.1");
    }
}
