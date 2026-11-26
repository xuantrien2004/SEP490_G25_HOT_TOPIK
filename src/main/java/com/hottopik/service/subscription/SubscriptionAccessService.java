package com.hottopik.service.subscription;

import com.hottopik.entity.subscription.PlanTier;
import com.hottopik.entity.subscription.SubscriptionFeature;
import com.hottopik.entity.subscription.SubscriptionPlanFeature;
import com.hottopik.entity.subscription.UserSubscription;
import com.hottopik.repository.subscription.SubscriptionFeatureRepository;
import com.hottopik.repository.subscription.SubscriptionPlanFeatureRepository;
import com.hottopik.repository.subscription.UserSubscriptionRepository;
import com.hottopik.util.PaymentDateTimeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Kiểm tra quyền lợi học tập (Entitlement). Role không đổi khi mua Premium (vẫn là học viên);
 * quyền Premium chỉ dựa vào bản ghi subscription hợp lệ TẠI THỜI ĐIỂM truy cập
 * (ACTIVE + đã bắt đầu + chưa hết hạn), không phụ thuộc Scheduler.
 */
@Service
public class SubscriptionAccessService {

    private final UserSubscriptionRepository subscriptions;
    private final SubscriptionFeatureRepository features;
    private final SubscriptionPlanFeatureRepository planFeatures;

    public SubscriptionAccessService(UserSubscriptionRepository subscriptions, SubscriptionFeatureRepository features,
                                     SubscriptionPlanFeatureRepository planFeatures) {
        this.subscriptions = subscriptions;
        this.features = features;
        this.planFeatures = planFeatures;
    }

    @Transactional(readOnly = true)
    public Optional<UserSubscription> getValidSubscription(Long userId) {
        if (userId == null) return Optional.empty();
        List<UserSubscription> valid = subscriptions.findValid(userId, PaymentDateTimeUtil.now());
        return valid.isEmpty() ? Optional.empty() : Optional.of(valid.get(0));
    }

    public record PremiumSnapshot(String planName, LocalDateTime endsAt) {}

    /** Tên gói + ngày hết hạn của Premium hợp lệ (đọc trong transaction để không lỗi lazy loading). */
    @Transactional(readOnly = true)
    public Optional<PremiumSnapshot> premiumSnapshot(Long userId) {
        return getValidSubscription(userId).map(s -> new PremiumSnapshot(
                s.getPlanVersion() != null ? s.getPlanVersion().getName()
                        : s.getPlan() != null ? s.getPlan().getName() : "Premium",
                s.getEndsAt()));
    }

    public boolean hasActiveSubscription(Long userId) {
        return getValidSubscription(userId).isPresent();
    }

    public boolean isPremium(Long userId) {
        return hasActiveSubscription(userId);
    }

    /**
     * Quyền lợi FREE: ai cũng có. Quyền lợi PREMIUM: cần Premium hợp lệ và phiên bản gói đã mua có quyền lợi đó
     * (dữ liệu cũ không có phiên bản gói thì được hưởng toàn bộ quyền lợi Premium đang bật).
     */
    @Transactional(readOnly = true)
    public boolean hasFeature(Long userId, String featureCode) {
        Optional<SubscriptionFeature> feature = features.findByCode(featureCode).filter(SubscriptionFeature::isActive);
        if (feature.isEmpty()) return false;
        if (feature.get().getTier() == PlanTier.FREE) return true;
        Optional<UserSubscription> sub = getValidSubscription(userId);
        if (sub.isEmpty()) return false;
        if (sub.get().getPlanVersion() == null) return true;
        return planFeatures.findActiveByVersionIds(List.of(sub.get().getPlanVersion().getId())).stream()
                .anyMatch(pf -> pf.getFeature().getCode().equals(featureCode));
    }

    /** Hạn mức của quyền lợi theo gói hiện tại; null = không có hạn mức riêng. */
    @Transactional(readOnly = true)
    public Integer getFeatureLimit(Long userId, String featureCode) {
        return getValidSubscription(userId)
                .filter(s -> s.getPlanVersion() != null)
                .flatMap(s -> planFeatures.findActiveByVersionIds(List.of(s.getPlanVersion().getId())).stream()
                        .filter(pf -> pf.getFeature().getCode().equals(featureCode))
                        .map(SubscriptionPlanFeature::getLimitValue)
                        .findFirst())
                .orElse(null);
    }

    /** Số ngày còn lại (làm tròn lên); 0 nếu không có Premium; -1 nếu Premium không thời hạn (dữ liệu cũ). */
    public long getRemainingDays(Long userId) {
        return getValidSubscription(userId).map(s -> remainingDays(s, PaymentDateTimeUtil.now())).orElse(0L);
    }

    static long remainingDays(UserSubscription s, LocalDateTime now) {
        if (s.getEndsAt() == null) return -1;
        long minutes = Duration.between(now, s.getEndsAt()).toMinutes();
        return minutes <= 0 ? 0 : (minutes + 1439) / 1440;
    }
}
