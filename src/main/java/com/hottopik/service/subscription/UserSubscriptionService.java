package com.hottopik.service.subscription;

import com.hottopik.dto.subscription.PlanFeatureDTO;
import com.hottopik.dto.subscription.UserSubscriptionDTO;
import com.hottopik.entity.User;
import com.hottopik.entity.subscription.*;
import com.hottopik.repository.UserRepository;
import com.hottopik.repository.subscription.SubscriptionHistoryRepository;
import com.hottopik.repository.subscription.SubscriptionPlanFeatureRepository;
import com.hottopik.repository.subscription.UserSubscriptionRepository;
import com.hottopik.util.PaymentDateTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Kích hoạt / gia hạn / hết hạn Premium. Mỗi đơn hàng chỉ được cấp hoặc gia hạn một lần
 * (kiểm tra lịch sử + unique index lọc trên subscription_history.order_id).
 */
@Slf4j
@Service
public class UserSubscriptionService {

    private static final List<SubscriptionAction> GRANT_ACTIONS = List.of(SubscriptionAction.ACTIVATED, SubscriptionAction.RENEWED);

    private final UserSubscriptionRepository subscriptions;
    private final SubscriptionHistoryRepository history;
    private final SubscriptionPlanFeatureRepository planFeatures;
    private final SubscriptionAccessService access;
    private final SubscriptionPlanService planService;
    private final UserRepository userRepository;
    private final BillingAuditService audit;
    private final SubscriptionNotifier notifier;

    public UserSubscriptionService(UserSubscriptionRepository subscriptions, SubscriptionHistoryRepository history,
                                   SubscriptionPlanFeatureRepository planFeatures, SubscriptionAccessService access,
                                   SubscriptionPlanService planService, UserRepository userRepository,
                                   BillingAuditService audit, SubscriptionNotifier notifier) {
        this.subscriptions = subscriptions;
        this.history = history;
        this.planFeatures = planFeatures;
        this.access = access;
        this.planService = planService;
        this.userRepository = userRepository;
        this.audit = audit;
        this.notifier = notifier;
    }

    /**
     * Gọi trong transaction xác nhận thanh toán. Còn Premium hợp lệ thì cộng thêm thời hạn vào ngày hết hạn hiện tại;
     * đã hết hạn thì tính từ thời điểm kích hoạt mới.
     * @return bản ghi subscription sau khi cấp/gia hạn; không làm gì nếu đơn đã được dùng để cấp quyền.
     */
    @Transactional
    public UserSubscription activateOrRenew(SubscriptionOrder order) {
        if (history.existsByOrderIdAndActionTypeIn(order.getId(), GRANT_ACTIONS)) {
            return access.getValidSubscription(order.getUserId()).orElse(null);
        }
        LocalDateTime now = PaymentDateTimeUtil.now();
        int days = order.getDurationDaysSnapshot();
        List<UserSubscription> active = subscriptions.findActiveForUpdate(order.getUserId());

        UserSubscription current = null;
        for (UserSubscription s : active) {
            if (s.isValidAt(now) && (current == null || later(s.getEndsAt(), current.getEndsAt()))) {
                current = s;
            }
        }
        // Bản ghi ACTIVE nhưng đã quá hạn (Scheduler chưa chạy): chuyển EXPIRED trước khi tạo mới.
        for (UserSubscription s : active) {
            if (s != current) {
                LocalDateTime oldEnd = s.getEndsAt();
                s.setStatus(SubscriptionStatus.EXPIRED);
                addHistory(s, null, SubscriptionAction.EXPIRED, oldEnd, oldEnd, null, "Hết hạn trước khi kích hoạt gói mới");
            }
        }
        subscriptions.flush();

        SubscriptionAction action;
        LocalDateTime oldEnd;
        if (current != null) {
            action = SubscriptionAction.RENEWED;
            oldEnd = current.getEndsAt();
            if (oldEnd != null) current.setEndsAt(oldEnd.plusDays(days));
            current.setPlan(order.getPlan());
            if (order.getPlanVersion() != null) current.setPlanVersion(order.getPlanVersion());
        } else {
            action = SubscriptionAction.ACTIVATED;
            oldEnd = null;
            current = new UserSubscription();
            current.setUserId(order.getUserId());
            current.setPlan(order.getPlan());
            current.setPlanVersion(order.getPlanVersion());
            current.setStatus(SubscriptionStatus.ACTIVE);
            current.setStartsAt(now);
            current.setEndsAt(now.plusDays(days));
        }
        current = subscriptions.saveAndFlush(current);
        addHistory(current, order, action, oldEnd, current.getEndsAt(), days,
                (action == SubscriptionAction.ACTIVATED ? "Kích hoạt " : "Gia hạn ") + order.getPlanNameSnapshot()
                        + " (" + days + " ngày) từ đơn " + order.getOrderCode());

        audit.log(BillingActor.SYSTEM, action == SubscriptionAction.ACTIVATED ? "SUBSCRIPTION_ACTIVATED" : "SUBSCRIPTION_RENEWED",
                "USER_SUBSCRIPTION", current.getId(), fmt(oldEnd), fmt(current.getEndsAt()),
                "Premium của user #" + order.getUserId() + " từ đơn " + order.getOrderCode());

        Long userId = order.getUserId();
        String endText = fmt(current.getEndsAt());
        String title = action == SubscriptionAction.ACTIVATED ? "Premium đã được kích hoạt" : "Premium đã được gia hạn";
        String message = "Gói " + order.getPlanNameSnapshot() + " có hiệu lực đến " + endText
                + ". (Thanh toán thử nghiệm VNPay Sandbox – không phát sinh tiền thật.)";
        String type = action == SubscriptionAction.ACTIVATED ? "PREMIUM_ACTIVATED" : "PREMIUM_RENEWED";
        notifier.afterCommit(() -> notifier.notifyUser(userId, type, title, message, "/subscription/my-plan"));
        return current;
    }

