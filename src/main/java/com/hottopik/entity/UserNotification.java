package com.hottopik.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_notifications", schema = "dbo", indexes = {
        @Index(name = "ix_notifications_user", columnList = "user_id, is_read, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    @Nationalized
    private String title;

    @Column(nullable = false, length = 1000)
    @Nationalized
    private String message;

    @Column(length = 50)
    private String type; // REPORT_RESOLVED, REPORT_REJECTED, SYSTEM

    @Column(length = 500)
    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
