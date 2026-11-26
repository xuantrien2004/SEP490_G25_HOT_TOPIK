package com.hottopik.dto.subscription;

import com.hottopik.util.PaymentAmountUtil;
import com.hottopik.util.PaymentDateTimeUtil;

import java.time.LocalDateTime;

/** Một lần thanh toán Sandbox (dùng cho Transaction Management và lịch sử giao dịch). */
public record PaymentTransactionDTO(
        Long id,
        String orderCode,
        String txnRef,
        String transactionNo,
        Long userId,
        String buyerName,
        String buyerEmail,
        String planName,
        Long expectedAmount,
        Long paidAmount,
        String bankCode,
        String responseCode,
        String transactionStatus,
        String status,
        String statusLabel,
        String orderStatus,
        String orderStatusLabel,
        String failureReason,
        String environment,
        LocalDateTime createdAt,
        LocalDateTime paidAt
) {

    public String getAmountText() {
        return PaymentAmountUtil.format(expectedAmount);
    }

    public String getPaidAmountText() {
        return PaymentAmountUtil.format(paidAmount);
    }

    public String getCreatedAtText() {
        return PaymentDateTimeUtil.display(createdAt);
    }

    public String getPaidAtText() {
        return PaymentDateTimeUtil.display(paidAt);
    }
}
