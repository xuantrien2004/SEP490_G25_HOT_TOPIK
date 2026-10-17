package com.hottopik.dto;

public class GrammarLevelSummaryDTO {
    private Integer level;
    private String levelName;
    private Integer typeCount;
    private Integer grammarCount;
    private boolean active;

    public GrammarLevelSummaryDTO() {}

    public GrammarLevelSummaryDTO(Integer level, String levelName, Integer typeCount, Integer grammarCount, boolean active) {
        this.level = level;
        this.levelName = levelName;
        this.typeCount = typeCount;
        this.grammarCount = grammarCount;
        this.active = active;
    }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public String getLevelName() { return levelName; }
    public void setLevelName(String levelName) { this.levelName = levelName; }

    public Integer getTypeCount() { return typeCount; }
    public void setTypeCount(Integer typeCount) { this.typeCount = typeCount; }

    public Integer getGrammarCount() { return grammarCount; }
    public void setGrammarCount(Integer grammarCount) { this.grammarCount = grammarCount; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
