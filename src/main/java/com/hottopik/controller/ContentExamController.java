package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.*;
import com.hottopik.service.exam.*;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Nhập đề, thư viện đề, trình soạn khung đề và ngân hàng câu hỏi.
 * Quyền (BRD 2.1): Content Manager = toàn quyền; Admin = chỉ xem; vai trò khác không truy cập.
 */
@Controller
@RequestMapping("/content")
public class ContentExamController {

    private final ExamImportService importService;
    private final ExamComposerService composer;
    private final QuestionBankService bank;
    private final QuestionTemplateService templates;
    private final com.hottopik.service.QuestionReportService reportService;

    public ContentExamController(ExamImportService importService, ExamComposerService composer, QuestionBankService bank,
                                 QuestionTemplateService templates, com.hottopik.service.QuestionReportService reportService) {
        this.importService = importService;
        this.composer = composer;
        this.bank = bank;
        this.templates = templates;
        this.reportService = reportService;
    }

    // ------------------------------------------------------------------ quyền

    private static UserDTO viewer(HttpSession session) {
        UserDTO u = AuthHelper.getLoggedInUser(session);
        if (u == null) return null;
        return (Actor.ROLE_CONTENT.equalsIgnoreCase(u.getRole()) || Actor.ROLE_ADMIN.equalsIgnoreCase(u.getRole())) ? u : null;
    }

    private static String denied(HttpSession session) {
        return AuthHelper.getLoggedInUser(session) == null ? "redirect:/login" : "redirect:/today";
    }

    private static void common(Model model, UserDTO u, String title) {
        model.addAttribute("currentUser", u);
        model.addAttribute("canEdit", Actor.ROLE_CONTENT.equalsIgnoreCase(u.getRole()));
        model.addAttribute("pageTitle", title);
        model.addAttribute("tracks", Track.values());
        model.addAttribute("skills", Skill.values());
        model.addAttribute("kinds", ExamKind.values());
        model.addAttribute("purposes", QuestionPurpose.values());
        model.addAttribute("questionStatuses", QuestionStatus.values());
    }

    @FunctionalInterface
    private interface Action {
        String run(Actor actor) throws Exception;
    }

