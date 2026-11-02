package com.hottopik.controller;

import com.hottopik.dto.TopicDTO;
import com.hottopik.dto.VocabularyDTO;
import com.hottopik.service.HanjaService;
import com.hottopik.service.VocabularyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@Controller
public class TheoryController {

    private final HanjaService hanjaService;
    private final VocabularyService vocabularyService;

    @Autowired
    public TheoryController(HanjaService hanjaService, VocabularyService vocabularyService) {
        this.hanjaService = hanjaService;
        this.vocabularyService = vocabularyService;
    }

    @GetMapping({"/ly-thuyet", "/theory", "/ly-thuyet/ngu-phap"})
    public String theory(@RequestParam(value = "tab", required = false) String tab,
                         HttpServletRequest request,
                         Model model) {
        if (tab == null && request != null && request.getRequestURI() != null && request.getRequestURI().endsWith("/ngu-phap")) {
            tab = "grammar";
        }
        model.addAttribute("pageTitle", "Lý thuyết · Từ vựng, Ngữ pháp, Hán Việt, Đặc thù");
        model.addAttribute("activePage", "theory");
        model.addAttribute("currentTab", tab != null ? tab : "vocab");
        model.addAttribute("topicsByLevel", vocabularyService.getAllTopicsGroupedByLevel());
        return "theory/index";
    }

    @GetMapping({"/ly-thuyet/tu-vung", "/theory/vocabulary"})
    public String vocabulary(@RequestParam(value = "topic", required = false) String topic,
                             @RequestParam(value = "level", required = false, defaultValue = "1") Integer level,
                             Model model) {
        List<TopicDTO> levelTopics = vocabularyService.getTopicsByLevel(level);
        if ((topic == null || topic.trim().isEmpty() || "daily".equalsIgnoreCase(topic)) && !levelTopics.isEmpty()) {
            topic = levelTopics.get(0).getName();
        } else if (topic == null || topic.trim().isEmpty()) {
            topic = "Gia đình";
        }

        List<VocabularyDTO> vocabList = vocabularyService.getVocabulariesForLearner(level, topic);

        model.addAttribute("pageTitle", "Chủ đề: " + topic + " · HOT TOPIK");
        model.addAttribute("activePage", "theory");
        model.addAttribute("currentTopic", topic);
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
                            Model model) {
        List<TopicDTO> levelTopics = vocabularyService.getTopicsByLevel(level);
        if ((topic == null || topic.trim().isEmpty()) && !levelTopics.isEmpty()) {
            topic = levelTopics.get(0).getName();
        } else if (topic == null || topic.trim().isEmpty()) {
            topic = "Gia đình";
        }

        List<VocabularyDTO> vocabList = vocabularyService.getVocabulariesForLearner(level, topic);

        model.addAttribute("pageTitle", "Học từ vựng bằng Flashcards · " + topic);
        model.addAttribute("activePage", "theory");
        model.addAttribute("currentTopic", topic);
        model.addAttribute("currentLevel", level);
        model.addAttribute("currentLevelName", VocabularyDTO.formatTopikName(level));
        model.addAttribute("vocabList", vocabList);
        return "theory/flashcard";
    }

    @GetMapping({"/ly-thuyet/quiz", "/theory/quiz"})
    public String quiz(@RequestParam(value = "topic", required = false) String topic,
                       @RequestParam(value = "level", required = false, defaultValue = "1") Integer level,
                       Model model) {
        List<TopicDTO> levelTopics = vocabularyService.getTopicsByLevel(level);
        if ((topic == null || topic.trim().isEmpty()) && !levelTopics.isEmpty()) {
            topic = levelTopics.get(0).getName();
        } else if (topic == null || topic.trim().isEmpty()) {
            topic = "Gia đình";
        }

        List<VocabularyDTO> vocabList = vocabularyService.getVocabulariesForLearner(level, topic);

        model.addAttribute("pageTitle", "Quiz kiểm tra từ vựng · " + topic);
        model.addAttribute("activePage", "theory");
        model.addAttribute("currentTopic", topic);
        model.addAttribute("currentLevel", level);
        model.addAttribute("currentLevelName", VocabularyDTO.formatTopikName(level));
        model.addAttribute("vocabList", vocabList);
        return "theory/quiz";
    }

    @GetMapping({"/tu-chua-thuoc", "/theory/review", "/theory/unlearned"})
    public String unlearnedWords(Model model) {
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

    @GetMapping("/theory/grammar")
    public String grammarDetail(@RequestParam(value = "id", defaultValue = "1") String id, Model model) {
        model.addAttribute("pageTitle", "Chi tiết ngữ pháp");
        model.addAttribute("activePage", "theory");
        model.addAttribute("grammarId", id);
        return "theory/grammar";
    }

    // ==========================================
    // REST APIs cho AJAX phía người dùng
    // ==========================================
    @GetMapping("/api/theory/vocabularies")
    @ResponseBody
    public ResponseEntity<?> getVocabulariesApi(@RequestParam(value = "level", required = false, defaultValue = "1") Integer level,
                                                @RequestParam(value = "topic", required = false) String topic) {
        return ResponseEntity.ok(vocabularyService.getVocabulariesForLearner(level, topic));
    }

    @GetMapping("/api/theory/topics")
    @ResponseBody
    public ResponseEntity<?> getTopicsApi(@RequestParam(value = "level", required = false, defaultValue = "1") Integer level) {
        return ResponseEntity.ok(vocabularyService.getTopicsByLevel(level));
    }
}
