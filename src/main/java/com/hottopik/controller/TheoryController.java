package com.hottopik.controller;

import com.hottopik.dto.TopicDTO;
import com.hottopik.dto.VocabularyDTO;
import com.hottopik.entity.Grammar;
import com.hottopik.entity.GrammarGroup;
import com.hottopik.service.GrammarService;
import com.hottopik.service.GrammarGroupService;
import com.hottopik.service.HanjaService;
import com.hottopik.service.VocabularyService;
import com.hottopik.service.VocabularySyncService;
import com.hottopik.service.SavedGrammarService;
import com.hottopik.dto.UserDTO;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;

@Controller
public class TheoryController {

    private final HanjaService hanjaService;
    private final VocabularyService vocabularyService;
    private final VocabularySyncService syncService;
    private final GrammarService grammarService;
    private final GrammarGroupService grammarGroupService;
    private final SavedGrammarService savedGrammarService;

    @Autowired
    public TheoryController(HanjaService hanjaService, VocabularyService vocabularyService, GrammarService grammarService,
                            GrammarGroupService grammarGroupService, VocabularySyncService syncService,
                            SavedGrammarService savedGrammarService) {
        this.hanjaService = hanjaService;
        this.vocabularyService = vocabularyService;
        this.syncService = syncService;
        this.grammarService = grammarService;
        this.grammarGroupService = grammarGroupService;
        this.savedGrammarService = savedGrammarService;
    }

    private static Long currentUserId(HttpSession session) {
        UserDTO user = AuthHelper.getLoggedInUser(session);
        return user != null ? user.getId() : null;
    }

    private void setNoCacheHeaders(HttpServletResponse response) {
        if (response != null) {
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
        }
    }

    @GetMapping({"/ly-thuyet", "/theory", "/ly-thuyet/ngu-phap"})
    public String theory(@RequestParam(value = "tab", required = false) String tab,
                         @RequestParam(value = "level", defaultValue = "1") Short level,
                         HttpServletRequest request,
                         HttpServletResponse response,
                         HttpSession session,
                         Model model) {
        setNoCacheHeaders(response);
        model.addAttribute("savedGrammarCount", savedGrammarService.getSavedGrammars(currentUserId(session)).size());
        if (tab == null && request != null && request.getRequestURI() != null && request.getRequestURI().endsWith("/ngu-phap")) {
            tab = "grammar";
        }
        model.addAttribute("pageTitle", "Lý thuyết · Từ vựng, Ngữ pháp, Hán Việt, Đặc thù");
        model.addAttribute("activePage", "theory");
        model.addAttribute("currentTab", tab != null ? tab : "vocab");
        model.addAttribute("selectedLevel", level);
        model.addAttribute("topicsByLevel", vocabularyService.getAllTopicsGroupedByLevel());
        if ("grammar".equals(tab != null ? tab : "vocab")) {
            var groups = grammarGroupService.getActiveGroupsByLevel(level);
            model.addAttribute("grammarGroups", groups);
            model.addAttribute("categoryTitle", VocabularyDTO.formatTopikName(Integer.valueOf(level)) + " · " + groups.size() + " nhóm ngữ pháp");
        } else {
            var topics = vocabularyService.getTopicsForLearner(level.intValue());
            model.addAttribute("categoryTitle", VocabularyDTO.formatTopikName(Integer.valueOf(level)) + " · " + topics.size() + " chủ đề");
        }
        return "theory/index";
    }

