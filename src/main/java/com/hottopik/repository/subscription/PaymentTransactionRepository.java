package com.hottopik.repository.subscription;

import com.hottopik.entity.subscription.PaymentStatus;
import com.hottopik.entity.subscription.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    /** Khóa ghi giao dịch khi xử lý IPN / đối soát: hai callback đồng thời được xử lý tuần tự. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PaymentTransaction t WHERE t.paymentProvider = :provider "
            + "AND t.paymentEnvironment = :env AND t.providerTxnRef = :txnRef")
    Optional<PaymentTransaction> findByTxnRefForUpdate(@Param("provider") String provider, @Param("env") String env,
                                                       @Param("txnRef") String txnRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PaymentTransaction t WHERE t.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT t FROM PaymentTransaction t JOIN FETCH t.order o WHERE t.providerTxnRef = :txnRef")
    Optional<PaymentTransaction> findByProviderTxnRef(@Param("txnRef") String txnRef);

    @Query("SELECT t FROM PaymentTransaction t JOIN FETCH t.order o JOIN FETCH o.plan WHERE t.id = :id")
    Optional<PaymentTransaction> findDetailById(@Param("id") Long id);

    List<PaymentTransaction> findByOrder_IdOrderByCreatedAtDesc(Long orderId);

    List<PaymentTransaction> findByOrder_IdAndStatus(Long orderId, PaymentStatus status);

    long countByOrder_Id(Long orderId);

    long countByStatus(PaymentStatus status);

    /** Tìm kiếm giao dịch cho Operation/Admin. Từ khóa: mã đơn, mã VNPay, mã tham chiếu hoặc người mua (userIds). */
    @Query(value = "SELECT t FROM PaymentTransaction t JOIN FETCH t.order o WHERE "
            + "(:status IS NULL OR t.status = :status) AND (:planId IS NULL OR o.plan.id = :planId) "
            + "AND (:fromTime IS NULL OR t.createdAt >= :fromTime) AND (:toTime IS NULL OR t.createdAt < :toTime) "
            + "AND (:keyword IS NULL OR o.orderCode LIKE CONCAT('%', :keyword, '%') "
            + "OR t.providerTransactionNo LIKE CONCAT('%', :keyword, '%') OR t.providerTxnRef LIKE CONCAT('%', :keyword, '%') "
            + "OR o.userId IN :userIds)",
            countQuery = "SELECT COUNT(t) FROM PaymentTransaction t JOIN t.order o WHERE "
            + "(:status IS NULL OR t.status = :status) AND (:planId IS NULL OR o.plan.id = :planId) "
            + "AND (:fromTime IS NULL OR t.createdAt >= :fromTime) AND (:toTime IS NULL OR t.createdAt < :toTime) "
            + "AND (:keyword IS NULL OR o.orderCode LIKE CONCAT('%', :keyword, '%') "
            + "OR t.providerTransactionNo LIKE CONCAT('%', :keyword, '%') OR t.providerTxnRef LIKE CONCAT('%', :keyword, '%') "
            + "OR o.userId IN :userIds)")
    Page<PaymentTransaction> search(@Param("keyword") String keyword, @Param("userIds") Collection<Long> userIds,
                                    @Param("status") PaymentStatus status, @Param("planId") Long planId,
                                    @Param("fromTime") LocalDateTime from, @Param("toTime") LocalDateTime to, Pageable pageable);

    /** Giao dịch Sandbox thành công đã xác minh trong khoảng thời gian (báo cáo doanh thu mô phỏng). */
    @Query("SELECT t FROM PaymentTransaction t JOIN FETCH t.order o WHERE t.status = com.hottopik.entity.subscription.PaymentStatus.SUCCESS "
            + "AND t.paymentEnvironment = 'SANDBOX' AND t.paidAt >= :fromTime AND t.paidAt < :toTime ORDER BY t.paidAt")
    List<PaymentTransaction> findSuccessBetween(@Param("fromTime") LocalDateTime from, @Param("toTime") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(t.paidAmount), 0) FROM PaymentTransaction t WHERE t.status = com.hottopik.entity.subscription.PaymentStatus.SUCCESS "
            + "AND t.paymentEnvironment = 'SANDBOX' AND t.paidAt >= :fromTime AND t.paidAt < :toTime")
    Long sumSuccessAmount(@Param("fromTime") LocalDateTime from, @Param("toTime") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(t.paidAmount), 0) FROM PaymentTransaction t WHERE t.status = com.hottopik.entity.subscription.PaymentStatus.SUCCESS "
            + "AND t.paymentEnvironment = 'SANDBOX'")
    Long sumAllSuccessAmount();

    List<PaymentTransaction> findTop10ByStatusOrderByPaidAtDesc(PaymentStatus status);
}
