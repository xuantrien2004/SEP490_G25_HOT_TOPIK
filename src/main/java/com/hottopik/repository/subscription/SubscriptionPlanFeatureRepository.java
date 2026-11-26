package com.hottopik.repository.subscription;

import com.hottopik.entity.subscription.SubscriptionPlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SubscriptionPlanFeatureRepository extends JpaRepository<SubscriptionPlanFeature, Long> {

    /** Quyền lợi đang bật của nhiều phiên bản cùng lúc (tránh N+1 khi hiển thị danh sách gói). */
    @Query("SELECT pf FROM SubscriptionPlanFeature pf JOIN FETCH pf.feature f JOIN FETCH pf.planVersion v "
            + "WHERE v.id IN :versionIds AND f.active = true ORDER BY f.sortOrder, f.id")
    List<SubscriptionPlanFeature> findActiveByVersionIds(@Param("versionIds") Collection<Long> versionIds);
}
