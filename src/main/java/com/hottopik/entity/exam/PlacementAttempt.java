package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Một lượt làm bài kiểm tra trình độ. */
@Entity
@Table(name = "placement_attempts", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class PlacementAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_email", length = 200)
    private String userEmail;

    @Column(name = "paper_code", nullable = false, length = 20)
    private String paperCode;

    @Column(name = "question_ids", nullable = false, length = 2000)
    private String questionIds;

    @Column(nullable = false)
    private int total;

    @Column(name = "answers_json", nullable = false, length = 4000)
    private String answersJson = "{}";

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "listen_correct", nullable = false)
    private int listenCorrect;

    @Column(name = "listen_total", nullable = false)
    private int listenTotal;

    @Column(name = "read_correct", nullable = false)
    private int readCorrect;

    @Column(name = "read_total", nullable = false)
    private int readTotal;

    @Column(nullable = false)
    private int points;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();
}
