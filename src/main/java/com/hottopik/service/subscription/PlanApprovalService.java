package com.hottopik.service.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hottopik.dto.ApprovalRequestDTO;
import com.hottopik.dto.subscription.PlanChangeRequestDTO;
import com.hottopik.entity.subscription.*;
import com.hottopik.repository.subscription.*;
import com.hottopik.service.ApprovalDecidedEvent;
import com.hottopik.service.ApprovalService;
import com.hottopik.util.PaymentAmountUtil;
import com.hottopik.util.PaymentDateTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Luồng phê duyệt thay đổi gói học. Tích hợp vào hộp duyệt chung ApprovalService (Admin thấy yêu cầu ở dashboard
 * hiện có lẫn trang /admin/subscription-requests) và áp dụng thay đổi trong ApprovalDecidedEvent – cùng cơ chế
 * module đề thi đang dùng. Dữ liệu yêu cầu lưu bền trong bảng plan_change_requests.
 *
 * Phê duyệt, cập nhật giá, tạo phiên bản mới và ghi lịch sử giá nằm trong cùng một transaction.
 */
@Slf4j
@Service
public class PlanApprovalService {

    public static final String APPROVAL_TARGET = "SUBSCRIPTION_PLAN_REQUEST";
    private static final ThreadLocal<BillingActor> DECIDER = new ThreadLocal<>();

    private final PlanChangeRequestRepository requests;
    private final SubscriptionPlanRepository plans;
    private final SubscriptionPlanVersionRepository versions;
    private final SubscriptionFeatureRepository features;
    private final SubscriptionPlanFeatureRepository planFeatures;
    private final PlanPriceHistoryRepository priceHistory;
    private final ApprovalService approvalService;
    private final BillingAuditService audit;
    private final SubscriptionNotifier notifier;
    private final ObjectMapper json;

    @Autowired
    @Lazy
    private PlanApprovalService self;

    public PlanApprovalService(PlanChangeRequestRepository requests, SubscriptionPlanRepository plans,
                               SubscriptionPlanVersionRepository versions, SubscriptionFeatureRepository features,
                               SubscriptionPlanFeatureRepository planFeatures, PlanPriceHistoryRepository priceHistory,
                               ApprovalService approvalService, BillingAuditService audit,
                               SubscriptionNotifier notifier, ObjectMapper json) {
        this.requests = requests;
        this.plans = plans;
        this.versions = versions;
        this.features = features;
        this.planFeatures = planFeatures;
        this.priceHistory = priceHistory;
        this.approvalService = approvalService;
        this.audit = audit;
        this.notifier = notifier;
        this.json = json;
    }

    // ------------------------------------------------------------------ quyền lợi & phiên bản

    /** Quyền lợi mặc định theo loại gói: Free = quyền lợi FREE; Premium = FREE + PREMIUM (chỉ những quyền lợi đang bật). */
    public List<String> defaultFeatureCodes(PlanTier tier) {
        return features.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .filter(f -> f.getTier() == PlanTier.FREE || tier == PlanTier.PREMIUM)
                .map(SubscriptionFeature::getCode)
                .collect(Collectors.toList());
    }

    /** Mã quyền lợi của phiên bản gói hiện hành (gói chưa duyệt lần nào thì lấy quyền lợi mặc định). */
    public List<String> currentFeatureCodes(SubscriptionPlan plan) {
        if (plan.getCurrentVersionNo() <= 0) return defaultFeatureCodes(plan.getTier());
        return versions.findByPlan_IdAndVersionNumber(plan.getId(), plan.getCurrentVersionNo())
                .map(v -> planFeatures.findActiveByVersionIds(List.of(v.getId())).stream()
                        .map(pf -> pf.getFeature().getCode()).collect(Collectors.toList()))
                .orElseGet(() -> defaultFeatureCodes(plan.getTier()));
    }

