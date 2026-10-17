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
    private LocalDateTime createdAt;

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

    public String getRole() { return role; }
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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
