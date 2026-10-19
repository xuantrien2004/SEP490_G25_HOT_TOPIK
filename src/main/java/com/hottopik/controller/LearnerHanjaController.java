package com.hottopik.controller;

import com.hottopik.dto.HanjaQuizQuestionDTO;
import com.hottopik.dto.HanjaRootDTO;
import com.hottopik.service.HanjaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for the learner-facing Hanja (Cầu nối Hán Việt) pages.
 * Maps to /ly-thuyet/han-viet and /theory/hanja routes (shared with TheoryController).
 *
 * Provides:
 *  - Page view with initial data for server-side rendering
 *  - REST API for AJAX loading of roots, root details, and quiz questions
 */
@Controller
@RequestMapping("/api/learner/hanja")
public class LearnerHanjaController {

    private final HanjaService hanjaService;

    @Autowired
    public LearnerHanjaController(HanjaService hanjaService) {
        this.hanjaService = hanjaService;
    }

    /**
     * REST API: List all ACTIVE Hanja roots for learner view.
     * GET /api/learner/hanja/roots?query=...
     */
    @GetMapping("/roots")
    @ResponseBody
    public ResponseEntity<List<HanjaRootDTO>> getActiveRoots(
            @RequestParam(value = "query", required = false) String query) {
        List<HanjaRootDTO> roots = hanjaService.getActiveHanjaRoots(query);
        return ResponseEntity.ok(roots);
    }

    /**
     * REST API: Get single Hanja root detail (with related words) for learner.
     * GET /api/learner/hanja/roots/{id}
     */
    @GetMapping("/roots/{id}")
    @ResponseBody
    public ResponseEntity<?> getRootDetail(@PathVariable("id") Integer id) {
        HanjaRootDTO dto = hanjaService.getHanjaRootById(id);
        if (dto == null || !"ACTIVE".equalsIgnoreCase(dto.getStatus())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    /**
     * REST API: Get randomized quiz questions for "Đoán nghĩa từ mới" mode.
     * GET /api/learner/hanja/quiz?count=10
     */
    @GetMapping("/quiz")
    @ResponseBody
    public ResponseEntity<List<HanjaQuizQuestionDTO>> getQuizQuestions(
            @RequestParam(value = "count", defaultValue = "10") int count) {
        // Clamp count between 5 and 30
        int safeCount = Math.max(5, Math.min(30, count));
        List<HanjaQuizQuestionDTO> questions = hanjaService.getRandomQuizQuestions(safeCount);
        return ResponseEntity.ok(questions);
    }

    /**
     * REST API: Stats for the learner Hanja page header.
     * GET /api/learner/hanja/stats
     */
    @GetMapping("/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(hanjaService.getLearnerStats());
    }
}