    /** Tạo phiên bản mới (bất biến) từ dữ liệu hiện tại của gói. Không sửa phiên bản cũ. */
    @Transactional
    public SubscriptionPlanVersion createVersion(SubscriptionPlan plan, List<String> featureCodes, String actorName) {
        SubscriptionPlanVersion v = new SubscriptionPlanVersion();
        v.setPlan(plan);
        v.setVersionNumber(plan.getCurrentVersionNo() + 1);
        v.setName(plan.getName());
        v.setDescription(plan.getDescription());
        v.setPrice(plan.getPrice());
        v.setDurationDays(plan.getDurationDays());
        v.setEffectiveAt(PaymentDateTimeUtil.now());
        v.setCreatedBy(actorName);
        versions.save(v);
        List<String> codes = featureCodes == null || featureCodes.isEmpty() ? defaultFeatureCodes(plan.getTier()) : featureCodes;
        for (SubscriptionFeature f : features.findByCodeIn(codes)) {
            if (f.getTier() == PlanTier.PREMIUM && plan.getTier() != PlanTier.PREMIUM) continue;
            SubscriptionPlanFeature pf = new SubscriptionPlanFeature();
            pf.setPlanVersion(v);
            pf.setFeature(f);
            planFeatures.save(pf);
        }
        plan.setCurrentVersionNo(v.getVersionNumber());
        return v;
    }

    // ------------------------------------------------------------------ gửi yêu cầu

    /** Tạo yêu cầu phê duyệt: kiểm tra trạng thái gói, dữ liệu, mỗi gói chỉ một yêu cầu PENDING; lưu phiên bản gói để chống ghi đè. */
    @Transactional
    public PlanChangeRequest submit(Long planId, PlanRequestType type, PlanData newData, String reason, BillingActor actor) {
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) throw new IllegalArgumentException("Vui lòng nhập lý do của yêu cầu.");
        if (cleanReason.length() > 1000) throw new IllegalArgumentException("Lý do tối đa 1000 ký tự.");

        SubscriptionPlan plan = plans.findByIdForUpdate(planId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy gói học."));
        if (requests.existsByPlan_IdAndStatus(planId, PlanRequestStatus.PENDING)) {
            throw new IllegalStateException("Gói \"" + plan.getName() + "\" đang có một yêu cầu chờ phê duyệt. "
                    + "Vui lòng chờ Admin xử lý hoặc hủy yêu cầu đó trước.");
        }
        validateTransition(plan, type);
        validateData(plan, type, newData);

        PlanData oldData = PlanData.of(plan, currentFeatureCodes(plan));
        if (type == PlanRequestType.CREATE_PLAN) {
            plan.setStatus(SubscriptionPlanStatus.PENDING_APPROVAL);
            plan.setUpdatedBy(actor.name());
            plans.saveAndFlush(plan);
        }

        PlanChangeRequest req = new PlanChangeRequest();
        req.setPlan(plan);
        req.setRequestType(type);
        req.setOldData(write(oldData));
        req.setNewData(write(newData));
        req.setExpectedPlanVersion(plan.getVersion());
        req.setReason(cleanReason);
        req.setRequestedBy(actor.id());
        req.setRequestedByName(actor.name());
        req = requests.saveAndFlush(req);
        registerInInbox(req, plan);

        audit.log(actor, type == PlanRequestType.UPDATE_PRICE ? "REQUEST_PRICE_CHANGE" : "REQUEST_" + type.name(),
                "SUBSCRIPTION_PLAN", plan.getId(),
                type == PlanRequestType.UPDATE_PRICE ? String.valueOf(oldData.price()) : null,
                type == PlanRequestType.UPDATE_PRICE ? String.valueOf(newData.price()) : null,
                "Gửi yêu cầu \"" + type.getLabel() + "\" cho gói " + plan.getName() + " (yêu cầu #" + req.getId() + ")");

        Long reqId = req.getId();
        String title = "Có yêu cầu " + type.getLabel().toLowerCase() + " mới";
        String message = actor.name() + " đề xuất " + type.getLabel().toLowerCase() + " cho gói " + plan.getName()
                + (type == PlanRequestType.UPDATE_PRICE ? ": " + PaymentAmountUtil.format(oldData.price()) + " → " + PaymentAmountUtil.format(newData.price()) : "") + ".";
        notifier.afterCommit(() -> notifier.notifyAdmins("PLAN_REQUEST_NEW", title, message, "/admin/subscription-requests/" + reqId));
        return req;
    }

