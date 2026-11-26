package com.hottopik.entity.subscription;

import com.hottopik.util.PaymentDateTimeUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

/** Ảnh chụp bất biến của gói học sau mỗi lần thay đổi được duyệt. Đơn hàng tham chiếu phiên bản này. */
@Entity
@Table(name = "subscription_plan_versions", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "UQ_plan_versions_plan_no", columnNames = {"plan_id", "version_number"}))
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPlanVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, foreignKey = @ForeignKey(name = "FK_plan_versions_plan"))
    private SubscriptionPlan plan;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Nationalized
    @Column(nullable = false, length = 100, updatable = false)
    private String name;

    @Nationalized
    @Column(length = 1000, updatable = false)
    private String description;

    @Column(nullable = false, updatable = false)
    private Long price;

    @Column(name = "duration_days", updatable = false)
    private Integer durationDays;

    @Column(name = "effective_at", nullable = false, updatable = false)
    private LocalDateTime effectiveAt;

    @Nationalized
    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = PaymentDateTimeUtil.now();
        if (effectiveAt == null) effectiveAt = createdAt;
    }
}
