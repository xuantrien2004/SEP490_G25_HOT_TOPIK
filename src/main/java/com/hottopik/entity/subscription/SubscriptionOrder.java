package com.hottopik.entity.subscription;

import com.hottopik.util.PaymentDateTimeUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

/**
 * Đơn mua gói học. Lưu snapshot tên/giá/thời hạn tại thời điểm mua và tham chiếu phiên bản gói (bất biến),
 * nên thay đổi giá về sau không ảnh hưởng đơn hàng cũ.
 */
@Entity
@Table(name = "subscription_orders", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "UQ_subscription_orders_code", columnNames = "order_code"),
        indexes = {
                @Index(name = "IX_subscription_orders_user", columnList = "user_id, created_at"),
                @Index(name = "IX_subscription_orders_status_exp", columnList = "status, expires_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, length = 30, updatable = false)
    private String orderCode;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "FK_subscription_orders_plan"))
    private SubscriptionPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_version_id", updatable = false, foreignKey = @ForeignKey(name = "FK_subscription_orders_version"))
    private SubscriptionPlanVersion planVersion;

    @Nationalized
    @Column(name = "plan_name_snapshot", nullable = false, length = 100, updatable = false)
    private String planNameSnapshot;

    @Column(name = "plan_price_snapshot", nullable = false, updatable = false)
    private Long planPriceSnapshot;

    @Column(name = "duration_days_snapshot", nullable = false, updatable = false)
    private Integer durationDaysSnapshot;

    @Column(name = "total_amount", nullable = false, updatable = false)
    private Long totalAmount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency = "VND";

    @Column(name = "payment_environment", nullable = false, length = 10, updatable = false)
    private String paymentEnvironment = "SANDBOX";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        createdAt = PaymentDateTimeUtil.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = PaymentDateTimeUtil.now();
    }

    public boolean isPendingPayment() {
        return status == OrderStatus.PENDING_PAYMENT;
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    // ---- Hiển thị (không ánh xạ cột)
    public String getTotalAmountText() { return com.hottopik.util.PaymentAmountUtil.format(totalAmount); }
    public String getCreatedAtText() { return PaymentDateTimeUtil.display(createdAt); }
    public String getExpiresAtText() { return PaymentDateTimeUtil.display(expiresAt); }
    public String getPaidAtText() { return PaymentDateTimeUtil.display(paidAt); }
}
