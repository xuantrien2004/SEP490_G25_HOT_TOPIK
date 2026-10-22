package com.hottopik.service.exam;

import com.hottopik.entity.exam.ProgramVersion;
import com.hottopik.entity.exam.QuestionTemplate;
import com.hottopik.entity.exam.StudyPlan;
import com.hottopik.entity.exam.Track;
import com.hottopik.repository.exam.QuestionTemplateRepository;
import com.hottopik.repository.exam.StudyPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Lịch học của người học, dựng từ bản lộ trình đang phát hành tại lúc tạo. */
@Service
public class LearnerRoadmapService {

    private static final int MIN_LESSONS = 2;
    private static final int MAX_LESSONS = 4;

    private static final Set<Integer> MINUTES = Set.of(10, 15, 20, 30, 45, 60);
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");

    private final StudyPlanRepository plans;
    private final ProgramRoadmapService programs;
    private final QuestionTemplateRepository templates;

    public LearnerRoadmapService(StudyPlanRepository plans, ProgramRoadmapService programs,
                                 QuestionTemplateRepository templates) {
        this.plans = plans;
        this.programs = programs;
        this.templates = templates;
    }

    public StudyPlan find(String owner) {
        return plans.findByOwnerEmail(owner).orElse(null);
    }

    /** Tạo lịch 60 ngày nếu học viên chưa có, để Hôm nay và Lộ trình mở được ngay. */
    @Transactional
    public StudyPlan ensure(String owner, Track track) {
        StudyPlan existing = find(owner);
        if (existing != null) return existing;
        return open(owner, track, 60, 20);
    }

    public ProgramVersion published() {
        return programs.published();
    }

    public RoadmapSketch.Result preview(Track track, int days) {
        ProgramVersion program = requirePublished();
        int safeDays = normalizeDays(days);
        return programs.preview(program, track, safeDays, lessonsFor(program, track, safeDays));
    }

    @Transactional
    public StudyPlan open(String owner, Track track, int days, int minutes) {
        if (owner == null || owner.isBlank()) {
            throw new ExamDomainException("Chưa xác định được tài khoản học viên.");
        }
        if (!MINUTES.contains(minutes)) {
            throw new ExamDomainException("Chọn 10, 15, 20, 30, 45 hoặc 60 phút mỗi ngày.");
        }
        ProgramVersion program = requirePublished();
        int safeDays = normalizeDays(days);
        int perDay = lessonsFor(program, track, safeDays);

        StudyPlan plan = plans.findByOwnerEmail(owner).orElseGet(StudyPlan::new);
        plan.setOwnerEmail(owner);
        plan.setProgramId(program.getId());
        plan.setVersionCode(program.getVersionCode());
        plan.setTrack(track);
        plan.setTotalDays(safeDays);
        plan.setItemsPerDay(perDay);
        plan.setMinutesPerDay(minutes);
        plan.setStartDate(LocalDate.now());
        plan.setDoneKeys(null);
        return plans.save(plan);
    }

    /**
     * Người học đổi số ngày, phút hoặc cấp TOPIK.
     * Lịch dựng lại theo bản Content Manager đang phát hành. Bài đã làm mà vẫn còn trên lịch mới được giữ.
     */
    @Transactional
    public StudyPlan reshape(String owner, Track track, int days, int minutes) {
        if (!MINUTES.contains(minutes)) {
            throw new ExamDomainException("Chọn 10, 15, 20, 30, 45 hoặc 60 phút mỗi ngày.");
        }
        StudyPlan plan = plans.findByOwnerEmail(owner)
                .orElseThrow(() -> new ExamDomainException("Bạn chưa có lộ trình để điều chỉnh."));
        ProgramVersion program = requirePublished();
        int safeDays = normalizeDays(days);
        int perDay = lessonsFor(program, track, safeDays);
        String kept = remapDone(plan, program, track, safeDays, perDay);
        plan.setProgramId(program.getId());
        plan.setVersionCode(program.getVersionCode());
        plan.setTrack(track);
        plan.setTotalDays(safeDays);
        plan.setItemsPerDay(perDay);
        plan.setMinutesPerDay(minutes);
        plan.setDoneKeys(kept);
        return plans.save(plan);
    }