    /** Thao tác ghi: chỉ Content Manager; lỗi nghiệp vụ hiện lại trên trang đích. */
    private String mutate(HttpSession session, RedirectAttributes ra, String fallback, Action action) {
        UserDTO u = AuthHelper.getLoggedInUser(session);
        if (u == null) return "redirect:/login";
        if (!Actor.ROLE_CONTENT.equalsIgnoreCase(u.getRole())) {
            ra.addFlashAttribute("flashError", "Bạn chỉ có quyền xem mục này.");
            return "redirect:" + fallback;
        }
        try {
            return action.run(Actor.of(u));
        } catch (ExamDomainException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
        } catch (IOException e) {
            ra.addFlashAttribute("flashError", "Không lưu được file: " + e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("flashError", "Có lỗi xảy ra: " + e.getMessage());
        }
        return "redirect:" + fallback;
    }

    // ------------------------------------------------------------------ 42 dạng câu

    @GetMapping("/question-types")
    public String questionTypes(HttpSession session, Model model) {
        UserDTO u = viewer(session);
        if (u == null) return denied(session);
        common(model, u, "Vị trí trong đề chuẩn");
        Map<String, List<QuestionTemplate>> groups = new LinkedHashMap<>();
        for (QuestionTemplate template : templates.list(null, null)) {
            groups.computeIfAbsent(template.getTrack().getLabel() + " · " + template.getSkill().getLabel(), k -> new ArrayList<>()).add(template);
        }
        model.addAttribute("templateGroups", groups);
        model.addAttribute("bankByTemplate", bank.activeByTemplate());
        model.addAttribute("sectionsI", QuestionTypeCatalog.sections(Track.TOPIK_I));
        model.addAttribute("sectionsII", QuestionTypeCatalog.sections(Track.TOPIK_II));
        return "content/exam/types";
    }

    @GetMapping("/templates")
    public String templates(@RequestParam(required = false) Track track,
                            @RequestParam(required = false) Skill skill,
                            @RequestParam(defaultValue = "0") int page,
                            HttpSession session, Model model) {
        UserDTO u = viewer(session);
        if (u == null) return denied(session);
        common(model, u, "Dạng câu");
        List<QuestionTemplate> all = templates.list(track, skill);
        int size = 10;
        int pages = Math.max(1, (all.size() + size - 1) / size);
        int safe = Math.min(Math.max(page, 0), pages - 1);
        int from = all.isEmpty() ? 0 : safe * size;
        int to = Math.min(from + size, all.size());
        model.addAttribute("rows", all.subList(from, to));
        model.addAttribute("page", safe);
        model.addAttribute("pages", pages);
        model.addAttribute("totalRows", all.size());
        model.addAttribute("pageFrom", all.isEmpty() ? 0 : from + 1);
        model.addAttribute("pageTo", to);
        model.addAttribute("prevPage", safe > 0 ? templatePage(track, skill, safe - 1) : null);
        model.addAttribute("nextPage", safe + 1 < pages ? templatePage(track, skill, safe + 1) : null);
        model.addAttribute("trackFilter", track);
        model.addAttribute("skillFilter", skill);
        return "content/exam/templates";
    }

    private static String templatePage(Track track, Skill skill, int page) {
        StringBuilder link = new StringBuilder("/content/templates?page=").append(page);
        if (track != null) link.append("&track=").append(track.name());
        if (skill != null) link.append("&skill=").append(skill.name());
        return link.toString();
    }

    @GetMapping("/templates/{code}")
    public String templateDetail(@PathVariable String code, HttpSession session, Model model, RedirectAttributes ra) {
        UserDTO u = viewer(session);
        if (u == null) return denied(session);
        QuestionTemplate template;
        try {
            template = templates.get(code);
        } catch (ExamDomainException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/content/templates";
        }
        common(model, u, template.getCode());
        model.addAttribute("template", template);
        model.addAttribute("fields", templates.fields(template));
        model.addAttribute("typeCodes", QuestionTemplateCatalog.typeCodes(code));
        model.addAttribute("statuses", TemplateStatus.values());
        return "content/exam/template-detail";
    }

    @PostMapping("/templates/{code}")
    public String updateTemplate(@PathVariable String code,
                                 @RequestParam String name,
                                 @RequestParam String description,
                                 @RequestParam TemplateStatus status,
                                 @RequestParam(defaultValue = "false") boolean allowMiniTest,
                                 @RequestParam(defaultValue = "false") boolean allowPractice,
                                 @RequestParam(defaultValue = "false") boolean allowRoadmap,
                                 HttpSession session, RedirectAttributes ra) {
        return mutate(session, ra, "/content/templates/" + code, actor -> {
            templates.update(code, name, description, status, allowMiniTest, allowPractice, allowRoadmap, actor);
            ra.addFlashAttribute("flashOk", "Đã lưu dạng câu " + code + ". Câu hỏi và đề đã xuất bản không bị viết lại.");
            return "redirect:/content/templates/" + code;
        });
    }

    // ------------------------------------------------------------------ nguồn đề + nhập đề

    @GetMapping("/sources")
    public String sources(HttpSession session, Model model) {
        UserDTO u = viewer(session);
        if (u == null) return denied(session);
        common(model, u, "Nhập đề mới");
        List<ExamSource> list = importService.listSources();
        Map<Long, ImportJob> jobs = new HashMap<>();
        for (ExamSource s : list) importService.latestJob(s.getId()).ifPresent(j -> jobs.put(s.getId(), j));
        model.addAttribute("sources", list);
        model.addAttribute("jobs", jobs);
        return "content/exam/sources";
    }

    @GetMapping("/sources/new")
    public String newSource(HttpSession session, Model model) {
        UserDTO u = viewer(session);
        if (u == null) return denied(session);
        common(model, u, "Tạo nguồn đề");
        return "content/exam/source-new";
    }

    /* =========================================================================================
     * LUỒNG CHỨC NĂNG: TẠO NGUỒN ĐỀ THI MỚI (EXAM SOURCE CREATION - FUNC_EXAM_01)
     * -----------------------------------------------------------------------------------------
     * 1. THAO TÁC NGƯỜI DÙNG & UI/HTML:
     *    - Content Manager vào "Quản lý nguồn đề" (/content/sources), nhấn nút "Tạo nguồn đề mới".
     *    - Giao diện src/main/resources/templates/content/exam/source-new.html hiển thị Form.
     *    - Người dùng nhập Tên đề, Khung (TOPIK I / TOPIK II), Năm, Kỳ thi, Bản quyền... rồi nhấn "Lưu nguồn đề".
     * 2. HTTP REQUEST & ENDPOINT:
     *    - URL: POST /content/sources
     *    - Headers: Content-Type: application/x-www-form-urlencoded
     *    - Params: name, track, year, round, expectedCount, assessment, copyrightNote, note
     * 3. CONTROLLER & SERVICE XỬ LÝ:
     *    - Controller: com.hottopik.controller.ContentExamController.createSource()
     *    - Kiểm tra quyền Content Manager qua helper mutate() và viewer().
     *    - Triệu gọi Service:
     *      + com.hottopik.service.exam.ExamImportService.createSource(form, actor):
     *        * Khởi tạo Entity ExamSource với trạng thái DRAFT.
     *        * Ghi vết audit log hành động tạo nguồn đề.
     * 4. TRUY VẤN CSDL:
     *    - INSERT INTO dbo.exam_sources (name, track, year, round, status, created_by, created_at...)
     * 5. HIỂN THỊ KẾT QUẢ CHO QUẢN LÝ:
     *    - Redirect: 302 đến /content/sources/{id} (Chi tiết nguồn đề vừa tạo).
     *    - Giao diện src/main/resources/templates/content/exam/source-detail.html hiển thị thông báo flashOk:
     *      "Đã tạo bộ đề. Chọn dạng câu rồi điền câu hỏi."
     * ========================================================================================= */
    @PostMapping("/sources")
    public String createSource(@RequestParam String name, @RequestParam Track track,
                               @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer round,
                               @RequestParam(required = false) Integer expectedCount,
                               @RequestParam(defaultValue = "false") boolean assessment,
                               @RequestParam String copyrightNote, @RequestParam(required = false) String note,
                               HttpSession session, RedirectAttributes ra) {
        ra.addFlashAttribute("form", Map.of("name", name, "copyrightNote", copyrightNote));
        return mutate(session, ra, "/content/sources/new", actor -> {
            ExamSource s = importService.createSource(new ExamImportService.SourceForm(name, track, year, round,
                    expectedCount, assessment, copyrightNote, note), actor);
            ra.addFlashAttribute("flashOk", "Đã tạo bộ đề. Chọn dạng câu rồi điền câu hỏi.");
            return "redirect:/content/sources/" + s.getId();
        });
    }

    @PostMapping("/sources/{id}/manual")
    public String startManual(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        return mutate(session, ra, "/content/sources/" + id, actor -> {
            importService.startManual(id, actor);
            ra.addFlashAttribute("flashOk", "Đã mở tờ đề trống. Chọn dạng câu rồi điền câu.");
            return "redirect:/content/sources/" + id;
        });
    }

    @PostMapping("/sources/{id}/paper")
    public String savePaper(@PathVariable Long id,
                            @RequestParam(defaultValue = "false") boolean writing,
                            @RequestParam(required = false) Skill skill,
                            @RequestParam int no,
                            @RequestParam(required = false) String prompt,
                            @RequestParam(required = false) String opt1,
                            @RequestParam(required = false) String opt2,
                            @RequestParam(required = false) String opt3,
                            @RequestParam(required = false) String opt4,
                            @RequestParam(required = false) String answer,
                            @RequestParam(required = false) String explanation,
                            @RequestParam(required = false) String audio,
                            @RequestParam(required = false) String script,
                            @RequestParam(required = false) String passage,
                            @RequestParam(required = false) String image,
                            @RequestParam(required = false) String keys1,
                            @RequestParam(required = false) String keys2,
                            @RequestParam(required = false) String chart,
                            @RequestParam(required = false) String modelAnswer,
                            @RequestParam(required = false) String rubric,
                            @RequestParam(required = false) MultipartFile questionImage,
                            @RequestParam(defaultValue = "false") boolean andNext,
                            HttpSession session, RedirectAttributes ra) {
        String key = writing ? "WRITING-" + no : (skill == null ? "WRITING-" + no : skill.name() + "-" + no);
        return mutate(session, ra, "/content/sources/" + id + "?slot=" + key + "#paper", actor -> {
            String savedChart = questionImage != null && !questionImage.isEmpty()
                    ? importService.uploadQuestionImage(id, questionImage, actor) : chart;
            importService.savePaper(id, new ExamImportService.PaperForm(writing, skill, no, prompt,
                    opt1, opt2, opt3, opt4, answer, explanation, audio, script, passage, image,
                    keys1, keys2, savedChart, modelAnswer, rubric), actor);
            String stay = key;
            if (andNext) {
                List<ExamImportService.PaperSlot> slots = importService.paper(id);
                for (int i = 0; i < slots.size(); i++) {
                    if (slots.get(i).key().equals(key) && i + 1 < slots.size()) {
                        stay = slots.get(i + 1).key();
                        break;
                    }
                }
            }
            ra.addFlashAttribute("flashOk", andNext
                    ? "Đã lưu. Các câu khác giữ nguyên. Đang mở câu tiếp theo."
                    : "Đã lưu câu này. Các câu khác giữ nguyên.");
            return "redirect:/content/sources/" + id + "?slot=" + stay + "#paper";
        });
    }

    private static List<String> values(String[] raw) { return raw == null ? List.of() : java.util.Arrays.asList(raw); }

    @PostMapping("/sources/{id}/cards")
    public String saveCard(@PathVariable Long id,
                           @RequestParam(required = false) String cardId,
                           @RequestParam(required = false) String templateCode,
                           @RequestParam(required = false) String typeCode,
                           @RequestParam(required = false) String audio,
                           @RequestParam(required = false) String image,
                           @RequestParam(required = false) String passage,
                           @RequestParam(required = false) String script,
                           @RequestParam(required = false) String scriptVi,
                           @RequestParam(required = false) List<Integer> qno,
                           @RequestParam(required = false) MultipartFile questionImage,
                           @RequestParam(required = false) List<MultipartFile> opt1Image,
                           @RequestParam(required = false) List<MultipartFile> opt2Image,
                           @RequestParam(required = false) List<MultipartFile> opt3Image,
                           @RequestParam(required = false) List<MultipartFile> opt4Image,
                           jakarta.servlet.http.HttpServletRequest request, HttpSession session, RedirectAttributes ra) {
        return mutate(session, ra, "/content/sources/" + id + "#paper", actor -> {
            List<String> charts = values(request.getParameterValues("chart"));
            if (questionImage != null && !questionImage.isEmpty()) {
                // Cho phép ảnh đề bài cho cả câu Nghe/Đọc và Viết
                if (qno == null || qno.size() != 1)
                    throw new ExamDomainException("Vui lòng chọn đúng một câu khi tải ảnh đề bài.");
                charts = List.of(importService.uploadQuestionImage(id, questionImage, actor));
            }
            List<String> opt1Imgs = processOptionImages(id, values(request.getParameterValues("opt1Img")), opt1Image, actor);
            List<String> opt2Imgs = processOptionImages(id, values(request.getParameterValues("opt2Img")), opt2Image, actor);
            List<String> opt3Imgs = processOptionImages(id, values(request.getParameterValues("opt3Img")), opt3Image, actor);
            List<String> opt4Imgs = processOptionImages(id, values(request.getParameterValues("opt4Img")), opt4Image, actor);
            String saved = importService.saveCard(id, new ExamImportService.CardForm(cardId, templateCode, audio, image,
                    passage, script, scriptVi, values(request.getParameterValues("prompt")), values(request.getParameterValues("opt1")), values(request.getParameterValues("opt2")),
                    values(request.getParameterValues("opt3")), values(request.getParameterValues("opt4")), values(request.getParameterValues("answer")), values(request.getParameterValues("explanation")),
                    values(request.getParameterValues("keys1")), values(request.getParameterValues("keys2")), values(request.getParameterValues("modelAnswer")), values(request.getParameterValues("rubric")),
                    charts,
                    opt1Imgs, opt2Imgs, opt3Imgs, opt4Imgs,
                    typeCode, qno), actor);

            ExamImportService.CardView view = importService.cards(id).stream()
                    .filter(c -> c.id().equals(saved)).findFirst().orElse(null);
            String skillName = view == null || view.skillName().isBlank() ? "LISTENING" : view.skillName();
            Integer focus = view == null || view.nos().isEmpty() ? null : view.nos().get(0);
            ra.addFlashAttribute("flashOk", "Đã lưu câu này. Các câu khác giữ nguyên.");
            return "redirect:/content/sources/" + id + "?skill=" + skillName + "&card=" + saved
                    + (focus == null ? "" : "&focus=" + focus) + "#paper";
        });
    }

    private List<String> processOptionImages(Long sourceId, List<String> existingNames, List<MultipartFile> files, Actor actor) throws IOException {
        if (files == null || files.isEmpty()) return existingNames;
        List<String> result = new ArrayList<>();
        int max = Math.max(existingNames != null ? existingNames.size() : 0, files.size());
        for (int i = 0; i < max; i++) {
            String existing = (existingNames != null && i < existingNames.size()) ? existingNames.get(i) : "";
            MultipartFile file = (i < files.size()) ? files.get(i) : null;
            if (file != null && !file.isEmpty()) {
                result.add(importService.uploadQuestionImage(sourceId, file, actor));
            } else {
                result.add(existing != null ? existing : "");
            }
        }
        return result;
    }

    @PostMapping("/sources/{id}/cards/delete")
    public String deleteCard(@PathVariable Long id, @RequestParam String cardId,
                             HttpSession session, RedirectAttributes ra) {
        return mutate(session, ra, "/content/sources/" + id, actor -> {
            importService.deleteCard(id, cardId, actor);
            ra.addFlashAttribute("flashOk", "Đã xóa câu. Các câu khác giữ nguyên.");
            return "redirect:/content/sources/" + id + "#paper";
        });
    }

    @GetMapping("/sources/{id}")
    public String sourceDetail(@PathVariable Long id, @RequestParam(required = false) String slot,
                               @RequestParam(required = false) String card,
                               @RequestParam(required = false) String skill,
                               @RequestParam(required = false) String type,
                               @RequestParam(required = false) List<Integer> nos,
                               @RequestParam(required = false) Integer focus,
                               HttpSession session, Model model, RedirectAttributes ra) {
        UserDTO u = viewer(session);
        if (u == null) return denied(session);
        ExamSource s;
        try {
            s = importService.getSource(id);
        } catch (ExamDomainException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/content/sources";
        }
        common(model, u, s.getName());
        if (Actor.ROLE_CONTENT.equalsIgnoreCase(u.getRole()) && s.getStatus() != SourceStatus.ACTIVE
                && importService.latestJob(id).isEmpty()) {
            importService.startManual(id, Actor.of(u));
        }
        Optional<ImportJob> job = importService.latestJob(id);
        List<ImportIssue> issues = job.map(importService::currentIssues).orElse(List.of());
        List<MediaAsset> media = importService.sharedMediaOf(id);
        model.addAttribute("source", s);
        model.addAttribute("job", job.orElse(null));
        model.addAttribute("bankActivated", job.map(j -> j.getStatus() == ImportJobStatus.ACTIVATED).orElse(false));
        model.addAttribute("issues", issues);
        model.addAttribute("errors", issues.stream().filter(ImportIssue::isError).toList());
        model.addAttribute("warnings", issues.stream().filter(i -> !i.isError()).toList());
        model.addAttribute("errorCount", issues.stream().filter(ImportIssue::isError).count());
        model.addAttribute("warningCount", issues.stream().filter(i -> !i.isError()).count());
        model.addAttribute("media", media);
        model.addAttribute("audioCount", media.stream().filter(m -> m.getKind() == MediaKind.AUDIO).count());
        model.addAttribute("imageCount", media.stream().filter(m -> m.getKind() == MediaKind.IMAGE).count());
        model.addAttribute("history", importService.jobHistory(id));
        model.addAttribute("fullCount", QuestionTypeCatalog.fullExamCount(s.getTrack()));
        boolean manual = job.map(importService::isManual).orElse(false);
        model.addAttribute("manual", manual);
        if (manual) {
            try {
                fillManualEntry(model, s, importService.cards(id), card, skill, type, nos, focus, issues);
            } catch (ExamDomainException e) {
                ra.addFlashAttribute("flashError", e.getMessage());
                return "redirect:/content/sources";
            }
        }
        List<ExamImportService.PaperSlot> slots = manual ? List.of() : importService.paper(id);
        if (manual) {
            model.addAttribute("sheetIssues", List.of());
            if (s.getStatus() == SourceStatus.ACTIVE) {
                model.addAttribute("createdQuestions", importService.questionsOf(id));
                model.addAttribute("createdWriting", importService.writingOf(id));
                model.addAttribute("sourceExams", composer.library("DRAFT", null, null).stream()
                        .filter(e -> e.getSource() != null && e.getSource().getId().equals(id)).toList());
            }
            return "content/exam/source-detail";
        }
        ExamImportService.PaperSlot edit = slots.stream().filter(p -> p.key().equals(slot)).findFirst()
                .orElseGet(() -> slots.stream().filter(p -> !p.filled()).findFirst().orElse(slots.isEmpty() ? null : slots.get(0)));
        int at = edit == null ? -1 : slots.indexOf(edit);
        model.addAttribute("slots", slots);
        model.addAttribute("editSlot", edit);
        QuestionTypeDef slotType = edit == null || edit.writing()
                ? (edit == null ? null : QuestionTypeCatalog.resolve(Track.TOPIK_II, Skill.WRITING, edit.no()).orElse(null))
                : QuestionTypeCatalog.resolve(s.getTrack(), edit.skill(), edit.no()).orElse(null);
        model.addAttribute("slotGuideKo", slotType == null ? "" : slotType.instructionKo());
        model.addAttribute("slotGuideName", slotType == null ? "" : slotType.code() + " · " + slotType.name());
        model.addAttribute("slotGuidePoints", QuestionPoints.line(slotType));
        model.addAttribute("slotGuideMark", QuestionPoints.mark(slotType));
        model.addAttribute("prevKey", at > 0 ? slots.get(at - 1).key() : null);
        model.addAttribute("nextKey", at >= 0 && at + 1 < slots.size() ? slots.get(at + 1).key() : null);
        model.addAttribute("filledSlots", slots.stream().filter(ExamImportService.PaperSlot::filled).count());
        model.addAttribute("sheetIssues", issues.stream()
                .filter(i -> i.no() == null || (i.message() != null && i.message().startsWith("Không đủ số câu")))
                .toList());
        if (s.getStatus() == SourceStatus.ACTIVE) {
            model.addAttribute("createdQuestions", importService.questionsOf(id));
            model.addAttribute("createdWriting", importService.writingOf(id));
            model.addAttribute("sourceExams", composer.library("DRAFT", null, null).stream()
                    .filter(e -> e.getSource() != null && e.getSource().getId().equals(id)).toList());
        }
        return "content/exam/source-detail";
    }

    @GetMapping("/sources/{id}/preview")
    public String previewPaper(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        UserDTO u = viewer(session);
        if (u == null) return denied(session);
        ExamSource s;
        try {
            s = importService.getSource(id);
        } catch (ExamDomainException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/content/sources";
        }
        common(model, u, "Xem đề · " + s.getName());
        if (Actor.ROLE_CONTENT.equalsIgnoreCase(u.getRole()) && s.getStatus() != SourceStatus.ACTIVE
                && importService.latestJob(id).isEmpty()) {
            importService.startManual(id, Actor.of(u));
        }
        List<ExamImportService.LearnerBai> bais = importService.learnerBais(id);
        model.addAttribute("source", s);
        model.addAttribute("bais", bais);
        java.util.List<com.hottopik.dto.QuestionPlayerItem> playerQuestions = new java.util.ArrayList<>();
        for (var bai : bais) {
            if (!bai.writing()) for (var ask : bai.asks()) {
                playerQuestions.add(com.hottopik.dto.QuestionPlayerItem.preview(bai, ask, id, playerQuestions.size()));
            }
        }
        model.addAttribute("playerQuestions", playerQuestions);
        model.addAttribute("listeningCount", bais.stream().filter(b -> b.skill() == Skill.LISTENING).count());
        model.addAttribute("readingCount", bais.stream().filter(b -> b.skill() == Skill.READING).count());
        return "content/exam/preview";
    }

    /* =========================================================================================
     * LUỒNG CHỨC NĂNG: NẠP ĐỀ TỪ EXCEL VÀ VALIDATE CẤU TRÚC (EXAM EXCEL IMPORT - FUNC_EXAM_02)
     * -----------------------------------------------------------------------------------------
     * 1. THAO TÁC NGƯỜI DÙNG & UI/HTML:
     *    - Content Manager ở màn hình chi tiết nguồn đề (/content/sources/{id}#paper).
     *    - Kéo thả file Excel (.xlsx) chứa câu hỏi vào form Upload hoặc chọn file từ máy tính.
     *    - Nhấn nút "Tải lên & Kiểm tra cú pháp".
     * 2. HTTP REQUEST & ENDPOINT:
     *    - URL: POST /content/sources/{id}/excel
     *    - Headers: Content-Type: multipart/form-data
     *    - Payload: MultipartFile file
     * 3. CONTROLLER & SERVICE XỬ LÝ:
     *    - Controller: com.hottopik.controller.ContentExamController.uploadExcel()
     *    - Triệu gọi Service:
     *      + com.hottopik.service.exam.ExamImportService.uploadExcel(id, file, actor):
     *        * com.hottopik.service.exam.ExamWorkbookParser.parse(): Phân tích các sheet MCQ, Writing, Group, Audio.
     *        * com.hottopik.service.exam.ExamImportValidator.validate(): Kiểm tra định dạng số câu, đáp án hợp lệ,
     *          mã dạng TOPIK, liên kết đoạn văn, file audio đính kèm.
     *        * Lưu trạng thái công việc nạp đề (ImportJob) cùng chi tiết cảnh báo/lỗi (nếu có).
     * 4. TRUY VẤN CSDL:
     *    - INSERT INTO dbo.import_jobs (source_id, status, mcq_rows, writing_rows, error_count, warning_count...)
     *    - INSERT/UPDATE dbo.staging_questions hoặc cập nhật nháp cho exam_sources.
     * 5. HIỂN THỊ KẾT QUẢ CHO QUẢN LÝ:
     *    - Redirect: 302 về /content/sources/{id}#paper
     *    - Flash Message hiển thị số lượng câu trắc nghiệm, câu Viết đã đọc thành công và danh sách lỗi (nếu có)
     *      để Content Manager chỉnh sửa trực tiếp trên giao diện hoặc re-upload.
     * ========================================================================================= */
    @PostMapping("/sources/{id}/excel")
    public String uploadExcel(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                              HttpSession session, RedirectAttributes ra) {
        String back = "/content/sources/" + id;
        return mutate(session, ra, back, actor -> {
            ImportJob job = importService.uploadExcel(id, file, actor);
            if (job.getErrorCount() > 0) {
                ra.addFlashAttribute("flashInfo", "Đã đọc " + job.getMcqRows() + " câu trắc nghiệm, " + job.getWritingRows()
                        + " câu Viết: còn " + job.getErrorCount() + " lỗi cần sửa.");
            } else {
                ra.addFlashAttribute("flashOk", "Đã đọc " + job.getMcqRows() + " câu trắc nghiệm, " + job.getWritingRows()
                        + " câu Viết: không có lỗi chặn" + (job.getWarningCount() > 0 ? ", " + job.getWarningCount() + " cảnh báo." : "."));
            }
            return "redirect:" + back + "#paper";
        });
    }

    @PostMapping("/sources/{id}/excel/skill")
    public String uploadSkillExcel(@PathVariable Long id, @RequestParam Skill skill,
                                   @RequestParam("file") MultipartFile file,
                                   HttpSession session, RedirectAttributes ra) {
        String back = "/content/sources/" + id;
        return mutate(session, ra, back, actor -> {
            ImportJob job = importService.uploadSkillExcel(id, skill, file, actor);
            if (job.getErrorCount() > 0) {
                ra.addFlashAttribute("flashInfo", "Đã nạp file kỹ năng " + skill.getLabel() + ": hiện có "
                        + job.getMcqRows() + " câu trắc nghiệm, " + job.getWritingRows()
                        + " câu Viết (còn " + job.getErrorCount() + " lỗi cần sửa).");
            } else {
                ra.addFlashAttribute("flashOk", "Đã nạp file kỹ năng " + skill.getLabel() + ": hiện có "
                        + job.getMcqRows() + " câu trắc nghiệm, " + job.getWritingRows()
                        + " câu Viết (không có lỗi chặn" + (job.getWarningCount() > 0 ? ", " + job.getWarningCount() + " cảnh báo." : ")."));
            }
            return "redirect:" + back + "?skill=" + skill.name() + "#paper";
        });
    }

    @PostMapping(value = "/sources/{id}/excel/pre-check", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> preCheckExcel(@PathVariable Long id,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(value = "skill", required = false) Skill skill,
                                           HttpSession session) {
        UserDTO u = AuthHelper.getLoggedInUser(session);
        if (u == null || !Actor.ROLE_CONTENT.equalsIgnoreCase(u.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "message", "Bạn không có quyền quản lý nội dung."));
        }
        try {
            ExamImportService.ImportPreCheckReport report = importService.preCheckExcel(id, skill, file, Actor.of(u));
            return ResponseEntity.ok(report);
        } catch (ExamDomainException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("ok", false, "message", "Lỗi đọc file: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/sources/{id}/excel/confirm", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> confirmImportExcel(@PathVariable Long id,
                                                @RequestParam("token") String token,
                                                HttpSession session) {
        UserDTO u = AuthHelper.getLoggedInUser(session);
        if (u == null || !Actor.ROLE_CONTENT.equalsIgnoreCase(u.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "message", "Bạn không có quyền quản lý nội dung."));
        }
        try {
            ExamImportService.ConfirmResult result = importService.confirmImport(id, token, Actor.of(u));
            Skill skill = result.skill();
            int total = result.totalRows();
            String skillLabel = skill != null ? skill.getLabel() : "toàn bộ đề";
            String msg = "Đã nhập thành công " + total + " câu (" + skillLabel + ") vào hệ thống.";
            String redirectUrl = "/content/sources/" + id + (skill != null ? ("?skill=" + skill.name()) : "") + "#paper";
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "message", msg,
                    "count", total,
                    "skill", skill != null ? skill.name() : "",
                    "redirectUrl", redirectUrl
            ));
        } catch (ExamDomainException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("ok", false, "message", "Lỗi khi nhập đề: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/sources/{id}/excel/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> cancelImportExcel(@PathVariable Long id,
                                               @RequestParam("token") String token,
                                               HttpSession session) {
        importService.cancelTempImport(token);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping(value = "/sources/{id}/listening/bulk-audio/pre-check", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> preCheckBulkAudio(@PathVariable Long id,
                                               @RequestParam("file") MultipartFile file,
                                               HttpSession session) {
        UserDTO u = AuthHelper.getLoggedInUser(session);
        if (u == null || !Actor.ROLE_CONTENT.equalsIgnoreCase(u.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "message", "Bạn không có quyền quản lý nội dung."));
        }
        try {
            ExamImportService.BulkAudioPreCheckReport report = importService.preCheckBulkAudio(id, file, Actor.of(u));
            return ResponseEntity.ok(report);
        } catch (ExamDomainException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("ok", false, "message", "Lỗi kiểm tra file audio: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/sources/{id}/listening/bulk-audio/confirm", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> confirmBulkAudio(@PathVariable Long id,
                                              @RequestParam("token") String token,
                                              HttpSession session) {
        UserDTO u = AuthHelper.getLoggedInUser(session);
        if (u == null || !Actor.ROLE_CONTENT.equalsIgnoreCase(u.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "message", "Bạn không có quyền quản lý nội dung."));
        }
        try {
            ExamImportService.BulkAudioConfirmResult result = importService.confirmBulkAudio(id, token, Actor.of(u));
            return ResponseEntity.ok(result);
        } catch (ExamDomainException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("ok", false, "message", "Lỗi khi lưu audio: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/sources/{id}/listening/bulk-audio/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> cancelBulkAudio(@PathVariable Long id,
                                             @RequestParam("token") String token,
                                             HttpSession session) {
        UserDTO u = AuthHelper.getLoggedInUser(session);
        if (u == null || !Actor.ROLE_CONTENT.equalsIgnoreCase(u.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "message", "Bạn không có quyền quản lý nội dung."));
        }
        try {
            importService.cancelBulkAudio(token);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "message", e.getMessage()));
        }
    }

    @PostMapping(value = "/sources/{id}/media/inline", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> uploadInline(@PathVariable Long id, @RequestParam("files") List<MultipartFile> files,
                                            HttpSession session) throws IOException {
        UserDTO u = AuthHelper.getLoggedInUser(session);
        if (u == null || !Actor.ROLE_CONTENT.equalsIgnoreCase(u.getRole())) {
            return Map.of("ok", false, "message", "Bạn không có quyền tải file.");
        }
        ExamImportService.MediaUploadReport report = importService.uploadMedia(id, files, Actor.of(u));
        if (report.stored() < 1) {
            String message = report.rejected().isEmpty() ? "Không nhận được file." : String.join("; ", report.rejected());
            return Map.of("ok", false, "message", message);
        }
        String filename = MediaStorageService.baseName(files.get(0).getOriginalFilename());
        boolean audio = MediaStorageService.isAudio(filename);
        String url = "/content/media/" + id + "/"
                + java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return Map.of("ok", true, "filename", filename, "kind", audio ? "AUDIO" : "IMAGE", "url", url);
    }

    @PostMapping("/sources/{id}/media")
    public String uploadMedia(@PathVariable Long id, @RequestParam("files") List<MultipartFile> files,
                              HttpSession session, RedirectAttributes ra) {
        String back = "/content/sources/" + id;
        return mutate(session, ra, back, actor -> {
            ExamImportService.MediaUploadReport r = importService.uploadMedia(id, files, actor);
            ra.addFlashAttribute(r.rejected().isEmpty() ? "flashOk" : "flashInfo", "Đã lưu " + r.stored() + " file."
                    + (r.rejected().isEmpty() ? "" : " Không nhận: " + String.join("; ", r.rejected())));
            return "redirect:" + back + "#media";
        });
    }

    @PostMapping("/sources/{id}/media/{mediaId}/delete")
    public String deleteMedia(@PathVariable Long id, @PathVariable Long mediaId, HttpSession session, RedirectAttributes ra) {
        String back = "/content/sources/" + id;
        return mutate(session, ra, back, actor -> {
            importService.deleteMedia(id, mediaId, actor);
            ra.addFlashAttribute("flashOk", "Đã xoá file.");
            return "redirect:" + back + "#tools";
        });
    }

    @PostMapping("/sources/{id}/media/delete-batch")
    public String deleteMediaBatch(@PathVariable Long id,
                                   @RequestParam(value = "mediaIds", required = false) List<Long> mediaIds,
                                   HttpSession session, RedirectAttributes ra) {
        String back = "/content/sources/" + id;
        return mutate(session, ra, back, actor -> {
            int count = importService.deleteMediaBatch(id, mediaIds, actor);
            ra.addFlashAttribute("flashOk", "Đã xoá " + count + " file đính kèm.");
            return "redirect:" + back + "#tools";
        });
    }

    @PostMapping("/sources/{id}/media/delete-all")
    public String deleteAllMedia(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        String back = "/content/sources/" + id;
        return mutate(session, ra, back, actor -> {
            int count = importService.deleteAllMedia(id, actor);
            ra.addFlashAttribute("flashOk", "Đã xoá toàn bộ " + count + " file đính kèm.");
            return "redirect:" + back + "#tools";
        });
    }

    @PostMapping(value = "/sources/{id}/media/clear-card-audio", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> clearCardAudio(@PathVariable Long id,
                                            @RequestParam String filename,
                                            @RequestParam(required = false) String cardId,
                                            HttpSession session) {
        UserDTO u = viewer(session);
        if (u == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false));
        try {
            boolean deleted = importService.clearCardAudio(id, cardId, filename, Actor.of(u));
            return ResponseEntity.ok(Map.of("ok", true, "deletedMedia", deleted));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/sources/{id}/validate")
    public String revalidate(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        String back = "/content/sources/" + id;
        return mutate(session, ra, back, actor -> {
            ImportJob job = importService.revalidate(id, actor);
            ra.addFlashAttribute(job.getErrorCount() > 0 ? "flashInfo" : "flashOk",
                    "Đã kiểm tra lại: " + job.getErrorCount() + " lỗi, " + job.getWarningCount() + " cảnh báo.");
            return "redirect:" + back + "#check";
        });
    }

    /* =========================================================================================
     * LUỒNG CHỨC NĂNG: KÍCH HOẠT NGUỒN ĐỀ VÀO NGÂN HÀNG CÂU HỎI (EXAM ACTIVATION - FUNC_EXAM_03)
     * -----------------------------------------------------------------------------------------
     * 1. THAO TÁC NGƯỜI DÙNG & UI/HTML:
     *    - Sau khi hoàn thành sửa lỗi và kiểm tra đạt 0 lỗi chặn, Content Manager nhấn nút "Kích hoạt vào ngân hàng".
     * 2. HTTP REQUEST & ENDPOINT:
     *    - URL: POST /content/sources/{id}/activate
     *    - Headers: Content-Type: application/x-www-form-urlencoded
     * 3. CONTROLLER & SERVICE XỬ LÝ:
     *    - Controller: com.hottopik.controller.ContentExamController.activate()
     *    - Triệu gọi Service:
     *      + com.hottopik.service.exam.ExamImportService.activate(id, actor):
     *        * Chuyển các câu trắc nghiệm từ nguồn đề vào bảng chính thức dbo.questions (trạng thái ACTIVE).
     *        * Chuyển các đề bài viết vào bảng dbo.writing_prompts (trạng thái ACTIVE).
     *        * Tạo bản ghi bài thi hoàn chỉnh trong thư viện đề thi dbo.exams (nếu đủ số câu chuẩn).
     * 4. TRUY VẤN CSDL:
     *    - INSERT INTO dbo.questions (group_id, no, prompt, options_json, answer_index, explanation_vi, status...)
     *    - INSERT INTO dbo.writing_prompts (...)
     *    - UPDATE dbo.exam_sources SET status = 'ACTIVE' WHERE id = :id
     *    - INSERT INTO dbo.exams (...)
     * 5. HIỂN THỊ KẾT QUẢ CHO QUẢN LÝ:
     *    - Nếu tạo bài thi: Redirect sang /content/exams/{examId} để xem trước và gửi duyệt phát hành.
     *    - Nếu nạp câu lẻ vào ngân hàng: Redirect về /content/sources/{id} với thông báo thành công:
     *      "Đã đưa X câu và Y đề Viết vào ngân hàng."
     * ========================================================================================= */
    @PostMapping("/sources/{id}/activate")
    public String activate(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        String back = "/content/sources/" + id;
        return mutate(session, ra, back, actor -> {
            ExamImportService.ActivationResult r = importService.activate(id, actor);
            ra.addFlashAttribute("flashOk", "Đã đưa " + r.questionCount() + " câu và " + r.writingCount()
                    + " đề Viết vào ngân hàng" + (r.examId() != null ? ". Đề đã mở trong thư viện, gửi duyệt để phát hành." : "."));
            return r.examId() != null ? "redirect:/content/exams/" + r.examId() : "redirect:" + back;
        });
    }

    @PostMapping("/sources/{id}/status")
    public String updateSourceStatus(@PathVariable Long id, @RequestParam SourceStatus status,
                                     @RequestParam(required = false) String back,
                                     HttpSession session, RedirectAttributes ra) {
        if (status == SourceStatus.ACTIVE
                && importService.latestJob(id).map(j -> j.getStatus() != ImportJobStatus.ACTIVATED).orElse(true)) {
            return activate(id, session, ra);
        }
        String target = (back != null && !back.isBlank()) ? back : "/content/sources";
        return mutate(session, ra, target, actor -> {
            importService.updateSourceStatus(id, status, actor);
            ra.addFlashAttribute("flashOk", "Đã đổi trạng thái nguồn đề thành: "
                    + (status == SourceStatus.ACTIVE ? "Đã kích hoạt" : "Bản nháp"));
            return "redirect:" + target;
        });
    }

    @PostMapping("/sources/{id}/delete")
    public String deleteSource(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        return mutate(session, ra, "/content/sources/" + id, actor -> {
            importService.deleteDraftSource(id, actor);
            ra.addFlashAttribute("flashOk", "Đã xoá nguồn đề nháp.");
            return "redirect:/content/sources";
        });
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template(@RequestParam(required = false) Track track,
                                           @RequestParam(required = false) Skill skill,
                                           HttpSession session) throws IOException {
        if (viewer(session) == null) return ResponseEntity.status(403).build();
        if (track != null && skill != null) {
            String skillTag = switch (skill) {
                case LISTENING -> "Nghe";
                case READING -> "Doc";
                case WRITING -> "Viet";
            };
            String filename = "HotTopik_Mau_" + track.name() + "_" + skillTag + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(filename, StandardCharsets.UTF_8).build().toString())
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(ExamTemplateBuilder.buildSkillTemplate(track, skill));
        }
        Track t = track != null ? track : Track.TOPIK_II;
        String filename = "HotTopik_Mau_Toan_Bo_" + t.name() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(ExamTemplateBuilder.buildFullTemplate(t));
    }

    @GetMapping("/sources/{id}/template")
    public ResponseEntity<byte[]> sourceTemplate(@PathVariable Long id,
                                                 @RequestParam Skill skill,
                                                 HttpSession session) throws IOException {
        if (viewer(session) == null) return ResponseEntity.status(403).build();
        ExamSource source = importService.getSource(id);
        String skillTag = switch (skill) {
            case LISTENING -> "Nghe";
            case READING -> "Doc";
            case WRITING -> "Viet";
        };
        String filename = "HotTopik_Mau_" + source.getTrack().name() + "_" + skillTag + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(ExamTemplateBuilder.buildSkillTemplate(source.getTrack(), skill));
    }

    @GetMapping("/sources/{id}/template-full")
    public ResponseEntity<byte[]> sourceFullTemplate(@PathVariable Long id,
                                                     HttpSession session) throws IOException {
        if (viewer(session) == null) return ResponseEntity.status(403).build();
        ExamSource source = importService.getSource(id);
        String filename = "HotTopik_Mau_Toan_Bo_" + source.getTrack().name() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(ExamTemplateBuilder.buildFullTemplate(source.getTrack()));
    }

    @GetMapping({"/media/{sourceId}/{filename:.+}", "/media/{sourceId}/**"})
    public void media(@PathVariable Long sourceId,
                      @PathVariable(required = false) String filename,
                      @RequestHeader(value = "Range", required = false) String range,
                      jakarta.servlet.http.HttpServletRequest request,
                      HttpSession session, HttpServletResponse response) throws IOException {
        if (viewer(session) == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (filename == null || filename.isBlank()) {
            String uri = request.getRequestURI();
            String prefix = "/content/media/" + sourceId + "/";
            int idx = uri.indexOf(prefix);
            if (idx >= 0) {
                filename = uri.substring(idx + prefix.length());
            }
        }
        Optional<MediaAsset> a = importService.findMedia(sourceId, filename);
        if (a.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path p = importService.mediaPath(a.get());
        if (!Files.exists(p)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        MediaResponses.play(p, filename, a.get().getKind(), range, response);
    }

    // ------------------------------------------------------------------ thư viện đề

    @GetMapping("/exams")
    public String library(@RequestParam(defaultValue = "PUBLISHED") String tab,
                          @RequestParam(required = false) ExamKind kind,
                          @RequestParam(required = false) Track track,
                          HttpSession session, Model model) {
        UserDTO u = viewer(session);
        if (u == null) return denied(session);
        common(model, u, "Thư viện đề");
        model.addAttribute("tab", tab);
        model.addAttribute("kind", kind);
        model.addAttribute("track", track);
        model.addAttribute("exams", composer.library(tab, kind, track));
        model.addAttribute("counts", composer.libraryCounts());
        return "content/exam/library";
    }

    @PostMapping("/exams")
    public String createExam(@RequestParam Track track, HttpSession session, RedirectAttributes ra) {
        return mutate(session, ra, "/content/exams", actor -> {
            Exam e = composer.createDraft(track, actor);
            ra.addFlashAttribute("flashOk", "Đã tạo " + e.getName() + " (nháp).");
            return "redirect:/content/exams/" + e.getId();
        });
    }

    @GetMapping("/exams/{id}")
    public String composerPage(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        UserDTO u = viewer(session);
        if (u == null) return denied(session);
        ExamComposerService.ExamFrame frame;
        try {
            frame = composer.frame(id);
        } catch (ExamDomainException e) {
            ra.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/content/exams";
        }
        Exam e = frame.exam();
        common(model, u, e.getName());
        model.addAttribute("frame", frame);
        model.addAttribute("exam", e);
        model.addAttribute("children", composer.children(id));
        if (e.getTrack() == Track.TOPIK_II) {
            Map<Integer, List<WritingPrompt>> opts = new LinkedHashMap<>();
            for (int no = 51; no <= 54; no++) opts.put(no, composer.writingOptions(no));
            model.addAttribute("writingOptions", opts);
        }
        List<Question> preview = e.getItems().stream()
                .sorted(Comparator.comparingInt((ExamItem i) -> sectionOrder(e.getTrack(), i.getSection())).thenComparingInt(ExamItem::getNo))
                .limit(5).map(ExamItem::getQuestion).toList();
        model.addAttribute("preview", preview);
        return "content/exam/composer";
    }

    private static int sectionOrder(Track track, Skill s) {
        List<QuestionTypeCatalog.Section> secs = QuestionTypeCatalog.sections(track);
        for (int i = 0; i < secs.size(); i++) if (secs.get(i).skill() == s) return i;
        return 9;
    }

    @PostMapping("/exams/{id}/autofill")
    public String autofill(@PathVariable Long id, @RequestParam(required = false) String type,
                           HttpSession session, RedirectAttributes ra) {
        String back = "/content/exams/" + id;
        return mutate(session, ra, back, actor -> {
            int n = (type == null || type.isBlank()) ? composer.autofillAll(id, actor) : composer.autofillType(id, type, actor);
            ra.addFlashAttribute(n > 0 ? "flashOk" : "flashInfo", n > 0 ? "Đã tự chọn " + n + " câu từ ngân hàng."
                    : "Không còn câu phù hợp trong ngân hàng (cùng dạng, đang dùng, chưa dùng trong đề này). Hãy nhập thêm đề.");
            return "redirect:" + back + (type != null && !type.isBlank() ? "#t-" + type : "");
        });
    }

    @PostMapping("/exams/{id}/clear")
    public String clear(@PathVariable Long id, @RequestParam String type, @RequestParam(required = false) Integer firstNo,
                        HttpSession session, RedirectAttributes ra) {
        String back = "/content/exams/" + id;
        return mutate(session, ra, back, actor -> {
            if (firstNo != null) composer.clearBlock(id, type, firstNo, actor);
            else composer.clearType(id, type, actor);
            return "redirect:" + back + "#t-" + type;
        });
    }

    @GetMapping("/exams/{id}/candidates")
    @ResponseBody
    public ResponseEntity<?> candidates(@PathVariable Long id, @RequestParam String type, @RequestParam int firstNo,
                                        HttpSession session) {
        if (viewer(session) == null) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(composer.candidates(id, type, firstNo));
        } catch (ExamDomainException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/exams/{id}/assign")
    public String assign(@PathVariable Long id, @RequestParam String type, @RequestParam int firstNo,
                         @RequestParam Long groupId, HttpSession session, RedirectAttributes ra) {
        String back = "/content/exams/" + id;
        return mutate(session, ra, back, actor -> {
            composer.assignBlock(id, type, firstNo, groupId, actor);
            ra.addFlashAttribute("flashOk", "Đã chọn câu cho " + type + " câu " + firstNo + ".");
            return "redirect:" + back + "#t-" + type;
        });
    }

    @PostMapping("/exams/{id}/writing")
    public String writing(@PathVariable Long id, @RequestParam Map<String, String> params,
                          HttpSession session, RedirectAttributes ra) {
        String back = "/content/exams/" + id;
        return mutate(session, ra, back, actor -> {
            Map<Integer, Long> ids = new HashMap<>();
            for (int no = 51; no <= 54; no++) {
                String v = params.get("w" + no);
                if (v != null && !v.isBlank()) ids.put(no, Long.valueOf(v));
            }
            composer.setWriting(id, ids, actor);
            ra.addFlashAttribute("flashOk", "Đã lưu bộ đề Viết 51–54.");
            return "redirect:" + back + "#writing";
        });
    }

    @PostMapping("/exams/{id}/settings")
    public String settings(@PathVariable Long id, @RequestParam(required = false) String name,
                           @RequestParam(required = false) Integer listeningMinutes,
                           @RequestParam(required = false) Integer writingMinutes,
                           @RequestParam(required = false) Integer readingMinutes,
                           HttpSession session, RedirectAttributes ra) {
        String back = "/content/exams/" + id;
        return mutate(session, ra, back, actor -> {
            composer.updateSettings(id, name, listeningMinutes, writingMinutes, readingMinutes, actor);
            ra.addFlashAttribute("flashOk", "Đã lưu tên và thời gian.");
            return "redirect:" + back;
        });
    }

    @PostMapping("/exams/{id}/submit")
    public String submit(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        String back = "/content/exams/" + id;
        return mutate(session, ra, back, actor -> {
            composer.submitForPublish(id, actor);
            ra.addFlashAttribute("flashOk", "Đã gửi duyệt phát hành. Khi Admin duyệt, đề hiện trong Thư viện đề của học viên và hệ thống tự sinh Mini Test.");
            return "redirect:" + back;
        });
    }

    @PostMapping("/exams/{id}/publish")
    public String publishExam(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        String back = "/content/exams/" + id;
        return mutate(session, ra, back, actor -> {
            composer.publishDirectly(id, actor);
            ra.addFlashAttribute("flashOk", "Da kich hoat phat hanh de thanh cong! De da hien trong Thu vien de cua hoc vien va he thong tu sinh Mini Test.");
            return "redirect:" + back;
        });
    }

    @PostMapping("/exams/{id}/clone")
    public String cloneExam(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        return mutate(session, ra, "/content/exams/" + id, actor -> {
            Exam c = composer.cloneExam(id, actor);
            ra.addFlashAttribute("flashOk", "Đã nhân bản thành " + c.getName() + " (nháp).");
            return "redirect:/content/exams/" + c.getId();
        });
    }

    @PostMapping("/exams/{id}/archive")
    public String archive(@PathVariable Long id, @RequestParam String reason, HttpSession session, RedirectAttributes ra) {
        String back = "/content/exams/" + id;
        return mutate(session, ra, back, actor -> {
            composer.requestArchive(id, reason, actor);
            ra.addFlashAttribute("flashOk", "Đã gửi yêu cầu lưu trữ tới Hộp duyệt.");
            return "redirect:" + back;
        });
    }

    @PostMapping("/exams/{id}/delete")
    public String deleteExam(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        return mutate(session, ra, "/content/exams/" + id, actor -> {
            composer.deleteDraft(id, actor);
            ra.addFlashAttribute("flashOk", "Đã xoá bản nháp.");
            return "redirect:/content/exams?tab=DRAFT";
        });
    }

    // ------------------------------------------------------------------ ngân hàng câu hỏi

    @GetMapping("/questions")
    public String questions(@RequestParam(required = false) Track track, @RequestParam(required = false) Skill skill,
                            @RequestParam(required = false) String type, @RequestParam(required = false) Integer zone,
                            @RequestParam(required = false) Long source, @RequestParam(required = false) QuestionPurpose purpose,
                            @RequestParam(required = false) String difficulty, @RequestParam(required = false) QuestionStatus status,
                            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                            HttpSession session, Model model) {
        UserDTO u = viewer(session);
        if (u == null) return denied(session);
        common(model, u, "Ngân hàng câu hỏi");
        QuestionBankService.Filter f = new QuestionBankService.Filter(track, skill, type, null, source, purpose, null, status, q);
        Page<Question> result = bank.search(f, skill == Skill.WRITING ? 0 : page);
        model.addAttribute("f", f);
        model.addAttribute("result", result);
        model.addAttribute("writings", bank.writings(f));
        model.addAttribute("counts", bank.counts());
        model.addAttribute("sources", importService.listSources());
        model.addAttribute("typeOptions", templates.list(track, skill));
        Map<String, String> typeNames = new HashMap<>();
        Map<String, String> templateOfType = new HashMap<>();
        for (QuestionTemplate template : templates.list(null, null)) typeNames.put(template.getCode(), template.getName());
        QuestionTypeCatalog.all().forEach(t -> {
            String mold = QuestionTemplateCatalog.templateCode(t.code());
            if (mold != null) templateOfType.put(t.code(), mold);
        });
        model.addAttribute("typeNames", typeNames);
        model.addAttribute("templateOfType", templateOfType);
        Map<Integer, String> writingMolds = new HashMap<>();
        for (int n = 51; n <= 54; n++) writingMolds.put(n, QuestionBankService.writingTemplate(n));
        model.addAttribute("writingMolds", writingMolds);
        return "content/exam/questions";
    }

    @GetMapping("/questions/{id}/detail")
    @ResponseBody
    public ResponseEntity<?> questionDetail(@PathVariable Long id, HttpSession session) {
        if (viewer(session) == null) return ResponseEntity.status(403).build();
        try {
            return ResponseEntity.ok(bank.detail(id));
        } catch (ExamDomainException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/writing-questions/{id}/detail")
    @ResponseBody
    public ResponseEntity<?> writingQuestionDetail(@PathVariable Long id, HttpSession session) {
        if (viewer(session) == null) return ResponseEntity.status(403).build();
        try { return ResponseEntity.ok(bank.writingDetail(id)); }
        catch (ExamDomainException ex) { return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage())); }
    }

    @PostMapping("/writing-questions/{id}")
    public String updateWritingQuestion(@PathVariable Long id, @ModelAttribute WritingPrompt form,
                                       @RequestParam(required = false) String back, HttpSession session, RedirectAttributes flash) {
        String target = safeBack(back);
        return mutate(session, flash, target, actor -> {
            bank.updateWriting(id, form, actor);
            flash.addFlashAttribute("flashOk", "Đã lưu câu Viết.");
            return "redirect:" + target;
        });
    }

    /*
     * Form ngân hàng khi sửa từ báo lỗi phải gửi reportId; kiểm tra báo lỗi OPEN đúng MC và đúng questionId trước khi cập nhật.
     * bank.update kiểm tra câu/4 phương án/đáp án/lý do; sau khi lưu, resolveReport đóng báo lỗi và tạo thông báo rồi quay tab reports.
     * Nếu có báo lỗi, updateMcAndResolve gộp sửa câu, tính lại điểm, gửi thông báo và đóng báo lỗi trong một transaction.
     */
    @PostMapping("/questions/{id}")
    public String updateQuestion(@PathVariable Long id, @RequestParam String promptKo,
                                 @RequestParam String opt1, @RequestParam String opt2,
                                 @RequestParam String opt3, @RequestParam String opt4,
                                 @RequestParam Integer answer, @RequestParam(required = false) String explanationVi,
                                 @RequestParam(required = false) String reason, @RequestParam(required = false) String back,
                                 @RequestParam(required = false) Long reportId,
                                 @RequestParam(required = false) String resolutionNote,
                                 @RequestParam(required = false) String correctionType,
                                 @RequestParam(defaultValue = "RECALCULATE") String scorePolicy,
                                 @RequestParam(required=false) String passageKo, @RequestParam(required=false) String scriptKo,
                                 @RequestParam(required=false) String imageFile, @RequestParam(required=false) String audioFile,
                                 @RequestParam(required=false) MultipartFile uploadImageFile,
                                 @RequestParam(required=false) MultipartFile uploadAudioFile,
                                 HttpSession session, RedirectAttributes ra) {
        String target = safeBack(back);
        return mutate(session, ra, target, actor -> {
            // Xử lý upload ảnh nếu có file được chọn
            String resolvedImage = imageFile;
            String resolvedAudio = audioFile;
            try {
                var questionDetail = bank.detail(id);
                Long sourceId = questionDetail != null ? (Long) questionDetail.get("sourceId") : null;
                if (sourceId != null) {
                    if (uploadImageFile != null && !uploadImageFile.isEmpty()) {
                        resolvedImage = importService.uploadMediaForBankEdit(sourceId, uploadImageFile, actor, true);
                    }
                    if (uploadAudioFile != null && !uploadAudioFile.isEmpty()) {
                        resolvedAudio = importService.uploadMediaForBankEdit(sourceId, uploadAudioFile, actor, false);
                    }
                }
            } catch (java.io.IOException e) {
                throw new ExamDomainException("Không thể upload file: " + e.getMessage());
            }
            if (reportId != null) {
                var report = reportService.getReportById(reportId).orElseThrow(() -> new ExamDomainException("Không tìm thấy báo lỗi."));
                if (reportService.isWriting(report) || !id.equals(report.getQuestionId())
                        || reportService.reportedQuestion(report) == null || !"OPEN".equals(report.getStatus())) {
                    throw new ExamDomainException("Báo lỗi không thuộc câu hỏi đang sửa.");
                }
            }
            if(reportId==null) bank.update(id, promptKo, List.of(opt1,opt2,opt3,opt4), answer, explanationVi, reason, actor, null, (correctionType==null||"AUTO".equals(correctionType))?"CONTENT":correctionType, scorePolicy,new QuestionBankService.GroupChange(passageKo,scriptKo,resolvedImage,resolvedAudio));
            if (reportId != null) {
                String note = (resolutionNote != null && !resolutionNote.isBlank()) ? resolutionNote : ("Đã chỉnh sửa nội dung câu hỏi: " + (reason != null ? reason : "Cập nhật đáp án"));
                reportService.updateMcAndResolve(id,promptKo,List.of(opt1,opt2,opt3,opt4),answer,explanationVi,reason,actor,reportId,correctionType,scorePolicy,note,new QuestionBankService.GroupChange(passageKo,scriptKo,resolvedImage,resolvedAudio));
                ra.addFlashAttribute("flashOk", "Đã cập nhật câu hỏi, giải quyết báo lỗi #" + reportId + " và thưởng +5 Xu cho học viên!");
                return "redirect:/content?tab=reports";
            }
            ra.addFlashAttribute("flashOk", "Đã lưu câu hỏi.");
            return "redirect:" + target;
        });
    }

    @PostMapping("/questions/{id}/status")
    public String questionStatus(@PathVariable Long id, @RequestParam boolean hidden, @RequestParam(required = false) String reason,
                                 @RequestParam(required = false) String back, HttpSession session, RedirectAttributes ra) {
        String target = safeBack(back);
        return mutate(session, ra, target, actor -> {
            bank.setHidden(id, hidden, reason, actor);
            ra.addFlashAttribute("flashOk", hidden ? "Đã ẩn câu hỏi. Kết quả học viên đã làm vẫn giữ nguyên." : "Đã hiện lại câu hỏi.");
            return "redirect:" + target;
        });
    }

    @PostMapping("/questions/{id}/purpose")
    public String questionPurpose(@PathVariable Long id, @RequestParam QuestionPurpose purpose,
                                  @RequestParam(required = false) String back, HttpSession session, RedirectAttributes ra) {
        String target = safeBack(back);
        return mutate(session, ra, target, actor -> {
            bank.setPurpose(id, purpose, actor);
            ra.addFlashAttribute("flashOk", "Đã đổi mục đích câu hỏi.");
            return "redirect:" + target;
        });
    }

    @PostMapping("/questions/bulk")
    public String bulk(@RequestParam(value = "ids", required = false) List<Long> ids, @RequestParam String action,
                       @RequestParam(required = false) String reason, @RequestParam(required = false) String back,
                       HttpSession session, RedirectAttributes ra) {
        String target = safeBack(back);
        return mutate(session, ra, target, actor -> {
            QuestionBankService.BulkResult r = bank.bulk(ids, action, reason, actor);
            ra.addFlashAttribute("flashOk", r.approvalRequestId() != null
                    ? "Ẩn hơn 20 câu cần Admin duyệt: đã tạo yêu cầu #" + r.approvalRequestId() + " trong Hộp duyệt."
                    : "Đã áp dụng cho " + r.applied() + " câu.");
            return "redirect:" + target;
        });
    }

    private static String safeBack(String back) {
        return (back != null && back.startsWith("/content/") && !back.contains("//")) ? back : "/content/questions";
    }

    public record EntryCell(int no, String css, String href) {}

    public record EntryType(String code, String name, String href, boolean selected, String templateCode,
                            String audioScope, String imageScope, String passageScope, String scriptScope,
                            boolean choices, String answerType, boolean writing, String help) {}

    private void fillManualEntry(Model model, ExamSource source, List<ExamImportService.CardView> cards,
                                 String card, String skillRaw, String typeRaw, List<Integer> nos, Integer focus,
                                 List<ImportIssue> issues) {
        model.addAttribute("cards", cards);
        model.addAttribute("slots", List.of());
        model.addAttribute("editSlot", null);
        model.addAttribute("prevKey", null);
        model.addAttribute("nextKey", null);
        model.addAttribute("filledSlots", cards.stream().mapToInt(ExamImportService.CardView::askCount).sum());
        model.addAttribute("sheetIssues", List.of());

        List<QuestionTypeCatalog.Section> sections = QuestionTypeCatalog.sections(source.getTrack());
        Skill skill = sections.stream().map(QuestionTypeCatalog.Section::skill)
                .filter(s -> s.name().equalsIgnoreCase(skillRaw == null ? "" : skillRaw))
                .findFirst().orElse(Skill.LISTENING);
        ExamImportService.CardView edit = card == null ? null
                : cards.stream().filter(c -> card.equals(c.id())).findFirst().orElse(null);
        if (edit != null && !edit.skillName().isBlank()) {
            skill = Skill.valueOf(edit.skillName());
        }
        QuestionTypeCatalog.Section section = QuestionTypeCatalog.section(source.getTrack(), skill).orElseThrow();
        Map<Integer, ExamImportService.CardView> byNo = owners(cards, skill);
        List<QuestionTemplate> molds = templates.list(source.getTrack(), skill);
        boolean explicitNos = nos != null && !nos.isEmpty();

        String templateCode = resolveMold(typeRaw, molds);
        List<Integer> picked = new ArrayList<>();
        if (edit != null && skill.name().equals(edit.skillName()) && !edit.nos().isEmpty()) {
            picked.addAll(edit.nos());
            if (templateCode == null) templateCode = resolveMold(edit.templateCode(), molds);
        } else if (explicitNos) {
            for (Integer n : nos) {
                if (n != null && section.contains(n) && !picked.contains(n)) picked.add(n);
            }
        }
        if (templateCode == null && !molds.isEmpty()) templateCode = molds.get(0).getCode();
        QuestionTemplate current = findMold(molds, templateCode);

        if (edit == null && !picked.isEmpty()) {
            ExamImportService.CardView owner = byNo.get(picked.get(0));
            if (owner != null && new HashSet<>(owner.nos()).containsAll(picked)) {
                edit = owner;
                picked = new ArrayList<>(owner.nos().isEmpty() ? picked : owner.nos());
                QuestionTemplate saved = findMold(molds, owner.templateCode());
                if (saved != null) current = saved;
            }
        }
        int cap = current == null ? 1 : Math.max(1, current.getChildMax());
        int need = current == null ? 1 : Math.max(1, Math.min(current.getChildMin(), cap));
        if (edit == null && picked.size() > cap) picked = new ArrayList<>(picked.subList(0, cap));

        if (picked.isEmpty() && current != null && cap == 1 && !explicitNos && edit == null) {
            int open = firstFree(section, byNo);
            if (open > 0) {
                picked.add(open);
            } else {
                ExamImportService.CardView owner = byNo.get(section.firstNo());
                if (owner != null) {
                    edit = owner;
                    picked.addAll(owner.nos().isEmpty() ? List.of(section.firstNo()) : owner.nos());
                    QuestionTemplate saved = findMold(molds, owner.templateCode());
                    if (saved != null) current = saved;
                }
            }
        }

        final List<Integer> chosenNos = List.copyOf(picked);
        final QuestionTemplate active = current;
        boolean grouped = active != null && active.getChildMax() > 1;
        String hint = "";
        if (grouped && chosenNos.size() < need) {
            hint = chosenNos.isEmpty()
                    ? "Câu dùng chung. Bấm đủ " + active.getChildMin()
                    + (active.getChildMax() > active.getChildMin() ? " đến " + active.getChildMax() : "")
                    + " ô bất kỳ."
                    : "Câu dùng chung. Chọn thêm " + (need - chosenNos.size()) + " ô. Không cần theo thứ tự.";
        }
        boolean complete = active != null && !chosenNos.isEmpty()
                && chosenNos.size() >= need && chosenNos.size() <= active.getChildMax();
        Integer focusNo = focus != null && chosenNos.contains(focus) ? focus
                : (chosenNos.isEmpty() ? null : chosenNos.get(chosenNos.size() - 1));

        List<EntryType> entryTypes = new ArrayList<>();
        for (QuestionTemplate mold : molds) {
            boolean several = mold.getChildMax() > 1;
            String help = several
                    ? (mold.getChildMin() == mold.getChildMax()
                    ? "Câu dùng chung. Bấm đủ " + mold.getChildMin() + " ô bất kỳ."
                    : "Câu dùng chung. Bấm từ " + mold.getChildMin() + " đến " + mold.getChildMax() + " ô bất kỳ.")
                    : "Bấm một ô để nhập câu đó.";
            entryTypes.add(new EntryType(mold.getCode(),
                    mold.getCode() + " · " + mold.getName(),
                    entryUrl(source.getId(), skill, mold.getCode(), List.of(), null, null),
                    active != null && mold.getCode().equals(active.getCode()),
                    mold.getCode(),
                    mold.getAudioScope().name(),
                    mold.getImageScope().name(),
                    mold.getPassageScope().name(),
                    mold.getScriptScope().name(),
                    mold.getOptionCount() != null,
                    mold.getAnswerType().name(),
                    mold.getSkill() == Skill.WRITING,
                    help));
        }

        String activeCode = active == null ? "" : active.getCode();
        boolean selectionIsNew = chosenNos.stream().noneMatch(byNo::containsKey);
        List<EntryCell> cells = new ArrayList<>();
        for (int n = section.firstNo(); n <= section.lastNo(); n++) {
            ExamImportService.CardView owner = byNo.get(n);
            boolean selected = chosenNos.contains(n);
            String css = owner == null ? "empty" : (owner.ready() ? "filled" : "filled bad");
            if (selected) css += " on";
            if (focusNo != null && focusNo == n) css += " focus";
            String href;
            if (owner != null) {
                String code = blankToNull(owner.templateCode()) == null ? activeCode : owner.templateCode();
                href = entryUrl(source.getId(), skill, code, owner.nos().isEmpty() ? List.of(n) : owner.nos(), owner.id(), n);
            } else if (grouped && selectionIsNew && !chosenNos.isEmpty() && chosenNos.size() < cap && !selected) {
                List<Integer> next = new ArrayList<>(chosenNos);
                next.add(n);
                href = entryUrl(source.getId(), skill, activeCode, next, null, n);
            } else {
                href = entryUrl(source.getId(), skill, activeCode, List.of(n), null, n);
            }
            cells.add(new EntryCell(n, css, href));
        }

        int addNo = firstFree(section, byNo);
        if (addNo <= 0) addNo = section.firstNo();
        ExamImportService.CardView addOwner = byNo.get(addNo);
        String addHref = addOwner == null
                ? entryUrl(source.getId(), skill, activeCode, List.of(addNo), null, addNo)
                : entryUrl(source.getId(), skill,
                blankToNull(addOwner.templateCode()) == null ? activeCode : addOwner.templateCode(),
                addOwner.nos().isEmpty() ? List.of(addNo) : addOwner.nos(), addOwner.id(), addNo);

        List<ExamImportService.AskView> editAsks = new ArrayList<>();
        if (complete) {
            for (int n : chosenNos) {
                ExamImportService.AskView found = edit == null ? null
                        : edit.asks().stream().filter(a -> Objects.equals(a.no(), n)).findFirst().orElse(null);
                editAsks.add(found == null ? ExamImportService.AskView.blank().withNo(n) : found.withNo(n));
            }
        }
        model.addAttribute("entrySkill", skill);
        model.addAttribute("entrySections", sections);
        model.addAttribute("entryTypes", entryTypes);
        model.addAttribute("entryCells", cells);
        model.addAttribute("editCard", edit);
        int cardIndex = edit == null ? -1 : cards.indexOf(edit) + 1;
        model.addAttribute("currentErrors", issues.stream()
                .filter(i -> i.isError() && "CARD".equals(i.sheet()) && Objects.equals(i.no(), cardIndex))
                .filter(i -> i.questionNo() == null || focusNo == null || Objects.equals(i.questionNo(), focusNo))
                .map(ImportIssue::message).toList());
        model.addAttribute("showCardForm", complete);
        model.addAttribute("editAsks", editAsks);
        model.addAttribute("pairHint", hint);
        model.addAttribute("focusNo", focusNo);
        model.addAttribute("addHref", addHref);
        QuestionTypeDef guideType = chosenNos.isEmpty() ? null
                : QuestionTypeCatalog.resolve(source.getTrack(), skill, chosenNos.get(0)).orElse(null);
        model.addAttribute("currentTypeCode", guideType == null ? "" : guideType.code());
        model.addAttribute("currentTypeName", active == null ? "" : active.getName());
        model.addAttribute("currentTemplate", activeCode);
        model.addAttribute("guideKo", guideType == null ? "" : guideType.instructionKo());
        model.addAttribute("guideName", guideType == null
                ? (active == null ? "" : active.getName())
                : guideType.code() + " · " + guideType.name());
        model.addAttribute("guidePoints", QuestionPoints.line(guideType));
        model.addAttribute("guideMark", QuestionPoints.mark(guideType));
        model.addAttribute("guideVi", active == null ? "" : active.getDescription());
        model.addAttribute("listening", skill == Skill.LISTENING);
        model.addAttribute("reading", skill == Skill.READING);
        model.addAttribute("writing", skill == Skill.WRITING);
        model.addAttribute("imageChoice", active != null && active.getDisplayMode() == DisplayMode.IMAGE_CHOICE);
        model.addAttribute("choices", active != null && active.getOptionCount() != null);
        model.addAttribute("acceptKeys", active != null && active.getAnswerType() == AnswerType.ACCEPT_KEYS);
        model.addAttribute("freeText", active != null && active.getAnswerType() == AnswerType.FREE_TEXT);
        model.addAttribute("chartImage", active != null && active.getSkill() == Skill.WRITING && active.isImageRequired());
    }

    private static String resolveMold(String raw, List<QuestionTemplate> molds) {
        if (raw == null || raw.isBlank()) return null;
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (findMold(molds, code) != null) return code;
        String mapped = QuestionTemplateCatalog.templateCode(code);
        return findMold(molds, mapped) == null ? null : mapped;
    }

    private static QuestionTemplate findMold(List<QuestionTemplate> molds, String code) {
        if (code == null || code.isBlank()) return null;
        return molds.stream().filter(m -> m.getCode().equalsIgnoreCase(code)).findFirst().orElse(null);
    }

    private static int firstFree(QuestionTypeCatalog.Section section, Map<Integer, ExamImportService.CardView> byNo) {
        for (int n = section.firstNo(); n <= section.lastNo(); n++) {
            if (!byNo.containsKey(n)) return n;
        }
        return 0;
    }

    private static Map<Integer, ExamImportService.CardView> owners(List<ExamImportService.CardView> cards, Skill skill) {
        Map<Integer, ExamImportService.CardView> byNo = new HashMap<>();
        for (ExamImportService.CardView view : cards) {
            if (!skill.name().equals(view.skillName())) continue;
            for (Integer n : view.nos()) byNo.put(n, view);
        }
        return byNo;
    }

    private static String entryUrl(Long id, Skill skill, String type, List<Integer> nos, String cardId, Integer focus) {
        StringBuilder sb = new StringBuilder("/content/sources/").append(id).append("?skill=").append(skill.name());
        if (type != null && !type.isBlank()) sb.append("&type=").append(type);
        if (nos != null) for (Integer n : nos) if (n != null) sb.append("&nos=").append(n);
        if (cardId != null && !cardId.isBlank()) sb.append("&card=").append(cardId);
        if (focus != null) sb.append("&focus=").append(focus);
        return sb.append("#paper").toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
