package com.hottopik.dto;

import lombok.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HanjaImportPreviewDTO implements Serializable {
    private boolean success;
    private String message;
    private int totalRows;
    private int validCount;
    private int duplicateInFileCount;
    private int existingInDbCount;
    private int errorCount;

    @Builder.Default
    private List<ValidRowDetail> validRows = new ArrayList<>();

    @Builder.Default
    private List<DuplicateInFileDetail> duplicateInFileRows = new ArrayList<>();

    @Builder.Default
    private List<ExistingInDbDetail> existingInDbRows = new ArrayList<>();

    @Builder.Default
    private List<ErrorRowDetail> errorRows = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ValidRowDetail implements Serializable {
        private int rowIndex;
        private String hanjaChar;
        private String sinoVi;
        private String koReading;
        private String audioUrl;
        private int relatedWordsCount;
        private HanjaRootDTO rootDto;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DuplicateInFileDetail implements Serializable {
        private int rowIndex;
        private String hanjaChar;
        private String sinoVi;
        private String koReading;
        private int originalRowIndex;
        private String originalSinoVi;
        private String originalKoReading;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExistingInDbDetail implements Serializable {
        private int rowIndex;
        // Dữ liệu từ file Excel
        private String fileHanjaChar;
        private String fileSinoVi;
        private String fileKoReading;

        // Dữ liệu nguồn trong hệ thống CSDL để đối chiếu
        private Integer dbId;
        private String dbHanjaChar;
        private String dbSinoVi;
        private String dbKoReading;
        private String dbMeaningVi;
        private String dbStatus;
        private int dbRelatedWordsCount;
        private String dbCreatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorRowDetail implements Serializable {
        private int rowIndex;
        private String hanjaChar;
        private String sinoVi;
        private String koReading;
        private String errorMessage;
    }
}
