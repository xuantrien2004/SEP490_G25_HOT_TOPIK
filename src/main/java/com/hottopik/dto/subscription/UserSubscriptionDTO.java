package com.hottopik.dto.subscription;

import com.hottopik.util.PaymentDateTimeUtil;

import java.time.LocalDateTime;
import java.util.List;

/** Gói hiện tại của học viên. premium = false nghĩa là đang dùng quyền Free. */
public record UserSubscriptionDTO(
        boolean premium,
        Long subscriptionId,
        Long userId,
        String userName,
        String userEmail,
        String planName,
        String status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        long remainingDays,
        int usedPercent,
        List<PlanFeatureDTO> features
) {

    public String getStartsAtText() {
        return PaymentDateTimeUtil.display(startsAt);
    }

    public String getEndsAtText() {
        return endsAt == null ? "Không thời hạn" : PaymentDateTimeUtil.display(endsAt);
    }

    public boolean isExpiringSoon() {
        return premium && endsAt != null && remainingDays <= 3;
    }
}
