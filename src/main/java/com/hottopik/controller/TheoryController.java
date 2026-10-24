package com.hottopik.controller;

import com.hottopik.entity.Grammar;
import com.hottopik.entity.GrammarGroup;
import com.hottopik.service.GrammarGroupService;
import com.hottopik.service.GrammarService;
import com.hottopik.service.HanjaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@Controller
public class TheoryController {

    private final HanjaService hanjaService;
    private final GrammarService grammarService;
    private final GrammarGroupService grammarGroupService;

    @Autowired
    public TheoryController(HanjaService hanjaService,
                            GrammarService grammarService,
                            GrammarGroupService grammarGroupService) {
        this.hanjaService = hanjaService;
        this.grammarService = grammarService;
        this.grammarGroupService = grammarGroupService;
    }

    @GetMapping({"/ly-thuyet", "/theory", "/ly-thuyet/ngu-phap"})
    public String theory(@RequestParam(value = "tab", required = false) String tab,
                         @RequestParam(value = "level", defaultValue = "1") Short level,
                         HttpServletRequest request,
                         Model model) {
        if (tab == null && request != null && request.getRequestURI() != null && request.getRequestURI().endsWith("/ngu-phap")) {
            tab = "grammar";
        }
        model.addAttribute("pageTitle", "Lý thuyết · Từ vựng, Ngữ pháp, Hán Việt, Đặc thù");
        model.addAttribute("activePage", "theory");
        model.addAttribute("currentTab", tab != null ? tab : "vocab");
        model.addAttribute("selectedLevel", level);

        // Lấy danh sách nhóm ngữ pháp từ Database theo cấp độ (1-6)
        if ("grammar".equals(model.getAttribute("currentTab"))) {
            List<GrammarGroup> grammarGroups = grammarGroupService.getGroupsByLevel(level);
            model.addAttribute("grammarGroups", grammarGroups);
        }

        return "theory/index";
    }

    @GetMapping({"/ly-thuyet/tu-vung", "/theory/vocabulary"})
    public String vocabulary(@RequestParam(value = "topic", defaultValue = "daily") String topic, Model model) {
        model.addAttribute("pageTitle", "Chủ đề từ vựng · HOT TOPIK");
        model.addAttribute("activePage", "theory");
        model.addAttribute("topic", topic);
        return "theory/vocabulary";
    }

    @GetMapping({"/ly-thuyet/the", "/theory/flashcard", "/theory/flashcards"})
    public String flashcard(Model model) {
        model.addAttribute("pageTitle", "Học từ vựng bằng Flashcards");
        model.addAttribute("activePage", "theory");
        return "theory/flashcard";
    }

    @GetMapping({"/ly-thuyet/quiz", "/theory/quiz"})
    public String quiz(Model model) {
        model.addAttribute("pageTitle", "Quiz kiểm tra từ vựng nhanh");
        model.addAttribute("activePage", "theory");
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
    public String grammarDetail(@RequestParam(value = "id", defaultValue = "1") Long id, Model model) {
        model.addAttribute("pageTitle", "Chi tiết ngữ pháp");
        model.addAttribute("activePage", "theory");

        try {
            Grammar grammar = grammarService.getGrammarById(id);
            model.addAttribute("grammar", grammar);

            // Lấy danh sách ngữ pháp liên quan (cùng nhóm hoặc cùng level)
            if (grammar != null) {
                if (grammar.getGrammarGroup() != null) {
                    model.addAttribute("relatedGrammars",
                            grammarService.getGrammarsByGroupId(grammar.getGrammarGroup().getId()));
                } else {
                    model.addAttribute("relatedGrammars",
                            grammarService.getGrammarsByLevelAndStatus(grammar.getLevel(), "ACTIVE"));
                }
            }
        } catch (Exception e) {
            model.addAttribute("grammar", null);
            model.addAttribute("errorMessage", "Không tìm thấy mẫu ngữ pháp yêu cầu.");
        }

        return "theory/grammar";
    }

    // Trang hiển thị danh sách ngữ pháp thuộc một nhóm cụ thể khi học viên bấm vào nhóm
    @GetMapping({"/ly-thuyet/ngu-phap-nhom", "/theory/grammar-list"})
    public String grammarListByGroup(@RequestParam(value = "groupId") Integer groupId, Model model) {
        model.addAttribute("pageTitle", "Danh sách ngữ pháp theo nhóm");
        model.addAttribute("activePage", "theory");

        try {
            // Sử dụng phương thức nạp sẵn danh sách ví dụ (examples) để tránh lỗi LazyInitializationException
            List<Grammar> grammars = grammarService.getGrammarsByGroupIdWithExamples(groupId);
            model.addAttribute("grammars", grammars);
        } catch (Exception e) {
            model.addAttribute("grammars", java.util.Collections.emptyList());
            model.addAttribute("errorMessage", "Không tìm thấy nhóm ngữ pháp yêu cầu.");
        }

        return "theory/grammar-list";
    }
}