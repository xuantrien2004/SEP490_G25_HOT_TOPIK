package com.hottopik.dto.subscription;

/** Một quyền lợi hiển thị cho người dùng. */
public record PlanFeatureDTO(String code, String name, String description, String tier, Integer limitValue) {

    public boolean isPremiumOnly() {
        return "PREMIUM".equals(tier);
    }
}
