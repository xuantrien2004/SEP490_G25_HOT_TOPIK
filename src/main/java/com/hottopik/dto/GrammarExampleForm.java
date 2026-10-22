
package com.hottopik.dto;

public class GrammarExampleForm {

    private Long id;

    private String sentenceKo;

    private String sentenceVi;

    private Short sortOrder = 0;

    public GrammarExampleForm() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
