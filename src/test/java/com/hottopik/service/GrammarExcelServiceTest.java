package com.hottopik.service;

import com.hottopik.dto.GrammarForm;
import com.hottopik.entity.Grammar;
import com.hottopik.entity.GrammarGroup;
import com.hottopik.repository.GrammarGroupRepository;
import com.hottopik.repository.GrammarRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GrammarExcelServiceTest {
    private final GrammarRepository grammars = mock(GrammarRepository.class);
    private final GrammarGroupRepository groups = mock(GrammarGroupRepository.class);
    private final GrammarService grammarService = mock(GrammarService.class);
    private final GrammarExcelService service = new GrammarExcelService(grammars, groups, grammarService);

    GrammarExcelServiceTest() {
        GrammarGroup g = new GrammarGroup();
        g.setId(7);
        g.setLevel((short) 2);
        g.setGroupNo((short) 1);
        g.setNameVi("Nối mệnh đề");
        when(groups.findById(7)).thenReturn(Optional.of(g));
        Grammar existing = new Grammar();
        existing.setPattern("-고 싶다");
        existing.setSortOrder((short) 4);
        when(grammars.findByGrammarGroup_IdOrderBySortOrderAscIdAsc(7)).thenReturn(new ArrayList<>(List.of(existing)));
    }

    private static MockMultipartFile xlsx(byte[] bytes) {
        return new MockMultipartFile("file", "ngu-phap.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    /** Mở file mẫu, ghi đè dữ liệu bằng các dòng cho sẵn rồi trả về bytes. */
    private byte[] templateWith(String[][] rows) throws Exception {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(service.buildTemplate(7)));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.getSheetAt(0);
            for (int r = s.getLastRowNum(); r >= 1; r--) if (s.getRow(r) != null) s.removeRow(s.getRow(r));
            for (int r = 0; r < rows.length; r++) {
                Row row = s.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) row.createCell(c).setCellValue(rows[r][c]);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void templateHasHeaderAndGuideSheet() throws Exception {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(service.buildTemplate(7)))) {
            assertEquals("NguPhap", wb.getSheetAt(0).getSheetName());
            assertEquals("Mẫu ngữ pháp (*)", wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertTrue(wb.getSheet("HuongDan").getRow(1).getCell(0).getStringCellValue().contains("Nối mệnh đề"));
        }
    }

    @Test
    void untouchedTemplateFlagsSampleAlreadyInGroup() {
        // File mẫu có sẵn "-고 싶다", mẫu này đã tồn tại trong nhóm nên phải bị đánh dấu lỗi.
        var preview = service.preview(xlsx(service.buildTemplate(7)), 7);
        assertEquals(2, preview.rows().size());
        assertTrue(preview.rows().get(0).valid());
        assertFalse(preview.rows().get(1).valid());
        assertTrue(preview.rows().get(1).message().contains("Đã có trong nhóm"));
    }

    @Test
    void previewValidatesEachRow() throws Exception {
        byte[] file = templateWith(new String[][]{
                {"-(으)니까", "Vì... nên", "", "", "", "có", "비가 오니까 우산을 가져가세요.", "Trời mưa nên hãy mang ô."},
                {"", "Thiếu mẫu", "", "", "", "", "", ""},
                {"-지만", "", "", "", "", "", "", ""},
                {"-(으)니까", "Trùng dòng 2", "", "", "", "", "", ""},
                {"-는데", "Nhưng", "", "", "", "có lẽ", "", ""},
                {"-거나", "Hoặc", "", "", "", "", "책을 읽거나", ""},
                {"", "", "", "", "", "", "", ""},
        });
        var p = service.preview(xlsx(file), 7);
        assertEquals(6, p.rows().size(), "dòng trống bị bỏ qua");
        assertEquals(1, p.validCount());
        var r = p.rows();
        assertTrue(r.get(0).valid());
        assertTrue(r.get(0).frequent());
        assertEquals(2, r.get(0).rowNo());
        assertTrue(r.get(1).message().contains("Thiếu mẫu ngữ pháp"));
        assertTrue(r.get(2).message().contains("Thiếu bản dịch"));
        assertTrue(r.get(3).message().contains("Trùng với dòng phía trên"));
        assertTrue(r.get(4).message().contains("Quan trọng"));
        assertTrue(r.get(5).message().contains("Ví dụ phải có đủ"));
    }

    @Test
    void previewRejectsWrongHeader() throws Exception {
        byte[] bytes;
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Row h = wb.createSheet().createRow(0);
            h.createCell(0).setCellValue("Tên");
            h.createCell(1).setCellValue("Nghĩa");
            wb.write(out);
            bytes = out.toByteArray();
        }
        var ex = assertThrows(IllegalArgumentException.class, () -> service.preview(xlsx(bytes), 7));
        assertTrue(ex.getMessage().contains("Sai định dạng file"));
    }

    @Test
    void confirmForcesDraftGroupAndSkipsDuplicates() {
        var ok = new GrammarExcelService.ImportRow(2, "-(으)니까", "Vì", "", "", "", true, "비가 오니까", "Vì mưa", true, "Hợp lệ");
        var dup = new GrammarExcelService.ImportRow(3, "-고 싶다", "Muốn", "", "", "", false, "", "", true, "Hợp lệ");
        var result = service.confirmImport(7, List.of(ok, dup));

        assertEquals(1, result.created());
        assertEquals(1, result.errors().size());
        ArgumentCaptor<GrammarForm> captor = ArgumentCaptor.forClass(GrammarForm.class);
        verify(grammarService).saveGrammar(captor.capture());
        GrammarForm f = captor.getValue();
        assertEquals("DRAFT", f.getStatus());
        assertEquals(7, f.getGroupId());
        assertEquals((short) 2, f.getLevel());
        assertEquals((short) 5, f.getSortOrder(), "nối tiếp sau thứ tự lớn nhất đang có");
        assertEquals(1, f.getExamples().size());
    }
}
