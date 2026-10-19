package com.hottopik.dto;

import lombok.*;

import java.util.List;

/**
 * Represents one quiz question for the Hanja "Đoán nghĩa từ mới" mode.
 * Contains the Korean word to guess, its Hanja, audio URL, and 4 choices (1 correct + 3 decoys).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HanjaQuizQuestionDTO {

    private Long vocabularyId;

    /** Korean word shown to the learner (e.g. 학교) */
    private String word;

    /** Hanja characters (e.g. 學校) */
    private String hanjaWord;

    /** Sino-Vietnamese reading (e.g. học hiệu) */
    private String sinoVi;

    /** Correct Vietnamese meaning */
    private String correctMeaning;

    /** Audio URL for pronunciation */
    private String audioUrl;

    /** Which root this word belongs to */
    private String rootChar;
    private String rootSinoVi;

    /**
     * 4 choices: one is correct, three are decoys.
     * The correct answer index is stored in correctChoiceIndex.
     */
    private List<String> choices;

    /** 0-based index of the correct choice in the choices list */
    private int correctChoiceIndex;
}
