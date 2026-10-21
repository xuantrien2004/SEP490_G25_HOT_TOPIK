package com.hottopik.service.exam;

import com.hottopik.entity.exam.Track;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** File mẫu nhập đề: QUESTIONS, WRITING, HUONG_DAN (quy tắc cột) và DANG_CAU (bảng 42 dạng). */
public final class ExamTemplateBuilder {

    private ExamTemplateBuilder() {
    }

    public static byte[] build() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle wrap = wb.createCellStyle();
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(VerticalAlignment.TOP);

            Sheet q = wb.createSheet(ParsedWorkbook.SHEET_QUESTIONS);
            writeRow(q, 0, ExamWorkbookParser.QUESTION_COLUMNS.toArray(String[]::new), header);
            writeRow(q, 1, new String[]{"21", "LISTENING", "II-N6", "G21", "listening_21.mp3",
                    "남자: 회의 자료 복사했어요? 여자: 아직이요. 지금 하려고요.", "", "",
                    "남자가 무엇을 하고 있는지 고르십시오.", "회의를 준비하고 있다", "자료를 찾고 있다",
                    "복사를 부탁하고 있다", "회의실을 예약하고 있다", "2", "Người nam đang nhờ…", "c1", "", "", ""}, wrap);
            writeRow(q, 2, new String[]{"22", "LISTENING", "II-N6", "G21", "listening_21.mp3", "", "", "",
                    "들은 내용과 같은 것을 고르십시오.", "여자는 자료를 복사했다", "남자는 회의에 늦었다",
                    "여자는 곧 복사를 할 것이다", "남자는 자료를 잃어버렸다", "3", "", "", "", "", ""}, wrap);
            writeRow(q, 3, new String[]{"1", "READING", "II-D1", "", "", "", "", "",
                    "숙제를 다 (    ) 게임을 할 수 있어요.", "해야", "하느라고", "하던데", "할수록", "1",
                    "-아/어야: phải… thì mới…", "c6", "", "", ""}, wrap);
            for (int i = 0; i < ExamWorkbookParser.QUESTION_COLUMNS.size(); i++) q.setColumnWidth(i, 18 * 256);

            Sheet w = wb.createSheet(ParsedWorkbook.SHEET_WRITING);
            writeRow(w, 0, ExamWorkbookParser.WRITING_COLUMNS.toArray(String[]::new), header);
            writeRow(w, 1, new String[]{"51", "초대합니다. 우리 동아리에서 ( ㉠ ). 시간이 있으신 분은 ( ㉡ ).",
                    "작은 음악회를 엽니다|음악회를 엽니다", "꼭 와 주십시오|와 주시기 바랍니다", "",
                    "㉠ 작은 음악회를 엽니다 ㉡ 꼭 와 주십시오", "Mỗi chỗ trống 5 điểm"}, wrap);
            writeRow(w, 2, new String[]{"52", "사람들은 보통 ( ㉠ ). 그러나 ( ㉡ ).",
                    "…", "…", "", "㉠ … ㉡ …", ""}, wrap);
            writeRow(w, 3, new String[]{"53", "다음은 '여가 활동'에 대한 조사 결과이다. 이 내용을 200~300자의 글로 쓰십시오.",
                    "", "", "[[\"인터넷\",42],[\"운동\",23],[\"독서\",15]]", "조사 결과에 따르면…", "Nội dung · Bố cục · Ngôn ngữ"}, wrap);
            writeRow(w, 4, new String[]{"54", "현대 사회에서 인공지능의 역할에 대해 자신의 생각을 쓰십시오.",
                    "", "", "", "인공지능은…", ""}, wrap);
            for (int i = 0; i < ExamWorkbookParser.WRITING_COLUMNS.size(); i++) w.setColumnWidth(i, 28 * 256);

