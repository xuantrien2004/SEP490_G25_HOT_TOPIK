package com.hottopik.dto.subscription;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Đề xuất thay đổi giá. Giá chính thức chỉ đổi khi Admin duyệt. */
@Getter
@Setter
public class ChangePriceRequest {

    @NotNull(message = "Vui lòng nhập giá mới.")
    @Positive(message = "Giá mới phải lớn hơn 0.")
    @Max(value = 1_000_000_000L, message = "Giá vượt quá giới hạn cho phép.")
    private Long newPrice;

    @NotBlank(message = "Lý do thay đổi giá là bắt buộc.")
    @Size(max = 1000, message = "Lý do tối đa 1000 ký tự.")
    private String reason;
}
