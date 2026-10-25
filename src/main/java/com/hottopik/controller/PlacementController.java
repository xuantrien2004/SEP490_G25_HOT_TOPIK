package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.ExamSource;
import com.hottopik.entity.exam.MediaKind;
import com.hottopik.entity.exam.PlacementAttempt;
import com.hottopik.entity.exam.SourceStatus;
import com.hottopik.entity.exam.Track;
import com.hottopik.repository.exam.ExamSourceRepository;
import com.hottopik.service.PlacementService;
import com.hottopik.service.PlacementService.ResultView;
import com.hottopik.service.UserService;
import com.hottopik.service.exam.ExamDomainException;
import com.hottopik.service.exam.ExamImportService;
import com.hottopik.service.exam.LearnerRoadmapService;
import com.hottopik.service.exam.PlacementRules;
import com.hottopik.service.exam.PlacementRules.Paper;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class PlacementController {

    private final PlacementService placement;
    private final UserService users;
    private final LearnerRoadmapService roadmaps;
    private final ExamImportService imports;
    private final ExamSourceRepository sources;

    public PlacementController(PlacementService placement, UserService users, LearnerRoadmapService roadmaps,
                               ExamImportService imports, ExamSourceRepository sources) {
        this.placement = placement;
        this.users = users;
        this.roadmaps = roadmaps;
        this.imports = imports;
        this.sources = sources;
    }

    @GetMapping("/placement")
    public String intro(@RequestParam(defaultValue = "TOPIK_I") String paper, HttpSession session, Model model) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        try {
            Paper chosen = Paper.fromCode(paper);
            model.addAttribute("pageTitle", chosen.title());
            model.addAttribute("sheet", placement.paper(chosen.name()));
            model.addAttribute("currentUser", user);
            return "placement/intro";
        } catch (ExamDomainException e) {
            model.addAttribute("pageTitle", "Kiểm tra trình độ");
            model.addAttribute("placementError", e.getMessage());
            model.addAttribute("currentUser", user);
            return "placement/intro";
        }
    }

    @PostMapping("/placement/start")
    public String start(@RequestParam String paper, HttpSession session, RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        try {
            PlacementAttempt attempt = placement.start(user.getId(), user.getEmail(), paper);
            return "redirect:/placement/play/" + attempt.getId();
        } catch (ExamDomainException e) {
            flash.addFlashAttribute("placementError", e.getMessage());
            return "redirect:/placement?paper=" + safe(paper);
        }
    }

    @GetMapping("/placement/play/{id}")
    public String play(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes flash) {
        try {
            PlacementAttempt attempt = owned(id, session);
            if (attempt.getCompletedAt() != null) return "redirect:/placement/result/" + id;
            Paper paper = Paper.fromCode(attempt.getPaperCode());
            model.addAttribute("pageTitle", paper.title());
            model.addAttribute("attempt", attempt);
            model.addAttribute("paper", paper);
            model.addAttribute("questions", placement.questions(attempt));
            model.addAttribute("currentUser", AuthHelper.getLoggedInUser(session));
            model.addAttribute("minutes", 20);
            return "placement/play";
        } catch (ExamDomainException e) {
            flash.addFlashAttribute("placementError", e.getMessage());
            return "redirect:/onboarding";
        }
    }

    @PostMapping("/placement/submit/{id}")
    public String submit(@PathVariable Long id, @RequestParam MultiValueMap<String, String> form,
                         HttpSession session, RedirectAttributes flash) {
        try {
            PlacementAttempt attempt = owned(id, session);
            Map<String, String> answers = form.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("answer_"))
                    .collect(Collectors.toMap(entry -> entry.getKey().substring("answer_".length()),
                            entry -> entry.getValue().isEmpty() ? "" : entry.getValue().get(0)));
            placement.submit(attempt, answers);
            return "redirect:/placement/result/" + id;
        } catch (ExamDomainException e) {
            flash.addFlashAttribute("placementError", e.getMessage());
            return "redirect:/onboarding";
        }
    }

    @GetMapping({"/placement/result/{id}", "/placement/ket-qua/{id}"})
    public String result(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes flash) {
        try {
            PlacementAttempt attempt = owned(id, session);
            if (attempt.getCompletedAt() == null) return "redirect:/placement/play/" + id;
            ResultView result = placement.result(attempt);
            model.addAttribute("pageTitle", "Kết quả kiểm tra trình độ");
            model.addAttribute("result", result);
            model.addAttribute("listenPoints", attempt.getListenCorrect() * PlacementRules.POINT);
            model.addAttribute("readPoints", attempt.getReadCorrect() * PlacementRules.POINT);
            model.addAttribute("currentUser", AuthHelper.getLoggedInUser(session));
            Track stay = result.level() == 0 && result.paper() == Paper.TOPIK_II ? Track.TOPIK_I : result.paper().track();
            model.addAttribute("stayTrack", stay.name());
            return "placement/result";
        } catch (ExamDomainException e) {
            flash.addFlashAttribute("placementError", e.getMessage());
            return "redirect:/onboarding";
        }
    }

    @GetMapping("/placement/khai")
    public String declare(HttpSession session, Model model) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        model.addAttribute("pageTitle", "Mình biết trình độ");
        model.addAttribute("currentUser", user);
        fillDates(model);
        return "placement/declare";
    }

    @GetMapping("/placement/muc-tieu")
    public String goal(@RequestParam Long attempt, @RequestParam String track, HttpSession session,
                       Model model, RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        try {
            PlacementAttempt taken = owned(attempt, session);
            if (taken.getCompletedAt() == null) return "redirect:/placement/play/" + attempt;
            ResultView result = placement.result(taken);
            Track chosen = Track.valueOf(track);
            int current = chosen == Track.TOPIK_I && result.paper() == Paper.TOPIK_II ? 0 : result.level();
            if (chosen == Track.TOPIK_II && result.paper() != Paper.TOPIK_II) {
                throw new ExamDomainException("Hãy làm bài TOPIK II trước khi chọn mục tiêu cấp 3–6.");
            }
            fillGoal(model, user, current, chosen, attempt);
            return "placement/goal";
        } catch (ExamDomainException | IllegalArgumentException e) {
            flash.addFlashAttribute("placementError", e.getMessage());
            return "redirect:/placement/result/" + attempt;
        }
    }

    @PostMapping("/placement/sinh")
    public String build(@RequestParam(required = false) Long attempt,
                        @RequestParam int current,
                        @RequestParam int target,
                        @RequestParam String date,
                        @RequestParam(defaultValue = "20") int minutes,
                        @RequestParam(required = false) String track,
                        HttpSession session, RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        String back = attempt == null ? "/placement/khai" : "/placement/muc-tieu?attempt=" + attempt + "&track=" + track;
        try {
            Track chosen = resolveTrack(attempt, current, target, track, session);
            List<Integer> allowed = PlacementRules.targets(current, chosen);
            if (!allowed.contains(target)) {
                throw new ExamDomainException("Mục tiêu không khớp trình hiện tại.");
            }
            LocalDate end = LocalDate.parse(date);
            long days = ChronoUnit.DAYS.between(LocalDate.now(), end) + 1;
            if (days < PlacementRules.MIN_DAYS || days > PlacementRules.MAX_DAYS) {
                throw new ExamDomainException("Chọn từ " + PlacementRules.MIN_DAYS + " đến " + PlacementRules.MAX_DAYS
                        + " ngày, hoặc một ngày trong khoảng đó.");
            }
            users.saveRoadmapSettings(user.getId(), String.valueOf(current), String.valueOf(target), minutes, attempt == null ? "SELF" : "PLACEMENT");
            user.setCurrentLevel(current == 0 ? "TOPIK_0" : "TOPIK_" + current);
            user.setTargetLevel("TOPIK_" + target);
            roadmaps.open(user.getEmail().trim().toLowerCase(), chosen, (int) days, minutes);
            return "redirect:/lo-trinh";
        } catch (DateTimeParseException e) {
            flash.addFlashAttribute("placementError", "Chọn ngày đạt mục tiêu.");
            return "redirect:" + back;
        } catch (ExamDomainException e) {
            flash.addFlashAttribute("placementError", e.getMessage());
            return "redirect:" + back;
        }
    }

    @GetMapping("/placement/media/{sourceId}/{filename:.+}")
    public ResponseEntity<Resource> media(@PathVariable Long sourceId, @PathVariable String filename) throws IOException {
        ExamSource source = sources.findById(sourceId).orElse(null);
        if (source == null || !source.isAssessment() || source.getStatus() != SourceStatus.ACTIVE) {
            return ResponseEntity.notFound().build();
        }
        Optional<com.hottopik.entity.exam.MediaAsset> asset = imports.findMedia(sourceId, filename);
        if (asset.isEmpty()) return ResponseEntity.notFound().build();
        Path path = imports.mediaPath(asset.get());
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        String type = Optional.ofNullable(Files.probeContentType(path))
                .orElse(asset.get().getKind() == MediaKind.AUDIO ? "audio/mpeg" : "application/octet-stream");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).body(new FileSystemResource(path));
    }

    private void fillGoal(Model model, UserDTO user, int current, Track track, Long attempt) {
        model.addAttribute("pageTitle", "Chọn mục tiêu");
        model.addAttribute("currentUser", user);
        model.addAttribute("currentLevel", current);
        model.addAttribute("currentLabel", PlacementRules.levelLabel(current));
        model.addAttribute("track", track);
        model.addAttribute("targets", PlacementRules.targets(current, track));
        model.addAttribute("attemptId", attempt);
        fillDates(model);
    }

    /** 31 ngày kết thúc vào hôm nay + 30. Mặc định 60 ngày. */
    private static void fillDates(Model model) {
        LocalDate today = LocalDate.now();
        model.addAttribute("minDays", PlacementRules.MIN_DAYS);
        model.addAttribute("maxDays", PlacementRules.MAX_DAYS);
        model.addAttribute("goalDays", PlacementRules.DEFAULT_DAYS);
        model.addAttribute("goalDate", today.plusDays(PlacementRules.DEFAULT_DAYS - 1L));
        model.addAttribute("earliest", today.plusDays(PlacementRules.MIN_DAYS - 1L));
        model.addAttribute("latest", today.plusDays(PlacementRules.MAX_DAYS - 1L));
    }

    private Track resolveTrack(Long attempt, int current, int target, String track, HttpSession session) {
        if (attempt == null) {
            if (target <= current && current < 6) {
                throw new ExamDomainException("Mục tiêu phải cao hơn trình hiện tại.");
            }
            return PlacementRules.trackForTarget(target);
        }
        owned(attempt, session);
        if (track == null) throw new ExamDomainException("Chưa chọn lộ trình.");
        return Track.valueOf(track);
    }

    private PlacementAttempt owned(Long id, HttpSession session) {
        PlacementAttempt attempt = placement.require(id);
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null || user.getId() == null || attempt.getUserId() == null || !attempt.getUserId().equals(user.getId())) {
            throw new ExamDomainException("Bài kiểm tra này không thuộc tài khoản đang đăng nhập.");
        }
        return attempt;
    }

    private static String safe(String paper) {
        try {
            return Paper.fromCode(paper).name();
        } catch (ExamDomainException e) {
            return Paper.TOPIK_I.name();
        }
    }
}
