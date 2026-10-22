package com.hottopik.service;

import com.hottopik.dto.HanjaImportResultDTO;
import com.hottopik.dto.HanjaQuizQuestionDTO;
import com.hottopik.dto.HanjaRootDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface HanjaService {

    List<HanjaRootDTO> getAllHanjaRoots(String query, String status);

    /** For learner: only ACTIVE roots, with optional search query */
    List<HanjaRootDTO> getActiveHanjaRoots(String query);

    HanjaRootDTO getHanjaRootById(Integer id);

    HanjaRootDTO createHanjaRoot(HanjaRootDTO dto);

    HanjaRootDTO updateHanjaRoot(Integer id, HanjaRootDTO dto);

    HanjaRootDTO toggleRootStatus(Integer id);

    void deleteHanjaRoot(Integer id);

    List<com.hottopik.dto.HanjaWordDTO> getWordsByRootId(Integer rootId);

    com.hottopik.dto.HanjaWordDTO createWord(Integer rootId, com.hottopik.dto.HanjaWordDTO dto);

    com.hottopik.dto.HanjaWordDTO updateWord(Long vocabId, com.hottopik.dto.HanjaWordDTO dto);

    void deleteWord(Integer rootId, Long vocabId);

    com.hottopik.dto.HanjaWordDTO toggleWordStatus(Long vocabId);

    byte[] exportTemplateExcel();

    HanjaImportResultDTO importExcel(MultipartFile file);

    com.hottopik.dto.HanjaImportPreviewDTO previewImportExcel(MultipartFile file);

    Map<String, Object> confirmImport(List<HanjaRootDTO> rootsToSave);

    long getTotalRootsCount();

    long getActiveRootsCount();

    long getDraftRootsCount();

    long getTotalRelatedWordsCount();

    void seedInitialHanjaRootsIfEmpty();

    /** Generate randomized quiz questions for Guess-Meaning mode.
     *  Each question contains: the Korean word, 1 correct meaning, 3 wrong choices.
     *  @param count number of questions to generate (default 10)
     */
    List<HanjaQuizQuestionDTO> getRandomQuizQuestions(int count);

    /** Stats for learner progress panel */
    Map<String, Object> getLearnerStats();
}
