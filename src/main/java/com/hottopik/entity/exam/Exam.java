package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Full Mock hoặc Mini Test (Mini luôn có parent là một Full Mock đã phát hành). */
@Entity
@Table(name = "exams", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 20)
    private String code;

    @Nationalized
    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Track track;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExamKind kind;

    /** Số thứ tự "Đề n" trong cặp (track, kind). */
    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_exam_id")
    private Exam parent;

    @Column(name = "is_auto", nullable = false)
    private boolean auto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private ExamSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExamStatus status = ExamStatus.DRAFT;

    /** PUBLISH hoặc ARCHIVE khi đang chờ duyệt. */
    @Column(name = "pending_action", length = 10)
    private String pendingAction;

    @Column(name = "approval_request_id")
    private Long approvalRequestId;

    @Column(name = "listening_minutes", nullable = false)
    private int listeningMinutes;

    @Column(name = "writing_minutes", nullable = false)
    private int writingMinutes;

    @Column(name = "reading_minutes", nullable = false)
    private int readingMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writing_51_id")
    private WritingPrompt writing51;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writing_52_id")
    private WritingPrompt writing52;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writing_53_id")
    private WritingPrompt writing53;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "writing_54_id")
    private WritingPrompt writing54;

    @Nationalized
    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("section ASC, no ASC")
    private List<ExamItem> items = new ArrayList<>();

    public boolean isEditable() {
        return status == ExamStatus.DRAFT;
    }

    public List<WritingPrompt> writingPrompts() {
        List<WritingPrompt> list = new ArrayList<>();
        if (writing51 != null) list.add(writing51);
        if (writing52 != null) list.add(writing52);
        if (writing53 != null) list.add(writing53);
        if (writing54 != null) list.add(writing54);
        return list;
    }

    public boolean hasFullWritingSet() {
        return writing51 != null && writing52 != null && writing53 != null && writing54 != null;
    }

    public int totalMinutes() {
        return switch (kind) {
            case FULL -> listeningMinutes + readingMinutes + (track == Track.TOPIK_II ? writingMinutes : 0);
            case LISTENING -> listeningMinutes;
            case READING -> readingMinutes;
            case WRITING -> writingMinutes;
        };
    }

    public int questionCount() {
        int writing = (kind == ExamKind.FULL || kind == ExamKind.WRITING) ? writingPrompts().size() : 0;
        return items.size() + writing;
    }
}
