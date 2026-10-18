package com.hottopik.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HanjaRootDTO {
    private Integer id;
    private String hanjaChar;      // Cột chữ Hán (e.g. 學)
    private String sinoVi;         // Âm Hán Việt (e.g. Học)
    private String koReading;      // Tiếng Hàn (âm đọc e.g. 학)
    private String meaningVi;      // Nghĩa gốc (e.g. Học tập, trường học)
    private String audioUrl;       // Âm thanh đọc tiếng Hàn
    private Short sortOrder;       // Thứ tự sắp xếp
    private String status;         // Trạng thái (ACTIVE / DRAFT)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private List<HanjaWordDTO> relatedWords = new ArrayList<>();

    public int getRelatedWordsCount() {
        return relatedWords != null ? relatedWords.size() : 0;
    }
}
