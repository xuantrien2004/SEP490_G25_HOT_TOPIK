package com.hottopik.dto;

public class GrammarTypeDTO {
    private Integer id; // Thêm trường id này
    private String name;
    private int level;
    private int grammarCount;
    private String description;
    private boolean active;

    // Cập nhật constructor có nhận id hoặc thêm getter/setter đầy đủ
    public GrammarTypeDTO(Integer id, String name, int level, int grammarCount, String description, boolean active) {
        this.id = id;
        this.name = name;
        this.level = level;
        this.grammarCount = grammarCount;
        this.description = description;
        this.active = active;
    }

    // Getters và Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getGrammarCount() { return grammarCount; }
    public void setGrammarCount(int grammarCount) { this.grammarCount = grammarCount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}