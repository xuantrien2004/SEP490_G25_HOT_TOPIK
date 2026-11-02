package com.hottopik.service.exam;

import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đề kiểm tra trình độ: 10 câu Nghe + 10 câu Đọc, mỗi câu 10 điểm.
 * Chỉ nhận dạng một câu cho một audio hoặc một đoạn ngắn.
 */
public final class PlacementRules {

    public static final int SKILL_QUOTA = 10;
    public static final int POINT = 10;
    public static final int MAX_POINTS = SKILL_QUOTA * 2 * POINT;
    public static final int MIN_DAYS = 31;
    public static final int MAX_DAYS = 200;

    public enum Paper {
        TOPIK_I(Track.TOPIK_I, "Bài kiểm tra trình độ"),
        TOPIK_II(Track.TOPIK_II, "Bài kiểm tra trình độ");

        private final Track track;
        private final String title;

        Paper(Track track, String title) {
            this.track = track;
            this.title = title;
        }

        public Track track() {
            return track;
        }

        public String title() {
            return title;
        }

        public int questions() {
            return SKILL_QUOTA * 2;
        }

        public static Paper fromCode(String code) {
            if (code == null) throw new ExamDomainException("Chưa chọn bài kiểm tra.");
            try {
                return Paper.valueOf(code.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ExamDomainException("Bài kiểm tra không hợp lệ.");
            }
        }
    }

    /** Học viên chỉ làm một bài. Câu lấy từ các dạng một câu sơ cấp. */
    public static Paper learnerPaper() {
        return Paper.TOPIK_I;
    }

    private PlacementRules() {
    }

    public static boolean singleQuestion(QuestionTypeDef type) {
        return type != null && !type.isWriting() && !type.isGrouped();
    }

    public static List<QuestionTypeDef> allowed(Paper paper) {
        return QuestionTypeCatalog.all().stream()
                .filter(PlacementRules::singleQuestion)
                .filter(type -> type.track() == paper.track())
                .toList();
    }

    /** Cấu trúc demo: 10 Nghe + 10 Đọc, bám các dạng một câu của đề chuẩn. */
    public static Map<String, Integer> demo(Paper paper) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (paper == Paper.TOPIK_I) {
            counts.put("I-N1", 2);
            counts.put("I-N2", 2);
            counts.put("I-N3", 2);
            counts.put("I-N4", 2);
            counts.put("I-N6", 2);
            counts.put("I-D1", 2);
            counts.put("I-D2", 2);
            counts.put("I-D3", 2);
            counts.put("I-D4", 2);
            counts.put("I-D5", 2);
        } else {
            counts.put("II-N1", 2);
            counts.put("II-N2", 2);
            counts.put("II-N3", 2);
            counts.put("II-N4", 2);
            counts.put("II-N5", 2);
            counts.put("II-D1", 2);
            counts.put("II-D2", 2);
            counts.put("II-D3", 2);
            counts.put("II-D4", 2);
            counts.put("II-D5", 2);
        }
        return counts;
    }

    public static boolean covered(Map<String, Integer> plan, Map<String, Integer> available) {
        for (Map.Entry<String, Integer> entry : plan.entrySet()) {
            if (available.getOrDefault(entry.getKey(), 0) < entry.getValue()) return false;
        }
        return true;
    }

    public static void check(Paper paper, Map<String, Integer> counts) {
        Map<String, Integer> chosen = normalize(paper, counts);
        int listening = 0;
        int reading = 0;
        for (Map.Entry<String, Integer> entry : chosen.entrySet()) {
            QuestionTypeDef type = QuestionTypeCatalog.byCode(entry.getKey()).orElseThrow();
            if (type.skill() == Skill.LISTENING) listening += entry.getValue();
            else reading += entry.getValue();
        }
        if (listening != SKILL_QUOTA) {
            throw new ExamDomainException("Phần Nghe của " + paper.title() + " phải đủ đúng 10 câu. Đang chọn " + listening + " câu.");
        }
        if (reading != SKILL_QUOTA) {
            throw new ExamDomainException("Phần Đọc của " + paper.title() + " phải đủ đúng 10 câu. Đang chọn " + reading + " câu.");
        }
    }

    public static Map<String, Integer> normalize(Paper paper, Map<String, Integer> counts) {
        Map<String, Integer> chosen = new LinkedHashMap<>();
        if (counts == null) return chosen;
        for (QuestionTypeDef type : allowed(paper)) {
            Integer raw = counts.get(type.code());
            int count = raw == null ? 0 : raw;
            if (count < 0 || count > paper.questions()) {
                throw new ExamDomainException("Số câu của dạng " + type.name() + " phải từ 0 đến " + paper.questions() + ".");
            }
            if (count > 0) chosen.put(type.code(), count);
        }
        return chosen;
    }

    public static int points(int correct) {
        return Math.max(0, correct) * POINT;
    }

    public static int percent(int scorePoints) {
        return Math.max(0, scorePoints) * 100 / MAX_POINTS;
    }

