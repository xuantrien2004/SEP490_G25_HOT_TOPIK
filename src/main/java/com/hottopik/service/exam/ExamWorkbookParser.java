package com.hottopik.service.exam;

import com.hottopik.entity.exam.Skill;
import org.apache.poi.ss.usermodel.*;

import java.io.InputStream;
import java.util.*;

/** Đọc file .xlsx theo mẫu: sheet QUESTIONS (trắc nghiệm) và WRITING (51–54). Cột tra theo tên tiêu đề. */
public final class ExamWorkbookParser {

    public static final List<String> QUESTION_COLUMNS = List.of(
            "no", "skill", "type_code", "group_id", "audio_file", "script_ko", "passage_ko", "image_file",
            "prompt_ko", "opt_1", "opt_2", "opt_3", "opt_4", "answer", "explanation_vi", "cause_hint",
            "vocab_ids", "grammar_ids", "purpose");
    public static final List<String> QUESTION_REQUIRED = List.of(
            "no", "skill", "prompt_ko", "opt_1", "opt_2", "opt_3", "opt_4", "answer");

    public static final List<String> WRITING_COLUMNS = List.of(
            "no", "prompt_ko", "keys_1", "keys_2", "chart_json", "model_answer", "rubric_note");
    public static final List<String> WRITING_REQUIRED = List.of("no", "prompt_ko", "model_answer");

    private static final DataFormatter FORMATTER = new DataFormatter(Locale.ROOT);

    private ExamWorkbookParser() {
    }

    public static ParsedWorkbook parse(InputStream in) {
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
            if (qs == null) {
                out.issues.add(ImportIssue.error(ParsedWorkbook.SHEET_QUESTIONS, null, null, null,
                        "Thiếu sheet QUESTIONS."));
            } else {
                parseQuestions(qs, out);
            }
            Sheet ws = findSheet(wb, ParsedWorkbook.SHEET_WRITING);
            if (ws != null) {
                out.hasWritingSheet = true;
                parseWriting(ws, out);
            }
        } catch (Exception e) {
            out.issues.add(ImportIssue.error("FILE", null, null, null, "Lỗi đọc file: " + e.getMessage()));
        }
        return out;
    }

    private static Sheet findSheet(Workbook wb, String name) {
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            if (wb.getSheetName(i).trim().equalsIgnoreCase(name)) return wb.getSheetAt(i);
        }
        return null;
    }

    private static Map<String, Integer> headerIndex(Sheet sheet) {
        Map<String, Integer> idx = new HashMap<>();
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) return idx;
        for (Cell c : header) {
            String name = FORMATTER.formatCellValue(c).trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) idx.putIfAbsent(name, c.getColumnIndex());
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

    private static void parseQuestions(Sheet sheet, ParsedWorkbook out) {
        Map<String, Integer> idx = headerIndex(sheet);
        if (!checkHeaders(sheet, idx, QUESTION_REQUIRED, out)) return;
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isBlankRow(row, idx)) continue;
            ParsedWorkbook.QuestionRow q = new ParsedWorkbook.QuestionRow();
            q.row = r + 1;
            q.noRaw = cell(row, idx, "no");
            q.no = parseInt(q.noRaw);
            q.skillRaw = cell(row, idx, "skill");
            q.skill = parseSkill(q.skillRaw);
            q.typeCode = upper(cell(row, idx, "type_code"));
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
            out.questions.add(q);
        }
    }

    private static void parseWriting(Sheet sheet, ParsedWorkbook out) {
        Map<String, Integer> idx = headerIndex(sheet);
        if (idx.isEmpty()) return;
        if (!checkHeaders(sheet, idx, WRITING_REQUIRED, out)) return;
        for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (isBlankRow(row, idx)) continue;
            ParsedWorkbook.WritingRow w = new ParsedWorkbook.WritingRow();
            w.row = r + 1;
            w.noRaw = cell(row, idx, "no");
            w.no = parseInt(w.noRaw);
            w.promptKo = cell(row, idx, "prompt_ko");
            w.keys1 = cell(row, idx, "keys_1");
            w.keys2 = cell(row, idx, "keys_2");
            w.chartJson = cell(row, idx, "chart_json");
            w.modelAnswer = cell(row, idx, "model_answer");
            w.rubricNote = cell(row, idx, "rubric_note");
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
}
