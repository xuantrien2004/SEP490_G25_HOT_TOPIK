package com.hottopik.service.subscription;

import com.hottopik.config.PaymentProperties;
import com.hottopik.dto.subscription.PaymentStatusDTO;
import com.hottopik.dto.subscription.PaymentTransactionDTO;
import com.hottopik.entity.User;
import com.hottopik.entity.subscription.*;
import com.hottopik.repository.UserRepository;
import com.hottopik.repository.subscription.PaymentTransactionRepository;
import com.hottopik.repository.subscription.SubscriptionOrderRepository;
import com.hottopik.util.OrderCodeGenerator;
import com.hottopik.util.PaymentAmountUtil;
import com.hottopik.util.PaymentDateTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Luồng thanh toán VNPay Sandbox.
 * - Return URL chỉ dùng để hiển thị: KHÔNG kích hoạt Premium dựa vào Return URL.
 * - Kết quả chỉ được ghi nhận khi có căn cứ xác thực: IPN có chữ ký hợp lệ, hoặc API QueryDR (server-to-server).
 * - Cập nhật Payment, Order, Subscription trong cùng một transaction; giao dịch bị khóa ghi (PESSIMISTIC_WRITE)
 *   nên IPN gửi lặp / đồng thời chỉ được xử lý một lần; SUCCESS không bao giờ bị ghi đè.
 */
@Slf4j
@Service
public class PaymentService {

    private final PaymentGateway gateway;
    private final PaymentProperties props;
    private final SubscriptionOrderRepository orders;
    private final PaymentTransactionRepository transactions;
    private final SubscriptionOrderService orderService;
    private final UserSubscriptionService userSubscriptionService;
    private final SubscriptionAccessService access;
    private final PaymentCallbackLogService callbackLog;
    private final UserRepository userRepository;
    private final BillingAuditService audit;
    private final SubscriptionNotifier notifier;

    @Autowired
    @Lazy
    private PaymentService self;

    public PaymentService(PaymentGateway gateway, PaymentProperties props, SubscriptionOrderRepository orders,
                          PaymentTransactionRepository transactions, SubscriptionOrderService orderService,
                          UserSubscriptionService userSubscriptionService, SubscriptionAccessService access,
                          PaymentCallbackLogService callbackLog, UserRepository userRepository,
                          BillingAuditService audit, SubscriptionNotifier notifier) {
        this.gateway = gateway;
        this.props = props;
        this.orders = orders;
        this.transactions = transactions;
        this.orderService = orderService;
        this.userSubscriptionService = userSubscriptionService;
        this.access = access;
        this.callbackLog = callbackLog;
        this.userRepository = userRepository;
        this.audit = audit;
        this.notifier = notifier;
    }

    public record IpnResponse(String rspCode, String message) {}

    // ------------------------------------------------------------------ tạo lần thanh toán

    /** Tạo một lần thanh toán mới cho đơn (lần đầu hoặc thử lại) và trả về URL VNPay Sandbox. */
    @Transactional
    public String createPayment(String orderCode, Long userId, String ipAddr) {
        gateway.assertReady();
        SubscriptionOrder found = orderService.getOrderForUser(orderCode, userId);
        SubscriptionOrder order = orders.findByIdForUpdate(found.getId()).orElseThrow();
        LocalDateTime now = PaymentDateTimeUtil.now();
        if (!order.isPendingPayment()) throw new IllegalStateException("Đơn hàng không còn ở trạng thái chờ thanh toán.");
        if (order.isExpiredAt(now)) throw new IllegalStateException("Đơn hàng đã hết hạn thanh toán. Vui lòng tạo đơn mới.");
        int attempt = (int) transactions.countByOrder_Id(order.getId()) + 1;
        if (attempt > 10) throw new IllegalStateException("Bạn đã thử thanh toán quá nhiều lần cho đơn này. Vui lòng tạo đơn mới.");

        LocalDateTime expire = order.getExpiresAt().isBefore(now.plusMinutes(props.getExpirationMinutes()))
                ? order.getExpiresAt() : now.plusMinutes(props.getExpirationMinutes());
        PaymentTransaction txn = new PaymentTransaction();
        txn.setOrder(order);
        txn.setPaymentProvider(PaymentProperties.PROVIDER);
        txn.setPaymentEnvironment(PaymentProperties.ENVIRONMENT);
        txn.setProviderTxnRef(OrderCodeGenerator.txnRef(order.getOrderCode(), attempt));
        txn.setProviderCreateDate(PaymentDateTimeUtil.toVnp(now));
        txn.setExpectedAmount(order.getTotalAmount());
        txn.setCurrency(order.getCurrency());
        txn.setStatus(PaymentStatus.PENDING);
        transactions.saveAndFlush(txn);

        return gateway.createPaymentUrl(new PaymentGateway.PaymentUrlRequest(txn.getProviderTxnRef(), order.getTotalAmount(),
                orderInfo(order), safeIp(ipAddr), txn.getProviderCreateDate(), PaymentDateTimeUtil.toVnp(expire)));
    }

