package com.hottopik.service;

import com.hottopik.dto.*;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ContentManagementService {

    private final Map<Long, ContentReportDTO> reports = new ConcurrentHashMap<>();
    private final Map<Long, GrammarDTO> grammars = new ConcurrentHashMap<>();
    private final Map<String, GrammarTypeDTO> grammarTypes = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // Seed Content Reports matching SEP490.sql
        reports.put(1L, new ContentReportDTO(1L, "Phạm Minh Anh", "learner@hottopik.com", "Đáp án bị đảo thứ tự", "Câu Đọc số 13 TOPIK II đáp án 2 và 3 bị đảo thứ tự logic.", "OPEN", 20, "2026-10-06 18:20"));
        reports.put(2L, new ContentReportDTO(2L, "Nguyễn Hoàng Nam", "viplearner@hottopik.com", "File audio bị rè", "Câu Nghe số 3 file mp3 bị rè ở giây thứ 8.", "OPEN", 10, "2026-10-07 08:45"));

        // Seed Grammar Types
        seedGrammarType("Nối mệnh đề chỉ nguyên nhân", 1, "Các cấu trúc liên từ nguyên nhân - kết quả cơ bản");
        seedGrammarType("Chỉ thời gian & Thứ tự hành động", 1, "Các cấu trúc diễn đạt thời điểm hoặc thứ tự trước sau");
        seedGrammarType("Nối mệnh đề tương phản", 2, "Các mẫu câu diễn đạt ý đối lập, nhượng bộ");
        seedGrammarType("Thể giả định & Điều kiện", 2, "Nếu... thì..., giả thiết tình huống");
        seedGrammarType("Dạng bổ ngữ & Định từ", 3, "Mẫu câu bổ nghĩa cho danh từ trong câu phức");
        seedGrammarType("Gián tiếp & Trích dẫn", 3, "Cách truyền đạt lại lời nói, suy nghĩ của người khác");
        seedGrammarType("Mức độ & Phạm vi", 4, "Cấu trúc so sánh, tỉ lệ, phạm vi giới hạn");
        seedGrammarType("Nhượng bộ & Đảo ngược", 5, "Cấu trúc văn viết học thuật nâng cao");
        seedGrammarType("Văn phong nghị luận chính thống", 6, "Ngữ pháp trình độ chuyên sâu TOPIK 6");

        // Seed Grammar Patterns
        addGrammarPattern(1L, "-아서/어서", "Vì... nên... (nguyên nhân trực tiếp, hiển nhiên)", "Nối mệnh đề chỉ nguyên nhân", "V/A + -아/어서", 1, "배가 아파서 병원에 갔어요.", "Vì đau bụng nên tôi đã đến bệnh viện.", "IMPORTANT", "ACTIVE");
        addGrammarPattern(2L, "-(으)니까", "Vì... nên... (nguyên nhân mang tính chủ quan, dùng được với mệnh lệnh, rủ rê)", "Nối mệnh đề chỉ nguyên nhân", "V/A + -(으)니까", 1, "날씨가 좋으니까 산책을 합시다.", "Vì thời tiết đẹp nên chúng ta đi dạo nhé.", "IMPORTANT", "ACTIVE");
        addGrammarPattern(3L, "-기 때문에", "Bởi vì... (nhấn mạnh lý do khách quan, văn phong trang trọng)", "Nối mệnh đề chỉ nguyên nhân", "V/A + -기 때문에; N + 때문에", 1, "비가 오기 때문에 길이 미끄럽습니다.", "Bởi vì trời mưa nên đường trơn trượt.", "NORMAL", "ACTIVE");

        addGrammarPattern(4L, "-(으)ㄹ 때", "Khi, vào lúc... (chỉ thời điểm hành động diễn ra)", "Chỉ thời gian & Thứ tự hành động", "V/A + -(으)ㄹ 때", 1, "한국에 갈 때 비행기를 탔어요.", "Khi sang Hàn Quốc tôi đã đi máy bay.", "IMPORTANT", "ACTIVE");
        addGrammarPattern(5L, "-기 전에", "Trước khi... (hành động xảy ra trước)", "Chỉ thời gian & Thứ tự hành động", "V + -기 전에; N + 전에", 1, "밥을 먹기 전에 손을 씻으세요.", "Trước khi ăn cơm hãy rửa tay.", "NORMAL", "ACTIVE");
        addGrammarPattern(6L, "-(으)ㄴ 후에", "Sau khi... (hành động diễn ra sau)", "Chỉ thời gian & Thứ tự hành động", "V + -(으)ㄴ 후에; N + 후에", 1, "수업이 끝난 후에 만나요.", "Gặp nhau sau khi tan học nhé.", "IMPORTANT", "ACTIVE");

        addGrammarPattern(7L, "-지만", "Nhưng... (nối hai vế đối lập trực tiếp)", "Nối mệnh đề tương phản", "V/A + -지만", 2, "한국어는 어렵지만 재미있어요.", "Tiếng Hàn khó nhưng mà thú vị.", "IMPORTANT", "ACTIVE");
        addGrammarPattern(8L, "-(으)ㄴ/는데", "Nhưng, trong khi, mở đầu bối cảnh...", "Nối mệnh đề tương phản", "V + -는데; A + -(으)ㄴ데", 2, "가게에 갔는데 문이 닫혀 있었어요.", "Tôi đã đến cửa hàng nhưng cửa lại đóng.", "IMPORTANT", "ACTIVE");

        addGrammarPattern(9L, "-(으)면", "Nếu... thì... (điều kiện đơn thuần)", "Thể giả định & Điều kiện", "V/A + -(으)면", 2, "시간이 있으면 같이 영화를 봐요.", "Nếu có thời gian thì cùng xem phim nhé.", "IMPORTANT", "ACTIVE");
        addGrammarPattern(10L, "-(으)려면", "Nếu muốn... thì phải...", "Thể giả định & Điều kiện", "V + -(으)려면", 2, "시험에 합격하려면 열심히 공부해야 해요.", "Nếu muốn đỗ kỳ thi thì phải học chăm chỉ.", "IMPORTANT", "ACTIVE");

        addGrammarPattern(11L, "-다고 하다", "Nghe nói là, bảo là... (trích dẫn câu trần thuật)", "Gián tiếp & Trích dẫn", "V + -ㄴ/는다고 하다; A + -다고 하다", 3, "민수 씨가 내일 한국에 간다고 해요.", "Nghe nói anh Minsoo ngày mai sẽ đi Hàn Quốc.", "IMPORTANT", "ACTIVE");
        addGrammarPattern(12L, "-자마자", "Ngay sau khi... thì...", "Chỉ thời gian & Thứ tự hành động", "V + -자마자", 2, "집에 도착하자마자 잠이 들었어요.", "Vừa về đến nhà là tôi đã ngủ thiếp đi.", "NORMAL", "ACTIVE");
    }

    private void seedGrammarType(String name, int level, String desc) {
        String key = level + "_" + name;
        grammarTypes.put(key, new GrammarTypeDTO(name, level, 0, desc, true));
    }

    private void addGrammarPattern(Long id, String pattern, String meaning, String type, String rule,
                                   int level, String exKr, String exVi, String imp, String status) {
        GrammarDTO dto = new GrammarDTO(
                id, pattern, meaning, type, rule, level, exKr, exVi, imp, status,
                "2026-10-01", "2026-10-15"
        );
        grammars.put(id, dto);

        // Update count in grammarType
        String key = level + "_" + type;
        GrammarTypeDTO typeDto = grammarTypes.get(key);
        if (typeDto != null) {
            typeDto.setGrammarCount(typeDto.getGrammarCount() + 1);
        } else {
            grammarTypes.put(key, new GrammarTypeDTO(type, level, 1, "Dạng ngữ pháp cấp " + level, true));
        }
    }

    public List<ContentReportDTO> getReports() {
        return new ArrayList<>(reports.values());
    }

    public int getPendingReportCount() {
        return (int) reports.values().stream().filter(r -> "OPEN".equalsIgnoreCase(r.getStatus()) || "PENDING".equalsIgnoreCase(r.getStatus())).count();
    }

    public int getTotalGrammarCount() {
        return grammars.size();
    }

    public int getActiveGrammarCount() {
        return (int) grammars.values().stream().filter(g -> "ACTIVE".equalsIgnoreCase(g.getStatus())).count();
    }

    public int getInactiveGrammarCount() {
        return (int) grammars.values().stream().filter(g -> !"ACTIVE".equalsIgnoreCase(g.getStatus())).count();
    }

    public int getTotalGrammarTypeCount() {
        return grammarTypes.size();
    }

    public List<GrammarLevelSummaryDTO> getGrammarLevelSummaries() {
        List<GrammarLevelSummaryDTO> list = new ArrayList<>();
        String[] levelNames = {"", "TOPIK I · Cấp 1", "TOPIK I · Cấp 2", "TOPIK II · Cấp 3", "TOPIK II · Cấp 4", "TOPIK II · Cấp 5", "TOPIK II · Cấp 6"};
        for (int i = 1; i <= 6; i++) {
            final int lvl = i;
            int typeCount = (int) grammarTypes.values().stream().filter(t -> t.getTopikLevel() == lvl).count();
            int gramCount = (int) grammars.values().stream().filter(g -> g.getTopikLevel() == lvl).count();
            list.add(new GrammarLevelSummaryDTO(lvl, levelNames[lvl], typeCount, gramCount, true));
        }
        return list;
    }

    public List<GrammarTypeDTO> getGrammarTypesForLevel(int level) {
        return grammarTypes.values().stream()
                .filter(t -> t.getTopikLevel() == level)
                .collect(Collectors.toList());
    }

    public List<GrammarDTO> getGrammarsForTypeAndLevel(int level, String type) {
        return grammars.values().stream()
                .filter(g -> g.getTopikLevel() == level && (type == null || type.equalsIgnoreCase(g.getGrammarType())))
                .collect(Collectors.toList());
    }

    public boolean resolveReport(Long reportId) {
        ContentReportDTO rep = reports.get(reportId);
        if (rep != null) {
            rep.setStatus("RESOLVED");
            return true;
        }
        return false;
    }

    public boolean rejectReport(Long reportId) {
        ContentReportDTO rep = reports.get(reportId);
        if (rep != null) {
            rep.setStatus("REJECTED");
            return true;
        }
        return false;
    }

    // =====================================================================
    // Exam management stubs (used by ContentPortalController / VinhTH)
    // TODO: Implement with real MockTest entity/repository when exam module is ready
    // =====================================================================

    /**
     * Toggle publish/draft status of a mock test by ID.
     * @return true if found and toggled, false if not found
     */
    public boolean toggleMockTestStatus(Long testId) {
        // Stub: will be replaced with real MockTest repository logic
        return testId != null;
    }

    /**
     * Submit a mock test for Admin approval.
     * @return true if found and submitted, false if not found
     */
    public boolean submitMockTestApproval(Long testId, String submitterName) {
        // Stub: will be replaced with real approval workflow logic
        return testId != null;
    }

    /**
     * Import exam questions from an uploaded file (mock).
     * @param fileName  Original file name or exam name
     * @param questionCount  Number of questions to generate (mock)
     * @return number of questions imported
     */
    public int importExamsMock(String fileName, int questionCount) {
        // Stub: will be replaced with real Excel-parse + entity-save logic
        return questionCount;
    }

}
