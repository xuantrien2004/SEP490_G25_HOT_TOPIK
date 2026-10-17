package com.hottopik.dto;

import java.io.Serializable;

public class AuditLogDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String actorName;
    private String actorRole;
    private String action;
    private String targetEntity;
    private String details;
    private String ipAddress;
    private String timestamp;

    public AuditLogDTO() {}

    public AuditLogDTO(Long id, String actorName, String actorRole, String action, String targetEntity, String details, String ipAddress, String timestamp) {
        this.id = id;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.action = action;
        this.targetEntity = targetEntity;
        this.details = details;
        this.ipAddress = ipAddress;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTargetEntity() { return targetEntity; }
    public void setTargetEntity(String targetEntity) { this.targetEntity = targetEntity; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
