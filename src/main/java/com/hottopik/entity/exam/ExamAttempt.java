package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Entity
@Table(name = "exam_attempts", schema = "dbo", indexes = {
        @Index(name = "ix_exam_attempt_user_exam", columnList = "user_id,exam_id,started_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ExamAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Nationalized
    @Column(name = "answers_json", nullable = false, columnDefinition = "nvarchar(max)")
    private String answersJson = "{}";

    @Column(name = "remaining_seconds", nullable = false)
    private int remainingSeconds;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(nullable = false)
    private int correct;

    @Column(nullable = false)
    private int total;
}
