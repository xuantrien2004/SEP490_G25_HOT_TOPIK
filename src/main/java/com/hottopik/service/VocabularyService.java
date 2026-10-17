package com.hottopik.service;

import com.hottopik.dto.TopicDTO;
import com.hottopik.dto.TopikLevelSummaryDTO;
import com.hottopik.dto.VocabularyDTO;
import com.hottopik.util.KoreanRomanizer;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class VocabularyService {

    private final Map<Long, VocabularyDTO> vocabMap = new ConcurrentHashMap<>();
    private final Map<String, TopicDTO> topicMap = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> levelStatusMap = new ConcurrentHashMap<>();
    private final AtomicLong vocabIdGen = new AtomicLong(100);
    private final AtomicLong topicIdGen = new AtomicLong(50);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public VocabularyService() {
        initSampleData();
    }

    private String topicKey(int level, String name) {
        return level + "_" + (name != null ? name.trim().toLowerCase() : "");
    }

    private void initSampleData() {
        for (int i = 1; i <= 6; i++) {
            levelStatusMap.put(i, true);
        }

        String now = LocalDateTime.now().minusDays(3).format(FORMATTER);
        String updated = LocalDateTime.now().minusHours(2).format(FORMATTER);

        // ==========================================
        // TOPIK 1: 6 CHỦ ĐỀ CHUẨN
        // ==========================================
        registerTopic(1, "Gia đình", "Từ vựng về các thành viên và quan hệ gia đình");
        addSample(1L, "가족", "ga-jok", "Gia đình", "Danh từ", 1, "Gia đình", null,
                "우리 가족은 모두 네 명입니다.", "Gia đình tôi có tất cả 4 người.", "ACTIVE", now, updated);
        addSample(2L, "부모님", "bu-mo-nim", "Bố mẹ / Phụ huynh", "Danh từ", 1, "Gia đình", null,
                "주말에 부모님 댁을 방문했습니다.", "Cuối tuần tôi đã về thăm nhà bố mẹ.", "ACTIVE", now, updated);
        addSample(3L, "어머니", "eo-meo-ni", "Mẹ", "Danh từ", 1, "Gia đình", null,
                "어머니께서 맛있는 음식을 만들어 주셨어요.", "Mẹ đã nấu cho tôi những món ăn rất ngon.", "ACTIVE", now, updated);
        addSample(4L, "아버지", "a-beo-ji", "Bố", "Danh từ", 1, "Gia đình", null,
                "아버지는 회사에 다니십니다.", "Bố tôi đang đi làm ở công ty.", "ACTIVE", now, updated);
        addSample(5L, "동생", "dong-saeng", "Em (trai / gái)", "Danh từ", 1, "Gia đình", null,
                "제 동생은 지금 고등학생입니다.", "Em của tôi hiện đang là học sinh cấp ba.", "ACTIVE", now, updated);

        registerTopic(1, "Trường học & Giáo dục", "Từ vựng về trường lớp, học tập và đồ dùng học sinh");
        addSample(6L, "학교", "hak-gyo", "Trường học", "Danh từ", 1, "Trường học & Giáo dục", null,
                "저는 매일 아침 8시에 학교에 갑니다.", "Tôi đến trường vào 8 giờ mỗi sáng.", "ACTIVE", now, updated);
        addSample(7L, "선생님", "seon-saeng-nim", "Thầy cô giáo", "Danh từ", 1, "Trường học & Giáo dục", null,
                "선생님, 질문이 하나 있습니다.", "Thưa thầy cô, em có một câu hỏi ạ.", "ACTIVE", now, updated);
        addSample(8L, "공부하다", "gong-bu-ha-da", "Học tập", "Động từ", 1, "Trường học & Giáo dục", null,
                "도서관에서 한국어를 열심히 공부합니다.", "Tôi chăm chỉ học tiếng Hàn ở thư viện.", "ACTIVE", now, updated);
        addSample(9L, "책상", "chaek-sang", "Bàn học", "Danh từ", 1, "Trường học & Giáo dục", null,
                "책상 위에 한국어 책과 공책이 있어요.", "Trên bàn học có sách và vở tiếng Hàn.", "ACTIVE", now, updated);

        registerTopic(1, "Ẩm thực & Mua sắm", "Từ vựng về đồ ăn, thức uống, giá cả và đi chợ");
        addSample(10L, "사과", "sa-gwa", "Quả táo / Lời xin lỗi", "Danh từ", 1, "Ẩm thực & Mua sắm", null,
                "시장에서 신선한 사과를 샀어요.", "Tôi đã mua những quả táo tươi ở chợ.", "ACTIVE", now, updated);
        addSample(11L, "물", "mul", "Nước", "Danh từ", 1, "Ẩm thực & Mua sắm", null,
                "식당에서 시원한 물을 마셨습니다.", "Tôi đã uống nước mát ở nhà hàng.", "ACTIVE", now, updated);
        addSample(12L, "밥", "bap", "Cơm / Bữa ăn", "Danh từ", 1, "Ẩm thực & Mua sắm", null,
                "점심에 친구와 비빔밥을 먹었어요.", "Bữa trưa tôi đã ăn cơm trộn bibimbap cùng bạn.", "ACTIVE", now, updated);
        addSample(13L, "시장", "si-jang", "Chợ", "Danh từ", 1, "Ẩm thực & Mua sắm", null,
                "주말에 동대문 시장에 구경하러 갔어요.", "Cuối tuần tôi đã đến chợ Dongdaemun để dạo ngắm.", "ACTIVE", now, updated);

        registerTopic(1, "Đời sống hàng ngày", "Các hoạt động sinh hoạt, bạn bè và thời gian");
        addSample(14L, "친구", "chin-gu", "Bạn bè", "Danh từ", 1, "Đời sống hàng ngày", null,
                "주말에 오랜만에 친구를 만났습니다.", "Cuối tuần tôi đã gặp bạn cũ sau thời gian dài.", "ACTIVE", now, updated);
        addSample(15L, "일어나다", "il-eo-na-da", "Thức dậy", "Động từ", 1, "Đời sống hàng ngày", null,
                "매일 아침 7시에 일어납니다.", "Mỗi sáng tôi thức dậy lúc 7 giờ.", "ACTIVE", now, updated);
        addSample(16L, "보다", "bo-da", "Xem / Nhìn", "Động từ", 1, "Đời sống hàng ngày", null,
                "저녁에 가족과 함께 텔레비전을 봅니다.", "Buổi tối tôi xem tivi cùng gia đình.", "ACTIVE", now, updated);

        registerTopic(1, "Thời tiết & Mùa", "Mùa trong năm, mưa, nắng và nhiệt độ");
        addSample(17L, "날씨", "nal-ssi", "Thời tiết", "Danh từ", 1, "Thời tiết & Mùa", null,
                "오늘 날씨가 정말 맑고 좋아요.", "Hôm nay thời tiết thật trong xanh và đẹp.", "ACTIVE", now, updated);
        addSample(18L, "비", "bi", "Mưa", "Danh từ", 1, "Thời tiết & Mùa", null,
                "밖에 비가 많이 내리고 있어요.", "Bên ngoài trời đang mưa rất to.", "ACTIVE", now, updated);
        addSample(19L, "봄", "bom", "Mùa xuân", "Danh từ", 1, "Thời tiết & Mùa", null,
                "봄에는 꽃이 예쁘게 핍니다.", "Vào mùa xuân hoa nở rất đẹp.", "ACTIVE", now, updated);

        registerTopic(1, "Giao thông cơ bản", "Phương tiện đi lại, bến tàu, xe buýt");
        addSample(20L, "버스", "beo-seu", "Xe buýt", "Danh từ", 1, "Giao thông cơ bản", null,
                "학교까지 버스를 타고 다닙니다.", "Tôi đi xe buýt đến trường học.", "ACTIVE", now, updated);
        addSample(21L, "타다", "ta-da", "Đi / Lên (xe, tàu)", "Động từ", 1, "Giao thông cơ bản", null,
                "정류장에서 100번 버스를 타세요.", "Xin hãy lên xe buýt số 100 tại trạm dừng.", "ACTIVE", now, updated);
        addSample(22L, "길", "gil", "Con đường / Đường xá", "Danh từ", 1, "Giao thông cơ bản", null,
                "길을 건널 때는 신호등을 잘 보세요.", "Khi qua đường hãy chú ý nhìn đèn tín hiệu.", "ACTIVE", now, updated);

        // ==========================================
        // TOPIK 2: 4 CHỦ ĐỀ
        // ==========================================
        registerTopic(2, "Sức khỏe & Y tế", "Bệnh viện, thuốc men và triệu chứng");
        addSample(23L, "병원", "byeong-won", "Bệnh viện", "Danh từ", 2, "Sức khỏe & Y tế", null,
                "감기가 심해서 병원에 가서 진료를 받았습니다.", "Bị cảm nặng nên tôi đã đến bệnh viện khám.", "ACTIVE", now, updated);
        addSample(24L, "약국", "yak-guk", "Hiệu thuốc", "Danh từ", 2, "Sức khỏe & Y tế", null,
                "약국에서 감기약을 사서 먹었어요.", "Tôi đã mua thuốc cảm ở hiệu thuốc và uống.", "ACTIVE", now, updated);

        registerTopic(2, "Văn hóa & Sở thích", "Xem phim, âm nhạc, du lịch và giải trí");
        addSample(25L, "취미", "chwi-mi", "Sở thích", "Danh từ", 2, "Văn hóa & Sở thích", null,
                "제 취미는 주말마다 사진을 찍는 것입니다.", "Sở thích của tôi là chụp ảnh vào mỗi cuối tuần.", "ACTIVE", now, updated);
        addSample(26L, "영화관", "yeong-hwa-gwan", "Rạp chiếu phim", "Danh từ", 2, "Văn hóa & Sở thích", null,
                "친구와 함께 영화관에서 한국 영화를 보았습니다.", "Tôi đã xem phim Hàn Quốc cùng bạn ở rạp.", "ACTIVE", now, updated);

        registerTopic(2, "Giao thông & Đi lại", "Tàu điện ngầm, vé tàu và lộ trình");
        addSample(27L, "지하철", "ji-ha-cheol", "Tàu điện ngầm", "Danh từ", 2, "Giao thông & Đi lại", null,
                "출퇴근 시간에는 지하철을 타는 것이 빠릅니다.", "Vào giờ đi làm thì đi tàu điện ngầm nhanh hơn.", "ACTIVE", now, updated);

        registerTopic(2, "Đời sống & Hẹn hò", "Lời hứa, cuộc hẹn và giao tiếp");
        addSample(28L, "약속", "yak-sok", "Cuộc hẹn / Lời hứa", "Danh từ", 2, "Đời sống & Hẹn hò", null,
                "친구와 6시에 영화관 앞에서 만나기로 약속했어요.", "Tôi đã hẹn gặp bạn lúc 6 giờ.", "ACTIVE", now, updated);

        // ==========================================
        // TOPIK 3: 4 CHỦ ĐỀ
        // ==========================================
        registerTopic(3, "Môi trường & Tự nhiên", "Bảo vệ môi trường, rác thải và khí hậu");
        addSample(29L, "환경", "hwan-gyeong", "Môi trường", "Danh từ", 3, "Môi trường & Tự nhiên", null,
                "환경을 보호하기 위해 일회용품 사용을 줄여야 합니다.", "Để bảo vệ môi trường cần giảm đồ dùng 1 lần.", "ACTIVE", now, updated);

        registerTopic(3, "Công việc & Trải nghiệm", "Kinh nghiệm làm việc, kỹ năng");
        addSample(30L, "경험", "gyeong-heom", "Kinh nghiệm / Trải nghiệm", "Danh từ", 3, "Công việc & Trải nghiệm", null,
                "다양한 사회 경험을 쌓는 것이 중요합니다.", "Tích lũy nhiều trải nghiệm đa dạng là rất quan trọng.", "ACTIVE", now, updated);

        registerTopic(3, "Trường học & Thủ tục", "Đăng ký học bổng, thi cử");
        addSample(31L, "신청하다", "sin-cheong-ha-da", "Đăng ký / Nộp đơn", "Động từ", 3, "Trường học & Thủ tục", null,
                "장학금을 지원하기 위해 온라인으로 신청서를 제출했습니다.", "Tôi đã nộp đơn đăng ký học bổng trực tuyến.", "ACTIVE", now, updated);

        registerTopic(3, "Giao thông đô thị", "Tắc đường, quy tắc an toàn giao thông");
        addSample(32L, "교통", "gyo-tong", "Giao thông", "Danh từ", 3, "Giao thông đô thị", null,
                "비가 오면 도로 교통이 평소보다 훨씬 혼잡해집니다.", "Trời mưa thì giao thông ùn tắc hơn nhiều.", "ACTIVE", now, updated);

        // ==========================================
        // TOPIK 4: 3 CHỦ ĐỀ
        // ==========================================
        registerTopic(4, "Kinh tế & Xã hội", "Thương mại, thị trường, tiêu dùng");
        addSample(33L, "경제", "gyeong-je", "Kinh tế", "Danh từ", 4, "Kinh tế & Xã hội", null,
                "세계 경제 침체로 인해 수출이 다소 감소했습니다.", "Suy thoái kinh tế khiến xuất khẩu giảm sút.", "ACTIVE", now, updated);
        addSample(34L, "갈등", "gal-deung", "Mâu thuẫn / Xung đột", "Danh từ", 4, "Kinh tế & Xã hội", null,
                "대화를 통해 세대 간의 갈등을 해결해야 합니다.", "Cần giải quyết mâu thuẫn thế hệ qua đối thoại.", "ACTIVE", now, updated);

        registerTopic(4, "Học thuật & Công việc", "Báo cáo khoa học, thuyết trình");
        addSample(35L, "발표하다", "bal-pyo-ha-da", "Phát biểu / Công bố", "Động từ", 4, "Học thuật & Công việc", null,
                "연구팀은 신약 개발 결과를 학회에서 발표했다.", "Nhóm nghiên cứu công bố kết quả tại hội nghị.", "ACTIVE", now, updated);

        registerTopic(4, "Quan hệ cộng đồng", "Giao tiếp công sở, văn hóa công ty");
        addSample(36L, "협력", "hyeop-ryeok", "Hợp tác / Chung sức", "Danh từ", 4, "Quan hệ cộng đồng", null,
                "동료 간의 긴밀한 협력이 프로젝트 성공의 열쇠이다.", "Sự hợp tác chặt chẽ là chìa khóa thành công.", "ACTIVE", now, updated);

        // ==========================================
        // TOPIK 5: 3 CHỦ ĐỀ
        // ==========================================
        registerTopic(5, "Khoa học & Xã hội", "Hiện tượng xã hội, công nghệ hiện đại");
        addSample(37L, "현상", "hyeon-sang", "Hiện tượng", "Danh từ", 5, "Khoa học & Xã hội", null,
                "저출산 현상은 국가적 위기로 인식되고 있다.", "Hiện tượng sinh thấp được xem là khủng hoảng quốc gia.", "ACTIVE", now, updated);

        registerTopic(5, "Phát triển bền vững", "Năng lượng tái tạo, bảo tồn sinh thái");
        addSample(38L, "지속가능성", "ji-sok-ga-neung-seong", "Tính bền vững", "Danh từ", 5, "Phát triển bền vững", null,
                "친환경 에너지 개발은 지구의 지속가능성을 위한 필수 과제이다.", "Phát triển năng lượng sạch là nhiệm vụ tất yếu.", "ACTIVE", now, updated);

        registerTopic(5, "Chính sách & Pháp luật", "Luật lao động, quyền công dân");
        addSample(39L, "제도", "je-do", "Chế độ / Thể chế", "Danh từ", 5, "Chính sách & Pháp luật", null,
                "새로운 복지 제도가 도입되어 많은 취약계층이 혜택을 받았다.", "Thể chế phúc lợi mới đã giúp đỡ nhiều người.", "ACTIVE", now, updated);

        // ==========================================
        // TOPIK 6: 3 CHỦ ĐỀ
        // ==========================================
        registerTopic(6, "Triết học & Tư tưởng", "Tư duy, hệ hình, nhân sinh quan");
        addSample(40L, "패러다임", "pae-reo-da-im", "Hệ hình tư duy (Paradigm)", "Danh từ", 6, "Triết học & Tư tưởng", null,
                "생성형 AI 기술의 혁신은 새로운 교육 패러다임을 요구한다.", "Sự đổi mới của AI đòi hỏi hệ hình tư duy mới.", "ACTIVE", now, updated);

        registerTopic(6, "Dân số học & Toàn cầu", "Già hóa dân số, biến đổi nhân khẩu");
        addSample(41L, "고령화", "go-ryeong-hwa", "Già hóa dân số", "Danh từ", 6, "Dân số học & Toàn cầu", null,
                "초고령화 사회에 진입함에 따라 복지 예산 확충이 시급하다.", "Xã hội siêu già hóa đòi hỏi mở rộng ngân sách phúc lợi.", "ACTIVE", now, updated);

        registerTopic(6, "Văn hóa nghệ thuật chuyên sâu", "Bảo tồn di sản, mỹ học truyền thống");
        addSample(42L, "정체성", "jeong-che-seong", "Bản sắc / Tính đồng nhất (Identity)", "Danh từ", 6, "Văn hóa nghệ thuật chuyên sâu", null,
                "전통 예술의 계승은 민족 정체성을 확립하는 중요한 근간이다.", "Kế thừa nghệ thuật truyền thống là gốc rễ của bản sắc.", "ACTIVE", now, updated);
    }

    private void registerTopic(int level, String name, String desc) {
        String key = topicKey(level, name);
        if (!topicMap.containsKey(key)) {
            TopicDTO t = new TopicDTO(topicIdGen.incrementAndGet(), name, level, desc, 0, 0, "ACTIVE");
            topicMap.put(key, t);
        }
    }

    private void addSample(Long id, String word, String romanization, String meaning, String wordType,
                           Integer level, String topic, String audioUrl, String exampleKr,
                           String exampleVi, String status, String createdAt, String updatedAt) {
        if (romanization == null || romanization.trim().isEmpty()) {
            romanization = KoreanRomanizer.romanize(word);
        }
        VocabularyDTO dto = new VocabularyDTO(id, word, romanization, meaning, wordType, level, topic,
                audioUrl, exampleKr, exampleVi, status, createdAt, updatedAt);
        vocabMap.put(id, dto);
        registerTopic(level, topic, "Chủ đề " + topic);
    }

    // ==========================================
    // TOPIK LEVEL SUMMARY (MÀN HÌNH 1: TOPIK 1 - 6)
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
    // TOPIC MANAGEMENT (MÀN HÌNH 2: CHỦ ĐỀ THEO CẤP)
    // ==========================================
    public List<TopicDTO> getTopicsByLevel(int level) {
        List<TopicDTO> result = new ArrayList<>();
        for (TopicDTO t : topicMap.values()) {
            if (t.getTopikLevel() != null && t.getTopikLevel() == level) {
                // calculate word counts
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

    public TopicDTO createTopic(int level, String name, String description) {
        String cleanName = name.trim();
        String key = topicKey(level, cleanName);
        if (topicMap.containsKey(key)) {
            throw new IllegalArgumentException("Chủ đề \"" + cleanName + "\" đã tồn tại trong TOPIK " + level + "!");
        }
        TopicDTO t = new TopicDTO(topicIdGen.incrementAndGet(), cleanName, level, description, 0, 0, "ACTIVE");
        topicMap.put(key, t);
        return t;
    }

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

        // Update all vocabularies belonging to oldName
        for (VocabularyDTO v : vocabMap.values()) {
            if (v.getTopikLevel() != null && v.getTopikLevel() == level && oldName.equalsIgnoreCase(v.getTopic())) {
                v.setTopic(cleanNew);
                v.setUpdatedAt(LocalDateTime.now().format(FORMATTER));
            }
        }

        return existing;
    }

    /**
     * Delete topic and delete ALL vocabularies in that topic.
     * Returns the count of deleted vocabularies.
     */
    public int deleteTopic(int level, String name) {
        String key = topicKey(level, name);
        topicMap.remove(key);

        List<Long> toRemove = new ArrayList<>();
        for (VocabularyDTO v : vocabMap.values()) {
            if (v.getTopikLevel() != null && v.getTopikLevel() == level && name.equalsIgnoreCase(v.getTopic())) {
                toRemove.add(v.getId());
            }
        }

        for (Long id : toRemove) {
            vocabMap.remove(id);
        }

        return toRemove.size();
    }

    public int getWordCountInTopic(int level, String name) {
        return (int) vocabMap.values().stream()
                .filter(v -> v.getTopikLevel() != null && v.getTopikLevel() == level && name.equalsIgnoreCase(v.getTopic()))
                .count();
    }

    // ==========================================
    // VOCABULARY CRUD & FILTERS (MÀN HÌNH 3)
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

    public VocabularyDTO saveOrUpdate(VocabularyDTO dto) {
        String now = LocalDateTime.now().format(FORMATTER);
        if (dto.getRomanization() == null || dto.getRomanization().trim().isEmpty()) {
            dto.setRomanization(KoreanRomanizer.romanize(dto.getWord()));
        }

        // auto register topic if not exists
        if (dto.getTopikLevel() != null && dto.getTopic() != null && !dto.getTopic().trim().isEmpty()) {
            registerTopic(dto.getTopikLevel(), dto.getTopic().trim(), "Chủ đề " + dto.getTopic().trim());
        }

        if (dto.getId() == null || dto.getId() <= 0) {
            long newId = vocabIdGen.incrementAndGet();
            dto.setId(newId);
            dto.setCreatedAt(now);
            dto.setUpdatedAt(now);
            if (dto.getStatus() == null || dto.getStatus().trim().isEmpty()) {
                dto.setStatus("ACTIVE");
            }
            dto.setTopikLevelName(VocabularyDTO.formatTopikName(dto.getTopikLevel()));
            vocabMap.put(newId, dto);
            return dto;
        } else {
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
                dto.setCreatedAt(now);
                dto.setUpdatedAt(now);
                dto.setTopikLevelName(VocabularyDTO.formatTopikName(dto.getTopikLevel()));
                vocabMap.put(dto.getId(), dto);
                return dto;
            }
        }
    }

    public boolean toggleWordStatus(Long id) {
        VocabularyDTO v = vocabMap.get(id);
        if (v != null) {
            String newStatus = "ACTIVE".equalsIgnoreCase(v.getStatus()) ? "INACTIVE" : "ACTIVE";
            v.setStatus(newStatus);
            v.setUpdatedAt(LocalDateTime.now().format(FORMATTER));
            return true;
        }
        return false;
    }

    public int toggleTopicStatus(String topic, boolean active) {
        if (topic == null || topic.trim().isEmpty()) return 0;
        int count = 0;
        for (int l = 1; l <= 6; l++) {
            count += toggleTopicStatus(l, topic, active);
        }
        return count;
    }

    public int toggleTopicStatus(int level, String topic, boolean active) {
        if (topic == null || topic.trim().isEmpty()) return 0;
        String newStatus = active ? "ACTIVE" : "INACTIVE";
        String now = LocalDateTime.now().format(FORMATTER);

        TopicDTO t = topicMap.get(topicKey(level, topic));
        if (t != null) {
            t.setStatus(newStatus);
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

    public int toggleTopikLevelStatus(int level, boolean active) {
        String newStatus = active ? "ACTIVE" : "INACTIVE";
        levelStatusMap.put(level, active);
        String now = LocalDateTime.now().format(FORMATTER);

        // toggle all topics of this level
        for (TopicDTO t : topicMap.values()) {
            if (t.getTopikLevel() != null && t.getTopikLevel() == level) {
                t.setStatus(newStatus);
            }
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

    public int bulkCreate(List<VocabularyDTO> list) {
        if (list == null || list.isEmpty()) return 0;
        int count = 0;
        String now = LocalDateTime.now().format(FORMATTER);
        for (VocabularyDTO item : list) {
            if (item.getWord() == null || item.getWord().trim().isEmpty()) {
                continue;
            }
            if (item.getRomanization() == null || item.getRomanization().trim().isEmpty()) {
                item.setRomanization(KoreanRomanizer.romanize(item.getWord()));
            }
            long newId = vocabIdGen.incrementAndGet();
            item.setId(newId);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            if (item.getStatus() == null || item.getStatus().trim().isEmpty()) {
                item.setStatus("ACTIVE");
            }
            item.setTopikLevelName(VocabularyDTO.formatTopikName(item.getTopikLevel()));
            vocabMap.put(newId, item);

            // Register topic
            if (item.getTopikLevel() != null && item.getTopic() != null && !item.getTopic().trim().isEmpty()) {
                registerTopic(item.getTopikLevel(), item.getTopic().trim(), "Chủ đề " + item.getTopic().trim());
            }

            count++;
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
}
