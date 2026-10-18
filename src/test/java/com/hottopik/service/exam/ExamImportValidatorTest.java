package com.hottopik.service.exam;

import com.hottopik.entity.exam.Track;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ExamImportValidatorTest {

    private static Map<String, Long> media(SampleExamFactory.Sample s) {
        Map<String, Long> m = new HashMap<>();
        s.audioFiles().forEach(f -> m.put(ExamImportValidator.key(f), 1024L));
        s.imageFiles().forEach(f -> m.put(ExamImportValidator.key(f), 512L));
        return m;
    }

    private static ExamImportValidator.Result validate(byte[] excel, Track track, Map<String, Long> media) {
        ParsedWorkbook wb = ExamWorkbookParser.parse(new ByteArrayInputStream(excel));
        return ExamImportValidator.validate(wb, new ExamImportValidator.Context(track, true, false, media, hashes -> Map.of()));
    }

    private static byte[] edit(byte[] excel, Consumer<Sheet> change) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            change.accept(wb.getSheet(ParsedWorkbook.SHEET_QUESTIONS));
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static int col(String name) {
        return ExamWorkbookParser.QUESTION_COLUMNS.indexOf(name);
    }

    private static void set(Sheet s, int row, String column, String value) {
        Row r = s.getRow(row);
        Cell c = r.getCell(col(column));
        if (c == null) c = r.createCell(col(column));
        c.setCellValue(value);
    }

    private static boolean hasError(ExamImportValidator.Result r, String fragment) {
        return r.issues().stream().anyMatch(i -> i.isError() && i.message().contains(fragment));
    }

    @Test
    void fullSamplesAreValid() throws IOException {
        for (Track t : Track.values()) {
            SampleExamFactory.Sample s = SampleExamFactory.full(t, 1);
            ExamImportValidator.Result r = validate(s.excel(), t, media(s));
            assertEquals(0, r.errorCount(), () -> t + ": " + r.issues());
        }
    }

    @Test
    void missingMediaIsAnError() throws IOException {
        SampleExamFactory.Sample s = SampleExamFactory.full(Track.TOPIK_I, 1);
        ExamImportValidator.Result r = validate(s.excel(), Track.TOPIK_I, Map.of());
        assertTrue(r.errorCount() > 0);
        assertTrue(r.issues().stream().anyMatch(i -> i.isError() && i.message().toLowerCase().contains("thiếu file")));
    }

    @Test
    void detectsBadAnswerDuplicateOptionsAndWrongType() throws IOException {
        SampleExamFactory.Sample s = SampleExamFactory.full(Track.TOPIK_I, 1);
        byte[] bad = edit(s.excel(), sh -> {
            set(sh, 1, "answer", "5");
            set(sh, 2, "opt_2", sh.getRow(2).getCell(col("opt_1")).getStringCellValue());
            set(sh, 3, "type_code", "I-N9");
        });
        ExamImportValidator.Result r = validate(bad, Track.TOPIK_I, media(s));
        assertTrue(r.errorCount() >= 3, () -> r.issues().toString());
        assertTrue(r.issues().stream().filter(ImportIssue::isError).anyMatch(i -> Integer.valueOf(2).equals(i.row())));
        assertTrue(r.issues().stream().filter(ImportIssue::isError).anyMatch(i -> Integer.valueOf(3).equals(i.row())));
        assertTrue(r.issues().stream().filter(ImportIssue::isError).anyMatch(i -> Integer.valueOf(4).equals(i.row())));
    }

    @Test
    void groupedQuestionsMustShareGroupId() throws IOException {
        SampleExamFactory.Sample s = SampleExamFactory.full(Track.TOPIK_I, 1);
        // TOPIK I Nghe câu 25–26 (I-N8) nằm ở dòng 26–27 của sheet.
        byte[] bad = edit(s.excel(), sh -> set(sh, 26, "group_id", "KHAC"));
        ExamImportValidator.Result r = validate(bad, Track.TOPIK_I, media(s));
        assertTrue(hasError(r, "group_id"), () -> r.issues().toString());
    }

    @Test
    void fullExamMustHaveEveryQuestion() throws IOException {
        SampleExamFactory.Sample s = SampleExamFactory.full(Track.TOPIK_I, 1);
        byte[] bad = edit(s.excel(), sh -> sh.removeRow(sh.getRow(70)));
        ExamImportValidator.Result r = validate(bad, Track.TOPIK_I, media(s));
        assertTrue(r.errorCount() > 0);
    }

    @Test
    void duplicateAgainstBankIsReported() throws IOException {
        SampleExamFactory.Sample s = SampleExamFactory.full(Track.TOPIK_I, 1);
        ParsedWorkbook wb = ExamWorkbookParser.parse(new ByteArrayInputStream(s.excel()));
        ExamImportValidator.Result r = ExamImportValidator.validate(wb, new ExamImportValidator.Context(Track.TOPIK_I, true, false, media(s),
                hashes -> {
                    Map<String, String> m = new HashMap<>();
                    hashes.stream().limit(1).forEach(h -> m.put(h, "E0001"));
                    return m;
                }));
        assertTrue(r.issues().stream().anyMatch(i -> i.message().contains("E0001")), () -> r.issues().toString());
    }
}
