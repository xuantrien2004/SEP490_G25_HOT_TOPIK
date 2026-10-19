package com.hottopik.service;

import com.hottopik.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class ContentManagementService {

    private final Map<Long, QuestionBankItemDTO> questions = new ConcurrentHashMap<>();
    private final Map<Long, MockTestItemDTO> mockTests = new ConcurrentHashMap<>();
    private final Map<Long, ContentReportDTO> reports = new ConcurrentHashMap<>();

    private final AtomicLong testIdGen = new AtomicLong(10);
    private final AtomicLong questionIdGen = new AtomicLong(20);

    @Autowired
    private ApprovalService approvalService;

    // Inject các service ngữ pháp chuẩn để tránh xung đột dữ liệu
    @Autowired
    private GrammarService grammarService;

    @Autowired
    private GrammarGroupService grammarGroupService;

    @PostConstruct
    public void init() {
        // Seed Question Bank
        questions.put(1L, new QuestionBankItemDTO(1L, "Q-READ-01", "READING", "TOPIK_I", "Điền từ vào chỗ trống (Câu 1-4)", "이 사람은 무엇을 하고 있습니까? (Chỉ hành động)", 2, false, true));
        questions.put(2L, new QuestionBankItemDTO(2L, "Q-READ-02", "READING", "TOPIK_I", "Chọn chủ đề biển báo (Câu 5-6)", "안내문: 도서관 안에서는 조용히 해주십시오.", 2, false, true));
        questions.put(3L, new QuestionBankItemDTO(3L, "Q-LIST-01", "LISTENING", "TOPIK_I", "Chọn câu đối đáp tiếp theo (Câu 1-4)", "여: 내일 몇 시에 만날까요? 남: ...", 1, true, true));
        questions.put(4L, new QuestionBankItemDTO(4L, "Q-READ-03", "READING", "TOPIK_II", "Đọc hiểu biểu đồ thống kê (Câu 53)", "Biểu đồ tỷ lệ người độc thân tại Hàn Quốc từ 2015-2025.", 4, false, true));
        questions.put(5L, new QuestionBankItemDTO(5L, "Q-WRIT-51", "WRITING", "TOPIK_II", "Điền từ thông báo thực tế (Câu 51)", "Soạn đoạn văn ngắn 50-70 chữ thông báo chuyển nhà.", 3, false, true));
        questions.put(6L, new QuestionBankItemDTO(6L, "Q-WRIT-52", "WRITING", "TOPIK_II", "Điền giải thích hiện tượng khoa học (Câu 52)", "Đoạn văn ngắn giải thích nguyên lý giấc ngủ sâu.", 4, false, true));
        questions.put(7L, new QuestionBankItemDTO(7L, "Q-WRIT-53", "WRITING", "TOPIK_II", "Viết giải thích biểu đồ (Câu 53, 200-300 chữ)", "Giải thích biểu đồ tăng trưởng thương mại điện tử.", 4, false, true));
        questions.put(8L, new QuestionBankItemDTO(8L, "Q-WRIT-54", "WRITING", "TOPIK_II", "Bài luận nghị luận xã hội (Câu 54, 600-700 chữ)", "Nghị luận về ảnh hưởng của trí tuệ nhân tạo đối với giới trẻ.", 5, false, true));

        // Seed Mock Tests & Versions (Quản lý Đề)
        mockTests.put(1L, new MockTestItemDTO(1L, "TEST-MOCK-01", "Đề thi thử TOPIK I số 1 (Đọc 1-40 & Nghe 1-30)", "TOPIK_I", "v1.2", 70, 100, "PUBLISHED", "2026-10-01"));
        mockTests.put(2L, new MockTestItemDTO(2L, "TEST-MOCK-02", "Đề thi thử Full Mock #2 TOPIK II (Đọc 1-50, Nghe 1-50 & Viết 51-54)", "TOPIK_II", "v2.0-rc", 104, 180, "DRAFT", "2026-10-06"));
        mockTests.put(3L, new MockTestItemDTO(3L, "TEST-MOCK-03", "Đề luyện nhanh Đọc hiểu TOPIK I (Câu 1-4 & 5-6)", "TOPIK_I", "v1.0", 30, 40, "DRAFT", "2026-10-07"));
        mockTests.put(4L, new MockTestItemDTO(4L, "TEST-MOCK-04", "Đề chuyên đề Viết luận TOPIK II (Câu 51-54)", "TOPIK_II", "v1.0", 4, 50, "PUBLISHED", "2026-10-08"));

        // Seed Content Reports matching SEP490.sql
        reports.put(1L, new ContentReportDTO(1L, "Phạm Minh Anh", "learner@hottopik.com", "Đáp án bị đảo thứ tự", "Câu Đọc số 13 TOPIK II đáp án 2 và 3 bị đảo thứ tự logic.", "OPEN", 20, "2026-10-06 18:20"));
        reports.put(2L, new ContentReportDTO(2L, "Nguyễn Hoàng Nam", "viplearner@hottopik.com", "File audio bị rè", "Câu Nghe số 3 file mp3 bị rè ở giây thứ 8.", "OPEN", 10, "2026-10-07 08:45"));
    }

    public List<QuestionBankItemDTO> getQuestions() {
        return new ArrayList<>(questions.values());
    }

    public List<MockTestItemDTO> getMockTests() {
        return new ArrayList<>(mockTests.values());
    }

    public List<ContentReportDTO> getReports() {
        return new ArrayList<>(reports.values());
    }

    public int getTotalQuestionCount() {
        return questions.size();
    }

    public int getPendingReportCount() {
        return (int) reports.values().stream().filter(r -> "OPEN".equalsIgnoreCase(r.getStatus()) || "PENDING".equalsIgnoreCase(r.getStatus())).count();
    }

    public int getTotalExamCount() {
        return mockTests.size();
    }

    // Các hàm thống kê ngữ pháp kết nối trực tiếp qua GrammarService & GrammarGroupService thực tế
    public int getTotalGrammarCount() {
        try {
            return grammarService.getAllGrammars().size();
        } catch (Exception e) {
            return 0;
        }
    }

    public int getActiveGrammarCount() {
        try {
            return (int) grammarService.getAllGrammars().stream().filter(g -> "ACTIVE".equalsIgnoreCase(g.getStatus())).count();
        } catch (Exception e) {
            return 0;
        }
    }

    public int getInactiveGrammarCount() {
        try {
            return (int) grammarService.getAllGrammars().stream().filter(g -> !"ACTIVE".equalsIgnoreCase(g.getStatus())).count();
        } catch (Exception e) {
            return 0;
        }
    }

    public int getTotalGrammarTypeCount() {
        try {
            return grammarGroupService.getAllGroups().size();
        } catch (Exception e) {
            return 0;
        }
    }

    public List<GrammarLevelSummaryDTO> getGrammarLevelSummaries() {
        List<GrammarLevelSummaryDTO> list = new ArrayList<>();
        String[] levelNames = {"", "TOPIK I · Cấp 1", "TOPIK I · Cấp 2", "TOPIK II · Cấp 3", "TOPIK II · Cấp 4", "TOPIK II · Cấp 5", "TOPIK II · Cấp 6"};
        for (int i = 1; i <= 6; i++) {
            final short lvl = (short) i;
            int typeCount = 0;
            int gramCount = 0;
            try {
                typeCount = grammarGroupService.getGroupsByLevel(lvl).size();
                gramCount = grammarService.filterGrammars(lvl, null, null, null).size();
            } catch (Exception ignored) {}

            list.add(new GrammarLevelSummaryDTO(i, levelNames[i], typeCount, gramCount, true));
        }
        return list;
    }

    public List<GrammarTypeDTO> getGrammarTypesForLevel(int level) {
        try {
            return grammarGroupService.getGroupsByLevel((short) level).stream().map(g ->
                    new GrammarTypeDTO(
                            g.getId(),
                            g.getNameVi(),
                            (int) g.getLevel(),
                            grammarService.getGrammarsByGroup(g.getId()).size(),
                            "Nhóm ngữ pháp số " + g.getGroupNo(),
                            true
                    )
            ).collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public boolean submitMockTestApproval(Long testId, String requesterName) {
        MockTestItemDTO test = mockTests.get(testId);
        if (test != null) {
            test.setStatus("PENDING_APPROVAL");
            approvalService.submitRequest(
                    requesterName,
                    "Content Manager",
                    "PUBLISH_EXAM",
                    "Phê duyệt phát hành " + test.getTitle() + " (" + test.getVersion() + ")",
                    "Đề thi gồm " + test.getQuestionCount() + " câu, thời gian làm bài " + test.getTimeLimitMinutes() + " phút."
            );
            return true;
        }
        return false;
    }

    public boolean toggleMockTestStatus(Long testId) {
        MockTestItemDTO test = mockTests.get(testId);
        if (test != null) {
            if ("PUBLISHED".equalsIgnoreCase(test.getStatus())) {
                test.setStatus("DRAFT");
            } else {
                test.setStatus("PUBLISHED");
            }
            return true;
        }
        return false;
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

    public int importExamsMock(String filename, int count) {
        long id = testIdGen.incrementAndGet();
        MockTestItemDTO newTest = new MockTestItemDTO(
                id,
                "TEST-IMP-" + id,
                "Đề nhập từ file: " + filename,
                "TOPIK_II",
                "v1.0",
                count > 0 ? count : 50,
                110,
                "DRAFT",
                "2026-10-16"
        );
        mockTests.put(id, newTest);
        return count > 0 ? count : 50;
    }
}