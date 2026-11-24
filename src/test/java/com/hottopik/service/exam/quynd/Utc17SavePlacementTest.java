package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.PlacementLine;
import com.hottopik.repository.exam.PlacementLineRepository;
import com.hottopik.service.PlacementService;
import com.hottopik.service.exam.PlacementRules.Paper;
import com.hottopik.service.exam.QuestionTypeCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** UTC17 – PlacementService.save(String paperCode, Map&lt;String,Integer&gt; counts): Content Manager cấu hình đề kiểm tra trình độ. */
class Utc17SavePlacementTest extends QuyndTestSupport {

    @Autowired PlacementService placement;
    @Autowired PlacementLineRepository lines;

    private final String title = Paper.TOPIK_I.title();

    @BeforeEach
    void baseline() {
        placement.save("TOPIK_I", DEFAULT_PLACEMENT);
    }

    private List<PlacementLine> saved() {
        return lines.findByPaperCodeOrderBySortOrderAsc("TOPIK_I");
    }

    private static Map<String, Integer> with(Map<String, Integer> base, String code, int count) {
        Map<String, Integer> m = new HashMap<>(base);
        m.put(code, count);
        return m;
    }

    @Test @DisplayName("UTCID01 N: 2 câu mỗi dạng (10 Nghe + 10 Đọc) → lưu 10 dòng, tổng 20 câu")
    void utcid01() {
        assertEquals(10, saved().size());
        assertEquals(20, saved().stream().mapToInt(PlacementLine::getQuestionCount).sum());
    }

    @Test @DisplayName("UTCID02 N: dồn I-N1 = 10, I-D1 = 10 → chỉ còn 2 dòng, cấu hình cũ bị thay")
    void utcid02() {
        placement.save("TOPIK_I", Map.of("I-N1", 10, "I-D1", 10));
        assertEquals(List.of("I-N1", "I-D1"), saved().stream().map(PlacementLine::getTypeCode).toList());
    }

    @Test @DisplayName("UTCID03 B: dạng có số câu 0 không được lưu thành dòng")
    void utcid03() {
        Map<String, Integer> m = new HashMap<>(Map.of("I-N1", 10, "I-D1", 10));
        m.put("I-N2", 0);
        m.put("I-D2", 0);
        placement.save("TOPIK_I", m);
        assertEquals(2, saved().size());
    }

    @Test @DisplayName("UTCID04 B: I-N1 đúng 20 câu (trần của một dạng) → qua kiểm tra dạng, bị chặn vì phần Nghe 20 ≠ 10")
    void utcid04() {
        expectError("Phần Nghe của " + title + " phải đủ đúng 10 câu. Đang chọn 20 câu.",
                () -> placement.save("TOPIK_I", Map.of("I-N1", 20)));
    }

    @Test @DisplayName("UTCID05 A: Nghe chỉ 9 câu → báo lỗi, cấu hình cũ giữ nguyên")
    void utcid05() {
        expectError("Phần Nghe của " + title + " phải đủ đúng 10 câu. Đang chọn 9 câu.",
                () -> placement.save("TOPIK_I", with(DEFAULT_PLACEMENT, "I-N1", 1)));
        assertEquals(10, saved().size());
    }

    @Test @DisplayName("UTCID06 A: Đọc 11 câu")
    void utcid06() {
        expectError("Phần Đọc của " + title + " phải đủ đúng 10 câu. Đang chọn 11 câu.",
                () -> placement.save("TOPIK_I", with(DEFAULT_PLACEMENT, "I-D1", 3)));
    }

    @Test @DisplayName("UTCID07 A: một dạng 21 câu (vượt trần 20)")
    void utcid07() {
        String name = QuestionTypeCatalog.byCode("I-N1").orElseThrow().name();
        expectError("Số câu của dạng " + name + " phải từ 0 đến 20.",
                () -> placement.save("TOPIK_I", with(DEFAULT_PLACEMENT, "I-N1", 21)));
    }

    @Test @DisplayName("UTCID08 A: số câu âm (-1)")
    void utcid08() {
        String name = QuestionTypeCatalog.byCode("I-N1").orElseThrow().name();
        expectError("Số câu của dạng " + name + " phải từ 0 đến 20.",
                () -> placement.save("TOPIK_I", with(DEFAULT_PLACEMENT, "I-N1", -1)));
    }

    @Test @DisplayName("UTCID09 A: mã đề không hợp lệ (TOPIK_X)")
    void utcid09() {
        expectError("Bài kiểm tra không hợp lệ.", () -> placement.save("TOPIK_X", DEFAULT_PLACEMENT));
    }

    @Test @DisplayName("UTCID10 A: mã đề null")
    void utcid10() {
        expectError("Chưa chọn bài kiểm tra.", () -> placement.save(null, DEFAULT_PLACEMENT));
    }

    @Test @DisplayName("UTCID11 A: counts = null → coi như 0 câu Nghe")
    void utcid11() {
        expectError("Phần Nghe của " + title + " phải đủ đúng 10 câu. Đang chọn 0 câu.", () -> placement.save("TOPIK_I", null));
    }
}
