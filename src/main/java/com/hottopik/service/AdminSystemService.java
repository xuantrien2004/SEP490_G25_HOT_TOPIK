package com.hottopik.service;

import com.hottopik.dto.AiBudgetDTO;
import com.hottopik.dto.AuditLogDTO;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class AdminSystemService {

    private final Map<Long, AiBudgetDTO> aiBudgets = new ConcurrentHashMap<>();
    private final List<AuditLogDTO> auditLogs = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean breakGlassMode = new AtomicBoolean(false);
    private final AtomicLong logIdGen = new AtomicLong(10);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Khởi tạo dữ liệu mẫu cho cấu hình ngân sách AI và nhật ký kiểm toán hệ thống.
     */
    @PostConstruct
    public void init() {
        // Seed AI Budgets matching SEP490.sql
        aiBudgets.put(1L, new AiBudgetDTO(1L, "OpenAI GPT-4o (Chấm bài viết)", 300.0, 186.5, 80, "NORMAL"));
        aiBudgets.put(2L, new AiBudgetDTO(2L, "Google Gemini 1.5 Pro (Sinh đề)", 150.0, 88.2, 80, "NORMAL"));
        aiBudgets.put(3L, new AiBudgetDTO(3L, "Azure Text-to-Speech (Audio)", 50.0, 37.4, 85, "WARNING"));

        // Seed Audit Logs matching SEP490.sql
        auditLogs.add(new AuditLogDTO(1L, "Vũ Minh Quân", "ROLE_ADMIN", "LOGIN", "AUTH", "Admin đăng nhập từ IP an toàn", "192.168.1.10", "2026-10-06 08:30:12"));
        auditLogs.add(new AuditLogDTO(2L, "Lê Thu Hà", "ROLE_CONTENT_MANAGER", "CREATE_VOCAB", "VOCABULARY", "Thêm 8 từ vựng mới vào TOPIK 1", "192.168.1.25", "2026-10-06 14:15:40"));
        auditLogs.add(new AuditLogDTO(3L, "Trần Quốc Tuấn", "ROLE_OPERATION_MANAGER", "APPROVE_ORDER", "ORDER", "Xác nhận thanh toán đơn #HT-10088", "192.168.1.30", "2026-10-06 16:45:00"));
        auditLogs.add(new AuditLogDTO(4L, "Vũ Minh Quân", "ROLE_ADMIN", "UPDATE_CONFIG", "AI_BUDGET", "Điều chỉnh ngưỡng cảnh báo GPT-4o lên 80%", "192.168.1.10", "2026-10-07 09:10:05"));
    }

    /**
     * Lấy danh sách cấu hình ngân sách các dịch vụ AI.
     */
    public List<AiBudgetDTO> getAiBudgets() {
        return new ArrayList<>(aiBudgets.values());
    }

    /**
     * Lấy toàn bộ nhật ký hoạt động hệ thống, sắp xếp mới nhất trước.
     */
    public List<AuditLogDTO> getAuditLogs() {
        List<AuditLogDTO> list = new ArrayList<>(auditLogs); // Sao chép danh sách log để tránh ConcurrentModification
        list.sort((a, b) -> Long.compare(b.getId(), a.getId())); // Sắp xếp ID giảm dần (mới nhất lên đầu)
        return list;
    }

    /**
     * Lấy nhật ký hoạt động lọc theo vai trò (ROLE_ADMIN xem toàn bộ).
     */
    public List<AuditLogDTO> getAuditLogsForRole(String role) {
        List<AuditLogDTO> list;
        if (role == null || role.equals("ROLE_ADMIN")) {
            list = new ArrayList<>(auditLogs); // Admin xem toàn bộ log
        } else {
            list = auditLogs.stream() // Lọc log theo actorRole tương ứng
                    .filter(l -> l.getActorRole() != null && l.getActorRole().equalsIgnoreCase(role))
                    .collect(Collectors.toList());
        }
        list.sort((a, b) -> Long.compare(b.getId(), a.getId())); // Sắp xếp mới nhất trước
        return list;
    }

    /**
     * Ghi nhận một bản ghi nhật ký thao tác mới vào đầu danh sách.
     */
    public void logAction(String actorName, String actorRole, String action, String targetEntity, String details, String ip) {
        long id = logIdGen.incrementAndGet(); // Sinh ID tự tăng thread-safe
        AuditLogDTO entry = new AuditLogDTO(id, actorName, actorRole, action, targetEntity, details, ip != null ? ip : "127.0.0.1", LocalDateTime.now().format(FORMATTER));
        auditLogs.add(0, entry); // Chèn lên đầu danh sách
    }

    /**
     * Cập nhật hạn mức chi tiêu hàng tháng (USD) và ngưỡng cảnh báo (%) cho một dịch vụ AI.
     */
    public boolean updateAiBudget(Long id, double limit, int threshold) {
        AiBudgetDTO budget = aiBudgets.get(id); // Tìm cấu hình ngân sách theo ID
        if (budget != null) {
            budget.setMonthlyLimitUsd(limit); // Cập nhật hạn mức tháng (USD)
            budget.setAlertThresholdPercent(threshold); // Cập nhật ngưỡng % cảnh báo
            return true;
        }
        return false;
    }

    /**
     * Kiểm tra chế độ khẩn cấp Break-glass có đang kích hoạt hay không.
     */
    public boolean isBreakGlassActive() {
        return breakGlassMode.get(); // Đọc cờ trạng thái AtomicBoolean
    }

    /**
     * Bật/tắt chế độ khẩn cấp Break-glass và ghi nhật ký bảo mật.
     */
    public boolean toggleBreakGlass(String reason) {
        boolean current = breakGlassMode.get(); // Lấy trạng thái hiện tại
        breakGlassMode.set(!current); // Đảo trạng thái bật <-> tắt
        logAction("Vũ Minh Quân", "ROLE_ADMIN", "BREAK_GLASS", "SYSTEM_SECURITY",
                (!current ? "KÍCH HOẠT" : "TẮT") + " chế độ khẩn cấp Break-glass. Lý do: " + (reason != null ? reason : "Kiểm tra bảo mật"), "127.0.0.1"); // Ghi audit log
        return breakGlassMode.get();
    }
}
