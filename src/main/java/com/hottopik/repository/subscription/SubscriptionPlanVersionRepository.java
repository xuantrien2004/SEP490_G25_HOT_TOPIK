package com.hottopik.repository.subscription;

import com.hottopik.entity.subscription.SubscriptionPlanVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanVersionRepository extends JpaRepository<SubscriptionPlanVersion, Long> {

    /** Phiên bản hiện hành của nhiều gói trong một truy vấn. */
    @Query("SELECT v FROM SubscriptionPlanVersion v JOIN FETCH v.plan p WHERE p.id IN :planIds AND v.versionNumber = p.currentVersionNo")
    List<SubscriptionPlanVersion> findCurrentByPlanIds(@Param("planIds") Collection<Long> planIds);

    Optional<SubscriptionPlanVersion> findByPlan_IdAndVersionNumber(Long planId, int versionNumber);

    List<SubscriptionPlanVersion> findByPlan_IdOrderByVersionNumberDesc(Long planId);
}
