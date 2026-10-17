package com.hottopik.dto;

import java.io.Serializable;

public class MockTestItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String title;
    private String level;
    private String version;
    private int questionCount;
    private int timeLimitMinutes;
    private String status; // DRAFT, PENDING_APPROVAL, PUBLISHED, ARCHIVED
    private String updatedAt;

    public MockTestItemDTO() {}

    public MockTestItemDTO(Long id, String code, String title, String level, String version,
                           int questionCount, int timeLimitMinutes, String status, String updatedAt) {
        this.id = id;
        this.code = code;
        this.title = title;
        this.level = level;
        this.version = version;
        this.questionCount = questionCount;
        this.timeLimitMinutes = timeLimitMinutes;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public int getQuestionCount() { return questionCount; }
    public void setQuestionCount(int questionCount) { this.questionCount = questionCount; }

    public int getTimeLimitMinutes() { return timeLimitMinutes; }
    public void setTimeLimitMinutes(int timeLimitMinutes) { this.timeLimitMinutes = timeLimitMinutes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
