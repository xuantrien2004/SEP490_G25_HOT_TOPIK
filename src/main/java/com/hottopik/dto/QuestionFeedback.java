package com.hottopik.dto;

public record QuestionFeedback(int answer, boolean correct, String explanation, String script,
                               String scriptTranslation, Integer selected) {
    public QuestionFeedback(int answer, boolean correct, String explanation, String script, String scriptTranslation) {
        this(answer, correct, explanation, script, scriptTranslation, null);
    }
}
