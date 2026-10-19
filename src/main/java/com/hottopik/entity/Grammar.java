
package com.hottopik.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "grammars", schema = "dbo")
public class Grammar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "group_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_grammars_group_id")
    )
    private GrammarGroup grammarGroup;

    @Column(name = "level", nullable = false)
    private Short level;

    @Column(name = "pattern", nullable = false, length = 100)
    private String pattern;

    @Column(name = "translation", nullable = false, length = 200)
    private String translation;

    @Column(name = "meaning", length = 1000)
    private String meaning;

    @Lob
    @Column(name = "explanation", columnDefinition = "nvarchar(max)")
    private String explanation;

    @Lob
    @Column(name = "notes", columnDefinition = "nvarchar(max)")
    private String notes;

    @Column(name = "is_frequent", nullable = false)
    private Boolean isFrequent = false;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder = 0;

    @Column(name = "status", nullable = false, length = 10)
    private String status = "DRAFT";

    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private LocalDateTime updatedAt;

    @OneToMany(
            mappedBy = "grammar",
            fetch = FetchType.LAZY,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("sortOrder ASC, id ASC")
    private List<GrammarExample> examples = new ArrayList<>();

    public Grammar() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public GrammarGroup getGrammarGroup() {
        return grammarGroup;
    }

    public void setGrammarGroup(GrammarGroup grammarGroup) {
        this.grammarGroup = grammarGroup;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<GrammarExample> getExamples() {
        return examples;
    }

    public void setExamples(List<GrammarExample> examples) {
        this.examples.clear();

        if (examples != null) {
            for (GrammarExample example : examples) {
                addExample(example);
            }
        }
    }

    public void addExample(GrammarExample example) {
        if (example != null) {
            examples.add(example);
            example.setGrammar(this);
        }
    }

    public void removeExample(GrammarExample example) {
        if (example != null) {
            examples.remove(example);
            example.setGrammar(null);
        }
    }
}
