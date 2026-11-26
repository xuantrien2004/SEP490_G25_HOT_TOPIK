package com.hottopik.repository.subscription;

import com.hottopik.entity.subscription.OrderStatus;
import com.hottopik.entity.subscription.SubscriptionOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionOrderRepository extends JpaRepository<SubscriptionOrder, Long> {

    @Query("SELECT o FROM SubscriptionOrder o JOIN FETCH o.plan WHERE o.orderCode = :orderCode")
    Optional<SubscriptionOrder> findByOrderCode(@Param("orderCode") String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM SubscriptionOrder o WHERE o.id = :id")
    Optional<SubscriptionOrder> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "SELECT o FROM SubscriptionOrder o WHERE o.userId = :userId AND (:status IS NULL OR o.status = :status)",
            countQuery = "SELECT COUNT(o) FROM SubscriptionOrder o WHERE o.userId = :userId AND (:status IS NULL OR o.status = :status)")
    Page<SubscriptionOrder> findByUser(@Param("userId") Long userId, @Param("status") OrderStatus status, Pageable pageable);

    /** Đơn đang chờ thanh toán còn hiệu lực của cùng học viên + gói (tái sử dụng khi bấm thanh toán nhiều lần). */
    Optional<SubscriptionOrder> findFirstByUserIdAndPlan_IdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(
            Long userId, Long planId, OrderStatus status, LocalDateTime now);

    List<SubscriptionOrder> findByUserIdAndPlan_IdAndStatus(Long userId, Long planId, OrderStatus status);

    List<SubscriptionOrder> findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(OrderStatus status, LocalDateTime now);

    long countByStatus(OrderStatus status);
}
