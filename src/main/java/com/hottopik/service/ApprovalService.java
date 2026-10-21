package com.hottopik.service;

import com.hottopik.dto.ApprovalRequestDTO;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class ApprovalService {

    private final Map<Long, ApprovalRequestDTO> requests = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(100);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @PostConstruct
    public void init() {
        // Seed matching SEP490.sql
        requests.put(1L, new ApprovalRequestDTO(
                1L,
                "Lê Thu Hà",
                "Content Manager",
                "PUBLISH_EXAM",
                "Phát hành đề thi thử Full Mock #2",
                "Đề gồm 100 câu Đọc & Nghe TOPIK II đã biên soạn và rà soát xong barem điểm.",
                "PENDING",
                "2026-10-06 14:30"
        ));

        requests.put(2L, new ApprovalRequestDTO(
                2L,
                "Trần Quốc Tuấn",
                "Operation Manager",
                "REFUND_ORDER",
                "Hoàn tiền đơn hàng #HT-10089",
                "Học viên chuyển nhầm cú pháp gói Trọn đời 499.000đ sang Momo cá nhân.",
                "PENDING",
                "2026-10-07 09:15"
        ));

        requests.put(3L, new ApprovalRequestDTO(
                3L,
                "Trần Quốc Tuấn",
                "Operation Manager",
                "VIP_COMPENSATION",
                "Tặng 30 ngày VIP bù sự cố đường truyền",
                "Cấp bù ngày học cho học viên le.van.c@gmail.com do lỗi server ngày 05/10.",
                "PENDING",
                "2026-10-07 11:20"
        ));
    }

    public List<ApprovalRequestDTO> getAllRequests() {
        return requests.values().stream()
                .sorted(Comparator.comparing(ApprovalRequestDTO::getId).reversed())
                .collect(Collectors.toList());
    }

    public List<ApprovalRequestDTO> getPendingRequests() {
        return requests.values().stream()
                .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()))
                .sorted(Comparator.comparing(ApprovalRequestDTO::getId).reversed())
                .collect(Collectors.toList());
    }

    public List<ApprovalRequestDTO> getRequestsByRole(String role) {
        return requests.values().stream()
                .filter(r -> r.getRequesterRole().equalsIgnoreCase(role))
                .sorted(Comparator.comparing(ApprovalRequestDTO::getId).reversed())
                .collect(Collectors.toList());
    }

    public ApprovalRequestDTO submitRequest(String requesterName, String requesterRole,
                                            String requestType, String title, String description) {
        return submitRequest(requesterName, requesterRole, requestType, title, description, null);
    }

    public ApprovalRequestDTO submitRequest(String requesterName, String requesterRole,
                                            String requestType, String title, String description, Long targetId) {
        long id = idGenerator.incrementAndGet();
        ApprovalRequestDTO req = new ApprovalRequestDTO(
                id,
                requesterName,
                requesterRole,
                requestType,
                title,
                description,
                "PENDING",
                LocalDateTime.now().format(FORMATTER)
        );
        req.setTargetId(targetId);
        requests.put(id, req);
        return req;
    }

    public Optional<ApprovalRequestDTO> getRequestById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(requests.get(id));
    }

    public boolean hasPendingPermanentLockRequest(Long userId) {
        if (userId == null) return false;
        return requests.values().stream()
                .anyMatch(r -> "PENDING".equalsIgnoreCase(r.getStatus())
                        && "PERMANENT_LOCK_USER".equalsIgnoreCase(r.getRequestType())
                        && userId.equals(r.getTargetId()));
    }

    public boolean decideRequest(Long id, boolean approve, String note) {
        ApprovalRequestDTO req = requests.get(id);
        if (req != null) {
            req.setStatus(approve ? "APPROVED" : "REJECTED");
            req.setDecisionNote(note);
            req.setDecidedAt(LocalDateTime.now().format(FORMATTER));
            return true;
        }
        return false;
    }
}
