package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.ProgramVersion;
import com.hottopik.entity.exam.StudyPlan;
import com.hottopik.entity.exam.Track;
import com.hottopik.service.exam.ExamDomainException;
import com.hottopik.service.exam.LearnerRoadmapService;
import com.hottopik.service.exam.RoadmapSketch;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Lộ trình của người học, lấy thứ tự dạng từ bản Content Manager đã phát hành. */
@Controller
public class RoadmapController {

    private static final DateTimeFormatter END_LABEL = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LearnerRoadmapService roadmaps;

    public RoadmapController(LearnerRoadmapService roadmaps) {
        this.roadmaps = roadmaps;
    }

    @GetMapping({"/lo-trinh", "/roadmap"})
    public String roadmap(HttpSession session, Model model) {
        StudyPlan plan;
        try {
            plan = roadmaps.ensure(owner(session), trackFor(session));
        } catch (ExamDomainException e) {
                model.addAttribute("pageTitle", "Lộ trình học theo ngày");
                model.addAttribute("activePage", "roadmap");
                model.addAttribute("loadError", e.getMessage());
                return "roadmap/index";
        }
        model.addAttribute("pageTitle", "Lộ trình học theo ngày");
        model.addAttribute("activePage", "roadmap");
        model.addAttribute("learnerName", LearnerRoadmapService.givenName(displayName(session)));
        model.addAttribute("levelPath", levelPath(session));
        try {
            model.addAttribute("plan", roadmaps.view(plan));
        } catch (ExamDomainException e) {
            model.addAttribute("loadError", e.getMessage());
        }
        return "roadmap/index";
    }

