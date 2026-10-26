package com.hottopik.service.exam;

import com.hottopik.entity.exam.Skill;

import java.util.ArrayList;
import java.util.List;

public class ParsedWorkbook {

    public static final String SHEET_QUESTIONS = "QUESTIONS";
    public static final String SHEET_WRITING = "WRITING";

    public static class QuestionRow {
        public int row;
        public Integer no;
        public String noRaw;
        public Skill skill;
        public String skillRaw;
        public String typeCode;
        public String groupId;
        public String audioFile;
        public String scriptKo;
        public String passageKo;
        public String imageFile;
        public String promptKo;
        public String[] options = new String[4];
        public String answerRaw;
        public Integer answer;
        public String explanationVi;
        public String causeHint;
        public String vocabIds;
        public String grammarIds;
        public String purpose;

        /** Gán sau khi kiểm tra: mã dạng tra theo (track, kỹ năng, số câu). */
        public String resolvedType;
    }

    public static class WritingRow {
        public int row;
        public Integer no;
        public String noRaw;
        public String promptKo;
        public String keys1;
        public String keys2;
        public String chartJson;
        public String modelAnswer;
        public String rubricNote;
    }

    /** Một bài nhập tay: chọn khuôn, tài nguyên dùng chung, rồi một hoặc nhiều câu. */
    public static class ManualAsk {
        public String promptKo;
        public String[] options = new String[4];
        public String answerRaw;
        public Integer answer;
        public String explanationVi;
        public String keys1;
        public String keys2;
        public String modelAnswer;
        public String rubricNote;
        public String chart;
        /** Số câu trên đề, trong phần Nghe, Đọc hoặc Viết. */
        public Integer no;
    }

    public static class ManualCard {
        public String id;
        public String templateCode;
        /** Dạng câu đang chọn, ví dụ I-N8. */
        public String typeCode;
        public String audioFile;
        public String imageFile;
        public String passageKo;
        public String scriptKo;
        public String scriptVi;
        public final List<ManualAsk> asks = new ArrayList<>();
    }

    public final List<QuestionRow> questions = new ArrayList<>();
    public final List<WritingRow> writings = new ArrayList<>();
    public final List<ManualCard> manualCards = new ArrayList<>();
    public final List<ImportIssue> issues = new ArrayList<>();
    public boolean hasWritingSheet;
}
