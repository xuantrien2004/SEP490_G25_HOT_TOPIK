package com.hottopik.entity.subscription;

import com.hottopik.util.PaymentDateTimeUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

/**
 * Gói học đang/sắp bán. Giá và thời hạn ở đây là giá "chính thức" hiện tại; mọi thay đổi trên gói đã bán
 * phải đi qua PlanChangeRequest và được Admin duyệt. Mỗi lần duyệt sinh một SubscriptionPlanVersion bất biến.
 */
@Entity
@Table(name = "subscription_plans", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "UQ_subscription_plans_code", columnNames = "code"),
        indexes = @Index(name = "IX_subscription_plans_status", columnList = "status, sort_order"))
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Nationalized
    @Column(nullable = false, length = 100)
    private String name;

    @Nationalized
    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PlanTier tier = PlanTier.PREMIUM;

    /** VND, số nguyên. Gói Free = 0, gói Premium > 0 (có check constraint). */
    @Column(nullable = false)
    private Long price;

    /** null = không giới hạn (chỉ dùng cho gói Free). */
    @Column(name = "duration_days")
    private Integer durationDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionPlanStatus status = SubscriptionPlanStatus.DRAFT;

    @Column(name = "is_featured", nullable = false)
    private boolean featured;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Số phiên bản đã được duyệt gần nhất (0 = chưa từng được duyệt). */
    @Column(name = "current_version_no", nullable = false)
    private int currentVersionNo;

    /** Optimistic locking: yêu cầu phê duyệt lưu giá trị này để phát hiện gói đã bị thay đổi. */
    @Version
    @Column(nullable = false)
    private Long version;

    @Nationalized
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Nationalized
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

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

    public boolean isPremium() {
        return tier == PlanTier.PREMIUM;
    }

    public boolean isPurchasable() {
        return status == SubscriptionPlanStatus.ACTIVE && isPremium();
    }
}
