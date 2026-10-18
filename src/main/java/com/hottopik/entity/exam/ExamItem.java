package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một ô câu trong đề: (phần, số câu) → câu hỏi trong ngân hàng. Khoá khi đề đã phát hành. */
@Entity
@Table(name = "exam_items", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "uq_exam_item_slot", columnNames = {"exam_id", "section", "item_no"}))
@Getter
@Setter
@NoArgsConstructor
public class ExamItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id")
    private Exam exam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Skill section;

    @Column(name = "item_no", nullable = false)
    private int no;

    @Column(name = "type_code", nullable = false, length = 10)
    private String typeCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id")
    private Question question;

    public ExamItem(Exam exam, Skill section, int no, String typeCode, Question question) {
        this.exam = exam;
        this.section = section;
        this.no = no;
        this.typeCode = typeCode;
        this.question = question;
    }
}
