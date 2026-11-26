package com.hottopik.entity.subscription;

import com.hottopik.util.PaymentDateTimeUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

/**
 * Lịch sử kích hoạt / gia hạn / hết hạn Premium. Unique index lọc (order_id, action ACTIVATED/RENEWED)
 * đảm bảo một đơn hàng chỉ cấp hoặc gia hạn Premium một lần.
 */
@Entity
@Table(name = "subscription_history", schema = "dbo",
        indexes = {
                @Index(name = "IX_subscription_history_user", columnList = "user_id, created_at"),
                @Index(name = "IX_subscription_history_order", columnList = "order_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private Long subscriptionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "order_id", updatable = false)
    private Long orderId;

    @Column(name = "plan_version_id", updatable = false)
    private Long planVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20, updatable = false)
    private SubscriptionAction actionType;

    @Column(name = "old_end_date", updatable = false)
    private LocalDateTime oldEndDate;

    @Column(name = "new_end_date", updatable = false)
    private LocalDateTime newEndDate;

    @Column(name = "duration_days", updatable = false)
    private Integer durationDays;

    @Nationalized
    @Column(length = 500, updatable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = PaymentDateTimeUtil.now();
    }

    // ---- Hiển thị (không ánh xạ cột)
    public String getCreatedAtText() { return PaymentDateTimeUtil.display(createdAt); }
    public String getOldEndDateText() { return PaymentDateTimeUtil.display(oldEndDate); }
    public String getNewEndDateText() { return PaymentDateTimeUtil.display(newEndDate); }
}
