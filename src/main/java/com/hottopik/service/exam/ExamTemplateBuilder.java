package com.hottopik.service.exam;

import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** File mẫu nhập đề: mẫu theo từng kỹ năng hoặc toàn bộ đề (mỗi kỹ năng một sheet riêng). Cột tiếng Việt chuẩn. */
public final class ExamTemplateBuilder {

    public static final String[] HEADERS_LISTENING = {
            "Câu", "Mã dạng câu", "Tên dạng câu", "Phụ đề",
            "Câu hỏi/Đề bài", "Phương án 1", "Phương án 2", "Phương án 3", "Phương án 4",
            "Đáp án đúng", "Giải thích"
    };

    public static final String[] HEADERS_READING = {
            "Câu", "Mã dạng câu", "Tên dạng câu", "Đoạn văn chung",
            "Câu hỏi/Đề bài", "Phương án 1", "Phương án 2", "Phương án 3", "Phương án 4",
            "Đáp án đúng", "Giải thích"
    };

    public static final String[] HEADERS_WRITING = {
            "Câu", "Mã dạng câu", "Tên dạng câu", "Đoạn văn dùng chung",
            "Câu hỏi/Đề bài", "Đáp án chấp nhận chỗ ㉠", "Đáp án chấp nhận chỗ ㉡", "Giải thích"
    };

    public static final String DANG_CAU_NOTE = "(*) GHI CHÚ: Cột 'Vị trí câu' và 'Số câu yêu cầu' là mặc định, có thể thay đổi tuỳ vào đề. Nếu đề không có gì khác biệt thì phải theo thông số của 2 cột này.";

    private ExamTemplateBuilder() {
    }

    private static CellStyle createHeaderStyle(Workbook wb) {
        CellStyle header = wb.createCellStyle();
        Font bold = wb.createFont();
        bold.setBold(true);
        header.setFont(bold);
        header.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setBorderBottom(BorderStyle.THIN);
        header.setBorderTop(BorderStyle.THIN);
        header.setBorderLeft(BorderStyle.THIN);
        header.setBorderRight(BorderStyle.THIN);
        return header;
    }

    private static CellStyle createNoteStyle(Workbook wb) {
        CellStyle noteStyle = wb.createCellStyle();
        Font noteFont = wb.createFont();
        noteFont.setItalic(true);
        noteFont.setBold(true);
        noteFont.setColor(IndexedColors.DARK_RED.getIndex());
        noteStyle.setFont(noteFont);
        noteStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        noteStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        noteStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        noteStyle.setBorderBottom(BorderStyle.THIN);
        noteStyle.setBorderTop(BorderStyle.THIN);
        noteStyle.setBorderLeft(BorderStyle.THIN);
        noteStyle.setBorderRight(BorderStyle.THIN);
        return noteStyle;
    }

    private static CellStyle createWrapStyle(Workbook wb) {
        CellStyle wrap = wb.createCellStyle();
        wrap.setWrapText(true);
        wrap.setVerticalAlignment(VerticalAlignment.TOP);
        return wrap;
    }

    private static CellStyle createCenterStyle(Workbook wb) {
        CellStyle centerWrap = wb.createCellStyle();
        centerWrap.setWrapText(true);
        centerWrap.setVerticalAlignment(VerticalAlignment.TOP);
        centerWrap.setAlignment(HorizontalAlignment.CENTER);
        return centerWrap;
    }

    private static CellStyle createCodeStyle(Workbook wb, CellStyle centerWrap) {
        CellStyle codeStyle = wb.createCellStyle();
        codeStyle.cloneStyleFrom(centerWrap);
        Font codeFont = wb.createFont();
        codeFont.setBold(true);
        codeStyle.setFont(codeFont);
        return codeStyle;
    }

    private static void setColumnWidths(Sheet sheet, Skill skill) {
        if (skill == Skill.LISTENING) {
            sheet.setColumnWidth(0, 8 * 256);
            sheet.setColumnWidth(1, 15 * 256);
            sheet.setColumnWidth(2, 35 * 256);
            sheet.setColumnWidth(3, 45 * 256);
            sheet.setColumnWidth(4, 35 * 256);
            sheet.setColumnWidth(5, 25 * 256);
            sheet.setColumnWidth(6, 25 * 256);
            sheet.setColumnWidth(7, 25 * 256);
            sheet.setColumnWidth(8, 25 * 256);
            sheet.setColumnWidth(9, 14 * 256);
            sheet.setColumnWidth(10, 35 * 256);
        } else if (skill == Skill.READING) {
            sheet.setColumnWidth(0, 8 * 256);
            sheet.setColumnWidth(1, 15 * 256);
            sheet.setColumnWidth(2, 35 * 256);
            sheet.setColumnWidth(3, 45 * 256);
            sheet.setColumnWidth(4, 35 * 256);
            sheet.setColumnWidth(5, 25 * 256);
            sheet.setColumnWidth(6, 25 * 256);
            sheet.setColumnWidth(7, 25 * 256);
            sheet.setColumnWidth(8, 25 * 256);
            sheet.setColumnWidth(9, 14 * 256);
            sheet.setColumnWidth(10, 35 * 256);
        } else {
            sheet.setColumnWidth(0, 8 * 256);
            sheet.setColumnWidth(1, 15 * 256);
            sheet.setColumnWidth(2, 35 * 256);
            sheet.setColumnWidth(3, 45 * 256);
            sheet.setColumnWidth(4, 45 * 256);
            sheet.setColumnWidth(5, 35 * 256);
            sheet.setColumnWidth(6, 35 * 256);
            sheet.setColumnWidth(7, 35 * 256);
        }
    }

