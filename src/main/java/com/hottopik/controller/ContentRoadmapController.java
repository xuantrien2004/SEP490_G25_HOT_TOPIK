package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.ProgramStatus;
import com.hottopik.entity.exam.ProgramVersion;
import com.hottopik.entity.exam.ProgramVersionItem;
import com.hottopik.entity.exam.QuestionTemplate;
import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;
import com.hottopik.service.exam.Actor;
import com.hottopik.service.exam.ExamDomainException;
import com.hottopik.service.exam.ProgramRoadmapService;
import com.hottopik.service.exam.RoadmapSketch;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/** Lộ trình học: Content Manager sắp thứ tự dạng và gửi Admin duyệt phát hành. */
@Controller
@RequestMapping("/content/roadmap")
public class ContentRoadmapController {

    private final ProgramRoadmapService programs;

    public ContentRoadmapController(ProgramRoadmapService programs) {
        this.programs = programs;
    }

    public record TrackColumn(Track track, List<ProgramVersionItem> items, List<QuestionTemplate> available) {
    }

    @GetMapping
    public String list(HttpSession session, HttpServletRequest request, Model model) {
        UserDTO user = viewer(session);
        if (user == null) return denied(session, request);
        common(model, user, "Lộ trình học");
        model.addAttribute("versions", programs.list());
        model.addAttribute("published", programs.published());
        model.addAttribute("openCopy", programs.openCopy());
        model.addAttribute("itemCounts", programs.itemCounts());
        return "content/roadmap/list";
    }

    @PostMapping
    public String createDraft(HttpSession session, RedirectAttributes ra) {
        return mutate(session, ra, "/content/roadmap", actor -> {
            ProgramVersion draft = programs.createDraft(actor);
            ra.addFlashAttribute("flashOk", "Đã tạo bản nháp " + draft.getVersionCode() + ". Học viên vẫn học theo bản đang phát hành.");
            return "redirect:/content/roadmap/" + draft.getId();
        });
    }

    @GetMapping("/{id}")
    public String editor(@PathVariable Long id,
                         @RequestParam(defaultValue = "60") int days,
                         @RequestParam(defaultValue = "2") int perDay,
                         @RequestParam(defaultValue = "TOPIK_I") String track,
                         HttpSession session, HttpServletRequest request, Model model) {
        UserDTO user = viewer(session);
        if (user == null) return denied(session, request);
        ProgramVersion program;
        try {
            program = programs.get(id);
        } catch (ExamDomainException e) {
            return "redirect:/content/roadmap";
        }
        common(model, user, "Lộ trình " + program.getVersionCode());
        List<ProgramVersionItem> rows = programs.itemsOf(id);
        List<TrackColumn> columns = new ArrayList<>();
        for (Track value : Track.values()) {
            List<ProgramVersionItem> inTrack = rows.stream().filter(i -> i.getTemplate().getTrack() == value).toList();
            columns.add(new TrackColumn(value, inTrack, programs.addable(value, rows)));
        }
        model.addAttribute("program", program);
        model.addAttribute("columns", columns);
        model.addAttribute("editable", program.getStatus() == ProgramStatus.DRAFT && Actor.ROLE_CONTENT.equalsIgnoreCase(user.getRole()));
        Track previewTrack = "TOPIK_II".equalsIgnoreCase(track) ? Track.TOPIK_II : Track.TOPIK_I;
        model.addAttribute("previewTrack", previewTrack);
        model.addAttribute("days", days);
        model.addAttribute("perDay", perDay);
        try {
            RoadmapSketch.Result preview = programs.preview(program, previewTrack, days, perDay);
            model.addAttribute("preview", preview);
            model.addAttribute("previewDays", preview.days().stream().limit(21).toList());
        } catch (ExamDomainException e) {
            model.addAttribute("previewError", e.getMessage());
        }
        return "content/roadmap/editor";
    }

    @PostMapping("/{id}/settings")
    public String saveSettings(@PathVariable Long id,
                               @RequestParam(required = false) String note,
                               @RequestParam String reviewOffsets,
                               @RequestParam String phaseSplit,
                               @RequestParam int sessions,
                               @RequestParam int questions,
                               @RequestParam int weekly,
                               HttpSession session, RedirectAttributes ra) {
        return mutate(session, ra, "/content/roadmap/" + id, actor -> {
            programs.saveSettings(id, note, reviewOffsets, phaseSplit, sessions, questions, weekly, actor);
            ra.addFlashAttribute("flashOk", "Đã lưu quy tắc lộ trình.");
            return "redirect:/content/roadmap/" + id;
        });
    }

