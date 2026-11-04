package com.hottopik.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/** Bài nộp bất biến; payload giữ nguyên đề, bài làm và lịch sử chấm tại thời điểm nộp. */
@Entity
@Table(name = "writing_submissions", schema = "dbo")
@Getter
@Setter
public class WritingSubmission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Bảng hiện có dùng user_id làm FK; learner_id vẫn được giữ cho các truy vấn bài nộp hiện tại.
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "learner_id", nullable = false)
    private Long learnerId;
    @Column(name = "prompt_id", nullable = false)
    private Long promptId;
    @Nationalized
    @Column(nullable = false, length = 10)
    private String mode;
    @Column(name = "mentor_id")
    private Long mentorId;
    @Nationalized
    @Column(nullable = false, length = 40)
    private String status;
    @Nationalized
    @Column(nullable = false, columnDefinition = "nvarchar(max)")
    private String payload;
    @Version
    private Long version;
}
