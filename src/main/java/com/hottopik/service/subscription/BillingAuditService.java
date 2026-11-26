package com.hottopik.service.subscription;

import com.hottopik.service.AdminSystemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Ghi Audit Log bằng AdminSystemService hiện có (tab Audit Log của Admin/Operation đọc từ đây).
 * Không ghi Hash Secret hay dữ liệu thanh toán nhạy cảm.
 */
@Slf4j
@Service
public class BillingAuditService {

    private final AdminSystemService adminSystemService;

    public BillingAuditService(AdminSystemService adminSystemService) {
        this.adminSystemService = adminSystemService;
    }

    public void log(BillingActor actor, String action, String targetType, Object targetId,
                    String oldValue, String newValue, String description) {
        StringBuilder details = new StringBuilder();
        if (description != null) details.append(description);
        details.append(" [actorId=").append(actor.id() == null ? "-" : actor.id())
                .append(", target=").append(targetType).append('#').append(targetId == null ? "-" : targetId);
        if (oldValue != null) details.append(", old=").append(oldValue);
        if (newValue != null) details.append(", new=").append(newValue);
        details.append(']');
        try {
            adminSystemService.logAction(actor.name(), actor.role(), action, targetType, details.toString(), "server");
        } catch (Exception e) {
            // Audit log không được làm hỏng nghiệp vụ chính.
            log.warn("Không ghi được audit log {}: {}", action, e.getMessage());
        }
    }
}