    public static byte[] buildSkillTemplate(Track track, Skill skill) throws IOException {
        if (track == Track.TOPIK_I && skill == Skill.WRITING) {
            throw new IllegalArgumentException("TOPIK I không có kỹ năng Viết.");
        }
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = createHeaderStyle(wb);
            CellStyle wrap = createWrapStyle(wb);
            CellStyle centerWrap = createCenterStyle(wb);
            CellStyle codeStyle = createCodeStyle(wb, centerWrap);

            String sheetName = switch (skill) {
                case LISTENING -> "NGHE";
                case READING -> "DOC";
                case WRITING -> "VIET";
            };

            Sheet mainSheet = wb.createSheet(sheetName);
            String[] headers = switch (skill) {
                case LISTENING -> HEADERS_LISTENING;
                case READING -> HEADERS_READING;
                case WRITING -> HEADERS_WRITING;
            };

            writeRow(mainSheet, 0, headers, header);

            QuestionTypeCatalog.Section section = QuestionTypeCatalog.section(track, skill)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy section cho " + track + " - " + skill));

            int rowIdx = 1;
            for (int no = section.firstNo(); no <= section.lastNo(); no++) {
                Row r = mainSheet.createRow(rowIdx++);
                for (int c = 0; c < headers.length; c++) {
                    Cell cell = r.createCell(c);
                    if (c == 0) {
                        cell.setCellValue(no);
                        cell.setCellStyle(centerWrap);
                    } else if (c == 1) {
                        cell.setCellValue("");
                        cell.setCellStyle(codeStyle);
                    } else if (c == 2) {
                        cell.setCellValue("");
                        cell.setCellStyle(wrap);
                    } else {
                        cell.setCellValue("");
                        cell.setCellStyle(wrap);
                    }
                }
            }

            setColumnWidths(mainSheet, skill);

            // Sheet 2: Danh sách dạng câu (DANG_CAU)
            Sheet typesSheet = wb.createSheet("DANG_CAU");
            CellStyle noteStyle = createNoteStyle(wb);

            Row noteRow = typesSheet.createRow(0);
            noteRow.setHeightInPoints(24);
            Cell noteCell = noteRow.createCell(0);
            noteCell.setCellValue(DANG_CAU_NOTE);
            noteCell.setCellStyle(noteStyle);
            for (int i = 1; i <= 5; i++) {
                Cell nc = noteRow.createCell(i);
                nc.setCellStyle(noteStyle);
            }
            typesSheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 5));

            String[] typeHeaders = {"Mã dạng câu", "Tên dạng câu", "Vị trí câu (*Mặc định)", "Đề bài mẫu tiếng Hàn", "Số câu yêu cầu (*Mặc định)", "Cấu trúc bài / Ghi chú"};
            writeRow(typesSheet, 1, typeHeaders, header);
            int tr = 2;
            for (QuestionTypeDef d : QuestionTypeCatalog.forTrackAndSkill(track, skill)) {
                Row r = typesSheet.createRow(tr++);
                Cell c0 = r.createCell(0); c0.setCellValue(d.code()); c0.setCellStyle(codeStyle);
                Cell c1 = r.createCell(1); c1.setCellValue(d.name()); c1.setCellStyle(wrap);
                Cell c2 = r.createCell(2); c2.setCellValue(d.rangeLabel()); c2.setCellStyle(centerWrap);
                Cell c3 = r.createCell(3); c3.setCellValue(d.instructionKo()); c3.setCellStyle(wrap);
                Cell c4 = r.createCell(4); c4.setCellValue(d.requiredCount()); c4.setCellStyle(centerWrap);
                Cell c5 = r.createCell(5); c5.setCellValue(d.groupLabel()); c5.setCellStyle(wrap);
            }
            typesSheet.setColumnWidth(0, 15 * 256);
            typesSheet.setColumnWidth(1, 35 * 256);
            typesSheet.setColumnWidth(2, 22 * 256);
            typesSheet.setColumnWidth(3, 50 * 256);
            typesSheet.setColumnWidth(4, 22 * 256);
            typesSheet.setColumnWidth(5, 25 * 256);

            wb.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] buildFullTemplate(Track track) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = createHeaderStyle(wb);
            CellStyle wrap = createWrapStyle(wb);
            CellStyle centerWrap = createCenterStyle(wb);
            CellStyle codeStyle = createCodeStyle(wb, centerWrap);

            List<QuestionTypeCatalog.Section> sections = QuestionTypeCatalog.sections(track);
            for (QuestionTypeCatalog.Section section : sections) {
                Skill skill = section.skill();
                String sheetName = switch (skill) {
                    case LISTENING -> "NGHE";
                    case READING -> "DOC";
                    case WRITING -> "VIET";
                };
                String[] headers = switch (skill) {
                    case LISTENING -> HEADERS_LISTENING;
                    case READING -> HEADERS_READING;
                    case WRITING -> HEADERS_WRITING;
                };

                Sheet sheet = wb.createSheet(sheetName);
                writeRow(sheet, 0, headers, header);

                int rowIdx = 1;
                for (int no = section.firstNo(); no <= section.lastNo(); no++) {
                    Row r = sheet.createRow(rowIdx++);
                    for (int c = 0; c < headers.length; c++) {
                        Cell cell = r.createCell(c);
                        if (c == 0) {
                            cell.setCellValue(no);
                            cell.setCellStyle(centerWrap);
                        } else if (c == 1) {
                            cell.setCellValue("");
                            cell.setCellStyle(codeStyle);
                        } else if (c == 2) {
                            cell.setCellValue("");
                            cell.setCellStyle(wrap);
                        } else {
                            cell.setCellValue("");
                            cell.setCellStyle(wrap);
                        }
                    }
                }
                setColumnWidths(sheet, skill);
            }

            // Sheet DANG_CAU: Danh sách toàn bộ dạng câu của track
            Sheet typesSheet = wb.createSheet("DANG_CAU");
            CellStyle noteStyle = createNoteStyle(wb);

            Row noteRow = typesSheet.createRow(0);
            noteRow.setHeightInPoints(24);
            Cell noteCell = noteRow.createCell(0);
            noteCell.setCellValue(DANG_CAU_NOTE);
            noteCell.setCellStyle(noteStyle);
            for (int i = 1; i <= 6; i++) {
                Cell nc = noteRow.createCell(i);
                nc.setCellStyle(noteStyle);
            }
            typesSheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 6));

            String[] typeHeaders = {"Mã dạng câu", "Kỹ năng", "Tên dạng câu", "Vị trí câu (*Mặc định)", "Đề bài mẫu tiếng Hàn", "Số câu yêu cầu (*Mặc định)", "Cấu trúc bài / Ghi chú"};
            writeRow(typesSheet, 1, typeHeaders, header);
            int tr = 2;
            for (QuestionTypeDef d : QuestionTypeCatalog.forTrack(track)) {
                Row r = typesSheet.createRow(tr++);
                Cell c0 = r.createCell(0); c0.setCellValue(d.code()); c0.setCellStyle(codeStyle);
                Cell c1 = r.createCell(1); c1.setCellValue(d.skill().getLabel()); c1.setCellStyle(wrap);
                Cell c2 = r.createCell(2); c2.setCellValue(d.name()); c2.setCellStyle(wrap);
                Cell c3 = r.createCell(3); c3.setCellValue(d.rangeLabel()); c3.setCellStyle(centerWrap);
                Cell c4 = r.createCell(4); c4.setCellValue(d.instructionKo()); c4.setCellStyle(wrap);
                Cell c5 = r.createCell(5); c5.setCellValue(d.requiredCount()); c5.setCellStyle(centerWrap);
                Cell c6 = r.createCell(6); c6.setCellValue(d.groupLabel()); c6.setCellStyle(wrap);
            }
            typesSheet.setColumnWidth(0, 15 * 256);
            typesSheet.setColumnWidth(1, 15 * 256);
            typesSheet.setColumnWidth(2, 35 * 256);
            typesSheet.setColumnWidth(3, 22 * 256);
            typesSheet.setColumnWidth(4, 50 * 256);
            typesSheet.setColumnWidth(5, 22 * 256);
            typesSheet.setColumnWidth(6, 25 * 256);

            wb.write(out);
            return out.toByteArray();
        }
    }

    public static byte[] build() throws IOException {
        return buildFullTemplate(Track.TOPIK_II);
    }

    private static void writeRow(Sheet sheet, int idx, String[] values, CellStyle style) {
        Row row = sheet.createRow(idx);
        for (int i = 0; i < values.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(values[i]);
            if (style != null) c.setCellStyle(style);
        }
    }
}