    private void validateTransition(SubscriptionPlan plan, PlanRequestType type) {
        SubscriptionPlanStatus s = plan.getStatus();
        boolean approvedBefore = plan.getCurrentVersionNo() > 0;
        boolean ok = switch (type) {
            case CREATE_PLAN -> s == SubscriptionPlanStatus.DRAFT;
            case UPDATE_PRICE, UPDATE_DURATION, UPDATE_BENEFITS, UPDATE_PLAN ->
                    approvedBefore && (s == SubscriptionPlanStatus.ACTIVE || s == SubscriptionPlanStatus.INACTIVE);
            case ACTIVATE_PLAN -> approvedBefore && s == SubscriptionPlanStatus.INACTIVE;
            case DEACTIVATE_PLAN -> s == SubscriptionPlanStatus.ACTIVE;
            case ARCHIVE_PLAN -> approvedBefore && (s == SubscriptionPlanStatus.ACTIVE || s == SubscriptionPlanStatus.INACTIVE);
        };
        if (!ok) {
            throw new IllegalStateException("Không thể gửi yêu cầu \"" + type.getLabel() + "\" khi gói đang ở trạng thái \""
                    + s.getLabel() + "\".");
        }
        if (!plan.isPremium() && type != PlanRequestType.UPDATE_BENEFITS && type != PlanRequestType.UPDATE_PLAN) {
            throw new IllegalStateException("Gói Free không cần tạo đơn hàng và không thay đổi giá/trạng thái bán.");
        }
    }

    private void validateData(SubscriptionPlan plan, PlanRequestType type, PlanData d) {
        if (d == null) throw new IllegalArgumentException("Thiếu dữ liệu đề xuất.");
        if (d.name() == null || d.name().isBlank()) throw new IllegalArgumentException("Tên gói không được để trống.");
        if (plan.isPremium()) {
            if (d.price() == null || d.price() <= 0) throw new IllegalArgumentException("Giá gói Premium phải lớn hơn 0.");
            if (d.durationDays() == null || d.durationDays() <= 0) throw new IllegalArgumentException("Thời hạn gói Premium phải lớn hơn 0.");
        } else if (d.price() == null || d.price() != 0) {
            throw new IllegalArgumentException("Gói Free luôn có giá 0đ.");
        }
        if (type == PlanRequestType.UPDATE_PRICE && Objects.equals(d.price(), plan.getPrice())) {
            throw new IllegalArgumentException("Giá mới không được bằng giá hiện tại.");
        }
    }

    private void registerInInbox(PlanChangeRequest req, SubscriptionPlan plan) {
        try {
            ApprovalRequestDTO dto = approvalService.submitTargetedRequest(
                    req.getRequestedByName() == null ? "Operation Manager" : req.getRequestedByName(),
                    "Operation Manager",
                    "SUBSCRIPTION_" + req.getRequestType().name(),
                    req.getRequestType().getLabel() + ": " + plan.getName(),
                    summarize(req) + " Lý do: " + req.getReason(),
                    APPROVAL_TARGET, req.getId(), null);
            req.setApprovalRefId(dto.getId());
        } catch (Exception e) {
            // Admin vẫn duyệt được ở /admin/subscription-requests.
            log.warn("Không đăng ký được yêu cầu gói học #{} vào hộp duyệt chung: {}", req.getId(), e.getMessage());
        }
    }

