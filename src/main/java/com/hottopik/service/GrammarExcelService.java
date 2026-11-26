package com.hottopik.service;

import com.hottopik.dto.GrammarExampleForm;
import com.hottopik.dto.GrammarForm;
import com.hottopik.entity.Grammar;
import com.hottopik.entity.GrammarGroup;
import com.hottopik.repository.GrammarGroupRepository;
import com.hottopik.repository.GrammarRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Tải file mẫu và import Excel cho Kho ngữ pháp, theo 2 bước giống module Hán tự:
 * preview() đọc và kiểm tra từng dòng (không ghi DB), confirmImport() lưu các dòng hợp lệ dưới dạng Bản nháp.
 * File không chứa cấp/nhóm: mọi dòng được nhập vào đúng nhóm đang mở trên màn hình Content Manager.
 */
@Service
public class GrammarExcelService {

    static final String[] HEADERS = {
            "Mẫu ngữ pháp (*)", "Bản dịch ngắn (*)", "Ý nghĩa chi tiết", "Giải thích / Cấu trúc",
            "Ghi chú", "Quan trọng (Có/Không)", "Ví dụ tiếng Hàn", "Dịch ví dụ tiếng Việt"
    };
    private static final int MAX_ROWS = 500;
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private final GrammarRepository grammarRepository;
    private final GrammarGroupRepository groupRepository;
    private final GrammarService grammarService;

    public GrammarExcelService(GrammarRepository grammarRepository, GrammarGroupRepository groupRepository,
                               GrammarService grammarService) {
        this.grammarRepository = grammarRepository;
        this.groupRepository = groupRepository;
        this.grammarService = grammarService;
    }

    /** Một dòng đã đọc từ Excel kèm kết quả kiểm tra; JS hiển thị và gửi lại các dòng hợp lệ khi xác nhận. */
    public record ImportRow(int rowNo, String pattern, String translation, String meaning, String explanation,
                            String notes, boolean frequent, String exampleKo, String exampleVi,
                            boolean valid, String message) {}

    public record PreviewResult(String groupName, int level, int validCount, int errorCount, List<ImportRow> rows) {}

    public record ConfirmResult(int created, List<String> errors) {}

    // ======================= FILE MẪU =======================

