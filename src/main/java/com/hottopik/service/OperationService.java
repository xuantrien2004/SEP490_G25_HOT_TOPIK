package com.hottopik.service;

import com.hottopik.dto.*;
import com.hottopik.entity.Review;
import com.hottopik.entity.User;
import com.hottopik.repository.ReviewRepository;
import com.hottopik.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AtomicLong annIdGen = new AtomicLong(10);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserRepository userRepository; // Tiêm UserRepository để lấy dữ liệu User thật từ DB

    @PostConstruct
    public void init() {
        // Seed Orders giả lập cho phần quản lý đơn hàng
        orders.put(1L, new OrderDTO(1L, "HT-10088", "Nguyễn Hoàng Nam", "viplearner@hottopik.com", "VIP Trọn đời (Lifetime)", 499000.0, "VNPAY", "PAID", "2026-10-06 10:15"));
        orders.put(2L, new OrderDTO(2L, "HT-10089", "Phạm Minh Anh", "learner@hottopik.com", "VIP 3 Tháng", 299000.0, "MOMO", "REFUND_REQUESTED", "2026-10-06 16:40"));
        orders.put(3L, new OrderDTO(3L, "HT-10090", "Lê Văn Cường", "cuong.le@gmail.com", "VIP 1 Tháng", 129000.0, "BANK_TRANSFER", "PENDING", "2026-10-07 09:30"));

        // Seed Package Prices
        packagePrices.put(1L, new PackagePriceDTO(1L, "PKG-1M", "Gói VIP 1 Tháng", 30, 129000.0, 129000.0, "ACTIVE", "Truy cập mọi tính năng luyện đề & AI 30 ngày"));
        packagePrices.put(2L, new PackagePriceDTO(2L, "PKG-3M", "Gói VIP 3 Tháng", 90, 299000.0, 279000.0, "PROPOSED", "Tiết kiệm 25%, tặng kèm lộ trình bứt tốc TOPIK"));
        packagePrices.put(3L, new PackagePriceDTO(3L, "PKG-LIFE", "Gói VIP Trọn đời", 3650, 499000.0, 499000.0, "ACTIVE", "Sở hữu vĩnh viễn trọn bộ tài nguyên học thi TOPIK"));

        // Seed Announcements
        announcements.put(1L, new AnnouncementDTO(1L, "Cập nhật kho từ vựng TOPIK 1-6 chuẩn mới", "Hệ thống vừa cập nhật 800+ từ vựng bám sát đề thi thật tháng 10/2026.", "ALL", "Trần Quốc Tuấn", "2026-10-06 09:00"));
        announcements.put(2L, new AnnouncementDTO(2L, "Ưu đãi gói VIP Mùa thi", "Giảm ngay 20% khi nâng cấp gói VIP 3 tháng cho tất cả học viên.", "ALL", "Trần Quốc Tuấn", "2026-10-07 08:30"));
    }

    public List<OrderDTO> getOrders() {
        return new ArrayList<>(orders.values());
    }

    public List<PackagePriceDTO> getPackagePrices() {
        return new ArrayList<>(packagePrices.values());
    }

    // Lấy danh sách Review từ Database thật khớp với Entity Review & User mới
    @Transactional(readOnly = true)
    public List<ReviewDTO> getReviews() {
        List<Review> entityList = reviewRepository.findAll();
        List<ReviewDTO> dtos = new ArrayList<>();
        for (Review r : entityList) {
            ReviewDTO dto = new ReviewDTO();
            dto.setId(r.getId());

            // Lấy tên từ quan hệ User
            String author = (r.getUser() != null && r.getUser().getDisplayName() != null)
                    ? r.getUser().getDisplayName() : "Học viên HOT TOPIK";
            dto.setAuthorName(author);

            dto.setRating(r.getRating() != null ? r.getRating().intValue() : 5);
            dto.setComment(r.getContent());

            dto.setApproved("APPROVED".equalsIgnoreCase(r.getStatus()));
            dto.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().format(FORMATTER) : "");

            dtos.add(dto);
        }
        return dtos;
    }

    public List<AnnouncementDTO> getAnnouncements() {
        return new ArrayList<>(announcements.values());
    }

    public boolean requestRefundApproval(Long orderId, String requesterName, String reason) {
        OrderDTO order = orders.get(orderId);
        if (order != null) {
            order.setStatus("REFUND_REQUESTED");
            approvalService.submitRequest(
                    requesterName,
                    "Operation Manager",
                    "REFUND_ORDER",
                    "Phê duyệt hoàn tiền đơn hàng #" + order.getOrderCode() + " (" + (int)order.getAmount() + "đ)",
                    "Lý do hoàn tiền: " + (reason != null ? reason : "Học viên yêu cầu hoàn tiền hợp lệ.")
            );
            return true;
        }
        return false;
    }

    public boolean proposePackagePrice(Long packageId, double proposedPrice, String requesterName) {
        PackagePriceDTO pkg = packagePrices.get(packageId);
        if (pkg != null) {
            pkg.setProposedPrice(proposedPrice);
            pkg.setProposalStatus("PROPOSED");
            approvalService.submitRequest(
                    requesterName,
                    "Operation Manager",
                    "PRICE_UPDATE",
                    "Phê duyệt điều chỉnh giá " + pkg.getName(),
                    "Giá hiện tại: " + (int)pkg.getCurrentPrice() + "đ -> Đề xuất giá mới: " + (int)proposedPrice + "đ."
            );
            return true;
        }
        return false;
    }

    // Toggle trạng thái duyệt review trực tiếp trên Database (Chuyển đổi giữa PENDING và APPROVED)
    @Transactional
    public boolean toggleReviewApproval(Long reviewId) {
        Optional<Review> opt = reviewRepository.findById(reviewId);
        if (opt.isPresent()) {
            Review rev = opt.get();
            if ("APPROVED".equalsIgnoreCase(rev.getStatus())) {
                rev.setStatus("PENDING");
            } else {
                rev.setStatus("APPROVED");
            }
            reviewRepository.save(rev);
            return true;
        }
        return false;
    }

    public AnnouncementDTO sendAnnouncement(String title, String content, String audience, String authorName) {
        long id = annIdGen.incrementAndGet();
        AnnouncementDTO ann = new AnnouncementDTO(id, title, content, audience, authorName, LocalDateTime.now().format(FORMATTER));
        announcements.put(id, ann);
        return ann;
    }
}