package com.hottopik.service.subscription;

import com.hottopik.dto.subscription.*;
import com.hottopik.entity.subscription.*;
import com.hottopik.repository.subscription.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gói học: đọc cho học viên (chỉ gói ACTIVE), quản lý bản nháp cho Operation Manager và tạo yêu cầu phê duyệt
 * cho mọi thay đổi trên gói đã được duyệt. Không cập nhật trực tiếp giá chính thức.
 */
@Service
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository plans;
    private final SubscriptionPlanVersionRepository versions;
    private final SubscriptionPlanFeatureRepository planFeatures;
    private final SubscriptionFeatureRepository features;
    private final PlanChangeRequestRepository requests;
    private final PlanPriceHistoryRepository priceHistory;
    private final PlanApprovalService approval;
    private final BillingAuditService audit;

    public SubscriptionPlanService(SubscriptionPlanRepository plans, SubscriptionPlanVersionRepository versions,
                                   SubscriptionPlanFeatureRepository planFeatures, SubscriptionFeatureRepository features,
                                   PlanChangeRequestRepository requests, PlanPriceHistoryRepository priceHistory,
                                   PlanApprovalService approval, BillingAuditService audit) {
        this.plans = plans;
        this.versions = versions;
        this.planFeatures = planFeatures;
        this.features = features;
        this.requests = requests;
        this.priceHistory = priceHistory;
        this.approval = approval;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ học viên

    @Transactional(readOnly = true)
    public List<SubscriptionPlanDTO> getActivePlans() {
        return toDTOs(plans.findByStatusOrderBySortOrderAscIdAsc(SubscriptionPlanStatus.ACTIVE));
    }

    /** Chi tiết gói cho học viên: chỉ gói đang bán. */
    @Transactional(readOnly = true)
    public SubscriptionPlanDTO getPlanDetail(Long id) {
        SubscriptionPlan plan = plans.findById(id)
                .filter(p -> p.getStatus() == SubscriptionPlanStatus.ACTIVE)
                .orElseThrow(() -> new NoSuchElementException("Gói học không tồn tại hoặc đã ngừng bán."));
        return toDTOs(List.of(plan)).get(0);
    }

    /** Quyền lợi đang bật, dùng cho bảng so sánh Free / Premium. */
    @Transactional(readOnly = true)
    public List<PlanFeatureDTO> getActiveFeatures() {
        return features.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(f -> new PlanFeatureDTO(f.getCode(), f.getName(), f.getDescription(), f.getTier().name(), null))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PlanFeatureDTO> getActivePremiumFeatures() {
        return getActiveFeatures().stream().filter(PlanFeatureDTO::isPremiumOnly).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ quản lý

    @Transactional(readOnly = true)
    public Page<SubscriptionPlanDTO> search(String keyword, SubscriptionPlanStatus status, int page, int size) {
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Page<SubscriptionPlan> result = plans.search(kw, status,
                PageRequest.of(Math.max(page, 0), size, Sort.by("sortOrder").ascending().and(Sort.by("id"))));
        List<SubscriptionPlanDTO> dtos = toDTOs(result.getContent());
        return new org.springframework.data.domain.PageImpl<>(dtos, result.getPageable(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Map<String, Long> summary() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("total", plans.count());
        m.put("active", plans.countByStatus(SubscriptionPlanStatus.ACTIVE));
        m.put("stopped", plans.countByStatus(SubscriptionPlanStatus.INACTIVE) + plans.countByStatus(SubscriptionPlanStatus.ARCHIVED));
        m.put("pending", requests.countByStatus(PlanRequestStatus.PENDING));
        return m;
    }

    @Transactional(readOnly = true)
    public SubscriptionPlanDTO getForManagement(Long id) {
        return toDTOs(List.of(find(id))).get(0);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanDTO> getAllForManagement() {
        return toDTOs(plans.findAllByOrderBySortOrderAscIdAsc());
    }

    @Transactional
    public SubscriptionPlan createDraft(CreatePlanRequest form, BillingActor actor) {
        String code = form.getCode().trim().toUpperCase(Locale.ROOT);
        if (plans.existsByCodeIgnoreCase(code)) throw new IllegalArgumentException("Mã gói \"" + code + "\" đã tồn tại.");
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode(code);
        plan.setTier(PlanTier.PREMIUM);
        plan.setName(form.getName().trim());
        plan.setDescription(blankToNull(form.getDescription()));
        plan.setPrice(form.getPrice());
        plan.setDurationDays(form.getDurationDays());
        plan.setFeatured(form.isFeatured());
        plan.setSortOrder(form.getSortOrder());
        plan.setStatus(SubscriptionPlanStatus.DRAFT);
        plan.setCreatedBy(actor.name());
        plan.setUpdatedBy(actor.name());
        plans.save(plan);
        audit.log(actor, "CREATE_SUBSCRIPTION_PLAN", "SUBSCRIPTION_PLAN", plan.getId(), null,
                plan.getPrice() + "đ/" + plan.getDurationDays() + " ngày", "Tạo bản nháp gói " + plan.getName() + " (" + code + ")");
        return plan;
    }

    /**
     * Gói DRAFT: lưu trực tiếp (chưa bán nên không cần duyệt).
     * Gói đã duyệt: tạo yêu cầu UPDATE_PRICE / UPDATE_DURATION / UPDATE_BENEFITS / UPDATE_PLAN tùy dữ liệu thay đổi.
     * @return true nếu đã tạo yêu cầu phê duyệt, false nếu lưu thẳng bản nháp.
     */
    @Transactional
    public boolean updatePlan(Long id, UpdatePlanRequest form, BillingActor actor) {
        SubscriptionPlan plan = find(id);
        if (plan.getStatus() == SubscriptionPlanStatus.DRAFT) {
            plan.setName(form.getName().trim());
            plan.setDescription(blankToNull(form.getDescription()));
            plan.setPrice(form.getPrice());
            plan.setDurationDays(form.getDurationDays());
            plan.setFeatured(form.isFeatured());
            plan.setSortOrder(form.getSortOrder());
            plan.setUpdatedBy(actor.name());
            audit.log(actor, "UPDATE_SUBSCRIPTION_PLAN", "SUBSCRIPTION_PLAN", plan.getId(), null, null,
                    "Cập nhật bản nháp gói " + plan.getName());
            return false;
        }
        if (plan.getStatus() == SubscriptionPlanStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Gói đang chờ phê duyệt lần đầu, chưa thể chỉnh sửa.");
        }
        List<String> currentCodes = approval.currentFeatureCodes(plan);
        List<String> newCodes = mergeFeatureCodes(form.getFeatureCodes());
        boolean nameChanged = !Objects.equals(plan.getName(), form.getName().trim())
                || !Objects.equals(nz(plan.getDescription()), nz(form.getDescription()))
                || plan.isFeatured() != form.isFeatured() || plan.getSortOrder() != form.getSortOrder();
        boolean priceChanged = !Objects.equals(plan.getPrice(), form.getPrice());
        boolean durationChanged = !Objects.equals(plan.getDurationDays(), form.getDurationDays());
        boolean benefitsChanged = !new HashSet<>(currentCodes).equals(new HashSet<>(newCodes));
        int changes = (nameChanged ? 1 : 0) + (priceChanged ? 1 : 0) + (durationChanged ? 1 : 0) + (benefitsChanged ? 1 : 0);
        if (changes == 0) throw new IllegalArgumentException("Không có thay đổi nào so với dữ liệu hiện tại.");
        PlanRequestType type = changes > 1 || nameChanged ? PlanRequestType.UPDATE_PLAN
                : priceChanged ? PlanRequestType.UPDATE_PRICE
                : durationChanged ? PlanRequestType.UPDATE_DURATION
                : PlanRequestType.UPDATE_BENEFITS;
        PlanData newData = new PlanData(form.getName().trim(), blankToNull(form.getDescription()), form.getPrice(),
                form.getDurationDays(), form.isFeatured(), form.getSortOrder(), plan.getStatus().name(), newCodes);
        approval.submit(plan.getId(), type, newData, form.getReason(), actor);
        return true;
    }

    @Transactional
    public void submitDraft(Long id, String reason, BillingActor actor) {
        SubscriptionPlan plan = find(id);
        PlanData data = PlanData.of(plan, approval.defaultFeatureCodes(plan.getTier()))
                .withStatus(SubscriptionPlanStatus.ACTIVE.name());
        approval.submit(id, PlanRequestType.CREATE_PLAN, data, reason, actor);
    }

    @Transactional
    public void requestPriceChange(Long id, ChangePriceRequest form, BillingActor actor) {
        SubscriptionPlan plan = find(id);
        if (Objects.equals(plan.getPrice(), form.getNewPrice())) {
            throw new IllegalArgumentException("Giá mới không được bằng giá hiện tại.");
        }
        PlanData data = PlanData.of(plan, approval.currentFeatureCodes(plan)).withPrice(form.getNewPrice());
        approval.submit(id, PlanRequestType.UPDATE_PRICE, data, form.getReason(), actor);
    }

    @Transactional
    public void requestStatusChange(Long id, PlanRequestType type, String reason, BillingActor actor) {
        SubscriptionPlanStatus target = switch (type) {
            case ACTIVATE_PLAN -> SubscriptionPlanStatus.ACTIVE;
            case DEACTIVATE_PLAN -> SubscriptionPlanStatus.INACTIVE;
            case ARCHIVE_PLAN -> SubscriptionPlanStatus.ARCHIVED;
            default -> throw new IllegalArgumentException("Loại yêu cầu không hợp lệ.");
        };
        SubscriptionPlan plan = find(id);
        PlanData data = PlanData.of(plan, approval.currentFeatureCodes(plan)).withStatus(target.name());
        approval.submit(id, type, data, reason, actor);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanVersion> getVersions(Long planId) {
        return versions.findByPlan_IdOrderByVersionNumberDesc(planId);
    }

    @Transactional(readOnly = true)
    public Page<PlanPriceHistory> getPriceHistory(Long planId, int page, int size) {
        return priceHistory.search(planId, PageRequest.of(Math.max(page, 0), size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    // ------------------------------------------------------------------ helpers

    private SubscriptionPlan find(Long id) {
        return plans.findById(id).orElseThrow(() -> new NoSuchElementException("Không tìm thấy gói học."));
    }

    /** Quyền lợi FREE luôn có trong gói Premium; form chỉ cho chọn các quyền lợi PREMIUM đang bật. */
    private List<String> mergeFeatureCodes(List<String> selectedPremium) {
        Set<String> premiumActive = features.findByActiveTrueAndTierOrderBySortOrderAscIdAsc(PlanTier.PREMIUM).stream()
                .map(SubscriptionFeature::getCode).collect(Collectors.toSet());
        List<String> result = new ArrayList<>(features.findByActiveTrueAndTierOrderBySortOrderAscIdAsc(PlanTier.FREE).stream()
                .map(SubscriptionFeature::getCode).toList());
        if (selectedPremium != null) {
            selectedPremium.stream().filter(premiumActive::contains).distinct().forEach(result::add);
        }
        return result;
    }

    private List<SubscriptionPlanDTO> toDTOs(List<SubscriptionPlan> list) {
        if (list.isEmpty()) return List.of();
        List<Long> ids = list.stream().map(SubscriptionPlan::getId).toList();
        Map<Long, SubscriptionPlanVersion> currentByPlan = versions.findCurrentByPlanIds(ids).stream()
                .collect(Collectors.toMap(v -> v.getPlan().getId(), Function.identity(), (a, b) -> a));
        Map<Long, List<PlanFeatureDTO>> featuresByVersion = new HashMap<>();
        if (!currentByPlan.isEmpty()) {
            for (SubscriptionPlanFeature pf : planFeatures.findActiveByVersionIds(
                    currentByPlan.values().stream().map(SubscriptionPlanVersion::getId).toList())) {
                featuresByVersion.computeIfAbsent(pf.getPlanVersion().getId(), k -> new ArrayList<>())
                        .add(toFeature(pf.getFeature(), pf.getLimitValue()));
            }
        }
        List<SubscriptionFeature> activeFeatures = features.findByActiveTrueOrderBySortOrderAscIdAsc();
        Map<Long, PlanChangeRequest> pending = requests.findPendingByPlanIds(ids).stream()
                .collect(Collectors.toMap(r -> r.getPlan().getId(), Function.identity(), (a, b) -> a));
        List<SubscriptionPlanDTO> out = new ArrayList<>();
        for (SubscriptionPlan p : list) {
            SubscriptionPlanVersion v = currentByPlan.get(p.getId());
            List<PlanFeatureDTO> fs = v != null ? featuresByVersion.getOrDefault(v.getId(), List.of())
                    : activeFeatures.stream().filter(f -> f.getTier() == PlanTier.FREE || p.isPremium())
                    .map(f -> toFeature(f, null)).toList();
            PlanChangeRequest req = pending.get(p.getId());
            out.add(new SubscriptionPlanDTO(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getTier().name(),
                    p.getPrice(), p.getDurationDays(), p.getStatus().name(), p.getStatus().getLabel(), p.isFeatured(),
                    p.getSortOrder(), p.getCurrentVersionNo(), p.getVersion(), p.getUpdatedAt(), p.getUpdatedBy(), fs,
                    req == null ? null : req.getRequestType().name(), req == null ? null : req.getRequestType().getLabel()));
        }
        return out;
    }

    private static PlanFeatureDTO toFeature(SubscriptionFeature f, Integer limit) {
        return new PlanFeatureDTO(f.getCode(), f.getName(), f.getDescription(), f.getTier().name(), limit);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
