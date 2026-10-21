package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Khuôn câu hỏi: cách hiển thị, tài nguyên và cách chấm. Không chứa nội dung một câu trong một đề.
 * Mã TPL là mã nội bộ, không phải mã của đơn vị tổ chức thi.
 */
@Entity
@Table(name = "question_templates", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "uq_question_templates_code", columnNames = "code"),
        indexes = @Index(name = "ix_question_templates_track", columnList = "track,skill,status"))
@Getter
@Setter
@NoArgsConstructor
public class QuestionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Nationalized
    @Column(nullable = false, length = 200)
    private String name;

    @Nationalized
    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false)
    private int version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TemplateStatus status = TemplateStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Track track;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Skill skill;

    @Column(name = "zone_min", nullable = false)
    private int zoneMin;

    @Column(name = "zone_max", nullable = false)
    private int zoneMax;

    @Nationalized
    @Column(length = 200)
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_mode", nullable = false, length = 20)
    private DisplayMode displayMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_type", nullable = false, length = 20)
    private AnswerType answerType;

    @Column(name = "prompt_required", nullable = false)
    private boolean promptRequired = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "audio_scope", nullable = false, length = 10)
    private ResourceScope audioScope = ResourceScope.NONE;

    @Column(name = "audio_required", nullable = false)
    private boolean audioRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_scope", nullable = false, length = 10)
    private ResourceScope imageScope = ResourceScope.NONE;

    @Column(name = "image_required", nullable = false)
    private boolean imageRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "passage_scope", nullable = false, length = 10)
    private ResourceScope passageScope = ResourceScope.NONE;

    @Column(name = "passage_required", nullable = false)
    private boolean passageRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "script_scope", nullable = false, length = 10)
    private ResourceScope scriptScope = ResourceScope.NONE;

    @Column(name = "option_count")
    private Integer optionCount;

    @Column(name = "child_min", nullable = false)
    private int childMin = 1;

    @Column(name = "child_max", nullable = false)
    private int childMax = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private GradingMode grading = GradingMode.AUTO;

    /** Null: điểm lấy theo vị trí trong đề, khuôn không áp điểm mặc định. */
    @Column(name = "default_points")
    private Integer defaultPoints;

    @Column(name = "min_chars")
    private Integer minChars;

    @Column(name = "max_chars")
    private Integer maxChars;

    @Nationalized
    @Column(length = 1000)
    private String rubric;

    @Column(name = "allow_mini_test", nullable = false)
    private boolean allowMiniTest = true;

    @Column(name = "allow_practice", nullable = false)
    private boolean allowPractice = true;

    @Column(name = "allow_roadmap", nullable = false)
    private boolean allowRoadmap = true;

    @Column(name = "system_owned", nullable = false)
    private boolean systemOwned = true;

    /** Admin đã sửa. Seed khởi động không ghi đè bản này. */
    @Column(nullable = false)
    private boolean customized;

    @Nationalized
    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Nationalized
    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean allowsSeveralChildren() {
        return childMax > 1;
    }

    public String childLabel() {
        if (childMax <= 1) return "1 câu";
        if (skill == Skill.LISTENING) return "1 đoạn nghe, nhiều câu";
        if (skill == Skill.READING) return "1 đoạn văn, nhiều câu";
        return "nhiều câu";
    }
}
