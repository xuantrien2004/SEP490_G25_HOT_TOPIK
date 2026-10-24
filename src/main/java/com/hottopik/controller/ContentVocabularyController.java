package com.hottopik.controller;

import com.hottopik.dto.BulkToggleRequest;
import com.hottopik.dto.TopicDTO;
import com.hottopik.dto.TopikLevelSummaryDTO;
import com.hottopik.dto.UserDTO;
import com.hottopik.dto.VocabularyDTO;
import com.hottopik.service.VocabularyService;
import com.hottopik.util.KoreanRomanizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.*;

@Controller
@RequestMapping({"/content/vocabulary", "/admin/vocabulary"})
public class ContentVocabularyController {

    private final VocabularyService vocabularyService;

    @Autowired
    public ContentVocabularyController(VocabularyService vocabularyService) {
        this.vocabularyService = vocabularyService;
    }

    @GetMapping
    public String viewVocabularyHierarchy(
            @RequestParam(value = "level", required = false) Integer level,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            HttpSession session,
            Model model) {

        UserDTO currentUser = com.hottopik.util.AuthHelper.getLoggedInUser(session);
        if (currentUser == null) {
            currentUser = new UserDTO(2L, "content_manager", "content@hottopik.com", "", "Lê Thu Hà", "ROLE_CONTENT_MANAGER", "Content Manager", "ACTIVE", "", "", "TOPIK_6", "TOPIK_5", false, null);
        }

        // Global counters
        model.addAttribute("totalCount", vocabularyService.getTotalCount());
        model.addAttribute("activeCount", vocabularyService.getActiveCount());
        model.addAttribute("inactiveCount", vocabularyService.getInactiveCount());
        model.addAttribute("topicCount", vocabularyService.getTotalTopicsCount());

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("currentRole", "content");
        model.addAttribute("roleTitle", currentUser.getRoleName() != null ? currentUser.getRoleName() : "Content Manager");
        model.addAttribute("userName", currentUser.getFullName());
        model.addAttribute("userEmail", currentUser.getEmail());
        model.addAttribute("pageTitle", "Quản lý Từ vựng · Content Manager · HOT TOPIK");

        // Determine view level:
        // 1. LEVEL_LIST: No level chosen (Display TOPIK 1 to 6 cards)
        // 2. TOPIC_LIST: Level chosen, no topic chosen (Display list of topics for that level)
        // 3. VOCAB_LIST: Level and topic both chosen (Display vocabularies for that topic)
        if (level == null || level < 1 || level > 6) {
            model.addAttribute("viewMode", "LEVEL_LIST");
            List<TopikLevelSummaryDTO> summaries = vocabularyService.getTopikLevelSummaries();
            model.addAttribute("levelSummaries", summaries);
            return "admin/vocabulary";
        }

        if (topic == null || topic.trim().isEmpty() || "ALL".equalsIgnoreCase(topic.trim())) {
            model.addAttribute("viewMode", "TOPIC_LIST");
            model.addAttribute("currentLevel", level);
            model.addAttribute("currentLevelName", VocabularyDTO.formatTopikName(level));
            List<TopicDTO> topicList = vocabularyService.getTopicsByLevel(level);
            model.addAttribute("topics", topicList);
            return "admin/vocabulary";
        }

        // View Mode: VOCAB_LIST
        model.addAttribute("viewMode", "VOCAB_LIST");
        model.addAttribute("currentLevel", level);
        model.addAttribute("currentLevelName", VocabularyDTO.formatTopikName(level));
        model.addAttribute("currentTopic", topic.trim());

        List<VocabularyDTO> list = vocabularyService.searchAndFilter(query, level, topic, type, status);
        model.addAttribute("vocabularies", list);
        model.addAttribute("allWordTypes", vocabularyService.getAllWordTypes());
        model.addAttribute("selectedQuery", query != null ? query : "");
        model.addAttribute("selectedType", type != null ? type : "ALL");
        model.addAttribute("selectedStatus", status != null ? status : "ALL");

        return "admin/vocabulary";
    }

    // ==========================================
    // TOPIC CRUD ENDPOINTS
    // ==========================================
    @GetMapping("/topic/check-delete")
    @ResponseBody
    public ResponseEntity<?> checkTopicDelete(
            @RequestParam("level") int level,
            @RequestParam("name") String name) {
        int wordCount = vocabularyService.getWordCountInTopic(level, name);
        return ResponseEntity.ok(Map.of(
                "level", level,
                "name", name,
                "wordCount", wordCount
        ));
    }

    @PostMapping("/topic/create")
    @ResponseBody
    public ResponseEntity<?> createTopic(@RequestBody Map<String, Object> body) {
        try {
            int level = Integer.parseInt(body.get("level").toString());
            String name = body.get("name").toString().trim();
            String desc = body.containsKey("description") && body.get("description") != null ? body.get("description").toString().trim() : "";

            if (name.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Tên chủ đề không được để trống!"));
            }

            TopicDTO topic = vocabularyService.createTopic(level, name, desc);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Đã tạo chủ đề \"" + name + "\" cho TOPIK " + level + " thành công!",
                    "data", topic
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Dữ liệu không hợp lệ!"));
        }
    }

    @PostMapping("/topic/update")
    @ResponseBody
    public ResponseEntity<?> updateTopic(@RequestBody Map<String, Object> body) {
        try {
            int level = Integer.parseInt(body.get("level").toString());
            String oldName = body.get("oldName").toString().trim();
            String newName = body.get("newName").toString().trim();
            String desc = body.containsKey("description") && body.get("description") != null ? body.get("description").toString().trim() : null;

            if (newName.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Tên mới không được để trống!"));
            }

            TopicDTO updated = vocabularyService.updateTopic(level, oldName, newName, desc);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Đã đổi tên chủ đề thành \"" + newName + "\" thành công!",
                    "data", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Dữ liệu không hợp lệ!"));
        }
    }

