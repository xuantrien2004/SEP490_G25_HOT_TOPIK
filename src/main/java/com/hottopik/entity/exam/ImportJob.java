package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

/** Một lần tải file Excel cho nguồn đề; "Tải lại file đã sửa" tạo job mới và job cũ thành SUPERSEDED. */
@Entity
@Table(name = "import_jobs", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id")
    private ExamSource source;

    @Nationalized
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ImportJobStatus status = ImportJobStatus.VALIDATING;

    @Column(name = "mcq_rows", nullable = false)
    private int mcqRows;

    @Column(name = "writing_rows", nullable = false)
    private int writingRows;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "warning_count", nullable = false)
    private int warningCount;

    @Lob
    @Nationalized
    @Column(name = "issues_json")
    private String issuesJson;

    /** Bản đề đang sửa trên web. Excel chỉ tạo bản này; kích hoạt đọc bản này, không đọc lại file. */
    @Lob
    @Nationalized
    @Column(name = "draft_json")
    private String draftJson;

    @Nationalized
    @Column(name = "created_by", length = 120)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;
}
