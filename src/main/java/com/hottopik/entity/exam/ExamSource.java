package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** Nguồn đề: một bộ câu hỏi nhập từ Excel, ví dụ "TOPIK II kỳ 83 (2022)". */
@Entity
@Table(name = "exam_sources", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class ExamSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Nationalized
    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Track track;

    @Column(name = "exam_year")
    private Integer year;

    @Column(name = "exam_round")
    private Integer round;

    @Column(name = "expected_count", nullable = false)
    private int expectedCount;

    @Column(name = "is_assessment", nullable = false)
    private boolean assessment;

    @Nationalized
    @Column(name = "copyright_note", nullable = false, length = 1000)
    private String copyrightNote;

    @Nationalized
    @Column(length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SourceStatus status = SourceStatus.DRAFT;

    @Nationalized
    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Nguồn khai đủ số câu của đề thật thì phải kiểm tra đủ cấu trúc và được tạo Full Mock nháp. */
    public boolean isFullExam() {
        return expectedCount == (track == Track.TOPIK_I ? 70 : 104);
    }
}