    @PostMapping("/topic/delete")
    @ResponseBody
    public ResponseEntity<?> deleteTopic(@RequestBody Map<String, Object> body) {
        try {
            int level = Integer.parseInt(body.get("level").toString());
            String name = body.get("name").toString().trim();

            int deletedWords = vocabularyService.deleteTopic(level, name);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "deletedWordsCount", deletedWords,
                    "message", "Đã xóa vĩnh viễn chủ đề \"" + name + "\" và " + deletedWords + " từ vựng liên quan!"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Lỗi khi xóa chủ đề!"));
        }
    }

    @PostMapping("/topic/toggle")
    @ResponseBody
    public ResponseEntity<?> toggleTopic(@RequestBody Map<String, Object> body) {
        try {
            int level = Integer.parseInt(body.get("level").toString());
            String name = body.get("name").toString().trim();
            boolean active = Boolean.parseBoolean(body.get("active").toString());

            int affected = vocabularyService.toggleTopicStatus(level, name, active);
            String state = active ? "Kích hoạt" : "Ẩn";
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "affectedCount", affected,
                    "message", "Đã " + state + " chủ đề \"" + name + "\" (" + affected + " từ)!"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Lỗi khi đổi trạng thái chủ đề!"));
        }
    }

    // ==========================================
    // VOCABULARY CRUD & BULK ENDPOINTS
    // ==========================================
    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> getVocabularyDetail(@PathVariable("id") Long id) {
        VocabularyDTO dto = vocabularyService.getById(id);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<?> saveVocabulary(@RequestBody VocabularyDTO dto) {
        if (dto.getWord() == null || dto.getWord().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Vui lòng nhập từ tiếng Hàn."));
        }
        if (dto.getMeaning() == null || dto.getMeaning().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Vui lòng nhập nghĩa tiếng Việt."));
        }
        if (dto.getTopikLevel() == null || dto.getTopikLevel() < 1 || dto.getTopikLevel() > 6) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Cấp TOPIK phải từ 1 đến 6."));
        }
        if (dto.getTopic() == null || dto.getTopic().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Vui lòng chọn hoặc nhập chủ đề."));
        }

        // Auto romanize if not provided
        if (dto.getRomanization() == null || dto.getRomanization().trim().isEmpty()) {
            dto.setRomanization(KoreanRomanizer.romanize(dto.getWord()));
        }

        boolean isNew = (dto.getId() == null || dto.getId() <= 0);
        boolean existedBefore = false;
        if (isNew && dto.getTopikLevel() != null && dto.getWord() != null) {
            existedBefore = vocabularyService.existsWordInLevel(dto.getTopikLevel(), dto.getWord());
        }

        VocabularyDTO saved = vocabularyService.saveOrUpdate(dto);

        String message;
        if (isNew && existedBefore) {
            message = "Từ vựng \"" + saved.getWord() + "\" đã tồn tại trong TOPIK " + saved.getTopikLevel() + "! Hệ thống đã tự động cập nhật thông tin (chỉ lưu 1 từ duy nhất).";
        } else if (isNew) {
            message = "Thêm mới từ vựng thành công!";
        } else {
            message = "Cập nhật từ vựng thành công!";
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", message,
                "data", saved
        ));
    }

    @PostMapping("/toggle/{id}")
    @ResponseBody
    public ResponseEntity<?> toggleWord(@PathVariable("id") Long id) {
        boolean success = vocabularyService.toggleWordStatus(id);
        if (!success) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Không tìm thấy từ vựng có ID " + id));
        }
        VocabularyDTO updated = vocabularyService.getById(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "newStatus", updated.getStatus(),
                "message", "Đã chuyển trạng thái sang " + ("ACTIVE".equalsIgnoreCase(updated.getStatus()) ? "Kích hoạt" : "Ẩn")
        ));
    }

    @PostMapping("/bulk-toggle")
    @ResponseBody
    public ResponseEntity<?> bulkToggle(@RequestBody BulkToggleRequest request) {
        if (request == null || request.getType() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Yêu cầu không hợp lệ."));
        }

        int affected = 0;
        String desc = "";

        if ("LEVEL".equalsIgnoreCase(request.getType())) {
            try {
                int level = Integer.parseInt(request.getTarget());
                affected = vocabularyService.toggleTopikLevelStatus(level, request.isActive());
                desc = "cấp " + VocabularyDTO.formatTopikName(level) + " (" + affected + " từ)";
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Cấp TOPIK không hợp lệ."));
            }
        } else {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Loại đối tượng không hỗ trợ: " + request.getType()));
        }

        String stateText = request.isActive() ? "Kích hoạt" : "Ẩn";
        return ResponseEntity.ok(Map.of(
                "success", true,
                "affectedCount", affected,
                "message", "Đã " + stateText + " toàn bộ " + desc + " thành công!"
        ));
    }

    @PostMapping("/bulk-create")
    @ResponseBody
    public ResponseEntity<?> bulkCreate(@RequestBody List<VocabularyDTO> list) {
        if (list == null || list.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Danh sách từ rỗng."));
        }

        int totalInput = list.size();
        int count = vocabularyService.bulkCreate(list);
        int skipped = totalInput - count;

        String msg;
        if (skipped > 0) {
            msg = "Đã nhập thành công " + count + " từ mới! (Đã tự động loại bỏ " + skipped + " từ bị trùng lặp)";
        } else {
            msg = "Đã tạo thành công " + count + " từ vựng vào hệ thống!";
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", count,
                "skipped", skipped,
                "message", msg
        ));
    }
}
