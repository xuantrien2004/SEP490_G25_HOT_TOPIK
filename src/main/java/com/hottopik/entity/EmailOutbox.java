package com.hottopik.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "to_email", nullable = false, length = 255)
    private String toEmail;

    @Column(nullable = false, length = 50)
    private String template; // VERIFY_EMAIL, RESET_PASSWORD, REMINDER, etc.

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String payload; // JSON format

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String status = "PENDING"; // PENDING, SENT, FAILED

    @Column(nullable = false)
    @Builder.Default
    private Short attempts = 0;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
