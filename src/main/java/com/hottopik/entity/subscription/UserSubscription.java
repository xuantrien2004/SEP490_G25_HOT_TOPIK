package com.hottopik.entity.subscription;

import com.hottopik.util.PaymentDateTimeUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Premium của học viên. Tái sử dụng bảng dbo.subscriptions đã có (UserRepository.countActiveSubscriptions
 * đọc bảng này lúc đăng nhập), chỉ bổ sung cột plan_id, plan_version_id, version, created_at, updated_at.
 * Các cột mới đều cho phép NULL để không làm hỏng dữ liệu cũ.
 */
@Entity
@Table(name = "subscriptions", schema = "dbo",
        indexes = @Index(name = "IX_subscriptions_user_status", columnList = "user_id, status, ends_at"))
@Getter
@Setter
@NoArgsConstructor
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", foreignKey = @ForeignKey(name = "FK_subscriptions_plan"))
    private SubscriptionPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_version_id", foreignKey = @ForeignKey(name = "FK_subscriptions_plan_version"))
    private SubscriptionPlanVersion planVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    /** null chỉ có ở dữ liệu cũ (Premium không thời hạn). */
    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Version
    private Long version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = PaymentDateTimeUtil.now();
        updatedAt = createdAt;
        if (version == null) version = 0L;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = PaymentDateTimeUtil.now();
    }

    /** Hợp lệ tại thời điểm now: ACTIVE, đã bắt đầu và chưa hết hạn (không phụ thuộc Scheduler). */
    public boolean isValidAt(LocalDateTime now) {
        return status == SubscriptionStatus.ACTIVE
                && startsAt != null && !startsAt.isAfter(now)
                && (endsAt == null || endsAt.isAfter(now));
    }
}
