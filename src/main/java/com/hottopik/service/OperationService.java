package com.hottopik.service;

import com.hottopik.dto.*;
import com.hottopik.entity.Review;
import com.hottopik.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class OperationService {

    private final Map<Long, OrderDTO> orders = new ConcurrentHashMap<>();
    private final Map<Long, PackagePriceDTO> packagePrices = new ConcurrentHashMap<>();
    private final Map<Long, AnnouncementDTO> announcements = new ConcurrentHashMap<>();
    private final List<VipCompensationRecordDTO> compensationHistory = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong annIdGen = new AtomicLong(10);
    private final AtomicLong compIdGen = new AtomicLong(10);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired(required = false)
    private ApprovalService approvalService;

    @Autowired
    private UserService userService;

    @Autowired
    private NotificationService notificationService;

    @Autowired(required = false)
    private ReviewRepository reviewRepository;

    @PostConstruct
    public void init() {
        // Seed Orders matching SEP490.sql
        orders.put(1L, new OrderDTO(1L, "HT-10088", "Nguyễn Hoàng Nam", "viplearner@hottopik.com", "VIP Trọn đời (Lifetime)", 499000.0, "VNPAY", "PAID", "2026-10-06 10:15"));
        orders.put(2L, new OrderDTO(2L, "HT-10089", "Phạm Minh Anh", "learner@hottopik.com", "VIP 3 Tháng", 299000.0, "MOMO", "PAID", "2026-10-06 16:40"));
        orders.put(3L, new OrderDTO(3L, "HT-10090", "Lê Văn Cường", "cuong.le@gmail.com", "VIP 1 Tháng", 129000.0, "BANK_TRANSFER", "PENDING", "2026-10-07 09:30"));

        // Seed Package Prices
        packagePrices.put(1L, new PackagePriceDTO(1L, "PKG-1M", "Gói VIP 1 Tháng", 30, 129000.0, 129000.0, "ACTIVE", "Truy cập mọi tính năng luyện đề & AI 30 ngày"));
        packagePrices.put(2L, new PackagePriceDTO(2L, "PKG-3M", "Gói VIP 3 Tháng", 90, 279000.0, 279000.0, "ACTIVE", "Tiết kiệm 25%, tặng kèm lộ trình bứt tốc TOPIK"));
        packagePrices.put(3L, new PackagePriceDTO(3L, "PKG-LIFE", "Gói VIP Trọn đời", 3650, 499000.0, 499000.0, "ACTIVE", "Sở hữu vĩnh viễn trọn bộ tài nguyên học thi TOPIK"));

        // Seed Announcements
        announcements.put(1L, new AnnouncementDTO(1L, "Cập nhật kho từ vựng TOPIK 1-6 chuẩn mới", "Hệ thống vừa cập nhật 800+ từ vựng bám sát đề thi thật tháng 10/2026.", "ALL", "Trần Quốc Tuấn", "2026-10-06 09:00"));
        announcements.put(2L, new AnnouncementDTO(2L, "Ưu đãi gói VIP Mùa thi", "Giảm ngay 20% khi nâng cấp gói VIP 3 tháng cho tất cả học viên.", "ALL", "Trần Quốc Tuấn", "2026-10-07 08:30"));

        // Seed Compensation Records
        compensationHistory.add(new VipCompensationRecordDTO(1L, 9005L, "Phạm Minh Anh", "learner@hottopik.com", 7, 50, "Bồi thường gián đoạn kết nối máy chủ ngày 05/10", "Trần Quốc Tuấn", "Operation Manager", "2026-10-06 15:30"));
        compensationHistory.add(new VipCompensationRecordDTO(2L, 9006L, "Nguyễn Hoàng Nam", "viplearner@hottopik.com", 30, 100, "Tri ân học viên VIP tích cực đạt mốc 100 giờ học", "Vũ Minh Quân", "Admin", "2026-10-07 10:00"));
    }

    public List<OrderDTO> getOrders() {
        return new ArrayList<>(orders.values());
    }

    public Optional<OrderDTO> getOrderById(Long id) {
        return Optional.ofNullable(orders.get(id));
    }

    public List<PackagePriceDTO> getPackagePrices() {
        return new ArrayList<>(packagePrices.values());
    }

    public Optional<PackagePriceDTO> getPackageById(Long id) {
        return Optional.ofNullable(packagePrices.get(id));
    }

    public List<ReviewDTO> getReviews() {
        if (reviewRepository == null) return Collections.emptyList();
        return reviewRepository.findAll().stream().map(r -> {
            ReviewDTO dto = new ReviewDTO();
            dto.setId(r.getId());
            dto.setAuthorName(r.getUser() != null && r.getUser().getDisplayName() != null
                    ? r.getUser().getDisplayName() : "Học viên HOT TOPIK");
            dto.setRating(r.getRating() == null ? 5 : r.getRating().intValue());
            dto.setComment(r.getContent());
            dto.setApproved("APPROVED".equalsIgnoreCase(r.getStatus()));
            dto.setCreatedAt(r.getCreatedAt() == null ? "" : r.getCreatedAt().format(FORMATTER));
            return dto;
        }).collect(Collectors.toList());
    }

    public List<AnnouncementDTO> getAnnouncements() {
        List<AnnouncementDTO> list = new ArrayList<>(announcements.values());
        list.sort(Comparator.comparing(AnnouncementDTO::getId).reversed());
        return list;
    }

    public List<VipCompensationRecordDTO> getCompensationHistory() {
        List<VipCompensationRecordDTO> list = new ArrayList<>(compensationHistory);
        list.sort(Comparator.comparing(VipCompensationRecordDTO::getId).reversed());
        return list;
    }

    /**
     * Operation Manager: Gửi đề nghị hoàn tiền lên Admin để phê duyệt
     */
    public boolean requestRefundApproval(Long orderId, String requesterName, String reason) {
        OrderDTO order = orders.get(orderId);
        if (order == null) return false;

        order.setStatus("REFUND_REQUESTED");
        order.setRefundReason(reason != null && !reason.trim().isEmpty() ? reason.trim() : "Operation Manager yêu cầu hoàn tiền");

        if (approvalService != null) {
            approvalService.submitTargetedRequest(
                    requesterName != null ? requesterName : "Operation Manager",
                    "Operation Manager",
                    "REFUND_ORDER",
                    "Đề nghị hoàn tiền đơn hàng #" + order.getOrderCode(),
                    "Khách hàng: " + order.getCustomerName() + " (" + order.getCustomerEmail() + "). Gói: " + order.getPackageName() + " (" + (int)order.getAmount() + "đ). Lý do hoàn tiền: " + order.getRefundReason(),
                    "ORDER",
                    orderId,
                    order.getOrderCode()
            );
        }
        return true;
    }

    /**
     * Phê duyệt hoàn tiền trực tiếp (Admin duyệt hoặc khi kích hoạt trực tiếp)
     */
    public boolean directRefundOrder(Long orderId, String reason, String actorName) {
        OrderDTO order = orders.get(orderId);
        if (order != null) {
            order.setStatus("REFUNDED");
            order.setRefundReason(reason != null && !reason.trim().isEmpty() ? reason.trim() : "Đã hoàn tiền theo quyết định phê duyệt");
            order.setRefundedAt(LocalDateTime.now().format(FORMATTER));
            order.setRefundedBy(actorName != null ? actorName : "Hệ thống");

            // Tìm học viên theo email và điều chỉnh / thu hồi quyền VIP nếu phù hợp
            if (order.getCustomerEmail() != null) {
                userService.findByEmail(order.getCustomerEmail()).ifPresent(user -> {
                    userService.revokeOrDowngradeVip(user.getId(), "Hoàn tiền đơn #" + order.getOrderCode());
                    if (notificationService != null) {
                        notificationService.createPersonalNotification(
                                user.getId(), user.getEmail(),
                                "Xác nhận hoàn tiền đơn hàng #" + order.getOrderCode(),
                                "Đơn hàng " + order.getPackageName() + " (" + (int)order.getAmount() + "đ) đã được phê duyệt hoàn tiền. Quyền lợi VIP tương ứng đã được cập nhật.",
                                "VIP", "💳", "/goi-cua-toi"
                        );
                    }
                });
            }
            return true;
        }
        return false;
    }

    /**
     * Operation Manager: Gửi đề xuất điều chỉnh giá gói lên Admin
     */
    public boolean proposePackagePrice(Long packageId, double proposedPrice, String requesterName, String reason) {
        PackagePriceDTO pkg = packagePrices.get(packageId);
        if (pkg == null || proposedPrice <= 0) return false;

        pkg.setProposedPrice(proposedPrice);
        pkg.setProposalStatus("PROPOSAL_PENDING");

        if (approvalService != null) {
            approvalService.submitTargetedRequest(
                    requesterName != null ? requesterName : "Operation Manager",
                    "Operation Manager",
                    "CHANGE_PACKAGE_PRICE",
                    "Đề xuất điều chỉnh giá gói: " + pkg.getName(),
                    "Gói: " + pkg.getName() + " (" + pkg.getPackageCode() + "). Giá hiện tại: " + (int)pkg.getCurrentPrice() + "đ -> Đề xuất giá mới: " + (int)proposedPrice + "đ. Lý do: " + (reason != null ? reason : "Điều chỉnh chính sách kinh doanh"),
                    "PACKAGE",
                    packageId,
                    String.valueOf(proposedPrice)
            );
        }
        return true;
    }

    /**
     * Cập nhật trực tiếp giá gói học (Khi Admin phê duyệt hoặc Admin cập nhật)
     */
    public boolean updatePackagePrice(Long packageId, double newPrice, String actorName) {
        PackagePriceDTO pkg = packagePrices.get(packageId);
        if (pkg != null && newPrice > 0) {
            pkg.setCurrentPrice(newPrice);
            pkg.setProposedPrice(newPrice);
            pkg.setProposalStatus("ACTIVE");
            return true;
        }
        return false;
    }

    /**
     * Operation Manager: Gửi đề xuất cấp bù hoặc gia hạn quyền lợi VIP lên Admin phê duyệt
     */
    public boolean proposeVipCompensation(Long userId, String userName, String userEmail,
                                          int days, int bonusCoins, String packageName,
                                          String reason, String requesterName) {
        if (userId == null || days <= 0) return false;

        String rsn = (reason != null && !reason.trim().isEmpty()) ? reason.trim() : "Cấp bù quyền lợi học viên";
        String payload = days + ";" + bonusCoins + ";" + (packageName != null ? packageName : "VIP") + ";" + rsn;

        if (approvalService != null) {
            approvalService.submitTargetedRequest(
                    requesterName != null ? requesterName : "Operation Manager",
                    "Operation Manager",
                    "VIP_COMPENSATION",
                    "Đề xuất cấp bù " + days + " ngày VIP (" + bonusCoins + " Xu) cho " + userName,
                    "Học viên: " + userName + " (" + userEmail + " - ID #" + userId + "). Cấp bù " + days + " ngày VIP, tặng kèm " + bonusCoins + " Xu. Lý do: " + rsn,
                    "USER",
                    userId,
                    payload
            );
        }
        return true;
    }

    /**
     * Áp dụng cấp bù hoặc gia hạn quyền lợi VIP sau khi Admin phê duyệt
     */
    public VipCompensationRecordDTO applyApprovedVipCompensation(Long userId, String userName, String userEmail,
                                                                 int days, int bonusCoins, String packageName,
                                                                 String reason, String actorName, String actorRole) {
        // 1. Áp dụng quyền lợi cho học viên
        userService.applyVipCompensation(userId, days, bonusCoins, packageName, reason);

        // 2. Ghi nhận lịch sử cấp bù
        VipCompensationRecordDTO rec = addCompensationRecord(userId, userName, userEmail, days, bonusCoins, reason, actorName, actorRole);

        // 3. Gửi thông báo trực tiếp đến hộp thư cá nhân của học viên
        if (notificationService != null) {
            notificationService.createPersonalNotification(
                    userId, userEmail,
                    "🎁 Bạn được cấp bù " + days + " ngày VIP và tặng " + bonusCoins + " Xu!",
                    "HOT TOPIK đã kích hoạt gia hạn " + days + " ngày quyền lợi gói " + (packageName != null ? packageName : "VIP") +
                            (bonusCoins > 0 ? " cùng " + bonusCoins + " Xu thưởng." : ".") + " Lý do: " + reason,
                    "VIP", "🎁", "/goi-cua-toi"
            );
        }

        return rec;
    }

    /**
     * Ghi nhận lịch sử cấp bù / gia hạn quyền lợi VIP
     */
    public VipCompensationRecordDTO addCompensationRecord(Long userId, String userName, String userEmail,
                                                          int days, int coins, String reason,
                                                          String actorName, String actorRole) {
        long id = compIdGen.incrementAndGet();
        VipCompensationRecordDTO rec = new VipCompensationRecordDTO(
                id, userId, userName, userEmail, days, coins,
                reason != null && !reason.trim().isEmpty() ? reason.trim() : "Cấp bù quyền lợi VIP",
                actorName, actorRole, LocalDateTime.now().format(FORMATTER)
        );
        compensationHistory.add(0, rec);
        return rec;
    }

    public boolean toggleReviewApproval(Long reviewId) {
        if (reviewRepository == null) return false;
        Optional<Review> review = reviewRepository.findById(reviewId);
        if (review.isPresent()) {
            Review rev = review.get();
            rev.setStatus("APPROVED".equalsIgnoreCase(rev.getStatus()) ? "PENDING" : "APPROVED");
            reviewRepository.save(rev);
            return true;
        }
        return false;
    }

    /**
     * Operation Manager: Soạn và phát thông báo theo nhóm người nhận hoặc đích danh học viên
     */
    public AnnouncementDTO sendAnnouncement(String title, String content, String audience,
                                            Long targetUserId, String targetUserName, String authorName) {
        long id = annIdGen.incrementAndGet();
        AnnouncementDTO ann = new AnnouncementDTO(
                id, title, content, audience,
                targetUserId, targetUserName, authorName,
                LocalDateTime.now().format(FORMATTER)
        );
        announcements.put(id, ann);

        // Phát vào hệ thống thông báo người học
        if (notificationService != null) {
            if ("SPECIFIC".equalsIgnoreCase(audience) && targetUserId != null) {
                notificationService.createPersonalNotification(
                        targetUserId, null, title, content, "ANNOUNCEMENT", "📢", "/thong-bao"
                );
            } else {
                notificationService.createBroadcastNotification(
                        title, content, audience, "ANNOUNCEMENT", "📢", "/thong-bao",
                        LocalDateTime.now().format(FORMATTER)
                );
            }
        }

        return ann;
    }

    public AnnouncementDTO sendAnnouncement(String title, String content, String audience, String authorName) {
        return sendAnnouncement(title, content, audience, null, null, authorName);
    }
}
