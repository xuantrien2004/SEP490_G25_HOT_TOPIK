package com.hottopik.service;

import com.hottopik.dto.HanjaImportResultDTO;
import com.hottopik.dto.HanjaRootDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface HanjaService {

    List<HanjaRootDTO> getAllHanjaRoots(String query, String status);

    HanjaRootDTO getHanjaRootById(Integer id);

    HanjaRootDTO createHanjaRoot(HanjaRootDTO dto);

    HanjaRootDTO updateHanjaRoot(Integer id, HanjaRootDTO dto);

    void deleteHanjaRoot(Integer id);

    byte[] exportTemplateExcel();

    HanjaImportResultDTO importExcel(MultipartFile file);

    long getTotalRootsCount();

    long getActiveRootsCount();

    long getDraftRootsCount();

    long getTotalRelatedWordsCount();

    void seedInitialHanjaRootsIfEmpty();
}
