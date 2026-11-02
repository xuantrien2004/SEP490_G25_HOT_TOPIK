package com.hottopik.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

@Entity
@Table(name = "question_reports", schema = "dbo", indexes = {
        @Index(name = "ix_reports_user", columnList = "user_id, status"),
        @Index(name = "ix_reports_question", columnList = "question_id")
})
@Getter
@Setter
@NoArgsConstructor
public class QuestionReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reporter_name", nullable = false, length = 100)
    @Nationalized
    private String reporterName;

    @Column(name = "reporter_email", nullable = false, length = 255)
    @Nationalized
    private String reporterEmail;

    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "question_kind", length = 20)
    private String questionKind;

    @Column(name = "question_code", length = 50)
    @Nationalized
    private String questionCode;

    @Column(name = "question_summary", length = 1000)
    @Nationalized
    private String questionSummary;

    @Column(name = "exam_id")
    private Long examId;

    @Column(name = "exam_title", length = 255)
    @Nationalized
    private String examTitle;

    @Column(name = "question_no")
    private Integer questionNo;

    @Column(name = "issue_type", nullable = false, length = 100)
    @Nationalized
    private String issueType;

    @Column(name = "details", nullable = false, length = 2000)
    @Nationalized
    private String details;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "OPEN"; // OPEN, RESOLVED, REJECTED

    @Column(name = "resolution_note", length = 2000)
    @Nationalized
    private String resolutionNote;

    @Column(name = "rejection_reason", length = 2000)
    @Nationalized
    private String rejectionReason;

    @Column(name = "reward_coins", nullable = false)
    private int rewardCoins = 5;

    @Column(name = "resolved_by", length = 100)
    @Nationalized
    private String resolvedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
