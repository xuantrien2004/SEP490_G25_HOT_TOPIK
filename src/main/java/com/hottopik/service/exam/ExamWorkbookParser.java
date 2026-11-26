package com.hottopik.service.exam;

import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;
import org.apache.poi.ss.usermodel.*;

import java.io.InputStream;
import java.util.*;

/** Đọc file .xlsx theo mẫu: sheet QUESTIONS (trắc nghiệm) và WRITING (51–54). Cột tra theo tên tiêu đề (Anh hoặc Việt). */
public final class ExamWorkbookParser {

    public static final List<String> QUESTION_COLUMNS = List.of(
            "no", "skill", "type_code", "group_id", "audio_file", "script_ko", "passage_ko", "image_file",
            "prompt_ko", "opt_1", "opt_2", "opt_3", "opt_4", "answer", "explanation_vi", "cause_hint",
            "vocab_ids", "grammar_ids", "purpose");
    public static final List<String> QUESTION_REQUIRED = List.of(
            "no", "skill", "prompt_ko", "opt_1", "opt_2", "opt_3", "opt_4", "answer");
    public static final List<String> QUESTION_SKILL_REQUIRED = List.of(
            "no", "prompt_ko", "opt_1", "opt_2", "opt_3", "opt_4", "answer");

    public static final List<String> WRITING_COLUMNS = List.of(
            "no", "prompt_ko", "keys_1", "keys_2", "chart_json", "model_answer", "rubric_note");
    public static final List<String> WRITING_REQUIRED = List.of("no");

