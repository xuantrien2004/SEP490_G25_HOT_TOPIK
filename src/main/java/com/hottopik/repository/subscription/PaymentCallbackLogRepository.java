package com.hottopik.repository.subscription;

import com.hottopik.entity.subscription.PaymentCallbackLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentCallbackLogRepository extends JpaRepository<PaymentCallbackLog, Long> {

    List<PaymentCallbackLog> findTop20ByOrderCodeOrderByReceivedAtDesc(String orderCode);
}