            Sheet guide = wb.createSheet("HUONG_DAN");
            writeRow(guide, 0, new String[]{"Cột", "Bắt buộc", "Quy tắc"}, header);
            List<String[]> rules = List.of(
                    new String[]{"no", "Có", "TOPIK I: Nghe 1–30, Đọc 31–70. TOPIK II: Nghe 1–50, Đọc 1–50 (phân biệt bằng skill). Không trùng (skill, no)."},
                    new String[]{"skill", "Có", "LISTENING hoặc READING. Câu Viết nhập ở sheet WRITING."},
                    new String[]{"type_code", "Không", "Hệ thống tự gán theo (track, skill, no). Nếu điền phải khớp, xem sheet DANG_CAU."},
                    new String[]{"group_id", "Khi dạng 2 câu/bài", "Các câu dùng chung một đoạn có cùng group_id, ví dụ II-N6 câu 21–22 cùng G21."},
                    new String[]{"audio_file", "Có với LISTENING", "Tên file mp3 đã tải ở bước 2; dùng chung cho cả group."},
                    new String[]{"script_ko", "Có với LISTENING", "Lời thoại; chỉ cần ở một dòng trong group."},
                    new String[]{"passage_ko", "Có với READING", "Đoạn văn; chỉ cần ở một dòng trong group. Dạng II-D1, II-D2, I-D1, I-D2 không cần."},
                    new String[]{"image_file", "Dạng tranh/biểu đồ", "Bắt buộc với I-N5, II-N1; có thể thay đoạn văn ở dạng thông báo/biểu đồ."},
                    new String[]{"prompt_ko", "Có", "Câu hỏi."},
                    new String[]{"opt_1 … opt_4", "Có", "Đủ 4 phương án, không trùng nhau."},
                    new String[]{"answer", "Có", "Một số 1–4."},
                    new String[]{"explanation_vi", "Không", "Giải thích tiếng Việt (trống thì cảnh báo)."},
                    new String[]{"cause_hint", "Không", "c1–c10, ghi đè nguyên nhân sai mặc định."},
                    new String[]{"vocab_ids, grammar_ids", "Không", "Danh sách mã từ vựng / mẫu ngữ pháp, ngăn bằng dấu phẩy."},
                    new String[]{"purpose", "Không", "EXAM hoặc PRACTICE. Mặc định EXAM. Nguồn dành cho đánh giá luôn là ASSESSMENT."},
                    new String[]{"WRITING.keys_1, keys_2", "Câu 51–52", "Các đáp án chấp nhận cho ㉠, ㉡, ngăn bằng |"},
                    new String[]{"WRITING.chart_json", "Câu 53", "[[\"nhãn\", số], …] hoặc tên file ảnh biểu đồ đã tải."},
                    new String[]{"WRITING.model_answer", "Có", "Bài mẫu / đáp án chuẩn."});
            for (int i = 0; i < rules.size(); i++) writeRow(guide, i + 1, rules.get(i), wrap);
            guide.setColumnWidth(0, 26 * 256);
            guide.setColumnWidth(1, 20 * 256);
            guide.setColumnWidth(2, 100 * 256);

            Sheet types = wb.createSheet("DANG_CAU");
            writeRow(types, 0, new String[]{"type_code", "Track", "Kỹ năng", "Câu", "Tên dạng", "Vùng cấp", "Số câu", "Cách chia bài"}, header);
            int r = 1;
            for (Track t : Track.values()) {
                for (QuestionTypeDef d : QuestionTypeCatalog.forTrack(t)) {
                    writeRow(types, r++, new String[]{d.code(), t.getLabel(), d.skill().getLabel(), d.rangeLabel(), d.name(),
                            d.zoneLabel(), String.valueOf(d.requiredCount()), d.groupLabel()}, null);
                }
            }
            for (int i = 0; i < 8; i++) types.setColumnWidth(i, (i == 4 ? 44 : 14) * 256);

            wb.write(out);
            return out.toByteArray();
        }
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
