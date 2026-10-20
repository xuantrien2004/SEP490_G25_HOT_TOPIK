package com.hottopik.service.exam;

import java.util.ArrayList;
import java.util.List;

/**
 * Lịch mẫu để Content Manager xem trước thứ tự đã xếp.
 * Mỗi dạng có một bài mới, rồi ôn vào các mốc (mặc định +2, +7, +14 ngày).
 * Ngày chia hết cho 7 dành một ô cho ôn tuần.
 */
public final class RoadmapSketch {

    public record TypeRef(String code, String name) {
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
                         int weeklyCount, List<String> unplaced) {
    }

    private static final String[] PHASE_NAMES = {"Làm quen", "Tăng tốc", "Nước rút"};

    private RoadmapSketch() {
    }

    public static Result build(List<TypeRef> types, int totalDays, int itemsPerDay, int[] reviewGaps,
                               int sessionsPerType, int[] phaseSplit) {
        if (totalDays < 31 || totalDays > 200) {
            throw new ExamDomainException("Lịch mẫu từ 31 đến 200 ngày.");
        }
        if (itemsPerDay < 1 || itemsPerDay > 4) {
            throw new ExamDomainException("Mỗi ngày từ 1 đến 4 bài.");
        }
        if (sessionsPerType < 1 || sessionsPerType > 4) {
            throw new ExamDomainException("Mỗi dạng học từ 1 đến 4 lần.");
        }
        if (phaseSplit == null || phaseSplit.length != 3) {
            throw new ExamDomainException("Cần đủ tỉ lệ ba chặng.");
        }

        List<Phase> phases = phases(totalDays, phaseSplit);
        boolean[][] taken = new boolean[totalDays + 1][itemsPerDay];
        Slot[][] grid = new Slot[totalDays + 1][itemsPerDay];
        int weekly = 0;
        for (int day = 7; day <= totalDays; day += 7) {
            int slot = itemsPerDay - 1;
            taken[day][slot] = true;
            grid[day][slot] = new Slot("WEEKLY", null, "Ôn tuần", 0);
            weekly++;
        }

        int news = 0;
        int reviews = 0;
        List<String> unplaced = new ArrayList<>();
        int reviewUses = Math.min(Math.max(sessionsPerType - 1, 0), reviewGaps.length);
        for (TypeRef type : types) {
            int[] at = firstFree(taken, 1, totalDays, itemsPerDay);
            if (at == null) {
                unplaced.add(type.name());
                continue;
            }
            grid[at[0]][at[1]] = new Slot("NEW", type.code(), type.name(), 1);
            taken[at[0]][at[1]] = true;
            news++;
            for (int r = 0; r < reviewUses; r++) {
                int target = at[0] + reviewGaps[r];
                if (target > totalDays) continue;
                int[] rev = firstFree(taken, target, totalDays, itemsPerDay);
                if (rev == null) continue;
                grid[rev[0]][rev[1]] = new Slot("REVIEW", type.code(), type.name(), r + 2);
                taken[rev[0]][rev[1]] = true;
                reviews++;
            }
        }

        List<DayPlan> days = new ArrayList<>();
        for (int day = 1; day <= totalDays; day++) {
            List<Slot> slots = new ArrayList<>();
            for (int s = 0; s < itemsPerDay; s++) {
                if (grid[day][s] != null) slots.add(grid[day][s]);
            }
            days.add(new DayPlan(day, (day - 1) / 7 + 1, phaseOf(phases, day), slots));
        }
        return new Result(phases, days, news, reviews, weekly, List.copyOf(unplaced));
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

    private static String phaseOf(List<Phase> phases, int day) {
        for (Phase phase : phases) {
            if (day >= phase.fromDay() && day <= phase.toDay()) return phase.name();
        }
        return phases.get(phases.size() - 1).name();
    }

    private static int[] firstFree(boolean[][] taken, int from, int totalDays, int itemsPerDay) {
        for (int day = from; day <= totalDays; day++) {
            for (int slot = 0; slot < itemsPerDay; slot++) {
                if (!taken[day][slot]) return new int[]{day, slot};
            }
        }
        return null;
    }
}
