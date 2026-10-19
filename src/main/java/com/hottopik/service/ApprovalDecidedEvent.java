package com.hottopik.service;

import com.hottopik.dto.ApprovalRequestDTO;

/**
 * Phát ra khi Admin duyệt / từ chối một yêu cầu, trước khi trạng thái yêu cầu được ghi nhận.
 * Listener ném lỗi thì quyết định bị huỷ (payload áp dụng "trong một transaction", BRD 10.3).
 */
public record ApprovalDecidedEvent(ApprovalRequestDTO request, boolean approved, String decidedBy, String note) {
}
