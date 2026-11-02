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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class PlacementController {

    private static final Logger log = LoggerFactory.getLogger(PlacementController.class);
    static final String DATABASE_NOT_READY = "Chưa mở được bài kiểm tra vì database local thiếu bảng lưu lượt làm. Không phải vì chưa có câu hỏi. Tắt app rồi chạy lại profile local.";

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
    public String intro(HttpSession session, Model model) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        try {
            Paper chosen = PlacementRules.learnerPaper();
            model.addAttribute("pageTitle", chosen.title());
            model.addAttribute("sheet", placement.paper(chosen.name()));
            model.addAttribute("currentUser", user);
            return "placement/intro";
        } catch (ExamDomainException e) {
            model.addAttribute("pageTitle", "Bài kiểm tra trình độ");
            model.addAttribute("placementError", e.getMessage());
            model.addAttribute("currentUser", user);
            return "placement/intro";
        } catch (DataAccessException e) {
            return placementDown(model, user, e);
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
        } catch (DataAccessException e) {
            log.warn("Không mở được bài kiểm tra trình độ: {}", e.toString());
            flash.addFlashAttribute("placementError", databaseMessage(e));
            return "redirect:/placement";
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
        } catch (DataAccessException e) {
            log.warn("Không mở được bài làm trình độ: {}", e.toString());
            flash.addFlashAttribute("placementError", databaseMessage(e));
            return "redirect:/placement";
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
            model.addAttribute("maxPoints", PlacementRules.MAX_POINTS);
            model.addAttribute("choices", choices(result.level()));
            model.addAttribute("currentUser", AuthHelper.getLoggedInUser(session));
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
    public String goal(@RequestParam Long attempt,
                       @RequestParam String track,
                       @RequestParam(required = false) Integer target,
                       @RequestParam(required = false) Integer targetI,
                       @RequestParam(required = false) Integer targetII,
                       HttpSession session, Model model, RedirectAttributes flash) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        if (user == null) return "redirect:/login";
        try {
            PlacementAttempt taken = owned(attempt, session);
            if (taken.getCompletedAt() == null) return "redirect:/placement/play/" + attempt;
            ResultView result = placement.result(taken);
            Track chosen = Track.valueOf(track);
            int chosenTarget = postedTarget(chosen, target, targetI, targetII);
            if (!PlacementRules.roadmapTargets(chosen).contains(chosenTarget)) {
                throw new ExamDomainException("Mục tiêu không nằm trong lộ trình đã chọn.");
            }
            fillGoal(model, user, result.level(), chosen, chosenTarget, attempt);
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
        String back = attempt == null ? "/placement/khai"
                : "/placement/muc-tieu?attempt=" + attempt + "&track=" + track + "&target=" + target;
        try {
            Track chosen = resolveTrack(attempt, current, target, track, session);
            List<Integer> allowed = attempt == null
                    ? PlacementRules.targets(current, chosen)
                    : PlacementRules.roadmapTargets(chosen);
            if (!allowed.contains(target)) {
                throw new ExamDomainException(attempt == null
                        ? "Mục tiêu không khớp trình hiện tại."
                        : "Mục tiêu không nằm trong lộ trình đã chọn.");
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

    private void fillGoal(Model model, UserDTO user, int current, Track track, int target, Long attempt) {
        model.addAttribute("pageTitle", "Hoàn thành trong bao lâu");
        model.addAttribute("currentUser", user);
        model.addAttribute("currentLevel", current);
        model.addAttribute("currentLabel", PlacementRules.levelLabel(current));
        model.addAttribute("track", track);
        model.addAttribute("targetLevel", target);
        model.addAttribute("choiceChip", "Đã chọn lộ trình " + track.getLabel() + " · mục tiêu TOPIK " + target);
        model.addAttribute("attemptId", attempt);
        fillDates(model);
    }

    /** Mốc tháng là lối tắt. Ô số ngày và ô lịch vẫn chọn bất kỳ ngày nào từ 31 đến 200. */
    private static void fillDates(Model model) {
        LocalDate today = LocalDate.now();
        int days = PlacementRules.defaultSpan();
        model.addAttribute("minDays", PlacementRules.MIN_DAYS);
        model.addAttribute("maxDays", PlacementRules.MAX_DAYS);
        model.addAttribute("goalDays", days);
        model.addAttribute("goalDate", today.plusDays(days - 1L));
        model.addAttribute("earliest", today.plusDays(PlacementRules.MIN_DAYS - 1L));
        model.addAttribute("latest", today.plusDays(PlacementRules.MAX_DAYS - 1L));
        model.addAttribute("dayMarks", PlacementRules.dayMarks());
    }

    private static int postedTarget(Track track, Integer target, Integer targetI, Integer targetII) {
        if (target != null) return target;
        Integer fromRow = track == Track.TOPIK_I ? targetI : targetII;
        return fromRow == null ? PlacementRules.defaultTarget(track) : fromRow;
    }

    private static List<Map<String, Object>> choices(int level) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Track track : PlacementRules.choiceOrder(level)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("track", track);
            row.put("label", PlacementRules.choiceLabel(track, level));
            row.put("suggested", track == PlacementRules.suggestedTrack(level));
            row.put("defaultTarget", PlacementRules.defaultTarget(track));
            row.put("targets", PlacementRules.roadmapTargets(track));
            row.put("field", track == Track.TOPIK_I ? "targetI" : "targetII");
            rows.add(row);
        }
        return rows;
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

    static String databaseMessage(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String detail = root.getMessage() == null ? "" : root.getMessage().replaceAll("\\s+", " ").trim();
        if (detail.length() > 160) detail = detail.substring(0, 160);
        return detail.isBlank() ? DATABASE_NOT_READY : DATABASE_NOT_READY + " " + detail;
    }

    private String placementDown(Model model, UserDTO user, Exception e) {
        log.warn("Không mở được bài kiểm tra trình độ: {}", e.toString());
        model.addAttribute("pageTitle", "Bài kiểm tra trình độ");
        model.addAttribute("placementError", databaseMessage(e));
        model.addAttribute("currentUser", user);
        return "placement/intro";
    }

    private static String safe(String paper) {
        try {
            return Paper.fromCode(paper).name();
        } catch (ExamDomainException e) {
            return Paper.TOPIK_I.name();
        }
    }
}