    // ------------------------------------------------------------------ duyệt / từ chối

    /**
     * Admin duyệt từ trang /admin/subscription-requests. Đi qua ApprovalService để trạng thái ở hộp duyệt chung
     * đồng bộ; nếu yêu cầu chưa có trong hộp duyệt (ví dụ vừa khởi động lại) thì áp dụng trực tiếp.
     * Không đặt @Transactional ở đây: transaction nằm trong applyDecision để commit trước khi ApprovalService ghi trạng thái.
     */
    public void decide(Long requestId, boolean approve, String note, BillingActor admin) {
        PlanChangeRequest req = requests.findDetailById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu."));
        if (req.getStatus() != PlanRequestStatus.PENDING) throw new IllegalStateException("Yêu cầu này đã được xử lý.");
        if (admin.id() != null && admin.id().equals(req.getRequestedBy())) {
            throw new IllegalStateException("Không được tự phê duyệt yêu cầu do chính mình gửi.");
        }
        if (!approve && (note == null || note.isBlank())) throw new IllegalArgumentException("Vui lòng nhập lý do từ chối.");

        ApprovalRequestDTO inbox = req.getApprovalRefId() == null ? null
                : approvalService.getRequestById(req.getApprovalRefId())
                .filter(r -> APPROVAL_TARGET.equals(r.getTargetType()) && requestId.equals(r.getTargetId())
                        && "PENDING".equalsIgnoreCase(r.getStatus()))
                .orElse(null);
        DECIDER.set(admin);
        try {
            if (inbox != null) {
                approvalService.decideRequest(inbox.getId(), approve, note == null ? null : note.trim(), admin.name());
            } else {
                self.applyDecision(requestId, approve, note, admin);
            }
        } finally {
            DECIDER.remove();
        }
    }

    /** Admin duyệt từ hộp duyệt chung (AdminController /admin/approval/decide) hoặc từ decide(). */
    @EventListener
    public void onApprovalDecided(ApprovalDecidedEvent ev) {
        ApprovalRequestDTO r = ev.request();
        if (!APPROVAL_TARGET.equals(r.getTargetType()) || r.getTargetId() == null) return;
        BillingActor admin = new BillingActor(null, ev.decidedBy() == null ? "Admin" : ev.decidedBy(), "ADMIN");
        self.applyDecision(r.getTargetId(), ev.approved(), ev.note(), admin);
    }