    /** Ít ngày nhất khi một ngày nhận tối đa 4 bài. */
    public int minimumDays(Track track) {
        ProgramVersion program = requirePublished();
        int sessions = program.getSessionsPerType();
        int low = 31;
        int high = 200;
        int best = -1;
        while (low <= high) {
            int mid = (low + high) / 2;
            RoadmapSketch.Result preview = programs.preview(program, track, mid, MAX_LESSONS);
            if (RoadmapSketch.incomplete(preview, sessions).isEmpty()) {
                best = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        if (best < 0) {
            throw new ExamDomainException("Kéo đến 200 ngày vẫn chưa đủ chỗ cho " + sessions + " lần học mỗi dạng.");
        }
        return best;
    }

    /**
     * Số bài mỗi ngày không phải lựa chọn riêng.
     * Lộ trình dài giữ 2 bài. Rút ngắn số ngày thì tăng lên 3 hoặc 4 để vẫn đủ lần học mỗi dạng.
     */
    int lessonsFor(ProgramVersion program, Track track, int days) {
        int sessions = program.getSessionsPerType();
        for (int perDay = MIN_LESSONS; perDay <= MAX_LESSONS; perDay++) {
            RoadmapSketch.Result preview = programs.preview(program, track, days, perDay);
            if (RoadmapSketch.incomplete(preview, sessions).isEmpty()) return perDay;
        }
        int least = minimumDays(track);
        throw new ExamDomainException("Với " + days + " ngày, lịch chưa bao hết dạng "
                + track.getLabel() + " và chưa đủ " + sessions
                + " lần học mỗi dạng. Cần ít nhất " + least + " ngày.");
    }

    public List<String> typeOrder(Track track) {
        ProgramVersion program = requirePublished();
        List<String> names = new ArrayList<>();
        for (var item : programs.itemsOf(program.getId())) {
            if (item.getTemplate().getTrack() == track && item.getTemplate().getSkill() != com.hottopik.entity.exam.Skill.WRITING) {
                names.add(item.getTemplate().getName());
            }
        }
        return names;
    }

    public Map<String, Object> view(StudyPlan plan) {
        ProgramVersion program = programOf(plan);
        RoadmapSketch.Result schedule = programs.preview(program, plan.getTrack(), plan.getTotalDays(), plan.getItemsPerDay());
        Map<String, QuestionTemplate> byCode = new LinkedHashMap<>();
        for (QuestionTemplate template : templates.findAll()) {
            byCode.put(template.getCode(), template);
        }

        List<Map<String, Object>> phases = new ArrayList<>();
        for (RoadmapSketch.Phase phase : schedule.phases()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", phase.name());
            row.put("from", phase.fromDay());
            row.put("to", phase.toDay());
            row.put("days", phase.days());
            phases.add(row);
        }

        List<Map<String, Object>> days = new ArrayList<>();
        for (RoadmapSketch.DayPlan day : schedule.days()) {
            List<Map<String, Object>> slots = new ArrayList<>();
            for (RoadmapSketch.Slot slot : day.slots()) {
                slots.add(slotView(slot, day.week(), program, byCode));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", day.day());
            row.put("week", day.week());
            row.put("phase", day.phase());
            row.put("date", plan.getStartDate().plusDays(day.day() - 1L).format(DAY_LABEL));
            row.put("slots", slots);
            days.add(row);
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("track", plan.getTrack().getLabel());
        view.put("version", plan.getVersionCode());
        view.put("minutes", plan.getMinutesPerDay());
        view.put("itemsPerDay", plan.getItemsPerDay());
        view.put("totalDays", plan.getTotalDays());
        view.put("today", todayIndex(plan));
        view.put("sessions", program.getSessionsPerType());
        view.put("questions", program.getQuestionsPerSession());
        view.put("weeklyQuestions", program.getWeeklyReviewQuestions());
        view.put("phases", phases);
        view.put("days", days);
        view.put("done", new ArrayList<>(doneKeys(plan)));
        view.put("unplaced", schedule.unplaced());
        view.put("newCount", schedule.newCount());
        view.put("reviewCount", schedule.reviewCount());
        view.put("weeklyCount", schedule.weeklyCount());
        view.put("miniCount", schedule.miniCount());
        view.put("fullCount", schedule.fullCount());
        return view;
    }

    /** Việc của đúng ngày hôm nay: bài trong ngày, bài làm bù, ôn tuần và tuần đang học. */
    public Map<String, Object> todayBoard(StudyPlan plan, String learnerName) {
        Map<String, Object> schedule = view(plan);
        int today = (int) schedule.get("today");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) schedule.get("days");
        Set<String> done = new java.util.HashSet<>();
        for (Object key : (List<?>) schedule.get("done")) done.add(String.valueOf(key));

        Map<String, Object> todayDay = days.get(today - 1);
        List<Map<String, Object>> lessons = new ArrayList<>();
        int doneToday = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> todaySlots = (List<Map<String, Object>>) todayDay.get("slots");
        for (int i = 0; i < todaySlots.size(); i++) {
            Map<String, Object> slot = todaySlots.get(i);
            boolean finished = done.contains(today + ":" + i);
            if (finished) doneToday++;
            lessons.add(lessonRow(today, i, slot, finished, "Hôm nay"));
        }

        Map<String, Object> makeup = null;
        int makeupCount = 0;
        for (Map<String, Object> day : days) {
            int dayNo = (int) day.get("day");
            if (dayNo >= today) break;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> slots = (List<Map<String, Object>>) day.get("slots");
            for (int i = 0; i < slots.size(); i++) {
                if (done.contains(dayNo + ":" + i)) continue;
                makeupCount++;
                if (makeup == null) makeup = lessonRow(dayNo, i, slots.get(i), false, "Làm bù");
            }
        }

        int week = (int) todayDay.get("week");
        Map<String, Object> weekly = null;
        List<Map<String, Object>> weekCells = new ArrayList<>();
        int weekDone = 0;
        int weekTotal = 0;
        int weekLate = 0;
        int weekAhead = 0;
        for (Map<String, Object> day : days) {
            if ((int) day.get("week") != week) continue;
            int dayNo = (int) day.get("day");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> slots = (List<Map<String, Object>>) day.get("slots");
            boolean weeklyDay = false;
            boolean anyOpen = false;
            boolean anyDone = false;
            int finishedOnDay = 0;
            for (int i = 0; i < slots.size(); i++) {
                weekTotal++;
                Map<String, Object> slot = slots.get(i);
                boolean finished = done.contains(dayNo + ":" + i);
                if ("WEEKLY".equals(slot.get("kind"))) weeklyDay = true;
                if (finished) {
                    weekDone++;
                    anyDone = true;
                    finishedOnDay++;
                } else {
                    anyOpen = true;
                    if (dayNo < today) weekLate++;
                    else if (dayNo > today) weekAhead++;
                }
                if (weekly == null && "WEEKLY".equals(slot.get("kind")) && dayNo != today) {
                    weekly = lessonRow(dayNo, i, slot, finished, finished ? "Đã xong" : "Làm trước");
                }
            }
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("day", dayNo);
            cell.put("label", "N" + dayNo);
            cell.put("done", finishedOnDay);
            cell.put("total", slots.size());
            String state = "ahead";
            if (dayNo == today) state = "today";
            else if (weeklyDay && anyOpen) state = "weekly";
            else if (slots.isEmpty()) state = "empty";
            else if (!anyOpen && anyDone) state = "done";
            else if (dayNo < today && anyOpen) state = "makeup";
            cell.put("state", state);
            weekCells.add(cell);
        }

        int totalDays = plan.getTotalDays();
        int studied = 0;
        for (Map<String, Object> day : days) {
            int dayNo = (int) day.get("day");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> slots = (List<Map<String, Object>>) day.get("slots");
            for (int i = 0; i < slots.size(); i++) {
                if (done.contains(dayNo + ":" + i)) {
                    studied++;
                    break;
                }
            }
        }
        Map<String, Object> weak = weakest(days, done, today, (int) schedule.get("sessions"));

        Map<String, Object> board = new LinkedHashMap<>();
        board.put("name", learnerName);
        board.put("track", schedule.get("track"));
        board.put("day", today);
        board.put("date", todayDay.get("date"));
        board.put("week", week);
        board.put("phase", todayDay.get("phase"));
        board.put("minutes", plan.getMinutesPerDay());
        board.put("lessons", lessons);
        board.put("doneToday", doneToday);
        board.put("totalToday", lessons.size());
        board.put("makeup", makeup);
        board.put("makeupCount", makeupCount);
        board.put("weekly", weekly);
        board.put("weekCells", weekCells);
        board.put("weekDone", weekDone);
        board.put("weekTotal", weekTotal);
        board.put("weekLate", weekLate);
        board.put("weekAhead", weekAhead);
        board.put("daysLeft", Math.max(0, totalDays - today));
        board.put("percent", totalDays == 0 ? 0 : (today - 1) * 100 / totalDays);
        board.put("studiedDays", studied);
        board.put("weak", weak);
        board.put("totalDays", totalDays);
        return board;
    }

    private static Map<String, Object> lessonRow(int day, int slot, Map<String, Object> source,
                                                boolean finished, String action) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("day", day);
        row.put("slot", slot);
        row.put("label", source.get("label"));
        row.put("title", source.get("title"));
        row.put("name", source.get("name"));
        row.put("kind", source.get("kind"));
        row.put("done", finished);
        row.put("action", action);
        row.put("href", practiceHref(source));
        return row;
    }

    private static String practiceHref(Map<String, Object> slot) {
        String kind = String.valueOf(slot.get("kind"));
        String type = practiceType(slot, kind);
        String name = String.valueOf(slot.get("name") == null ? slot.get("title") : slot.get("name"));
        String href = "/luyen-tap/chuan-bi?type=" + url(type) + "&name=" + url(name);
        if ("MINI".equals(kind) || "FULL".equals(kind)) return href;
        Object questions = slot.get("questions");
        return href + "&questions=" + (questions == null ? "8" : questions);
    }

    private static String practiceType(Map<String, Object> slot, String kind) {
        if ("MINI".equals(kind)) {
            String name = String.valueOf(slot.get("name"));
            return name.contains("Đọc") ? "mini-reading" : "mini-listening";
        }
        if ("FULL".equals(kind)) return "full";
        if (slot.get("code") == null) return "weekly";
        return String.valueOf(slot.get("code"));
    }

    private static Map<String, Object> weakest(List<Map<String, Object>> days, Set<String> done,
                                              int today, int sessions) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, String> titles = new LinkedHashMap<>();
        Map<String, String> opens = new LinkedHashMap<>();
        for (Map<String, Object> day : days) {
            if ((int) day.get("day") > today) break;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> slots = (List<Map<String, Object>>) day.get("slots");
            for (int i = 0; i < slots.size(); i++) {
                Map<String, Object> slot = slots.get(i);
                if (slot.get("code") == null) continue;
                String code = String.valueOf(slot.get("code"));
                counts.computeIfAbsent(code, k -> new int[2]);
                counts.get(code)[1]++;
                if (done.contains(day.get("day") + ":" + i)) counts.get(code)[0]++;
                else opens.putIfAbsent(code, practiceHref(slot));
                titles.putIfAbsent(code, String.valueOf(slot.get("title")));
            }
        }
        String chosen = null;
        double worst = 2;
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            int[] pair = entry.getValue();
            if (pair[0] >= sessions) continue;
            double ratio = pair[1] == 0 ? 1 : (double) pair[0] / pair[1];
            if (chosen == null || ratio < worst) {
                worst = ratio;
                chosen = entry.getKey();
            }
        }
        if (chosen == null) return null;
        int[] pair = counts.get(chosen);
        Map<String, Object> weak = new LinkedHashMap<>();
        weak.put("title", titles.get(chosen));
        weak.put("done", pair[0]);
        weak.put("seen", pair[1]);
        weak.put("sessions", sessions);
        weak.put("percent", pair[1] == 0 ? 0 : pair[0] * 100 / pair[1]);
        weak.put("href", opens.getOrDefault(chosen, "/lo-trinh"));
        return weak;
    }

