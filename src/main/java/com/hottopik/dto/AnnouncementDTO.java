package com.hottopik.dto;

import java.io.Serializable;

public class AnnouncementDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String content;
    private String targetAudience; // ALL, VIP, FREE, TOPIK_I, TOPIK_II, SPECIFIC
    private String targetAudienceLabel;
    private Long targetUserId;
    private String targetUserName;
    private String authorName;
    private String sentAt;

    public AnnouncementDTO() {}

    public AnnouncementDTO(Long id, String title, String content, String targetAudience, String authorName, String sentAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.targetAudience = targetAudience;
        this.targetAudienceLabel = parseAudienceLabel(targetAudience);
        this.authorName = authorName;
        this.sentAt = sentAt;
    }

    public AnnouncementDTO(Long id, String title, String content, String targetAudience,
                           Long targetUserId, String targetUserName, String authorName, String sentAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.targetAudience = targetAudience;
        this.targetAudienceLabel = parseAudienceLabel(targetAudience);
        this.targetUserId = targetUserId;
        this.targetUserName = targetUserName;
        this.authorName = authorName;
        this.sentAt = sentAt;
    }

    public static String parseAudienceLabel(String code) {
        if (code == null) return "Tất cả học viên";
        switch (code.toUpperCase()) {
            case "VIP":
            case "VIP_ONLY": return "Chỉ thành viên VIP";
            case "FREE":
            case "FREE_ONLY": return "Học viên gói Cơ bản / Miễn phí";
            case "TOPIK_I": return "Học viên mục tiêu TOPIK I (Cấp 1-2)";
            case "TOPIK_II": return "Học viên mục tiêu TOPIK II (Cấp 3-6)";
            case "SPECIFIC": return "Học viên đích danh";
            default: return "Tất cả học viên (ALL)";
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) {
        this.targetAudience = targetAudience;
        this.targetAudienceLabel = parseAudienceLabel(targetAudience);
    }

    public String getTargetAudienceLabel() {
        if (targetAudienceLabel == null) targetAudienceLabel = parseAudienceLabel(targetAudience);
        return targetAudienceLabel;
    }
    public void setTargetAudienceLabel(String targetAudienceLabel) { this.targetAudienceLabel = targetAudienceLabel; }

    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }

    public String getTargetUserName() { return targetUserName; }
    public void setTargetUserName(String targetUserName) { this.targetUserName = targetUserName; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getSentAt() { return sentAt; }
    public void setSentAt(String sentAt) { this.sentAt = sentAt; }
}
