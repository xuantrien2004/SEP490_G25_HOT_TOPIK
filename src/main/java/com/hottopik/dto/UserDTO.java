package com.hottopik.dto;

import java.io.Serializable;
import java.util.List;

public class UserDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    private String password;
    private String fullName;
    private String role;
    private String roleName;
    private String status;
    private String phone;
    private String avatar;
    private String targetLevel;
    private String currentLevel;
    private boolean isVip;
    private Integer streakDays;
    private Integer leafCount;
    private Integer coinBalance;
    private List<String> permissions;

    public UserDTO() {}

    public UserDTO(Long id, String username, String email, String password, String fullName,
                   String role, String roleName, String status, String phone, String avatar,
                   String targetLevel, String currentLevel, boolean isVip, List<String> permissions) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.roleName = roleName;
        this.status = status;
        this.phone = phone;
        this.avatar = avatar;
        this.targetLevel = targetLevel;
        this.currentLevel = currentLevel;
        this.isVip = isVip;
        this.permissions = permissions;
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
        return "ROLE_" + role.toUpperCase();
    }

    public boolean isVip() {
        return isVip || "ROLE_VIP".equalsIgnoreCase(role) || "VIP".equalsIgnoreCase(role);
    }

    public void setVip(boolean vip) { isVip = vip; }

    public Integer getStreakDays() { return streakDays != null ? streakDays : 0; }
    public void setStreakDays(Integer streakDays) { this.streakDays = streakDays; }

    public Integer getLeafCount() { return leafCount != null ? leafCount : 0; }
    public void setLeafCount(Integer leafCount) { this.leafCount = leafCount; }

    public Integer getCoinBalance() { return coinBalance != null ? coinBalance : 0; }
    public void setCoinBalance(Integer coinBalance) { this.coinBalance = coinBalance; }

    public void setRole(String role) { this.role = role; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getTargetLevel() { return targetLevel; }
    public void setTargetLevel(String targetLevel) { this.targetLevel = targetLevel; }

    public String getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(String currentLevel) { this.currentLevel = currentLevel; }

    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }

    /** Alias for getFullName() – used in legacy code for display purposes */
    public String getDisplayName() { return fullName; }
    public void setDisplayName(String displayName) { this.fullName = displayName; }

    /** Alias for streakDays – alias used in legacy service code */
    public Integer getStudiedDays() { return streakDays != null ? streakDays : 0; }
    public void setStudiedDays(int studiedDays) { this.streakDays = studiedDays; }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role);
    }

    public boolean isTeacher() {
        return "TEACHER".equalsIgnoreCase(role) || "ROLE_TEACHER".equalsIgnoreCase(role)
                || "MENTOR".equalsIgnoreCase(role) || "ROLE_MENTOR".equalsIgnoreCase(role);
    }

    public boolean isMentor() {
        return isTeacher();
    }

    public boolean isContentManager() {
        return "CONTENT_MANAGER".equalsIgnoreCase(role) || "ROLE_CONTENT_MANAGER".equalsIgnoreCase(role);
    }

    public boolean isOperationManager() {
        return "OPERATION_MANAGER".equalsIgnoreCase(role) || "ROLE_OPERATION_MANAGER".equalsIgnoreCase(role);
    }

    public boolean isStudent() {
        if (role == null) return true;
        return "STUDENT".equalsIgnoreCase(role) || "ROLE_USER".equalsIgnoreCase(role)
                || "ROLE_VIP".equalsIgnoreCase(role) || "ROLE_LEARNER".equalsIgnoreCase(role)
                || "LEARNER".equalsIgnoreCase(role) || "VIP".equalsIgnoreCase(role)
                || "USER".equalsIgnoreCase(role) || "ROLE_STUDENT".equalsIgnoreCase(role);
    }
}