    @GetMapping({"/ly-thuyet/tu-vung", "/theory/vocabulary"})
    public String vocabulary(@RequestParam(value = "topic", required = false) String topic,
                             @RequestParam(value = "level", required = false, defaultValue = "1") Integer level,
                             HttpServletResponse response,
                             Model model) {
        setNoCacheHeaders(response);
        List<TopicDTO> levelTopics = vocabularyService.getTopicsForLearner(level);
        final String requestedTopic = (topic != null) ? topic.trim() : "";
        String activeTopic = null;
        if (!levelTopics.isEmpty()) {
            if (requestedTopic.isEmpty() || "daily".equalsIgnoreCase(requestedTopic) || levelTopics.stream().noneMatch(t -> t.getName().equalsIgnoreCase(requestedTopic))) {
                activeTopic = levelTopics.get(0).getName();
            } else {
                activeTopic = requestedTopic;
            }
        }

        List<VocabularyDTO> vocabList = (activeTopic != null) ? vocabularyService.getVocabulariesForLearner(level, activeTopic) : java.util.Collections.emptyList();

        model.addAttribute("pageTitle", (activeTopic != null ? "Chủ đề: " + activeTopic : VocabularyDTO.formatTopikName(level)) + " · HOT TOPIK");
        model.addAttribute("activePage", "theory");
        model.addAttribute("currentTopic", activeTopic != null ? activeTopic : "");
        model.addAttribute("currentLevel", level);
        model.addAttribute("currentLevelName", VocabularyDTO.formatTopikName(level));
        model.addAttribute("vocabList", vocabList);
        model.addAttribute("levelTopics", levelTopics);
        model.addAttribute("totalCount", vocabList.size());
        return "theory/vocabulary";
    }

    @GetMapping({"/ly-thuyet/the", "/theory/flashcard", "/theory/flashcards"})
    public String flashcard(@RequestParam(value = "topic", required = false) String topic,
                            @RequestParam(value = "level", required = false, defaultValue = "1") Integer level,
                            HttpServletResponse response,
                            Model model) {
        setNoCacheHeaders(response);
        List<TopicDTO> levelTopics = vocabularyService.getTopicsForLearner(level);
        final String requestedTopic = (topic != null) ? topic.trim() : "";
        String activeTopic = null;
        if (!levelTopics.isEmpty()) {
            if (requestedTopic.isEmpty() || levelTopics.stream().noneMatch(t -> t.getName().equalsIgnoreCase(requestedTopic))) {
                activeTopic = levelTopics.get(0).getName();
            } else {
                activeTopic = requestedTopic;
            }
        }

        List<VocabularyDTO> vocabList = (activeTopic != null) ? vocabularyService.getVocabulariesForLearner(level, activeTopic) : java.util.Collections.emptyList();

        model.addAttribute("pageTitle", "Học từ vựng bằng Flashcards · " + (activeTopic != null ? activeTopic : VocabularyDTO.formatTopikName(level)));
        model.addAttribute("activePage", "theory");
        model.addAttribute("currentTopic", activeTopic != null ? activeTopic : "");
        model.addAttribute("currentLevel", level);
        model.addAttribute("currentLevelName", VocabularyDTO.formatTopikName(level));
        model.addAttribute("vocabList", vocabList);
        return "theory/flashcard";
    }

    @GetMapping({"/ly-thuyet/quiz", "/theory/quiz"})
    public String quiz(@RequestParam(value = "topic", required = false) String topic,
                       @RequestParam(value = "level", required = false, defaultValue = "1") Integer level,
                       HttpServletResponse response,
                       Model model) {
        setNoCacheHeaders(response);
        List<TopicDTO> levelTopics = vocabularyService.getTopicsForLearner(level);
        final String requestedTopic = (topic != null) ? topic.trim() : "";
        String activeTopic = null;
        if (!levelTopics.isEmpty()) {
            if (requestedTopic.isEmpty() || levelTopics.stream().noneMatch(t -> t.getName().equalsIgnoreCase(requestedTopic))) {
                activeTopic = levelTopics.get(0).getName();
            } else {
                activeTopic = requestedTopic;
            }
        }

        List<VocabularyDTO> vocabList = (activeTopic != null) ? vocabularyService.getVocabulariesForLearner(level, activeTopic) : java.util.Collections.emptyList();

        model.addAttribute("pageTitle", "Quiz kiểm tra từ vựng · " + (activeTopic != null ? activeTopic : VocabularyDTO.formatTopikName(level)));
        model.addAttribute("activePage", "theory");
        model.addAttribute("currentTopic", activeTopic != null ? activeTopic : "");
        model.addAttribute("currentLevel", level);
        model.addAttribute("currentLevelName", VocabularyDTO.formatTopikName(level));
        model.addAttribute("vocabList", vocabList);
        return "theory/quiz";
    }

    @GetMapping({"/tu-chua-thuoc", "/theory/review", "/theory/unlearned"})
    public String unlearnedWords(HttpServletResponse response, Model model) {
        setNoCacheHeaders(response);
        model.addAttribute("pageTitle", "Sổ từ chưa thuộc · HOT TOPIK");
        model.addAttribute("activePage", "theory");
        return "theory/unlearned";
    }

