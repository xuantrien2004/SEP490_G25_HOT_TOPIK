package com.hottopik.service.subscription;

import com.hottopik.entity.subscription.*;
import com.hottopik.repository.subscription.SubscriptionFeatureRepository;
import com.hottopik.repository.subscription.SubscriptionPlanRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * Dữ liệu ban đầu (chỉ thêm khi chưa có, không ghi đè dữ liệu do quản trị chỉnh sửa).
 *
 * Quyền lợi được đối chiếu với code hiện tại: chỉ bật những quyền lợi đã triển khai. Các quyền lợi Premium
 * trong yêu cầu nhưng chưa có trong hệ thống (đề Premium, AI hạn mức cao, giải thích nâng cao, thống kê chi tiết)
 * được seed ở trạng thái TẮT để không quảng cáo tính năng chưa có.
 *
 * Giá dưới đây là giá mẫu phục vụ demo/kiểm thử; sau khi seed, giá chỉ thay đổi qua luồng phê duyệt.
 */
@Slf4j
@Service
public class SubscriptionDataSeeder {

    private final SubscriptionPlanRepository plans;
    private final SubscriptionFeatureRepository features;
    private final PlanApprovalService approval;

    public SubscriptionDataSeeder(SubscriptionPlanRepository plans, SubscriptionFeatureRepository features,
                                  PlanApprovalService approval) {
        this.plans = plans;
        this.features = features;
        this.approval = approval;
    }

    /** Không bọc chung một transaction: lỗi ở đây chỉ được ghi log, không làm ứng dụng khởi động thất bại. */
    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void seed() {
        try {
            feature("VOCABULARY", "Học từ vựng TOPIK 1–6", "Học từ vựng theo chủ đề, nghe phát âm và ôn tập.", PlanTier.FREE, true, 10);
            feature("GRAMMAR", "Học ngữ pháp TOPIK 1–6", "Học mẫu ngữ pháp kèm ví dụ, lưu ngữ pháp vào sổ tay.", PlanTier.FREE, true, 20);
            feature("HANJA", "Học từ Hán Hàn", "Học gốc Hán và từ vựng Hán Hàn liên quan.", PlanTier.FREE, true, 30);
            feature("PRACTICE", "Luyện tập theo dạng câu hỏi", "Luyện các dạng câu hỏi Nghe – Đọc – Viết.", PlanTier.FREE, true, 40);
            feature("PLACEMENT_TEST", "Bài kiểm tra đầu vào", "Xác định trình độ hiện tại.", PlanTier.FREE, true, 50);
            feature("ROADMAP", "Lộ trình học tập cá nhân", "Lộ trình theo mục tiêu và thời gian học mỗi ngày.", PlanTier.FREE, true, 60);
            feature("MOCK_EXAM", "Làm đề thi thử", "Làm đề thi thử TOPIK và xem kết quả.", PlanTier.FREE, true, 70);
            feature("TEACHER_WRITING_REVIEW", "Gửi bài Viết cho Giáo viên chấm",
                    "Gửi bài Viết (câu 51–54) cho Giáo viên chấm và nhận nhận xét.", PlanTier.PREMIUM, true, 100);
            // Chưa triển khai trong hệ thống hiện tại -> tắt, không hiển thị.
            feature("PREMIUM_EXAMS", "Kho đề thi Premium", "Chưa triển khai.", PlanTier.PREMIUM, false, 110);
            feature("AI_QUOTA_PLUS", "Hạn mức AI cao hơn", "Chưa triển khai (hệ thống chưa có chức năng AI cho học viên).", PlanTier.PREMIUM, false, 120);
            feature("ADVANCED_EXPLANATIONS", "Giải thích đáp án nâng cao", "Chưa triển khai.", PlanTier.PREMIUM, false, 130);
            feature("ADVANCED_STATS", "Thống kê học tập chi tiết", "Chưa triển khai.", PlanTier.PREMIUM, false, 140);

            plan("FREE", "Free", "Học miễn phí các chức năng cơ bản của HOT TOPIK.", PlanTier.FREE, 0L, null, false, 0);
            plan("PREMIUM_1M", "Premium 1 tháng", "Phù hợp ôn cấp tốc trước kỳ thi.", PlanTier.PREMIUM, 99_000L, 30, false, 10);
            plan("PREMIUM_3M", "Premium 3 tháng", "Lựa chọn phổ biến cho một kỳ ôn thi.", PlanTier.PREMIUM, 249_000L, 90, true, 20);
            plan("PREMIUM_6M", "Premium 6 tháng", "Ôn luyện dài hạn, tiết kiệm hơn.", PlanTier.PREMIUM, 449_000L, 180, false, 30);
            plan("PREMIUM_12M", "Premium 12 tháng", "Đồng hành cả năm, chi phí mỗi tháng thấp nhất.", PlanTier.PREMIUM, 799_000L, 365, false, 40);
        } catch (Exception e) {
            log.warn("Không seed được dữ liệu gói học: {}", e.getMessage());
        }
    }

    private void feature(String code, String name, String description, PlanTier tier, boolean active, int sort) {
        if (features.findByCode(code).isPresent()) return;
        SubscriptionFeature f = new SubscriptionFeature();
        f.setCode(code);
        f.setName(name);
        f.setDescription(description);
        f.setTier(tier);
        f.setActive(active);
        f.setSortOrder(sort);
        features.save(f);
    }

    private void plan(String code, String name, String description, PlanTier tier, long price, Integer days,
                      boolean featured, int sort) {
        if (plans.findByCode(code).isPresent()) return;
        SubscriptionPlan p = new SubscriptionPlan();
        p.setCode(code);
        p.setName(name);
        p.setDescription(description);
        p.setTier(tier);
        p.setPrice(price);
        p.setDurationDays(days);
        p.setFeatured(featured);
        p.setSortOrder(sort);
        p.setStatus(SubscriptionPlanStatus.ACTIVE);
        p.setCreatedBy("Hệ thống (seed)");
        p.setUpdatedBy("Hệ thống (seed)");
        plans.saveAndFlush(p);
        approval.createVersion(p, null, "Hệ thống (seed)");
        plans.save(p);
    }
}
