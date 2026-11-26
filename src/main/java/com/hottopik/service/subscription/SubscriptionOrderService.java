package com.hottopik.service.subscription;

import com.hottopik.config.PaymentProperties;
import com.hottopik.entity.subscription.*;
import com.hottopik.repository.subscription.PaymentTransactionRepository;
import com.hottopik.repository.subscription.SubscriptionOrderRepository;
import com.hottopik.repository.subscription.SubscriptionPlanRepository;
import com.hottopik.repository.subscription.SubscriptionPlanVersionRepository;
import com.hottopik.util.OrderCodeGenerator;
import com.hottopik.util.PaymentAmountUtil;
import com.hottopik.util.PaymentDateTimeUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Đơn mua gói học. Giá luôn lấy từ database (không tin giá frontend gửi lên) và được chốt vào snapshot của đơn.
 * Tạo đơn KHÔNG kích hoạt Premium.
 */
@Service
public class SubscriptionOrderService {

    private final SubscriptionOrderRepository orders;
    private final SubscriptionPlanRepository plans;
    private final SubscriptionPlanVersionRepository versions;
    private final PaymentTransactionRepository transactions;
    private final PaymentProperties props;
    private final BillingAuditService audit;

    public SubscriptionOrderService(SubscriptionOrderRepository orders, SubscriptionPlanRepository plans,
                                    SubscriptionPlanVersionRepository versions, PaymentTransactionRepository transactions,
                                    PaymentProperties props, BillingAuditService audit) {
        this.orders = orders;
        this.plans = plans;
        this.versions = versions;
        this.transactions = transactions;
        this.props = props;
        this.audit = audit;
    }

    /** Đơn chờ thanh toán còn hiệu lực của học viên cho gói này (để trang checkout gợi ý tiếp tục thanh toán). */
    @Transactional(readOnly = true)
    public Optional<SubscriptionOrder> findReusablePendingOrder(Long userId, Long planId) {
        return orders.findFirstByUserIdAndPlan_IdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
                userId, planId, OrderStatus.PENDING_PAYMENT, PaymentDateTimeUtil.now());
    }

    /**
     * Tạo đơn (hoặc tái sử dụng đơn chờ thanh toán còn hiệu lực của cùng gói – chống bấm nhiều lần).
     * @param expectedPrice giá học viên nhìn thấy khi xác nhận; khác giá hiện tại thì ném PriceChangedException.
     */
    @Transactional
    public SubscriptionOrder createOrder(Long userId, Long planId, Long expectedPrice, BillingActor actor) {
        if (userId == null) throw new SecurityException("Vui lòng đăng nhập để mua gói học.");
        SubscriptionPlan plan = plans.findById(planId).filter(SubscriptionPlan::isPurchasable)
                .orElseThrow(() -> new IllegalArgumentException("Gói học không tồn tại hoặc đã ngừng bán."));
        LocalDateTime now = PaymentDateTimeUtil.now();

        for (SubscriptionOrder o : orders.findByUserIdAndPlan_IdAndStatus(userId, planId, OrderStatus.PENDING_PAYMENT)) {
            if (!o.isExpiredAt(now)) {
                return o; // đơn còn hiệu lực: dùng lại với giá đã chốt
            }
            // Đơn quá hạn: chuyển EXPIRED. Nếu sau đó VNPay báo đã thanh toán, giao dịch sẽ vào REQUIRES_REVIEW để đối soát.
            o.setStatus(OrderStatus.EXPIRED);
            for (PaymentTransaction t : transactions.findByOrder_IdAndStatus(o.getId(), PaymentStatus.PENDING)) {
                t.setStatus(PaymentStatus.EXPIRED);
                t.setFailureReason("Hết thời gian thanh toán.");
            }
            orders.saveAndFlush(o);
        }

        if (expectedPrice == null || !expectedPrice.equals(plan.getPrice())) {
            throw new PriceChangedException(plan.getPrice());
        }
        SubscriptionPlanVersion version = versions.findByPlan_IdAndVersionNumber(plan.getId(), plan.getCurrentVersionNo())
                .orElseThrow(() -> new IllegalStateException("Gói học chưa có phiên bản được phê duyệt."));

        SubscriptionOrder order = new SubscriptionOrder();
        order.setOrderCode(newUniqueCode());
        order.setUserId(userId);
        order.setPlan(plan);
        order.setPlanVersion(version);
        order.setPlanNameSnapshot(version.getName());
        order.setPlanPriceSnapshot(version.getPrice());
        order.setDurationDaysSnapshot(version.getDurationDays());
        order.setTotalAmount(version.getPrice());
        order.setCurrency(PaymentProperties.CURRENCY);
        order.setPaymentEnvironment(PaymentProperties.ENVIRONMENT);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setExpiresAt(now.plusMinutes(props.getExpirationMinutes()));
        order = orders.saveAndFlush(order);
        audit.log(actor, "CREATE_PAYMENT_ORDER", "SUBSCRIPTION_ORDER", order.getId(), null,
                PaymentAmountUtil.format(order.getTotalAmount()),
                "Tạo đơn " + order.getOrderCode() + " – " + order.getPlanNameSnapshot() + " (Sandbox)");
        return order;
    }

    private String newUniqueCode() {
        for (int i = 0; i < 5; i++) {
            String code = OrderCodeGenerator.newOrderCode();
            if (orders.findByOrderCode(code).isEmpty()) return code;
        }
        throw new IllegalStateException("Không tạo được mã đơn hàng, vui lòng thử lại.");
    }

    /** Chống IDOR: học viên chỉ xem được đơn của mình; đơn của người khác được báo như không tồn tại. */
    @Transactional(readOnly = true)
    public SubscriptionOrder getOrderForUser(String orderCode, Long userId) {
        return orders.findByOrderCode(orderCode)
                .filter(o -> userId != null && userId.equals(o.getUserId()))
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng."));
    }

    /** Đơn hàng của học viên (lọc trạng thái, mới nhất trước). */
    @Transactional(readOnly = true)
    public Page<SubscriptionOrder> getUserOrders(Long userId, OrderStatus status, int page, int size) {
        return orders.findByUser(userId, status, PageRequest.of(Math.max(page, 0), size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /** Các lần thanh toán của một đơn, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<PaymentTransaction> getAttempts(Long orderId) {
        return transactions.findByOrder_IdOrderByCreatedAtDesc(orderId);
    }
}
