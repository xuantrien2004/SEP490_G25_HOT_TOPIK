package com.hottopik;

import com.hottopik.dto.VocabularyDTO;
import com.hottopik.service.VocabularyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VocabularyServiceTest {

    private VocabularyService vocabularyService;

    @BeforeEach
    void setUp() {
        vocabularyService = new VocabularyService();
    }

    @Test
    @DisplayName("Verify Initial Sample Dataset contains TOPIK 1 to 6")
    void testInitialData() {
        List<VocabularyDTO> all = vocabularyService.getAll();
        assertNotNull(all);
        assertTrue(all.size() >= 20, "Should contain at least 20 initial vocabularies");

        long level1Count = all.stream().filter(v -> v.getTopikLevel() == 1).count();
        long level6Count = all.stream().filter(v -> v.getTopikLevel() == 6).count();
        assertTrue(level1Count > 0, "Level 1 vocabularies should exist");
        assertTrue(level6Count > 0, "Level 6 vocabularies should exist");
    }

    @Test
    @DisplayName("Test Search and Filter by Keyword, Level, and Topic")
    void testSearchAndFilter() {
        // Search by Korean word
        List<VocabularyDTO> results = vocabularyService.searchAndFilter("가족", null, null, null, null);
        assertFalse(results.isEmpty());
        assertEquals("가족", results.get(0).getWord());

        // Search by Vietnamese meaning
        List<VocabularyDTO> meaningResults = vocabularyService.searchAndFilter("môi trường", null, null, null, null);
        assertFalse(meaningResults.isEmpty());
        assertTrue(meaningResults.stream().anyMatch(v -> v.getMeaning().toLowerCase().contains("môi trường")));

        // Filter by Level 1
        List<VocabularyDTO> level1Results = vocabularyService.searchAndFilter(null, 1, null, null, null);
        assertTrue(level1Results.stream().allMatch(v -> v.getTopikLevel() == 1));

        // Filter by Topic "Gia đình"
        List<VocabularyDTO> topicResults = vocabularyService.searchAndFilter(null, null, "Gia đình", null, null);
        assertFalse(topicResults.isEmpty());
        assertTrue(topicResults.stream().allMatch(v -> "Gia đình".equalsIgnoreCase(v.getTopic())));
    }

    @Test
    @DisplayName("Test Manual Vocabulary Creation and Editing")
    void testManualCreationAndEdit() {
        VocabularyDTO newWord = new VocabularyDTO();
        newWord.setWord("사랑하다");
        newWord.setRomanization("sa-rang-ha-da");
        newWord.setMeaning("Yêu thương");
        newWord.setWordType("Động từ");
        newWord.setTopikLevel(1);
        newWord.setTopic("Tình cảm & Cảm xúc");
        newWord.setExampleKr("부모님을 사랑합니다.");
        newWord.setExampleVi("Tôi yêu thương cha mẹ.");
        newWord.setStatus("ACTIVE");

        VocabularyDTO saved = vocabularyService.saveOrUpdate(newWord);
        assertNotNull(saved.getId());
        assertEquals("사랑하다", saved.getWord());
        assertEquals("TOPIK I · Cấp 1", saved.getTopikLevelName());

        // Edit
        saved.setMeaning("Yêu quý / Yêu thương sâu sắc");
        VocabularyDTO updated = vocabularyService.saveOrUpdate(saved);
        assertEquals("Yêu quý / Yêu thương sâu sắc", updated.getMeaning());
        assertEquals(saved.getId(), updated.getId());
    }

    @Test
    @DisplayName("Test Single Word Status Toggle")
    void testToggleWordStatus() {
        VocabularyDTO item = vocabularyService.getAll().get(0);
        Long id = item.getId();
        String initialStatus = item.getStatus();

        boolean toggled = vocabularyService.toggleWordStatus(id);
        assertTrue(toggled);

        VocabularyDTO reloaded = vocabularyService.getById(id);
        assertNotEquals(initialStatus, reloaded.getStatus());
    }

    @Test
    @DisplayName("Test Bulk Toggle by TOPIK Level")
    void testBulkToggleLevel() {
        // Hide all Level 1 words
        int affected = vocabularyService.toggleTopikLevelStatus(1, false);
        assertTrue(affected > 0);

        List<VocabularyDTO> level1 = vocabularyService.searchAndFilter(null, 1, null, null, null);
        assertTrue(level1.stream().allMatch(v -> "INACTIVE".equals(v.getStatus())));

        // Reactivate all Level 1 words
        int reactivated = vocabularyService.toggleTopikLevelStatus(1, true);
        assertEquals(affected, reactivated);
        List<VocabularyDTO> level1Active = vocabularyService.searchAndFilter(null, 1, null, null, null);
        assertTrue(level1Active.stream().allMatch(v -> "ACTIVE".equals(v.getStatus())));
    }

    @Test
    @DisplayName("Test Bulk Toggle by Topic")
    void testBulkToggleTopic() {
        int affected = vocabularyService.toggleTopicStatus("Gia đình", false);
        assertTrue(affected > 0);

        List<VocabularyDTO> topicWords = vocabularyService.searchAndFilter(null, null, "Gia đình", null, null);
        assertTrue(topicWords.stream().allMatch(v -> "INACTIVE".equals(v.getStatus())));
    }

    @Test
    @DisplayName("Test Bulk Create (Excel Import Simulation)")
    void testBulkCreate() {
        VocabularyDTO v1 = new VocabularyDTO(null, "책상", "chaek-sang", "Bàn học", "Danh từ", 1, "Trường học", null, "책상 위에 책이 있다.", "Trên bàn có quyển sách.", "ACTIVE", null, null);
        VocabularyDTO v2 = new VocabularyDTO(null, "의자", "ui-ja", "Ghế ngồi", "Danh từ", 1, "Trường học", null, "의자에 앉으세요.", "Xin hãy ngồi vào ghế.", "ACTIVE", null, null);

        int count = vocabularyService.bulkCreate(List.of(v1, v2));
        assertEquals(2, count);

        assertFalse(vocabularyService.searchAndFilter("책상", null, null, null, null).isEmpty());
        assertFalse(vocabularyService.searchAndFilter("의자", null, null, null, null).isEmpty());
    }

    @Test
    @DisplayName("Test Topic CRUD and Delete Cascade on Vocabularies")
    void testTopicCrudAndDelete() {
        // Create topic
        var topic = vocabularyService.createTopic(1, "Sở thích mới", "Mô tả sở thích");
        assertNotNull(topic);
        assertEquals("Sở thích mới", topic.getName());

        // Add 2 vocabularies to this new topic
        VocabularyDTO v1 = new VocabularyDTO(null, "그림", null, "Bức tranh", "Danh từ", 1, "Sở thích mới", null, null, null, "ACTIVE", null, null);
        VocabularyDTO v2 = new VocabularyDTO(null, "음악", null, "Âm nhạc", "Danh từ", 1, "Sở thích mới", null, null, null, "ACTIVE", null, null);
        vocabularyService.saveOrUpdate(v1);
        vocabularyService.saveOrUpdate(v2);

        assertEquals(2, vocabularyService.getWordCountInTopic(1, "Sở thích mới"));

        // Update topic name
        vocabularyService.updateTopic(1, "Sở thích mới", "Nghệ thuật & Sở thích", "Mô tả mới");
        assertEquals(2, vocabularyService.getWordCountInTopic(1, "Nghệ thuật & Sở thích"));

        // Delete topic: must delete topic AND all 2 vocabularies
        int deletedWords = vocabularyService.deleteTopic(1, "Nghệ thuật & Sở thích");
        assertEquals(2, deletedWords);
        assertEquals(0, vocabularyService.getWordCountInTopic(1, "Nghệ thuật & Sở thích"));
    }

    @Test
    @DisplayName("Test Korean Hangul Auto-Romanizer")
    void testKoreanRomanizer() {
        String rom1 = com.hottopik.util.KoreanRomanizer.romanize("가족");
        assertEquals("ga-jok", rom1);

        String rom2 = com.hottopik.util.KoreanRomanizer.romanize("학교");
        assertEquals("hak-gyo", rom2);
    }
}