    // ------------------------------------------------------------------ IPN

    /** VNPay gọi server-to-server. Không cần đăng nhập nhưng bắt buộc chữ ký hợp lệ. */
    public IpnResponse handleIpn(Map<String, String> params) {
        Long logId = callbackLog.received("IPN", params);
        if (!gateway.verifyCallback(params)) {
            log.warn("[SECURITY][VNPay Sandbox IPN] Sai chữ ký, txnRef={}", params.get("vnp_TxnRef"));
            callbackLog.finish(logId, "INVALID_SIGNATURE", "Chữ ký không hợp lệ");
            return new IpnResponse("97", "Invalid Checksum");
        }
        try {
            IpnResponse r = self.processIpn(params);
            callbackLog.finish(logId, "00".equals(r.rspCode()) ? "PROCESSED" : ("02".equals(r.rspCode()) ? "DUPLICATE" : "REJECTED"), r.message());
            return r;
        } catch (Exception e) {
            log.error("[VNPay Sandbox IPN] Lỗi xử lý txnRef={}: {}", params.get("vnp_TxnRef"), e.getMessage());
            callbackLog.finish(logId, "ERROR", e.getMessage());
            return new IpnResponse("99", "Unknown error");
        }
    }

    /** Xử lý IPN đã đúng chữ ký: kiểm tra merchant, giao dịch, số tiền, trạng thái rồi ghi nhận kết quả (idempotent). */
    @Transactional
    public IpnResponse processIpn(Map<String, String> p) {
        if (!gateway.merchantCode().equals(p.get("vnp_TmnCode"))) {
            log.warn("[SECURITY][VNPay Sandbox IPN] Sai mã merchant cho txnRef={}", p.get("vnp_TxnRef"));
            return new IpnResponse("99", "Invalid merchant");
        }
        Optional<PaymentTransaction> found = transactions.findByTxnRefForUpdate(
                PaymentProperties.PROVIDER, PaymentProperties.ENVIRONMENT, p.get("vnp_TxnRef"));
        if (found.isEmpty()) return new IpnResponse("01", "Order not found");
        PaymentTransaction txn = found.get();
        PaymentGateway.GatewayResult r = new PaymentGateway.GatewayResult(true, "00", null, p.get("vnp_TxnRef"),
                p.get("vnp_TmnCode"), p.get("vnp_ResponseCode"), p.get("vnp_TransactionStatus"), p.get("vnp_TransactionNo"),
                p.get("vnp_BankCode"), PaymentAmountUtil.fromVnpAmount(p.get("vnp_Amount")), p.get("vnp_PayDate"));

        if (r.amountVnd() == null || !r.amountVnd().equals(txn.getExpectedAmount())) {
            markAmountMismatch(txn, r);
            return new IpnResponse("04", "Invalid amount");
        }
        if (!canApply(txn, r)) return new IpnResponse("02", "Order already confirmed");
        applyResult(txn, r, null, BillingActor.VNPAY_IPN);
        return new IpnResponse("00", "Confirm Success");
    }

    // ------------------------------------------------------------------ Return URL

    /**
     * Return URL: xác minh chữ ký rồi đối soát bằng QueryDR (phương án dùng được trên localhost khi VNPay không gọi
     * được IPN). Trả về mã đơn để hiển thị, hoặc null nếu dữ liệu không hợp lệ.
     */
    public String handleReturn(Map<String, String> params, String ipAddr) {
        Long logId = callbackLog.received("RETURN", params);
        if (!gateway.verifyCallback(params)) {
            log.warn("[SECURITY][VNPay Sandbox Return] Sai chữ ký, txnRef={}", params.get("vnp_TxnRef"));
            callbackLog.finish(logId, "INVALID_SIGNATURE", "Chữ ký không hợp lệ");
            return null;
        }
        Optional<PaymentTransaction> txn = transactions.findByProviderTxnRef(params.get("vnp_TxnRef"));
        if (txn.isEmpty()) {
            callbackLog.finish(logId, "REJECTED", "Không tìm thấy giao dịch");
            return null;
        }
        if (txn.get().isPending()) {
            try {
                // Ghi nhận tín hiệu từ Return URL (chỉ để không hết hạn nhầm đơn đã trả tiền; không kích hoạt Premium).
                self.recordReturnResult(txn.get().getId(), params.get("vnp_ResponseCode"), params.get("vnp_TransactionStatus"));
                self.reconcile(txn.get().getId(), ipAddr, params.get("vnp_ResponseCode"), BillingActor.SYSTEM, true);
            } catch (Exception e) {
                log.warn("[VNPay Sandbox] Đối soát sau Return URL thất bại: {}", e.getMessage());
            }
        }
        String note = null;
        if (props.isAcceptSignedReturn()) {
            try {
                note = self.confirmFromSignedReturn(params);
            } catch (Exception e) {
                log.warn("[VNPay Sandbox] Không xác nhận được từ Return URL: {}", e.getMessage());
                note = "Lỗi xác nhận từ Return URL";
            }
        }
        callbackLog.finish(logId, "PROCESSED", note);
        return txn.get().getOrder().getOrderCode();
    }

