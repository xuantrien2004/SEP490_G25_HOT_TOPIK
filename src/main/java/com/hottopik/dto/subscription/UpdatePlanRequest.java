package com.hottopik.dto.subscription;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Sửa gói học. Gói DRAFT: lưu trực tiếp. Gói đã được duyệt: tạo yêu cầu phê duyệt (cần lý do),
 * không cập nhật dữ liệu chính thức ngay.
 */
@Getter
@Setter
public class UpdatePlanRequest {

    @NotBlank(message = "Tên gói không được để trống.")
    @Size(max = 100, message = "Tên gói tối đa 100 ký tự.")
    private String name;

    @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự.")
    private String description;

    @NotNull(message = "Vui lòng nhập giá.")
    @Positive(message = "Giá gói Premium phải lớn hơn 0.")
    @Max(value = 1_000_000_000L, message = "Giá vượt quá giới hạn cho phép.")
    private Long price;

    @NotNull(message = "Vui lòng nhập thời hạn.")
    @Positive(message = "Thời hạn phải lớn hơn 0 ngày.")
    @Max(value = 3650, message = "Thời hạn tối đa 3650 ngày.")
    private Integer durationDays;

    private boolean featured;

    @PositiveOrZero(message = "Thứ tự hiển thị không được âm.")
    private int sortOrder;

    /** Mã quyền lợi được chọn (chỉ áp dụng khi đề xuất thay đổi quyền lợi). */
    private List<String> featureCodes = new ArrayList<>();

    @Size(max = 1000, message = "Lý do tối đa 1000 ký tự.")
    private String reason;
}
