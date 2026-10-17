package com.hottopik.dto;

public class GrammarDTO {
    private Long id;
    private String patternName;     // e.g. -아서/어서
    private String meaning;         // e.g. Vì... nên...
    private String grammarType;     // e.g. Nối mệnh đề chỉ nguyên nhân
    private String attachmentRule;  // e.g. V/A + -아/어서
    private Integer topikLevel;     // 1 to 6
    private String exampleKr;       // e.g. 배가 아파서 병원에 갔어요.
    private String exampleVi;       // e.g. Vì đau bụng nên tôi đã đến bệnh viện.
    private String importance;      // IMPORTANT, NORMAL
    private String status;          // ACTIVE, INACTIVE, DRAFT
    private String createdAt;
    private String updatedAt;

    public GrammarDTO() {}

    public GrammarDTO(Long id, String patternName, String meaning, String grammarType,
                      String attachmentRule, Integer topikLevel, String exampleKr,
                      String exampleVi, String importance, String status,
                      String createdAt, String updatedAt) {
        this.id = id;
        this.patternName = patternName;
        this.meaning = meaning;
        this.grammarType = grammarType;
        this.attachmentRule = attachmentRule;
        this.topikLevel = topikLevel;
        this.exampleKr = exampleKr;
        this.exampleVi = exampleVi;
        this.importance = importance;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPatternName() { return patternName; }
    public void setPatternName(String patternName) { this.patternName = patternName; }

    public String getMeaning() { return meaning; }
    public void setMeaning(String meaning) { this.meaning = meaning; }

    public String getGrammarType() { return grammarType; }
    public void setGrammarType(String grammarType) { this.grammarType = grammarType; }

    public String getAttachmentRule() { return attachmentRule; }
    public void setAttachmentRule(String attachmentRule) { this.attachmentRule = attachmentRule; }

    public Integer getTopikLevel() { return topikLevel; }
    public void setTopikLevel(Integer topikLevel) { this.topikLevel = topikLevel; }

    public String getExampleKr() { return exampleKr; }
    public void setExampleKr(String exampleKr) { this.exampleKr = exampleKr; }

    public String getExampleVi() { return exampleVi; }
    public void setExampleVi(String exampleVi) { this.exampleVi = exampleVi; }

    public String getImportance() { return importance; }
    public void setImportance(String importance) { this.importance = importance; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(this.status);
    }
}
