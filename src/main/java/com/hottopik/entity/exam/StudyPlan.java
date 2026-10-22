package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lộ trình một học viên đã tạo từ một bản phát hành.
 * Bản Content Manager đổi sau đó không xếp lại lịch này.
 */
@Entity
@Table(name = "study_plans", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "uq_study_plans_owner", columnNames = "owner_email"))
@Getter
@Setter
@NoArgsConstructor
public class StudyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_email", nullable = false, length = 180)
    private String ownerEmail;

    @Column(name = "program_id", nullable = false)
    private Long programId;

    @Column(name = "version_code", nullable = false, length = 20)
    private String versionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Track track;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    @Column(name = "items_per_day", nullable = false)
    private int itemsPerDay;

    @Column(name = "minutes_per_day", nullable = false)
    private int minutesPerDay;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Các ô đã học, dạng {@code 11:0,11:1}. */
    @Lob
    @Column(name = "done_keys")
    private String doneKeys;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
