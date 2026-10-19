package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "questions", schema = "dbo", indexes = {
        @Index(name = "ix_questions_type", columnList = "type_code,status,purpose"),
        @Index(name = "ix_questions_hash", columnList = "content_hash")
})
@Getter
@Setter
@NoArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã nghiệp vụ E0001…, gán ngay sau khi có id. */
    @Column(unique = true, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private ExamSource source;

    @Column(name = "type_code", nullable = false, length = 10)
    private String typeCode;

    /** Mã khuôn nội bộ, ví dụ TPL2-LIS-06. Dài hơn cột type_code nên để riêng. */
    @Column(name = "template_code", length = 20)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Track track;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Skill skill;

    @Column(name = "original_no", nullable = false)
    private int originalNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private QuestionGroup group;

    @Nationalized
    @Column(name = "prompt_ko", nullable = false, length = 2000)
    private String promptKo;

    @Nationalized
    @Column(name = "option_1", nullable = false, length = 1000)
    private String option1;

    @Nationalized
    @Column(name = "option_2", nullable = false, length = 1000)
    private String option2;

    @Nationalized
    @Column(name = "option_3", nullable = false, length = 1000)
    private String option3;

    @Nationalized
    @Column(name = "option_4", nullable = false, length = 1000)
    private String option4;

    @Column(name = "answer_index", nullable = false)
    private int answerIndex;

    @Nationalized
    @Column(name = "explanation_vi", length = 4000)
    private String explanationVi;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private QuestionPurpose purpose = QuestionPurpose.EXAM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private QuestionStatus status = QuestionStatus.ACTIVE;

    @Column(name = "cause_hint", length = 5)
    private String causeHint;

    @Column(name = "vocab_ids", length = 500)
    private String vocabIds;

    @Column(name = "grammar_ids", length = 500)
    private String grammarIds;

    @Column(name = "stats_attempts", nullable = false)
    private int statsAttempts;

    @Column(name = "stats_correct", nullable = false)
    private int statsCorrect;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Nationalized
    @Column(name = "hidden_reason", length = 500)
    private String hiddenReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public List<String> options() {
        return List.of(option1, option2, option3, option4);
    }

    /** Tỉ lệ đúng (%), null khi chưa có lượt làm. */
    public Integer correctRate() {
        if (statsAttempts <= 0) return null;
        return Math.round(statsCorrect * 100f / statsAttempts);
    }

    /** Dễ ≥ 75%, Vừa 45–74%, Khó < 45% (BRD 10.2). */
    public String difficulty() {
        Integer r = correctRate();
        if (r == null) return "NONE";
        if (r >= 75) return "EASY";
        if (r >= 45) return "MEDIUM";
        return "HARD";
    }

    public boolean isAbnormal() {
        Integer r = correctRate();
        return r != null && (r < 20 || r > 95);
    }
}