    /** Dưới 80 là 0, từ 80 đến 139 là 1, từ 140 là 2. Điểm chỉ để khuyên. */
    public static int suggestedLevel(Paper paper, int scorePoints) {
        if (scorePoints >= 140) return 2;
        if (scorePoints >= 80) return 1;
        return 0;
    }

    /** Mức 0 và 1 gợi ý lộ trình TOPIK I. Mức 2 gợi ý lộ trình TOPIK II. */
    public static Track suggestedTrack(int level) {
        return level >= 2 ? Track.TOPIK_II : Track.TOPIK_I;
    }

    public static String advice(int scorePoints) {
        Track track = suggestedTrack(suggestedLevel(Paper.TOPIK_I, scorePoints));
        return "Hệ thống gợi ý lộ trình " + track.getLabel();
    }

    /** Tên ngắn để người học phân biệt cấp 1–6 khi chọn mục tiêu. */
    public static String levelName(int level) {
        return switch (level) {
            case 1 -> "Sơ cấp 1";
            case 2 -> "Sơ cấp 2";
            case 3 -> "Trung cấp 1";
            case 4 -> "Trung cấp 2";
            case 5 -> "Cao cấp 1";
            case 6 -> "Cao cấp 2";
            default -> "";
        };
    }

    public static String levelOption(int level) {
        String name = levelName(level);
        return name.isEmpty() ? "TOPIK " + level : "TOPIK " + level + " · " + name;
    }

    /** Việc người đạt cấp đó làm được, một dòng, để chọn mục tiêu. */
    public static String levelDetail(int level) {
        return switch (level) {
            case 1 -> "Chào hỏi, mua bán, giới thiệu bản thân";
            case 2 -> "Sinh hoạt hàng ngày: gọi điện, nhờ vả";
            case 3 -> "Đọc đoạn văn quen, dùng dịch vụ công";
            case 4 -> "Tin tức và chủ đề xã hội thông thường";
            case 5 -> "Công việc, nghiên cứu, chủ đề chưa quen";
            case 6 -> "Diễn đạt chính xác, gần như thành thạo";
            default -> "";
        };
    }

    public static String choiceLabel(Track track, int level) {
        if (track == suggestedTrack(level)) return "Nên chọn";
        if (level >= 2 && track == Track.TOPIK_I) return "Học lại";
        return "Vẫn chọn được";
    }

    /** Dropdown của lộ trình: TOPIK I là cấp 1–2, TOPIK II là cấp 3–6. */
    public static List<Integer> roadmapTargets(Track track) {
        return track == Track.TOPIK_I ? List.of(1, 2) : List.of(3, 4, 5, 6);
    }

    public static int defaultTarget(Track track) {
        return track == Track.TOPIK_I ? 2 : 3;
    }

    public static List<Track> choiceOrder(int level) {
        Track suggested = suggestedTrack(level);
        Track other = suggested == Track.TOPIK_I ? Track.TOPIK_II : Track.TOPIK_I;
        return List.of(suggested, other);
    }

    public record LevelChoice(int level, String option, String detail) {
    }

    public static List<LevelChoice> levelChoices() {
        List<LevelChoice> rows = new ArrayList<>();
        for (int level = 1; level <= 6; level++) {
            rows.add(new LevelChoice(level, levelOption(level), levelDetail(level)));
        }
        return rows;
    }

    public static List<LevelChoice> levelChoices(Track track) {
        return levelChoices().stream().filter(item -> roadmapTargets(track).contains(item.level())).toList();
    }

    /** Mốc gợi ý. 1 tháng lấy 31 ngày vì lịch nhận từ 31 đến 200. */
    public record DayMark(int days, String label) {
    }

    public static List<DayMark> dayMarks() {
        return List.of(
                new DayMark(31, "1 tháng"),
                new DayMark(60, "2 tháng"),
                new DayMark(90, "3 tháng"),
                new DayMark(180, "6 tháng"));
    }

    public static int defaultSpan() {
        return 90;
    }

    public static String levelLabel(int level) {
        return level <= 0 ? "Chưa đạt cấp 1" : "TOPIK " + level;
    }

    public static String scoreLine(int correct, int total, int scorePoints) {
        return "Bạn đúng " + correct + "/" + total + " câu → " + scorePoints + "/" + MAX_POINTS
                + " điểm (" + percent(scorePoints) + "%).";
    }

    /** Mục tiêu được chọn sau khi đã biết trình hiện tại, trong đúng track. */
    public static List<Integer> targets(int current, Track track) {
        int low = track == Track.TOPIK_I ? 1 : 3;
        int high = track == Track.TOPIK_I ? 2 : 6;
        int start = Math.max(low, current <= 0 ? low : current);
        List<Integer> levels = new ArrayList<>();
        for (int level = start; level <= high; level++) {
            if (level > current || (current >= high && level == high)) levels.add(level);
        }
        if (levels.isEmpty()) levels.add(high);
        return levels;
    }

    public static Track trackForTarget(int target) {
        if (target < 1 || target > 6) throw new ExamDomainException("Mục tiêu phải từ TOPIK 1 đến TOPIK 6.");
        return target <= 2 ? Track.TOPIK_I : Track.TOPIK_II;
    }
}
