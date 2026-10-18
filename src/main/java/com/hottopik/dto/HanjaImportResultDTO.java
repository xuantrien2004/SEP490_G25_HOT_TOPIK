package com.hottopik.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HanjaImportResultDTO {
    private boolean success;
    private String message;
    private int totalRows;               // Tổng số từ trong file excel
    private int duplicateInFileCount;    // Số từ trùng lặp trong file excel
    private int existingInDbCount;       // Số từ đã tồn tại trong hệ thống
    private int successCount;            // Số từ thêm mới thành công
    private int errorCount;              // Số dòng lỗi định dạng

    @Builder.Default
    private List<String> duplicateInFileDetails = new ArrayList<>(); // Danh sách từ trùng trong file

    @Builder.Default
    private List<String> existingInDbDetails = new ArrayList<>();    // Danh sách từ đã có trong hệ thống

    @Builder.Default
    private List<String> errorDetails = new ArrayList<>();           // Chi tiết các lỗi khác (thiếu trường, v.v.)

    @Builder.Default
    private List<String> successDetails = new ArrayList<>();         // Danh sách từ đã import thành công
}
