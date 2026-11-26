package com.hottopik.entity.subscription;

import com.hottopik.util.PaymentDateTimeUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

/**
 * Yêu cầu thay đổi gói học (giá, thời hạn, quyền lợi, trạng thái...). Lưu bền trong database vì hộp duyệt
 * chung ApprovalService hiện chỉ nằm trong bộ nhớ; mỗi yêu cầu PENDING được đăng ký vào hộp duyệt đó
 * (approval_ref_id) để Admin duyệt ở cùng một chỗ. Unique index lọc: mỗi gói tối đa một yêu cầu PENDING.
 */
@Entity
@Table(name = "plan_change_requests", schema = "dbo",
        indexes = @Index(name = "IX_plan_change_requests_status", columnList = "status, requested_at"))
@Getter
@Setter
@NoArgsConstructor
public class PlanChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "FK_plan_change_requests_plan"))
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20, updatable = false)
    private PlanRequestType requestType;

    /** JSON dữ liệu trước thay đổi. */
    @Nationalized
    @Lob
    @Column(name = "old_data", updatable = false)
    private String oldData;

    /** JSON dữ liệu đề xuất. */
    @Nationalized
    @Lob
    @Column(name = "new_data", updatable = false)
    private String newData;

    /** Giá trị SubscriptionPlan.version lúc gửi; khác lúc duyệt = gói đã bị thay đổi, không ghi đè. */
    @Column(name = "expected_plan_version", nullable = false, updatable = false)
    private Long expectedPlanVersion;

    @Nationalized
    @Column(nullable = false, length = 1000, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanRequestStatus status = PlanRequestStatus.PENDING;

    @Column(name = "requested_by", updatable = false)
    private Long requestedBy;

    @Nationalized
    @Column(name = "requested_by_name", length = 100, updatable = false)
    private String requestedByName;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Nationalized
    @Column(name = "reviewed_by_name", length = 100)
    private String reviewedByName;

    @Nationalized
    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** Id của yêu cầu tương ứng trong ApprovalService (bộ nhớ); được đăng ký lại sau mỗi lần khởi động. */
    @Column(name = "approval_ref_id")
    private Long approvalRefId;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        requestedAt = PaymentDateTimeUtil.now();
    }
}