    /** Tạo file Excel mẫu (sheet NguPhap có 2 dòng ví dụ + sheet HuongDan) cho nhóm ngữ pháp được chọn. */
    @Transactional(readOnly = true)
    public byte[] buildTemplate(Integer groupId) {
        GrammarGroup group = requireGroup(groupId);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font bold = wb.createFont();
            bold.setBold(true);
            bold.setColor(IndexedColors.WHITE.getIndex());
            CellStyle header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setWrapText(true);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            CellStyle wrap = wb.createCellStyle();
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(VerticalAlignment.TOP);

            Sheet sheet = wb.createSheet("NguPhap");
            Row h = sheet.createRow(0);
            h.setHeightInPoints(32);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = h.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(header);
            }
            String[][] samples = {
                    {"-아서/어서", "Vì... nên...", "Diễn tả nguyên nhân, lý do dẫn đến kết quả ở vế sau.",
                            "V/A + 아서/어서", "Không dùng với câu mệnh lệnh, rủ rê.", "Có",
                            "배가 아파서 병원에 갔어요.", "Vì đau bụng nên tôi đã đến bệnh viện."},
                    {"-고 싶다", "Muốn...", "Diễn tả mong muốn của người nói.", "V + 고 싶다", "", "Không",
                            "한국에 가고 싶어요.", "Tôi muốn đi Hàn Quốc."}
            };
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < samples[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(samples[r][c]);
                    cell.setCellStyle(wrap);
                }
            }
            int[] widths = {18, 22, 40, 24, 30, 14, 34, 38};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
            sheet.createFreezePane(0, 1);

            Sheet guide = wb.createSheet("HuongDan");
            String[] lines = {
                    "HƯỚNG DẪN NHẬP NGỮ PHÁP TỪ EXCEL",
                    "File này dùng để nhập vào nhóm: " + group.getNameVi() + " (TOPIK cấp " + group.getLevel() + ").",
                    "1. Nhập dữ liệu ở sheet \"NguPhap\" từ dòng 2. Giữ nguyên dòng tiêu đề và thứ tự cột.",
                    "2. Cột có dấu (*) là bắt buộc. Mẫu ngữ pháp tối đa 100 ký tự, Bản dịch ngắn tối đa 200 ký tự, Ý nghĩa tối đa 1000 ký tự.",
                    "3. Cột \"Quan trọng\": ghi Có hoặc Không (để trống = Không).",
                    "4. Ví dụ tiếng Hàn và Dịch ví dụ tiếng Việt phải điền cả hai hoặc bỏ trống cả hai (mỗi câu tối đa 500 ký tự).",
                    "5. Mẫu ngữ pháp trùng với mẫu đã có trong nhóm, hoặc trùng nhau trong file, sẽ bị bỏ qua.",
                    "6. Mọi dòng hợp lệ được lưu ở trạng thái Bản nháp. Tối đa " + MAX_ROWS + " dòng mỗi lần nhập.",
                    "7. Xóa 2 dòng ví dụ mẫu trước khi nhập nếu không muốn thêm chúng vào hệ thống."
            };
            for (int i = 0; i < lines.length; i++) guide.createRow(i).createCell(0).setCellValue(lines[i]);
            guide.setColumnWidth(0, 120 * 256);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được file mẫu Excel.", e);
        }
    }

    // ======================= BƯỚC 1: XEM TRƯỚC =======================

    /** Đọc file Excel và kiểm tra từng dòng (thiếu dữ liệu, trùng trong nhóm / trong file, sai định dạng); chưa lưu gì vào database. */
    @Transactional(readOnly = true)
    public PreviewResult preview(MultipartFile file, Integer groupId) {
        GrammarGroup group = requireGroup(groupId);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Vui lòng chọn file Excel.");
        String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls"))
            throw new IllegalArgumentException("Chỉ hỗ trợ file Excel .xlsx hoặc .xls.");
        if (file.getSize() > MAX_FILE_BYTES) throw new IllegalArgumentException("File quá lớn (tối đa 5MB).");

        Set<String> existing = new HashSet<>();
        for (Grammar g : grammarRepository.findByGrammarGroup_IdOrderBySortOrderAscIdAsc(groupId))
            existing.add(key(g.getPattern()));

        List<ImportRow> rows = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();
        DataFormatter fmt = new DataFormatter();
        try (InputStream is = file.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            checkHeader(sheet.getRow(0), fmt);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                String[] v = new String[HEADERS.length];
                boolean blank = true;
                for (int c = 0; c < HEADERS.length; c++) {
                    v[c] = row == null ? "" : fmt.formatCellValue(row.getCell(c)).trim();
                    if (!v[c].isEmpty()) blank = false;
                }
                if (blank) continue;
                if (rows.size() >= MAX_ROWS)
                    throw new IllegalArgumentException("File có quá " + MAX_ROWS + " dòng dữ liệu. Vui lòng chia nhỏ file.");
                rows.add(validateRow(r + 1, v, existing, seenInFile));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Không đọc được file Excel. Hãy dùng đúng file mẫu (.xlsx).");
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("File không có dòng dữ liệu nào (dữ liệu bắt đầu từ dòng 2).");

        int valid = (int) rows.stream().filter(ImportRow::valid).count();
        return new PreviewResult(group.getNameVi(), group.getLevel(), valid, rows.size() - valid, rows);
    }

    private void checkHeader(Row header, DataFormatter fmt) {
        String first = header == null ? "" : fmt.formatCellValue(header.getCell(0)).trim().toLowerCase(Locale.ROOT);
        String second = header == null ? "" : fmt.formatCellValue(header.getCell(1)).trim().toLowerCase(Locale.ROOT);
        if (!first.startsWith("mẫu ngữ pháp") || !second.startsWith("bản dịch"))
            throw new IllegalArgumentException("Sai định dạng file: dòng 1 phải là tiêu đề của file mẫu "
                    + "(\"Mẫu ngữ pháp (*)\", \"Bản dịch ngắn (*)\", ...). Hãy tải file mẫu và nhập lại.");
    }

    private ImportRow validateRow(int rowNo, String[] v, Set<String> existing, Set<String> seenInFile) {
        String pattern = v[0], translation = v[1], meaning = v[2], explanation = v[3], notes = v[4];
        String exKo = v[6], exVi = v[7];
        List<String> errors = new ArrayList<>();
        if (pattern.isEmpty()) errors.add("Thiếu mẫu ngữ pháp");
        else if (pattern.length() > 100) errors.add("Mẫu ngữ pháp quá 100 ký tự");
        if (translation.isEmpty()) errors.add("Thiếu bản dịch ngắn");
        else if (translation.length() > 200) errors.add("Bản dịch quá 200 ký tự");
        if (meaning.length() > 1000) errors.add("Ý nghĩa quá 1000 ký tự");
        if (explanation.length() > 4000) errors.add("Giải thích quá 4000 ký tự");
        if (notes.length() > 4000) errors.add("Ghi chú quá 4000 ký tự");
        Boolean frequent = parseYesNo(v[5]);
        if (frequent == null) errors.add("Cột Quan trọng chỉ nhận Có hoặc Không");
        if (exKo.isEmpty() != exVi.isEmpty()) errors.add("Ví dụ phải có đủ câu tiếng Hàn và câu dịch");
        if (exKo.length() > 500 || exVi.length() > 500) errors.add("Câu ví dụ quá 500 ký tự");
        if (!pattern.isEmpty()) {
            String k = key(pattern);
            if (existing.contains(k)) errors.add("Đã có trong nhóm này");
            else if (!seenInFile.add(k)) errors.add("Trùng với dòng phía trên trong file");
        }
        boolean ok = errors.isEmpty();
        return new ImportRow(rowNo, pattern, translation, meaning, explanation, notes,
                Boolean.TRUE.equals(frequent), exKo, exVi, ok, ok ? "Hợp lệ" : String.join("; ", errors));
    }

    // ======================= BƯỚC 2: XÁC NHẬN LƯU =======================

    /**
     * Lưu lại các dòng hợp lệ do trình duyệt gửi về. Không tin dữ liệu client: kiểm tra trùng lần nữa,
     * ép nhóm/cấp theo groupId và trạng thái DRAFT; mỗi dòng lưu qua GrammarService.saveGrammar (validate đầy đủ).
     */
    public ConfirmResult confirmImport(Integer groupId, List<ImportRow> rows) {
        GrammarGroup group = requireGroup(groupId);
        if (rows == null || rows.isEmpty()) throw new IllegalArgumentException("Không có dòng hợp lệ nào để lưu.");
        if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("Tối đa " + MAX_ROWS + " dòng mỗi lần nhập.");

        List<Grammar> current = grammarRepository.findByGrammarGroup_IdOrderBySortOrderAscIdAsc(groupId);
        Set<String> existing = new HashSet<>();
        int nextOrder = 0;
        for (Grammar g : current) {
            existing.add(key(g.getPattern()));
            if (g.getSortOrder() != null) nextOrder = Math.max(nextOrder, g.getSortOrder());
        }

        int created = 0;
        List<String> errors = new ArrayList<>();
        for (ImportRow row : rows) {
            String pattern = Objects.toString(row.pattern(), "").trim();
            if (!existing.add(key(pattern))) {
                errors.add("Dòng " + row.rowNo() + ": \"" + pattern + "\" đã có trong nhóm, bỏ qua.");
                continue;
            }
            GrammarForm form = new GrammarForm();
            form.setGroupId(group.getId());
            form.setLevel(group.getLevel());
            form.setPattern(pattern);
            form.setTranslation(row.translation());
            form.setMeaning(row.meaning());
            form.setExplanation(row.explanation());
            form.setNotes(row.notes());
            form.setIsFrequent(row.frequent());
            form.setStatus("DRAFT");
            form.setSortOrder((short) Math.min(Short.MAX_VALUE, ++nextOrder));
            List<GrammarExampleForm> examples = new ArrayList<>();
            if (row.exampleKo() != null && !row.exampleKo().isBlank()) {
                GrammarExampleForm ex = new GrammarExampleForm();
                ex.setSentenceKo(row.exampleKo());
                ex.setSentenceVi(row.exampleVi());
                ex.setSortOrder((short) 1);
                examples.add(ex);
            }
            form.setExamples(examples);
            try {
                grammarService.saveGrammar(form);
                created++;
            } catch (Exception e) {
                errors.add("Dòng " + row.rowNo() + ": " + e.getMessage());
            }
        }
        return new ConfirmResult(created, errors);
    }

    // ======================= TIỆN ÍCH =======================

    private GrammarGroup requireGroup(Integer groupId) {
        if (groupId == null) throw new IllegalArgumentException("Thiếu nhóm ngữ pháp cần nhập.");
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm ngữ pháp."));
    }

    private static String key(String pattern) {
        return pattern == null ? "" : pattern.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static Boolean parseYesNo(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty() || v.equals("không") || v.equals("khong") || v.equals("no") || v.equals("false") || v.equals("0"))
            return false;
        if (v.equals("có") || v.equals("co") || v.equals("x") || v.equals("yes") || v.equals("true") || v.equals("1")
                || v.equals("quan trọng"))
            return true;
        return null;
    }
}
