package com.hottopik.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class    VocabularyDTO {
    private Long id;
    private String word;            // Từ tiếng Hàn
    private String romanization;     // Phiên âm Latin
    private String meaning;          // Nghĩa tiếng Việt
    private String wordType;         // Loại từ: Danh từ, Động từ, Tính từ, Phó từ, Cụm từ
    private Integer topikLevel;      // 1, 2, 3, 4, 5, 6
    private String topikLevelName;   // TOPIK I (Cấp 1), TOPIK II (Cấp 3)...
    private String topic;            // Chủ đề (Gia đình, Đời sống, v.v.)
    private String audioUrl;         // Đường dẫn âm thanh
    private String exampleKr;        // Ví dụ tiếng Hàn
    private String exampleVi;        // Dịch ví dụ
    private String status;           // ACTIVE / INACTIVE
    private String createdAt;        // Ngày tạo dd/MM/yyyy HH:mm
    private String updatedAt;        // Ngày sửa dd/MM/yyyy HH:mm

    public VocabularyDTO() {
        this.status = "ACTIVE";
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        this.createdAt = now.format(dtf);
        this.updatedAt = now.format(dtf);
    }

    public VocabularyDTO(Long id, String word, String romanization, String meaning, String wordType,
                         Integer topikLevel, String topic, String audioUrl, String exampleKr,
                         String exampleVi, String status, String createdAt, String updatedAt) {
        this.id = id;
        this.word = word;
        this.romanization = romanization;
        this.meaning = meaning;
        this.wordType = wordType;
        this.topikLevel = topikLevel;
        this.topikLevelName = formatTopikName(topikLevel);
        this.topic = topic;
        this.audioUrl = audioUrl;
        this.exampleKr = exampleKr;
        this.exampleVi = exampleVi;
        this.status = (status != null && !status.isEmpty()) ? status : "ACTIVE";
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static String formatTopikName(Integer level) {
        if (level == null) return "Chưa phân cấp";
        switch (level) {
            case 1: return "TOPIK I · Cấp 1";
            case 2: return "TOPIK I · Cấp 2";
            case 3: return "TOPIK II · Cấp 3";
            case 4: return "TOPIK II · Cấp 4";
            case 5: return "TOPIK II · Cấp 5";
            case 6: return "TOPIK II · Cấp 6";
            default: return "Cấp " + level;
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getWord() {
        return word;
    }
    public void setWord(String word) {
        this.word = word;
    }
    public String getRomanization() {
        return romanization;
    }
    public void setRomanization(String romanization) {
        this.romanization = romanization;
    }
    public String getMeaning() {
        return meaning;
    }
    public void setMeaning(String meaning) {
        this.meaning = meaning;
    }
    public String getWordType() {
        return wordType;
    }
    public void setWordType(String wordType) {
        this.wordType = wordType;
    }
    public Integer getTopikLevel() {
        return topikLevel;
    }
    public void setTopikLevel(Integer topikLevel) {
        this.topikLevel = topikLevel;
        this.topikLevelName = formatTopikName(topikLevel);
    }
    public String getTopikLevelName() {
        return topikLevelName;
    }
    public void setTopikLevelName(String topikLevelName) {
        this.topikLevelName = topikLevelName;
    }
    public String getTopic() {
        return topic;
    }
    public void setTopic(String topic) {
        this.topic = topic;
    }
    public String getAudioUrl() {
        return audioUrl;
    }
    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }
    public String getExampleKr() {
        return exampleKr;
    }
    public void setExampleKr(String exampleKr) {
        this.exampleKr = exampleKr;
    }
    public String getExampleVi() {
        return exampleVi;
    }
    public void setExampleVi(String exampleVi) {
        this.exampleVi = exampleVi;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public String getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    public String getUpdatedAt() {
        return updatedAt;
    }
    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(this.status);
    }
}