    /**
     * Chỉ chạy khi VNPAY_ACCEPT_SIGNED_RETURN=true. Chữ ký Return URL đã được kiểm tra ở handleReturn; ở đây kiểm tra thêm
     * merchant, số tiền, trạng thái giao dịch rồi ghi nhận kết quả giống IPN (cùng transaction, cùng khóa ghi, idempotent).
     */
    @Transactional
    public String confirmFromSignedReturn(Map<String, String> p) {
        if (!gateway.verifyCallback(p)) return "Sai chữ ký";
        if (!gateway.merchantCode().equals(p.get("vnp_TmnCode"))) return "Sai mã merchant";
        Optional<PaymentTransaction> found = transactions.findByTxnRefForUpdate(
                PaymentProperties.PROVIDER, PaymentProperties.ENVIRONMENT, p.get("vnp_TxnRef"));
        if (found.isEmpty()) return "Không tìm thấy giao dịch";
        PaymentTransaction txn = found.get();
        PaymentGateway.GatewayResult r = new PaymentGateway.GatewayResult(true, "00", null, p.get("vnp_TxnRef"),
                p.get("vnp_TmnCode"), p.get("vnp_ResponseCode"), p.get("vnp_TransactionStatus"), p.get("vnp_TransactionNo"),
                p.get("vnp_BankCode"), PaymentAmountUtil.fromVnpAmount(p.get("vnp_Amount")), p.get("vnp_PayDate"));
        if (r.amountVnd() == null || !r.amountVnd().equals(txn.getExpectedAmount())) {
            markAmountMismatch(txn, r);
            return "Sai số tiền";
        }
        if (!canApply(txn, r)) return "Giao dịch đã được xử lý";
        BillingActor actor = new BillingActor(null, "VNPay Sandbox (Return URL có chữ ký)", "SYSTEM");
        applyResult(txn, r, null, actor);
        audit.log(actor, "PAYMENT_CONFIRMED_BY_SIGNED_RETURN", "PAYMENT_TRANSACTION", txn.getId(), null, txn.getStatus().name(),
                "Xác nhận giao dịch " + txn.getProviderTxnRef() + " bằng dữ liệu Return URL có chữ ký (VNPAY_ACCEPT_SIGNED_RETURN=true)");
        return "Xác nhận bằng Return URL có chữ ký";
    }

    /** Lưu mã kết quả VNPay ghi trên Return URL (đã kiểm tra chữ ký) để không hết hạn nhầm đơn đã trả tiền. */
    @Transactional
    public void recordReturnResult(Long txnId, String responseCode, String transactionStatus) {
        PaymentTransaction txn = transactions.findByIdForUpdate(txnId).orElseThrow();
        if (!txn.isPending()) return;
        txn.setReturnResponseCode(cut(responseCode, 10));
        txn.setReturnTransactionStatus(cut(transactionStatus, 10));
    }

    private static String cut(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }

    // ------------------------------------------------------------------ đối soát QueryDR

