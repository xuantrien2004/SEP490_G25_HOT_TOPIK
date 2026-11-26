package com.hottopik.entity.subscription;

import com.hottopik.util.PaymentDateTimeUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

/**
 * Quyền lợi học tập. Chỉ quyền lợi is_active = true mới được hiển thị/quảng cáo:
 * quyền lợi chưa được triển khai trong code được seed ở trạng thái tắt.
 */
@Entity
@Table(name = "subscription_features", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "UQ_subscription_features_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Nationalized
    @Column(nullable = false, length = 150)
    private String name;

    @Nationalized
    @Column(length = 500)
    private String description;

    /** FREE = có trong mọi gói; PREMIUM = chỉ gói Premium. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PlanTier tier;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = PaymentDateTimeUtil.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = PaymentDateTimeUtil.now();
    }
}