    @GetMapping({"/ly-thuyet/han-viet", "/theory/hanja"})
    public String hanja(Model model) {
        model.addAttribute("pageTitle", "Cầu nối Hán Việt · 32 gốc từ");
        model.addAttribute("activePage", "theory");
        try {
            model.addAttribute("hanjaRoots", hanjaService.getActiveHanjaRoots(null));
            model.addAttribute("hanjaStats", hanjaService.getLearnerStats());
        } catch (Exception e) {
            model.addAttribute("hanjaRoots", java.util.Collections.emptyList());
            model.addAttribute("hanjaStats", java.util.Collections.emptyMap());
        }
        return "theory/hanja";
    }

    @GetMapping({"/ly-thuyet/dac-thu", "/theory/special"})
    public String special(Model model) {
        model.addAttribute("pageTitle", "Đặc thù tiếng Hàn");
        model.addAttribute("activePage", "theory");
        return "theory/special";
    }

    /** GET /theory/grammar – trang chi tiết một mẫu ngữ pháp (chỉ ngữ pháp và nhóm đang hiển thị), kèm nút lưu ⭐. */
    @GetMapping("/theory/grammar")
    public String grammarDetail(@RequestParam(value = "id", defaultValue = "1") Long id, HttpSession session, Model model) {
        Long userId = currentUserId(session);
        model.addAttribute("isLoggedIn", userId != null);
        model.addAttribute("isSaved", savedGrammarService.isSaved(userId, id));
        model.addAttribute("pageTitle", "Chi tiết ngữ pháp");
        model.addAttribute("activePage", "theory");
        // Dùng truy vấn JOIN FETCH nhóm + ví dụ: open-in-view=false nên getGrammarById() trả entity lazy
        // làm template (grammarGroup.nameVi, examples) ném LazyInitializationException và sập trang.
        Grammar grammar = grammarService.getActiveGrammarDetail(id).orElse(null);
        model.addAttribute("grammar", grammar);
        if (grammar == null) {
            model.addAttribute("errorMessage", "Mẫu ngữ pháp này không tồn tại hoặc hiện không khả dụng.");
        } else {
            model.addAttribute("pageTitle", grammar.getPattern() + " · Ngữ pháp TOPIK " + grammar.getLevel());
            List<Grammar> related = new java.util.ArrayList<>(
                    grammarService.getActiveGrammarsByGroupIdWithExamples(grammar.getGrammarGroup().getId()));
            related.removeIf(g -> g.getId().equals(grammar.getId()));
            model.addAttribute("relatedGrammars", related);
        }
        return "theory/grammar";
    }

    /** GET /ly-thuyet/ngu-phap-nhom – danh sách ngữ pháp trong một nhóm, đánh dấu những ngữ pháp học viên đã lưu. */
    @GetMapping({"/ly-thuyet/ngu-phap-nhom", "/theory/grammar-list"})
    public String grammarListByGroup(@RequestParam("groupId") Integer groupId, HttpSession session, Model model) {
        model.addAttribute("pageTitle", "Danh sách ngữ pháp theo nhóm");
        model.addAttribute("activePage", "theory");
        Long userId = currentUserId(session);
        model.addAttribute("isLoggedIn", userId != null);
        model.addAttribute("savedIds", savedGrammarService.getSavedGrammarIds(userId));
        // Nhóm không tồn tại hoặc đã bị Content Manager ẩn thì học viên không xem được (kể cả khi gõ thẳng URL).
        GrammarGroup group = null;
        try {
            group = grammarGroupService.getGroupById(groupId);
        } catch (IllegalArgumentException ignored) {
        }
        if (group == null || !"ACTIVE".equalsIgnoreCase(group.getStatus())) {
            model.addAttribute("grammars", java.util.Collections.emptyList());
            model.addAttribute("errorMessage", "Nhóm ngữ pháp này không tồn tại hoặc hiện không khả dụng.");
            return "theory/grammar-list";
        }
        model.addAttribute("group", group);
        model.addAttribute("groupLevel", group.getLevel());
        model.addAttribute("pageTitle", group.getNameVi() + " · Ngữ pháp TOPIK " + group.getLevel());
        model.addAttribute("grammars", grammarService.getActiveGrammarsByGroupIdWithExamples(groupId));
        return "theory/grammar-list";
    }