    /**
     * Đối soát một giao dịch bằng API QueryDR của VNPay Sandbox. Chỉ ghi nhận kết quả khi phản hồi có chữ ký hợp lệ.
     * @param responseCodeHint vnp_ResponseCode từ Return URL đã xác minh chữ ký (để phân biệt hủy / thất bại).
     * @param force true = bỏ qua giới hạn tần suất 10 giây.
     */
    @Transactional
    public PaymentGateway.GatewayResult reconcile(Long txnId, String ipAddr, String responseCodeHint, BillingActor actor, boolean force) {
        PaymentTransaction txn = transactions.findByIdForUpdate(txnId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy giao dịch."));
        LocalDateTime now = PaymentDateTimeUtil.now();
        if (!force && txn.getLastQueriedAt() != null && Duration.between(txn.getLastQueriedAt(), now).getSeconds() < 10) {
            return null;
        }
        txn.setLastQueriedAt(now);
        SubscriptionOrder order = txn.getOrder();
        PaymentGateway.GatewayResult q = gateway.queryTransaction(txn.getProviderTxnRef(), txn.getProviderCreateDate(),
                orderInfo(order), safeIp(ipAddr));
        Map<String, String> logParams = new HashMap<>();
        logParams.put("vnp_TxnRef", txn.getProviderTxnRef());
        logParams.put("vnp_TransactionNo", q.transactionNo());
        logParams.put("vnp_ResponseCode", q.apiCode());
        logParams.put("vnp_TransactionStatus", q.transactionStatus());
        Long logId = callbackLog.received("QUERY", logParams);
        if (!q.queryOk()) {
            callbackLog.finish(logId, "REJECTED", q.message());
            return q;
        }
        if (q.tmnCode() != null && !q.tmnCode().isBlank() && !gateway.merchantCode().equals(q.tmnCode())) {
            callbackLog.finish(logId, "REJECTED", "Sai mã merchant");
            return q;
        }
        if ("01".equals(q.transactionStatus())) {
            callbackLog.finish(logId, "PROCESSED", "Giao dịch chưa hoàn tất");
            return q; // chưa hoàn tất: giữ trạng thái đang xác minh
        }
        if (q.isSuccess() && (q.amountVnd() == null || !q.amountVnd().equals(txn.getExpectedAmount()))) {
            markAmountMismatch(txn, q);
            callbackLog.finish(logId, "PROCESSED", "Sai số tiền");
            return q;
        }
        if (canApply(txn, q)) {
            applyResult(txn, q, responseCodeHint, actor);
            if (!"SYSTEM".equals(actor.role())) {
                audit.log(actor, "PAYMENT_RECONCILED", "PAYMENT_TRANSACTION", txn.getId(), null, txn.getStatus().name(),
                        "Đối soát VNPay Sandbox giao dịch " + txn.getProviderTxnRef());
            }
        }
        callbackLog.finish(logId, "PROCESSED", null);
        return q;
    }

    /** Không ghi đè SUCCESS; chỉ áp dụng cho giao dịch đang chờ, hoặc thanh toán thành công đến muộn (để đưa vào đối soát). */
    private boolean canApply(PaymentTransaction txn, PaymentGateway.GatewayResult r) {
        if (txn.getStatus() == PaymentStatus.PENDING) return true;
        // Đơn đang "cần đối soát" chỉ vì chờ xác nhận chính thức: IPN / QueryDR thành công đến muộn vẫn được áp dụng.
        if (r.isSuccess() && txn.isAwaitingConfirmation()) return true;
        return r.isSuccess() && (txn.getStatus() == PaymentStatus.EXPIRED || txn.getStatus() == PaymentStatus.CANCELLED
                || txn.getStatus() == PaymentStatus.FAILED);
    }

    /** Ghi nhận kết quả đã xác thực. Gọi trong transaction, giao dịch đã bị khóa ghi. */
    private void applyResult(PaymentTransaction txn, PaymentGateway.GatewayResult r, String responseCodeHint, BillingActor actor) {
        LocalDateTime now = PaymentDateTimeUtil.now();
        txn.setResponseCode(r.responseCode());
        txn.setTransactionStatus(r.transactionStatus());
        if (r.transactionNo() != null && !r.transactionNo().isBlank() && !"0".equals(r.transactionNo())) {
            txn.setProviderTransactionNo(r.transactionNo());
        }
        if (r.bankCode() != null) txn.setBankCode(r.bankCode());
        SubscriptionOrder order = orders.findByIdForUpdate(txn.getOrder().getId()).orElseThrow();

        if (r.isSuccess()) {
            boolean awaiting = txn.isAwaitingConfirmation() && order.getStatus() == OrderStatus.REQUIRES_REVIEW;
            LocalDateTime paidAt = Optional.ofNullable(PaymentDateTimeUtil.fromVnp(r.payDate())).orElse(now);
            txn.setPaidAmount(r.amountVnd());
            txn.setPaidAt(paidAt);
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT || awaiting) {
                txn.setStatus(PaymentStatus.SUCCESS);
                txn.setFailureReason(null);
                order.setStatus(OrderStatus.PAID);
                order.setPaidAt(paidAt);
                orders.save(order);
                transactions.saveAndFlush(txn);
                userSubscriptionService.activateOrRenew(order);
                audit.log(actor, "PAYMENT_SUCCESS", "SUBSCRIPTION_ORDER", order.getId(), null,
                        PaymentAmountUtil.format(r.amountVnd()), "Thanh toán thử nghiệm thành công đơn " + order.getOrderCode()
                                + " (mã VNPay " + txn.getProviderTransactionNo() + ")");
                Long userId = order.getUserId();
                String msg = "Đơn " + order.getOrderCode() + " – " + order.getPlanNameSnapshot() + " đã thanh toán thử nghiệm thành công "
                        + PaymentAmountUtil.format(r.amountVnd()) + " (VNPay Sandbox, không phát sinh tiền thật).";
                String link = "/subscription/payment/result?orderCode=" + order.getOrderCode();
                notifier.afterCommit(() -> notifier.notifyUser(userId, "PAYMENT_SUCCESS", "Thanh toán thử nghiệm thành công", msg, link));
            } else {
                txn.setStatus(PaymentStatus.REQUIRES_REVIEW);
                txn.setFailureReason(order.getStatus() == OrderStatus.PAID
                        ? "Đơn đã được thanh toán bởi một giao dịch khác – cần Admin đối soát."
                        : "VNPay báo thanh toán thành công sau khi đơn đã " + order.getStatus().getLabel().toLowerCase() + " – cần Admin đối soát.");
                if (order.getStatus() != OrderStatus.PAID) {
                    order.setStatus(OrderStatus.REQUIRES_REVIEW);
                    orders.save(order);
                }
                audit.log(actor, "PAYMENT_REQUIRES_REVIEW", "PAYMENT_TRANSACTION", txn.getId(), null, null,
                        txn.getFailureReason() + " Đơn " + order.getOrderCode());
            }
            return;
        }

        String code = r.responseCode();
        if ("07".equals(code)) {
            // VNPay: trừ tiền thành công nhưng giao dịch bị nghi ngờ – không cấp Premium, chờ đối soát.
            txn.setStatus(PaymentStatus.REQUIRES_REVIEW);
            txn.setFailureReason(describe(code));
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) order.setStatus(OrderStatus.REQUIRES_REVIEW);
            audit.log(actor, "PAYMENT_REQUIRES_REVIEW", "PAYMENT_TRANSACTION", txn.getId(), null, null, describe(code));
            return;
        }
        String reasonCode = responseCodeHint != null && !"00".equals(responseCodeHint) && !"00".equals(code) ? responseCodeHint : code;
        PaymentStatus status = "24".equals(reasonCode) ? PaymentStatus.CANCELLED
                : "11".equals(reasonCode) ? PaymentStatus.EXPIRED : PaymentStatus.FAILED;
        if (txn.getStatus() != PaymentStatus.PENDING) return; // kết quả thất bại đến muộn: giữ nguyên
        txn.setStatus(status);
        txn.setFailureReason(describe(reasonCode));
        // Chính sách thử lại: đơn vẫn chờ thanh toán đến khi hết hạn để học viên thử lại.
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT && order.isExpiredAt(now)) {
            order.setStatus(OrderStatus.EXPIRED);
        }
        audit.log(actor, status == PaymentStatus.CANCELLED ? "PAYMENT_CANCELLED" : "PAYMENT_FAILED", "PAYMENT_TRANSACTION",
                txn.getId(), null, null, "Đơn " + order.getOrderCode() + ": " + describe(reasonCode));
        if (status == PaymentStatus.FAILED) {
            Long userId = order.getUserId();
            String msg = "Đơn " + order.getOrderCode() + ": " + describe(reasonCode) + " Bạn có thể thử thanh toán lại.";
            String link = "/subscription/payment/" + order.getOrderCode();
            notifier.afterCommit(() -> notifier.notifyUser(userId, "PAYMENT_FAILED", "Thanh toán thử nghiệm thất bại", msg, link));
        }
    }

    private void markAmountMismatch(PaymentTransaction txn, PaymentGateway.GatewayResult r) {
        if (txn.getStatus() == PaymentStatus.SUCCESS) return;
        txn.setStatus(PaymentStatus.REQUIRES_REVIEW);
        txn.setPaidAmount(r.amountVnd());
        txn.setResponseCode(r.responseCode());
        txn.setTransactionStatus(r.transactionStatus());
        if (r.transactionNo() != null && !"0".equals(r.transactionNo())) txn.setProviderTransactionNo(r.transactionNo());
        txn.setFailureReason("Số tiền VNPay trả về (" + PaymentAmountUtil.format(r.amountVnd()) + ") khác số tiền đơn hàng ("
                + PaymentAmountUtil.format(txn.getExpectedAmount()) + ").");
        SubscriptionOrder order = orders.findByIdForUpdate(txn.getOrder().getId()).orElseThrow();
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) order.setStatus(OrderStatus.REQUIRES_REVIEW);
        audit.log(BillingActor.VNPAY_IPN, "PAYMENT_REQUIRES_REVIEW", "PAYMENT_TRANSACTION", txn.getId(),
                String.valueOf(txn.getExpectedAmount()), String.valueOf(r.amountVnd()), txn.getFailureReason());
    }

    // ------------------------------------------------------------------ trạng thái / hủy / hết hạn

    /** Polling từ trang kết quả: chỉ đọc trạng thái trong DB; nếu đang chờ thì thử đối soát QueryDR (giới hạn 10 giây/lần). */
    public PaymentStatusDTO getPaymentStatus(String orderCode, Long userId, String ipAddr) {
        SubscriptionOrder order = orderService.getOrderForUser(orderCode, userId);
        if (order.isPendingPayment() && gateway.isConfigured()) {
            for (PaymentTransaction t : orderService.getAttempts(order.getId())) {
                if (t.isPending() && Duration.between(t.getCreatedAt(), PaymentDateTimeUtil.now()).getSeconds() >= 5) {
                    try {
                        self.reconcile(t.getId(), ipAddr, null, BillingActor.SYSTEM, false);
                    } catch (Exception e) {
                        log.debug("Đối soát khi polling thất bại: {}", e.getMessage());
                    }
                    break;
                }
            }
            order = orderService.getOrderForUser(orderCode, userId);
        }
        return toStatus(order, orderService.getAttempts(order.getId()));
    }

    /** Đổi trạng thái đơn + lần thanh toán thành trạng thái hiển thị (SUCCESS, VERIFYING, FAILED...) cho trang kết quả. */
    public PaymentStatusDTO toStatus(SubscriptionOrder order, List<PaymentTransaction> attempts) {
        PaymentTransaction latest = attempts.isEmpty() ? null : attempts.get(0);
        PaymentTransaction success = attempts.stream().filter(t -> t.getStatus() == PaymentStatus.SUCCESS).findFirst().orElse(null);
        String state;
        boolean finalState = true;
        boolean canRetry = false;
        String message;
        switch (order.getStatus()) {
            case PAID -> {
                state = "SUCCESS";
                message = "Thanh toán thử nghiệm thành công. Premium đã được kích hoạt.";
            }
            case REQUIRES_REVIEW -> {
                state = "REQUIRES_REVIEW";
                message = latest != null && latest.isAwaitingConfirmation()
                        ? "VNPay đã báo thanh toán thành công nhưng chưa gửi xác nhận chính thức. Premium sẽ tự kích hoạt khi nhận được "
                          + "xác nhận (IPN) hoặc khi ban quản trị đối soát thành công."
                        : "Giao dịch cần được đối soát. Ban quản trị sẽ kiểm tra với VNPay Sandbox; Premium chưa được kích hoạt.";
            }
            case CANCELLED -> {
                state = "CANCELLED";
                message = "Đơn hàng đã được hủy.";
            }
            case EXPIRED -> {
                state = "EXPIRED";
                message = "Đơn hàng đã hết hạn thanh toán. Vui lòng tạo đơn mới.";
            }
            case FAILED -> {
                state = "FAILED";
                message = "Thanh toán thất bại.";
            }
            default -> {
                boolean orderOpen = !order.isExpiredAt(PaymentDateTimeUtil.now());
                if (latest == null) {
                    state = "PENDING";
                    message = "Đơn hàng đang chờ thanh toán.";
                    canRetry = orderOpen;
                } else if (latest.isPending()) {
                    state = "VERIFYING";
                    finalState = false;
                    message = latest.isReturnReportedSuccess()
                            ? "VNPay đã báo thanh toán thành công. Hệ thống đang chờ xác nhận chính thức từ VNPay (IPN) "
                              + "trước khi kích hoạt Premium. Bạn có thể đóng trang này, Premium sẽ tự kích hoạt khi có xác nhận."
                            : "Giao dịch đang được xác minh. Vui lòng chờ trong giây lát.";
                } else if (latest.getStatus() == PaymentStatus.CANCELLED) {
                    state = "CANCELLED";
                    message = "Bạn đã hủy thanh toán trên VNPay Sandbox. Premium chưa được kích hoạt.";
                    canRetry = orderOpen;
                } else if (latest.getStatus() == PaymentStatus.EXPIRED) {
                    state = "EXPIRED";
                    message = "Phiên thanh toán đã hết hạn. Premium chưa được kích hoạt.";
                    canRetry = orderOpen;
                } else {
                    state = "FAILED";
                    message = "Thanh toán thất bại. Premium chưa được kích hoạt.";
                    canRetry = orderOpen;
                }
            }
        }
        PaymentTransaction shown = success != null ? success : latest;
        String premiumStart = null;
        String premiumEnd = null;
        if (order.getStatus() == OrderStatus.PAID) {
            Optional<UserSubscription> sub = access.getValidSubscription(order.getUserId());
            if (sub.isPresent()) {
                premiumStart = PaymentDateTimeUtil.display(sub.get().getStartsAt());
                premiumEnd = sub.get().getEndsAt() == null ? "Không thời hạn" : PaymentDateTimeUtil.display(sub.get().getEndsAt());
            }
        }
        return new PaymentStatusDTO(order.getOrderCode(), state, finalState, message, order.getStatus().name(),
                shown == null ? null : shown.getStatus().name(), order.getPlanNameSnapshot(), order.getTotalAmount(),
                PaymentAmountUtil.format(order.getTotalAmount()), shown == null ? null : shown.getProviderTransactionNo(),
                shown == null || shown.getPaidAt() == null ? null : PaymentDateTimeUtil.display(shown.getPaidAt()),
                premiumStart, premiumEnd, shown == null ? null : shown.getFailureReason(), canRetry);
    }

    /** Học viên hủy đơn chưa thanh toán. Trước khi hủy, đối soát các lần thanh toán đang chờ để tránh hủy nhầm đơn đã trả. */
    public void cancelOrder(String orderCode, Long userId, String ipAddr) {
        SubscriptionOrder order = orderService.getOrderForUser(orderCode, userId);
        if (!order.isPendingPayment()) throw new IllegalStateException("Chỉ hủy được đơn hàng đang chờ thanh toán.");
        if (gateway.isConfigured()) {
            for (PaymentTransaction t : orderService.getAttempts(order.getId())) {
                if (t.isPending()) {
                    try {
                        self.reconcile(t.getId(), ipAddr, null, BillingActor.SYSTEM, true);
                    } catch (Exception e) {
                        log.debug("Đối soát trước khi hủy thất bại: {}", e.getMessage());
                    }
                }
            }
        }
        self.doCancel(order.getId(), userId);
    }

    /** Hủy đơn trong transaction (khóa ghi đơn); các lần thanh toán đang chờ chuyển sang CANCELLED. */
    @Transactional
    public void doCancel(Long orderId, Long userId) {
        SubscriptionOrder order = orders.findByIdForUpdate(orderId).orElseThrow();
        if (!userId.equals(order.getUserId())) throw new NoSuchElementException("Không tìm thấy đơn hàng.");
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Đơn hàng vừa được cập nhật trạng thái (" + order.getStatus().getLabel() + "), không thể hủy.");
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(PaymentDateTimeUtil.now());
        for (PaymentTransaction t : transactions.findByOrder_IdAndStatus(orderId, PaymentStatus.PENDING)) {
            t.setStatus(PaymentStatus.CANCELLED);
            t.setFailureReason("Học viên hủy đơn hàng trên HOT TOPIK.");
        }
        audit.log(new BillingActor(userId, "Học viên #" + userId, "STUDENT"), "PAYMENT_CANCELLED", "SUBSCRIPTION_ORDER",
                orderId, null, null, "Học viên hủy đơn " + order.getOrderCode());
    }

    /** Mỗi phút: đơn quá hạn (thêm 5 phút chờ IPN đến muộn) được đối soát rồi chuyển EXPIRED nếu vẫn chưa thanh toán. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    public void expireOrders() {
        List<SubscriptionOrder> due;
        try {
            due = orders.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(OrderStatus.PENDING_PAYMENT,
                    PaymentDateTimeUtil.now().minusMinutes(5));
        } catch (Exception e) {
            log.debug("Bỏ qua kiểm tra đơn hết hạn: {}", e.getMessage());
            return;
        }
        for (SubscriptionOrder o : due) {
            try {
                if (gateway.isConfigured()) {
                    for (PaymentTransaction t : transactions.findByOrder_IdAndStatus(o.getId(), PaymentStatus.PENDING)) {
                        self.reconcile(t.getId(), "127.0.0.1", null, BillingActor.SYSTEM, true);
                    }
                }
                self.expireOrder(o.getId());
            } catch (Exception e) {
                log.warn("Không xử lý được đơn hết hạn {}: {}", o.getOrderCode(), e.getMessage());
            }
        }
    }

    /** Chuyển đơn quá hạn sang EXPIRED, hoặc REQUIRES_REVIEW nếu VNPay đã báo thành công trên Return URL. */
    @Transactional
    public void expireOrder(Long orderId) {
        SubscriptionOrder order = orders.findByIdForUpdate(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) return;
        List<PaymentTransaction> pending = transactions.findByOrder_IdAndStatus(orderId, PaymentStatus.PENDING);
        boolean reportedPaid = pending.stream().anyMatch(PaymentTransaction::isReturnReportedSuccess);
        // Khách đã được VNPay báo thành công (Return URL có chữ ký) nhưng chưa có IPN/QueryDR: không hết hạn nhầm,
        // chuyển sang cần đối soát; IPN / QueryDR thành công đến sau vẫn kích hoạt Premium.
        order.setStatus(reportedPaid ? OrderStatus.REQUIRES_REVIEW : OrderStatus.EXPIRED);
        for (PaymentTransaction t : pending) {
            if (t.isReturnReportedSuccess()) {
                t.setStatus(PaymentStatus.REQUIRES_REVIEW);
                t.setFailureReason("VNPay báo thanh toán thành công trên trang quay về nhưng chưa nhận được xác nhận chính thức "
                        + "(IPN / QueryDR) – cần đối soát.");
            } else {
                t.setStatus(PaymentStatus.EXPIRED);
                t.setFailureReason("Hết thời gian thanh toán.");
            }
        }
        if (reportedPaid) {
            audit.log(BillingActor.SYSTEM, "PAYMENT_REQUIRES_REVIEW", "SUBSCRIPTION_ORDER", orderId, null, null,
                    "Đơn " + order.getOrderCode() + " chưa xác nhận được qua IPN/QueryDR dù VNPay báo thành công trên Return URL");
        }
    }

    // ------------------------------------------------------------------ quản lý giao dịch

    /** Tìm kiếm giao dịch cho Operation / Admin (từ khóa: mã đơn, mã VNPay, email, tên người mua). */
    @Transactional(readOnly = true)
    public Page<PaymentTransactionDTO> search(String keyword, PaymentStatus status, Long planId,
                                              LocalDateTime from, LocalDateTime to, int page, int size) {
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<Long> userIds = new ArrayList<>();
        userIds.add(-1L); // tránh IN () rỗng
        if (kw != null) {
            userRepository.findTop200ByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(kw, kw)
                    .forEach(u -> userIds.add(u.getId()));
        }
        Page<PaymentTransaction> result = transactions.search(kw, userIds, status, planId, from, to,
                PageRequest.of(Math.max(page, 0), size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return toDTOs(result);
    }

    /** Chi tiết một giao dịch. */
    @Transactional(readOnly = true)
    public Optional<PaymentTransactionDTO> getTransaction(Long id) {
        return transactions.findDetailById(id).map(t -> toDTOs(List.of(t)).get(0));
    }

    /** Thống kê số giao dịch theo trạng thái cho thẻ tóm tắt. */
    @Transactional(readOnly = true)
    public Map<String, Long> transactionSummary() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("total", transactions.count());
        m.put("success", transactions.countByStatus(PaymentStatus.SUCCESS));
        m.put("pending", transactions.countByStatus(PaymentStatus.PENDING));
        m.put("failed", transactions.countByStatus(PaymentStatus.FAILED) + transactions.countByStatus(PaymentStatus.CANCELLED)
                + transactions.countByStatus(PaymentStatus.EXPIRED));
        m.put("review", transactions.countByStatus(PaymentStatus.REQUIRES_REVIEW));
        return m;
    }

    private Page<PaymentTransactionDTO> toDTOs(Page<PaymentTransaction> page) {
        List<PaymentTransactionDTO> list = toDTOs(page.getContent());
        return new org.springframework.data.domain.PageImpl<>(list, page.getPageable(), page.getTotalElements());
    }

    List<PaymentTransactionDTO> toDTOs(List<PaymentTransaction> list) {
        Map<Long, User> users = userRepository.findAllById(list.stream().map(t -> t.getOrder().getUserId()).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().map(t -> {
            SubscriptionOrder o = t.getOrder();
            User u = users.get(o.getUserId());
            return new PaymentTransactionDTO(t.getId(), o.getOrderCode(), t.getProviderTxnRef(), t.getProviderTransactionNo(),
                    o.getUserId(), u == null ? "—" : u.getDisplayName(), u == null ? "—" : u.getEmail(),
                    o.getPlanNameSnapshot(), t.getExpectedAmount(), t.getPaidAmount(), t.getBankCode(), t.getResponseCode(),
                    t.getTransactionStatus(), t.getStatus().name(), t.getStatus().getLabel(), o.getStatus().name(),
                    o.getStatus().getLabel(), t.getFailureReason(), t.getPaymentEnvironment(), t.getCreatedAt(), t.getPaidAt());
        }).toList();
    }

    // ------------------------------------------------------------------ helpers

    private static String orderInfo(SubscriptionOrder order) {
        // vnp_OrderInfo: tiếng Việt không dấu, không ký tự đặc biệt.
        return "Thanh toan don hang " + order.getOrderCode();
    }

    private static String safeIp(String ip) {
        if (ip == null || ip.isBlank() || ip.contains(":")) return "127.0.0.1";
        return ip.length() > 45 ? ip.substring(0, 45) : ip;
    }

    /** Ý nghĩa mã phản hồi VNPay (theo bảng mã lỗi trong tài liệu VNPay 2.1.0, cần đối chiếu khi tài liệu cập nhật). */
    public static String describe(String code) {
        if (code == null) return "Không xác định được kết quả giao dịch.";
        return switch (code) {
            case "00" -> "Giao dịch thành công.";
            case "07" -> "VNPay báo trừ tiền thành công nhưng giao dịch bị nghi ngờ – cần đối soát.";
            case "09" -> "Thẻ/Tài khoản chưa đăng ký dịch vụ InternetBanking.";
            case "10" -> "Xác thực thông tin thẻ/tài khoản không đúng quá 3 lần.";
            case "11" -> "Đã hết hạn chờ thanh toán.";
            case "12" -> "Thẻ/Tài khoản bị khóa.";
            case "13" -> "Nhập sai mật khẩu xác thực giao dịch (OTP).";
            case "24" -> "Bạn đã hủy giao dịch trên VNPay Sandbox.";
            case "51" -> "Tài khoản không đủ số dư.";
            case "65" -> "Tài khoản đã vượt quá hạn mức giao dịch trong ngày.";
            case "75" -> "Ngân hàng thanh toán đang bảo trì.";
            case "79" -> "Nhập sai mật khẩu thanh toán quá số lần quy định.";
            case "01" -> "Giao dịch chưa hoàn tất.";
            case "02" -> "Giao dịch bị lỗi.";
            default -> "Giao dịch không thành công (mã " + code + ").";
        };
    }
}
