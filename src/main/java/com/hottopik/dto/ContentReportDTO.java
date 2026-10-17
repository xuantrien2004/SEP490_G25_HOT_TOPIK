package com.hottopik.dto;

import java.io.Serializable;

public class ContentReportDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String reporterName;
    private String reporterEmail;
    private String issueType;
    private String details;
    private String status; // OPEN, RESOLVED
    private int rewardCoins;
    private String createdAt;

    public ContentReportDTO() {}

    public ContentReportDTO(Long id, String reporterName, String reporterEmail,
                            String issueType, String details, String status,
                            int rewardCoins, String createdAt) {
        this.id = id;
        this.reporterName = reporterName;
        this.reporterEmail = reporterEmail;
        this.issueType = issueType;
        this.details = details;
        this.status = status;
        this.rewardCoins = rewardCoins;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }

    public String getReporterEmail() { return reporterEmail; }
    public void setReporterEmail(String reporterEmail) { this.reporterEmail = reporterEmail; }

    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getRewardCoins() { return rewardCoins; }
    public void setRewardCoins(int rewardCoins) { this.rewardCoins = rewardCoins; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
