package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Một bản thứ tự dạng học. Học viên tạo lộ trình cá nhân từ bản đang phát hành.
 * Viết không nằm trong bản này.
 */
@Entity
@Table(name = "program_versions", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "uq_program_versions_code", columnNames = "version_code"))
@Getter
@Setter
@NoArgsConstructor
public class ProgramVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_code", nullable = false, length = 20)
    private String versionCode;

    @Nationalized
    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProgramStatus status = ProgramStatus.DRAFT;

    /** Các mốc ôn, ví dụ 2,7,14. */
    @Column(name = "review_offsets", nullable = false, length = 30)
    private String reviewOffsets = "2,7,14";

    @Column(name = "weekly_review_questions", nullable = false)
    private int weeklyReviewQuestions = 20;

    /** Tỉ lệ ba chặng Làm quen, Tăng tốc, Nước rút. Cộng đủ 100. */
    @Column(name = "phase_split", nullable = false, length = 20)
    private String phaseSplit = "40,40,20";

    @Column(name = "sessions_per_type", nullable = false)
    @ColumnDefault("4")
    private int sessionsPerType = 4;

    @Column(name = "questions_per_session", nullable = false)
    @ColumnDefault("8")
    private int questionsPerSession = 8;

    @Column(name = "pending_action", length = 10)
    private String pendingAction;

    @Column(name = "approval_request_id")
    private Long approvalRequestId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
