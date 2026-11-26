package com.hottopik.dto.subscription;

import com.hottopik.util.PaymentAmountUtil;
import com.hottopik.util.PaymentDateTimeUtil;

import java.time.LocalDateTime;
import java.util.List;

/** Gói học dùng cho giao diện (không expose Entity). */
public record SubscriptionPlanDTO(
        Long id,
        String code,
        String name,
        String description,
        String tier,
        Long price,
        Integer durationDays,
        String status,
        String statusLabel,
        boolean featured,
        int sortOrder,
        int currentVersionNo,
        Long version,
        LocalDateTime updatedAt,
        String updatedBy,
        List<PlanFeatureDTO> features,
        String pendingRequestType,
        String pendingRequestLabel
) {

    public boolean isPremium() {
        return "PREMIUM".equals(tier);
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isDraft() {
        return "DRAFT".equals(status);
    }

    public boolean hasPendingRequest() {
        return pendingRequestType != null;
    }

    public String getPriceText() {
        return price != null && price == 0 ? "0đ" : PaymentAmountUtil.format(price);
    }

    public String getDurationText() {
        return durationDays == null ? "Không giới hạn" : durationDays + " ngày";
    }

    /** Giá quy đổi theo tháng (30 ngày) để học viên dễ so sánh. */
    public String getMonthlyPriceText() {
        if (price == null || durationDays == null || durationDays <= 0 || price == 0) return null;
        return PaymentAmountUtil.format(Math.round(price * 30.0 / durationDays)) + "/tháng";
    }

    public String getUpdatedAtText() {
        return PaymentDateTimeUtil.display(updatedAt);
    }
}
