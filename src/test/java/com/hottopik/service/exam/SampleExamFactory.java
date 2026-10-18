package com.hottopik.service.exam;

import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Sinh một bộ đề đầy đủ hợp lệ (Excel + tên file media cần có) theo đúng QuestionTypeCatalog.
 * {@code variant} đổi nội dung để hai bộ đề không bị coi là trùng nhau.
 */
public final class SampleExamFactory {

    public record Sample(byte[] excel, Set<String> audioFiles, Set<String> imageFiles) {
    }

    private static final String[] WORDS = {"학교", "도서관", "시장", "병원", "회사", "공원", "식당", "우체국", "은행", "공항",
            "영화관", "백화점", "약국", "미술관", "체육관", "편의점", "기차역", "박물관", "서점", "카페"};

    private SampleExamFactory() {
    }

    public static Sample full(Track track, int variant) throws IOException {
        Set<String> audio = new TreeSet<>();
        Set<String> images = new TreeSet<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet qs = wb.createSheet(ParsedWorkbook.SHEET_QUESTIONS);
            header(qs, ExamWorkbookParser.QUESTION_COLUMNS);
            int r = 1;
            for (QuestionTypeCatalog.Section sec : QuestionTypeCatalog.sections(track)) {
                if (sec.skill() == Skill.WRITING) continue;
                for (int no = sec.firstNo(); no <= sec.lastNo(); no++) {
                    QuestionTypeDef t = QuestionTypeCatalog.resolve(track, sec.skill(), no).orElseThrow();
                    List<Integer> block = t.blockOf(no);
                    int first = block.get(0);
                    boolean listening = sec.skill() == Skill.LISTENING;
                    String tag = (listening ? "L" : "R") + first;
                    Map<String, String> v = new LinkedHashMap<>();
                    v.put("no", String.valueOf(no));
                    v.put("skill", sec.skill().name());
                    v.put("type_code", t.code());
                    if (block.size() > 1) v.put("group_id", "G" + tag);
                    if (listening) {
                        String file = "v" + variant + "_" + tag + ".mp3";
                        v.put("audio_file", file);
                        audio.add(file);
                        if (no == first) v.put("script_ko", "남자: 오늘 " + word(variant, first) + "에 가요? 여자: 네, 친구와 같이 가요. (" + variant + "-" + first + ")");
                    } else if (t.passageRequired() && no == first) {
                        v.put("passage_ko", "저는 주말마다 " + word(variant, first) + "에 갑니다. 그곳에서 책을 읽고 사람들을 만납니다. (" + variant + "-" + first + ")");
                    }
                    if (t.imageRequired()) {
                        String img = "v" + variant + "_" + tag + "_" + no + ".png";
                        v.put("image_file", img);
                        images.add(img);
                    }
                    v.put("prompt_ko", "[" + variant + "] " + sec.skill().getLabel() + " " + no + ": 알맞은 것을 고르십시오.");
                    for (int i = 1; i <= 4; i++) v.put("opt_" + i, word(variant + i, no) + " " + i + "번 (" + variant + "/" + no + ")");
                    v.put("answer", String.valueOf(1 + (no + variant) % 4));
                    v.put("explanation_vi", "Đáp án dựa vào chi tiết trong bài, câu " + no + ".");
                    v.put("cause_hint", "c" + (1 + no % 10));
                    v.put("purpose", "EXAM");
                    row(qs, r++, ExamWorkbookParser.QUESTION_COLUMNS, v);
                }
            }
            if (track == Track.TOPIK_II) {
                Sheet ws = wb.createSheet(ParsedWorkbook.SHEET_WRITING);
                header(ws, ExamWorkbookParser.WRITING_COLUMNS);
                row(ws, 1, ExamWorkbookParser.WRITING_COLUMNS, Map.of("no", "51",
                        "prompt_ko", "[" + variant + "] 우리 동아리에서 회원을 ( ㉠ ). 관심 있는 분은 ( ㉡ ).",
                        "keys_1", "모집합니다|찾습니다", "keys_2", "연락해 주십시오|신청해 주십시오",
                        "model_answer", "㉠ 모집합니다 ㉡ 연락해 주십시오", "rubric_note", "Mỗi ô 5 điểm"));
                row(ws, 2, ExamWorkbookParser.WRITING_COLUMNS, Map.of("no", "52",
                        "prompt_ko", "[" + variant + "] 사람들은 보통 ( ㉠ ). 그러나 실제로는 ( ㉡ ).",
                        "keys_1", "쉽다고 생각한다", "keys_2", "그렇지 않다",
                        "model_answer", "㉠ 쉽다고 생각한다 ㉡ 그렇지 않다", "rubric_note", "Mỗi ô 5 điểm"));
                row(ws, 3, ExamWorkbookParser.WRITING_COLUMNS, Map.of("no", "53",
                        "prompt_ko", "[" + variant + "] 다음 그래프를 보고 200~300자로 쓰십시오.",
                        "chart_json", "[[\"2015\", " + (20 + variant) + "], [\"2020\", 35], [\"2025\", 48]]",
                        "model_answer", "조사 결과 1인 가구 비율이 꾸준히 증가하였다…", "rubric_note", "Nội dung 7 · Tổ chức 7 · Ngôn ngữ 16"));
                row(ws, 4, ExamWorkbookParser.WRITING_COLUMNS, Map.of("no", "54",
                        "prompt_ko", "[" + variant + "] 인공지능이 청소년에게 미치는 영향에 대해 600~700자로 쓰십시오.",
                        "model_answer", "현대 사회에서 인공지능은…", "rubric_note", "Nội dung 12 · Tổ chức 12 · Ngôn ngữ 26"));
            }
            wb.write(out);
            return new Sample(out.toByteArray(), audio, images);
        }
    }

    /** Khoảng 2 giây im lặng: 77 khung MPEG-1 Layer III 128 kbps / 44.1 kHz (417 byte/khung, phần dữ liệu toàn 0). */
    public static byte[] fakeMp3() {
        int frameSize = 417;
        int frames = 77;
        byte[] b = new byte[frameSize * frames];
        for (int f = 0; f < frames; f++) {
            int o = f * frameSize;
            b[o] = (byte) 0xFF;
            b[o + 1] = (byte) 0xFB;
            b[o + 2] = (byte) 0x90;
            b[o + 3] = (byte) 0x64;
        }
        return b;
    }

    /** PNG 1×1. */
    public static byte[] fakePng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkqPtfDwAE/AH+dl1W6wAAAABJRU5ErkJggg==");
    }

    private static String word(int a, int b) {
        return WORDS[Math.floorMod(a * 7 + b, WORDS.length)];
    }

    private static void header(Sheet s, List<String> cols) {
        Row h = s.createRow(0);
        for (int i = 0; i < cols.size(); i++) h.createCell(i).setCellValue(cols.get(i));
    }

    private static void row(Sheet s, int idx, List<String> cols, Map<String, String> values) {
        Row row = s.createRow(idx);
        for (int i = 0; i < cols.size(); i++) {
            String v = values.get(cols.get(i));
            if (v != null) row.createCell(i).setCellValue(v);
        }
    }
}
