package com.hottopik.repository;

import com.hottopik.entity.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    List<UserNotification> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<UserNotification> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndReadFalse(Long userId);
}
