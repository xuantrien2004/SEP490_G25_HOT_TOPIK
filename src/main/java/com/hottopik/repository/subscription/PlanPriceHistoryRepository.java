package com.hottopik.repository.subscription;

import com.hottopik.entity.subscription.PlanPriceHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Chỉ đọc và thêm mới; không cung cấp chức năng sửa/xóa lịch sử giá trên giao diện. */
public interface PlanPriceHistoryRepository extends JpaRepository<PlanPriceHistory, Long> {

    @Query(value = "SELECT h FROM PlanPriceHistory h WHERE (:planId IS NULL OR h.plan.id = :planId)",
            countQuery = "SELECT COUNT(h) FROM PlanPriceHistory h WHERE (:planId IS NULL OR h.plan.id = :planId)")
    Page<PlanPriceHistory> search(@Param("planId") Long planId, Pageable pageable);
}
