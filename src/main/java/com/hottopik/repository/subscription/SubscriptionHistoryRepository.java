package com.hottopik.repository.subscription;

import com.hottopik.entity.subscription.SubscriptionAction;
import com.hottopik.entity.subscription.SubscriptionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory, Long> {

    boolean existsByOrderIdAndActionTypeIn(Long orderId, Collection<SubscriptionAction> actions);

    List<SubscriptionHistory> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