    // ===== SỔ NGỮ PHÁP: học viên lưu (⭐) mẫu ngữ pháp hay/khó nhớ để ôn lại =====

    /** GET /ly-thuyet/ngu-phap-da-luu – sổ tay ngữ pháp đã lưu của học viên (cần đăng nhập). */
    @GetMapping({"/ly-thuyet/ngu-phap-da-luu", "/theory/saved-grammar"})
    public String savedGrammars(HttpSession session, HttpServletResponse response, Model model) {
        Long userId = currentUserId(session);
        if (userId == null) return "redirect:/login";
        setNoCacheHeaders(response);
        model.addAttribute("pageTitle", "Ngữ pháp đã lưu · HOT TOPIK");
        model.addAttribute("activePage", "theory");
        model.addAttribute("savedGrammars", savedGrammarService.getSavedGrammars(userId));
        return "theory/saved-grammar";
    }

    /** POST /api/theory/grammar/{id}/save – lưu / bỏ lưu ngữ pháp vào sổ tay (401 nếu chưa đăng nhập). */
    @PostMapping("/api/theory/grammar/{id}/save")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, Object>> toggleSavedGrammar(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("success", false,
                    "message", "Vui lòng đăng nhập để lưu ngữ pháp."));
        }
        try {
            boolean saved = savedGrammarService.toggle(userId, id);
            return ResponseEntity.ok(java.util.Map.of("success", true, "saved", saved,
                    "message", saved ? "Đã lưu vào Sổ ngữ pháp ⭐" : "Đã bỏ lưu ngữ pháp."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", e.getMessage()));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Hai request lưu cùng lúc: request kia đã lưu xong, ràng buộc unique chặn bản trùng.
            return ResponseEntity.ok(java.util.Map.of("success", true, "saved", true, "message", "Đã lưu vào Sổ ngữ pháp ⭐"));
        }
    }

    // ==========================================
    // REST APIs cho AJAX phía người dùng
    // ==========================================
    @GetMapping("/api/theory/vocabularies")
    @ResponseBody
    public ResponseEntity<?> getVocabulariesApi(@RequestParam(value = "level", required = false, defaultValue = "1") Integer level,
                                                @RequestParam(value = "topic", required = false) String topic) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .body(vocabularyService.getVocabulariesForLearner(level, topic));
    }

    @GetMapping("/api/theory/topics")
    @ResponseBody
    public ResponseEntity<?> getTopicsApi(@RequestParam(value = "level", required = false, defaultValue = "1") Integer level) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .body(vocabularyService.getTopicsForLearner(level));
    }

    @GetMapping("/api/theory/topics/all")
    @ResponseBody
    public ResponseEntity<?> getAllTopicsGroupedApi() {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .body(vocabularyService.getAllTopicsGroupedByLevel());
    }

    /**
     * Endpoint phát âm tiếng Hàn (Korean TTS Proxy):
     * Tự động sinh âm thanh giọng đọc chuẩn cho từ vựng và câu ví dụ,
     * hoạt động độc lập và ổn định trên mọi trình duyệt (Brave, Chrome, Edge, Safari...).
     */
    @GetMapping(value = "/api/tts", produces = "audio/mpeg")
    @ResponseBody
    public ResponseEntity<byte[]> getKoreanTts(@RequestParam("text") String text) {
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String encoded = URLEncoder.encode(text.trim(), StandardCharsets.UTF_8);
            String url = "https://translate.google.com/translate_tts?ie=UTF-8&tl=ko&client=tw-ob&q=" + encoded;
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(4))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://translate.google.com/")
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 0) {
                return ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS))
                        .contentType(MediaType.valueOf("audio/mpeg"))
                        .body(resp.body());
            }
        } catch (Exception ignored) {}
        return ResponseEntity.notFound().build();
    }

    @GetMapping(value = {"/api/theory/sync/stream", "/api/vocab-sync/stream", "/api/vocabulary/sync/stream"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter syncStreamForLearner() {
        return syncService != null ? syncService.createEmitter() : new SseEmitter();
    }
}
