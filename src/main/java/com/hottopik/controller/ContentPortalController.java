package com.hottopik.controller;

import com.hottopik.dto.GrammarTypeDTO;
import com.hottopik.dto.UserDTO;
import com.hottopik.entity.Grammar;
import com.hottopik.entity.GrammarGroup;
import com.hottopik.service.AdminSystemService;
import com.hottopik.service.ContentManagementService;
import com.hottopik.service.GrammarGroupService;
import com.hottopik.service.GrammarService;
import com.hottopik.service.QuestionReportService;
import com.hottopik.service.VocabularyService;
import com.hottopik.service.exam.ExamComposerService;
import com.hottopik.service.exam.QuestionBankService;
import com.hottopik.service.UserService;
import com.hottopik.util.AuthHelper;
import com.hottopik.util.GrammarStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/content")
public class ContentPortalController {

    private final ContentManagementService contentService;
    private final VocabularyService vocabularyService;
    private final AdminSystemService adminSystemService;
    private final QuestionBankService questionBank;
    private final ExamComposerService examComposer;
    private final UserService userService;
    private final GrammarService grammarService;
    private final GrammarGroupService grammarGroupService;
    private final QuestionReportService questionReportService;

    @Autowired
    public ContentPortalController(ContentManagementService contentService,
                                   VocabularyService vocabularyService,
                                   AdminSystemService adminSystemService,
                                   QuestionBankService questionBank,
                                   ExamComposerService examComposer,
                                   UserService userService,
                                   GrammarService grammarService,
                                   GrammarGroupService grammarGroupService,
                                   QuestionReportService questionReportService) {
        this.contentService = contentService;
        this.vocabularyService = vocabularyService;
        this.adminSystemService = adminSystemService;
        this.questionBank = questionBank;
        this.examComposer = examComposer;
        this.userService = userService;
        this.grammarService = grammarService;
        this.grammarGroupService = grammarGroupService;
        this.questionReportService = questionReportService;
    }

