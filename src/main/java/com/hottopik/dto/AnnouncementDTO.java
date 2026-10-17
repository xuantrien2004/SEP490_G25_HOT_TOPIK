package com.hottopik.dto;

import java.io.Serializable;

public class AnnouncementDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String content;
    private String targetAudience; // ALL, VIP_ONLY, BASIC_ONLY
    private String authorName;
    private String sentAt;

    public AnnouncementDTO() {}

    public AnnouncementDTO(Long id, String title, String content, String targetAudience, String authorName, String sentAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.targetAudience = targetAudience;
        this.authorName = authorName;
        this.sentAt = sentAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getSentAt() { return sentAt; }
    public void setSentAt(String sentAt) { this.sentAt = sentAt; }
}
