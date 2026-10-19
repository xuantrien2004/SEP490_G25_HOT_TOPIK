package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

/** Đề Viết 51–54 (W51 văn bản thực dụng, W52 đoạn giải thích, W53 biểu đồ, W54 nghị luận). */
@Entity
@Table(name = "writing_prompts", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class WritingPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private ExamSource source;

    @Column(name = "question_no", nullable = false)
    private int no;

    @Nationalized
    @Column(name = "prompt_ko", nullable = false, length = 4000)
    private String promptKo;

    @Nationalized
    @Column(name = "keys_1", length = 1000)
    private String keys1;

    @Nationalized
    @Column(name = "keys_2", length = 1000)
    private String keys2;

    @Nationalized
    @Column(name = "chart_json", length = 2000)
    private String chartJson;

    @Nationalized
    @Column(name = "chart_image", length = 255)
    private String chartImage;

    @Nationalized
    @Column(name = "model_answer", nullable = false, length = 4000)
    private String modelAnswer;

    @Nationalized
    @Column(name = "rubric_note", length = 2000)
    private String rubricNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private QuestionStatus status = QuestionStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public String typeCode() {
        return "II-V" + (no - 50);
    }
}
