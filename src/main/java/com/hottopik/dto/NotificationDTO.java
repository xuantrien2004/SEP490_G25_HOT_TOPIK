package com.hottopik.dto;

import java.io.Serializable;

public class NotificationDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId; // null if broadcast to group
    private String userEmail;
    private String title;
    private String content;
    private String category; // ANNOUNCEMENT, SYSTEM, STUDY, WRITING, VIP
    private String icon; // 📢, ✍️, ⏰, 👑, 🪙, etc.
    private String linkUrl;
    private boolean read;
    private String createdAt;
    private String targetAudience; // ALL, VIP, FREE, TOPIK_I, TOPIK_II, SPECIFIC

    public NotificationDTO() {}

    public NotificationDTO(Long id, Long userId, String userEmail, String title, String content,
                           String category, String icon, String linkUrl, boolean read,
                           String createdAt, String targetAudience) {
        this.id = id;
        this.userId = userId;
        this.userEmail = userEmail;
        this.title = title;
        this.content = content;
        this.category = category;
        this.icon = icon;
        this.linkUrl = linkUrl;
        this.read = read;
        this.createdAt = createdAt;
        this.targetAudience = targetAudience;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }
}