    private void addHistory(UserSubscription s, SubscriptionOrder order, SubscriptionAction action,
                            LocalDateTime oldEnd, LocalDateTime newEnd, Integer days, String description) {
        SubscriptionHistory h = new SubscriptionHistory();
        h.setSubscriptionId(s.getId());
        h.setUserId(s.getUserId());
        h.setOrderId(order == null ? null : order.getId());
        h.setPlanVersionId(order != null && order.getPlanVersion() != null ? order.getPlanVersion().getId()
                : s.getPlanVersion() == null ? null : s.getPlanVersion().getId());
        h.setActionType(action);
        h.setOldEndDate(oldEnd);
        h.setNewEndDate(newEnd);
        h.setDurationDays(days);
        h.setDescription(description);
        history.save(h);
    }

    // ------------------------------------------------------------------ đọc dữ liệu

    @Transactional(readOnly = true)
    public UserSubscriptionDTO getCurrentSubscription(Long userId) {
        LocalDateTime now = PaymentDateTimeUtil.now();
        Optional<UserSubscription> sub = access.getValidSubscription(userId);
        if (sub.isEmpty()) {
            List<PlanFeatureDTO> free = planService.getActiveFeatures().stream().filter(f -> !f.isPremiumOnly()).toList();
            return new UserSubscriptionDTO(false, null, userId, null, null, "Free", "FREE", null, null, 0, 0, free);
        }
        UserSubscription s = sub.get();
        long remaining = SubscriptionAccessService.remainingDays(s, now);
        int usedPercent = 0;
        if (s.getEndsAt() != null) {
            long total = Math.max(1, Duration.between(s.getStartsAt(), s.getEndsAt()).toMinutes());
            long used = Math.max(0, Duration.between(s.getStartsAt(), now).toMinutes());
            usedPercent = (int) Math.min(100, used * 100 / total);
        }
        List<PlanFeatureDTO> fs;
        if (s.getPlanVersion() != null) {
            fs = planFeatures.findActiveByVersionIds(List.of(s.getPlanVersion().getId())).stream()
                    .map(pf -> new PlanFeatureDTO(pf.getFeature().getCode(), pf.getFeature().getName(),
                            pf.getFeature().getDescription(), pf.getFeature().getTier().name(), pf.getLimitValue()))
                    .toList();
        } else {
            fs = planService.getActiveFeatures();
        }
        String planName = s.getPlanVersion() != null ? s.getPlanVersion().getName()
                : s.getPlan() != null ? s.getPlan().getName() : "Premium";
        return new UserSubscriptionDTO(true, s.getId(), userId, null, null, planName, s.getStatus().name(),
                s.getStartsAt(), s.getEndsAt(), remaining, usedPercent, fs);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionHistory> getSubscriptionHistory(Long userId) {
        return history.findByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    /** Danh sách học viên đang dùng Premium (cho Operation Manager). */
    @Transactional(readOnly = true)
    public Page<UserSubscriptionDTO> getActivePremiumUsers(int page, int size) {
        LocalDateTime now = PaymentDateTimeUtil.now();
        Page<UserSubscription> result = subscriptions.findAllValid(now,
                PageRequest.of(Math.max(page, 0), size, Sort.by(Sort.Direction.ASC, "endsAt")));
        Map<Long, User> users = userRepository.findAllById(result.getContent().stream().map(UserSubscription::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return result.map(s -> {
            User u = users.get(s.getUserId());
            String planName = s.getPlanVersion() != null ? s.getPlanVersion().getName()
                    : s.getPlan() != null ? s.getPlan().getName() : "Premium (dữ liệu cũ)";
            long remaining = SubscriptionAccessService.remainingDays(s, now);
            return new UserSubscriptionDTO(true, s.getId(), s.getUserId(), u == null ? "—" : u.getDisplayName(),
                    u == null ? "—" : u.getEmail(), planName, s.getStatus().name(), s.getStartsAt(), s.getEndsAt(),
                    remaining, 0, List.of());
        });
    }

    // ------------------------------------------------------------------ Scheduler

    /** Mỗi phút: chuyển Premium quá hạn sang EXPIRED. Quyền truy cập vẫn được kiểm tra theo thời gian thực. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void expireSubscriptions() {
        LocalDateTime now = PaymentDateTimeUtil.now();
        List<UserSubscription> due;
        try {
            due = subscriptions.findTop200ByStatusAndEndsAtLessThanEqual(SubscriptionStatus.ACTIVE, now);
        } catch (Exception e) {
            log.debug("Bỏ qua kiểm tra hết hạn Premium: {}", e.getMessage());
            return;
        }
        for (UserSubscription s : due) {
            s.setStatus(SubscriptionStatus.EXPIRED);
            addHistory(s, null, SubscriptionAction.EXPIRED, s.getEndsAt(), s.getEndsAt(), null, "Premium hết hạn, quay về quyền Free");
            audit.log(BillingActor.SYSTEM, "SUBSCRIPTION_EXPIRED", "USER_SUBSCRIPTION", s.getId(), null, null,
                    "Premium của user #" + s.getUserId() + " hết hạn");
            Long userId = s.getUserId();
            String link = "/subscription/my-plan?expired=" + s.getId();
            notifier.afterCommit(() -> notifier.notifyOnce(userId, "PREMIUM_EXPIRED", "Premium đã hết hạn",
                    "Gói Premium của bạn đã hết hạn. Dữ liệu và tiến độ học vẫn được giữ nguyên – gia hạn để tiếp tục dùng quyền lợi Premium.",
                    link));
        }
    }

    /** Mỗi giờ: nhắc gia hạn trước 3 ngày và 1 ngày (mỗi mốc gửi một lần). */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 90_000)
    @Transactional(readOnly = true)
    public void remindExpiring() {
        LocalDateTime now = PaymentDateTimeUtil.now();
        try {
            for (UserSubscription s : subscriptions.findEndingBetween(now, now.plusDays(3))) {
                boolean oneDay = !s.getEndsAt().isAfter(now.plusDays(1));
                String type = oneDay ? "PREMIUM_EXPIRING_1D" : "PREMIUM_EXPIRING_3D";
                String link = "/subscription/my-plan?remind=" + s.getId() + "-" + s.getEndsAt().toLocalDate();
                notifier.notifyOnce(s.getUserId(), type, "Premium sắp hết hạn",
                        "Gói Premium của bạn sẽ hết hạn lúc " + fmt(s.getEndsAt()) + ". Gia hạn để không bị gián đoạn quyền lợi.", link);
            }
        } catch (Exception e) {
            log.debug("Bỏ qua nhắc hạn Premium: {}", e.getMessage());
        }
    }

    private static boolean later(LocalDateTime a, LocalDateTime b) {
        if (a == null) return true;
        if (b == null) return false;
        return a.isAfter(b);
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }
}
