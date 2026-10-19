package com.hottopik.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSetting {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String theme = "LIGHT"; // LIGHT, DARK, SYSTEM

    @Column(name = "font_size", nullable = false, length = 10)
    @Builder.Default
    private String fontSize = "M"; // S, M, L

    @Column(name = "show_romanization", nullable = false)
    @Builder.Default
    private Boolean showRomanization = true;

    @Column(name = "audio_speed", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal audioSpeed = new BigDecimal("1.00");

    @Column(name = "reminder_enabled", nullable = false)
    @Builder.Default
    private Boolean reminderEnabled = false;

    @Column(name = "reminder_time")
    private LocalTime reminderTime;

    @Column(name = "reminder_email", nullable = false)
    @Builder.Default
    private Boolean reminderEmail = false;

    @Column(name = "browser_push_enabled", nullable = false)
    @Builder.Default
    private Boolean browserPushEnabled = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
