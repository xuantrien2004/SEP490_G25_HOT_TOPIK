package com.hottopik.entity.subscription;

/** Loại gói: miễn phí hoặc Premium (các gói Premium cùng nhóm quyền lợi, chỉ khác giá và thời hạn). */
public enum PlanTier {
    FREE("Miễn phí"),
    PREMIUM("Premium");

    private final String label;

    PlanTier(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
