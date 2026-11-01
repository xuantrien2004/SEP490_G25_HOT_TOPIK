package com.hottopik.service.exam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Công thức chung để sinh lịch học.
 * <p>
 * Số ngày đã chọn là D. Năm ngày cuối là mini test và full test, nên cửa sổ học là W = D − 5.
 * Bài mới chỉ mở ở các ngày không trùng mốc ôn của một bài mới đã chọn. Với mốc +2, +7, +14
 * thì mỗi khối 21 ngày có bảy ngày: vị trí 1, 2, 5, 6, 10, 11 và 14. Ngày đó còn phải
 * nhỏ hơn hoặc bằng D − 5 − mốc ôn xa nhất, để lần ôn cuối cùng nằm trước năm ngày thi.
 * Hệ số là số dạng chia số ngày được mở. Phần nguyên là số bài ở các ngày học mới đầu.
 * Phần dư thêm một bài vào từng ngày học mới cuối. Hết dạng thì những ngày được phép còn lại để trống.
 * Ngày không có bài mới và không có bài ôn để trống, người học tự ôn hoặc tự kiểm tra.
 * Không dời mốc ôn, không giữ ô ôn tuần, không rải mini test vào ngày trống.
 */
public final class RoadmapSketch {

    public static final int TEST_DAYS = 5;

    private static final String[] TEST_KINDS = {"MINI", "MINI", "MINI", "MINI", "FULL"};
    private static final String[] TEST_NAMES = {
            "Mini test Đọc", "Mini test Nghe", "Mini test Đọc", "Mini test Nghe", "Full test"
    };
    private static final String[] PHASE_NAMES = {"Làm quen", "Tăng tốc", "Nước rút"};

    public record TypeRef(String code, String name, String skill) {
        public TypeRef(String code, String name) {
            this(code, name, null);
        }

        public TypeRef {
            String raw = skill == null ? "" : skill.toUpperCase(Locale.ROOT);
            if (raw.contains("LIS") || "NGHE".equals(raw)) skill = "LISTENING";
            else if (raw.isBlank() && code != null && code.toUpperCase(Locale.ROOT).contains("LIS")) skill = "LISTENING";
            else skill = "READING";
        }
    }

    public record Slot(String kind, String code, String name, int repetition) {
    }

    public record DayPlan(int day, int week, String phase, List<Slot> slots) {
    }

    public record Phase(String name, int fromDay, int toDay) {
        public int days() {
            return toDay - fromDay + 1;
        }
    }

    public record Result(List<Phase> phases, List<DayPlan> days, int newCount, int reviewCount,
                         int weeklyCount, List<String> unplaced, int miniCount, int fullCount,
                         int baseQuota, int heavyNewDays) {
    }

    private RoadmapSketch() {
    }

    /** Lịch theo số ngày. Số bài mỗi ngày học mới do số dạng và các ngày được mở quyết định. */
    public static Result build(List<TypeRef> types, int totalDays, int[] reviewGaps,
                               int sessionsPerType, int[] phaseSplit) {
        return build(types, totalDays, 0, reviewGaps, sessionsPerType, phaseSplit);
    }

    /**
     * @param baseQuota giữ tham số cho lời gọi cũ. Công thức không dùng sàn này.
     */
    public static Result build(List<TypeRef> types, int totalDays, int baseQuota, int[] reviewGaps,
                               int sessionsPerType, int[] phaseSplit) {
        Prepared prepared = prepare(types, totalDays, baseQuota, reviewGaps, sessionsPerType, phaseSplit);
        return placedOrExplain(prepared, sessionsPerType);
    }

    private static Result placedOrExplain(Prepared prepared, int sessionsPerType) {
        Result result = place(prepared);
        if (result != null) return result;
        int least = search(prepared.ordered, prepared.gaps, sessionsPerType, prepared.phaseSplit);
        if (least < 0) {
            throw new ExamDomainException("Kéo đến 200 ngày vẫn chưa đủ chỗ cho " + sessionsPerType
                    + " lần học mỗi dạng.");
        }
        throw new ExamDomainException("Với " + prepared.totalDays
                + " ngày, mốc ôn chưa kịp trước năm ngày thi. Cần ít nhất " + least + " ngày.");
    }

