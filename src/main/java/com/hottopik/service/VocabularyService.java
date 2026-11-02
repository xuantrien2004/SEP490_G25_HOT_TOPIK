package com.hottopik.service;

import com.hottopik.dto.TopicDTO;
import com.hottopik.dto.TopikLevelSummaryDTO;
import com.hottopik.dto.VocabularyDTO;
import com.hottopik.entity.Topic;
import com.hottopik.entity.Vocabulary;
import com.hottopik.repository.TopicRepository;
import com.hottopik.repository.VocabularyRepository;
import com.hottopik.util.KoreanRomanizer;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class VocabularyService {

    @Autowired(required = false)
    private VocabularyRepository vocabularyRepository;

    @Autowired(required = false)
    private TopicRepository topicRepository;

    private final Map<Long, VocabularyDTO> vocabMap = new ConcurrentHashMap<>();
    private final Map<String, TopicDTO> topicMap = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> levelStatusMap = new ConcurrentHashMap<>();
    private final AtomicLong vocabIdGen = new AtomicLong(100);
    private final AtomicLong topicIdGen = new AtomicLong(50);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public VocabularyService() {
        for (int i = 1; i <= 6; i++) {
            levelStatusMap.put(i, true);
        }
        initSampleDataInMemory();
    }

    public VocabularyService(VocabularyRepository vocabularyRepository, TopicRepository topicRepository) {
        this();
        this.vocabularyRepository = vocabularyRepository;
        this.topicRepository = topicRepository;
        initDatabaseSync();
    }

    @PostConstruct
    public void init() {
        if (vocabularyRepository != null && topicRepository != null) {
            initDatabaseSync();
        }
    }

    private String topicKey(int level, String name) {
        return level + "_" + (name != null ? name.trim().toLowerCase() : "");
    }

    /**
     * Đồng bộ dữ liệu CSDL: Nếu chưa có dữ liệu từ vựng theo chủ đề thì tự động seed vào Database.
     * Sau đó đọc toàn bộ từ CSDL vào bộ nhớ để truy vấn nhanh và đồng bộ.
     */
    @Transactional
    public synchronized void initDatabaseSync() {
        if (vocabularyRepository == null || topicRepository == null) {
            return;
        }

        try {
            long existingCount = vocabularyRepository.findAll().stream()
                    .filter(v -> v.getTopic() != null && !v.getTopic().trim().isEmpty())
                    .count();

            if (existingCount == 0) {
                // Seed sample data into Database
                for (TopicDTO t : topicMap.values()) {
                    if (!topicRepository.existsByLevelAndName(t.getTopikLevel(), t.getName())) {
                        Topic entity = Topic.builder()
                                .level(t.getTopikLevel())
                                .name(t.getName())
                                .description(t.getDescription())
                                .status(t.getStatus() != null ? t.getStatus() : "ACTIVE")
                                .build();
                        topicRepository.save(entity);
                    }
                }

                for (VocabularyDTO v : vocabMap.values()) {
                    Vocabulary entity = Vocabulary.builder()
                            .word(v.getWord())
                            .romanization(v.getRomanization())
                            .pos(v.getWordType())
                            .meaningVi(v.getMeaning())
                            .level(v.getTopikLevel() != null ? v.getTopikLevel().shortValue() : 1)
                            .topic(v.getTopic())
                            .audioUrl(v.getAudioUrl())
                            .exampleKr(v.getExampleKr())
                            .exampleVi(v.getExampleVi())
                            .status(v.getStatus() != null ? v.getStatus() : "ACTIVE")
                            .topicId(1)
                            .build();
                    vocabularyRepository.save(entity);
                }
            }

            // Load from Database into Memory Map
            reloadFromDatabase();
        } catch (Exception e) {
            System.err.println("VocabularyService DB Sync Warning: " + e.getMessage());
        }
    }

    /**
     * Tải lại toàn bộ dữ liệu từ Database vào bộ nhớ
     */
    public synchronized void reloadFromDatabase() {
        if (vocabularyRepository == null || topicRepository == null) {
            return;
        }

        vocabMap.clear();
        topicMap.clear();

        // 1. Tải topics từ DB
        List<Topic> dbTopics = topicRepository.findAll();
        for (Topic t : dbTopics) {
            TopicDTO dto = new TopicDTO(
                    t.getId(),
                    t.getName(),
                    t.getLevel(),
                    t.getDescription(),
                    0,
                    0,
                    t.getStatus() != null ? t.getStatus() : "ACTIVE"
            );
            topicMap.put(topicKey(t.getLevel(), t.getName()), dto);
            if (t.getId() != null && t.getId() > topicIdGen.get()) {
                topicIdGen.set(t.getId());
            }
        }

        // 2. Tải vocabularies từ DB
        List<Vocabulary> dbVocabs = vocabularyRepository.findAll();
        for (Vocabulary v : dbVocabs) {
            if (v.getTopic() == null || v.getTopic().trim().isEmpty()) {
                continue;
            }
            int lvl = v.getLevel() != null ? v.getLevel().intValue() : 1;
            String createdAtStr = v.getCreatedAt() != null ? v.getCreatedAt().format(FORMATTER) : LocalDateTime.now().format(FORMATTER);
            String updatedAtStr = v.getUpdatedAt() != null ? v.getUpdatedAt().format(FORMATTER) : LocalDateTime.now().format(FORMATTER);

            VocabularyDTO dto = new VocabularyDTO(
                    v.getId(),
                    v.getWord(),
                    v.getRomanization(),
                    v.getMeaningVi(),
                    v.getPos() != null ? v.getPos() : "Danh từ",
                    lvl,
                    v.getTopic(),
                    v.getAudioUrl(),
                    v.getExampleKr(),
                    v.getExampleVi(),
                    v.getStatus() != null ? v.getStatus() : "ACTIVE",
                    createdAtStr,
                    updatedAtStr
            );
            vocabMap.put(v.getId(), dto);
            if (v.getId() != null && v.getId() > vocabIdGen.get()) {
                vocabIdGen.set(v.getId());
            }

            // Đảm bảo topic tồn tại trong topicMap
            String key = topicKey(lvl, v.getTopic());
            if (!topicMap.containsKey(key)) {
                TopicDTO newTopicDto = new TopicDTO(
                        topicIdGen.incrementAndGet(),
                        v.getTopic(),
                        lvl,
                        "Chủ đề " + v.getTopic(),
                        0,
                        0,
                        "ACTIVE"
                );
                topicMap.put(key, newTopicDto);
            }
        }
    }

    private void initSampleDataInMemory() {
        String now = LocalDateTime.now().minusDays(3).format(FORMATTER);
        String updated = LocalDateTime.now().minusHours(2).format(FORMATTER);

        // TOPIK 1
        registerTopicInMemory(1, "Gia đình", "Từ vựng về các thành viên và quan hệ gia đình");
        addSampleInMemory(1L, "가족", "ga-jok", "Gia đình", "Danh từ", 1, "Gia đình", null,
                "우리 가족은 모두 네 명입니다.", "Gia đình tôi có tất cả 4 người.", "ACTIVE", now, updated);
        addSampleInMemory(2L, "부모님", "bu-mo-nim", "Bố mẹ / Phụ huynh", "Danh từ", 1, "Gia đình", null,
                "주말에 부모님 댁을 방문했습니다.", "Cuối tuần tôi đã về thăm nhà bố mẹ.", "ACTIVE", now, updated);
        addSampleInMemory(3L, "어머니", "eo-meo-ni", "Mẹ", "Danh từ", 1, "Gia đình", null,
                "어머니께서 맛있는 음식을 만들어 주셨어요.", "Mẹ đã nấu cho tôi những món ăn rất ngon.", "ACTIVE", now, updated);
        addSampleInMemory(4L, "아버지", "a-beo-ji", "Bố", "Danh từ", 1, "Gia đình", null,
                "아버지는 회사에 다니십니다.", "Bố tôi đang đi làm ở công ty.", "ACTIVE", now, updated);
        addSampleInMemory(5L, "동생", "dong-saeng", "Em (trai / gái)", "Danh từ", 1, "Gia đình", null,
                "제 동생은 지금 고등학생입니다.", "Em của tôi hiện đang là học sinh cấp ba.", "ACTIVE", now, updated);

        registerTopicInMemory(1, "Trường học & Giáo dục", "Từ vựng về trường lớp, học tập và đồ dùng học sinh");
        addSampleInMemory(6L, "학교", "hak-gyo", "Trường học", "Danh từ", 1, "Trường học & Giáo dục", null,
                "저는 매일 아침 8시에 학교에 갑니다.", "Tôi đến trường vào 8 giờ mỗi sáng.", "ACTIVE", now, updated);
        addSampleInMemory(7L, "선생님", "seon-saeng-nim", "Thầy cô giáo", "Danh từ", 1, "Trường học & Giáo dục", null,
                "선생님, 질문이 하나 있습니다.", "Thưa thầy cô, em có một câu hỏi ạ.", "ACTIVE", now, updated);
        addSampleInMemory(8L, "공부하다", "gong-bu-ha-da", "Học tập", "Động từ", 1, "Trường học & Giáo dục", null,
                "도서관에서 한국어를 열심히 공부합니다.", "Tôi chăm chỉ học tiếng Hàn ở thư viện.", "ACTIVE", now, updated);
        addSampleInMemory(9L, "책상", "chaek-sang", "Bàn học", "Danh từ", 1, "Trường học & Giáo dục", null,
                "책상 위에 한국어 책과 공책이 있어요.", "Trên bàn học có sách và vở tiếng Hàn.", "ACTIVE", now, updated);

        registerTopicInMemory(1, "Ẩm thực & Mua sắm", "Từ vựng về đồ ăn, thức uống, giá cả và đi chợ");
        addSampleInMemory(10L, "사과", "sa-gwa", "Quả táo / Lời xin lỗi", "Danh từ", 1, "Ẩm thực & Mua sắm", null,
                "시장에서 신선한 사과를 샀어요.", "Tôi đã mua những quả táo tươi ở chợ.", "ACTIVE", now, updated);
        addSampleInMemory(11L, "물", "mul", "Nước", "Danh từ", 1, "Ẩm thực & Mua sắm", null,
                "식당에서 시원한 물을 마셨습니다.", "Tôi đã uống nước mát ở nhà hàng.", "ACTIVE", now, updated);
        addSampleInMemory(12L, "밥", "bap", "Cơm / Bữa ăn", "Danh từ", 1, "Ẩm thực & Mua sắm", null,
                "점심에 친구와 비빔밥을 먹었어요.", "Bữa trưa tôi đã ăn cơm trộn bibimbap cùng bạn.", "ACTIVE", now, updated);
        addSampleInMemory(13L, "시장", "si-jang", "Chợ", "Danh từ", 1, "Ẩm thực & Mua sắm", null,
                "주말에 동대문 시장에 구경하러 갔어요.", "Cuối tuần tôi đã đến chợ Dongdaemun để dạo ngắm.", "ACTIVE", now, updated);

        registerTopicInMemory(1, "Đời sống hàng ngày", "Các hoạt động sinh hoạt, bạn bè và thời gian");
        addSampleInMemory(14L, "친구", "chin-gu", "Bạn bè", "Danh từ", 1, "Đời sống hàng ngày", null,
                "주말에 오랜만에 친구를 만났습니다.", "Cuối tuần tôi đã gặp bạn cũ sau thời gian dài.", "ACTIVE", now, updated);
        addSampleInMemory(15L, "일어나다", "il-eo-na-da", "Thức dậy", "Động từ", 1, "Đời sống hàng ngày", null,
                "매일 아침 7시에 일어납니다.", "Mỗi sáng tôi thức dậy lúc 7 giờ.", "ACTIVE", now, updated);
        addSampleInMemory(16L, "보다", "bo-da", "Xem / Nhìn", "Động từ", 1, "Đời sống hàng ngày", null,
                "저녁에 가족과 함께 텔레비전을 봅니다.", "Buổi tối tôi xem tivi cùng gia đình.", "ACTIVE", now, updated);

        registerTopicInMemory(1, "Thời tiết & Mùa", "Mùa trong năm, mưa, nắng và nhiệt độ");
        addSampleInMemory(17L, "날씨", "nal-ssi", "Thời tiết", "Danh từ", 1, "Thời tiết & Mùa", null,
                "오늘 날씨가 정말 맑고 좋아요.", "Hôm nay thời tiết thật trong xanh và đẹp.", "ACTIVE", now, updated);
        addSampleInMemory(18L, "비", "bi", "Mưa", "Danh từ", 1, "Thời tiết & Mùa", null,
                "밖에 비가 많이 내리고 있어요.", "Bên ngoài trời đang mưa rất to.", "ACTIVE", now, updated);
        addSampleInMemory(19L, "봄", "bom", "Mùa xuân", "Danh từ", 1, "Thời tiết & Mùa", null,
                "봄에는 꽃이 예쁘게 핍니다.", "Vào mùa xuân hoa nở rất đẹp.", "ACTIVE", now, updated);

        registerTopicInMemory(1, "Giao thông cơ bản", "Phương tiện đi lại, bến tàu, xe buýt");
        addSampleInMemory(20L, "버스", "beo-seu", "Xe buýt", "Danh từ", 1, "Giao thông cơ bản", null,
                "학교까지 버스를 타고 다닙니다.", "Tôi đi xe buýt đến trường học.", "ACTIVE", now, updated);
        addSampleInMemory(21L, "타다", "ta-da", "Đi / Lên (xe, tàu)", "Động từ", 1, "Giao thông cơ bản", null,
                "정류장에서 100번 버스를 타세요.", "Xin hãy lên xe buýt số 100 tại trạm dừng.", "ACTIVE", now, updated);
        addSampleInMemory(22L, "길", "gil", "Con đường / Đường xá", "Danh từ", 1, "Giao thông cơ bản", null,
                "길을 건널 때는 신호등을 잘 보세요.", "Khi qua đường hãy chú ý nhìn đèn tín hiệu.", "ACTIVE", now, updated);

        // TOPIK 2
        registerTopicInMemory(2, "Sức khỏe & Y tế", "Bệnh viện, thuốc men và triệu chứng");
        addSampleInMemory(23L, "병원", "byeong-won", "Bệnh viện", "Danh từ", 2, "Sức khỏe & Y tế", null,
                "감기가 심해서 병원에 가서 진료를 받았습니다.", "Bị cảm nặng nên tôi đã đến bệnh viện khám.", "ACTIVE", now, updated);
        addSampleInMemory(24L, "약국", "yak-guk", "Hiệu thuốc", "Danh từ", 2, "Sức khỏe & Y tế", null,
                "약국에서 감기약을 사서 먹었어요.", "Tôi đã mua thuốc cảm ở hiệu thuốc và uống.", "ACTIVE", now, updated);

        registerTopicInMemory(2, "Văn hóa & Sở thích", "Xem phim, âm nhạc, du lịch và giải trí");
        addSampleInMemory(25L, "취미", "chwi-mi", "Sở thích", "Danh từ", 2, "Văn hóa & Sở thích", null,
                "제 취미는 주말마다 사진을 찍는 것입니다.", "Sở thích của tôi là chụp ảnh vào mỗi cuối tuần.", "ACTIVE", now, updated);
        addSampleInMemory(26L, "영화관", "yeong-hwa-gwan", "Rạp chiếu phim", "Danh từ", 2, "Văn hóa & Sở thích", null,
                "친구와 함께 영화관에서 한국 영화를 보았습니다.", "Tôi đã xem phim Hàn Quốc cùng bạn ở rạp.", "ACTIVE", now, updated);

        // TOPIK 3
        registerTopicInMemory(3, "Môi trường & Tự nhiên", "Bảo vệ môi trường, rác thải và khí hậu");
        addSampleInMemory(29L, "환경", "hwan-gyeong", "Môi trường", "Danh từ", 3, "Môi trường & Tự nhiên", null,
                "환경을 보호하기 위해 일회용품 사용을 줄여야 합니다.", "Để bảo vệ môi trường cần giảm đồ dùng 1 lần.", "ACTIVE", now, updated);
        addSampleInMemory(30L, "온실효과", "on-sil-hyo-gwa", "Hiệu ứng nhà kính", "Danh từ", 3, "Môi trường & Tự nhiên", null,
                "온실효과로 인해 지구의 평균 기온이 계속 상승하고 있다.", "Do hiệu ứng nhà kính nhiệt độ Trái Đất liên tục tăng.", "ACTIVE", now, updated);
        addSampleInMemory(31L, "재활용", "jae-hwal-yong", "Tái chế rác thải", "Danh từ", 3, "Môi trường & Tự nhiên", null,
                "분리수거를 철저히 하여 재활용률을 높여야 합니다.", "Cần phân loại rác kỹ để nâng cao tỉ lệ tái chế.", "ACTIVE", now, updated);

        // TOPIK 4
        registerTopicInMemory(4, "Kinh tế & Xã hội", "Thương mại, thị trường, tiêu dùng");
        addSampleInMemory(33L, "경제", "gyeong-je", "Kinh tế", "Danh từ", 4, "Kinh tế & Xã hội", null,
                "세계 경제 침체로 인해 수출이 다소 감소했습니다.", "Suy thoái kinh tế khiến xuất khẩu giảm sút.", "ACTIVE", now, updated);
        addSampleInMemory(34L, "갈등", "gal-deung", "Mâu thuẫn / Xung đột", "Danh từ", 4, "Kinh tế & Xã hội", null,
                "대화를 통해 세대 간의 갈등을 해결해야 합니다.", "Cần giải quyết mâu thuẫn thế hệ qua đối thoại.", "ACTIVE", now, updated);

        // TOPIK 5
        registerTopicInMemory(5, "Khoa học & Xã hội", "Hiện tượng xã hội, công nghệ hiện đại");
        addSampleInMemory(37L, "현상", "hyeon-sang", "Hiện tượng", "Danh từ", 5, "Khoa học & Xã hội", null,
                "저출산 현상은 국가적 위기로 인식되고 있다.", "Hiện tượng sinh thấp được xem là khủng hoảng quốc gia.", "ACTIVE", now, updated);

        // TOPIK 6
        registerTopicInMemory(6, "Triết học & Tư tưởng", "Tư duy, hệ hình, nhân sinh quan");
        addSampleInMemory(40L, "패러다임", "pae-reo-da-im", "Hệ hình tư duy (Paradigm)", "Danh từ", 6, "Triết học & Tư tưởng", null,
                "생성형 AI 기술의 혁신은 새로운 교육 패러다임을 요구한다.", "Sự đổi mới của AI đòi hỏi hệ hình tư duy mới.", "ACTIVE", now, updated);
    }

    private void registerTopicInMemory(int level, String name, String desc) {
        String key = topicKey(level, name);
        if (!topicMap.containsKey(key)) {
            TopicDTO t = new TopicDTO(topicIdGen.incrementAndGet(), name, level, desc, 0, 0, "ACTIVE");
            topicMap.put(key, t);
        }
    }

    private void addSampleInMemory(Long id, String word, String romanization, String meaning, String wordType,
                                   Integer level, String topic, String audioUrl, String exampleKr,
                                   String exampleVi, String status, String createdAt, String updatedAt) {
        if (romanization == null || romanization.trim().isEmpty()) {
            romanization = KoreanRomanizer.romanize(word);
        }
        VocabularyDTO dto = new VocabularyDTO(id, word, romanization, meaning, wordType, level, topic,
                audioUrl, exampleKr, exampleVi, status, createdAt, updatedAt);
        vocabMap.put(id, dto);
        registerTopicInMemory(level, topic, "Chủ đề " + topic);
    }

    // ==========================================
    // TOPIK LEVEL SUMMARY
    // ==========================================
    public List<TopikLevelSummaryDTO> getTopikLevelSummaries() {
        List<TopikLevelSummaryDTO> list = new ArrayList<>();
        String[] titles = {
                "Sơ cấp 1 (Sơ khai)",
                "Sơ cấp 2 (Giao tiếp cơ bản)",
                "Trung cấp 1 (Sinh hoạt & Xã hội)",
                "Trung cấp 2 (Công việc & Chuyên môn)",
                "Cao cấp 1 (Học thuật & Tranh luận)",
                "Cao cấp 2 (Lưu loát như người bản xứ)"
        };

        for (int lvl = 1; lvl <= 6; lvl++) {
            final int l = lvl;
            List<TopicDTO> topics = getTopicsByLevel(l);
            long totalWords = vocabMap.values().stream().filter(v -> v.getTopikLevel() != null && v.getTopikLevel() == l).count();
            long activeWords = vocabMap.values().stream().filter(v -> v.getTopikLevel() != null && v.getTopikLevel() == l && "ACTIVE".equalsIgnoreCase(v.getStatus())).count();
            String tier = (l <= 2) ? "TOPIK I" : "TOPIK II";
            boolean isLevelActive = levelStatusMap.getOrDefault(l, true);

            list.add(new TopikLevelSummaryDTO(
                    l,
                    tier + " · Cấp " + l,
                    tier,
                    titles[l - 1],
                    topics.size(),
                    (int) totalWords,
                    (int) activeWords,
                    isLevelActive ? "ACTIVE" : "INACTIVE"
            ));
        }
        return list;
    }

    // ==========================================
    // TOPIC MANAGEMENT
    // ==========================================
    public List<TopicDTO> getTopicsByLevel(int level) {
        List<TopicDTO> result = new ArrayList<>();
        for (TopicDTO t : topicMap.values()) {
            if (t.getTopikLevel() != null && t.getTopikLevel() == level) {
                long count = vocabMap.values().stream()
                        .filter(v -> v.getTopikLevel() != null && v.getTopikLevel() == level && t.getName().equalsIgnoreCase(v.getTopic()))
                        .count();
                long activeCount = vocabMap.values().stream()
                        .filter(v -> v.getTopikLevel() != null && v.getTopikLevel() == level && t.getName().equalsIgnoreCase(v.getTopic()) && "ACTIVE".equalsIgnoreCase(v.getStatus()))
                        .count();
                t.setWordCount((int) count);
                t.setActiveCount((int) activeCount);
                result.add(t);
            }
        }
        result.sort(Comparator.comparing(TopicDTO::getName));
        return result;
    }

    public TopicDTO getTopic(int level, String name) {
        TopicDTO t = topicMap.get(topicKey(level, name));
        if (t != null) {
            long count = vocabMap.values().stream()
                    .filter(v -> v.getTopikLevel() != null && v.getTopikLevel() == level && t.getName().equalsIgnoreCase(v.getTopic()))
                    .count();
            t.setWordCount((int) count);
        }
        return t;
    }

    @Transactional
    public TopicDTO createTopic(int level, String name, String description) {
        String cleanName = name.trim();
        String key = topicKey(level, cleanName);
        if (topicMap.containsKey(key)) {
            throw new IllegalArgumentException("Chủ đề \"" + cleanName + "\" đã tồn tại trong TOPIK " + level + "!");
        }

        TopicDTO t = new TopicDTO(topicIdGen.incrementAndGet(), cleanName, level, description, 0, 0, "ACTIVE");
        topicMap.put(key, t);

        // Lưu vào Database
        if (topicRepository != null) {
            Topic entity = Topic.builder()
                    .level(level)
                    .name(cleanName)
                    .description(description)
                    .status("ACTIVE")
                    .build();
            Topic saved = topicRepository.save(entity);
            t.setId(saved.getId());
        }

        return t;
    }

    @Transactional
    public TopicDTO updateTopic(int level, String oldName, String newName, String description) {
        String oldKey = topicKey(level, oldName);
        TopicDTO existing = topicMap.get(oldKey);
        if (existing == null) {
            throw new IllegalArgumentException("Không tìm thấy chủ đề \"" + oldName + "\" trong TOPIK " + level);
        }

        String cleanNew = newName.trim();
        String newKey = topicKey(level, cleanNew);
        if (!oldKey.equals(newKey) && topicMap.containsKey(newKey)) {
            throw new IllegalArgumentException("Chủ đề \"" + cleanNew + "\" đã tồn tại trong TOPIK " + level + "!");
        }

        topicMap.remove(oldKey);
        existing.setName(cleanNew);
        if (description != null) existing.setDescription(description);
        topicMap.put(newKey, existing);

        // Cập nhật CSDL
        if (topicRepository != null) {
            topicRepository.findByLevelAndName(level, oldName).ifPresent(entity -> {
                entity.setName(cleanNew);
                if (description != null) entity.setDescription(description);
                topicRepository.save(entity);
            });
        }

        if (vocabularyRepository != null) {
            vocabularyRepository.updateTopicName((short) level, oldName, cleanNew);
        }

        // Cập nhật các từ vựng trong bộ nhớ
        for (VocabularyDTO v : vocabMap.values()) {
            if (v.getTopikLevel() != null && v.getTopikLevel() == level && oldName.equalsIgnoreCase(v.getTopic())) {
                v.setTopic(cleanNew);
                v.setUpdatedAt(LocalDateTime.now().format(FORMATTER));
            }
        }

        return existing;
    }

    @Transactional
    public int deleteTopic(int level, String name) {
        String key = topicKey(level, name);
        topicMap.remove(key);

        // Xóa trong CSDL
        if (topicRepository != null) {
            topicRepository.findByLevelAndName(level, name).ifPresent(topicRepository::delete);
        }

        int deletedInDb = 0;
        if (vocabularyRepository != null) {
            deletedInDb = vocabularyRepository.deleteByLevelAndTopic((short) level, name);
        }

        List<Long> toRemove = new ArrayList<>();
        for (VocabularyDTO v : vocabMap.values()) {
            if (v.getTopikLevel() != null && v.getTopikLevel() == level && name.equalsIgnoreCase(v.getTopic())) {
                toRemove.add(v.getId());
            }
        }

        for (Long id : toRemove) {
            vocabMap.remove(id);
        }

        return Math.max(deletedInDb, toRemove.size());
    }

    public int getWordCountInTopic(int level, String name) {
        return (int) vocabMap.values().stream()
                .filter(v -> v.getTopikLevel() != null && v.getTopikLevel() == level && name.equalsIgnoreCase(v.getTopic()))
                .count();
    }

    // ==========================================
    // VOCABULARY CRUD & FILTERS
    // ==========================================
    public List<VocabularyDTO> getAll() {
        return vocabMap.values().stream()
                .sorted(Comparator.comparing(VocabularyDTO::getId).reversed())
                .collect(Collectors.toList());
    }

    public VocabularyDTO getById(Long id) {
        return vocabMap.get(id);
    }

    public List<VocabularyDTO> getByLevelAndTopic(int level, String topic) {
        return vocabMap.values().stream()
                .filter(v -> v.getTopikLevel() != null && v.getTopikLevel() == level && topic.equalsIgnoreCase(v.getTopic()))
                .sorted(Comparator.comparing(VocabularyDTO::getId).reversed())
                .collect(Collectors.toList());
    }

    public List<VocabularyDTO> searchAndFilter(String query, Integer level, String topic, String wordType, String status) {
        return vocabMap.values().stream()
                .filter(v -> {
                    if (query != null && !query.trim().isEmpty()) {
                        String q = query.trim().toLowerCase();
                        boolean matchWord = v.getWord() != null && v.getWord().toLowerCase().contains(q);
                        boolean matchRom = v.getRomanization() != null && v.getRomanization().toLowerCase().contains(q);
                        boolean matchMean = v.getMeaning() != null && v.getMeaning().toLowerCase().contains(q);
                        if (!matchWord && !matchRom && !matchMean) {
                            return false;
                        }
                    }
                    if (level != null && level > 0) {
                        if (v.getTopikLevel() == null || !v.getTopikLevel().equals(level)) {
                            return false;
                        }
                    }
                    if (topic != null && !topic.trim().isEmpty() && !topic.equalsIgnoreCase("ALL")) {
                        if (v.getTopic() == null || !v.getTopic().equalsIgnoreCase(topic.trim())) {
                            return false;
                        }
                    }
                    if (wordType != null && !wordType.trim().isEmpty() && !wordType.equalsIgnoreCase("ALL")) {
                        if (v.getWordType() == null || !v.getWordType().equalsIgnoreCase(wordType.trim())) {
                            return false;
                        }
                    }
                    if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
                        if (v.getStatus() == null || !v.getStatus().equalsIgnoreCase(status.trim())) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted(Comparator.comparing(VocabularyDTO::getId).reversed())
                .collect(Collectors.toList());
    }

    /**
     * CONTENT MANAGER TẠO HOẶC SỬA TỪ VỰNG:
     * Dữ liệu được chuyển trực tiếp vào Database!
     */
    @Transactional
    public VocabularyDTO saveOrUpdate(VocabularyDTO dto) {
        String now = LocalDateTime.now().format(FORMATTER);
        if (dto.getRomanization() == null || dto.getRomanization().trim().isEmpty()) {
            dto.setRomanization(KoreanRomanizer.romanize(dto.getWord()));
        }

        // Đảm bảo Topic tồn tại
        if (dto.getTopikLevel() != null && dto.getTopic() != null && !dto.getTopic().trim().isEmpty()) {
            String topicName = dto.getTopic().trim();
            registerTopicInMemory(dto.getTopikLevel(), topicName, "Chủ đề " + topicName);
            if (topicRepository != null && !topicRepository.existsByLevelAndName(dto.getTopikLevel(), topicName)) {
                Topic newTopic = Topic.builder()
                        .level(dto.getTopikLevel())
                        .name(topicName)
                        .description("Chủ đề " + topicName)
                        .status("ACTIVE")
                        .build();
                topicRepository.save(newTopic);
            }
        }

        Vocabulary entity = null;
        if (dto.getId() != null && dto.getId() > 0 && vocabularyRepository != null) {
            entity = vocabularyRepository.findById(dto.getId()).orElse(null);
        }

        if (entity == null) {
            // Thêm mới vào Database
            entity = Vocabulary.builder()
                    .word(dto.getWord())
                    .romanization(dto.getRomanization())
                    .pos(dto.getWordType())
                    .meaningVi(dto.getMeaning())
                    .level(dto.getTopikLevel() != null ? dto.getTopikLevel().shortValue() : 1)
                    .topic(dto.getTopic() != null ? dto.getTopic().trim() : "")
                    .audioUrl(dto.getAudioUrl())
                    .exampleKr(dto.getExampleKr())
                    .exampleVi(dto.getExampleVi())
                    .status(dto.getStatus() != null && !dto.getStatus().trim().isEmpty() ? dto.getStatus().trim().toUpperCase() : "ACTIVE")
                    .topicId(1)
                    .build();

            if (vocabularyRepository != null) {
                entity = vocabularyRepository.save(entity);
                dto.setId(entity.getId());
            } else {
                dto.setId(vocabIdGen.incrementAndGet());
            }

            dto.setCreatedAt(now);
            dto.setUpdatedAt(now);
            dto.setTopikLevelName(VocabularyDTO.formatTopikName(dto.getTopikLevel()));
            vocabMap.put(dto.getId(), dto);
            return dto;
        } else {
            // Cập nhật từ vựng trong Database
            entity.setWord(dto.getWord());
            entity.setRomanization(dto.getRomanization());
            entity.setPos(dto.getWordType());
            entity.setMeaningVi(dto.getMeaning());
            if (dto.getTopikLevel() != null) {
                entity.setLevel(dto.getTopikLevel().shortValue());
            }
            if (dto.getTopic() != null) {
                entity.setTopic(dto.getTopic().trim());
            }
            entity.setAudioUrl(dto.getAudioUrl());
            entity.setExampleKr(dto.getExampleKr());
            entity.setExampleVi(dto.getExampleVi());
            if (dto.getStatus() != null && !dto.getStatus().trim().isEmpty()) {
                entity.setStatus(dto.getStatus().trim().toUpperCase());
            }

            if (vocabularyRepository != null) {
                entity = vocabularyRepository.save(entity);
            }

            VocabularyDTO existing = vocabMap.get(dto.getId());
            if (existing != null) {
                existing.setWord(dto.getWord());
                existing.setRomanization(dto.getRomanization());
                existing.setMeaning(dto.getMeaning());
                existing.setWordType(dto.getWordType());
                existing.setTopikLevel(dto.getTopikLevel());
                existing.setTopic(dto.getTopic());
                existing.setAudioUrl(dto.getAudioUrl());
                existing.setExampleKr(dto.getExampleKr());
                existing.setExampleVi(dto.getExampleVi());
                if (dto.getStatus() != null && !dto.getStatus().trim().isEmpty()) {
                    existing.setStatus(dto.getStatus());
                }
                existing.setUpdatedAt(now);
                return existing;
            } else {
                dto.setUpdatedAt(now);
                vocabMap.put(dto.getId(), dto);
                return dto;
            }
        }
    }

    @Transactional
    public boolean toggleWordStatus(Long id) {
        VocabularyDTO v = vocabMap.get(id);
        if (v != null) {
            String newStatus = "ACTIVE".equalsIgnoreCase(v.getStatus()) ? "INACTIVE" : "ACTIVE";
            v.setStatus(newStatus);
            v.setUpdatedAt(LocalDateTime.now().format(FORMATTER));

            if (vocabularyRepository != null) {
                vocabularyRepository.findById(id).ifPresent(entity -> {
                    entity.setStatus(newStatus);
                    vocabularyRepository.save(entity);
                });
            }
            return true;
        }
        return false;
    }

    @Transactional
    public int toggleTopicStatus(String topic, boolean active) {
        if (topic == null || topic.trim().isEmpty()) return 0;
        int count = 0;
        for (int l = 1; l <= 6; l++) {
            count += toggleTopicStatus(l, topic, active);
        }
        return count;
    }

    @Transactional
    public int toggleTopicStatus(int level, String topic, boolean active) {
        if (topic == null || topic.trim().isEmpty()) return 0;
        String newStatus = active ? "ACTIVE" : "INACTIVE";
        String now = LocalDateTime.now().format(FORMATTER);

        TopicDTO t = topicMap.get(topicKey(level, topic));
        if (t != null) {
            t.setStatus(newStatus);
        }

        if (topicRepository != null) {
            topicRepository.findByLevelAndName(level, topic).ifPresent(entity -> {
                entity.setStatus(newStatus);
                topicRepository.save(entity);
            });
        }

        if (vocabularyRepository != null) {
            vocabularyRepository.updateStatusByLevelAndTopic((short) level, topic, newStatus);
        }

        int count = 0;
        for (VocabularyDTO v : vocabMap.values()) {
            if (v.getTopikLevel() != null && v.getTopikLevel() == level && topic.equalsIgnoreCase(v.getTopic())) {
                v.setStatus(newStatus);
                v.setUpdatedAt(now);
                count++;
            }
        }
        return count;
    }

    @Transactional
    public int toggleTopikLevelStatus(int level, boolean active) {
        String newStatus = active ? "ACTIVE" : "INACTIVE";
        levelStatusMap.put(level, active);
        String now = LocalDateTime.now().format(FORMATTER);

        for (TopicDTO t : topicMap.values()) {
            if (t.getTopikLevel() != null && t.getTopikLevel() == level) {
                t.setStatus(newStatus);
            }
        }

        if (vocabularyRepository != null) {
            vocabularyRepository.updateStatusByLevel((short) level, newStatus);
        }

        int count = 0;
        for (VocabularyDTO v : vocabMap.values()) {
            if (v.getTopikLevel() != null && v.getTopikLevel() == level) {
                v.setStatus(newStatus);
                v.setUpdatedAt(now);
                count++;
            }
        }
        return count;
    }

    /**
     * Nhập hàng loạt từ vựng (Bulk import Excel):
     * Lưu toàn bộ danh sách vào Database!
     */
    @Transactional
    public int bulkCreate(List<VocabularyDTO> list) {
        if (list == null || list.isEmpty()) return 0;
        int count = 0;
        String now = LocalDateTime.now().format(FORMATTER);

        List<Vocabulary> entitiesToSave = new ArrayList<>();

        for (VocabularyDTO item : list) {
            if (item.getWord() == null || item.getWord().trim().isEmpty()) {
                continue;
            }
            if (item.getRomanization() == null || item.getRomanization().trim().isEmpty()) {
                item.setRomanization(KoreanRomanizer.romanize(item.getWord()));
            }

            int lvl = item.getTopikLevel() != null ? item.getTopikLevel() : 1;
            String status = (item.getStatus() != null && !item.getStatus().trim().isEmpty()) ? item.getStatus() : "ACTIVE";
            String topicName = item.getTopic() != null ? item.getTopic().trim() : "Chung";

            // Đảm bảo topic tồn tại
            registerTopicInMemory(lvl, topicName, "Chủ đề " + topicName);
            if (topicRepository != null && !topicRepository.existsByLevelAndName(lvl, topicName)) {
                Topic newTopic = Topic.builder()
                        .level(lvl)
                        .name(topicName)
                        .description("Chủ đề " + topicName)
                        .status("ACTIVE")
                        .build();
                topicRepository.save(newTopic);
            }

            Vocabulary entity = Vocabulary.builder()
                    .word(item.getWord().trim())
                    .romanization(item.getRomanization().trim())
                    .pos(item.getWordType() != null ? item.getWordType().trim() : "Danh từ")
                    .meaningVi(item.getMeaning() != null ? item.getMeaning().trim() : "")
                    .level((short) lvl)
                    .topic(topicName)
                    .audioUrl(item.getAudioUrl())
                    .exampleKr(item.getExampleKr())
                    .exampleVi(item.getExampleVi())
                    .status(status)
                    .topicId(1)
                    .build();

            entitiesToSave.add(entity);
            count++;
        }

        if (vocabularyRepository != null && !entitiesToSave.isEmpty()) {
            List<Vocabulary> saved = vocabularyRepository.saveAll(entitiesToSave);
            for (int i = 0; i < saved.size(); i++) {
                Vocabulary s = saved.get(i);
                VocabularyDTO d = list.get(i);
                d.setId(s.getId());
                d.setCreatedAt(now);
                d.setUpdatedAt(now);
                d.setStatus(s.getStatus());
                d.setTopikLevelName(VocabularyDTO.formatTopikName(d.getTopikLevel()));
                vocabMap.put(s.getId(), d);
            }
        } else {
            for (VocabularyDTO item : list) {
                long newId = vocabIdGen.incrementAndGet();
                item.setId(newId);
                item.setCreatedAt(now);
                item.setUpdatedAt(now);
                item.setTopikLevelName(VocabularyDTO.formatTopikName(item.getTopikLevel()));
                vocabMap.put(newId, item);
            }
        }

        return count;
    }

    public List<String> getAllTopics() {
        return vocabMap.values().stream()
                .map(VocabularyDTO::getTopic)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<String> getAllWordTypes() {
        return Arrays.asList("Danh từ", "Động từ", "Tính từ", "Phó từ", "Cụm từ", "Liên từ", "Đại từ");
    }

    public long getTotalCount() {
        return vocabMap.size();
    }

    public long getActiveCount() {
        return vocabMap.values().stream().filter(v -> "ACTIVE".equalsIgnoreCase(v.getStatus())).count();
    }

    public long getInactiveCount() {
        return vocabMap.values().stream().filter(v -> !"ACTIVE".equalsIgnoreCase(v.getStatus())).count();
    }

    public int getTotalTopicsCount() {
        return topicMap.size();
    }

    // ==========================================
    // CÁC HÀM PHỤC VỤ NGƯỜI DÙNG XEM TỪ VỰNG
    // ==========================================

    /**
     * Lấy danh sách từ vựng ACTIVE của chủ đề và cấp độ để người dùng học
     */
    public List<VocabularyDTO> getVocabulariesForLearner(Integer level, String topic) {
        return vocabMap.values().stream()
                .filter(v -> "ACTIVE".equalsIgnoreCase(v.getStatus()))
                .filter(v -> level == null || (v.getTopikLevel() != null && v.getTopikLevel().equals(level)))
                .filter(v -> topic == null || topic.trim().isEmpty() || "ALL".equalsIgnoreCase(topic) || (v.getTopic() != null && v.getTopic().equalsIgnoreCase(topic.trim())))
                .sorted(Comparator.comparing(VocabularyDTO::getId))
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách chủ đề theo cấp độ kèm số từ active cho người dùng
     */
    public List<TopicDTO> getTopicsForLearner(Integer level) {
        return getTopicsByLevel(level != null ? level : 1);
    }

    /**
     * Lấy tất cả chủ đề gom nhóm theo từng cấp TOPIK (1 đến 6) cho trang Lý thuyết
     */
    public Map<Integer, List<TopicDTO>> getAllTopicsGroupedByLevel() {
        Map<Integer, List<TopicDTO>> map = new HashMap<>();
        for (int l = 1; l <= 6; l++) {
            map.put(l, getTopicsByLevel(l));
        }
        return map;
    }
}
