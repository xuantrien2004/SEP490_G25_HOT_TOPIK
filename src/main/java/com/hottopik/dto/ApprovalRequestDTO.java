package com.hottopik.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ApprovalRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String requesterName;
    private String requesterRole;
    private String requestType;
    private String title;
    private String description;
    private String status; // PENDING, APPROVED, REJECTED
    private String decisionNote;
    private String createdAt;
    private String decidedAt;
    private Long targetId;

    public ApprovalRequestDTO() {}

    public ApprovalRequestDTO(Long id, String requesterName, String requesterRole,
                              String requestType, String title, String description,
                              String status, String createdAt) {
        this.id = id;
        this.requesterName = requesterName;
        this.requesterRole = requesterRole;
        this.requestType = requestType;
        this.title = title;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }

    public String getRequesterRole() { return requesterRole; }
    public void setRequesterRole(String requesterRole) { this.requesterRole = requesterRole; }

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getDecidedAt() { return decidedAt; }
    public void setDecidedAt(String decidedAt) { this.decidedAt = decidedAt; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
}
