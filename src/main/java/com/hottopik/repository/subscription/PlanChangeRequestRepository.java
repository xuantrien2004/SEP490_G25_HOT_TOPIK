package com.hottopik.repository.subscription;

import com.hottopik.entity.subscription.PlanChangeRequest;
import com.hottopik.entity.subscription.PlanRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlanChangeRequestRepository extends JpaRepository<PlanChangeRequest, Long> {

    boolean existsByPlan_IdAndStatus(Long planId, PlanRequestStatus status);

    long countByStatus(PlanRequestStatus status);

    @Query("SELECT r FROM PlanChangeRequest r JOIN FETCH r.plan WHERE r.status = :status ORDER BY r.requestedAt")
    List<PlanChangeRequest> findByStatusWithPlan(@Param("status") PlanRequestStatus status);

    @Query("SELECT r FROM PlanChangeRequest r JOIN FETCH r.plan WHERE r.id = :id")
    Optional<PlanChangeRequest> findDetailById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM PlanChangeRequest r WHERE r.id = :id")
    Optional<PlanChangeRequest> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "SELECT r FROM PlanChangeRequest r JOIN FETCH r.plan p WHERE (:status IS NULL OR r.status = :status) "
            + "AND (:requestedBy IS NULL OR r.requestedBy = :requestedBy)",
            countQuery = "SELECT COUNT(r) FROM PlanChangeRequest r WHERE (:status IS NULL OR r.status = :status) "
            + "AND (:requestedBy IS NULL OR r.requestedBy = :requestedBy)")
    Page<PlanChangeRequest> search(@Param("status") PlanRequestStatus status, @Param("requestedBy") Long requestedBy, Pageable pageable);

    @Query("SELECT r FROM PlanChangeRequest r WHERE r.plan.id IN :planIds AND r.status = com.hottopik.entity.subscription.PlanRequestStatus.PENDING")
    List<PlanChangeRequest> findPendingByPlanIds(@Param("planIds") Collection<Long> planIds);
}
