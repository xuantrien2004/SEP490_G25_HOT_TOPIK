package com.hottopik.repository.subscription;

import com.hottopik.entity.subscription.SubscriptionStatus;
import com.hottopik.entity.subscription.UserSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    /** Premium hợp lệ tại thời điểm now (không phụ thuộc Scheduler đã cập nhật EXPIRED hay chưa). */
    @Query("SELECT s FROM UserSubscription s WHERE s.userId = :userId AND s.status = com.hottopik.entity.subscription.SubscriptionStatus.ACTIVE "
            + "AND s.startsAt <= :now AND (s.endsAt IS NULL OR s.endsAt > :now) ORDER BY s.endsAt DESC")
    List<UserSubscription> findValid(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserSubscription s WHERE s.userId = :userId AND s.status = com.hottopik.entity.subscription.SubscriptionStatus.ACTIVE")
    List<UserSubscription> findActiveForUpdate(@Param("userId") Long userId);

    List<UserSubscription> findTop200ByStatusAndEndsAtLessThanEqual(SubscriptionStatus status, LocalDateTime now);

    @Query("SELECT s FROM UserSubscription s WHERE s.status = com.hottopik.entity.subscription.SubscriptionStatus.ACTIVE "
            + "AND s.endsAt > :now AND s.endsAt <= :until")
    List<UserSubscription> findEndingBetween(@Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    @Query(value = "SELECT s FROM UserSubscription s WHERE s.status = com.hottopik.entity.subscription.SubscriptionStatus.ACTIVE "
            + "AND s.startsAt <= :now AND (s.endsAt IS NULL OR s.endsAt > :now)",
            countQuery = "SELECT COUNT(s) FROM UserSubscription s WHERE s.status = com.hottopik.entity.subscription.SubscriptionStatus.ACTIVE "
            + "AND s.startsAt <= :now AND (s.endsAt IS NULL OR s.endsAt > :now)")
    Page<UserSubscription> findAllValid(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT s.userId) FROM UserSubscription s WHERE s.status = com.hottopik.entity.subscription.SubscriptionStatus.ACTIVE "
            + "AND s.startsAt <= :now AND (s.endsAt IS NULL OR s.endsAt > :now)")
    long countValidUsers(@Param("now") LocalDateTime now);
}