    @PostMapping("/{id}/items/{itemId}/move")
    public String move(@PathVariable Long id, @PathVariable Long itemId, @RequestParam String dir,
                       @RequestParam(required = false) String track,
                       @RequestParam(required = false) Integer days,
                       @RequestParam(required = false) Integer perDay,
                       HttpSession session, RedirectAttributes ra) {
        return mutate(session, ra, editorPath(id, track, days, perDay), actor -> {
            programs.move(id, itemId, "up".equals(dir) ? -1 : 1, actor);
            return "redirect:" + editorPath(id, track, days, perDay);
        });
    }

    @PostMapping("/{id}/items/{itemId}/remove")
    public String remove(@PathVariable Long id, @PathVariable Long itemId,
                         @RequestParam(required = false) String track,
                         @RequestParam(required = false) Integer days,
                         @RequestParam(required = false) Integer perDay,
                         HttpSession session, RedirectAttributes ra) {
        return mutate(session, ra, editorPath(id, track, days, perDay), actor -> {
            programs.remove(id, itemId, actor);
            ra.addFlashAttribute("flashOk", "Đã gỡ dạng khỏi lộ trình.");
            return "redirect:" + editorPath(id, track, days, perDay);
        });
    }

    @PostMapping("/{id}/items")
    public String add(@PathVariable Long id, @RequestParam Long templateId,
                      @RequestParam(required = false) String track,
                      @RequestParam(required = false) Integer days,
                      @RequestParam(required = false) Integer perDay,
                      HttpSession session, RedirectAttributes ra) {
        return mutate(session, ra, editorPath(id, track, days, perDay), actor -> {
            programs.add(id, templateId, actor);
            ra.addFlashAttribute("flashOk", "Đã thêm dạng vào cuối lộ trình của track đó.");
            return "redirect:" + editorPath(id, track, days, perDay);
        });
    }

    @PostMapping("/{id}/publish")
    public String publish(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        return mutate(session, ra, "/content/roadmap/" + id, actor -> {
            programs.submitForPublish(id, actor);
            ra.addFlashAttribute("flashOk", "Đã gửi Admin duyệt. Học viên vẫn học theo bản đang phát hành cho đến khi được duyệt.");
            return "redirect:/content/roadmap/" + id;
        });
    }

    private static UserDTO viewer(HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return null;
        return (Actor.ROLE_CONTENT.equalsIgnoreCase(user.getRole()) || Actor.ROLE_ADMIN.equalsIgnoreCase(user.getRole())) ? user : null;
    }

    private static String denied(HttpSession session, HttpServletRequest request) {
        if (AuthHelper.getLoggedInUser(session) == null) {
            String path = request.getRequestURI();
            String query = request.getQueryString();
            if (query != null && !query.isBlank()) path = path + "?" + query;
            session.setAttribute("RETURN_AFTER_LOGIN", path);
            return "redirect:/login";
        }
        return "redirect:/today";
    }

    /** Giữ bộ lọc lịch mẫu khi sắp lại thứ tự, để trang không mở lại bằng tham số mặc định. */
    private static String editorPath(Long id, String track, Integer days, Integer perDay) {
        String chosen = "TOPIK_II".equalsIgnoreCase(track) ? "TOPIK_II" : "TOPIK_I";
        int safeDays = days == null ? 60 : Math.max(31, Math.min(200, days));
        int safePerDay = perDay == null ? 2 : Math.max(1, Math.min(4, perDay));
        return "/content/roadmap/" + id + "?track=" + chosen + "&days=" + safeDays + "&perDay=" + safePerDay;
    }

    private static void common(Model model, UserDTO user, String title) {
        model.addAttribute("currentUser", user);
        model.addAttribute("canEdit", Actor.ROLE_CONTENT.equalsIgnoreCase(user.getRole()));
        model.addAttribute("pageTitle", title);
        model.addAttribute("writingSkill", Skill.WRITING);
    }

    @FunctionalInterface
    private interface Action {
        String run(Actor actor);
    }

    private String mutate(HttpSession session, RedirectAttributes ra, String fallback, Action action) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        if (!Actor.ROLE_CONTENT.equalsIgnoreCase(user.getRole())) {
            ra.addFlashAttribute("flashError", "Bạn chỉ có quyền xem mục này.");
            return "redirect:" + fallback;
        }
        try {
            return action.run(Actor.of(user));
        } catch (ExamDomainException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Có lỗi xảy ra: " + e.getMessage());
        }
        return "redirect:" + fallback;
    }
}
