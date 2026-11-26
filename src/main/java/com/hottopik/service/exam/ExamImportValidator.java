package com.hottopik.service.exam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;
import com.hottopik.service.exam.ParsedWorkbook.QuestionRow;
import com.hottopik.service.exam.ParsedWorkbook.WritingRow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Danh sách kiểm tra khi nhập đề (BRD 8.4). Lỗi chặn kích hoạt; cảnh báo thì không.
 * Không phụ thuộc DB: dữ liệu ngoài (file đã tải, câu đã có trong ngân hàng) được truyền vào.
 */
public final class ExamImportValidator {

    public record Context(Track track, boolean fullExam, boolean assessment,
                          Map<String, Long> mediaSizes,
                          Function<Set<String>, Map<String, String>> existingHashLookup,
                          ParsedWorkbook existingSystemWb) {

        public Context(Track track, boolean fullExam, boolean assessment,
                       Map<String, Long> mediaSizes,
                       Function<Set<String>, Map<String, String>> existingHashLookup) {
            this(track, fullExam, assessment, mediaSizes, existingHashLookup, null);
        }
    }

    public record Result(List<ImportIssue> issues) {
        public long errorCount() {
            return issues.stream().filter(ImportIssue::isError).count();
        }

        public long warningCount() {
            return issues.size() - errorCount();
        }
    }

