package com.hottopik.dto;

import java.io.Serializable;

public class VipCompensationRecordDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private int daysGranted;
    private int coinsGranted;
    private String reason;
    private String actorName;
    private String actorRole;
    private String createdAt;

    public VipCompensationRecordDTO() {}

    public VipCompensationRecordDTO(Long id, Long userId, String userName, String userEmail,
                                    int daysGranted, int coinsGranted, String reason,
                                    String actorName, String actorRole, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.daysGranted = daysGranted;
        this.coinsGranted = coinsGranted;
        this.reason = reason;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public int getDaysGranted() { return daysGranted; }
    public void setDaysGranted(int daysGranted) { this.daysGranted = daysGranted; }

    public int getCoinsGranted() { return coinsGranted; }
    public void setCoinsGranted(int coinsGranted) { this.coinsGranted = coinsGranted; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