    private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT);

    private ExamWorkbookParser() {
    }

    public static ParsedWorkbook parse(InputStream in) {
        return parse(in, Track.TOPIK_II);
    }

    public static ParsedWorkbook parse(InputStream in, Track track) {
        ParsedWorkbook out = new ParsedWorkbook();
        Workbook wb;
        try {
            wb = WorkbookFactory.create(in);
        } catch (Exception e) {
            out.issues.add(ImportIssue.error("FILE", null, null, null,
                    "Không đọc được file Excel (.xlsx). Hãy dùng đúng file mẫu."));
            return out;
        }
        try (wb) {
            Sheet qs = findSheet(wb, ParsedWorkbook.SHEET_QUESTIONS);
            if (qs == null) qs = findSheet(wb, "CAU_HOI");

            Sheet ngheSheet = findSheet(wb, "NGHE");
            if (ngheSheet == null) ngheSheet = findSheet(wb, "LISTENING");
            Sheet docSheet = findSheet(wb, "DOC");
            if (docSheet == null) docSheet = findSheet(wb, "ĐỌC");
            if (docSheet == null) docSheet = findSheet(wb, "READING");
            Sheet vietSheet = findSheet(wb, "VIET");
            if (vietSheet == null) vietSheet = findSheet(wb, "VIẾT");
            if (vietSheet == null) vietSheet = findSheet(wb, "WRITING");

            boolean hasSkillSheets = (ngheSheet != null || docSheet != null || (vietSheet != null && qs == null));
            if (hasSkillSheets) {
                if (ngheSheet != null) {
                    parseSkillQuestions(ngheSheet, track, Skill.LISTENING, out);
                }
                if (vietSheet != null) {
                    out.hasWritingSheet = true;
                    parseSkillWriting(vietSheet, track, out);
                }
                if (docSheet != null) {
                    parseSkillQuestions(docSheet, track, Skill.READING, out);
                }
            } else if (qs != null) {
                parseQuestions(qs, track, out);
                Sheet ws = findSheet(wb, ParsedWorkbook.SHEET_WRITING);
                if (ws == null) ws = findSheet(wb, "VIET");
                if (ws != null) {
                    out.hasWritingSheet = true;
                    parseWriting(ws, out);
                }
            } else {
                out.issues.add(ImportIssue.error(ParsedWorkbook.SHEET_QUESTIONS, null, null, null,
                        "Thiếu sheet câu hỏi. Hãy dùng đúng file mẫu."));
            }
        } catch (Exception e) {
            out.issues.add(ImportIssue.error("FILE", null, null, null, "Lỗi đọc file: " + e.getMessage()));
        }
        return out;
    }

    public static ParsedWorkbook parseSkill(InputStream in, Track track, Skill targetSkill) {
        ParsedWorkbook out = new ParsedWorkbook();
        Workbook wb;
        try {
            wb = WorkbookFactory.create(in);
        } catch (Exception e) {
            out.issues.add(ImportIssue.error("FILE", null, null, null,
                    "Không đọc được file Excel (.xlsx). Hãy dùng đúng file mẫu."));
            return out;
        }
        try (wb) {
            Sheet sheet = findSkillSheet(wb, targetSkill);
            if (sheet == null) {
                out.issues.add(ImportIssue.error("FILE", null, targetSkill.name(), null,
                        "Không tìm thấy sheet cho kỹ năng " + targetSkill.getLabel() + "."));
                return out;
            }
            if (targetSkill == Skill.WRITING) {
                out.hasWritingSheet = true;
                parseSkillWriting(sheet, track, out);
            } else {
                parseSkillQuestions(sheet, track, targetSkill, out);
            }
        } catch (Exception e) {
            out.issues.add(ImportIssue.error("FILE", null, null, null, "Lỗi đọc file: " + e.getMessage()));
        }
        return out;
    }

    private static Sheet findSkillSheet(Workbook wb, Skill skill) {
        List<String> names = switch (skill) {
            case LISTENING -> List.of("NGHE", "LISTENING", "QUESTIONS", "CAU_HOI");
            case READING -> List.of("DOC", "ĐỌC", "READING", "QUESTIONS", "CAU_HOI");
            case WRITING -> List.of("VIET", "VIẾT", "WRITING");
        };
        for (String n : names) {
            Sheet s = findSheet(wb, n);
            if (s != null) return s;
        }
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            String sname = wb.getSheetName(i).trim().toUpperCase(Locale.ROOT);
            if (!sname.contains("DANG_CAU") && !sname.contains("HUONG_DAN")) {
                return wb.getSheetAt(i);
            }
        }
        return wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
    }

    private static Sheet findSheet(Workbook wb, String name) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (wb.getSheetName(i).trim().equalsIgnoreCase(name)) return wb.getSheetAt(i);
        }
        return null;
    }

    public static String canonicalHeader(String raw) {
        if (raw == null) return "";
        String h = raw.trim().toLowerCase(Locale.ROOT).replace(" ", "_");
        h = h.replaceAll("[/\\\\:;]", "_");
        if (h.equals("câu") || h.equals("cau") || h.equals("stt") || h.equals("no")) return "no";
        if (h.contains("tên_dạng") || h.contains("ten_dang") || h.equals("type_name")) return "type_name";
        if (h.equals("dạng") || h.equals("dang") || h.contains("dạng") || h.contains("dang") || h.contains("mã_dạng") || h.contains("ma_dang") || h.equals("type_code") || h.equals("type")) return "type_code";
        if (h.contains("kỹ_năng") || h.contains("ky_nang") || h.equals("skill")) return "skill";
        if (h.contains("phụ_đề") || h.contains("phu_de") || h.contains("lời_thoại") || h.equals("script_ko") || h.equals("script")) return "script_ko";
        if (h.contains("file_nghe") || h.contains("tên_file_nghe") || h.contains("audio") || h.equals("audio_file")) return "audio_file";
        if (h.contains("đoạn_văn") || h.contains("doan_van") || h.equals("passage_ko") || h.equals("passage")) return "passage_ko";
        if (h.contains("câu_hỏi") || h.contains("cau_hoi") || h.contains("đề_bài") || h.contains("de_bai") || h.equals("prompt_ko") || h.equals("prompt")) return "prompt_ko";
        if (h.contains("phương_án_1") || h.contains("phuong_an_1") || h.equals("opt_1") || h.equals("opt1") || h.equals("1")) return "opt_1";
        if (h.contains("phương_án_2") || h.contains("phuong_an_2") || h.equals("opt_2") || h.equals("opt2") || h.equals("2")) return "opt_2";
        if (h.contains("phương_án_3") || h.contains("phuong_an_3") || h.equals("opt_3") || h.equals("opt3") || h.equals("3")) return "opt_3";
        if (h.contains("phương_án_4") || h.contains("phuong_an_4") || h.equals("opt_4") || h.equals("opt4") || h.equals("4")) return "opt_4";
        if (h.contains("đáp_án_đúng") || h.contains("dap_an_dung") || (h.startsWith("đáp_án") && !h.contains("chấp_nhận")) || h.equals("answer")) return "answer";
        if (h.contains("giải_thích") || h.contains("giai_thich") || h.equals("explanation_vi") || h.equals("explanation")) return "explanation_vi";
        if (h.contains("㉠") || h.contains("keys_1") || h.contains("dap_an_1") || (h.contains("chấp_nhận") && (h.contains("1") || h.contains("a") || h.contains("giáp")))) return "keys_1";
        if (h.contains("㉡") || h.contains("keys_2") || h.contains("dap_an_2") || (h.contains("chấp_nhận") && (h.contains("2") || h.contains("b") || h.contains("ất")))) return "keys_2";
        if (h.contains("hình_ảnh") || h.contains("hinh_anh") || h.contains("image") || h.equals("image_file")) return "image_file";
        if (h.contains("group") || h.contains("nhóm")) return "group_id";
        if (h.contains("bài_mẫu") || h.contains("model_answer")) return "model_answer";
        if (h.contains("biểu_đồ") || h.contains("chart")) return "chart_json";
        return h;
    }

    private static Map<String, Integer> headerIndex(Sheet sheet) {
        Map<String, Integer> idx = new HashMap<>();
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) return idx;
        for (Cell c : header) {
            String raw = FORMATTER.formatCellValue(c).trim();
            if (!raw.isEmpty()) {
                String lower = raw.toLowerCase(Locale.ROOT);
                idx.putIfAbsent(lower, c.getColumnIndex());
                String canon = canonicalHeader(raw);
                if (!canon.isEmpty()) {
                    idx.putIfAbsent(canon, c.getColumnIndex());
                }
            }
        }
        return idx;
    }

    private static boolean checkHeaders(Sheet sheet, Map<String, Integer> idx, List<String> required, ParsedWorkbook out) {
        List<String> missing = required.stream().filter(c -> !idx.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            out.issues.add(ImportIssue.error(sheet.getSheetName(), 1, null, null,
                    "Thiếu cột bắt buộc: " + String.join(", ", missing)));
            return false;
        }
        return true;
    }

    private static String cell(Row row, Map<String, Integer> idx, String col) {
        Integer i = idx.get(col);
        if (i == null || row == null) return null;
        Cell c = row.getCell(i);
        if (c == null) return null;
        String v = c.getCellType() == CellType.FORMULA
                ? FORMATTER.formatCellValue(c, c.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator())
                : FORMATTER.formatCellValue(c);
        v = v == null ? null : v.trim();
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static boolean isBlankRow(Row row, Map<String, Integer> idx) {
        if (row == null) return true;
        for (Integer i : idx.values()) {
            Cell c = row.getCell(i);
            if (c != null && !FORMATTER.formatCellValue(c).trim().isEmpty()) return false;
        }
        return true;
    }

    private static boolean isQuestionContentEmpty(Row row, Map<String, Integer> idx) {
        String prompt = cell(row, idx, "prompt_ko");
        String opt1 = cell(row, idx, "opt_1");
        String opt2 = cell(row, idx, "opt_2");
        String opt3 = cell(row, idx, "opt_3");
        String opt4 = cell(row, idx, "opt_4");
        String ans = cell(row, idx, "answer");
        String script = cell(row, idx, "script_ko");
        String passage = cell(row, idx, "passage_ko");
        String audio = cell(row, idx, "audio_file");
        return blank(prompt) && blank(opt1) && blank(opt2) && blank(opt3) && blank(opt4)
                && blank(ans) && blank(script) && blank(passage) && blank(audio);
    }

    private static boolean isWritingContentEmpty(Row row, Map<String, Integer> idx) {
        String prompt = cell(row, idx, "prompt_ko");
        String passage = cell(row, idx, "passage_ko");
        String k1 = cell(row, idx, "keys_1");
        String k2 = cell(row, idx, "keys_2");
        String model = cell(row, idx, "model_answer");
        String exp = cell(row, idx, "explanation_vi");
        String rub = cell(row, idx, "rubric_note");
        return blank(prompt) && blank(passage) && blank(k1) && blank(k2) && blank(model) && blank(exp) && blank(rub);
    }

    static Integer parseInt(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void parseQuestions(Sheet sheet, Track track, ParsedWorkbook out) {
        Map<String, Integer> idx = headerIndex(sheet);
        if (!checkHeaders(sheet, idx, QUESTION_REQUIRED, out)) return;
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isBlankRow(row, idx) || isQuestionContentEmpty(row, idx)) continue;
            ParsedWorkbook.QuestionRow q = new ParsedWorkbook.QuestionRow();
            q.row = r + 1;
            q.noRaw = cell(row, idx, "no");
            q.no = parseInt(q.noRaw);
            q.skillRaw = cell(row, idx, "skill");
            q.skill = parseSkill(q.skillRaw);
            q.groupId = cell(row, idx, "group_id");
            q.audioFile = cell(row, idx, "audio_file");
            q.scriptKo = cell(row, idx, "script_ko");
            q.passageKo = cell(row, idx, "passage_ko");
            q.imageFile = cell(row, idx, "image_file");
            q.promptKo = cell(row, idx, "prompt_ko");
            for (int k = 0; k < 4; k++) q.options[k] = cell(row, idx, "opt_" + (k + 1));
            q.answerRaw = cell(row, idx, "answer");
            q.answer = parseInt(q.answerRaw);
            q.explanationVi = cell(row, idx, "explanation_vi");
            q.causeHint = cell(row, idx, "cause_hint");
            q.vocabIds = cell(row, idx, "vocab_ids");
            q.grammarIds = cell(row, idx, "grammar_ids");
            q.purpose = upper(cell(row, idx, "purpose"));

            String rawTypeCode = cell(row, idx, "type_code");
            String rawTypeName = cell(row, idx, "type_name");
            if (!blank(rawTypeCode)) {
                q.typeCode = resolveTypeCode(track, q.skill, rawTypeCode);
            } else if (!blank(rawTypeName)) {
                q.typeCode = resolveTypeCode(track, q.skill, rawTypeName);
            }

            if (q.no != null && q.skill != null) {
                if (blank(q.typeCode)) {
                    QuestionTypeCatalog.resolve(track, q.skill, q.no).ifPresent(type -> {
                        q.typeCode = type.code();
                        List<Integer> block = type.blockOf(q.no);
                        if (block.size() > 1 && blank(q.groupId)) {
                            q.groupId = (q.skill == Skill.LISTENING ? "L" : "R") + block.get(0);
                        }
                    });
                } else {
                    QuestionTypeCatalog.byCode(q.typeCode).ifPresent(type -> {
                        List<Integer> block = type.blockOf(q.no);
                        if (block.size() > 1 && blank(q.groupId)) {
                            q.groupId = (q.skill == Skill.LISTENING ? "L" : "R") + block.get(0);
                        }
                    });
                }
            }
            out.questions.add(q);
        }

        propagateSharedResources(track, null, out);
    }

    private static void parseSkillQuestions(Sheet sheet, Track track, Skill targetSkill, ParsedWorkbook out) {
        Map<String, Integer> idx = headerIndex(sheet);
        if (!checkHeaders(sheet, idx, QUESTION_SKILL_REQUIRED, out)) return;
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isBlankRow(row, idx) || isQuestionContentEmpty(row, idx)) continue;
            ParsedWorkbook.QuestionRow q = new ParsedWorkbook.QuestionRow();
            q.row = r + 1;
            q.noRaw = cell(row, idx, "no");
            q.no = parseInt(q.noRaw);
            q.skill = targetSkill;
            q.skillRaw = targetSkill.name();
            q.groupId = cell(row, idx, "group_id");
            q.audioFile = cell(row, idx, "audio_file");
            q.scriptKo = cell(row, idx, "script_ko");
            q.passageKo = cell(row, idx, "passage_ko");
            q.imageFile = cell(row, idx, "image_file");
            q.promptKo = cell(row, idx, "prompt_ko");
            for (int k = 0; k < 4; k++) q.options[k] = cell(row, idx, "opt_" + (k + 1));
            q.answerRaw = cell(row, idx, "answer");
            q.answer = parseInt(q.answerRaw);
            q.explanationVi = cell(row, idx, "explanation_vi");
            q.causeHint = cell(row, idx, "cause_hint");
            q.vocabIds = cell(row, idx, "vocab_ids");
            q.grammarIds = cell(row, idx, "grammar_ids");
            q.purpose = upper(cell(row, idx, "purpose"));

            String rawTypeCode = cell(row, idx, "type_code");
            String rawTypeName = cell(row, idx, "type_name");
            if (!blank(rawTypeCode)) {
                q.typeCode = resolveTypeCode(track, targetSkill, rawTypeCode);
            } else if (!blank(rawTypeName)) {
                q.typeCode = resolveTypeCode(track, targetSkill, rawTypeName);
            }

            if (q.no != null) {
                if (blank(q.typeCode)) {
                    QuestionTypeCatalog.resolve(track, q.skill, q.no).ifPresent(type -> {
                        q.typeCode = type.code();
                        List<Integer> block = type.blockOf(q.no);
                        if (block.size() > 1 && blank(q.groupId)) {
                            q.groupId = (q.skill == Skill.LISTENING ? "L" : "R") + block.get(0);
                        }
                    });
                } else {
                    QuestionTypeCatalog.byCode(q.typeCode).ifPresent(type -> {
                        List<Integer> block = type.blockOf(q.no);
                        if (block.size() > 1 && blank(q.groupId)) {
                            q.groupId = (q.skill == Skill.LISTENING ? "L" : "R") + block.get(0);
                        }
                    });
                }
            }
            out.questions.add(q);
        }

        propagateSharedResources(track, targetSkill, out);
    }

    static String resolveTypeCode(Track track, Skill skill, String codeOrName) {
        if (codeOrName == null || codeOrName.isBlank()) return null;
        String raw = codeOrName.trim();
        Optional<QuestionTypeDef> byCode = QuestionTypeCatalog.byCode(raw);
        if (byCode.isPresent()) return byCode.get().code();

        List<QuestionTypeDef> list = (track != null && skill != null)
                ? QuestionTypeCatalog.forTrackAndSkill(track, skill)
                : QuestionTypeCatalog.all();

        for (QuestionTypeDef td : list) {
            if (td.code().equalsIgnoreCase(raw)) return td.code();
            if (td.name().equalsIgnoreCase(raw)) return td.code();
        }
        for (QuestionTypeDef td : list) {
            if (raw.toLowerCase(Locale.ROOT).contains(td.code().toLowerCase(Locale.ROOT))
                    || td.name().toLowerCase(Locale.ROOT).contains(raw.toLowerCase(Locale.ROOT))) {
                return td.code();
            }
        }
        return raw;
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static void propagateSharedResources(Track track, Skill targetSkill, ParsedWorkbook out) {
        // 1. Lan truyền theo blocks chuẩn của đề TOPIK nếu khớp số câu chính thức
        for (QuestionTypeDef type : QuestionTypeCatalog.mcqTypes(track)) {
            if (targetSkill != null && type.skill() != targetSkill) continue;
            for (List<Integer> block : type.blocks()) {
                if (block.size() > 1) {
                    String audio = null;
                    String script = null;
                    String passage = null;
                    String img = null;
                    for (int no : block) {
                        for (ParsedWorkbook.QuestionRow q : out.questions) {
                            if (q.skill == type.skill() && q.no != null && q.no == no) {
                                if (audio == null && !blank(q.audioFile)) audio = q.audioFile;
                                if (script == null && !blank(q.scriptKo)) script = q.scriptKo;
                                if (passage == null && !blank(q.passageKo)) passage = q.passageKo;
                                if (img == null && !blank(q.imageFile)) img = q.imageFile;
                            }
                        }
                    }
                    for (int no : block) {
                        for (ParsedWorkbook.QuestionRow q : out.questions) {
                            if (q.skill == type.skill() && q.no != null && q.no == no) {
                                if (blank(q.audioFile) && audio != null) q.audioFile = audio;
                                if (blank(q.scriptKo) && script != null) q.scriptKo = script;
                                if (blank(q.passageKo) && passage != null) q.passageKo = passage;
                                if (blank(q.imageFile) && img != null) q.imageFile = img;
                                if (blank(q.groupId)) {
                                    q.groupId = (q.skill == Skill.LISTENING ? "L" : "R") + block.get(0);
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Lan truyền theo group_id nếu file Excel có cột group_id
        Map<String, List<ParsedWorkbook.QuestionRow>> byGid = new HashMap<>();
        for (ParsedWorkbook.QuestionRow q : out.questions) {
            if (!blank(q.groupId) && (targetSkill == null || q.skill == targetSkill)) {
                byGid.computeIfAbsent(q.skill + "#" + q.groupId.trim(), k -> new ArrayList<>()).add(q);
            }
        }
        for (List<ParsedWorkbook.QuestionRow> group : byGid.values()) {
            if (group.size() > 1) {
                String audio = group.stream().map(q -> q.audioFile).filter(s -> !blank(s)).findFirst().orElse(null);
                String script = group.stream().map(q -> q.scriptKo).filter(s -> !blank(s)).findFirst().orElse(null);
                String passage = group.stream().map(q -> q.passageKo).filter(s -> !blank(s)).findFirst().orElse(null);
                String img = group.stream().map(q -> q.imageFile).filter(s -> !blank(s)).findFirst().orElse(null);
                for (ParsedWorkbook.QuestionRow q : group) {
                    if (blank(q.audioFile) && audio != null) q.audioFile = audio;
                    if (blank(q.scriptKo) && script != null) q.scriptKo = script;
                    if (blank(q.passageKo) && passage != null) q.passageKo = passage;
                    if (blank(q.imageFile) && img != null) q.imageFile = img;
                }
            }
        }

        // 3. Lan truyền các câu liên tiếp theo dạng nhóm hoặc khi dùng chung đoạn văn / phụ đề
        List<Skill> skills = targetSkill != null ? List.of(targetSkill) : List.of(Skill.LISTENING, Skill.READING);
        for (Skill sk : skills) {
            List<ParsedWorkbook.QuestionRow> list = out.questions.stream()
                    .filter(q -> q.skill == sk && q.no != null)
                    .sorted(Comparator.comparingInt(q -> q.no))
                    .toList();
            if (list.size() < 2) continue;

            int n = list.size();
            int i = 0;
            while (i < n) {
                ParsedWorkbook.QuestionRow q0 = list.get(i);
                QuestionTypeDef type0 = null;
                if (!blank(q0.typeCode)) {
                    type0 = QuestionTypeCatalog.byCode(q0.typeCode).orElse(null);
                }
                if (type0 == null && q0.no != null) {
                    type0 = QuestionTypeCatalog.resolve(track, sk, q0.no).orElse(null);
                }

                boolean isGrouped = type0 != null && type0.isGrouped();
                int maxGroup = (sk == Skill.READING) ? 3 : 2;

                int matched = 1;
                while (matched < maxGroup && (i + matched) < n) {
                    ParsedWorkbook.QuestionRow qNext = list.get(i + matched);
                    boolean samePassage = (sk == Skill.READING && !blank(q0.passageKo) && !blank(qNext.passageKo)
                            && clean(q0.passageKo).equals(clean(qNext.passageKo)));
                    boolean passBlank = (sk == Skill.READING && !blank(q0.passageKo) && blank(qNext.passageKo));

                    boolean sameScript = (sk == Skill.LISTENING && !blank(q0.scriptKo) && !blank(qNext.scriptKo)
                            && clean(q0.scriptKo).equals(clean(qNext.scriptKo)));
                    boolean scriptBlank = (sk == Skill.LISTENING && !blank(q0.scriptKo) && blank(qNext.scriptKo));

                    boolean sameAudio = (sk == Skill.LISTENING && !blank(q0.audioFile) && !blank(qNext.audioFile)
                            && q0.audioFile.trim().equalsIgnoreCase(qNext.audioFile.trim()));
                    boolean audioBlank = (sk == Skill.LISTENING && !blank(q0.audioFile) && blank(qNext.audioFile));

                    boolean sameType = isGrouped && (blank(qNext.typeCode) || (type0 != null && qNext.typeCode.equalsIgnoreCase(type0.code())));

                    if (samePassage || (isGrouped && passBlank && sameType)
                            || sameScript || sameAudio || (isGrouped && (scriptBlank || audioBlank) && sameType)) {
                        matched++;
                    } else {
                        break;
                    }
                }

                if (matched > 1) {
                    String sharedAudio = null;
                    String sharedScript = null;
                    String sharedPassage = null;
                    String sharedImg = null;
                    for (int k = 0; k < matched; k++) {
                        ParsedWorkbook.QuestionRow q = list.get(i + k);
                        if (sharedAudio == null && !blank(q.audioFile)) sharedAudio = q.audioFile;
                        if (sharedScript == null && !blank(q.scriptKo)) sharedScript = q.scriptKo;
                        if (sharedPassage == null && !blank(q.passageKo)) sharedPassage = q.passageKo;
                        if (sharedImg == null && !blank(q.imageFile)) sharedImg = q.imageFile;
                    }

                    String gid = !blank(q0.groupId) ? q0.groupId : ((sk == Skill.LISTENING ? "L" : "R") + q0.no);
                    for (int k = 0; k < matched; k++) {
                        ParsedWorkbook.QuestionRow q = list.get(i + k);
                        if (blank(q.audioFile) && sharedAudio != null) q.audioFile = sharedAudio;
                        if (blank(q.scriptKo) && sharedScript != null) q.scriptKo = sharedScript;
                        if (blank(q.passageKo) && sharedPassage != null) q.passageKo = sharedPassage;
                        if (blank(q.imageFile) && sharedImg != null) q.imageFile = sharedImg;
                        if (blank(q.groupId)) q.groupId = gid;
                        if (type0 != null && blank(q.typeCode)) q.typeCode = type0.code();
                    }
                    i += matched;
                } else {
                    i++;
                }
            }
        }
    }

    private static void parseWriting(Sheet sheet, ParsedWorkbook out) {
        Map<String, Integer> idx = headerIndex(sheet);
        if (idx.isEmpty()) return;
        if (!checkHeaders(sheet, idx, WRITING_REQUIRED, out)) return;
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isBlankRow(row, idx) || isWritingContentEmpty(row, idx)) continue;
            ParsedWorkbook.WritingRow w = new ParsedWorkbook.WritingRow();
            w.row = r + 1;
            w.noRaw = cell(row, idx, "no");
            w.no = parseInt(w.noRaw);
            w.typeCode = upper(cell(row, idx, "type_code"));
            w.promptKo = cell(row, idx, "prompt_ko");
            w.passageKo = cell(row, idx, "passage_ko");
            w.keys1 = cell(row, idx, "keys_1");
            w.keys2 = cell(row, idx, "keys_2");
            w.chartJson = cell(row, idx, "chart_json");
            w.modelAnswer = cell(row, idx, "model_answer");
            w.rubricNote = cell(row, idx, "rubric_note");
            if (w.rubricNote == null) w.rubricNote = cell(row, idx, "explanation_vi");
            if ((w.no == null || w.no <= 52) && blank(w.modelAnswer) && (!blank(w.keys1) || !blank(w.keys2))) {
                String k1 = w.keys1 != null ? w.keys1.split("\\|")[0].trim() : "";
                String k2 = w.keys2 != null ? w.keys2.split("\\|")[0].trim() : "";
                w.modelAnswer = "㉠ " + k1 + " ㉡ " + k2;
            }
            out.writings.add(w);
        }
    }

    private static void parseSkillWriting(Sheet sheet, Track track, ParsedWorkbook out) {
        Map<String, Integer> idx = headerIndex(sheet);
        if (idx.isEmpty()) return;
        if (!checkHeaders(sheet, idx, WRITING_REQUIRED, out)) return;
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isBlankRow(row, idx) || isWritingContentEmpty(row, idx)) continue;
            ParsedWorkbook.WritingRow w = new ParsedWorkbook.WritingRow();
            w.row = r + 1;
            w.noRaw = cell(row, idx, "no");
            w.no = parseInt(w.noRaw);
            w.typeCode = upper(cell(row, idx, "type_code"));
            w.promptKo = cell(row, idx, "prompt_ko");
            w.passageKo = cell(row, idx, "passage_ko");
            w.keys1 = cell(row, idx, "keys_1");
            w.keys2 = cell(row, idx, "keys_2");
            w.chartJson = cell(row, idx, "chart_json");
            w.modelAnswer = cell(row, idx, "model_answer");
            w.rubricNote = cell(row, idx, "rubric_note");
            if (w.rubricNote == null) w.rubricNote = cell(row, idx, "explanation_vi");
            if ((w.no == null || w.no <= 52) && blank(w.modelAnswer) && (!blank(w.keys1) || !blank(w.keys2))) {
                String k1 = w.keys1 != null ? w.keys1.split("\\|")[0].trim() : "";
                String k2 = w.keys2 != null ? w.keys2.split("\\|")[0].trim() : "";
                w.modelAnswer = "㉠ " + k1 + " ㉡ " + k2;
            }
            out.writings.add(w);
        }
    }

    private static Skill parseSkill(String raw) {
        if (raw == null) return null;
        try {
            return Skill.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String upper(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
