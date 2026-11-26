package com.hottopik.service.subscription;

import com.hottopik.entity.subscription.SubscriptionPlan;

import java.util.List;

/** Dữ liệu gói học lưu dạng JSON trong old_data / new_data của yêu cầu phê duyệt. */
public record PlanData(
        String name,
        String description,
        Long price,
        Integer durationDays,
        Boolean featured,
        Integer sortOrder,
        String status,
        List<String> featureCodes
) {

    /** Chụp dữ liệu hiện tại của gói. */
    public static PlanData of(SubscriptionPlan plan, List<String> featureCodes) {
        return new PlanData(plan.getName(), plan.getDescription(), plan.getPrice(), plan.getDurationDays(),
                plan.isFeatured(), plan.getSortOrder(), plan.getStatus().name(), featureCodes);
    }

    /** Bản sao với trạng thái mới. */
    public PlanData withStatus(String newStatus) {
        return new PlanData(name, description, price, durationDays, featured, sortOrder, newStatus, featureCodes);
    }

    /** Bản sao với giá mới. */
    public PlanData withPrice(Long newPrice) {
        return new PlanData(name, description, newPrice, durationDays, featured, sortOrder, status, featureCodes);
    }
}
