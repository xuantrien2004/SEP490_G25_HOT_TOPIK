package com.hottopik.controller;

import com.hottopik.dto.HanjaImportResultDTO;
import com.hottopik.dto.HanjaRootDTO;
import com.hottopik.dto.UserDTO;
import com.hottopik.service.HanjaService;
import com.hottopik.util.AuthHelper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping({"/content/hanja", "/admin/hanja"})
public class ContentHanjaController {

    private final HanjaService hanjaService;

    @Autowired
    public ContentHanjaController(HanjaService hanjaService) {
        this.hanjaService = hanjaService;
    }

    /**
     * View Kho từ Hán Việt page under Quản lý Lý thuyết
     */
    @GetMapping
    public String viewHanjaManagement(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "status", required = false) String status,
            HttpSession session,
            Model model) {

        UserDTO currentUser = AuthHelper.getLoggedInUser(session);
        if (currentUser == null) {
            currentUser = new UserDTO(2L, "content_manager", "content@hottopik.com", "", "Lê Thu Hà", "ROLE_CONTENT_MANAGER", "Content Manager", "ACTIVE", "", "", "TOPIK_6", "TOPIK_5", false, null);
        }

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("currentRole", "content");
        model.addAttribute("roleTitle", currentUser.getRoleName() != null ? currentUser.getRoleName() : "Content Manager");
        model.addAttribute("userName", currentUser.getFullName());
        model.addAttribute("userEmail", currentUser.getEmail());
        model.addAttribute("pageTitle", "Kho từ Hán Việt · Quản lý Lý thuyết · HOT TOPIK");

        // Counters
        model.addAttribute("totalRootsCount", hanjaService.getTotalRootsCount());
        model.addAttribute("activeRootsCount", hanjaService.getActiveRootsCount());
        model.addAttribute("draftRootsCount", hanjaService.getDraftRootsCount());
        model.addAttribute("totalRelatedWordsCount", hanjaService.getTotalRelatedWordsCount());

        // Initial list
        List<HanjaRootDTO> roots = hanjaService.getAllHanjaRoots(query, status);
        model.addAttribute("hanjaRoots", roots);
        model.addAttribute("currentQuery", query != null ? query : "");
        model.addAttribute("currentStatus", status != null ? status : "");

        return "content/hanja";
    }

    /**
     * REST API: List Hanja roots with search & filter
     */
    @GetMapping("/api/list")
    @ResponseBody
    public ResponseEntity<List<HanjaRootDTO>> listRoots(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(hanjaService.getAllHanjaRoots(query, status));
    }

    /**
     * REST API: Get single Hanja root details with related words
     */
    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<?> getRootDetail(@PathVariable("id") Integer id) {
        HanjaRootDTO dto = hanjaService.getHanjaRootById(id);
        if (dto == null) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Không tìm thấy từ Hán Việt có ID: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
        }
        return ResponseEntity.ok(dto);
    }

    /**
     * REST API: Create Hanja root manually
     */
    @PostMapping("/api/create")
    @ResponseBody
    public ResponseEntity<?> createRoot(@RequestBody HanjaRootDTO dto) {
        try {
            HanjaRootDTO created = hanjaService.createHanjaRoot(dto);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "Thêm từ Hán Việt '" + created.getHanjaChar() + "' thành công!");
            resp.put("data", created);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Lỗi tạo từ: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * REST API: Update Hanja root
     */
    @PutMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<?> updateRoot(@PathVariable("id") Integer id, @RequestBody HanjaRootDTO dto) {
        try {
            HanjaRootDTO updated = hanjaService.updateHanjaRoot(id, dto);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "Cập nhật từ Hán Việt '" + updated.getHanjaChar() + "' thành công!");
            resp.put("data", updated);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Lỗi cập nhật: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    /**
     * REST API: Delete Hanja root
     */
    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteRoot(@PathVariable("id") Integer id) {
        try {
            hanjaService.deleteHanjaRoot(id);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "Đã xóa từ Hán Việt thành công!");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Lỗi xóa từ: " + e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    /**
     * Download sample Excel template (.xlsx)
     */
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadExcelTemplate() {
        byte[] excelBytes = hanjaService.exportTemplateExcel();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "Mau_Kho_Tu_Han_Viet.xlsx");
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
    }

    /**
     * Import Excel file with duplicate and existing checks
     */
    @PostMapping("/import")
    @ResponseBody
    public ResponseEntity<HanjaImportResultDTO> importExcel(@RequestParam("file") MultipartFile file) {
        HanjaImportResultDTO result = hanjaService.importExcel(file);
        return ResponseEntity.ok(result);
    }
}