    /** Áp dụng quyết định trong một transaction: duyệt thì cập nhật gói, tạo phiên bản mới, ghi lịch sử giá; từ chối thì giữ nguyên. */
    @Transactional
    public void applyDecision(Long requestId, boolean approve, String note, BillingActor fallbackAdmin) {
        BillingActor admin = DECIDER.get() != null ? DECIDER.get() : fallbackAdmin;
        PlanChangeRequest req = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy yêu cầu thay đổi gói học."));
        if (req.getStatus() != PlanRequestStatus.PENDING) throw new IllegalStateException("Yêu cầu này đã được xử lý.");
        boolean ownRequest = (admin.id() != null && admin.id().equals(req.getRequestedBy()))
                || (admin.name() != null && admin.name().equalsIgnoreCase(req.getRequestedByName()));
        if (ownRequest) throw new IllegalStateException("Không được tự phê duyệt yêu cầu do chính mình gửi.");

        SubscriptionPlan plan = plans.findByIdForUpdate(req.getPlan().getId())
                .orElseThrow(() -> new IllegalStateException("Gói học không còn tồn tại."));
        LocalDateTime now = PaymentDateTimeUtil.now();
        PlanRequestType type = req.getRequestType();
        req.setReviewedBy(admin.id());
        req.setReviewedByName(admin.name());
        req.setReviewedAt(now);

        if (!approve) {
            if (note == null || note.isBlank()) throw new IllegalStateException("Vui lòng nhập lý do từ chối.");
            req.setStatus(PlanRequestStatus.REJECTED);
            req.setRejectionReason(note.trim());
            if (type == PlanRequestType.CREATE_PLAN && plan.getStatus() == SubscriptionPlanStatus.PENDING_APPROVAL) {
                plan.setStatus(SubscriptionPlanStatus.DRAFT);
            }
            audit.log(admin, type == PlanRequestType.UPDATE_PRICE ? "REJECT_PRICE_CHANGE" : "REJECT_PLAN_CHANGE",
                    "SUBSCRIPTION_PLAN", plan.getId(), null, null,
                    "Từ chối yêu cầu #" + req.getId() + " (" + type.getLabel() + ") gói " + plan.getName() + ". Lý do: " + note.trim());
            notifyRequester(req, plan, false);
            return;
        }

        if (!Objects.equals(plan.getVersion(), req.getExpectedPlanVersion())) {
            throw new IllegalStateException("Gói học đã thay đổi kể từ khi yêu cầu được gửi nên không thể áp dụng để tránh ghi đè dữ liệu. "
                    + "Hãy từ chối yêu cầu này để Operation Manager gửi lại theo dữ liệu mới.");
        }
        PlanData data = read(req.getNewData());
        Long oldPrice = plan.getPrice();
        String action;
        switch (type) {
            case CREATE_PLAN -> {
                applyData(plan, data);
                plan.setStatus(SubscriptionPlanStatus.ACTIVE);
                createVersion(plan, data.featureCodes(), admin.name());
                action = "CREATE_SUBSCRIPTION_PLAN";
            }
            case UPDATE_PRICE, UPDATE_DURATION, UPDATE_BENEFITS, UPDATE_PLAN -> {
                applyData(plan, data);
                createVersion(plan, data.featureCodes(), admin.name());
                action = !Objects.equals(oldPrice, plan.getPrice()) ? "APPROVE_PRICE_CHANGE" : "UPDATE_SUBSCRIPTION_PLAN";
            }
            case ACTIVATE_PLAN -> {
                plan.setStatus(SubscriptionPlanStatus.ACTIVE);
                action = "ACTIVATE_PLAN";
            }
            case DEACTIVATE_PLAN -> {
                plan.setStatus(SubscriptionPlanStatus.INACTIVE);
                action = "DEACTIVATE_PLAN";
            }
            case ARCHIVE_PLAN -> {
                plan.setStatus(SubscriptionPlanStatus.ARCHIVED);
                action = "DEACTIVATE_PLAN";
            }
            default -> throw new IllegalStateException("Loại yêu cầu không hỗ trợ.");
        }
        plan.setUpdatedBy(admin.name());
        plans.save(plan);

        if (type != PlanRequestType.CREATE_PLAN && !Objects.equals(oldPrice, plan.getPrice())) {
            PlanPriceHistory h = new PlanPriceHistory();
            h.setPlan(plan);
            h.setPlanName(plan.getName());
            h.setOldPrice(oldPrice);
            h.setNewPrice(plan.getPrice());
            h.setReason(req.getReason());
            h.setRequestId(req.getId());
            h.setRequestedBy(req.getRequestedByName());
            h.setApprovedBy(admin.name());
            h.setRequestedAt(req.getRequestedAt());
            h.setApprovedAt(now);
            h.setEffectiveAt(now);
            priceHistory.save(h);
        }
        req.setStatus(PlanRequestStatus.APPROVED);
        audit.log(admin, action, "SUBSCRIPTION_PLAN", plan.getId(),
                action.equals("APPROVE_PRICE_CHANGE") ? String.valueOf(oldPrice) : null,
                action.equals("APPROVE_PRICE_CHANGE") ? String.valueOf(plan.getPrice()) : null,
                "Phê duyệt yêu cầu #" + req.getId() + " (" + type.getLabel() + ") cho gói " + plan.getName());
        notifyRequester(req, plan, true);
    }

