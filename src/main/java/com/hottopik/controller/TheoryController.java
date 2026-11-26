package com.hottopik.controller;

import com.hottopik.dto.TopicDTO;
import com.hottopik.dto.VocabularyDTO;
import com.hottopik.entity.Grammar;
import com.hottopik.service.GrammarService;
import com.hottopik.service.GrammarGroupService;
import com.hottopik.service.HanjaService;
import com.hottopik.service.VocabularyService;
import com.hottopik.service.VocabularySyncService;
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

    @Autowired
    public TheoryController(HanjaService hanjaService, VocabularyService vocabularyService, GrammarService grammarService,
                            GrammarGroupService grammarGroupService, VocabularySyncService syncService) {
        this.hanjaService = hanjaService;
        this.vocabularyService = vocabularyService;
        this.syncService = syncService;
        this.grammarService = grammarService;
        this.grammarGroupService = grammarGroupService;
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
                         Model model) {
        setNoCacheHeaders(response);
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

    /* =========================================================================================
     * LUỒNG CHỨC NĂNG: CẦU NỐI HÁN VIỆT 24 GỐC HÁN TỰ (HANJA ROOTS THEORY - FUNC_THEO_06)
     * -----------------------------------------------------------------------------------------
     * 1. THAO TÁC NGƯỜI DÙNG & UI/HTML:
     *    - Học viên click vào menu "Lý thuyết" -> Chọn "Cầu nối Hán Việt" (theory/index.html).
     * 2. HTTP REQUEST & ENDPOINT:
     *    - URL: GET /ly-thuyet/han-viet?tab={learn/guess/learned/mistakes}
     * 3. CONTROLLER & SERVICE XỬ LÝ:
     *    - Controller: com.hottopik.controller.TheoryController.hanja()
     *    - Triệu gọi Service: com.hottopik.service.HanjaService.getActiveHanjaRoots():
     *      + Nạp 24 gốc Hán tự và 192 từ vựng liên quan.
     *      + Thống kê số gốc đã học, số từ cần luyện tập.
     * 4. TRUY VẤN CSDL:
     *    - Bảng dbo.hanja_roots (SELECT gốc Hán).
     *    - Bảng dbo.hanja_root_words (SELECT từ vựng cùng họ).
     * 5. HIỂN THỊ KẾT QUẢ CHO NGƯỜI DÙNG:
     *    - Trả về View: "theory/hanja" (src/main/resources/templates/theory/hanja.html)
     *    - Giao diện hiển thị: Lưới 4 cột gốc Hán, bảng nghĩa Hán Việt, phát âm Audio và 4 tab chức năng.
     * ========================================================================================= */
    @GetMapping({"/ly-thuyet/han-viet", "/theory/hanja"})
    public String hanja(@RequestParam(value = "tab", required = false) String tab, Model model) {
        model.addAttribute("pageTitle", "Cầu nối Hán Việt · 32 gốc từ");
        model.addAttribute("activePage", "theory");
        model.addAttribute("currentTab", tab != null ? tab : "learn");
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

    @GetMapping("/theory/grammar")
    public String grammarDetail(@RequestParam(value = "id", defaultValue = "1") Long id, Model model) {
        model.addAttribute("pageTitle", "Chi tiết ngữ pháp");
        model.addAttribute("activePage", "theory");
        try {
            Grammar grammar = grammarService.getGrammarById(id);
            if (grammar == null || !"ACTIVE".equalsIgnoreCase(grammar.getStatus())) {
                model.addAttribute("grammar", null);
                model.addAttribute("errorMessage", "Mẫu ngữ pháp này hiện không khả dụng.");
            } else {
                model.addAttribute("grammar", grammar);
                List<Grammar> related = grammar.getGrammarGroup() != null
                        ? grammarService.getActiveGrammarsByGroupIdWithExamples(grammar.getGrammarGroup().getId())
                        : grammarService.getActiveGrammarsByLevel(grammar.getLevel());
                related.removeIf(g -> g.getId().equals(grammar.getId()));
                model.addAttribute("relatedGrammars", related);
            }
        } catch (Exception e) {
            model.addAttribute("grammar", null);
            model.addAttribute("errorMessage", "Không tìm thấy mẫu ngữ pháp yêu cầu.");
        }
        return "theory/grammar";
    }

    @GetMapping({"/ly-thuyet/ngu-phap-nhom", "/theory/grammar-list"})
    public String grammarListByGroup(@RequestParam("groupId") Integer groupId, Model model) {
        model.addAttribute("pageTitle", "Danh sách ngữ pháp theo nhóm");
        model.addAttribute("activePage", "theory");
        try {
            model.addAttribute("grammars", grammarService.getActiveGrammarsByGroupIdWithExamples(groupId));
        } catch (Exception e) {
            model.addAttribute("grammars", java.util.Collections.emptyList());
            model.addAttribute("errorMessage", "Không tìm thấy nhóm ngữ pháp yêu cầu.");
        }
        return "theory/grammar-list";
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

    /* =========================================================================================
     * LUỒNG CHỨC NĂNG: PHÁT ÂM TIẾNG HÀN QUA TTS PROXY (KOREAN TTS PROXY - FUNC_THEO_09)
     * -----------------------------------------------------------------------------------------
     * 1. THAO TÁC NGƯỜI DÙNG & UI/HTML:
     *    - Học viên bấm icon chiếc loa phát âm <i class="bi bi-volume-up"></i> trên bất kỳ từ vựng
     *      hoặc câu ví dụ nào trong các trang Từ vựng, Flashcard, Quiz, Hán Việt.
     *    - JavaScript gọi speakWord(text) phát âm qua thẻ new Audio('/api/tts?text=' + encoded).
     * 2. HTTP REQUEST & ENDPOINT:
     *    - URL: GET /api/tts?text={koreanText}
     * 3. CONTROLLER & BACKEND XỬ LÝ:
     *    - Controller: com.hottopik.controller.TheoryController.getKoreanTts()
     *    - Triệu gọi HTTP Client kết nối đến dịch vụ Google Translate TTS Proxy:
     *      https://translate.google.com/translate_tts?ie=UTF-8&tl=ko&client=tw-ob&q=...
     *    - Thiết lập connectTimeout 4s, readTimeout 6s để chống treo server.
     * 4. TRUY VẤN CSDL:
     *    - Không truy vấn CSDL (Xử lý Stream IO byte array).
     * 5. HIỂN THỊ KẾT QUẢ CHO NGƯỜI DÙNG:
     *    - Trả về ResponseEntity<byte[]> với Content-Type: audio/mpeg và Cache-Control: max-age=7 days.
     *    - Trình duyệt học viên nhận luồng MP3 và phát trực tiếp qua loa mà không bị phụ thuộc vào giọng đọc máy của hệ điều hành.
     * ========================================================================================= */
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
