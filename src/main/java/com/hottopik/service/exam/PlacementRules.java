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
    public static final int TOPIK_II_OFFER = 160;
    public static final int MIN_DAYS = 31;
    public static final int MAX_DAYS = 200;
    public static final int DEFAULT_DAYS = 60;

    public enum Paper {
        TOPIK_I(Track.TOPIK_I, "Bài TOPIK I"),
        TOPIK_II(Track.TOPIK_II, "Bài TOPIK II");

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

    public static int suggestedLevel(Paper paper, int scorePoints) {
        if (paper == Paper.TOPIK_I) {
            if (scorePoints >= 140) return 2;
            if (scorePoints >= 80) return 1;
            return 0;
        }
        if (scorePoints >= 160) return 6;
        if (scorePoints >= 130) return 5;
        if (scorePoints >= 100) return 4;
        if (scorePoints >= 80) return 3;
        return 0;
    }

    public static boolean offerTopikII(Paper paper, int scorePoints) {
        return paper == Paper.TOPIK_I && scorePoints >= TOPIK_II_OFFER;
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
