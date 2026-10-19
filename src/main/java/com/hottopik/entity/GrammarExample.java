
package com.hottopik.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "grammar_examples", schema = "dbo")
public class GrammarExample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "grammar_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_grammar_examples_grammar_id")
    )
    private Grammar grammar;

    @Column(name = "sentence_ko", nullable = false, length = 500)
    private String sentenceKo;

    @Column(name = "sentence_vi", nullable = false, length = 500)
    private String sentenceVi;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder = 0;

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

    public GrammarExample() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Grammar getGrammar() {
        return grammar;
    }

    public void setGrammar(Grammar grammar) {
        this.grammar = grammar;
    }

    public String getSentenceKo() {
        return sentenceKo;
    }

    public void setSentenceKo(String sentenceKo) {
        this.sentenceKo = sentenceKo;
    }

    public String getSentenceVi() {
        return sentenceVi;
    }

    public void setSentenceVi(String sentenceVi) {
        this.sentenceVi = sentenceVi;
    }

    public Short getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Short sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
