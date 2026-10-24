package com.hottopik;

import com.hottopik.dto.VocabularyDTO;
import com.hottopik.entity.Topic;
import com.hottopik.entity.Vocabulary;
import com.hottopik.repository.TopicRepository;
import com.hottopik.repository.VocabularyRepository;
import com.hottopik.service.VocabularyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        VocabularyDTO v1 = new VocabularyDTO(null, "칠판", "chil-pan", "Bảng đen", "Danh từ", 1, "Trường học", null, "선생님이 칠판에 글씨를 쓰십니다.", "Thầy giáo viết chữ lên bảng.", "ACTIVE", null, null);
        VocabularyDTO v2 = new VocabularyDTO(null, "의자", "ui-ja", "Ghế ngồi", "Danh từ", 1, "Trường học", null, "의자에 앉으세요.", "Xin hãy ngồi vào ghế.", "ACTIVE", null, null);

        int count = vocabularyService.bulkCreate(List.of(v1, v2));
        assertEquals(2, count);

        assertFalse(vocabularyService.searchAndFilter("칠판", null, null, null, null).isEmpty());
        assertFalse(vocabularyService.searchAndFilter("의자", null, null, null, null).isEmpty());
    }

    @Test
    @DisplayName("Test Duplicate Vocabulary Manual Save Merges Into One")
    void testManualSaveDuplicateWord() {
        VocabularyDTO w1 = new VocabularyDTO(null, "행복", "haeng-bok", "Hạnh phúc", "Danh từ", 1, "Cảm xúc", null, "행복하세요", "Chúc hạnh phúc", "ACTIVE", null, null);
        VocabularyDTO saved1 = vocabularyService.saveOrUpdate(w1);
        assertNotNull(saved1.getId());
        assertEquals("Hạnh phúc", saved1.getMeaning());

        // Cùng từ "행복" ở cấp 1 nhưng nhập mới (id = null) với nghĩa mới
        VocabularyDTO w2 = new VocabularyDTO(null, "행복", null, "Niềm vui và hạnh phúc trọn vẹn", "Danh từ", 1, "Cảm xúc", null, null, null, "ACTIVE", null, null);
        VocabularyDTO saved2 = vocabularyService.saveOrUpdate(w2);

        // Phải tái sử dụng ID của từ đã tồn tại, không sinh ra ID mới
        assertEquals(saved1.getId(), saved2.getId());
        assertEquals("Niềm vui và hạnh phúc trọn vẹn", saved2.getMeaning());

        // Kiểm tra danh sách chỉ có đúng 1 từ "행복" ở cấp 1
        List<VocabularyDTO> list = vocabularyService.searchAndFilter("행복", 1, null, null, null);
        assertEquals(1, list.size());
    }

    @Test
    @DisplayName("Test Bulk Create Deduplication (In-batch and Existing Word)")
    void testBulkCreateDeduplication() {
        // "가족" đã có sẵn trong dữ liệu mẫu cấp 1
        // "시계" xuất hiện 2 lần trong cùng 1 đợt import
        VocabularyDTO dupExisting = new VocabularyDTO(null, "가족", null, "Gia đình mới", "Danh từ", 1, "Gia đình", null, null, null, "ACTIVE", null, null);
        VocabularyDTO newWord1 = new VocabularyDTO(null, "시계", null, "Đồng hồ", "Danh từ", 1, "Đồ vật", null, null, null, "ACTIVE", null, null);
        VocabularyDTO newWord2Duplicate = new VocabularyDTO(null, "시계", null, "Đồng hồ đeo tay", "Danh từ", 1, "Đồ vật", null, null, null, "ACTIVE", null, null);

        int count = vocabularyService.bulkCreate(List.of(dupExisting, newWord1, newWord2Duplicate));

        // Chỉ tạo đúng 1 từ duy nhất ("시계" đầu tiên), bỏ qua "가족" (đã tồn tại) và "시계" thứ 2 (trùng trong batch)
        assertEquals(1, count);

        List<VocabularyDTO> results = vocabularyService.searchAndFilter("시계", 1, null, null, null);
        assertEquals(1, results.size());
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

    @Test
    @DisplayName("Verify Content Manager creates vocabulary and saves directly to Database Repository")
    void testSaveVocabularyToDatabaseRepository() {
        VocabularyRepository mockVocabRepo = mock(VocabularyRepository.class);
        TopicRepository mockTopicRepo = mock(TopicRepository.class);

        // Giả lập lưu trả về entity có ID
        when(mockVocabRepo.save(any(Vocabulary.class))).thenAnswer(invocation -> {
            Vocabulary arg = invocation.getArgument(0);
            arg.setId(999L);
            return arg;
        });
        when(mockTopicRepo.existsByLevelAndName(anyInt(), anyString())).thenReturn(true);

        VocabularyService serviceWithDb = new VocabularyService(mockVocabRepo, mockTopicRepo);

        VocabularyDTO newWord = new VocabularyDTO();
        newWord.setWord("희망");
        newWord.setMeaning("Hy vọng");
        newWord.setTopikLevel(2);
        newWord.setTopic("Cảm xúc & Tâm lý");
        newWord.setWordType("Danh từ");
        newWord.setStatus("ACTIVE");

        VocabularyDTO savedDto = serviceWithDb.saveOrUpdate(newWord);

        assertNotNull(savedDto);
        assertEquals(999L, savedDto.getId());
        assertEquals("희망", savedDto.getWord());

        // Xác minh vocabularyRepository.save() ĐƯỢC GỌI để lưu vào Database!
        verify(mockVocabRepo, atLeastOnce()).save(any(Vocabulary.class));
    }

    @Test
    @DisplayName("Verify Learner gets vocabularies created by Content Manager")
    void testLearnerReceivesCreatedVocabularies() {
        VocabularyDTO newWord = new VocabularyDTO();
        newWord.setWord("꿈");
        newWord.setMeaning("Ước mơ / Giấc mơ");
        newWord.setTopikLevel(1);
        newWord.setTopic("Ước mơ & Tương lai");
        newWord.setWordType("Danh từ");
        newWord.setStatus("ACTIVE");

        vocabularyService.saveOrUpdate(newWord);

        // Người dùng (Learner) truy vấn từ vựng của chủ đề "Ước mơ & Tương lai"
        List<VocabularyDTO> learnerVocabs = vocabularyService.getVocabulariesForLearner(1, "Ước mơ & Tương lai");
        assertNotNull(learnerVocabs);
        assertFalse(learnerVocabs.isEmpty());
        assertEquals(1, learnerVocabs.size());
        assertEquals("꿈", learnerVocabs.get(0).getWord());
        assertEquals("Ước mơ / Giấc mơ", learnerVocabs.get(0).getMeaning());
    }
}
