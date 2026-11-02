package com.hottopik.service;

import com.hottopik.dto.NotificationDTO;
import com.hottopik.dto.UserDTO;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final Map<Long, NotificationDTO> notifications = new ConcurrentHashMap<>();
    // Lưu trạng thái đã đọc của từng user cho từng thông báo: Key = "userId_notifId"
    private final Set<String> readReceipts = Collections.synchronizedSet(new HashSet<>());
    private final AtomicLong idGen = new AtomicLong(100);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @PostConstruct
    public void init() {
        // Thông báo hệ thống ban đầu (ALL)
        createBroadcastNotification(
                "Cập nhật kho từ vựng TOPIK 1-6 chuẩn mới",
                "Hệ thống vừa cập nhật 800+ từ vựng bám sát đề thi thật tháng 10/2026. Hãy mở phần Luyện tập để thử sức ngay!",
                "ALL", "ANNOUNCEMENT", "📢", "/luyen-tap", "2026-10-06 09:00"
        );

        // Thông báo ưu đãi VIP (ALL)
        createBroadcastNotification(
                "Ưu đãi gói VIP Mùa thi - Giảm ngay 20%",
                "Giảm ngay 20% khi nâng cấp gói VIP 3 tháng cho tất cả học viên trong tuần này.",
                "ALL", "PROMO", "🎁", "/vip", "2026-10-07 08:30"
        );

        // Thông báo đặc quyền VIP (VIP)
        createBroadcastNotification(
                "Đặc quyền VIP: Lộ trình cá nhân hóa cùng AI Bé Ssak",
                "Học viên VIP được mở khóa tính năng tự động tối ưu hóa lộ trình ôn thi theo điểm yếu từng kỹ năng.",
                "VIP", "VIP", "👑", "/lo-trinh", "2026-10-07 14:00"
        );

        // Thông báo học tập mẫu
        NotificationDTO n1 = new NotificationDTO(
                1L, null, null,
                "Nhắc học hôm nay · Duy trì chuỗi ngày liên tục",
                "Bạn còn 1 bài ôn Nghe trong Lộ trình hôm nay để hoàn thành mục tiêu 20 phút. Cùng cố lên nhé!",
                "STUDY", "⏰", "/hom-nay", false, "2026-10-08 10:00", "ALL"
        );
        notifications.put(1L, n1);

        NotificationDTO n2 = new NotificationDTO(
                2L, null, null,
                "AI đã chấm xong bài viết Câu 53 (+8 điểm)",
                "Bài viết biểu đồ số 4 đã được AI chấm phân tích chi tiết tiêu chí từ vựng và ngữ pháp. Bấm để xem nhận xét.",
                "WRITING", "✍️", "/bai-cham", false, "2026-10-08 14:15", "ALL"
        );
        notifications.put(2L, n2);

        NotificationDTO n3 = new NotificationDTO(
                3L, null, null,
                "Báo lỗi câu hỏi đã được xác nhận · +20 Xu",
                "Đội ngũ ban điều hành đã tiếp nhận và chỉnh sửa giải thích câu hỏi bạn đóng góp. Đã cộng 20 Xu vào tài khoản.",
                "SYSTEM", "🪙", "/xu", true, "2026-10-05 18:30", "ALL"
        );
        notifications.put(3L, n3);
    }

    public List<NotificationDTO> getNotificationsForUser(UserDTO user) {
        if (user == null) {
            return Collections.emptyList();
        }

        List<NotificationDTO> list = new ArrayList<>();
        Long uid = user.getId();
        boolean isVip = user.isVip();
        String track = user.getTargetLevel() != null ? user.getTargetLevel().toUpperCase() : "";

        for (NotificationDTO n : notifications.values()) {
            boolean match = false;

            // Nếu thông báo gửi đích danh cho user
            if (n.getUserId() != null && n.getUserId().equals(uid)) {
                match = true;
            } else if (n.getUserId() == null) {
                // Broadcast thông báo
                String aud = n.getTargetAudience() != null ? n.getTargetAudience().toUpperCase() : "ALL";
                if ("ALL".equals(aud)) {
                    match = true;
                } else if ("VIP".equals(aud) || "VIP_ONLY".equals(aud)) {
                    match = isVip;
                } else if ("FREE".equals(aud) || "FREE_ONLY".equals(aud)) {
                    match = !isVip;
                } else if ("TOPIK_I".equals(aud)) {
                    match = track.contains("1") || track.contains("2") || track.contains("I");
                } else if ("TOPIK_II".equals(aud)) {
                    match = track.contains("3") || track.contains("4") || track.contains("5") || track.contains("6") || track.contains("II");
                }
            }

            if (match) {
                // Clone DTO và áp dụng trạng thái đã đọc cá nhân của user
                NotificationDTO userNotif = cloneDto(n);
                String receiptKey = uid + "_" + n.getId();
                if (readReceipts.contains(receiptKey) || n.isRead()) {
                    userNotif.setRead(true);
                } else {
                    userNotif.setRead(false);
                }
                list.add(userNotif);
            }
        }

        list.sort(Comparator.comparing(NotificationDTO::getId).reversed());
        return list;
    }

    public int getUnreadCount(UserDTO user) {
        if (user == null) return 0;
        return (int) getNotificationsForUser(user).stream().filter(n -> !n.isRead()).count();
    }

    public boolean markAsRead(Long notifId, Long userId) {
        if (notifId == null || userId == null) return false;
        readReceipts.add(userId + "_" + notifId);
        return true;
    }

    public boolean markAllAsRead(Long userId, UserDTO user) {
        if (userId == null) return false;
        List<NotificationDTO> userNotifs = getNotificationsForUser(user);
        for (NotificationDTO n : userNotifs) {
            readReceipts.add(userId + "_" + n.getId());
        }
        return true;
    }

    public NotificationDTO createBroadcastNotification(String title, String content, String audience,
                                                       String category, String icon, String linkUrl, String sentAt) {
        long id = idGen.incrementAndGet();
        NotificationDTO dto = new NotificationDTO(
                id, null, null, title, content,
                category != null ? category : "ANNOUNCEMENT",
                icon != null ? icon : "📢",
                linkUrl != null ? linkUrl : "/thong-bao",
                false,
                sentAt != null ? sentAt : LocalDateTime.now().format(FORMATTER),
                audience != null ? audience : "ALL"
        );
        notifications.put(id, dto);
        return dto;
    }

    public NotificationDTO createPersonalNotification(Long userId, String userEmail, String title,
                                                      String content, String category, String icon, String linkUrl) {
        long id = idGen.incrementAndGet();
        NotificationDTO dto = new NotificationDTO(
                id, userId, userEmail, title, content,
                category != null ? category : "SYSTEM",
                icon != null ? icon : "🔔",
                linkUrl != null ? linkUrl : "/thong-bao",
                false,
                LocalDateTime.now().format(FORMATTER),
                "SPECIFIC"
        );
        notifications.put(id, dto);
        return dto;
    }

    private NotificationDTO cloneDto(NotificationDTO src) {
        return new NotificationDTO(
                src.getId(), src.getUserId(), src.getUserEmail(),
                src.getTitle(), src.getContent(), src.getCategory(),
                src.getIcon(), src.getLinkUrl(), src.isRead(),
                src.getCreatedAt(), src.getTargetAudience()
        );
    }
}
