package com.hottopik.dto;

public class TopikLevelSummaryDTO {
    private int level;             // 1 - 6
    private String name;           // TOPIK I · Cấp 1, etc.
    private String tier;           // TOPIK I hoặc TOPIK II
    private String subTitle;       // Sơ cấp 1, Trung cấp 1, Cao cấp 2...
    private int topicCount;        // Số lượng chủ đề (ví dụ 6 chủ đề)
    private int wordCount;         // Tổng số từ vựng
    private int activeWordCount;   // Số từ đang hiển thị
    private String status;         // ACTIVE / INACTIVE

    public TopikLevelSummaryDTO() {
        this.status = "ACTIVE";
    }

    public TopikLevelSummaryDTO(int level, String name, String tier, String subTitle, int topicCount, int wordCount, int activeWordCount, String status) {
        this.level = level;
        this.name = name;
        this.tier = tier;
        this.subTitle = subTitle;
        this.topicCount = topicCount;
        this.wordCount = wordCount;
        this.activeWordCount = activeWordCount;
        this.status = status;
    }

    public int getLevel() {
        return level;
    }
    public void setLevel(int level) {
        this.level = level;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getTier() {
        return tier;
    }
    public void setTier(String tier) {
        this.tier = tier;
    }
    public String getSubTitle() {
        return subTitle;
    }
    public void setSubTitle(String subTitle) {
        this.subTitle = subTitle;
    }
    public int getTopicCount() {
        return topicCount;
    }
    public void setTopicCount(int topicCount) {
        this.topicCount = topicCount;
    }
    public int getWordCount() {
        return wordCount;
    }
    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
    }
    public int getActiveWordCount() {
        return activeWordCount;
    }
    public void setActiveWordCount(int activeWordCount) {
        this.activeWordCount = activeWordCount;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(this.status);
    }
}
