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

    /**
     * Khởi tạo các yêu cầu phê duyệt mẫu ban đầu.
     */
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

    /**
     * Lấy toàn bộ danh sách yêu cầu phê duyệt, sắp xếp mới nhất trước.
     */
    public List<ApprovalRequestDTO> getAllRequests() {
        return requests.values().stream()
                .sorted(Comparator.comparing(ApprovalRequestDTO::getId).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách các yêu cầu đang ở trạng thái chờ duyệt (PENDING).
     */
    public List<ApprovalRequestDTO> getPendingRequests() {
        return requests.values().stream()
                .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()))
                .sorted(Comparator.comparing(ApprovalRequestDTO::getId).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Lọc danh sách yêu cầu phê duyệt theo vai trò người gửi.
     */
    public List<ApprovalRequestDTO> getRequestsByRole(String role) {
        return requests.values().stream()
                .filter(r -> r.getRequesterRole().equalsIgnoreCase(role))
                .sorted(Comparator.comparing(ApprovalRequestDTO::getId).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Gửi một yêu cầu phê duyệt chung lên Admin.
     */
    public ApprovalRequestDTO submitRequest(String requesterName, String requesterRole,
                                            String requestType, String title, String description) {
        return submitRequest(requesterName, requesterRole, requestType, title, description, null);
    }

    /**
     * Gửi yêu cầu phê duyệt gắn với một đối tượng mục tiêu (targetId).
     */
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

    /**
     * Gửi yêu cầu phê duyệt có kiểm tra trùng lặp theo đối tượng và loại yêu cầu đang PENDING.
     */
    public ApprovalRequestDTO submitTargetedRequest(String requesterName, String requesterRole,
                                                    String requestType, String title, String description,
                                                    String targetType, Long targetId, String payload) {
        boolean duplicate = requests.values().stream().anyMatch(r -> "PENDING".equalsIgnoreCase(r.getStatus())
                && targetType.equals(r.getTargetType()) && Objects.equals(targetId, r.getTargetId())
                && requestType.equals(r.getRequestType()));
        if (duplicate) {
            throw new IllegalStateException("Đối tượng này đang chờ duyệt, không gửi trùng được.");
        }
        ApprovalRequestDTO req = submitRequest(requesterName, requesterRole, requestType, title, description);
        req.setTargetType(targetType);
        req.setTargetId(targetId);
        req.setPayload(payload);
        return req;
    }

    /**
     * Tìm yêu cầu phê duyệt theo ID.
     */
    public Optional<ApprovalRequestDTO> findById(Long id) {
        return Optional.ofNullable(requests.get(id));
    }

    /**
     * Lấy chi tiết yêu cầu phê duyệt theo ID (trả về empty nếu id null).
     */
    public Optional<ApprovalRequestDTO> getRequestById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(requests.get(id));
    }

    /**
     * Kiểm tra học viên có đang có yêu cầu khóa vĩnh viễn chờ duyệt hay không.
     */
    public boolean hasPendingPermanentLockRequest(Long userId) {
        if (userId == null) return false;
        return requests.values().stream()
                .anyMatch(r -> "PENDING".equalsIgnoreCase(r.getStatus())
                        && "PERMANENT_LOCK_USER".equalsIgnoreCase(r.getRequestType())
                        && userId.equals(r.getTargetId()));
    }

    /**
     * Phê duyệt hoặc từ chối yêu cầu (không kèm tên người quyết định).
     */
    public boolean decideRequest(Long id, boolean approve, String note) {
        return decideRequest(id, approve, note, null);
    }

    /**
     * Không ai duyệt yêu cầu do chính mình gửi (BRD 2.2). Listener của ApprovalDecidedEvent áp dụng payload;
     * nếu listener ném lỗi thì yêu cầu vẫn ở trạng thái PENDING.
     */
    public boolean decideRequest(Long id, boolean approve, String note, String deciderName) {
        ApprovalRequestDTO req = requests.get(id); // Tìm yêu cầu theo ID
        if (req == null) return false;
        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            throw new IllegalStateException("Yêu cầu này đã được xử lý."); // Chặn xử lý lại yêu cầu đã đóng
        }
        if (deciderName != null && deciderName.equalsIgnoreCase(req.getRequesterName())) {
            throw new IllegalStateException("Không được duyệt yêu cầu do chính mình gửi; cần một Admin khác."); // Nguyên tắc tách biệt người gửi và người duyệt
        }
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new ApprovalDecidedEvent(req, approve, deciderName, note)); // Phát sự kiện để listener thực thi payload nếu có
        }
        req.setStatus(approve ? "APPROVED" : "REJECTED"); // Cập nhật trạng thái duyệt hoặc từ chối
        req.setDecisionNote(note); // Lưu ghi chú quyết định
        req.setDecidedBy(deciderName); // Lưu tên người quyết định
        req.setDecidedAt(LocalDateTime.now().format(FORMATTER)); // Ghi nhận thời điểm quyết định
        return true;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
}
