package com.hottopik.repository.subscription;

import com.hottopik.entity.subscription.SubscriptionPlan;
import com.hottopik.entity.subscription.SubscriptionPlanStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    Optional<SubscriptionPlan> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<SubscriptionPlan> findByStatusOrderBySortOrderAscIdAsc(SubscriptionPlanStatus status);

    List<SubscriptionPlan> findAllByOrderBySortOrderAscIdAsc();

    long countByStatus(SubscriptionPlanStatus status);

    @Query("SELECT p FROM SubscriptionPlan p WHERE (:status IS NULL OR p.status = :status) AND "
            + "(:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<SubscriptionPlan> search(@Param("keyword") String keyword, @Param("status") SubscriptionPlanStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM SubscriptionPlan p WHERE p.id = :id")
    Optional<SubscriptionPlan> findByIdForUpdate(@Param("id") Long id);
}
