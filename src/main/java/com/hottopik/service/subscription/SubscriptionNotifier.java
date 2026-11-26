package com.hottopik.service.subscription;

import com.hottopik.entity.UserNotification;
import com.hottopik.repository.UserNotificationRepository;
import com.hottopik.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Gửi thông báo in-app bằng bảng user_notifications sẵn có (chuông thông báo của học viên).
 * Thông báo được gửi SAU KHI transaction nghiệp vụ commit và trong transaction riêng,
 * nên lỗi gửi thông báo không làm rollback giao dịch thanh toán đã xác nhận.
 */
@Slf4j
@Service
public class SubscriptionNotifier {

    private final UserNotificationRepository notifications;
    private final UserRepository userRepository;

    public SubscriptionNotifier(UserNotificationRepository notifications, UserRepository userRepository) {
        this.notifications = notifications;
        this.userRepository = userRepository;
    }

    /** Chạy sau khi transaction hiện tại commit (hoặc chạy ngay nếu không có transaction). */
    public void afterCommit(Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("Gửi thông báo thất bại (không ảnh hưởng giao dịch): {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyUser(Long userId, String type, String title, String message, String link) {
        if (userId == null) return;
        UserNotification n = new UserNotification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(cut(title, 200));
        n.setMessage(cut(message, 1000));
        n.setLink(link);
        notifications.save(n);
    }

    /** Chống gửi trùng nhắc hạn: (user, type, link) đã có thì bỏ qua. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyOnce(Long userId, String type, String title, String message, String link) {
        if (userId == null || notifications.existsByUserIdAndTypeAndLink(userId, type, link)) return;
        notifyUser(userId, type, title, message, link);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyAdmins(String type, String title, String message, String link) {
        userRepository.findByRoleIn(List.of("ADMIN", "ROLE_ADMIN"))
                .forEach(admin -> notifyUser(admin.getId(), type, title, message, link));
    }

    private static String cut(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max));
    }
}
