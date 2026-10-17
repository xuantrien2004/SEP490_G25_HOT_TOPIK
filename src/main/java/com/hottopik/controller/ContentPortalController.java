package com.hottopik.controller;

import com.hottopik.dto.UserDTO;
import com.hottopik.service.AdminSystemService;
import com.hottopik.service.ContentManagementService;
import com.hottopik.service.VocabularyService;
import com.hottopik.util.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/content")
public class ContentPortalController {

    private final ContentManagementService contentService;
    private final VocabularyService vocabularyService;
    private final AdminSystemService adminSystemService;

    @Autowired
    public ContentPortalController(ContentManagementService contentService,
                                   VocabularyService vocabularyService,
                                   AdminSystemService adminSystemService) {
        this.contentService = contentService;
        this.vocabularyService = vocabularyService;
        this.adminSystemService = adminSystemService;
    }

    @GetMapping
    public String dashboard(HttpSession session, Model model) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);

        // Security check: Only Content Manager (or Admin supervising)
        if (currentUser == null) {
            return "redirect:/login";
        }
        if (!"ROLE_CONTENT_MANAGER".equalsIgnoreCase(currentUser.getRole())
                && !"ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            if ("ROLE_OPERATION_MANAGER".equalsIgnoreCase(currentUser.getRole())) {
                return "redirect:/operation";
            }
            return "redirect:/today";
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("pageTitle", "Cổng Content Manager · HOT TOPIK");
        model.addAttribute("totalVocabCount", vocabularyService.getTotalCount());
        model.addAttribute("totalTopicCount", vocabularyService.getTotalTopicsCount());
        model.addAttribute("totalGrammarCount", contentService.getTotalGrammarCount());
        model.addAttribute("totalQuestionCount", contentService.getTotalQuestionCount());
        model.addAttribute("pendingReportCount", contentService.getPendingReportCount());
        model.addAttribute("totalExamCount", contentService.getTotalExamCount());
        model.addAttribute("questions", contentService.getQuestions());
        model.addAttribute("mockTests", contentService.getMockTests());
        model.addAttribute("mockExams", contentService.getMockTests());
        model.addAttribute("reports", contentService.getReports());
        model.addAttribute("auditLogs", adminSystemService.getAuditLogsForRole("ROLE_CONTENT_MANAGER"));

        return "content/dashboard";
    }

    @PostMapping("/exam/toggle-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleExamStatus(
            @RequestParam("testId") Long testId,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();
        if (currentUser == null) {
            resp.put("success", false);
            resp.put("message", "Chưa đăng nhập.");
            return ResponseEntity.status(401).body(resp);
        }
        boolean ok = contentService.toggleMockTestStatus(testId);
        resp.put("success", ok);
        resp.put("message", ok ? "Đã thay đổi trạng thái phát hành đề thi!" : "Không tìm thấy đề.");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/mock-test/submit-approval")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitApproval(
            @RequestParam("testId") Long testId,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!"ROLE_CONTENT_MANAGER".equalsIgnoreCase(currentUser.getRole()) && !"ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole()))) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        boolean ok = contentService.submitMockTestApproval(testId, currentUser.getFullName());
        if (ok) {
            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "SUBMIT_APPROVAL",
                    "MOCK_TEST",
                    "Gửi yêu cầu duyệt phát hành đề thi ID #" + testId + " lên Admin",
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("message", "Đã gửi yêu cầu phê duyệt đề thi thử lên Admin thành công!");
        } else {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy đề thi.");
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/report/resolve")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resolveReport(
            @RequestParam("reportId") Long reportId,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!"ROLE_CONTENT_MANAGER".equalsIgnoreCase(currentUser.getRole()) && !"ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole()))) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        boolean ok = contentService.resolveReport(reportId);
        if (ok) {
            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "RESOLVE_CONTENT_REPORT",
                    "CONTENT_REPORT",
                    "Đã rà soát và giải quyết báo lỗi nội dung ID #" + reportId + ", tặng thưởng Xu cho người báo",
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("message", "Đã giải quyết báo cáo và tặng Xu thưởng cho học viên thành công!");
        } else {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy báo cáo.");
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/exam/import")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> importExam(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "examName", defaultValue = "Đề thi thử mới") String examName,
            @RequestParam(value = "questionCount", defaultValue = "50") int questionCount,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!"ROLE_CONTENT_MANAGER".equalsIgnoreCase(currentUser.getRole()) && !"ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole()))) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        String fileName = (file != null && !file.isEmpty()) ? file.getOriginalFilename() : examName;
        int count = contentService.importExamsMock(fileName, questionCount);

        adminSystemService.logAction(
                currentUser.getFullName(),
                currentUser.getRole(),
                "IMPORT_EXAM",
                "MOCK_TEST",
                "Import đề thi mới từ tập tin: " + fileName + " (" + count + " câu hỏi)",
                "127.0.0.1"
        );

        resp.put("success", true);
        resp.put("count", count);
        resp.put("message", "Đã nhập đề thi thành công (" + count + " câu hỏi được thêm vào hệ thống dạng bản nháp)!");
        return ResponseEntity.ok(resp);
    }

    // ==========================================
    // GRAMMAR MANAGEMENT
    // ==========================================

    @GetMapping("/grammar")
    public String grammarPage(
            @RequestParam(value = "level", required = false) Integer level,
            @RequestParam(value = "grammarType", required = false) String grammarType,
            HttpSession session,
            Model model) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        if (currentUser == null) return "redirect:/login";
        if (!"ROLE_CONTENT_MANAGER".equalsIgnoreCase(currentUser.getRole())
                && !"ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            return "redirect:/today";
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("pageTitle", "Quản lý Ngữ pháp · Content Manager · HOT TOPIK");
        model.addAttribute("userName", currentUser.getFullName());
        model.addAttribute("userEmail", currentUser.getEmail());
        model.addAttribute("currentLevel", level);
        model.addAttribute("currentGrammarType", grammarType);
        model.addAttribute("totalCount", contentService.getTotalGrammarCount());
        model.addAttribute("activeCount", contentService.getActiveGrammarCount());
        model.addAttribute("inactiveCount", contentService.getInactiveGrammarCount());
        model.addAttribute("grammarTypeCount", contentService.getTotalGrammarTypeCount());

        // Determine view mode
        if (level == null) {
            model.addAttribute("viewMode", "LEVEL_LIST");
            model.addAttribute("levels", contentService.getGrammarLevelSummaries());
        } else if (grammarType == null) {
            model.addAttribute("viewMode", "GRAMMAR_TYPE_LIST");
            model.addAttribute("currentLevelName", "TOPIK " + level);
            model.addAttribute("grammarTypes", contentService.getGrammarTypesForLevel(level));
        } else {
            model.addAttribute("viewMode", "GRAMMAR_LIST");
            model.addAttribute("currentLevelName", "TOPIK " + level);
            model.addAttribute("grammars", contentService.getGrammarsForTypeAndLevel(level, grammarType));
        }

        return "content/grammar";
    }

    @PostMapping("/grammar/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveGrammar(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();
        if (currentUser == null) {
            resp.put("success", false);
            resp.put("message", "Chưa đăng nhập.");
            return ResponseEntity.status(401).body(resp);
        }
        resp.put("success", true);
        resp.put("message", "Đã lưu ngữ pháp thành công!");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/grammar/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleGrammar(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Đã cập nhật trạng thái ngữ pháp!");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/grammar/delete-type")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteGrammarType(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Đã xóa dạng ngữ pháp!");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/grammar/bulk-create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> bulkCreateGrammar(
            @RequestBody java.util.List<Map<String, Object>> payload,
            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("created", payload != null ? payload.size() : 0);
        resp.put("message", "Đã tạo " + (payload != null ? payload.size() : 0) + " ngữ pháp thành công (trạng thái: Nháp)!");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/grammar/check-delete-type")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkDeleteGrammarType(
            @RequestParam int level,
            @RequestParam String name,
            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("grammarCount", 0);
        resp.put("typeName", name);
        return ResponseEntity.ok(resp);
    }

    // ==========================================
    // PROFILE PAGE
    // ==========================================

    @GetMapping("/profile")
    public String profilePage(HttpSession session, Model model) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        if (currentUser == null) return "redirect:/login";
        if (!"ROLE_CONTENT_MANAGER".equalsIgnoreCase(currentUser.getRole())
                && !"ROLE_ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            return "redirect:/today";
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("pageTitle", "Hồ sơ cá nhân · Content Manager · HOT TOPIK");
        model.addAttribute("recentLogs", adminSystemService.getAuditLogsForRole("ROLE_CONTENT_MANAGER")
                .stream().limit(5).collect(java.util.stream.Collectors.toList()));
        return "content/profile";
    }

    @PostMapping("/profile/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Đã cập nhật thông tin hồ sơ!");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/profile/change-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "Đã cập nhật mật khẩu thành công!");
        return ResponseEntity.ok(resp);
    }

    // ==========================================
    // REJECT REPORT
    // ==========================================

    @PostMapping("/report/reject")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rejectReport(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();
        if (currentUser == null) {
            resp.put("success", false);
            resp.put("message", "Chưa đăng nhập.");
            return ResponseEntity.status(401).body(resp);
        }
        Long reportId = payload.containsKey("reportId") ? Long.valueOf(payload.get("reportId").toString()) : null;
        if (reportId != null) {
            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "REJECT_CONTENT_REPORT",
                    "CONTENT_REPORT",
                    "Từ chối báo lỗi #" + reportId + " - Không xác nhận lỗi, đã thông báo học viên",
                    "127.0.0.1"
            );
        }
        resp.put("success", true);
        resp.put("message", "Đã từ chối báo lỗi và thông báo học viên!");
        return ResponseEntity.ok(resp);
    }
}