    private void applyData(SubscriptionPlan plan, PlanData d) {
        plan.setName(d.name().trim());
        plan.setDescription(d.description());
        plan.setPrice(d.price());
        plan.setDurationDays(d.durationDays());
        if (d.featured() != null) plan.setFeatured(d.featured());
        if (d.sortOrder() != null) plan.setSortOrder(d.sortOrder());
    }

    private void notifyRequester(PlanChangeRequest req, SubscriptionPlan plan, boolean approved) {
        Long userId = req.getRequestedBy();
        if (userId == null) return;
        String title = approved ? "Yêu cầu đã được phê duyệt" : "Yêu cầu bị từ chối";
        String message = req.getRequestType().getLabel() + " – gói " + plan.getName()
                + (approved ? " đã được Admin phê duyệt." : ". Lý do: " + req.getRejectionReason());
        String type = approved ? "PLAN_REQUEST_APPROVED" : "PLAN_REQUEST_REJECTED";
        notifier.afterCommit(() -> notifier.notifyUser(userId, type, title, message, "/operation/subscriptions/requests"));
    }

    /** Người gửi hủy yêu cầu đang chờ duyệt. */
    @Transactional
    public void cancel(Long requestId, BillingActor actor) {
        PlanChangeRequest req = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu."));
        if (req.getStatus() != PlanRequestStatus.PENDING) throw new IllegalStateException("Chỉ hủy được yêu cầu đang chờ duyệt.");
        if (actor.id() == null || !actor.id().equals(req.getRequestedBy())) {
            throw new IllegalStateException("Chỉ người gửi mới được hủy yêu cầu này.");
        }
        req.setStatus(PlanRequestStatus.CANCELLED);
        req.setReviewedAt(PaymentDateTimeUtil.now());
        SubscriptionPlan plan = req.getPlan();
        if (req.getRequestType() == PlanRequestType.CREATE_PLAN && plan.getStatus() == SubscriptionPlanStatus.PENDING_APPROVAL) {
            plan.setStatus(SubscriptionPlanStatus.DRAFT);
        }
        if (req.getApprovalRefId() != null) {
            approvalService.getRequestById(req.getApprovalRefId())
                    .filter(r -> APPROVAL_TARGET.equals(r.getTargetType()) && requestId.equals(r.getTargetId()))
                    .ifPresent(r -> r.setStatus("CANCELLED"));
        }
        audit.log(actor, "CANCEL_PLAN_REQUEST", "SUBSCRIPTION_PLAN", plan.getId(), null, null,
                "Hủy yêu cầu #" + req.getId() + " (" + req.getRequestType().getLabel() + ")");
    }

