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
                          Function<Set<String>, Map<String, String>> existingHashLookup) {
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

            QuestionTypeDef type = QuestionTypeCatalog.resolve(ctx.track(), q.skill, q.no).orElseThrow();
            q.resolvedType = type.code();
            if (q.typeCode != null && !q.typeCode.equals(type.code())) {
                issues.add(ImportIssue.error(S, q.row, skillName, q.no,
                        "type_code \"" + q.typeCode + "\" không khớp mã tự gán " + type.code() + " (" + type.name() + ")."));
            }

            validateRowContent(q, type, ctx, issues);
        }

        validateGroups(wb, ctx, issues);
        validateStimulus(wb, ctx, issues);
        warnDuplicateContent(wb, ctx, issues);
        if (ctx.fullExam()) validateStructure(bySlot, ctx, issues);
    }

    private static void validateRowContent(QuestionRow q, QuestionTypeDef type, Context ctx, List<ImportIssue> issues) {
        final String S = ParsedWorkbook.SHEET_QUESTIONS;
        String sk = q.skill.name();

        if (blank(q.promptKo)) issues.add(ImportIssue.error(S, q.row, sk, q.no, "Thiếu câu hỏi (prompt_ko)."));

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

        if (type.imageRequired() && blank(q.imageFile)) {
            issues.add(ImportIssue.error(S, q.row, sk, q.no, "Dạng " + type.code() + " (" + type.name() + ") bắt buộc có image_file."));
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
                    if (audios.isEmpty()) {
                        issues.add(ImportIssue.error(S, head.row, sk, head.no, "Câu Nghe bắt buộc có audio_file."));
                    } else {
                        if (audios.size() > 1) {
                            issues.add(ImportIssue.error(S, head.row, sk, head.no,
                                    "Các câu dùng chung đoạn nghe phải cùng một audio_file (" + String.join(", ", audios) + ")."));
                        }
                        for (String a : audios) {
                            Long size = ctx.mediaSizes().get(key(a));
                            if (size == null) {
                                issues.add(ImportIssue.error(S, head.row, sk, head.no, "Thiếu file âm thanh " + a + "."));
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
        final String S = ParsedWorkbook.SHEET_QUESTIONS;
        Map<String, QuestionRow> hashToRow = new LinkedHashMap<>();
        Map<QuestionRow, String> rowHash = new HashMap<>();
        for (QuestionRow q : wb.questions) {
            if (q.resolvedType == null || blank(q.promptKo)) continue;
            String h = contentHash(q);
            rowHash.put(q, h);
            QuestionRow prev = hashToRow.putIfAbsent(h, q);
            if (prev != null) {
                issues.add(ImportIssue.warning(S, q.row, q.skill.name(), q.no,
                        "Nội dung trùng với dòng " + prev.row + " trong cùng file."));
            }
        }
        if (rowHash.isEmpty() || ctx.existingHashLookup() == null) return;
        Map<String, String> existing = ctx.existingHashLookup().apply(new HashSet<>(rowHash.values()));
        for (Map.Entry<QuestionRow, String> e : rowHash.entrySet()) {
            String code = existing.get(e.getValue());
            if (code != null) {
                QuestionRow q = e.getKey();
                issues.add(ImportIssue.warning(S, q.row, q.skill.name(), q.no,
                        "Câu trùng nội dung với câu " + code + " đã có trong ngân hàng."));
            }
        }
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
            if (blank(w.promptKo)) issues.add(ImportIssue.error(S, w.row, "WRITING", w.no, "Thiếu đề bài (prompt_ko)."));
            if (blank(w.modelAnswer)) issues.add(ImportIssue.error(S, w.row, "WRITING", w.no, "Thiếu bài mẫu / đáp án chuẩn (model_answer)."));
            if (w.no == 51 || w.no == 52) {
                if (blank(w.keys1) || blank(w.keys2)) {
                    issues.add(ImportIssue.error(S, w.row, "WRITING", w.no, "Câu " + w.no + " cần đáp án chấp nhận cho cả ㉠ (keys_1) và ㉡ (keys_2)."));
                }
                if (!blank(w.promptKo) && (!w.promptKo.contains("㉠") || !w.promptKo.contains("㉡"))) {
                    issues.add(ImportIssue.warning(S, w.row, "WRITING", w.no, "Đề câu " + w.no + " nên có hai chỗ trống ( ㉠ ) và ( ㉡ )."));
                }
            }
            if (w.no == 53) validateChart(w, ctx, issues);
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
            issues.add(ImportIssue.error(S, w.row, "WRITING", 53, "Câu 53 cần chart_json hoặc tên file ảnh biểu đồ."));
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
                issues.add(ImportIssue.error(S, w.row, "WRITING", 53,
                        "chart_json không đúng định dạng [[\"nhãn\", số], …]."));
            }
        } else if (!ctx.mediaSizes().containsKey(key(v))) {
            issues.add(ImportIssue.error(S, w.row, "WRITING", 53, "Thiếu file ảnh biểu đồ " + v + "."));
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
        for (String o : options) sb.append('|').append(normalize(o));
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
