package com.hottopik.dto;

public class GrammarTypeDTO {
    private String name;
    private Integer topikLevel;
    private Integer grammarCount;
    private String description;
    private boolean active;

    public GrammarTypeDTO() {}

    public GrammarTypeDTO(String name, Integer topikLevel, Integer grammarCount, String description, boolean active) {
        this.name = name;
        this.topikLevel = topikLevel;
        this.grammarCount = grammarCount;
        this.description = description;
        this.active = active;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getTopikLevel() { return topikLevel; }
    public void setTopikLevel(Integer topikLevel) { this.topikLevel = topikLevel; }

    public Integer getGrammarCount() { return grammarCount; }
    public void setGrammarCount(Integer grammarCount) { this.grammarCount = grammarCount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
