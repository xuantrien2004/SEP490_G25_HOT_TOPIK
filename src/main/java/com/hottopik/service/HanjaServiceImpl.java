package com.hottopik.service;

import com.hottopik.dto.HanjaImportPreviewDTO;
import com.hottopik.dto.HanjaImportResultDTO;
import com.hottopik.dto.HanjaQuizQuestionDTO;
import com.hottopik.dto.HanjaRootDTO;
import com.hottopik.dto.HanjaWordDTO;
import java.time.format.DateTimeFormatter;
import com.hottopik.entity.HanjaRoot;
import com.hottopik.entity.HanjaRootWord;
import com.hottopik.entity.HanjaRootWordId;
import com.hottopik.entity.Vocabulary;
import com.hottopik.repository.HanjaRootRepository;
import com.hottopik.repository.HanjaRootWordRepository;
import com.hottopik.repository.VocabularyRepository;
import jakarta.annotation.PostConstruct;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class HanjaServiceImpl implements HanjaService {

    private final HanjaRootRepository hanjaRootRepository;
    private final HanjaRootWordRepository hanjaRootWordRepository;
    private final VocabularyRepository vocabularyRepository;

    @Autowired
    public HanjaServiceImpl(HanjaRootRepository hanjaRootRepository,
                            HanjaRootWordRepository hanjaRootWordRepository,
                            VocabularyRepository vocabularyRepository) {
        this.hanjaRootRepository = hanjaRootRepository;
        this.hanjaRootWordRepository = hanjaRootWordRepository;
        this.vocabularyRepository = vocabularyRepository;
    }

    @PostConstruct
    public void init() {
        try {
            seedInitialHanjaRootsIfEmpty();
        } catch (Exception e) {
            System.err.println("Notice: Hanja seed check: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<HanjaRootDTO> getAllHanjaRoots(String query, String status) {
        List<HanjaRoot> roots = hanjaRootRepository.searchRoots(query, status);
        List<HanjaRootDTO> result = new ArrayList<>();
        for (HanjaRoot root : roots) {
            result.add(convertToDTO(root));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public HanjaRootDTO getHanjaRootById(Integer id) {
        return hanjaRootRepository.findById(id)
                .map(this::convertToDTO)
                .orElse(null);
    }

    @Override
    @Transactional
    public HanjaRootDTO createHanjaRoot(HanjaRootDTO dto) {
        if (dto.getHanjaChar() == null || dto.getHanjaChar().trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập Chữ Hán!");
        }
        if (dto.getKoReading() == null || dto.getKoReading().trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập Tiếng Hàn!");
        }
        if (dto.getSinoVi() == null || dto.getSinoVi().trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập Âm Hán Việt!");
        }
        String cleanChar = dto.getHanjaChar().trim();
        if (hanjaRootRepository.existsByHanjaChar(cleanChar)) {
            throw new IllegalArgumentException("Chữ Hán '" + cleanChar + "' đã tồn tại trong hệ thống!");
        }

        // Mặc định trạng thái luôn là DRAFT khi tạo mới
        HanjaRoot root = HanjaRoot.builder()
                .hanjaChar(cleanChar)
                .sinoVi(dto.getSinoVi().trim())
                .koReading(dto.getKoReading().trim())
                .meaningVi(dto.getMeaningVi() != null ? dto.getMeaningVi().trim() : "")
                .audioUrl(dto.getAudioUrl() != null ? dto.getAudioUrl().trim() : "")
                .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : (short) 0)
                .status("DRAFT")
                .build();

        HanjaRoot savedRoot = hanjaRootRepository.save(root);

        // Save related words nếu có
        if (dto.getRelatedWords() != null && !dto.getRelatedWords().isEmpty()) {
            saveRelatedWords(savedRoot, dto.getRelatedWords());
        }

        return convertToDTO(savedRoot);
    }

    @Override
    @Transactional
    public HanjaRootDTO updateHanjaRoot(Integer id, HanjaRootDTO dto) {
        HanjaRoot root = hanjaRootRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy từ Hán Việt có ID: " + id));

        String cleanChar = dto.getHanjaChar() != null ? dto.getHanjaChar().trim() : root.getHanjaChar();
        if (!root.getHanjaChar().equalsIgnoreCase(cleanChar) && hanjaRootRepository.existsByHanjaChar(cleanChar)) {
            throw new IllegalArgumentException("Chữ Hán '" + cleanChar + "' đã tồn tại!");
        }

        root.setHanjaChar(cleanChar);
        if (dto.getSinoVi() != null) root.setSinoVi(dto.getSinoVi().trim());
        if (dto.getKoReading() != null) root.setKoReading(dto.getKoReading().trim());
        if (dto.getMeaningVi() != null) root.setMeaningVi(dto.getMeaningVi().trim());
        if (dto.getAudioUrl() != null) root.setAudioUrl(dto.getAudioUrl().trim());
        if (dto.getSortOrder() != null) root.setSortOrder(dto.getSortOrder());
        if (dto.getStatus() != null && !dto.getStatus().trim().isEmpty()) {
            root.setStatus(dto.getStatus().trim().toUpperCase());
        }

        HanjaRoot savedRoot = hanjaRootRepository.save(root);

        // Chỉ cập nhật từ liên quan nếu explicitly cung cấp mảng (không null)
        if (dto.getRelatedWords() != null && !dto.getRelatedWords().isEmpty()) {
            hanjaRootWordRepository.deleteByRootId(savedRoot.getId());
            saveRelatedWords(savedRoot, dto.getRelatedWords());
        }

        return convertToDTO(savedRoot);
    }

    @Override
    @Transactional
    public HanjaRootDTO toggleRootStatus(Integer id) {
        HanjaRoot root = hanjaRootRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy từ Hán Việt có ID: " + id));
        if ("ACTIVE".equalsIgnoreCase(root.getStatus())) {
            root.setStatus("DRAFT");
        } else {
            root.setStatus("ACTIVE");
        }
        HanjaRoot savedRoot = hanjaRootRepository.save(root);
        return convertToDTO(savedRoot);
    }

    @Override
    @Transactional
    public void deleteHanjaRoot(Integer id) {
        if (!hanjaRootRepository.existsById(id)) {
            throw new IllegalArgumentException("Không tìm thấy từ Hán Việt có ID: " + id);
        }
        hanjaRootWordRepository.deleteByRootId(id);
        hanjaRootRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HanjaWordDTO> getWordsByRootId(Integer rootId) {
        List<HanjaWordDTO> words = new ArrayList<>();
        List<HanjaRootWord> links = hanjaRootWordRepository.findByHanjaRootId(rootId);
        for (HanjaRootWord link : links) {
            Vocabulary v = link.getVocabulary();
            if (v != null) {
                words.add(HanjaWordDTO.builder()
                        .vocabularyId(v.getId())
                        .hanjaWord(link.getHanjaWord())
                        .word(v.getWord())
                        .sinoVi(v.getSinoVi())
                        .meaningVi(v.getMeaningVi())
                        .audioUrl(v.getAudioUrl())
                        .status(v.getStatus())
                        .build());
            }
        }
        return words;
    }

    @Override
    @Transactional
    public HanjaWordDTO createWord(Integer rootId, HanjaWordDTO dto) {
        HanjaRoot root = hanjaRootRepository.findById(rootId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chữ Hán gốc có ID: " + rootId));
        if (dto.getWord() == null || dto.getWord().trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập Từ tiếng Hàn!");
        }
        if (dto.getHanjaWord() == null || dto.getHanjaWord().trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập Chữ Hán ghép!");
        }
        if (dto.getSinoVi() == null || dto.getSinoVi().trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập Âm Hán Việt!");
        }
        if (dto.getMeaningVi() == null || dto.getMeaningVi().trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập Nghĩa tiếng Việt!");
        }

        String koWord = dto.getWord().trim();
        String hanjaWord = dto.getHanjaWord().trim();
        String rootChar = root.getHanjaChar() != null ? root.getHanjaChar().trim() : "";
        if (!rootChar.isEmpty() && !hanjaWord.contains(rootChar)) {
            throw new IllegalArgumentException("Chữ Hán ghép bắt buộc phải chứa chữ Hán gốc '" + rootChar + "'!");
        }

        String sinoVi = dto.getSinoVi().trim();
        String meaningVi = dto.getMeaningVi().trim();
        String audioUrl = dto.getAudioUrl() != null ? dto.getAudioUrl().trim() : "";
        // Mặc định từ mới cũng luôn là DRAFT
        String status = dto.getStatus() != null && !dto.getStatus().trim().isEmpty() ? dto.getStatus().trim().toUpperCase() : "DRAFT";

        Vocabulary vocab = Vocabulary.builder()
                .topicId(1)
                .level((short) 1)
                .word(koWord)
                .hanja(hanjaWord)
                .sinoVi(sinoVi)
                .meaningVi(meaningVi)
                .audioUrl(audioUrl)
                .status(status)
                .build();
        vocab = vocabularyRepository.save(vocab);

        HanjaRootWordId linkId = new HanjaRootWordId(root.getId(), vocab.getId());
        HanjaRootWord link = HanjaRootWord.builder()
                .id(linkId)
                .hanjaRoot(root)
                .vocabulary(vocab)
                .hanjaWord(hanjaWord)
                .build();
        hanjaRootWordRepository.save(link);

        return HanjaWordDTO.builder()
                .vocabularyId(vocab.getId())
                .word(vocab.getWord())
                .hanjaWord(hanjaWord)
                .sinoVi(vocab.getSinoVi())
                .meaningVi(vocab.getMeaningVi())
                .audioUrl(vocab.getAudioUrl())
                .status(vocab.getStatus())
                .build();
    }

    @Override
    @Transactional
    public HanjaWordDTO updateWord(Long vocabId, HanjaWordDTO dto) {
        Vocabulary vocab = vocabularyRepository.findById(vocabId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy từ vựng có ID: " + vocabId));

        if (dto.getWord() != null && !dto.getWord().trim().isEmpty()) {
            vocab.setWord(dto.getWord().trim());
        }
        if (dto.getHanjaWord() != null && !dto.getHanjaWord().trim().isEmpty()) {
            vocab.setHanja(dto.getHanjaWord().trim());
        }
        if (dto.getSinoVi() != null) vocab.setSinoVi(dto.getSinoVi().trim());
        if (dto.getMeaningVi() != null) vocab.setMeaningVi(dto.getMeaningVi().trim());
        if (dto.getAudioUrl() != null) vocab.setAudioUrl(dto.getAudioUrl().trim());
        if (dto.getStatus() != null && !dto.getStatus().trim().isEmpty()) {
            vocab.setStatus(dto.getStatus().trim().toUpperCase());
        }
        vocab = vocabularyRepository.save(vocab);

        return HanjaWordDTO.builder()
                .vocabularyId(vocab.getId())
                .word(vocab.getWord())
                .hanjaWord(vocab.getHanja())
                .sinoVi(vocab.getSinoVi())
                .meaningVi(vocab.getMeaningVi())
                .audioUrl(vocab.getAudioUrl())
                .status(vocab.getStatus())
                .build();
    }

    @Override
    @Transactional
    public void deleteWord(Integer rootId, Long vocabId) {
        hanjaRootWordRepository.deleteByRootIdAndVocabId(rootId, vocabId);
    }

    @Override
    @Transactional
    public HanjaWordDTO toggleWordStatus(Long vocabId) {
        Vocabulary vocab = vocabularyRepository.findById(vocabId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy từ vựng có ID: " + vocabId));
        if ("ACTIVE".equalsIgnoreCase(vocab.getStatus())) {
            vocab.setStatus("DRAFT");
        } else {
            vocab.setStatus("ACTIVE");
        }
        vocab = vocabularyRepository.save(vocab);

        return HanjaWordDTO.builder()
                .vocabularyId(vocab.getId())
                .word(vocab.getWord())
                .hanjaWord(vocab.getHanja())
                .sinoVi(vocab.getSinoVi())
                .meaningVi(vocab.getMeaningVi())
                .audioUrl(vocab.getAudioUrl())
                .status(vocab.getStatus())
                .build();
    }

    private void saveRelatedWords(HanjaRoot root, List<HanjaWordDTO> wordDTOs) {
        for (HanjaWordDTO w : wordDTOs) {
            if (w.getWord() == null || w.getWord().trim().isEmpty()) {
                continue;
            }
            String koWord = w.getWord().trim();
            String hanjaWord = w.getHanjaWord() != null ? w.getHanjaWord().trim() : root.getHanjaChar();

            // Find or create vocabulary
            Vocabulary vocab = vocabularyRepository.findFirstByWordAndHanja(koWord, hanjaWord)
                    .orElse(null);

            if (vocab == null) {
                vocab = Vocabulary.builder()
                        .topicId(1)
                        .level((short) 1)
                        .word(koWord)
                        .hanja(hanjaWord)
                        .sinoVi(w.getSinoVi() != null ? w.getSinoVi().trim() : "")
                        .meaningVi(w.getMeaningVi() != null ? w.getMeaningVi().trim() : "")
                        .audioUrl(w.getAudioUrl() != null ? w.getAudioUrl().trim() : "")
                        .status(w.getStatus() != null ? w.getStatus().trim().toUpperCase() : "ACTIVE")
                        .build();
                vocab = vocabularyRepository.save(vocab);
            } else {
                // Update vocabulary info if provided
                if (w.getSinoVi() != null && !w.getSinoVi().isEmpty()) vocab.setSinoVi(w.getSinoVi().trim());
                if (w.getMeaningVi() != null && !w.getMeaningVi().isEmpty()) vocab.setMeaningVi(w.getMeaningVi().trim());
                if (w.getAudioUrl() != null && !w.getAudioUrl().isEmpty()) vocab.setAudioUrl(w.getAudioUrl().trim());
                vocab = vocabularyRepository.save(vocab);
            }

            // Create link in hanja_root_words
            HanjaRootWordId linkId = new HanjaRootWordId(root.getId(), vocab.getId());
            HanjaRootWord link = HanjaRootWord.builder()
                    .id(linkId)
                    .hanjaRoot(root)
                    .vocabulary(vocab)
                    .hanjaWord(hanjaWord)
                    .build();
            hanjaRootWordRepository.save(link);
        }
    }

    private HanjaRootDTO convertToDTO(HanjaRoot root) {
        List<HanjaWordDTO> relatedWords = new ArrayList<>();
        List<HanjaRootWord> links = hanjaRootWordRepository.findByHanjaRootId(root.getId());
        for (HanjaRootWord link : links) {
            Vocabulary v = link.getVocabulary();
            if (v != null) {
                relatedWords.add(HanjaWordDTO.builder()
                        .vocabularyId(v.getId())
                        .hanjaWord(link.getHanjaWord())
                        .word(v.getWord())
                        .sinoVi(v.getSinoVi())
                        .meaningVi(v.getMeaningVi())
                        .audioUrl(v.getAudioUrl())
                        .status(v.getStatus())
                        .build());
            }
        }

        return HanjaRootDTO.builder()
                .id(root.getId())
                .hanjaChar(root.getHanjaChar())
                .sinoVi(root.getSinoVi())
                .koReading(root.getKoReading())
                .meaningVi(root.getMeaningVi())
                .audioUrl(root.getAudioUrl())
                .sortOrder(root.getSortOrder())
                .status(root.getStatus())
                .createdAt(root.getCreatedAt())
                .updatedAt(root.getUpdatedAt())
                .relatedWords(relatedWords)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalRootsCount() {
        return hanjaRootRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long getActiveRootsCount() {
        return hanjaRootRepository.countByStatus("ACTIVE");
    }

    @Override
    @Transactional(readOnly = true)
    public long getDraftRootsCount() {
        return hanjaRootRepository.countByStatus("DRAFT");
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalRelatedWordsCount() {
        return hanjaRootWordRepository.count();
    }

    @Override
    public byte[] exportTemplateExcel() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Kho Từ Hán Việt");

            // Title Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Instructions Style
            CellStyle noteStyle = workbook.createCellStyle();
            Font noteFont = workbook.createFont();
            noteFont.setItalic(true);
            noteFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            noteStyle.setFont(noteFont);

            // Header row
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(28);

            String[] columns = {
                    "Chữ Hán (*)",
                    "Âm Hán Việt (*)",
                    "Tiếng Hàn (*)",
                    "Âm thanh đọc",
                    "Từ liên quan (Hàn|Hán|Hán Việt|Nghĩa ; ...)"
            };

            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            // Sample data rows (Đã bỏ cột Nghĩa gốc và Trạng thái)
            Object[][] sampleData = {
                    {"學", "Học", "학", "", "학교|學校|học hiệu|trường học; 학생|學生|học sinh|học sinh; 대학|大學|đại học|trường đại học"},
                    {"生", "Sinh", "생", "", "학생|學生|học sinh|học sinh; 선생님|先生|tiên sinh|giáo viên; 생활|生活|sinh hoạt|cuộc sống sinh hoạt"},
                    {"人", "Nhân", "인", "", "인간|人間|nhân gian|loài người; 외국인|外國人|ngoại quốc nhân|người nước ngoài; 인기|人氣|nhân khí|sự nổi tiếng"},
                    {"國", "Quốc", "국", "", "한국|韓國|hàn quốc|nước Hàn Quốc; 외국|外國|ngoại quốc|nước ngoài; 국가|國家|quốc gia|đất nước"},
                    {"會", "Hội", "회", "", "회의|會議|hội nghị|cuộc họp; 사회|社會|xã hội|xã hội; 회사|會社|hội xã|công ty"}
            };

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            for (int r = 0; r < sampleData.length; r++) {
                Row row = sheet.createRow(r + 1);
                row.setHeightInPoints(22);
                for (int c = 0; c < sampleData[r].length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue((String) sampleData[r][c]);
                    cell.setCellStyle(dataStyle);
                }
            }

            // Auto-size columns
            sheet.setColumnWidth(0, 15 * 256);
            sheet.setColumnWidth(1, 20 * 256);
            sheet.setColumnWidth(2, 16 * 256);
            sheet.setColumnWidth(3, 25 * 256);
            sheet.setColumnWidth(4, 75 * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xuất file Excel mẫu: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public HanjaImportResultDTO importExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return HanjaImportResultDTO.builder()
                    .success(false)
                    .message("Vui lòng chọn file Excel (.xlsx hoặc .xls) để tải lên!")
                    .build();
        }

        HanjaImportResultDTO result = HanjaImportResultDTO.builder()
                .success(true)
                .message("Phân tích và import dữ liệu hoàn tất")
                .build();

        Set<String> seenInFile = new HashSet<>();
        List<HanjaRootDTO> validToSave = new ArrayList<>();

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                result.setSuccess(false);
                result.setMessage("File Excel không có dữ liệu!");
                return result;
            }

            int totalRows = 0;
            int duplicateInFileCount = 0;
            int existingInDbCount = 0;
            int errorCount = 0;

            int rowCount = sheet.getLastRowNum();
            for (int r = 1; r <= rowCount; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String hanjaChar = getCellString(row.getCell(0));
                String sinoVi = getCellString(row.getCell(1));
                String koReading = getCellString(row.getCell(2));

                // If empty row, skip
                if (hanjaChar.isEmpty() && sinoVi.isEmpty() && koReading.isEmpty()) {
                    continue;
                }

                totalRows++;

                // Validation 1: Required fields (Chữ Hán, Âm Hán Việt, Tiếng Hàn)
                if (hanjaChar.isEmpty() || sinoVi.isEmpty() || koReading.isEmpty()) {
                    errorCount++;
                    result.getErrorDetails().add("Dòng " + (r + 1) + ": Thiếu trường bắt buộc (Chữ Hán, Âm Hán Việt, Tiếng Hàn)");
                    continue;
                }

                String upperChar = hanjaChar.trim();

                // Validation 2: Duplicate in this Excel file
                if (seenInFile.contains(upperChar)) {
                    duplicateInFileCount++;
                    result.getDuplicateInFileDetails().add("Dòng " + (r + 1) + ": Từ Hán '" + upperChar + "' (" + sinoVi + ") bị trùng lặp trong file Excel");
                    continue;
                }
                seenInFile.add(upperChar);

                // Validation 3: Already exists in Database
                if (hanjaRootRepository.existsByHanjaChar(upperChar)) {
                    existingInDbCount++;
                    result.getExistingInDbDetails().add("Dòng " + (r + 1) + ": Từ Hán '" + upperChar + "' (" + sinoVi + ") đã tồn tại trong hệ thống");
                    continue;
                }

                // Parse audio and related words flexibly
                String audioUrl = "";
                String relatedRaw = "";
                String col3 = getCellString(row.getCell(3));
                String col4 = getCellString(row.getCell(4));
                String col5 = getCellString(row.getCell(5));
                String col6 = getCellString(row.getCell(6));

                if (col3.contains("|")) {
                    // col3 is related words
                    relatedRaw = col3;
                } else if (col4.contains("|")) {
                    // 5-column format: 0:char, 1:sino, 2:reading, 3:audio, 4:related
                    audioUrl = col3;
                    relatedRaw = col4;
                } else if (col6.contains("|") || !col6.isEmpty()) {
                    // 7-column format: 0:char, 1:sino, 2:reading, 3:meaning, 4:status, 5:audio, 6:related
                    audioUrl = col5;
                    relatedRaw = col6;
                } else {
                    audioUrl = col3;
                    relatedRaw = col4;
                }

                // Parse related words: 학교|學校|học hiệu|trường học; ...
                List<HanjaWordDTO> relatedWords = new ArrayList<>();
                if (!relatedRaw.isEmpty()) {
                    String[] items = relatedRaw.split(";");
                    for (String item : items) {
                        String trimmed = item.trim();
                        if (trimmed.isEmpty()) continue;
                        String[] parts = trimmed.split("\\|");
                        String rWord = parts.length > 0 ? parts[0].trim() : "";
                        String rHanja = parts.length > 1 ? parts[1].trim() : upperChar;
                        String rSino = parts.length > 2 ? parts[2].trim() : "";
                        String rMeaning = parts.length > 3 ? parts[3].trim() : "";

                        if (!rWord.isEmpty()) {
                            relatedWords.add(HanjaWordDTO.builder()
                                    .word(rWord)
                                    .hanjaWord(rHanja)
                                    .sinoVi(rSino)
                                    .meaningVi(rMeaning)
                                    .status("DRAFT") // Mặc định từ liên quan cũng là DRAFT
                                    .build());
                        }
                    }
                }

                HanjaRootDTO validDTO = HanjaRootDTO.builder()
                        .hanjaChar(upperChar)
                        .sinoVi(sinoVi.trim())
                        .koReading(koReading.trim())
                        .meaningVi("")
                        .status("DRAFT") // Luôn là Bản nháp/Ẩn (DRAFT)
                        .audioUrl(audioUrl.trim())
                        .relatedWords(relatedWords)
                        .build();

                validToSave.add(validDTO);
            }

            // Save valid items into database
            int successCount = 0;
            for (HanjaRootDTO dto : validToSave) {
                createHanjaRoot(dto);
                successCount++;
                result.getSuccessDetails().add("Thêm thành công: " + dto.getHanjaChar() + " (" + dto.getSinoVi() + " - " + dto.getKoReading() + ")");
            }

            result.setTotalRows(totalRows);
            result.setDuplicateInFileCount(duplicateInFileCount);
            result.setExistingInDbCount(existingInDbCount);
            result.setErrorCount(errorCount);
            result.setSuccessCount(successCount);

            if (successCount == 0 && (duplicateInFileCount > 0 || existingInDbCount > 0 || errorCount > 0)) {
                result.setMessage("Không có từ mới nào được thêm. Vui lòng kiểm tra các từ bị trùng hoặc lỗi bên dưới.");
            } else {
                result.setMessage("Đã import thành công " + successCount + "/" + totalRows + " từ vào hệ thống.");
            }

            return result;

        } catch (Exception e) {
            result.setSuccess(false);
            result.setMessage("Lỗi xử lý file Excel: " + e.getMessage());
            return result;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public HanjaImportPreviewDTO previewImportExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return HanjaImportPreviewDTO.builder()
                    .success(false)
                    .message("Vui lòng chọn file Excel (.xlsx hoặc .xls) để phân tích!")
                    .build();
        }

        HanjaImportPreviewDTO preview = HanjaImportPreviewDTO.builder()
                .success(true)
                .message("Phân tích file hoàn tất. Vui lòng kiểm tra đối chiếu dữ liệu trước khi xác nhận lưu.")
                .build();

        Map<String, int[]> seenInFile = new HashMap<>();
        Map<String, String[]> seenDetails = new HashMap<>();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                preview.setSuccess(false);
                preview.setMessage("File Excel không có dữ liệu!");
                return preview;
            }

            int totalRows = 0;
            int rowCount = sheet.getLastRowNum();

            for (int r = 1; r <= rowCount; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String hanjaChar = getCellString(row.getCell(0));
                String sinoVi = getCellString(row.getCell(1));
                String koReading = getCellString(row.getCell(2));

                // If completely empty row, skip
                if (hanjaChar.isEmpty() && sinoVi.isEmpty() && koReading.isEmpty()) {
                    continue;
                }

                totalRows++;
                int displayRow = r + 1; // 1-indexed row number as seen in Excel

                // Check 1: Required fields
                if (hanjaChar.isEmpty() || sinoVi.isEmpty() || koReading.isEmpty()) {
                    List<String> missingFields = new ArrayList<>();
                    if (hanjaChar.isEmpty()) missingFields.add("Chữ Hán");
                    if (sinoVi.isEmpty()) missingFields.add("Âm Hán Việt");
                    if (koReading.isEmpty()) missingFields.add("Tiếng Hàn");

                    preview.getErrorRows().add(HanjaImportPreviewDTO.ErrorRowDetail.builder()
                            .rowIndex(displayRow)
                            .hanjaChar(hanjaChar)
                            .sinoVi(sinoVi)
                            .koReading(koReading)
                            .errorMessage("Thiếu trường bắt buộc: " + String.join(", ", missingFields))
                            .build());
                    continue;
                }

                String upperChar = hanjaChar.trim();

                // Check 2: Duplicate in this Excel file
                if (seenInFile.containsKey(upperChar)) {
                    int originalRow = seenInFile.get(upperChar)[0];
                    String[] origData = seenDetails.get(upperChar);
                    preview.getDuplicateInFileRows().add(HanjaImportPreviewDTO.DuplicateInFileDetail.builder()
                            .rowIndex(displayRow)
                            .hanjaChar(upperChar)
                            .sinoVi(sinoVi.trim())
                            .koReading(koReading.trim())
                            .originalRowIndex(originalRow)
                            .originalSinoVi(origData != null && origData.length > 0 ? origData[0] : "")
                            .originalKoReading(origData != null && origData.length > 1 ? origData[1] : "")
                            .build());
                    continue;
                }
                seenInFile.put(upperChar, new int[]{displayRow});
                seenDetails.put(upperChar, new String[]{sinoVi.trim(), koReading.trim()});

                // Check 3: Already exists in Database (Provide full source data for comparison!)
                Optional<HanjaRoot> existingOpt = hanjaRootRepository.findByHanjaChar(upperChar);
                if (existingOpt.isPresent()) {
                    HanjaRoot dbRoot = existingOpt.get();
                    String formattedDate = dbRoot.getCreatedAt() != null ? dbRoot.getCreatedAt().format(dtf) : "N/A";
                    int countWords = hanjaRootWordRepository.findByHanjaRootId(dbRoot.getId()).size();

                    preview.getExistingInDbRows().add(HanjaImportPreviewDTO.ExistingInDbDetail.builder()
                            .rowIndex(displayRow)
                            .fileHanjaChar(upperChar)
                            .fileSinoVi(sinoVi.trim())
                            .fileKoReading(koReading.trim())
                            .dbId(dbRoot.getId())
                            .dbHanjaChar(dbRoot.getHanjaChar())
                            .dbSinoVi(dbRoot.getSinoVi())
                            .dbKoReading(dbRoot.getKoReading())
                            .dbMeaningVi(dbRoot.getMeaningVi() != null ? dbRoot.getMeaningVi() : "")
                            .dbStatus(dbRoot.getStatus())
                            .dbRelatedWordsCount(countWords)
                            .dbCreatedAt(formattedDate)
                            .build());
                    continue;
                }

                // Parse audio and related words
                String audioUrl = "";
                String relatedRaw = "";
                String col3 = getCellString(row.getCell(3));
                String col4 = getCellString(row.getCell(4));
                String col5 = getCellString(row.getCell(5));
                String col6 = getCellString(row.getCell(6));

                if (col3.contains("|")) {
                    relatedRaw = col3;
                } else if (col4.contains("|")) {
                    audioUrl = col3;
                    relatedRaw = col4;
                } else if (col6.contains("|") || !col6.isEmpty()) {
                    audioUrl = col5;
                    relatedRaw = col6;
                } else {
                    audioUrl = col3;
                    relatedRaw = col4;
                }

                List<HanjaWordDTO> relatedWords = new ArrayList<>();
                if (!relatedRaw.isEmpty()) {
                    String[] items = relatedRaw.split(";");
                    for (String item : items) {
                        String trimmed = item.trim();
                        if (trimmed.isEmpty()) continue;
                        String[] parts = trimmed.split("\\|");
                        String rWord = parts.length > 0 ? parts[0].trim() : "";
                        String rHanja = parts.length > 1 ? parts[1].trim() : upperChar;
                        String rSino = parts.length > 2 ? parts[2].trim() : "";
                        String rMeaning = parts.length > 3 ? parts[3].trim() : "";

                        if (!rWord.isEmpty()) {
                            relatedWords.add(HanjaWordDTO.builder()
                                    .word(rWord)
                                    .hanjaWord(rHanja)
                                    .sinoVi(rSino)
                                    .meaningVi(rMeaning)
                                    .status("DRAFT")
                                    .build());
                        }
                    }
                }

                HanjaRootDTO validDTO = HanjaRootDTO.builder()
                        .hanjaChar(upperChar)
                        .sinoVi(sinoVi.trim())
                        .koReading(koReading.trim())
                        .meaningVi("")
                        .status("DRAFT")
                        .audioUrl(audioUrl.trim())
                        .relatedWords(relatedWords)
                        .build();

                preview.getValidRows().add(HanjaImportPreviewDTO.ValidRowDetail.builder()
                        .rowIndex(displayRow)
                        .hanjaChar(upperChar)
                        .sinoVi(sinoVi.trim())
                        .koReading(koReading.trim())
                        .audioUrl(audioUrl.trim())
                        .relatedWordsCount(relatedWords.size())
                        .rootDto(validDTO)
                        .build());
            }

            preview.setTotalRows(totalRows);
            preview.setValidCount(preview.getValidRows().size());
            preview.setDuplicateInFileCount(preview.getDuplicateInFileRows().size());
            preview.setExistingInDbCount(preview.getExistingInDbRows().size());
            preview.setErrorCount(preview.getErrorRows().size());

            return preview;

        } catch (Exception e) {
            preview.setSuccess(false);
            preview.setMessage("Lỗi phân tích file Excel: " + e.getMessage());
            return preview;
        }
    }

    @Override
    @Transactional
    public Map<String, Object> confirmImport(List<HanjaRootDTO> rootsToSave) {
        Map<String, Object> resp = new HashMap<>();
        if (rootsToSave == null || rootsToSave.isEmpty()) {
            resp.put("success", false);
            resp.put("message", "Không có từ nào được chọn để lưu!");
            resp.put("savedCount", 0);
            return resp;
        }

        int savedCount = 0;
        List<String> savedChars = new ArrayList<>();
        List<String> skippedChars = new ArrayList<>();

        for (HanjaRootDTO dto : rootsToSave) {
            if (dto.getHanjaChar() == null || dto.getHanjaChar().trim().isEmpty()) {
                continue;
            }
            String cleanChar = dto.getHanjaChar().trim();
            if (hanjaRootRepository.existsByHanjaChar(cleanChar)) {
                skippedChars.add(cleanChar);
                continue;
            }
            // Always ensure DRAFT status
            dto.setStatus("DRAFT");
            createHanjaRoot(dto);
            savedCount++;
            savedChars.add(cleanChar);
        }

        resp.put("success", true);
        resp.put("savedCount", savedCount);
        resp.put("savedChars", savedChars);
        resp.put("skippedChars", skippedChars);
        resp.put("message", "Đã lưu thành công " + savedCount + " chữ Hán vào hệ thống ở trạng thái Bản nháp!");
        return resp;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue() != null ? cell.getStringCellValue().trim() : "";
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    @Override
    @Transactional
    public void seedInitialHanjaRootsIfEmpty() {
        if (hanjaRootRepository.count() > 0) {
            return;
        }

        // 32 Standard Hanja Roots from Prototype v21 / Spec v3
        Object[][] seeds = {
                {"學", "học", "학", "Học tập, trường học", new String[][]{
                        {"학교", "學校", "học hiệu", "trường học"},
                        {"학생", "學生", "học sinh", "học sinh"},
                        {"대학", "大學", "đại học", "trường đại học"},
                        {"과학", "科學", "khoa học", "khoa học"},
                        {"학원", "學院", "học viện", "trung tâm học thêm"}
                }},
                {"生", "sinh", "생", "Sự sống, sinh sôi, phát sinh", new String[][]{
                        {"학생", "學生", "học sinh", "học sinh"},
                        {"선생님", "先生", "tiên sinh", "giáo viên"},
                        {"생활", "生活", "sinh hoạt", "sinh hoạt, cuộc sống"},
                        {"생각", "生覺", "sinh giác", "suy nghĩ, ý kiến"}
                }},
                {"人", "nhân", "인", "Con người, nhân cách", new String[][]{
                        {"인간", "人間", "nhân gian", "loài người"},
                        {"외국인", "外國人", "ngoại quốc nhân", "người nước ngoài"},
                        {"인기", "人氣", "nhân khí", "sự nổi tiếng"},
                        {"인사", "人事", "nhân sự", "chào hỏi, nhân sự"}
                }},
                {"國", "quốc", "국", "Đất nước, quốc gia", new String[][]{
                        {"한국", "韓國", "hàn quốc", "Hàn Quốc"},
                        {"외국", "外國", "ngoại quốc", "nước ngoài"},
                        {"국가", "國家", "quốc gia", "quốc gia, đất nước"}
                }},
                {"會", "hội", "회", "Hội họp, tập hợp, đoàn thể", new String[][]{
                        {"회의", "會議", "hội nghị", "cuộc họp"},
                        {"사회", "社會", "xã hội", "xã hội"},
                        {"회사", "會社", "hội xã", "công ty"}
                }},
                {"大", "đại", "대", "To lớn, vĩ đại", new String[][]{
                        {"대학", "大學", "đại học", "đại học"},
                        {"대통령", "大統領", "đại thống lĩnh", "tổng thống"},
                        {"위대하다", "偉大", "vĩ đại", "vĩ đại"}
                }},
                {"電", "điện", "전", "Điện, điện lực, điện tử", new String[][]{
                        {"전화", "電話", "điện thoại", "điện thoại"},
                        {"전기", "電氣", "điện khí", "điện, điện năng"},
                        {"전자", "電子", "điện tử", "thiết bị điện tử"}
                }},
                {"話", "thoại", "화", "Lời nói, đối thoại, câu chuyện", new String[][]{
                        {"전화", "電話", "điện thoại", "điện thoại"},
                        {"대화", "對話", "đối thoại", "đối thoại, trò chuyện"},
                        {"이야기", "話", "thoại", "câu chuyện"}
                }},
                {"時", "thời", "시", "Thời gian, thời khắc, giờ", new String[][]{
                        {"시간", "時間", "thời gian", "thời gian, tiếng đồng hồ"},
                        {"시작", "始作", "thủy tác", "bắt đầu"},
                        {"시험", "試驗", "thí nghiệm", "kỳ thi, bài kiểm tra"}
                }},
                {"間", "gian", "간", "Khoảng cách, ở giữa, thời hạn", new String[][]{
                        {"시간", "時間", "thời gian", "thời gian"},
                        {"인간", "人間", "nhân gian", "con người"},
                        {"기간", "期間", "kỳ gian", "khoảng thời gian"}
                }},
                {"家", "gia", "가", "Gia đình, nhà, chuyên gia", new String[][]{
                        {"가족", "家族", "gia tộc", "gia đình"},
                        {"국가", "國家", "quốc gia", "quốc gia"},
                        {"작가", "作家", "tác gia", "nhà văn, tác giả"}
                }},
                {"動", "động", "동", "Chuyển động, hành động, biến động", new String[][]{
                        {"운동", "運動", "vận động", "thể thao, vận động"},
                        {"활동", "活動", "hoạt động", "hoạt động"},
                        {"이동", "移動", "di động", "di chuyển"}
                }},
                {"物", "vật", "물", "Đồ vật, sinh vật, vạn vật", new String[][]{
                        {"물건", "物件", "vật kiện", "đồ vật, hàng hóa"},
                        {"동물", "動物", "động vật", "động vật"},
                        {"식물", "植物", "thực vật", "thực vật"}
                }},
                {"文", "văn", "문", "Chữ viết, văn chương, văn hóa", new String[][]{
                        {"문화", "文化", "văn hóa", "văn hóa"},
                        {"문자", "文字", "văn tự", "chữ viết, ký tự"},
                        {"논문", "論文", "luận văn", "luận văn, bài báo khoa học"}
                }},
                {"化", "hóa", "화", "Biến đổi, chuyển hóa, thay đổi", new String[][]{
                        {"문화", "文化", "văn hóa", "văn hóa"},
                        {"변화", "變化", "biến hóa", "sự thay đổi"},
                        {"현대화", "現代化", "hiện đại hóa", "hiện đại hóa"}
                }},
                {"心", "tâm", "심", "Trái tim, lòng dạ, tâm hồn", new String[][]{
                        {"관심", "關心", "quan tâm", "sự quan tâm, hứng thú"},
                        {"중심", "中心", "trung tâm", "trung tâm, trọng tâm"},
                        {"안심", "安心", "an tâm", "yên tâm, an lòng"}
                }},
                {"安", "an", "안", "Bình an, yên ổn, an toàn", new String[][]{
                        {"안전", "安全", "an toàn", "an toàn"},
                        {"안심", "安心", "an tâm", "yên tâm"},
                        {"불안", "不安", "bất an", "lo âu, bất an"}
                }},
                {"全", "toàn", "전", "Toàn bộ, vẹn toàn, đầy đủ", new String[][]{
                        {"안전", "安全", "an toàn", "an toàn"},
                        {"전부", "全部", "toàn bộ", "tất cả, toàn bộ"},
                        {"완전", "完全", "hoàn toàn", "hoàn toàn, trọn vẹn"}
                }},
                {"社", "xã", "사", "Xã hội, đoàn thể, cơ quan", new String[][]{
                        {"사회", "社會", "xã hội", "xã hội"},
                        {"회사", "會社", "hội xã", "công ty"},
                        {"사원", "社員", "xã viên", "nhân viên công ty"}
                }},
                {"新", "tân", "신", "Mới mẻ, tươi mới", new String[][]{
                        {"신문", "新聞", "tân văn", "báo giấy"},
                        {"신상품", "新商品", "tân thương phẩm", "sản phẩm mới"},
                        {"혁신", "革新", "cách tân", "đổi mới, cải tiến"}
                }}
        };

        short sort = 1;
        for (Object[] item : seeds) {
            String hanjaChar = (String) item[0];
            String sinoVi = (String) item[1];
            String koReading = (String) item[2];
            String meaningVi = (String) item[3];
            String[][] words = (String[][]) item[4];

            HanjaRoot root = HanjaRoot.builder()
                    .hanjaChar(hanjaChar)
                    .sinoVi(sinoVi)
                    .koReading(koReading)
                    .meaningVi(meaningVi)
                    .sortOrder(sort++)
                    .status("ACTIVE")
                    .build();

            root = hanjaRootRepository.save(root);

            for (String[] w : words) {
                String koWord = w[0];
                String hanjaWord = w[1];
                String wSino = w[2];
                String wMeaning = w[3];

                Vocabulary vocab = vocabularyRepository.findFirstByWordAndHanja(koWord, hanjaWord)
                        .orElse(null);

                if (vocab == null) {
                    vocab = Vocabulary.builder()
                            .topicId(1)
                            .level((short) 1)
                            .word(koWord)
                            .hanja(hanjaWord)
                            .sinoVi(wSino)
                            .meaningVi(wMeaning)
                            .status("ACTIVE")
                            .build();
                    vocab = vocabularyRepository.save(vocab);
                }

                HanjaRootWordId linkId = new HanjaRootWordId(root.getId(), vocab.getId());
                HanjaRootWord link = HanjaRootWord.builder()
                        .id(linkId)
                        .hanjaRoot(root)
                        .vocabulary(vocab)
                        .hanjaWord(hanjaWord)
                        .build();
                hanjaRootWordRepository.save(link);
            }
        }
        System.out.println("Seeded " + seeds.length + " initial Hanja roots successfully!");
    }

    // ==========================================
    // LEARNER-FACING METHODS
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public List<HanjaRootDTO> getActiveHanjaRoots(String query) {
        // Only return ACTIVE roots for learner view
        List<HanjaRoot> roots = hanjaRootRepository.searchRoots(query, "ACTIVE");
        List<HanjaRootDTO> result = new ArrayList<>();
        for (HanjaRoot root : roots) {
            result.add(convertToDTO(root));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HanjaQuizQuestionDTO> getRandomQuizQuestions(int count) {
        // Collect all active words with their roots
        List<HanjaRoot> activeRoots = hanjaRootRepository.searchRoots(null, "ACTIVE");

        // Build a flat list of (word, root) pairs for quiz
        List<HanjaQuizQuestionDTO> allCandidates = new ArrayList<>();
        List<String> allMeanings = new ArrayList<>();  // pool for decoys

        for (HanjaRoot root : activeRoots) {
            List<HanjaRootWord> links = hanjaRootWordRepository.findByHanjaRootId(root.getId());
            for (HanjaRootWord link : links) {
                Vocabulary v = link.getVocabulary();
                if (v != null && "ACTIVE".equals(v.getStatus())
                        && v.getWord() != null && !v.getWord().isEmpty()
                        && v.getMeaningVi() != null && !v.getMeaningVi().isEmpty()) {

                    HanjaQuizQuestionDTO q = HanjaQuizQuestionDTO.builder()
                            .vocabularyId(v.getId())
                            .word(v.getWord())
                            .hanjaWord(link.getHanjaWord())
                            .sinoVi(v.getSinoVi())
                            .correctMeaning(v.getMeaningVi())
                            .audioUrl(v.getAudioUrl())
                            .rootChar(root.getHanjaChar())
                            .rootSinoVi(root.getSinoVi())
                            .build();
                    allCandidates.add(q);
                    allMeanings.add(v.getMeaningVi());
                }
            }
        }

        if (allCandidates.isEmpty()) {
            return new ArrayList<>();
        }

        // Shuffle and limit
        Collections.shuffle(allCandidates);
        int actualCount = Math.min(count, allCandidates.size());
        List<HanjaQuizQuestionDTO> questions = new ArrayList<>(allCandidates.subList(0, actualCount));

        Random rng = new Random();

        for (HanjaQuizQuestionDTO q : questions) {
            String correct = q.getCorrectMeaning();

            // Pick 3 unique decoys (different from correct answer)
            List<String> decoyPool = new ArrayList<>(allMeanings);
            decoyPool.remove(correct);
            Collections.shuffle(decoyPool, rng);

            List<String> choices = new ArrayList<>();
            choices.add(correct);
            int addedDecoys = 0;
            for (String d : decoyPool) {
                if (!d.equals(correct)) {
                    choices.add(d);
                    addedDecoys++;
                    if (addedDecoys == 3) break;
                }
            }
            // Pad with generic decoys if not enough words in DB
            while (choices.size() < 4) {
                choices.add("—");
            }

            // Shuffle choices and record correct index
            Collections.shuffle(choices, rng);
            int correctIdx = choices.indexOf(correct);

            q.setChoices(choices);
            q.setCorrectChoiceIndex(correctIdx);
        }

        return questions;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getLearnerStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRoots", hanjaRootRepository.count());
        stats.put("activeRoots", hanjaRootRepository.countByStatus("ACTIVE"));
        stats.put("totalWords", hanjaRootWordRepository.count());
        return stats;
    }
}
