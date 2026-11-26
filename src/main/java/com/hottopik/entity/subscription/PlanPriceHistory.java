package com.hottopik.entity.subscription;

import com.hottopik.util.PaymentDateTimeUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

/** Lịch sử thay đổi giá (chỉ ghi khi Admin duyệt). Không có chức năng sửa/xóa trên giao diện. */
@Entity
@Table(name = "plan_price_history", schema = "dbo",
        indexes = @Index(name = "IX_plan_price_history_plan", columnList = "plan_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class PlanPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "FK_plan_price_history_plan"))
    private SubscriptionPlan plan;

    @Nationalized
    @Column(name = "plan_name", nullable = false, length = 100, updatable = false)
    private String planName;

    @Column(name = "old_price", updatable = false)
    private Long oldPrice;

    @Column(name = "new_price", nullable = false, updatable = false)
    private Long newPrice;

    @Nationalized
    @Column(length = 1000, updatable = false)
    private String reason;

    @Column(name = "request_id", updatable = false)
    private Long requestId;

    @Nationalized
    @Column(name = "requested_by", length = 100, updatable = false)
    private String requestedBy;

    @Nationalized
    @Column(name = "approved_by", length = 100, updatable = false)
    private String approvedBy;

    @Column(name = "requested_at", updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at", updatable = false)
    private LocalDateTime approvedAt;

    @Column(name = "effective_at", nullable = false, updatable = false)
    private LocalDateTime effectiveAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = PaymentDateTimeUtil.now();
    }

    // ---- Hiển thị (không ánh xạ cột)
    public String getOldPriceText() { return com.hottopik.util.PaymentAmountUtil.format(oldPrice); }
    public String getNewPriceText() { return com.hottopik.util.PaymentAmountUtil.format(newPrice); }
    public String getRequestedAtText() { return PaymentDateTimeUtil.display(requestedAt); }
    public String getApprovedAtText() { return PaymentDateTimeUtil.display(approvedAt); }
    public String getEffectiveAtText() { return PaymentDateTimeUtil.display(effectiveAt); }
}
