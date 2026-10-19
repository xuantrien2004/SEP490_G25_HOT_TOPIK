
package com.hottopik.dto;

import java.util.ArrayList;
import java.util.List;

public class GrammarForm {

    private Long id;

    private Integer groupId;

    private Short level;

    private String pattern;

    private String translation;

    private String meaning;

    private String explanation;

    private String notes;

    private Boolean isFrequent = false;

    private Short sortOrder = 0;

    private String status = "DRAFT";

    private List<GrammarExampleForm> examples = new ArrayList<>();

    public GrammarForm() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public Short getLevel() {
        return level;
    }

    public void setLevel(Short level) {
        this.level = level;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
    }

    public String getMeaning() {
        return meaning;
    }

    public void setMeaning(String meaning) {
        this.meaning = meaning;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Boolean getIsFrequent() {
        return isFrequent;
    }

    public void setIsFrequent(Boolean isFrequent) {
        this.isFrequent = isFrequent;
    }

    public Short getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Short sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<GrammarExampleForm> getExamples() {
        return examples;
    }

    public void setExamples(List<GrammarExampleForm> examples) {
        this.examples = examples != null
                ? examples
                : new ArrayList<>();
    }
}
