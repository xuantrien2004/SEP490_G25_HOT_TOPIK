package com.hottopik.entity.subscription;

import com.hottopik.util.PaymentDateTimeUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

/**
 * Một lần thử thanh toán VNPay Sandbox của đơn hàng (mỗi lần có vnp_TxnRef riêng).
 * Không lưu số thẻ, tên chủ thẻ, OTP hay mật khẩu ngân hàng.
 */
@Entity
@Table(name = "payment_transactions", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "UQ_payment_transactions_ref",
                columnNames = {"payment_provider", "payment_environment", "provider_txn_ref"}),
        indexes = {
                @Index(name = "IX_payment_transactions_order", columnList = "order_id"),
                @Index(name = "IX_payment_transactions_status", columnList = "status, created_at")
        })
@Getter
@Setter
@NoArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "FK_payment_transactions_order"))
    private SubscriptionOrder order;

    @Column(name = "payment_provider", nullable = false, length = 20, updatable = false)
    private String paymentProvider = "VNPAY";

    @Column(name = "payment_environment", nullable = false, length = 10, updatable = false)
    private String paymentEnvironment = "SANDBOX";

    /** vnp_TxnRef gửi sang VNPay. */
    @Column(name = "provider_txn_ref", nullable = false, length = 40, updatable = false)
    private String providerTxnRef;

    /** vnp_CreateDate của lần thử (QueryDR cần giá trị này làm vnp_TransactionDate). */
    @Column(name = "provider_create_date", nullable = false, length = 14, updatable = false)
    private String providerCreateDate;

    /** vnp_TransactionNo do VNPay cấp. */
    @Column(name = "provider_transaction_no", length = 40)
    private String providerTransactionNo;

    @Column(name = "bank_code", length = 20)
    private String bankCode;

    @Column(name = "expected_amount", nullable = false, updatable = false)
    private Long expectedAmount;

    @Column(name = "paid_amount")
    private Long paidAmount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency = "VND";

    @Column(name = "response_code", length = 10)
    private String responseCode;

    @Column(name = "transaction_status", length = 10)
    private String transactionStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Nationalized
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "last_queried_at")
    private LocalDateTime lastQueriedAt;

    /**
     * Kết quả VNPay ghi trên Return URL (đã kiểm tra chữ ký). Chỉ để biết khách báo đã thanh toán khi chưa nhận được
     * IPN/QueryDR; KHÔNG dùng để kích hoạt Premium.
     */
    @Column(name = "return_response_code", length = 10)
    private String returnResponseCode;

    @Column(name = "return_transaction_status", length = 10)
    private String returnTransactionStatus;

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

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    /** Return URL (đã xác minh chữ ký) báo thành công nhưng chưa có xác nhận chính thức qua IPN / QueryDR. */
    public boolean isReturnReportedSuccess() {
        return "00".equals(returnResponseCode) && "00".equals(returnTransactionStatus);
    }

    /** Đang chờ xác nhận chính thức: chưa có dữ liệu thanh toán được xác thực (paidAmount) và chưa SUCCESS. */
    public boolean isAwaitingConfirmation() {
        return isReturnReportedSuccess() && paidAmount == null
                && (status == PaymentStatus.PENDING || status == PaymentStatus.REQUIRES_REVIEW);
    }

    // ---- Hiển thị (không ánh xạ cột)
    public String getExpectedAmountText() { return com.hottopik.util.PaymentAmountUtil.format(expectedAmount); }
    public String getCreatedAtText() { return PaymentDateTimeUtil.display(createdAt); }
    public String getPaidAtText() { return PaymentDateTimeUtil.display(paidAt); }
}
