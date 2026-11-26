package com.hottopik.dto.subscription;

import com.hottopik.util.PaymentDateTimeUtil;

import java.time.LocalDateTime;
import java.util.List;

/** Yêu cầu phê duyệt kèm bảng so sánh Trước / Sau. */
public record PlanChangeRequestDTO(
        Long id,
        Long planId,
        String planCode,
        String planName,
        String requestType,
        String requestTypeLabel,
        String status,
        String statusLabel,
        String reason,
        Long requestedBy,
        String requestedByName,
        String reviewedByName,
        String rejectionReason,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt,
        List<CompareRow> rows
) {

    public record CompareRow(String label, String before, String after, boolean changed) {}

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public String getRequestedAtText() {
        return PaymentDateTimeUtil.display(requestedAt);
    }

    public String getReviewedAtText() {
        return PaymentDateTimeUtil.display(reviewedAt);
    }
}
