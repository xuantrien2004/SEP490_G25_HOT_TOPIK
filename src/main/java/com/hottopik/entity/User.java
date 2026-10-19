package com.hottopik.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 40)
    private String displayName;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "STUDENT"; // STUDENT, MENTOR, CONTENT_MANAGER, OPERATION_MANAGER, ADMIN

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING_VERIFY"; // PENDING_VERIFY, ACTIVE, LOCKED, DELETED

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String timezone = "Asia/Ho_Chi_Minh";

    @Column(name = "current_level")
    private Short currentLevel; // 0 to 6

    @Column(name = "level_source", length = 10)
    private String levelSource; // PLACEMENT, SELF

    @Column(name = "target_level")
    private Short targetLevel; // 1 to 6

    @Column(name = "coin_balance", nullable = false)
    @Builder.Default
    private Integer coinBalance = 0;

    @Column(name = "daily_goal_minutes", nullable = false)
    @Builder.Default
    private Short dailyGoalMinutes = 20;

    @Column(name = "studied_days", nullable = false)
    @Builder.Default
    private Integer studiedDays = 0;

    @Column(name = "failed_login_count", nullable = false)
    @Builder.Default
    private Short failedLoginCount = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "lock_reason", length = 500)
    private String lockReason;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "leaf_color", length = 20)
    private String leafColor;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
