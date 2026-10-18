package com.hottopik.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HanjaWordDTO {
    private Long vocabularyId;
    private String hanjaWord;     // Chữ Hán (e.g. 學校, 大學)
    private String word;          // Tiếng Hàn (e.g. 학교, 대학)
    private String sinoVi;        // Âm Hán Việt (e.g. học hiệu, đại học)
    private String meaningVi;     // Nghĩa (e.g. trường học, trường đại học)
    private String audioUrl;      // Âm thanh đọc tiếng Hàn
    private String status;        // ACTIVE / DRAFT
}
