package com.hottopik.dto;

import java.io.Serializable;

public class QuestionBankItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String code;
    private String skill; // READING, LISTENING, WRITING
    private String level; // TOPIK_I, TOPIK_II
    private String questionType;
    private String contentSummary;
    private int difficulty; // 1 to 5
    private boolean hasAudio;
    private boolean active;

    public QuestionBankItemDTO() {}

    public QuestionBankItemDTO(Long id, String code, String skill, String level,
                               String questionType, String contentSummary,
                               int difficulty, boolean hasAudio, boolean active) {
        this.id = id;
        this.code = code;
        this.skill = skill;
        this.level = level;
        this.questionType = questionType;
        this.contentSummary = contentSummary;
        this.difficulty = difficulty;
        this.hasAudio = hasAudio;
        this.active = active;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getSkill() { return skill; }
    public void setSkill(String skill) { this.skill = skill; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public String getContentSummary() { return contentSummary; }
    public void setContentSummary(String contentSummary) { this.contentSummary = contentSummary; }

    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }

    public boolean isHasAudio() { return hasAudio; }
    public void setHasAudio(boolean hasAudio) { this.hasAudio = hasAudio; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