    static final long LARGE_AUDIO_BYTES = 6L * 1024 * 1024;
    private static final Pattern CAUSE = Pattern.compile("^c([1-9]|10)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTI_ANSWER = Pattern.compile("^\\s*[1-4]\\s*([,;/ ]+\\s*[1-4]\\s*)+$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private ExamImportValidator() {
    }

    public static Result validate(ParsedWorkbook wb, Context ctx) {
        List<ImportIssue> issues = new ArrayList<>(wb.issues);
        validateQuestions(wb, ctx, issues);
        validateWriting(wb, ctx, issues);
        warnUnusedMedia(wb, ctx, issues);
        issues.sort(Comparator.comparing((ImportIssue i) -> i.isError() ? 0 : 1)
                .thenComparing(i -> i.sheet() == null ? "" : i.sheet())
                .thenComparing(i -> i.skill() == null ? "" : i.skill())
                .thenComparing(i -> i.no() == null ? 0 : i.no()));
        return new Result(issues);
    }

    // ------------------------------------------------------------------ QUESTIONS

    private static void validateQuestions(ParsedWorkbook wb, Context ctx, List<ImportIssue> issues) {
        final String S = ParsedWorkbook.SHEET_QUESTIONS;
        Map<String, QuestionRow> bySlot = new HashMap<>();

        for (QuestionRow q : wb.questions) {
            String skillName = q.skill == null ? null : q.skill.name();
            if (q.no == null) {
                issues.add(ImportIssue.error(S, q.row, skillName, null,
                        "Cột no phải là số nguyên (đang là \"" + nz(q.noRaw) + "\")."));
            }
            if (q.skill == null) {
                issues.add(ImportIssue.error(S, q.row, null, q.no,
                        "Cột skill phải là LISTENING hoặc READING (đang là \"" + nz(q.skillRaw) + "\")."));
            } else if (q.skill == Skill.WRITING) {
                issues.add(ImportIssue.error(S, q.row, skillName, q.no,
                        "Câu Viết nhập ở sheet WRITING, không nhập ở sheet QUESTIONS."));
                continue;
            }
            if (q.no == null || q.skill == null) continue;

            Optional<QuestionTypeCatalog.Section> section = QuestionTypeCatalog.section(ctx.track(), q.skill);
            if (section.isEmpty() || !section.get().contains(q.no)) {
                String range = section.map(s -> s.firstNo() + "–" + s.lastNo()).orElse("?");
                issues.add(ImportIssue.error(S, q.row, skillName, q.no,
                        "Số câu ngoài phạm vi: " + ctx.track().getLabel() + " phần " + q.skill.getLabel()
                                + " chỉ có câu " + range + "."));
                continue;
            }

            String slot = q.skill + "#" + q.no;
            QuestionRow first = bySlot.putIfAbsent(slot, q);
            if (first != null) {
                issues.add(ImportIssue.error(S, q.row, skillName, q.no,
                        "Trùng (skill, no) với dòng " + first.row + "."));
                continue;
            }

            QuestionTypeDef defaultType = QuestionTypeCatalog.resolve(ctx.track(), q.skill, q.no).orElse(null);
            QuestionTypeDef appliedType = defaultType;

            if (!blank(q.typeCode)) {
                Optional<QuestionTypeDef> customType = QuestionTypeCatalog.byCode(q.typeCode);
                if (customType.isPresent() && customType.get().track() == ctx.track() && customType.get().skill() == q.skill) {
                    q.resolvedType = customType.get().code();
                    appliedType = customType.get();
                } else {
                    issues.add(ImportIssue.error(S, q.row, skillName, q.no,
                            "Mã dạng câu \"" + q.typeCode + "\" không hợp lệ cho " + ctx.track().getLabel()
                                    + " phần " + q.skill.getLabel() + ". Hãy tra cứu tại sheet DANG_CAU."));
                    q.resolvedType = defaultType != null ? defaultType.code() : null;
                }
            } else {
                // Để trống: tự động lấy dạng mặc định theo vị trí câu
                if (defaultType != null) {
                    q.resolvedType = defaultType.code();
                } else {
                    issues.add(ImportIssue.error(S, q.row, skillName, q.no,
                            "Không xác định được dạng câu mặc định cho câu " + q.no + "."));
                }
            }

            if (appliedType != null) {
                validateRowContent(q, appliedType, ctx, issues);
            }
        }

        validateGroups(wb, ctx, issues);
        validateStimulus(wb, ctx, issues);
        warnDuplicateContent(wb, ctx, issues);
        if (ctx.fullExam()) validateStructure(bySlot, ctx, issues);
    }

    private static void validateRowContent(QuestionRow q, QuestionTypeDef type, Context ctx, List<ImportIssue> issues) {
        final String S = ParsedWorkbook.SHEET_QUESTIONS;
        String sk = q.skill.name();

        List<Integer> missingOpts = new ArrayList<>();
        for (int k = 0; k < 4; k++) if (blank(q.options[k])) missingOpts.add(k + 1);
        if (!missingOpts.isEmpty()) {
            issues.add(ImportIssue.error(S, q.row, sk, q.no, "Thiếu phương án " + joinInts(missingOpts) + "."));
        } else {
            Set<String> seen = new HashSet<>();
            for (int k = 0; k < 4; k++) {
                if (!seen.add(normalize(q.options[k]))) {
                    issues.add(ImportIssue.error(S, q.row, sk, q.no, "Các phương án bị trùng nhau (opt_" + (k + 1) + ")."));
                    break;
                }
            }
        }

        if (q.answerRaw == null) {
            issues.add(ImportIssue.error(S, q.row, sk, q.no, "Thiếu đáp án (answer)."));
        } else if (MULTI_ANSWER.matcher(q.answerRaw).matches()) {
            issues.add(ImportIssue.error(S, q.row, sk, q.no,
                    "File đánh dấu nhiều đáp án đúng (\"" + q.answerRaw + "\"); mỗi câu chỉ có đúng 1 đáp án."));
        } else if (q.answer == null || q.answer < 1 || q.answer > 4) {
            issues.add(ImportIssue.error(S, q.row, sk, q.no, "answer phải là số 1–4 (đang là \"" + q.answerRaw + "\")."));
        }

        if (blank(q.explanationVi)) {
            issues.add(ImportIssue.warning(S, q.row, sk, q.no, "Thiếu giải thích tiếng Việt (explanation_vi)."));
        }
        if (q.causeHint != null && !CAUSE.matcher(q.causeHint).matches()) {
            issues.add(ImportIssue.error(S, q.row, sk, q.no, "cause_hint phải là c1–c10 (đang là \"" + q.causeHint + "\")."));
        }
        if (q.purpose != null && !q.purpose.equals("EXAM") && !q.purpose.equals("PRACTICE")) {
            issues.add(ImportIssue.error(S, q.row, sk, q.no, "purpose chỉ nhận EXAM hoặc PRACTICE (đang là \"" + q.purpose + "\")."));
        }
        if (ctx.assessment() && q.purpose != null) {
            issues.add(ImportIssue.warning(S, q.row, sk, q.no,
                    "Nguồn dành riêng cho đánh giá: mọi câu sẽ có purpose = ASSESSMENT, cột purpose bị bỏ qua."));
        }

        if (!blank(q.imageFile) && !ctx.mediaSizes().containsKey(key(q.imageFile))) {
            issues.add(ImportIssue.error(S, q.row, sk, q.no, "Thiếu file ảnh " + q.imageFile + "."));
        }
    }

    /** Dạng 2-câu/bài: các câu trong cùng block phải đủ cặp và chung một group_id. */
    private static void validateGroups(ParsedWorkbook wb, Context ctx, List<ImportIssue> issues) {
        final String S = ParsedWorkbook.SHEET_QUESTIONS;
        Map<String, QuestionRow> bySlot = wb.questions.stream()
                .filter(q -> q.resolvedType != null)
                .collect(Collectors.toMap(q -> q.skill + "#" + q.no, q -> q, (a, b) -> a));
        Map<String, String> groupOwner = new HashMap<>();

        for (QuestionTypeDef type : QuestionTypeCatalog.mcqTypes(ctx.track())) {
            for (List<Integer> block : type.blocks()) {
                List<QuestionRow> present = block.stream()
                        .map(n -> bySlot.get(type.skill() + "#" + n)).filter(Objects::nonNull).toList();
                if (present.isEmpty()) continue;
                String blockLabel = block.get(0) + (block.size() > 1 ? "–" + block.get(block.size() - 1) : "");

                if (block.size() > 1) {
                    if (present.size() < block.size()) {
                        List<Integer> missing = block.stream()
                                .filter(n -> !bySlot.containsKey(type.skill() + "#" + n)).toList();
                        issues.add(ImportIssue.error(S, present.get(0).row, type.skill().name(), present.get(0).no,
                                "Câu " + blockLabel + " (" + type.code() + ") dùng chung một bài nhưng thiếu câu " + joinInts(missing) + "."));
                    }
                    Set<String> gids = present.stream().map(q -> q.groupId).filter(Objects::nonNull).collect(Collectors.toSet());
                    boolean anyBlank = present.stream().anyMatch(q -> blank(q.groupId));
                    if (anyBlank || gids.size() != 1) {
                        issues.add(ImportIssue.error(S, present.get(0).row, type.skill().name(), present.get(0).no,
                                "Câu " + blockLabel + " (" + type.code() + ") phải có cùng một group_id."));
                    }
                }
                for (QuestionRow q : present) {
                    if (blank(q.groupId)) continue;
                    String gk = q.skill + "#" + q.groupId.trim();
                    String owner = groupOwner.putIfAbsent(gk, type.code() + ":" + blockLabel);
                    if (owner != null && !owner.equals(type.code() + ":" + blockLabel)) {
                        issues.add(ImportIssue.error(S, q.row, q.skill.name(), q.no,
                                "group_id \"" + q.groupId + "\" đang dùng cho câu khác (" + owner.replace(":", " câu ") + ")."));
                    }
                }
            }
        }
    }

    /** Đoạn nghe / đoạn văn dùng chung theo block: chỉ cần một dòng trong block có dữ liệu. */
    private static void validateStimulus(ParsedWorkbook wb, Context ctx, List<ImportIssue> issues) {
        final String S = ParsedWorkbook.SHEET_QUESTIONS;
        Map<String, QuestionRow> bySlot = wb.questions.stream()
                .filter(q -> q.resolvedType != null)
                .collect(Collectors.toMap(q -> q.skill + "#" + q.no, q -> q, (a, b) -> a));

        for (QuestionTypeDef type : QuestionTypeCatalog.mcqTypes(ctx.track())) {
            for (List<Integer> block : type.blocks()) {
                List<QuestionRow> present = block.stream()
                        .map(n -> bySlot.get(type.skill() + "#" + n)).filter(Objects::nonNull).toList();
                if (present.isEmpty()) continue;
                QuestionRow head = present.get(0);
                String sk = type.skill().name();

                if (type.skill() == Skill.LISTENING) {
                    Set<String> audios = present.stream().map(q -> q.audioFile).filter(a -> !blank(a))
                            .map(String::trim).collect(Collectors.toCollection(LinkedHashSet::new));
                    if (audios.isEmpty() && ctx.mediaSizes() != null) {
                        String matched = findMatchingAudio(ctx.mediaSizes().keySet(), block.get(0));
                        if (matched != null) {
                            audios.add(matched);
                            for (QuestionRow q : present) q.audioFile = matched;
                        }
                    }
                    if (audios.isEmpty()) {
                        issues.add(ImportIssue.warning(S, head.row, sk, head.no, "Câu Nghe chưa có file âm thanh. Bạn có thể tải file mp3 ở mục tệp đính kèm."));
                    } else {
                        if (audios.size() > 1) {
                            issues.add(ImportIssue.error(S, head.row, sk, head.no,
                                    "Các câu dùng chung đoạn nghe phải cùng một audio_file (" + String.join(", ", audios) + ")."));
                        }
                        for (String a : audios) {
                            Long size = ctx.mediaSizes().get(key(a));
                            if (size == null) {
                                issues.add(ImportIssue.warning(S, head.row, sk, head.no, "Chưa tìm thấy file âm thanh " + a + " trong danh sách tệp đã tải."));
                            } else if (size > LARGE_AUDIO_BYTES) {
                                issues.add(ImportIssue.warning(S, head.row, sk, head.no,
                                        "File âm thanh " + a + " lớn bất thường (" + (size / 1024 / 1024) + " MB, có thể dài hơn 4 phút)."));
                            }
                        }
                    }
                    if (present.stream().allMatch(q -> blank(q.scriptKo))) {
                        issues.add(ImportIssue.error(S, head.row, sk, head.no, "Câu Nghe bắt buộc có lời thoại (script_ko)."));
                    }
                } else if (type.passageRequired()) {
                    boolean hasPassage = present.stream().anyMatch(q -> !blank(q.passageKo));
                    boolean hasImage = present.stream().anyMatch(q -> !blank(q.imageFile));
                    if (!hasPassage && !hasImage) {
                        issues.add(ImportIssue.error(S, head.row, sk, head.no,
                                "Câu Đọc dạng " + type.code() + " bắt buộc có đoạn văn (passage_ko) hoặc ảnh (image_file)."));
                    }
                }
            }
        }
    }

    private static void warnDuplicateContent(ParsedWorkbook wb, Context ctx, List<ImportIssue> issues) {
        // 1. Kiểm tra trùng lặp giữa các câu trong file Excel (MCQ: trùng đoạn văn/lời thoại và 4 phương án, hoặc trùng đề bài và 4 phương án)
        for (int i = 0; i < wb.questions.size(); i++) {
            QuestionRow q1 = wb.questions.get(i);
            if (q1.skill == null || q1.no == null) continue;
            for (int j = i + 1; j < wb.questions.size(); j++) {
                QuestionRow q2 = wb.questions.get(j);
                if (q2.skill == null || q2.no == null || q1.skill != q2.skill) continue;

                String sname = q2.skill == Skill.LISTENING ? "NGHE" : "DOC";
                String p1 = normalize(q1.passageKo != null ? q1.passageKo : q1.scriptKo);
                String p2 = normalize(q2.passageKo != null ? q2.passageKo : q2.scriptKo);
                boolean hasPassage = !p1.isEmpty();
                boolean samePassage = hasPassage && p1.equals(p2);

                boolean optBlank1 = isAllOptionsBlank(q1.options);
                boolean optBlank2 = isAllOptionsBlank(q2.options);
                boolean sameOptions = !optBlank1 && !optBlank2 && areOptionsEqual(q1.options, q2.options);

                boolean sameAnswer = q1.answer != null && q1.answer.equals(q2.answer);
                String pr1 = normalize(q1.promptKo);
                String pr2 = normalize(q2.promptKo);
                boolean hasPrompt = !pr1.isEmpty();
                boolean samePrompt = hasPrompt && pr1.equals(pr2);

                if (samePassage && sameOptions) {
                    String reason = "Trùng đoạn văn/ngữ liệu và 4 phương án" + (sameAnswer ? " (cùng đáp án đúng " + q1.answer + ")" : "");
                    issues.add(ImportIssue.warning(sname, q2.row, q2.skill.name(), q2.no,
                            "Trùng câu với Câu " + q1.no + " (dòng " + q1.row + "): " + reason + "."));
                } else if (!hasPassage && samePrompt && sameOptions) {
                    String reason = "Trùng câu hỏi/đề bài và 4 phương án" + (sameAnswer ? " (cùng đáp án đúng " + q1.answer + ")" : "");
                    issues.add(ImportIssue.warning(sname, q2.row, q2.skill.name(), q2.no,
                            "Trùng câu với Câu " + q1.no + " (dòng " + q1.row + "): " + reason + "."));
                }
            }
        }

        // 2. Kiểm tra trùng lặp câu Viết trong file Excel
        for (int i = 0; i < wb.writings.size(); i++) {
            WritingRow w1 = wb.writings.get(i);
            if (w1.no == null) continue;
            for (int j = i + 1; j < wb.writings.size(); j++) {
                WritingRow w2 = wb.writings.get(j);
                if (w2.no == null) continue;
                String pr1 = normalize(w1.promptKo);
                String pr2 = normalize(w2.promptKo);
                String pass1 = normalize(w1.passageKo);
                String pass2 = normalize(w2.passageKo);
                if (!pr1.isEmpty() && pr1.equals(pr2) && pass1.equals(pass2)) {
                    issues.add(ImportIssue.warning(ParsedWorkbook.SHEET_WRITING, w2.row, "WRITING", w2.no,
                            "Trùng câu với Câu " + w1.no + " (dòng " + w1.row + "): Trùng đề bài và đoạn văn."));
                }
            }
        }

        // 3. Kiểm tra đối chiếu với dữ liệu hiện có trên hệ thống của nguồn đề này
        if (ctx.existingSystemWb() != null) {
            checkDuplicateWithExistingSystem(wb, ctx.existingSystemWb(), issues);
        }

        // 4. Kiểm tra trùng nội dung với câu đã có trong ngân hàng
        Map<QuestionRow, String> rowHash = new HashMap<>();
        for (QuestionRow q : wb.questions) {
            if (q.resolvedType == null || (blank(q.promptKo) && blank(q.passageKo))) continue;
            rowHash.put(q, contentHash(q));
        }
        if (!rowHash.isEmpty() && ctx.existingHashLookup() != null) {
            Map<String, String> existing = ctx.existingHashLookup().apply(new HashSet<>(rowHash.values()));
            for (Map.Entry<QuestionRow, String> e : rowHash.entrySet()) {
                String code = existing.get(e.getValue());
                if (code != null) {
                    QuestionRow q = e.getKey();
                    String sname = q.skill == Skill.LISTENING ? "NGHE" : (q.skill == Skill.READING ? "DOC" : ParsedWorkbook.SHEET_QUESTIONS);
                    issues.add(ImportIssue.warning(sname, q.row, q.skill.name(), q.no,
                            "Câu trùng nội dung với câu " + code + " đã có trong ngân hàng."));
                }
            }
        }
    }

    private static void checkDuplicateWithExistingSystem(ParsedWorkbook wb, ParsedWorkbook existingWb, List<ImportIssue> issues) {
        if (existingWb == null) return;

        // 1. Kiểm tra trắc nghiệm (MCQ)
        if (wb.questions != null && existingWb.questions != null && !existingWb.questions.isEmpty()) {
            Map<String, QuestionRow> existingBySlot = new HashMap<>();
            for (QuestionRow eq : existingWb.questions) {
                if (eq.skill != null && eq.no != null) {
                    existingBySlot.put(eq.skill.name() + "#" + eq.no, eq);
                }
            }

            for (QuestionRow q : wb.questions) {
                if (q.skill == null || q.no == null) continue;
                String sname = q.skill == Skill.LISTENING ? "NGHE" : (q.skill == Skill.READING ? "DOC" : ParsedWorkbook.SHEET_QUESTIONS);
                String slotKey = q.skill.name() + "#" + q.no;

                QuestionRow sameSlot = existingBySlot.get(slotKey);
                if (sameSlot != null) {
                    boolean existHasContent = !blank(sameSlot.promptKo) || !blank(sameSlot.passageKo)
                            || !blank(sameSlot.scriptKo) || !isAllOptionsBlank(sameSlot.options);
                    boolean incomingHasContent = !blank(q.promptKo) || !blank(q.passageKo)
                            || !blank(q.scriptKo) || !isAllOptionsBlank(q.options);

                    if (existHasContent && incomingHasContent) {
                        String h1 = contentHash(q);
                        String h2 = contentHash(sameSlot);
                        boolean sameContent = h1.equals(h2);
                        boolean sameAnswer = Objects.equals(q.answer, sameSlot.answer);

                        if (sameContent && sameAnswer) {
                            String ansStr = q.answer != null ? ("đáp án " + q.answer) : "";
                            issues.add(ImportIssue.warning(sname, q.row, q.skill.name(), q.no,
                                    "Trùng câu với Câu " + q.no + " đã có trên hệ thống (nội dung và " + ansStr + " trùng khớp hoàn toàn, nếu nhập sẽ ghi đè dữ liệu cũ)."));
                        } else if (sameContent) {
                            issues.add(ImportIssue.warning(sname, q.row, q.skill.name(), q.no,
                                    "Trùng câu với Câu " + q.no + " đã có trên hệ thống (nội dung trùng khớp nhưng khác đáp án: file là " + q.answer + ", hệ thống là " + sameSlot.answer + ", nếu nhập sẽ ghi đè dữ liệu cũ)."));
                        } else {
                            issues.add(ImportIssue.warning(sname, q.row, q.skill.name(), q.no,
                                    "Trùng số câu: Câu " + q.no + " đã có nội dung trên hệ thống (nội dung khác, nếu xác nhận nhập sẽ ghi đè lên câu hiện có)."));
                        }
                    }
                } else {
                    boolean incomingHasContent = !blank(q.promptKo) || !blank(q.passageKo)
                            || !blank(q.scriptKo) || !isAllOptionsBlank(q.options);
                    if (incomingHasContent) {
                        String h1 = contentHash(q);
                        for (QuestionRow eq : existingWb.questions) {
                            if (eq.skill != q.skill || eq.no == null) continue;
                            boolean eqHasContent = !blank(eq.promptKo) || !blank(eq.passageKo)
                                    || !blank(eq.scriptKo) || !isAllOptionsBlank(eq.options);
                            if (eqHasContent && h1.equals(contentHash(eq))) {
                                issues.add(ImportIssue.warning(sname, q.row, q.skill.name(), q.no,
                                        "Trùng nội dung với Câu " + eq.no + " đã có trên hệ thống (trùng ngữ liệu và các phương án)."));
                                break;
                            }
                        }
                    }
                }
            }
        }

        // 2. Kiểm tra câu tự luận Viết
        if (wb.writings != null && existingWb.writings != null && !existingWb.writings.isEmpty()) {
            Map<Integer, WritingRow> existingWByNo = new HashMap<>();
            for (WritingRow ew : existingWb.writings) {
                if (ew.no != null) existingWByNo.put(ew.no, ew);
            }

            for (WritingRow w : wb.writings) {
                if (w.no == null) continue;
                WritingRow sameNo = existingWByNo.get(w.no);
                if (sameNo != null) {
                    boolean existWHasContent = !blank(sameNo.promptKo) || !blank(sameNo.passageKo) || !blank(sameNo.modelAnswer);
                    boolean incomingWHasContent = !blank(w.promptKo) || !blank(w.passageKo) || !blank(w.modelAnswer);

                    if (existWHasContent && incomingWHasContent) {
                        String pr1 = normalize(w.promptKo);
                        String pr2 = normalize(sameNo.promptKo);
                        String pass1 = normalize(w.passageKo);
                        String pass2 = normalize(sameNo.passageKo);
                        if (pr1.equals(pr2) && pass1.equals(pass2)) {
                            issues.add(ImportIssue.warning(ParsedWorkbook.SHEET_WRITING, w.row, "WRITING", w.no,
                                    "Trùng câu với Câu " + w.no + " đã có trên hệ thống (đề bài và đoạn văn trùng khớp, nếu nhập sẽ ghi đè dữ liệu cũ)."));
                        } else {
                            issues.add(ImportIssue.warning(ParsedWorkbook.SHEET_WRITING, w.row, "WRITING", w.no,
                                    "Trùng số câu: Câu " + w.no + " đã có nội dung trên hệ thống (nội dung khác, nếu xác nhận nhập sẽ ghi đè lên câu hiện có)."));
                        }
                    }
                } else {
                    boolean incomingWHasContent = !blank(w.promptKo) || !blank(w.passageKo) || !blank(w.modelAnswer);
                    if (incomingWHasContent) {
                        String pr1 = normalize(w.promptKo);
                        String pass1 = normalize(w.passageKo);
                        for (WritingRow ew : existingWb.writings) {
                            if (ew.no == null) continue;
                            boolean ewHasContent = !blank(ew.promptKo) || !blank(ew.passageKo) || !blank(ew.modelAnswer);
                            String pr2 = normalize(ew.promptKo);
                            String pass2 = normalize(ew.passageKo);
                            if (ewHasContent && !pr1.isEmpty() && pr1.equals(pr2) && pass1.equals(pass2)) {
                                issues.add(ImportIssue.warning(ParsedWorkbook.SHEET_WRITING, w.row, "WRITING", w.no,
                                        "Trùng nội dung với Câu " + ew.no + " đã có trên hệ thống (trùng đề bài và đoạn văn)."));
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isAllOptionsBlank(String[] opts) {
        if (opts == null) return true;
        for (String o : opts) {
            if (!blank(o)) return false;
        }
        return true;
    }

    private static boolean areOptionsEqual(String[] o1, String[] o2) {
        if (o1 == null || o2 == null || o1.length != 4 || o2.length != 4) return false;
        for (int k = 0; k < 4; k++) {
            if (!normalize(o1[k]).equals(normalize(o2[k]))) return false;
        }
        return true;
    }

    /** Nguồn khai là đề đầy đủ: mọi dạng phải đủ số câu theo cấu trúc TOPIK. */
    private static void validateStructure(Map<String, QuestionRow> bySlot, Context ctx, List<ImportIssue> issues) {
        for (QuestionTypeDef type : QuestionTypeCatalog.mcqTypes(ctx.track())) {
            List<Integer> missing = type.numbers().stream()
                    .filter(n -> !bySlot.containsKey(type.skill() + "#" + n)).toList();
            if (!missing.isEmpty()) {
                issues.add(ImportIssue.error(ParsedWorkbook.SHEET_QUESTIONS, null, type.skill().name(), missing.get(0),
                        "Không đủ số câu dạng " + type.code() + " (" + type.name() + ", cần " + type.requiredCount()
                                + " câu " + type.rangeLabel() + "): thiếu câu " + joinInts(missing) + "."));
            }
        }
    }

    // ------------------------------------------------------------------ WRITING

    private static void validateWriting(ParsedWorkbook wb, Context ctx, List<ImportIssue> issues) {
        final String S = ParsedWorkbook.SHEET_WRITING;
        if (ctx.track() == Track.TOPIK_I) {
            if (!wb.writings.isEmpty()) {
                issues.add(ImportIssue.warning(S, null, "WRITING", null,
                        "TOPIK I không có phần Viết; sheet WRITING sẽ bị bỏ qua."));
            }
            return;
        }
        if (ctx.fullExam() && !wb.hasWritingSheet) {
            issues.add(ImportIssue.error(S, null, "WRITING", null, "Đề TOPIK II đầy đủ cần sheet WRITING với câu 51–54."));
            return;
        }
        Map<Integer, WritingRow> byNo = new HashMap<>();
        for (WritingRow w : wb.writings) {
            if (w.no == null || w.no < 51 || w.no > 54) {
                issues.add(ImportIssue.error(S, w.row, "WRITING", w.no, "Cột no của sheet WRITING chỉ nhận 51, 52, 53, 54 (đang là \"" + nz(w.noRaw) + "\")."));
                continue;
            }
            WritingRow prev = byNo.putIfAbsent(w.no, w);
            if (prev != null) {
                issues.add(ImportIssue.error(S, w.row, "WRITING", w.no, "Trùng câu " + w.no + " với dòng " + prev.row + "."));
                continue;
            }
            if ((w.no == 51 || w.no == 52) && blank(w.modelAnswer) && (!blank(w.keys1) || !blank(w.keys2))) {
                String k1 = w.keys1 != null ? w.keys1.split("\\|")[0].trim() : "";
                String k2 = w.keys2 != null ? w.keys2.split("\\|")[0].trim() : "";
                w.modelAnswer = "㉠ " + k1 + " ㉡ " + k2;
            }
            if (w.no <= 52 && blank(w.modelAnswer)) issues.add(ImportIssue.error(S, w.row, "WRITING", w.no, "Thiếu bài mẫu / đáp án chuẩn (model_answer)."));
            if (w.no == 51 || w.no == 52) {
                if (blank(w.keys1) || blank(w.keys2)) {
                    issues.add(ImportIssue.error(S, w.row, "WRITING", w.no, "Câu " + w.no + " cần đáp án chấp nhận cho cả ㉠ (keys_1) và ㉡ (keys_2)."));
                }
                String combined = (w.promptKo == null ? "" : w.promptKo) + " " + (w.passageKo == null ? "" : w.passageKo);
                if (!combined.contains("㉠") || !combined.contains("㉡")) {
                    issues.add(ImportIssue.warning(S, w.row, "WRITING", w.no, "Đề câu " + w.no + " nên có hai chỗ trống ( ㉠ ) và ( ㉡ )."));
                }
            }
            if (w.no >= 53) validateChart(w, ctx, issues);
            if (blank(w.rubricNote)) {
                issues.add(ImportIssue.warning(S, w.row, "WRITING", w.no, "Chưa có ghi chú tiêu chí chấm (rubric_note)."));
            }
        }
        if (ctx.fullExam() || !wb.writings.isEmpty()) {
            for (int n = 51; n <= 54; n++) {
                if (!byNo.containsKey(n) && (ctx.fullExam())) {
                    issues.add(ImportIssue.error(S, null, "WRITING", n, "Sheet WRITING thiếu câu " + n + "."));
                }
            }
        }
    }

    private static void validateChart(WritingRow w, Context ctx, List<ImportIssue> issues) {
        final String S = ParsedWorkbook.SHEET_WRITING;
        if (blank(w.chartJson)) {
            return;
        }
        String v = w.chartJson.trim();
        if (v.startsWith("[")) {
            try {
                JsonNode node = JSON.readTree(v);
                boolean ok = node.isArray() && node.size() > 0;
                for (JsonNode item : node) {
                    ok &= item.isArray() && item.size() == 2 && item.get(0).isTextual() && item.get(1).isNumber();
                }
                if (!ok) throw new IllegalArgumentException();
            } catch (Exception e) {
                issues.add(ImportIssue.error(S, w.row, "WRITING", w.no,
                        "chart_json không đúng định dạng [[\"nhãn\", số], …]."));
            }
        } else if (!ctx.mediaSizes().containsKey(key(v))) {
            issues.add(ImportIssue.error(S, w.row, "WRITING", w.no, "Thiếu file ảnh biểu đồ " + v + "."));
        }
    }

    private static void warnUnusedMedia(ParsedWorkbook wb, Context ctx, List<ImportIssue> issues) {
        Set<String> used = new HashSet<>();
        for (QuestionRow q : wb.questions) {
            if (!blank(q.audioFile)) used.add(key(q.audioFile));
            if (!blank(q.imageFile)) used.add(key(q.imageFile));
        }
        for (WritingRow w : wb.writings) {
            if (!blank(w.chartJson) && !w.chartJson.trim().startsWith("[")) used.add(key(w.chartJson));
        }
        List<String> unused = ctx.mediaSizes().keySet().stream().filter(k -> !used.contains(k)).sorted().toList();
        if (!unused.isEmpty()) {
            issues.add(ImportIssue.warning("MEDIA", null, null, null,
                    unused.size() + " file đã tải nhưng không được dùng trong Excel: "
                            + String.join(", ", unused.stream().limit(8).toList()) + (unused.size() > 8 ? "…" : "")));
        }
    }

    // ------------------------------------------------------------------ helpers

    public static String key(String filename) {
        return filename == null ? null : filename.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}…“”‘’·]+", "");
    }

    public static String contentHash(QuestionRow q) {
        return contentHash(q.promptKo, q.passageKo, q.scriptKo, q.options);
    }

    public static String contentHash(String prompt, String passage, String script, String[] options) {
        StringBuilder sb = new StringBuilder(normalize(prompt)).append('|').append(normalize(passage))
                .append('|').append(normalize(script));
        if (options != null) {
            for (String o : options) sb.append('|').append(normalize(o));
        }
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String findMatchingAudio(Set<String> mediaKeys, int no) {
        if (mediaKeys == null || mediaKeys.isEmpty()) return null;
        List<String> candidates = List.of(
                "listening_" + no + ".mp3",
                "listening_" + String.format("%02d", no) + ".mp3",
                "l" + no + ".mp3",
                "l" + String.format("%02d", no) + ".mp3",
                no + ".mp3",
                String.format("%02d", no) + ".mp3",
                "cau_" + no + ".mp3",
                "q" + no + ".mp3"
        );
        for (String cand : candidates) {
            String k = key(cand);
            if (mediaKeys.contains(k)) return cand;
        }
        for (String k : mediaKeys) {
            if (k.endsWith(".mp3") || !k.contains(".")) {
                List<Integer> extracted = ExamImportService.extractQuestionNumbers(k);
                if (extracted.contains(no)) return k;
            }
        }
        return null;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String joinInts(List<Integer> nums) {
        return nums.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