    @GetMapping
    public String dashboard(HttpSession session, Model model) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);

        if (currentUser == null) {
            return "redirect:/login";
        }
        if (!currentUser.isContentManager() && !currentUser.isAdmin()) {
            if (currentUser.isOperationManager()) {
                return "redirect:/operation";
            }
            return "redirect:/today";
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("pageTitle", "Cổng Content Manager · HOT TOPIK");
        model.addAttribute("totalVocabCount", vocabularyService.getTotalCount());
        model.addAttribute("totalTopicCount", vocabularyService.getTotalTopicsCount());
        model.addAttribute("totalGrammarCount", grammarService.getAllGrammars().size());
        model.addAttribute("totalQuestionCount", contentService.getTotalQuestionCount());
        model.addAttribute("pendingReportCount", questionReportService.getPendingCount());
        model.addAttribute("totalExamCount", examComposer.libraryCounts().values().stream().mapToLong(Long::longValue).sum());
        model.addAttribute("reports", questionReportService.getAllReports());
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

        if (currentUser == null || (!currentUser.isContentManager() && !currentUser.isAdmin())) {
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
            @RequestParam(value = "note", required = false) String note,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!currentUser.isContentManager() && !currentUser.isAdmin())) {
            resp.put("success", false);
            resp.put("message", "Từ chối quyền truy cập.");
            return ResponseEntity.status(403).body(resp);
        }

        boolean ok = questionReportService.resolveReport(reportId, note, currentUser.getFullName());
        if (ok) {
            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "RESOLVE_CONTENT_REPORT",
                    "CONTENT_REPORT",
                    "Đã rà soát và giải quyết báo lỗi nội dung ID #" + reportId + ", tặng thưởng +5 Xu cho học viên",
                    "127.0.0.1"
            );
            resp.put("success", true);
            resp.put("message", "Đã giải quyết báo cáo và tặng +5 Xu thưởng cho học viên thành công!");
        } else {
            resp.put("success", false);
            resp.put("message", "Không tìm thấy báo cáo.");
        }
        return ResponseEntity.ok(resp);
    }


    @PostMapping("/exam/import")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> importExam(
            @RequestParam(value = "file", required = false) org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "examName", defaultValue = "Đề thi thử mới") String examName,
            @RequestParam(value = "questionCount", defaultValue = "50") int questionCount,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null || (!currentUser.isContentManager() && !currentUser.isAdmin())) {
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
    // GRAMMAR MANAGEMENT (TÍCH HỢP 3 VIEW MODE & API GIAO DIỆN)
    // ==========================================

    @GetMapping("/grammar")
    public String grammarPage(
            @RequestParam(value = "level", required = false) Integer level,
            @RequestParam(value = "groupId", required = false) Integer groupId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpSession session,
            Model model) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        if (currentUser == null) return "redirect:/login";
        if (!currentUser.isContentManager() && !currentUser.isAdmin()) {
            return "redirect:/today";
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("pageTitle", "Quản lý Kho Ngữ pháp · Content Manager · HOT TOPIK");
        model.addAttribute("userName", currentUser.getFullName());
        model.addAttribute("userEmail", currentUser.getEmail());
        model.addAttribute("totalCount", grammarService.getAllGrammars().size());
        model.addAttribute("grammarTypeCount", grammarGroupService.getAllGroups().size());

        // Determine view mode matching the UI
        if (level == null) {
            model.addAttribute("viewMode", "LEVEL_LIST");
            model.addAttribute("levels", contentService.getGrammarLevelSummaries());
        } else if (groupId == null) {
            model.addAttribute("viewMode", "GRAMMAR_TYPE_LIST");
            model.addAttribute("currentLevel", level);
            model.addAttribute("currentLevelName", "TOPIK " + (level <= 2 ? "I · Cấp " + level : "II · Cấp " + level));

            // ==========================================
            // ĐẶT ĐOẠN CODE CỦA BẠN VÀO ĐÂY:
            // ==========================================
            List<GrammarTypeDTO> grammarTypes = grammarGroupService.getGroupsByLevel(level.shortValue()).stream().map(g ->
                    new GrammarTypeDTO(
                            g.getId(),
                            g.getNameVi(),
                            (int) g.getLevel(),
                            grammarService.getGrammarsByGroup(g.getId()).size(),
                            "Nhóm ngữ pháp số " + g.getGroupNo(),
                            true
                    )
            ).collect(java.util.stream.Collectors.toList());

            model.addAttribute("grammarTypes", grammarTypes);
            // ==========================================

        } else {
            model.addAttribute("viewMode", "GRAMMAR_LIST");
            model.addAttribute("currentLevel", level);
            model.addAttribute("selectedGroupId", groupId);

            GrammarGroup group = grammarGroupService.getAllGroups().stream()
                    .filter(g -> g.getId().equals(groupId))
                    .findFirst().orElse(null);

            model.addAttribute("currentGrammarType", group != null ? group.getNameVi() : "Chi tiết nhóm");
            model.addAttribute("currentLevelName", "TOPIK " + level);
            model.addAttribute("grammars", grammarService.filterGrammars(level.shortValue(), groupId, status, keyword));
        }

        model.addAttribute("selectedLevel", level);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("keyword", keyword);
        model.addAttribute("statuses", GrammarStatus.values());

        return "content/grammar";
    }

    @PostMapping("/grammar/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveGrammar(
            @RequestBody com.hottopik.dto.GrammarForm form,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();
        if (currentUser == null) {
            resp.put("success", false);
            resp.put("message", "Chưa đăng nhập.");
            return ResponseEntity.status(401).body(resp);
        }
        try {
            grammarService.saveGrammar(form);
            resp.put("success", true);
            resp.put("message", "Đã lưu mẫu ngữ pháp thành công!");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @PostMapping("/grammar/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleGrammar(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Long id = Long.valueOf(payload.get("id").toString());
            Grammar grammar = grammarService.getGrammarById(id);
            String newStatus = "ACTIVE".equals(grammar.getStatus()) ? "HIDDEN" : "ACTIVE";
            grammarService.updateStatus(id, newStatus);
            resp.put("success", true);
            resp.put("message", "Đã chuyển trạng thái thành " + newStatus);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @PostMapping("/grammar/delete-type")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteGrammarType(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String name = (String) payload.get("name");
            GrammarGroup group = grammarGroupService.getAllGroups().stream()
                    .filter(g -> g.getNameVi().equals(name))
                    .findFirst().orElse(null);

            if (group != null) {
                grammarGroupService.deleteGroup(group.getId());
                resp.put("success", true);
                resp.put("message", "Đã xóa dạng ngữ pháp thành công!");
                return ResponseEntity.ok(resp);
            }
            resp.put("success", false);
            resp.put("message", "Không tìm thấy dạng ngữ pháp.");
            return ResponseEntity.status(404).body(resp);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.status(409).body(resp);
        }
    }

    @PostMapping("/grammar/bulk-create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> bulkCreateGrammar(
            @RequestBody List<com.hottopik.dto.GrammarForm> forms,
            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        int count = 0;
        if (forms != null) {
            for (com.hottopik.dto.GrammarForm form : forms) {
                try {
                    if (form.getStatus() == null) {
                        form.setStatus("DRAFT");
                    }
                    grammarService.saveGrammar(form);
                    count++;
                } catch (Exception ignored) {}
            }
        }
        resp.put("success", true);
        resp.put("created", count);
        resp.put("message", "Đã nhập thành công " + count + " mẫu ngữ pháp dạng Bản nháp!");
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
        if (!currentUser.isContentManager() && !currentUser.isAdmin()) {
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
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();
        if (currentUser == null) {
            resp.put("success", false);
            resp.put("message", "Vui lòng đăng nhập lại!");
            return ResponseEntity.status(401).body(resp);
        }

        String fullName = (String) payload.get("fullName");
        String phone = (String) payload.get("phone");
        String avatar = (String) payload.get("avatar");
        String bio = (String) payload.get("bio");
        String specialty = (String) payload.get("specialty");
        String timezone = (String) payload.get("timezone");

        if (fullName == null || fullName.trim().length() < 2 || fullName.trim().length() > 40) {
            resp.put("success", false);
            resp.put("message", "Họ và tên phải từ 2 đến 40 ký tự!");
            return ResponseEntity.badRequest().body(resp);
        }

        boolean ok = userService.updateUserProfile(currentUser.getId(), fullName, phone, avatar, bio, specialty, timezone);
        if (ok) {
            currentUser.setFullName(fullName.trim());
            currentUser.setDisplayName(fullName.trim());
            if (phone != null) currentUser.setPhone(phone.trim().isEmpty() ? null : phone.trim());
            if (avatar != null) currentUser.setAvatar(avatar.trim().isEmpty() ? null : avatar.trim());
            if (bio != null) currentUser.setBio(bio.trim().isEmpty() ? null : bio.trim());
            if (specialty != null) currentUser.setSpecialty(specialty.trim().isEmpty() ? null : specialty.trim());
            if (timezone != null) currentUser.setTimezone(timezone.trim().isEmpty() ? null : timezone.trim());

            session.setAttribute("LOGGED_IN_USER", currentUser);

            adminSystemService.logAction(
                    currentUser.getFullName(),
                    currentUser.getRole(),
                    "UPDATE_PROFILE",
                    "USER_PROFILE",
                    "Cập nhật thông tin hồ sơ Content Manager cá nhân",
                    "127.0.0.1"
            );

            resp.put("success", true);
            resp.put("user", currentUser);
            resp.put("message", "Đã cập nhật thông tin hồ sơ thành công!");
            return ResponseEntity.ok(resp);
        } else {
            resp.put("success", false);
            resp.put("message", "Không thể cập nhật hồ sơ lúc này!");
            return ResponseEntity.badRequest().body(resp);
        }
    }

    @PostMapping("/profile/clear-field")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearField(
            @RequestParam("field") String field,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();
        if (currentUser == null) {
            resp.put("success", false);
            resp.put("message", "Vui lòng đăng nhập lại!");
            return ResponseEntity.status(401).body(resp);
        }
        if ("phone".equalsIgnoreCase(field)) {
            userService.updateUserProfile(currentUser.getId(), null, "", null, null, null, null);
            currentUser.setPhone(null);
            session.setAttribute("LOGGED_IN_USER", currentUser);
            resp.put("success", true);
            resp.put("message", "Đã xóa số điện thoại khỏi hồ sơ!");
            return ResponseEntity.ok(resp);
        } else if ("avatar".equalsIgnoreCase(field)) {
            String defaultAvatar = "https://api.dicebear.com/7.x/bottts/svg?seed=" + currentUser.getEmail();
            userService.updateUserProfile(currentUser.getId(), null, null, defaultAvatar, null, null, null);
            currentUser.setAvatar(defaultAvatar);
            session.setAttribute("LOGGED_IN_USER", currentUser);
            resp.put("success", true);
            resp.put("avatar", defaultAvatar);
            resp.put("message", "Đã khôi phục ảnh đại diện mặc định!");
            return ResponseEntity.ok(resp);
        }
        resp.put("success", false);
        resp.put("message", "Trường thông tin không hợp lệ.");
        return ResponseEntity.badRequest().body(resp);
    }

    @PostMapping("/profile/change-password")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestBody Map<String, Object> payload,
            HttpSession session) {
        Map<String, Object> resp = new HashMap<>();
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        if (currentUser == null) {
            resp.put("success", false);
            resp.put("message", "Vui lòng đăng nhập lại!");
            return ResponseEntity.status(401).body(resp);
        }

        String oldPass = (String) payload.get("oldPassword");
        String newPass = (String) payload.get("newPassword");
        if (newPass == null || newPass.trim().length() < 6) {
            resp.put("success", false);
            resp.put("message", "Mật khẩu mới phải từ 6 ký tự trở lên!");
            return ResponseEntity.badRequest().body(resp);
        }

        boolean ok = userService.changePassword(currentUser.getId(), oldPass, newPass);
        if (ok) {
            resp.put("success", true);
            resp.put("message", "Đã đổi mật khẩu thành công!");
            return ResponseEntity.ok(resp);
        } else {
            resp.put("success", false);
            resp.put("message", "Mật khẩu hiện tại không đúng. Vui lòng kiểm tra lại!");
            return ResponseEntity.badRequest().body(resp);
        }
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
        if (currentUser == null || (!currentUser.isContentManager() && !currentUser.isAdmin())) {
            resp.put("success", false);
            resp.put("message", "Chưa đăng nhập hoặc không có quyền.");
            return ResponseEntity.status(401).body(resp);
        }
        Long reportId = payload.containsKey("reportId") ? Long.valueOf(payload.get("reportId").toString()) : null;
        String reason = payload.containsKey("reason") ? payload.get("reason").toString() : "";
        if (reportId != null) {
            boolean ok = questionReportService.rejectReport(reportId, reason, currentUser.getFullName());
            if (ok) {
                adminSystemService.logAction(
                        currentUser.getFullName(),
                        currentUser.getRole(),
                        "REJECT_CONTENT_REPORT",
                        "CONTENT_REPORT",
                        "Từ chối báo lỗi #" + reportId + " - Lý do: " + reason,
                        "127.0.0.1"
                );
                resp.put("success", true);
                resp.put("message", "Đã từ chối báo lỗi và gửi thông báo giải thích đến học viên!");
                return ResponseEntity.ok(resp);
            }
        }
        resp.put("success", false);
        resp.put("message", "Không tìm thấy báo cáo cần từ chối.");
        return ResponseEntity.badRequest().body(resp);
    }
    // Thêm API này vào ContentPortalController.java
    @PostMapping("/grammar/group/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveGrammarGroup(
            @RequestBody com.hottopik.dto.GrammarGroupForm form,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();
        if (currentUser == null) {
            resp.put("success", false);
            resp.put("message", "Chưa đăng nhập.");
            return ResponseEntity.status(401).body(resp);
        }
        try {
            grammarGroupService.saveGroup(form);
            resp.put("success", true);
            resp.put("message", "Đã tạo dạng ngữ pháp mới thành công!");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }
    @PostMapping("/grammar/update")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateGrammar(
            @RequestBody com.hottopik.dto.GrammarForm form,
            HttpSession session) {
        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        Map<String, Object> resp = new HashMap<>();

        if (currentUser == null) {
            resp.put("success", false);
            resp.put("message", "Chưa đăng nhập.");
            return ResponseEntity.status(401).body(resp);
        }

        try {
            // Gọi service xử lý cập nhật ngữ pháp dựa vào ID có sẵn trong form
            grammarService.updateGrammar(form);
            resp.put("success", true);
            resp.put("message", "Đã cập nhật mẫu ngữ pháp thành công!");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", "Lỗi cập nhật: " + e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        }
    }
}