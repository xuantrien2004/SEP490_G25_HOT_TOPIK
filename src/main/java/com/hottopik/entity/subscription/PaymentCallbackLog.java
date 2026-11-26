package com.hottopik.entity.subscription;

import com.hottopik.util.PaymentDateTimeUtil;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.LocalDateTime;

/** Nhật ký Return URL / IPN / QueryDR để đối soát. Chỉ lưu hash của payload, không lưu dữ liệu nhạy cảm. */
@Entity
@Table(name = "payment_callback_logs", schema = "dbo",
        indexes = {
                @Index(name = "IX_payment_callback_logs_order", columnList = "order_code, received_at"),
                @Index(name = "IX_payment_callback_logs_event", columnList = "event_key")
        })
@Getter
@Setter
@NoArgsConstructor
public class PaymentCallbackLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String provider = "VNPAY";

    @Column(name = "payment_environment", nullable = false, length = 10)
    private String paymentEnvironment = "SANDBOX";

    /** RETURN, IPN, QUERY. */
    @Column(name = "callback_type", nullable = false, length = 10)
    private String callbackType;

    /** Khóa sự kiện: txnRef + transactionNo + responseCode, dùng để nhận diện callback lặp lại. */
    @Column(name = "event_key", length = 120)
    private String eventKey;

    @Column(name = "order_code", length = 40)
    private String orderCode;

    @Column(name = "provider_transaction_no", length = 40)
    private String providerTransactionNo;

    /** RECEIVED, PROCESSED, DUPLICATE, INVALID_SIGNATURE, REJECTED, ERROR. */
    @Column(name = "processing_status", nullable = false, length = 30)
    private String processingStatus;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Nationalized
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    void onCreate() {
        if (receivedAt == null) receivedAt = PaymentDateTimeUtil.now();
    }
}