    /** Hộp duyệt chung nằm trong bộ nhớ: sau khi khởi động lại, đăng ký lại các yêu cầu còn PENDING. */
    @EventListener(ApplicationReadyEvent.class)
    public void registerPendingAfterStartup() {
        try {
            for (PlanChangeRequest req : requests.findByStatusWithPlan(PlanRequestStatus.PENDING)) {
                registerInInbox(req, req.getPlan());
                requests.save(req);
            }
        } catch (Exception e) {
            log.warn("Không đồng bộ được yêu cầu gói học vào hộp duyệt: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ đọc dữ liệu

    /** Danh sách yêu cầu (lọc trạng thái, người gửi), kèm bảng so sánh Trước / Sau. */
    @Transactional(readOnly = true)
    public Page<PlanChangeRequestDTO> search(PlanRequestStatus status, Long requestedBy, int page, int size) {
        return requests.search(status, requestedBy,
                        PageRequest.of(Math.max(page, 0), size, Sort.by(Sort.Direction.DESC, "requestedAt")))
                .map(this::toDTO);
    }

    /** Chi tiết một yêu cầu kèm bảng so sánh Trước / Sau. */
    @Transactional(readOnly = true)
    public PlanChangeRequestDTO getDetail(Long requestId) {
        return requests.findDetailById(requestId).map(this::toDTO)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu."));
    }

    /** Số yêu cầu đang chờ duyệt. */
    public long countPending() {
        return requests.countByStatus(PlanRequestStatus.PENDING);
    }

    private PlanChangeRequestDTO toDTO(PlanChangeRequest r) {
        PlanData before = read(r.getOldData());
        PlanData after = read(r.getNewData());
        Map<String, String> featureNames = features.findAll().stream()
                .collect(Collectors.toMap(SubscriptionFeature::getCode, SubscriptionFeature::getName, (a, b) -> a));
        List<PlanChangeRequestDTO.CompareRow> rows = new ArrayList<>();
        row(rows, "Tên gói", before.name(), after.name());
        row(rows, "Giá", PaymentAmountUtil.format(before.price()), PaymentAmountUtil.format(after.price()));
        row(rows, "Thời hạn", days(before.durationDays()), days(after.durationDays()));
        row(rows, "Trạng thái", statusLabel(before.status()), statusLabel(after.status()));
        row(rows, "Gói nổi bật", yesNo(before.featured()), yesNo(after.featured()));
        row(rows, "Mô tả", nz(before.description()), nz(after.description()));
        row(rows, "Quyền lợi", names(before.featureCodes(), featureNames), names(after.featureCodes(), featureNames));
        SubscriptionPlan p = r.getPlan();
        return new PlanChangeRequestDTO(r.getId(), p.getId(), p.getCode(), p.getName(),
                r.getRequestType().name(), r.getRequestType().getLabel(), r.getStatus().name(), r.getStatus().getLabel(),
                r.getReason(), r.getRequestedBy(), r.getRequestedByName(), r.getReviewedByName(), r.getRejectionReason(),
                r.getRequestedAt(), r.getReviewedAt(), rows);
    }

    private String summarize(PlanChangeRequest r) {
        PlanData before = read(r.getOldData());
        PlanData after = read(r.getNewData());
        List<String> parts = new ArrayList<>();
        if (!Objects.equals(before.price(), after.price())) {
            parts.add("Giá: " + PaymentAmountUtil.format(before.price()) + " → " + PaymentAmountUtil.format(after.price()) + ".");
        }
        if (!Objects.equals(before.durationDays(), after.durationDays())) {
            parts.add("Thời hạn: " + days(before.durationDays()) + " → " + days(after.durationDays()) + ".");
        }
        if (!Objects.equals(before.status(), after.status())) {
            parts.add("Trạng thái: " + statusLabel(before.status()) + " → " + statusLabel(after.status()) + ".");
        }
        return parts.isEmpty() ? r.getRequestType().getLabel() + "." : String.join(" ", parts);
    }

    private static void row(List<PlanChangeRequestDTO.CompareRow> rows, String label, String before, String after) {
        rows.add(new PlanChangeRequestDTO.CompareRow(label, before, after, !Objects.equals(before, after)));
    }

    private static String days(Integer d) {
        return d == null ? "Không giới hạn" : d + " ngày";
    }

    private static String yesNo(Boolean b) {
        return Boolean.TRUE.equals(b) ? "Có" : "Không";
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static String statusLabel(String status) {
        try {
            return SubscriptionPlanStatus.valueOf(status).getLabel();
        } catch (Exception e) {
            return nz(status);
        }
    }

    private static String names(List<String> codes, Map<String, String> featureNames) {
        if (codes == null || codes.isEmpty()) return "—";
        return codes.stream().sorted().map(c -> featureNames.getOrDefault(c, c)).collect(Collectors.joining("; "));
    }

    String write(PlanData data) {
        try {
            return json.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không lưu được dữ liệu yêu cầu.", e);
        }
    }

    PlanData read(String value) {
        try {
            return value == null ? new PlanData(null, null, null, null, null, null, null, List.of())
                    : json.readValue(value, PlanData.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Dữ liệu yêu cầu bị hỏng.", e);
        }
    }
}