    @GetMapping({"/lo-trinh/tao", "/roadmap/create"})
    public String createForm(@RequestParam(defaultValue = "TOPIK_II") String track,
                             @RequestParam(defaultValue = "60") int days,
                             @RequestParam(defaultValue = "20") int minutes,
                             @RequestParam(required = false) String error,
                             HttpSession session, Model model) {
        model.addAttribute("pageTitle", "Tạo lộ trình học cá nhân");
        model.addAttribute("activePage", "roadmap");
        model.addAttribute("days", days);
        model.addAttribute("minutes", minutes);
        model.addAttribute("error", error);
        Track chosen;
        try {
            chosen = LearnerRoadmapService.trackOf(track);
        } catch (ExamDomainException e) {
            chosen = Track.TOPIK_II;
        }
        model.addAttribute("track", chosen);
        StudyPlan current = roadmaps.find(owner(session));
        if (current != null) {
            model.addAttribute("current", current.getTrack().getLabel() + " · " + current.getTotalDays()
                    + " ngày · bản " + current.getVersionCode());
        }
        ProgramVersion published = roadmaps.published();
        if (published == null) {
            model.addAttribute("missing", true);
            return "roadmap/create";
        }
        model.addAttribute("version", published.getVersionCode());
        model.addAttribute("phaseSplit", published.getPhaseSplit());
        model.addAttribute("questions", published.getQuestionsPerSession());
        model.addAttribute("weeklyQuestions", published.getWeeklyReviewQuestions());
        try {
            int safeDays = days == 30 ? 31 : days;
            RoadmapSketch.Result preview = roadmaps.preview(chosen, safeDays);
            model.addAttribute("preview", preview);
            model.addAttribute("shortfalls", RoadmapSketch.incomplete(preview, published.getSessionsPerType()));
            model.addAttribute("minimumDays", roadmaps.minimumDays(chosen));
            model.addAttribute("endDate", LocalDate.now().plusDays(safeDays - 1L).format(END_LABEL));
            model.addAttribute("lessonCount", preview.newCount() + preview.reviewCount()
                    + preview.weeklyCount() + preview.miniCount() + preview.fullCount());
        } catch (ExamDomainException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "roadmap/create";
    }

    @PostMapping({"/lo-trinh", "/roadmap"})
    public String create(@RequestParam String track,
                         @RequestParam int days,
                         @RequestParam int minutes,
                         HttpSession session) {
        try {
            roadmaps.open(owner(session), LearnerRoadmapService.trackOf(track), days, minutes);
            return "redirect:/lo-trinh";
        } catch (ExamDomainException e) {
            return "redirect:/lo-trinh/tao?track=" + track + "&days=" + days + "&minutes=" + minutes
                    + "&error=" + url(e.getMessage());
        }
    }

    @PostMapping("/lo-trinh/tien-do")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> mark(@RequestParam int day,
                                                    @RequestParam int slot,
                                                    @RequestParam(defaultValue = "1") String done,
                                                    HttpSession session) {
        try {
            boolean finished = !"0".equals(done) && !"false".equalsIgnoreCase(done);
            return ResponseEntity.ok(Map.of("ok", true,
                    "done", roadmaps.setDone(owner(session), day, slot, finished)));
        } catch (ExamDomainException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        }
    }

    @GetMapping({"/lo-trinh/dieu-chinh", "/roadmap/adjust"})
    public String adjustForm(@RequestParam(required = false) String track,
                             @RequestParam(required = false) Integer days,
                             @RequestParam(required = false) Integer minutes,
                             @RequestParam(required = false) String error,
                             HttpSession session, Model model) {
        model.addAttribute("pageTitle", "Điều chỉnh lộ trình học");
        model.addAttribute("activePage", "roadmap");
        model.addAttribute("error", error);
        try {
            StudyPlan plan = roadmaps.ensure(owner(session), trackFor(session));
            Track chosen;
            try {
                chosen = track == null || track.isBlank() ? plan.getTrack() : LearnerRoadmapService.trackOf(track);
            } catch (ExamDomainException e) {
                chosen = plan.getTrack();
            }
            int safeDays = days == null ? plan.getTotalDays() : days;
            int safeMinutes = minutes == null ? plan.getMinutesPerDay() : minutes;
            ProgramVersion published = roadmaps.published();
            model.addAttribute("planDays", plan.getTotalDays());
            model.addAttribute("planMinutes", plan.getMinutesPerDay());
            model.addAttribute("planTrack", plan.getTrack().getLabel());
            model.addAttribute("planVersion", plan.getVersionCode());
            model.addAttribute("track", chosen);
            model.addAttribute("days", safeDays);
            model.addAttribute("minutes", safeMinutes);
            if (published == null) {
                model.addAttribute("missing", true);
                return "roadmap/adjust";
            }
            model.addAttribute("version", published.getVersionCode());
            model.addAttribute("reviewOffsets", published.getReviewOffsets());
            model.addAttribute("phaseSplit", published.getPhaseSplit());
            model.addAttribute("sessions", published.getSessionsPerType());
            model.addAttribute("questions", published.getQuestionsPerSession());
            model.addAttribute("weeklyQuestions", published.getWeeklyReviewQuestions());
            model.addAttribute("typeOrder", roadmaps.typeOrder(chosen));
            int previewDays = safeDays == 30 ? 31 : safeDays;
            RoadmapSketch.Result preview = roadmaps.preview(chosen, previewDays);
            model.addAttribute("preview", preview);
            model.addAttribute("shortfalls", RoadmapSketch.incomplete(preview, published.getSessionsPerType()));
            model.addAttribute("minimumDays", roadmaps.minimumDays(chosen));
            model.addAttribute("endDate", plan.getStartDate().plusDays(previewDays - 1L).format(END_LABEL));
        } catch (ExamDomainException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "roadmap/adjust";
    }

    @PostMapping({"/lo-trinh/dieu-chinh", "/roadmap/adjust"})
    public String adjust(@RequestParam String track,
                         @RequestParam int days,
                         @RequestParam int minutes,
                         HttpSession session) {
        try {
            roadmaps.reshape(owner(session), LearnerRoadmapService.trackOf(track), days, minutes);
            return "redirect:/lo-trinh";
        } catch (ExamDomainException e) {
            return "redirect:/lo-trinh/dieu-chinh?track=" + track + "&days=" + days + "&minutes=" + minutes
                    + "&error=" + url(e.getMessage());
        }
    }

    @GetMapping({"/lo-trinh/nhin-lai-tuan", "/roadmap/retrospective"})
    public String retrospective(Model model) {
        model.addAttribute("pageTitle", "Nhìn lại tuần học");
        model.addAttribute("activePage", "roadmap");
        return "roadmap/retrospective";
    }

    private static Track trackFor(HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        String level = user == null || user.getTargetLevel() == null ? "" : user.getTargetLevel().toUpperCase();
        if (level.equals("TOPIK_I") || level.equals("TOPIK_1") || level.equals("TOPIK_2")
                || level.endsWith("_1") || level.endsWith("_2")) {
            return Track.TOPIK_I;
        }
        return Track.TOPIK_II;
    }

    private static String levelPath(HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return null;
        String from = prettyLevel(user.getCurrentLevel());
        String to = prettyLevel(user.getTargetLevel());
        if (from != null && to != null && !from.equals(to)) return from + " → " + to;
        if (to != null) return to;
        return from;
    }

    private static String prettyLevel(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        if (value.startsWith("TOPIK_")) {
            String rest = value.substring("TOPIK_".length());
            if (rest.matches("[0-9]")) return "TOPIK " + rest;
            return "TOPIK " + rest;
        }
        return raw.trim();
    }

    private static String displayName(HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return "bạn";
    }

    private static String owner(HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail().trim().toLowerCase();
        }
        return "guest";
    }

    private static String url(String text) {
        return java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);
    }
}
