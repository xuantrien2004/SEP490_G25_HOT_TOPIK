package com.hottopik.repository.subscription;

import com.hottopik.entity.subscription.PlanTier;
import com.hottopik.entity.subscription.SubscriptionFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionFeatureRepository extends JpaRepository<SubscriptionFeature, Long> {

    Optional<SubscriptionFeature> findByCode(String code);

    List<SubscriptionFeature> findByActiveTrueOrderBySortOrderAscIdAsc();

    List<SubscriptionFeature> findByActiveTrueAndTierOrderBySortOrderAscIdAsc(PlanTier tier);

    List<SubscriptionFeature> findByCodeIn(Collection<String> codes);
}
