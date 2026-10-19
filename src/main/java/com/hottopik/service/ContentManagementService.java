package com.hottopik.service;

import com.hottopik.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ContentManagementService {

    private final Map<Long, ContentReportDTO> reports = new ConcurrentHashMap<>();

    @Autowired
    private GrammarService grammarService;

    @Autowired
    private GrammarGroupService grammarGroupService;

    @PostConstruct
    public void init() {
        // Seed Content Reports matching SEP490.sql
        reports.put(1L, new ContentReportDTO(1L, "Phạm Minh Anh", "learner@hottopik.com", "Đáp án bị đảo thứ tự", "Câu Đọc số 13 TOPIK II đáp án 2 và 3 bị đảo thứ tự logic.", "OPEN", 20, "2026-10-06 18:20"));
        reports.put(2L, new ContentReportDTO(2L, "Nguyễn Hoàng Nam", "viplearner@hottopik.com", "File audio bị rè", "Câu Nghe số 3 file mp3 bị rè ở giây thứ 8.", "OPEN", 10, "2026-10-07 08:45"));
    }

    public List<ContentReportDTO> getReports() {
        return new ArrayList<>(reports.values());
    }

    public int getPendingReportCount() {
        return (int) reports.values().stream().filter(r -> "OPEN".equalsIgnoreCase(r.getStatus()) || "PENDING".equalsIgnoreCase(r.getStatus())).count();
    }


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

    public boolean toggleMockTestStatus(Long testId) {
        return testId != null;
    }

    public boolean submitMockTestApproval(Long testId, String submitterName) {
        return testId != null;
    }

    public int importExamsMock(String fileName, int questionCount) {
        return questionCount;
    }

    public int getTotalQuestionCount() {
        return 0;
    }
}