    /** Số ngày ít nhất mà vẫn còn một ngày học mới trước hạn ôn. */
    public static int minimumDays(List<TypeRef> types, int[] reviewGaps, int sessionsPerType, int[] phaseSplit) {
        Prepared sample = prepare(types, 31, 0, reviewGaps, sessionsPerType, phaseSplit);
        int least = search(sample.ordered, sample.gaps, sessionsPerType, phaseSplit);
        if (least < 0) {
            throw new ExamDomainException("Kéo đến 200 ngày vẫn chưa đủ chỗ cho " + sessionsPerType
                    + " lần học mỗi dạng.");
        }
        return least;
    }

    /** Dạng chưa có chỗ, hoặc đã có bài nhưng chưa đủ số lần học. */
    public static List<String> incomplete(Result result, int sessions) {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> names = new java.util.LinkedHashMap<>();
        for (DayPlan day : result.days()) {
            for (Slot slot : day.slots()) {
                if (slot.code() == null) continue;
                counts.merge(slot.code(), 1, Integer::sum);
                names.putIfAbsent(slot.code(), slot.name());
            }
        }
        List<String> missing = new ArrayList<>();
        for (String name : result.unplaced()) {
            missing.add(name + " chưa có chỗ");
        }
        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() < sessions) {
                missing.add(names.get(entry.getKey()) + " mới có " + entry.getValue() + "/" + sessions + " lần");
            }
        }
        return missing;
    }

    static List<Phase> phases(int totalDays, int[] split) {
        int[] lens = new int[3];
        int assigned = 0;
        for (int i = 0; i < 3; i++) {
            lens[i] = i == 2 ? totalDays - assigned : Math.max(1, totalDays * split[i] / 100);
            assigned += lens[i];
        }
        if (lens[2] < 1) {
            int gap = 1 - lens[2];
            lens[0] -= gap;
            lens[2] = 1;
        } else if (assigned != totalDays) {
            lens[2] += totalDays - assigned;
        }
        List<Phase> phases = new ArrayList<>();
        int day = 1;
        for (int i = 0; i < 3; i++) {
            int to = day + lens[i] - 1;
            phases.add(new Phase(PHASE_NAMES[i], day, to));
            day = to + 1;
        }
        return phases;
    }

    private static Result place(Prepared prepared) {
        int studyDays = prepared.totalDays - TEST_DAYS;
        List<List<Slot>> grid = new ArrayList<>();
        for (int day = 0; day <= studyDays; day++) grid.add(new ArrayList<>());
        int horizon = horizon(prepared.gaps);
        List<Integer> legal = legalNewDays(studyDays - horizon, prepared.gaps);
        if (legal.isEmpty() && !prepared.ordered.isEmpty()) return null;
        int[] lessons = lessonCounts(prepared.ordered.size(), legal.size());
        int cursor = 0;
        for (int index = 0; index < legal.size(); index++) {
            int day = legal.get(index);
            for (int placed = 0; placed < lessons[index]; placed++) {
                TypeRef type = prepared.ordered.get(cursor++);
                grid.get(day).add(new Slot("NEW", type.code(), type.name(), 1));
                for (int review = 0; review < prepared.gaps.length; review++) {
                    int at = day + prepared.gaps[review];
                    grid.get(at).add(new Slot("REVIEW", type.code(), type.name(), review + 2));
                }
            }
        }
        if (cursor != prepared.ordered.size()) return null;

        int baseQuota = lessons.length == 0 || prepared.ordered.isEmpty() ? 1 : lessons[0];
        int heavy = 0;
        for (int count : lessons) {
            if (count == baseQuota + 1) heavy++;
        }

        List<Phase> phases = phases(prepared.totalDays, prepared.phaseSplit);
        List<DayPlan> days = new ArrayList<>();
        int news = 0;
        int reviews = 0;
        for (int day = 1; day <= studyDays; day++) {
            List<Slot> slots = grid.get(day);
            for (Slot slot : slots) {
                if ("NEW".equals(slot.kind())) news++;
                else if ("REVIEW".equals(slot.kind())) reviews++;
            }
            days.add(new DayPlan(day, weekOf(day), phaseOf(phases, day), List.copyOf(slots)));
        }
        for (int i = 0; i < TEST_DAYS; i++) {
            int day = studyDays + 1 + i;
            Slot slot = new Slot(TEST_KINDS[i], null, TEST_NAMES[i], TEST_KINDS[i].equals("FULL") ? 1 : i + 1);
            days.add(new DayPlan(day, weekOf(day), phaseOf(phases, day), List.of(slot)));
        }
        return new Result(phases, days, news, reviews, 0, List.of(), TEST_DAYS - 1, 1, baseQuota, heavy);
    }

    /** Ngày được mở bài mới: đi từ ngày 1, bỏ ngày trùng mốc ôn của một ngày đã chọn, dừng trước hạn. */
    static List<Integer> legalNewDays(int lastLegal, int[] gaps) {
        List<Integer> days = new ArrayList<>();
        if (lastLegal < 1) return days;
        boolean[] blocked = new boolean[lastLegal + 1];
        for (int day = 1; day <= lastLegal; day++) {
            if (blocked[day]) continue;
            days.add(day);
            for (int gap : gaps) {
                int at = day + gap;
                if (at >= 1 && at <= lastLegal) blocked[at] = true;
            }
        }
        return days;
    }

    /** Phần nguyên ở các ngày đầu, phần dư mỗi bài một ngày ở cuối. Hết dạng thì ngày sau để 0. */
    static int[] lessonCounts(int types, int days) {
        int[] counts = new int[days];
        if (types <= 0 || days <= 0) return counts;
        if (types <= days) {
            for (int i = 0; i < types; i++) counts[i] = 1;
            return counts;
        }
        int base = types / days;
        int extra = types % days;
        for (int i = 0; i < days; i++) counts[i] = i >= days - extra ? base + 1 : base;
        return counts;
    }

    private static int search(List<TypeRef> ordered, int[] gaps, int sessionsPerType, int[] phaseSplit) {
        for (int days = 31; days <= 200; days++) {
            Prepared prepared = new Prepared(ordered, days, gaps, phaseSplit);
            if (place(prepared) != null) return days;
        }
        return -1;
    }

    private static Prepared prepare(List<TypeRef> types, int totalDays, int baseQuota, int[] reviewGaps,
                                    int sessionsPerType, int[] phaseSplit) {
        if (totalDays < 31 || totalDays > 200) {
            throw new ExamDomainException("Lịch mẫu từ 31 đến 200 ngày.");
        }
        if (baseQuota < 0) {
            throw new ExamDomainException("Sàn mỗi ngày không được âm.");
        }
        if (sessionsPerType < 1 || sessionsPerType > 4) {
            throw new ExamDomainException("Mỗi dạng học từ 1 đến 4 lần.");
        }
        if (reviewGaps == null) {
            throw new ExamDomainException("Cần mốc ôn.");
        }
        for (int gap : reviewGaps) {
            if (gap < 1) throw new ExamDomainException("Mốc ôn phải là số ngày dương.");
        }
        if (phaseSplit == null || phaseSplit.length != 3) {
            throw new ExamDomainException("Cần đủ tỉ lệ ba chặng.");
        }
        int reviewUses = Math.min(Math.max(sessionsPerType - 1, 0), reviewGaps.length);
        int[] gaps = Arrays.copyOf(reviewGaps, reviewUses);
        List<TypeRef> ordered = interleave(types == null ? List.of() : types);
        return new Prepared(ordered, totalDays, gaps, phaseSplit);
    }

    /** Nghe rồi Đọc theo thứ tự trong từng kỹ năng. Đọc còn lại xếp tiếp khi Nghe đã hết. */
    static List<TypeRef> interleave(List<TypeRef> types) {
        List<TypeRef> listening = new ArrayList<>();
        List<TypeRef> reading = new ArrayList<>();
        for (TypeRef type : types) {
            if ("LISTENING".equals(type.skill())) listening.add(type);
            else reading.add(type);
        }
        List<TypeRef> ordered = new ArrayList<>();
        int count = Math.max(listening.size(), reading.size());
        for (int i = 0; i < count; i++) {
            if (i < listening.size()) ordered.add(listening.get(i));
            if (i < reading.size()) ordered.add(reading.get(i));
        }
        return ordered;
    }

    private static int horizon(int[] gaps) {
        int horizon = 0;
        for (int gap : gaps) horizon = Math.max(horizon, gap);
        return horizon;
    }

    private static int weekOf(int day) {
        return (day - 1) / 7 + 1;
    }

    private static String phaseOf(List<Phase> phases, int day) {
        for (Phase phase : phases) {
            if (day >= phase.fromDay() && day <= phase.toDay()) return phase.name();
        }
        return phases.get(phases.size() - 1).name();
    }

    private record Prepared(List<TypeRef> ordered, int totalDays, int[] gaps, int[] phaseSplit) {
    }
}
