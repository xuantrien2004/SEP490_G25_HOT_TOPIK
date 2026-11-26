package com.hottopik.service.subscription;

import com.hottopik.entity.subscription.PaymentCallbackLog;
import com.hottopik.repository.subscription.PaymentCallbackLogRepository;
import com.hottopik.util.PaymentDateTimeUtil;
import com.hottopik.util.VNPaySignatureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeMap;

/** Ghi nhật ký callback trong transaction riêng để log vẫn còn khi xử lý chính bị rollback. */
@Slf4j
@Service
public class PaymentCallbackLogService {

    private final PaymentCallbackLogRepository repository;

    public PaymentCallbackLogService(PaymentCallbackLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long received(String type, Map<String, String> params) {
        try {
            PaymentCallbackLog entry = new PaymentCallbackLog();
            entry.setCallbackType(type);
            String txnRef = params.get("vnp_TxnRef");
            String txnNo = params.get("vnp_TransactionNo");
            entry.setOrderCode(cut(txnRef, 40));
            entry.setProviderTransactionNo(cut(txnNo, 40));
            entry.setEventKey(cut(txnRef + "|" + txnNo + "|" + params.get("vnp_ResponseCode") + "|" + params.get("vnp_TransactionStatus"), 120));
            entry.setPayloadHash(VNPaySignatureUtil.sha256(new TreeMap<>(params).toString()));
            entry.setProcessingStatus("RECEIVED");
            return repository.save(entry).getId();
        } catch (Exception e) {
            log.warn("Không ghi được callback log: {}", e.getMessage());
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long logId, String status, String error) {
        if (logId == null) return;
        try {
            repository.findById(logId).ifPresent(entry -> {
                entry.setProcessingStatus(status);
                entry.setErrorMessage(cut(error, 500));
                entry.setProcessedAt(PaymentDateTimeUtil.now());
                repository.save(entry);
            });
        } catch (Exception e) {
            log.warn("Không cập nhật được callback log: {}", e.getMessage());
        }
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