    public static String givenName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "bạn";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length >= 3) return parts[parts.length - 2] + " " + parts[parts.length - 1];
        return parts[parts.length - 1];
    }

    private static String url(String text) {
        return java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Transactional
    public List<String> setDone(String owner, int day, int slot, boolean done) {
        StudyPlan plan = plans.findByOwnerEmail(owner)
                .orElseThrow(() -> new ExamDomainException("Bạn chưa có lộ trình."));
        RoadmapSketch.Result schedule = programs.preview(programOf(plan), plan.getTrack(),
                plan.getTotalDays(), plan.getItemsPerDay());
        RoadmapSketch.DayPlan dayPlan = schedule.days().stream()
                .filter(d -> d.day() == day)
                .findFirst()
                .orElseThrow(() -> new ExamDomainException("Không có ngày này trong lộ trình."));
        if (slot < 0 || slot >= dayPlan.slots().size()) {
            throw new ExamDomainException("Ngày này không có bài ở vị trí đó.");
        }
        Set<String> keys = doneKeys(plan);
        String key = day + ":" + slot;
        if (done) keys.add(key);
        else keys.remove(key);
        plan.setDoneKeys(keys.isEmpty() ? null : String.join(",", keys));
        plans.save(plan);
        return new ArrayList<>(keys);
    }

    private Map<String, Object> slotView(RoadmapSketch.Slot slot, int week, ProgramVersion program,
                                         Map<String, QuestionTemplate> byCode) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", slot.kind());
        row.put("code", slot.code());
        row.put("repetition", slot.repetition());
        if ("WEEKLY".equals(slot.kind())) {
            row.put("name", "Ôn tuần " + week);
            row.put("title", "Ôn tuần " + week);
            row.put("skill", "Ôn");
            row.put("label", "Ôn tuần");
            row.put("questions", program.getWeeklyReviewQuestions());
            return row;
        }
        if ("MINI".equals(slot.kind()) || "FULL".equals(slot.kind())) {
            row.put("name", slot.name());
            row.put("title", slot.name());
            row.put("skill", "FULL".equals(slot.kind()) ? "Thi" : "Mini");
            row.put("label", "FULL".equals(slot.kind()) ? "Full test" : "Mini test");
            return row;
        }
        QuestionTemplate template = slot.code() == null ? null : byCode.get(slot.code());
        String skill = template == null ? "" : template.getSkill().getLabel();
        String name = template == null || template.getName() == null ? slot.name() : template.getName();
        row.put("name", name);
        row.put("title", name);
        row.put("skill", skill);
        row.put("label", "NEW".equals(slot.kind()) ? "Bài mới" : "Ôn lần " + slot.repetition());
        row.put("questions", program.getQuestionsPerSession());
        return row;
    }

    private ProgramVersion programOf(StudyPlan plan) {
        try {
            return programs.get(plan.getProgramId());
        } catch (ExamDomainException e) {
            throw new ExamDomainException("Bản lộ trình này không còn. Hãy tạo lộ trình mới.");
        }
    }

    private ProgramVersion requirePublished() {
        ProgramVersion program = programs.published();
        if (program == null) {
            throw new ExamDomainException("Content Manager chưa phát hành lộ trình. Học viên chưa tạo được lịch.");
        }
        return program;
    }

    private String remapDone(StudyPlan plan, ProgramVersion nextProgram, Track nextTrack, int nextDays,
                             int nextPerDay) {
        RoadmapSketch.Result previous;
        try {
            previous = programs.preview(programOf(plan), plan.getTrack(), plan.getTotalDays(), plan.getItemsPerDay());
        } catch (ExamDomainException e) {
            return null;
        }
        Set<String> done = doneKeys(plan);
        Set<String> identities = new java.util.LinkedHashSet<>();
        for (RoadmapSketch.DayPlan day : previous.days()) {
            List<RoadmapSketch.Slot> slots = day.slots();
            for (int i = 0; i < slots.size(); i++) {
                if (done.contains(day.day() + ":" + i)) identities.add(identity(day.day(), slots.get(i)));
            }
        }
        if (identities.isEmpty()) return null;
        RoadmapSketch.Result next = programs.preview(nextProgram, nextTrack, nextDays, nextPerDay);
        List<String> kept = new ArrayList<>();
        for (RoadmapSketch.DayPlan day : next.days()) {
            List<RoadmapSketch.Slot> slots = day.slots();
            for (int i = 0; i < slots.size(); i++) {
                if (identities.contains(identity(day.day(), slots.get(i)))) kept.add(day.day() + ":" + i);
            }
        }
        return kept.isEmpty() ? null : String.join(",", kept);
    }

    private static String identity(int day, RoadmapSketch.Slot slot) {
        if ("WEEKLY".equals(slot.kind())) return "W:" + day;
        if ("MINI".equals(slot.kind())) return "MINI#" + slot.repetition();
        if ("FULL".equals(slot.kind())) return "FULL#" + slot.repetition();
        return slot.code() + "#" + slot.repetition();
    }

    private static int normalizeDays(int days) {
        if (days == 30) days = 31;
        if (days < 31 || days > 200) {
            throw new ExamDomainException("Lộ trình từ 31 đến 200 ngày.");
        }
        return days;
    }

    static int todayIndex(StudyPlan plan) {
        long passed = ChronoUnit.DAYS.between(plan.getStartDate(), LocalDate.now());
        int today = (int) passed + 1;
        if (today < 1) return 1;
        return Math.min(today, plan.getTotalDays());
    }

    private static Set<String> doneKeys(StudyPlan plan) {
        Set<String> keys = new TreeSet<>();
        if (plan.getDoneKeys() == null || plan.getDoneKeys().isBlank()) return keys;
        for (String part : plan.getDoneKeys().split(",")) {
            if (!part.isBlank()) keys.add(part.trim());
        }
        return keys;
    }

    public static Track trackOf(String raw) {
        if ("TOPIK_I".equalsIgnoreCase(raw) || "TOPIK I".equalsIgnoreCase(raw)) return Track.TOPIK_I;
        if ("TOPIK_II".equalsIgnoreCase(raw) || "TOPIK II".equalsIgnoreCase(raw)) return Track.TOPIK_II;
        throw new ExamDomainException("Chọn TOPIK I hoặc TOPIK II.");
    }
}
