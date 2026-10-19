
package com.hottopik.controller;

import com.hottopik.dto.GrammarExampleForm;
import com.hottopik.dto.GrammarForm;
import com.hottopik.dto.GrammarGroupForm;
import com.hottopik.entity.Grammar;
import com.hottopik.entity.GrammarGroup;
import com.hottopik.service.GrammarExampleService;
import com.hottopik.service.GrammarGroupService;
import com.hottopik.service.GrammarService;
import com.hottopik.util.GrammarStatus;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class GrammarController {

    private final GrammarService grammarService;
    private final GrammarGroupService grammarGroupService;
    private final GrammarExampleService grammarExampleService;

    public GrammarController(
            GrammarService grammarService,
            GrammarGroupService grammarGroupService,
            GrammarExampleService grammarExampleService) {
        this.grammarService = grammarService;
        this.grammarGroupService = grammarGroupService;
        this.grammarExampleService = grammarExampleService;
    }

    // =====================================================
    // TRANG QUẢN LÝ KHO NGỮ PHÁP
    // =====================================================

    @GetMapping({
            "/content/grammars",
            "/admin/grammars"
    })
    public String grammarManagementPage(
            @RequestParam(required = false) Short level,
            @RequestParam(required = false) Integer groupId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            Model model) {

        List<Grammar> grammars = grammarService.filterGrammars(
                level,
                groupId,
                status,
                keyword
        );

        model.addAttribute("grammars", grammars);
        model.addAttribute(
                "grammarGroups",
                grammarGroupService.getAllGroups()
        );

        model.addAttribute("selectedLevel", level);
        model.addAttribute("selectedGroupId", groupId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("keyword", keyword);

        model.addAttribute("statuses", GrammarStatus.values());

        return "admin/grammar";
    }

    // =====================================================
    // API NGỮ PHÁP
    // =====================================================

    // GET /api/grammars
    // Lấy danh sách hoặc lọc ngữ pháp
    @GetMapping("/api/grammars")
    @ResponseBody
    public ResponseEntity<?> getGrammars(
            @RequestParam(required = false) Short level,
            @RequestParam(required = false) Integer groupId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        try {
            List<Grammar> grammars = grammarService.filterGrammars(
                    level,
                    groupId,
                    status,
                    keyword
            );

            return ResponseEntity.ok(grammars);
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // GET /api/grammars/{id}
    // Lấy chi tiết một ngữ pháp
    @GetMapping("/api/grammars/{id}")
    @ResponseBody
    public ResponseEntity<?> getGrammarById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(
                    grammarService.getGrammarById(id)
            );
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // POST /api/grammars
    // Thêm ngữ pháp mới
    @PostMapping("/api/grammars")
    @ResponseBody
    public ResponseEntity<?> createGrammar(
            @RequestBody GrammarForm form) {
        try {
            Grammar grammar = grammarService.saveGrammar(form);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(grammar);
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể thêm ngữ pháp. Vui lòng kiểm tra dữ liệu."
            );
        }
    }

    // PUT /api/grammars/{id}
    // Cập nhật ngữ pháp
    @PutMapping("/api/grammars/{id}")
    @ResponseBody
    public ResponseEntity<?> updateGrammar(
            @PathVariable Long id,
            @RequestBody GrammarForm form) {
        try {
            form.setId(id);

            Grammar grammar = grammarService.saveGrammar(form);

            return ResponseEntity.ok(grammar);
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể cập nhật ngữ pháp."
            );
        }
    }

    // DELETE /api/grammars/{id}
    // Xóa ngữ pháp
    @DeleteMapping("/api/grammars/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteGrammar(@PathVariable Long id) {
        try {
            grammarService.deleteGrammar(id);

            return ResponseEntity.ok(
                    successResponse("Xóa ngữ pháp thành công.")
            );
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.CONFLICT,
                    "Không thể xóa ngữ pháp. Có thể ngữ pháp đang được câu hỏi sử dụng."
            );
        }
    }

    // PATCH /api/grammars/{id}/status
    // Cập nhật trạng thái
    @PatchMapping("/api/grammars/{id}/status")
    @ResponseBody
    public ResponseEntity<?> updateGrammarStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            String status = request.get("status");

            Grammar grammar = grammarService.updateStatus(id, status);

            return ResponseEntity.ok(grammar);
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể cập nhật trạng thái."
            );
        }
    }

    // PATCH /api/grammars/{id}/frequent?frequent=true
    // Đánh dấu hoặc bỏ đánh dấu thường gặp
    @PatchMapping("/api/grammars/{id}/frequent")
    @ResponseBody
    public ResponseEntity<?> updateGrammarFrequent(
            @PathVariable Long id,
            @RequestParam boolean frequent) {
        try {
            Grammar grammar = grammarService.updateFrequent(id, frequent);

            return ResponseEntity.ok(grammar);
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể cập nhật trạng thái thường gặp."
            );
        }
    }

    // =====================================================
    // API NHÓM NGỮ PHÁP
    // =====================================================

    // GET /api/grammar-groups
    @GetMapping("/api/grammar-groups")
    @ResponseBody
    public ResponseEntity<?> getGrammarGroups(
            @RequestParam(required = false) Short level) {
        try {
            List<GrammarGroup> groups = level == null
                    ? grammarGroupService.getAllGroups()
                    : grammarGroupService.getGroupsByLevel(level);

            return ResponseEntity.ok(groups);
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // POST /api/grammar-groups
    @PostMapping("/api/grammar-groups")
    @ResponseBody
    public ResponseEntity<?> createGrammarGroup(
            @RequestBody GrammarGroupForm form) {
        try {
            GrammarGroup group = grammarGroupService.saveGroup(form);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(group);
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể thêm nhóm ngữ pháp."
            );
        }
    }

    // PUT /api/grammar-groups/{id}
    @PutMapping("/api/grammar-groups/{id}")
    @ResponseBody
    public ResponseEntity<?> updateGrammarGroup(
            @PathVariable Integer id,
            @RequestBody GrammarGroupForm form) {
        try {
            form.setId(id);

            GrammarGroup group = grammarGroupService.saveGroup(form);

            return ResponseEntity.ok(group);
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể cập nhật nhóm ngữ pháp."
            );
        }
    }

    // DELETE /api/grammar-groups/{id}
    @DeleteMapping("/api/grammar-groups/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteGrammarGroup(
            @PathVariable Integer id) {
        try {
            grammarGroupService.deleteGroup(id);

            return ResponseEntity.ok(
                    successResponse("Xóa nhóm ngữ pháp thành công.")
            );
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            return errorResponse(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.CONFLICT,
                    "Không thể xóa nhóm ngữ pháp."
            );
        }
    }

    // =====================================================
    // API CÂU VÍ DỤ
    // =====================================================

    // GET /api/grammars/{grammarId}/examples
    @GetMapping("/api/grammars/{grammarId}/examples")
    @ResponseBody
    public ResponseEntity<?> getGrammarExamples(
            @PathVariable Long grammarId) {
        try {
            return ResponseEntity.ok(
                    grammarExampleService.getExamplesByGrammarId(grammarId)
            );
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // POST /api/grammars/{grammarId}/examples
    @PostMapping("/api/grammars/{grammarId}/examples")
    @ResponseBody
    public ResponseEntity<?> createGrammarExample(
            @PathVariable Long grammarId,
            @RequestBody GrammarExampleForm form) {
        try {
            form.setId(null);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(grammarExampleService.saveExample(grammarId, form));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể thêm câu ví dụ."
            );
        }
    }

    // PUT /api/grammars/{grammarId}/examples/{exampleId}
    @PutMapping("/api/grammars/{grammarId}/examples/{exampleId}")
    @ResponseBody
    public ResponseEntity<?> updateGrammarExample(
            @PathVariable Long grammarId,
            @PathVariable Long exampleId,
            @RequestBody GrammarExampleForm form) {
        try {
            form.setId(exampleId);

            return ResponseEntity.ok(
                    grammarExampleService.saveExample(grammarId, form)
            );
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể cập nhật câu ví dụ."
            );
        }
    }

    // DELETE /api/grammars/{grammarId}/examples/{exampleId}
    @DeleteMapping("/api/grammars/{grammarId}/examples/{exampleId}")
    @ResponseBody
    public ResponseEntity<?> deleteGrammarExample(
            @PathVariable Long grammarId,
            @PathVariable Long exampleId) {
        try {
            grammarExampleService.deleteExample(grammarId, exampleId);

            return ResponseEntity.ok(
                    successResponse("Xóa câu ví dụ thành công.")
            );
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            return errorResponse(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể xóa câu ví dụ."
            );
        }
    }

    // =====================================================
    // HÀM TẠO RESPONSE THỐNG NHẤT
    // =====================================================

    private Map<String, Object> successResponse(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", message);
        return response;
    }

    private ResponseEntity<Map<String, Object>> errorResponse(
            HttpStatus status,
            String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("message", message);

        return ResponseEntity.status(status).body(response);
    }
}
