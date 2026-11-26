package com.hottopik.dto.subscription;

/**
 * Trạng thái hiển thị của đơn hàng cho trang kết quả / polling.
 * state: SUCCESS, FAILED, CANCELLED, EXPIRED, VERIFYING, PENDING, REQUIRES_REVIEW.
 * finalState = true thì frontend dừng polling.
 */
public record PaymentStatusDTO(
        String orderCode,
        String state,
        boolean finalState,
        String message,
        String orderStatus,
        String paymentStatus,
        String planName,
        Long amount,
        String amountText,
        String transactionNo,
        String paidAtText,
        String premiumStartText,
        String premiumEndText,
        String failureReason,
        boolean canRetry
) {}
