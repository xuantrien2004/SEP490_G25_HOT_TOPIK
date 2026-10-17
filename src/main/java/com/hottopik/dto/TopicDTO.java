package com.hottopik.dto;

public class TopicDTO {
    private Long id;
    private String name;
    private Integer topikLevel;
    private String description;
    private int wordCount;
    private int activeCount;
    private String status; // ACTIVE / INACTIVE

    public TopicDTO() {
        this.status = "ACTIVE";
    }

    public TopicDTO(Long id, String name, Integer topikLevel, String description, int wordCount, int activeCount, String status) {
        this.id = id;
        this.name = name;
        this.topikLevel = topikLevel;
        this.description = description;
        this.wordCount = wordCount;
        this.activeCount = activeCount;
        this.status = (status != null && !status.isEmpty()) ? status : "ACTIVE";
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Integer getTopikLevel() {
        return topikLevel;
    }
    public void setTopikLevel(Integer topikLevel) {
        this.topikLevel = topikLevel;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public int getWordCount() {
        return wordCount;
    }
    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
    }
    public int getActiveCount() {
        return activeCount;
    }
    public void setActiveCount(int activeCount) {
        this.activeCount = activeCount;
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
