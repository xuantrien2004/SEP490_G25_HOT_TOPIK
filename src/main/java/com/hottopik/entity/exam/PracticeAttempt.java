package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "practice_attempts", schema = "dbo", indexes = {
        @Index(name = "ix_practice_user_type", columnList = "user_id, type_code, completed_at")
})
@Getter
@Setter
@NoArgsConstructor
public class PracticeAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Track track;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Skill skill;

    @Column(name = "type_code", nullable = false, length = 10)
    private String typeCode;

    @Column(name = "question_ids", nullable = false, length = 4000)
    private String questionIds;

    @Column(name = "answers_json", nullable = false, length = 4000)
    private String answersJson = "{}";

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "total", nullable = false)
    private int total;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
