package com.hottopik.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

public class UserDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    private String password;
    private String fullName;
    private String role; // ROLE_ADMIN, ROLE_CONTENT_MANAGER, ROLE_OPERATION_MANAGER, ROLE_VIP, ROLE_USER
    private String roleName; // Quản trị viên, Content Manager, Operation Manager, VIP Learner, Học viên
    private String status; // ACTIVE, LOCKED
    private String avatar;
    private String phone;
    private String targetLevel;
    private String currentLevel;
    private boolean isVip;
    private String vipPackage;
    private Integer coinBalance = 0;
    private Integer studiedDays = 0;
    private LocalDateTime createdAt;
    private LocalDateTime lockedUntil;
    private String lockReason;
    private boolean pendingPermanentLock = false;

    public Integer getCoinBalance() { return coinBalance != null ? coinBalance : 0; }
    public void setCoinBalance(Integer coinBalance) { this.coinBalance = coinBalance; }

    public Integer getStudiedDays() { return studiedDays != null ? studiedDays : 0; }
    public void setStudiedDays(Integer studiedDays) { this.studiedDays = studiedDays; }

    public UserDTO() {
    }

    public UserDTO(Long id, String username, String email, String password, String fullName,
                   String role, String roleName, String status, String avatar, String phone,
                   String targetLevel, String currentLevel, boolean isVip, String vipPackage) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.roleName = roleName;
        this.status = status;
        this.avatar = avatar;
        this.phone = phone;
        this.targetLevel = targetLevel;
        this.currentLevel = currentLevel;
        this.isVip = isVip;
        this.vipPackage = vipPackage;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() {
        if (role == null) return "ROLE_USER";
        if (role.startsWith("ROLE_")) return role;
        switch (role.toUpperCase()) {
            case "ADMIN": return "ROLE_ADMIN";
            case "CONTENT_MANAGER": return "ROLE_CONTENT_MANAGER";
            case "OPERATION_MANAGER": return "ROLE_OPERATION_MANAGER";
            case "MENTOR": return "ROLE_TEACHER";
            case "STUDENT": return isVip ? "ROLE_VIP" : "ROLE_USER";
            default: return "ROLE_" + role.toUpperCase();
        }
    }

    public String getRawRole() {
        if (role == null) return "STUDENT";
        if (role.startsWith("ROLE_")) {
            String r = role.substring(5);
            if ("TEACHER".equals(r)) return "MENTOR";
            if ("USER".equals(r) || "VIP".equals(r)) return "STUDENT";
            return r;
        }
        return role;
    }

    public void setRole(String role) { this.role = role; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getTargetLevel() { return targetLevel; }
    public void setTargetLevel(String targetLevel) { this.targetLevel = targetLevel; }

    public String getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(String currentLevel) { this.currentLevel = currentLevel; }

    public boolean isVip() { return isVip; }
    public void setVip(boolean vip) { isVip = vip; }

    public String getVipPackage() { return vipPackage; }
    public void setVipPackage(String vipPackage) { this.vipPackage = vipPackage; }

    public String getDisplayName() {
        return fullName != null ? fullName : username;
    }

    public void setDisplayName(String displayName) {
        this.fullName = displayName;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role);
    }

    public boolean isMentor() {
        return "MENTOR".equalsIgnoreCase(role) || "ROLE_TEACHER".equalsIgnoreCase(role);
    }

    public boolean isContentManager() {
        return "CONTENT_MANAGER".equalsIgnoreCase(role) || "ROLE_CONTENT_MANAGER".equalsIgnoreCase(role);
    }

    public boolean isOperationManager() {
        return "OPERATION_MANAGER".equalsIgnoreCase(role) || "ROLE_OPERATION_MANAGER".equalsIgnoreCase(role);
    }

    public boolean isStudent() {
        return "STUDENT".equalsIgnoreCase(role) || "ROLE_USER".equalsIgnoreCase(role) || "ROLE_VIP".equalsIgnoreCase(role);
    }

    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }

    public String getLockReason() { return lockReason; }
    public void setLockReason(String lockReason) { this.lockReason = lockReason; }

    public boolean isPermanentlyLocked() {
        return "LOCKED".equalsIgnoreCase(status) && (lockedUntil == null || lockedUntil.isAfter(LocalDateTime.now().plusYears(1)));
    }

    public String getFormattedLockedUntil() {
        if (lockedUntil == null) return null;
        if (isPermanentlyLocked()) return "Vĩnh viễn";
        return lockedUntil.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    public boolean isPendingPermanentLock() { return pendingPermanentLock; }
    public void setPendingPermanentLock(boolean pendingPermanentLock) { this.pendingPermanentLock = pendingPermanentLock; }
}
