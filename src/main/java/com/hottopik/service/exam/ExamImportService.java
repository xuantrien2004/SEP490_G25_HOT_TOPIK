package com.hottopik.service.exam;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.*;
import com.hottopik.service.AdminSystemService;
import com.hottopik.service.exam.ParsedWorkbook.QuestionRow;
import com.hottopik.service.exam.ParsedWorkbook.WritingRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Nhập đề mới bằng Excel theo 4 bước (BRD 8.1). */
@Service
public class ExamImportService {

    public record SourceForm(String name, Track track, Integer year, Integer round, Integer expectedCount,
                             boolean assessment, String copyrightNote, String note) {
    }

    public record MediaUploadReport(int stored, List<String> rejected) {
    }

    public record ActivationResult(int questionCount, int writingCount, Long examId) {
    }

    public record ConfirmResult(ImportJob job, Skill skill, int totalRows) {
    }

    public record ImportPreCheckReport(
            boolean ok,
            String token,
            String fileName,
            String track,
            String skill,
            int totalRows,
            int validRows,
            int errorRows,
            int duplicateRows,
            int warningRows,
            List<PreCheckIssue> issues
    ) {
    }

    public record PreCheckIssue(
            String sheet,
            Integer row,
            String skill,
            Integer no,
            String severity,
            String issueType,
            String message
    ) {
    }

    public record TempImportEntry(
            Path filePath,
            String originalFilename,
            Long sourceId,
            Skill skill,
            LocalDateTime createdAt,
            Set<String> validQuestionKeys,
            Set<Integer> validWritingNos
    ) {
        public TempImportEntry(Path filePath, String originalFilename, Long sourceId, Skill skill, LocalDateTime createdAt) {
            this(filePath, originalFilename, sourceId, skill, createdAt, Set.of(), Set.of());
        }
    }

    public record BulkAudioPreCheckReport(
            boolean ok,
            String token,
            String zipFileName,
            String track,
            int totalExpected,
            int validCount,
            int missingCount,
            int errorCount,
            List<Integer> missingQuestions,
            List<BulkAudioMatch> matches,
            List<BulkAudioIssue> issues
    ) {
    }

    public record BulkAudioMatch(
            String filename,
            List<Integer> questionNos,
            String questionLabel,
            long sizeBytes,
            String sizeFormatted
    ) {
    }

    public record BulkAudioIssue(
            String filename,
            List<Integer> questionNos,
            String severity,
            String message
    ) {
    }

    public record BulkAudioConfirmResult(
            int totalFiles,
            int updatedQuestions,
            String message,
            String redirectUrl
    ) {
    }

    public record TempBulkAudioEntry(
            String token,
            Path tempDir,
            String originalFilename,
            Long sourceId,
            Track track,
            Map<Integer, String> validFiles,
            LocalDateTime createdAt
    ) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper DRAFT_JSON = new ObjectMapper()
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    static final String MANUAL_PATH = "manual";

    private final ExamSourceRepository sources;
    private final ImportJobRepository jobs;
    private final MediaAssetRepository media;
    private final QuestionRepository questions;
    private final QuestionGroupRepository groups;
    private final WritingPromptRepository writingPrompts;
    private final MediaStorageService storage;
    private final ExamComposerService composer;
    private final AdminSystemService audit;
    private final QuestionTemplateRepository templateRepo;

    public ExamImportService(ExamSourceRepository sources, ImportJobRepository jobs, MediaAssetRepository media,
                             QuestionRepository questions, QuestionGroupRepository groups,
                             WritingPromptRepository writingPrompts, MediaStorageService storage,
                             ExamComposerService composer, AdminSystemService audit,
                             QuestionTemplateRepository templateRepo) {
        this.sources = sources;
        this.jobs = jobs;
        this.media = media;
        this.questions = questions;
        this.groups = groups;
        this.writingPrompts = writingPrompts;
        this.storage = storage;
        this.composer = composer;
        this.audit = audit;
        this.templateRepo = templateRepo;
    }

    // ------------------------------------------------------------------ Bước 1: tạo nguồn đề

    @Transactional
    public ExamSource createSource(SourceForm f, Actor actor) {
        requireContent(actor);
        String name = trim(f.name());
        if (name == null || name.length() < 3 || name.length() > 200) {
            throw new ExamDomainException("Tên nguồn đề dài từ 3 đến 200 ký tự.");
        }
        if (f.track() == null) throw new ExamDomainException("Chọn track TOPIK I hoặc TOPIK II.");
        int full = QuestionTypeCatalog.fullExamCount(f.track());
        int expected = f.expectedCount() == null ? full : f.expectedCount();
        if (expected < 1 || expected > full) {
            throw new ExamDomainException("Số câu dự kiến của " + f.track().getLabel() + " từ 1 đến " + full + ".");
        }
        if (f.year() != null && (f.year() < 1997 || f.year() > 2100)) {
            throw new ExamDomainException("Năm không hợp lệ.");
        }
        String copyright = trim(f.copyrightNote());
        if (copyright == null || copyright.length() < 5) {
            throw new ExamDomainException("Ghi chú bản quyền là bắt buộc (nguồn, quyền sử dụng), ít nhất 5 ký tự.");
        }
        ExamSource s = new ExamSource();
        s.setName(name);
        s.setTrack(f.track());
        s.setYear(f.year());
        s.setRound(f.round());
        s.setExpectedCount(expected);
        s.setAssessment(f.assessment());
        s.setCopyrightNote(copyright);
        s.setNote(trim(f.note()));
        s.setCreatedBy(actor.name());
        sources.save(s);
        log(actor, "CREATE_EXAM_SOURCE", "Tạo nguồn đề \"" + name + "\" (" + f.track().getLabel() + ", " + expected + " câu)");
        return s;
    }

    // ------------------------------------------------------------------ Bước 2: tải Excel và âm thanh

    @Transactional
    public ImportJob uploadExcel(Long sourceId, MultipartFile file, Actor actor) throws IOException {
        requireContent(actor);
        ExamSource source = draftSource(sourceId);
        if (file == null || file.isEmpty()) throw new ExamDomainException("Chọn file Excel (.xlsx).");
        String name = MediaStorageService.baseName(file.getOriginalFilename());
        if (!"xlsx".equals(MediaStorageService.extension(name))) {
            throw new ExamDomainException("Chỉ nhận file .xlsx theo mẫu.");
        }
        if (file.getSize() > MediaStorageService.MAX_EXCEL_BYTES) {
            throw new ExamDomainException("File Excel tối đa 10 MB.");
        }
        Optional<ImportJob> prevJobOpt = jobs.findFirstBySourceIdOrderByIdDesc(sourceId);
        ParsedWorkbook prevWb = prevJobOpt.map(this::workbookOf).orElse(null);
        prevJobOpt.ifPresent(prev -> {
            if (prev.getStatus() != ImportJobStatus.ACTIVATED) prev.setStatus(ImportJobStatus.SUPERSEDED);
        });
        Path path = storage.storeExcel(sourceId, file);

        ParsedWorkbook incoming;
        try (InputStream in = Files.newInputStream(path)) {
            incoming = ExamWorkbookParser.parse(in, source.getTrack());
        }
        preserveMediaFromPreviousDraft(prevWb, incoming);
        autoMatchAudioFromMediaAssets(source, incoming);
        autoMatchImagesFromMediaAssets(source, incoming);
        syncManualCards(source, incoming, null);

        ImportJob job = new ImportJob();
        job.setSource(source);
        job.setFileName(name);
        job.setFilePath(MANUAL_PATH); // Luôn giữ form nhập đề mặc định
        job.setCreatedBy(actor.name());
        jobs.save(job);
        storeDraft(job, incoming);
        runValidation(job);
        log(actor, "UPLOAD_EXAM_EXCEL", "Tải file " + name + " cho nguồn đề #" + sourceId
                + " → " + job.getErrorCount() + " lỗi, " + job.getWarningCount() + " cảnh báo");
        return job;
    }

    @Transactional
    public ImportJob uploadSkillExcel(Long sourceId, Skill skill, MultipartFile file, Actor actor) throws IOException {
        requireContent(actor);
        ExamSource source = draftSource(sourceId);
        if (file == null || file.isEmpty()) throw new ExamDomainException("Chọn file Excel (.xlsx).");
        String name = MediaStorageService.baseName(file.getOriginalFilename());
        if (!"xlsx".equals(MediaStorageService.extension(name))) {
            throw new ExamDomainException("Chỉ nhận file .xlsx theo mẫu.");
        }
        if (file.getSize() > MediaStorageService.MAX_EXCEL_BYTES) {
            throw new ExamDomainException("File Excel tối đa 10 MB.");
        }
        if (source.getTrack() == Track.TOPIK_I && skill == Skill.WRITING) {
            throw new ExamDomainException("TOPIK I không có kỹ năng Viết.");
        }

        Optional<ImportJob> prevJobOpt = jobs.findFirstBySourceIdOrderByIdDesc(sourceId);
        ParsedWorkbook currentWb;
        if (prevJobOpt.isPresent()) {
            currentWb = workbookOf(prevJobOpt.get());
        } else {
            currentWb = new ParsedWorkbook();
        }

        ParsedWorkbook incoming;
        try (InputStream in = file.getInputStream()) {
            incoming = ExamWorkbookParser.parseSkill(in, source.getTrack(), skill);
        }

        if (incoming.issues.stream().anyMatch(ImportIssue::isError)) {
            ImportIssue firstErr = incoming.issues.stream().filter(ImportIssue::isError).findFirst().get();
            throw new ExamDomainException("Lỗi đọc file: " + firstErr.message());
        }

        if (skill == Skill.WRITING) {
            for (ParsedWorkbook.WritingRow inW : incoming.writings) {
                ParsedWorkbook.WritingRow ex = currentWb.writings.stream()
                        .filter(w -> Objects.equals(w.no, inW.no))
                        .findFirst().orElse(null);
                if (ex != null && blank(inW.chartJson) && !blank(ex.chartJson)) {
                    inW.chartJson = ex.chartJson;
                }
            }
            currentWb.writings.clear();
            currentWb.writings.addAll(incoming.writings);
            currentWb.hasWritingSheet = true;
        } else {
            for (ParsedWorkbook.QuestionRow inQ : incoming.questions) {
                ParsedWorkbook.QuestionRow ex = currentWb.questions.stream()
                        .filter(q -> q.skill == skill && Objects.equals(q.no, inQ.no))
                        .findFirst().orElse(null);
                if (ex != null) {
                    if (blank(inQ.audioFile) && !blank(ex.audioFile)) inQ.audioFile = ex.audioFile;
                    if (blank(inQ.imageFile) && !blank(ex.imageFile)) inQ.imageFile = ex.imageFile;
                }
            }
            currentWb.questions.removeIf(q -> q.skill == skill);
            currentWb.questions.addAll(incoming.questions);
            currentWb.questions.sort(Comparator.comparingInt(q -> (q.no == null ? 0 : q.no)));
        }
        autoMatchAudioFromMediaAssets(source, currentWb);
        autoMatchImagesFromMediaAssets(source, currentWb);
        syncManualCards(source, currentWb, skill);

        Path path = storage.storeExcel(sourceId, file);
        prevJobOpt.ifPresent(prev -> {
            if (prev.getStatus() != ImportJobStatus.ACTIVATED) prev.setStatus(ImportJobStatus.SUPERSEDED);
        });

        ImportJob job = new ImportJob();
        job.setSource(source);
        job.setFileName(name);
        job.setFilePath(MANUAL_PATH); // Luôn giữ form nhập đề mặc định
        job.setCreatedBy(actor.name());
        jobs.save(job);

        storeDraft(job, currentWb);
        runValidation(job);

        log(actor, "UPLOAD_SKILL_EXCEL", "Tải file " + name + " cho kỹ năng " + skill.getLabel() + " nguồn đề #" + sourceId
                + " → " + job.getErrorCount() + " lỗi, " + job.getWarningCount() + " cảnh báo");
        return job;
    }

    private final Map<String, TempImportEntry> tempImports = new ConcurrentHashMap<>();

    private void cleanExpiredTempImports() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        tempImports.entrySet().removeIf(entry -> {
            if (entry.getValue().createdAt().isBefore(cutoff)) {
                try {
                    Files.deleteIfExists(entry.getValue().filePath());
                } catch (Exception ignored) {
                }
                return true;
            }
            return false;
        });
    }

    public ImportPreCheckReport preCheckExcel(Long sourceId, Skill skill, MultipartFile file, Actor actor) throws IOException {
        requireContent(actor);
        ExamSource source = draftSource(sourceId);
        if (file == null || file.isEmpty()) throw new ExamDomainException("Chọn file Excel (.xlsx).");
        String name = MediaStorageService.baseName(file.getOriginalFilename());
        if (!"xlsx".equals(MediaStorageService.extension(name))) {
            throw new ExamDomainException("Chỉ nhận file .xlsx theo mẫu.");
        }
        if (file.getSize() > MediaStorageService.MAX_EXCEL_BYTES) {
            throw new ExamDomainException("File Excel tối đa 10 MB.");
        }
        if (source.getTrack() == Track.TOPIK_I && skill == Skill.WRITING) {
            throw new ExamDomainException("TOPIK I không có kỹ năng Viết.");
        }

        cleanExpiredTempImports();

        Path tempPath = Files.createTempFile("ht-precheck-" + sourceId + "-", ".xlsx");
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
        }

        ParsedWorkbook wb;
        int totalRows;
        if (skill != null) {
            try (InputStream in = Files.newInputStream(tempPath)) {
                wb = ExamWorkbookParser.parseSkill(in, source.getTrack(), skill);
            }
            totalRows = skill == Skill.WRITING ? wb.writings.size() : wb.questions.size();
        } else {
            try (InputStream in = Files.newInputStream(tempPath)) {
                wb = ExamWorkbookParser.parse(in, source.getTrack());
            }
            totalRows = wb.questions.size() + wb.writings.size();
        }

        // Bảo tồn media từ bản nháp hiện tại và tự động khớp audio từ MediaAsset đã tải lên
        Optional<ImportJob> existingJobOpt = jobs.findFirstBySourceIdOrderByIdDesc(sourceId);
        if (existingJobOpt.isPresent()) {
            preserveMediaFromPreviousDraft(workbookOf(existingJobOpt.get()), wb);
        }
        autoMatchAudioFromMediaAssets(source, wb);
        autoMatchImagesFromMediaAssets(source, wb);

        ParsedWorkbook existingSystemWb = getExistingSystemWorkbook(sourceId);
        ExamImportValidator.Context ctx = new ExamImportValidator.Context(
                source.getTrack(),
                skill == null,
                source.isAssessment(),
                mediaSizes(sourceId),
                hashes -> existingHashLookup(sourceId, hashes),
                existingSystemWb
        );

        ExamImportValidator.Result validationResult = ExamImportValidator.validate(wb, ctx);

        List<PreCheckIssue> preCheckIssues = new ArrayList<>();
        Set<String> errorQuestions = new HashSet<>();
        Set<String> duplicateQuestions = new HashSet<>();

        for (ImportIssue issue : validationResult.issues()) {
            String severity = issue.isError() ? "ERROR" : "WARNING";
            String issueType;
            String msg = issue.message() != null ? issue.message() : "";
            String slot = (issue.skill() != null ? issue.skill() : issue.sheet()) + "#" + (issue.no() != null ? issue.no() : ("R" + issue.row()));

            if (msg.contains("Trùng") || msg.contains("đã có trên hệ thống") || msg.contains("đã tồn tại")) {
                issueType = "DUPLICATE";
                duplicateQuestions.add(slot);
            } else if (msg.contains("Thiếu") || msg.contains("bắt buộc") || msg.contains("chưa có") || msg.contains("cần")) {
                issueType = "MISSING_INFO";
            } else if (msg.contains("không hợp lệ") || msg.contains("ngoài phạm vi") || msg.contains("phải là") || msg.contains("không đúng")) {
                issueType = "INVALID";
            } else {
                issueType = "OTHER";
            }

            if (issue.isError()) {
                errorQuestions.add(slot);
            }

            preCheckIssues.add(new PreCheckIssue(
                    issue.sheet(),
                    issue.row(),
                    issue.skill(),
                    issue.no(),
                    severity,
                    issueType,
                    msg
            ));
        }

        Set<String> validQuestionKeys = new LinkedHashSet<>();
        Set<Integer> validWritingNos = new LinkedHashSet<>();

        for (ParsedWorkbook.QuestionRow q : wb.questions) {
            boolean hasError = validationResult.issues().stream()
                    .filter(ImportIssue::isError)
                    .anyMatch(i -> issueMatchesQuestion(i, q));
            boolean hasDup = validationResult.issues().stream()
                    .filter(i -> {
                        String m = i.message() != null ? i.message() : "";
                        return m.contains("Trùng") || m.contains("đã có trên hệ thống") || m.contains("đã tồn tại");
                    })
                    .anyMatch(i -> issueMatchesQuestion(i, q));

            if (!hasError && !hasDup && q.no != null && q.skill != null) {
                validQuestionKeys.add(q.skill.name() + "#" + q.no);
            }
        }

        for (ParsedWorkbook.WritingRow w : wb.writings) {
            boolean hasError = validationResult.issues().stream()
                    .filter(ImportIssue::isError)
                    .anyMatch(i -> issueMatchesWriting(i, w));
            boolean hasDup = validationResult.issues().stream()
                    .filter(i -> {
                        String m = i.message() != null ? i.message() : "";
                        return m.contains("Trùng") || m.contains("đã có trên hệ thống") || m.contains("đã tồn tại");
                    })
                    .anyMatch(i -> issueMatchesWriting(i, w));

            if (!hasError && !hasDup && w.no != null) {
                validWritingNos.add(w.no);
            }
        }

        int validRows = skill == Skill.WRITING ? validWritingNos.size()
                : (skill != null ? validQuestionKeys.size() : (validQuestionKeys.size() + validWritingNos.size()));
        int errorRows = errorQuestions.size();
        int duplicateRows = duplicateQuestions.size();
        int warningRows = (int) validationResult.warningCount();

        String token = UUID.randomUUID().toString();
        tempImports.put(token, new TempImportEntry(tempPath, file.getOriginalFilename(), sourceId, skill, LocalDateTime.now(), validQuestionKeys, validWritingNos));

        return new ImportPreCheckReport(
                true,
                token,
                file.getOriginalFilename(),
                source.getTrack().getLabel(),
                skill != null ? skill.getLabel() : "Toàn bộ đề",
                totalRows,
                validRows,
                errorRows,
                duplicateRows,
                warningRows,
                preCheckIssues
        );
    }

    public ParsedWorkbook getExistingSystemWorkbook(Long sourceId) {
        if (sourceId == null) return null;
        Optional<ImportJob> jobOpt = jobs.findFirstBySourceIdOrderByIdDesc(sourceId);
        ParsedWorkbook wb = null;
        if (jobOpt.isPresent()) {
            try {
                wb = workbookOf(jobOpt.get());
            } catch (Exception ignored) {}
        }
        if (wb == null) {
            wb = new ParsedWorkbook();
        }

        // Nếu có manualCards (được đồng bộ từ lần import trước hoặc sửa qua giao diện web),
        // đồng bộ dữ liệu sang questions/writings để đối chiếu chính xác
        if (!wb.manualCards.isEmpty()) {
            populateQuestionsFromCards(wb, sourceId);
        }

        // Dự phòng: nếu chưa có draft nhưng đã kích hoạt câu hỏi trong CSDL
        if (wb.questions.isEmpty() && wb.writings.isEmpty()) {
            List<Question> dbQuestions = questionsOf(sourceId);
            if (dbQuestions != null && !dbQuestions.isEmpty()) {
                for (Question q : dbQuestions) {
                    ParsedWorkbook.QuestionRow qr = new ParsedWorkbook.QuestionRow();
                    qr.skill = q.getSkill();
                    qr.no = q.getOriginalNo();
                    qr.typeCode = q.getTypeCode();
                    qr.resolvedType = q.getTypeCode();
                    qr.promptKo = q.getPromptKo();
                    if (q.getGroup() != null) {
                        qr.passageKo = q.getGroup().getPassageKo();
                        qr.scriptKo = q.getGroup().getScriptKo();
                        qr.audioFile = q.getGroup().getAudioFile();
                        qr.imageFile = q.getGroup().getImageFile();
                    }
                    qr.options = new String[]{
                            q.getOption1() != null ? q.getOption1() : "",
                            q.getOption2() != null ? q.getOption2() : "",
                            q.getOption3() != null ? q.getOption3() : "",
                            q.getOption4() != null ? q.getOption4() : ""
                    };
                    qr.answer = q.getAnswerIndex();
                    qr.explanationVi = q.getExplanationVi();
                    wb.questions.add(qr);
                }
            }
            List<WritingPrompt> dbWritings = writingOf(sourceId);
            if (dbWritings != null && !dbWritings.isEmpty()) {
                for (WritingPrompt wp : dbWritings) {
                    ParsedWorkbook.WritingRow wr = new ParsedWorkbook.WritingRow();
                    wr.no = wp.getNo();
                    wr.promptKo = wp.getPromptKo();
                    wr.keys1 = wp.getKeys1();
                    wr.keys2 = wp.getKeys2();
                    wr.modelAnswer = wp.getModelAnswer();
                    wr.rubricNote = wp.getRubricNote();
                    wb.writings.add(wr);
                }
            }
        }

        if (wb.questions.isEmpty() && wb.writings.isEmpty()) {
            return null;
        }
        return wb;
    }

    private void populateQuestionsFromCards(ParsedWorkbook wb, Long sourceId) {
        Map<String, ParsedWorkbook.QuestionRow> qMap = new HashMap<>();
        for (ParsedWorkbook.QuestionRow q : wb.questions) {
            if (q.skill != null && q.no != null) {
                qMap.put(q.skill.name() + "#" + q.no, q);
            }
        }
        Map<Integer, ParsedWorkbook.WritingRow> wMap = new HashMap<>();
        for (ParsedWorkbook.WritingRow w : wb.writings) {
            if (w.no != null) {
                wMap.put(w.no, w);
            }
        }

        for (ParsedWorkbook.ManualCard card : wb.manualCards) {
            QuestionTemplate template = blank(card.templateCode) ? null : templateRepo.findByCode(card.templateCode).orElse(null);
            Skill skill = template != null ? template.getSkill() : null;
            if (skill == null && !blank(card.typeCode)) {
                skill = QuestionTypeCatalog.byCode(card.typeCode).map(QuestionTypeDef::skill).orElse(null);
            }

            if (skill == Skill.WRITING) {
                for (ParsedWorkbook.ManualAsk ask : card.asks) {
                    if (ask.no == null) continue;
                    ParsedWorkbook.WritingRow w = wMap.computeIfAbsent(ask.no, n -> {
                        ParsedWorkbook.WritingRow newW = new ParsedWorkbook.WritingRow();
                        newW.no = n;
                        wb.writings.add(newW);
                        return newW;
                    });
                    w.promptKo = ask.promptKo;
                    w.passageKo = card.passageKo;
                    w.keys1 = ask.keys1;
                    w.keys2 = ask.keys2;
                    w.modelAnswer = ask.modelAnswer;
                    w.rubricNote = ask.rubricNote;
                }
            } else {
                for (ParsedWorkbook.ManualAsk ask : card.asks) {
                    if (ask.no == null) continue;
                    Skill resolvedSkill = skill;
                    if (resolvedSkill == null) {
                        for (ParsedWorkbook.QuestionRow existingQ : wb.questions) {
                            if (existingQ.no != null && existingQ.no.equals(ask.no)) {
                                resolvedSkill = existingQ.skill;
                                break;
                            }
                        }
                    }
                    if (resolvedSkill == null) resolvedSkill = Skill.READING;

                    String key = resolvedSkill.name() + "#" + ask.no;
                    final Skill fSkill = resolvedSkill;
                    ParsedWorkbook.QuestionRow q = qMap.computeIfAbsent(key, k -> {
                        ParsedWorkbook.QuestionRow newQ = new ParsedWorkbook.QuestionRow();
                        newQ.skill = fSkill;
                        newQ.no = ask.no;
                        wb.questions.add(newQ);
                        return newQ;
                    });
                    q.typeCode = card.typeCode;
                    q.resolvedType = card.typeCode;
                    q.promptKo = ask.promptKo;
                    q.passageKo = card.passageKo;
                    q.scriptKo = card.scriptKo;
                    q.audioFile = card.audioFile;
                    q.imageFile = card.imageFile;
                    if (ask.options != null && ask.options.length == 4) {
                        q.options = ask.options.clone();
                    }
                    q.answer = ask.answer;
                    q.explanationVi = ask.explanationVi;
                }
            }
        }
    }

    @Transactional
    public ConfirmResult confirmImport(Long sourceId, String token, Actor actor) throws IOException {
        requireContent(actor);
        ExamSource source = draftSource(sourceId);
        TempImportEntry entry = tempImports.remove(token);
        if (entry == null || !entry.sourceId().equals(sourceId)) {
            throw new ExamDomainException("Phiên nhập Excel đã hết hạn hoặc không hợp lệ. Vui lòng chọn lại file.");
        }

        Path tempPath = entry.filePath();
        if (!Files.exists(tempPath)) {
            throw new ExamDomainException("File tạm không tồn tại. Vui lòng tải lại file.");
        }

        try {
            String name = MediaStorageService.baseName(entry.originalFilename());
            Skill skill = entry.skill();

            Optional<ImportJob> prevJobOpt = jobs.findFirstBySourceIdOrderByIdDesc(sourceId);
            ParsedWorkbook currentWb;
            if (prevJobOpt.isPresent()) {
                currentWb = workbookOf(prevJobOpt.get());
            } else {
                currentWb = new ParsedWorkbook();
            }

            ParsedWorkbook incoming;
            int importedCount = 0;
            if (skill != null) {
                try (InputStream in = Files.newInputStream(tempPath)) {
                    incoming = ExamWorkbookParser.parseSkill(in, source.getTrack(), skill);
                }

                if (incoming.issues.stream().anyMatch(ImportIssue::isError)) {
                    ImportIssue firstErr = incoming.issues.stream().filter(ImportIssue::isError).findFirst().get();
                    throw new ExamDomainException("Lỗi đọc file: " + firstErr.message());
                }

                if (skill == Skill.WRITING) {
                    if (entry.validWritingNos() != null) {
                        incoming.writings.removeIf(w -> w.no == null || !entry.validWritingNos().contains(w.no));
                    }
                    if (incoming.writings.isEmpty()) {
                        throw new ExamDomainException("Không có câu hỏi hợp lệ nào để nhập vào hệ thống.");
                    }
                    for (ParsedWorkbook.WritingRow w : incoming.writings) {
                        ParsedWorkbook.WritingRow existing = currentWb.writings.stream()
                                .filter(ex -> Objects.equals(ex.no, w.no))
                                .findFirst().orElse(null);
                        if (existing != null) {
                            if (blank(w.chartJson) && !blank(existing.chartJson)) {
                                w.chartJson = existing.chartJson;
                            }
                            currentWb.writings.remove(existing);
                        }
                        currentWb.writings.add(w);
                    }
                    currentWb.writings.sort(Comparator.comparingInt(w -> (w.no == null ? 0 : w.no)));
                    currentWb.hasWritingSheet = true;
                    importedCount = incoming.writings.size();
                } else {
                    if (entry.validQuestionKeys() != null) {
                        incoming.questions.removeIf(q -> q.no == null || !entry.validQuestionKeys().contains((q.skill != null ? q.skill.name() : skill.name()) + "#" + q.no));
                    }
                    if (incoming.questions.isEmpty()) {
                        throw new ExamDomainException("Không có câu hỏi hợp lệ nào để nhập vào hệ thống.");
                    }
                    for (ParsedWorkbook.QuestionRow q : incoming.questions) {
                        ParsedWorkbook.QuestionRow existing = currentWb.questions.stream()
                                .filter(ex -> ex.skill == skill && Objects.equals(ex.no, q.no))
                                .findFirst().orElse(null);
                        if (existing != null) {
                            if (blank(q.audioFile) && !blank(existing.audioFile)) {
                                q.audioFile = existing.audioFile;
                            }
                            if (blank(q.imageFile) && !blank(existing.imageFile)) {
                                q.imageFile = existing.imageFile;
                            }
                            currentWb.questions.remove(existing);
                        }
                        currentWb.questions.add(q);
                    }
                    currentWb.questions.sort(Comparator.comparingInt(q -> (q.no == null ? 0 : q.no)));
                    importedCount = incoming.questions.size();
                }
                autoMatchAudioFromMediaAssets(source, currentWb);
                autoMatchImagesFromMediaAssets(source, currentWb);
                syncManualCards(source, currentWb, skill);
            } else {
                try (InputStream in = Files.newInputStream(tempPath)) {
                    incoming = ExamWorkbookParser.parse(in, source.getTrack());
                }

                if (incoming.issues.stream().anyMatch(ImportIssue::isError)) {
                    ImportIssue firstErr = incoming.issues.stream().filter(ImportIssue::isError).findFirst().get();
                    throw new ExamDomainException("Lỗi đọc file: " + firstErr.message());
                }

                if (entry.validQuestionKeys() != null) {
                    incoming.questions.removeIf(q -> q.no == null || q.skill == null || !entry.validQuestionKeys().contains(q.skill.name() + "#" + q.no));
                }
                if (entry.validWritingNos() != null) {
                    incoming.writings.removeIf(w -> w.no == null || !entry.validWritingNos().contains(w.no));
                }
                if (incoming.questions.isEmpty() && incoming.writings.isEmpty()) {
                    throw new ExamDomainException("Không có câu hỏi hợp lệ nào để nhập vào hệ thống.");
                }

                for (ParsedWorkbook.QuestionRow q : incoming.questions) {
                    ParsedWorkbook.QuestionRow existing = currentWb.questions.stream()
                            .filter(ex -> ex.skill == q.skill && Objects.equals(ex.no, q.no))
                            .findFirst().orElse(null);
                    if (existing != null) {
                        if ((blank(q.audioFile) || isImagePlaceholder(q.audioFile)) && !blank(existing.audioFile)) {
                            q.audioFile = existing.audioFile;
                        }
                        if ((blank(q.imageFile) || isImagePlaceholder(q.imageFile)) && !blank(existing.imageFile) && !isImagePlaceholder(existing.imageFile)) {
                            q.imageFile = existing.imageFile;
                        }
                        currentWb.questions.remove(existing);
                    }
                    currentWb.questions.add(q);
                }
                currentWb.questions.sort(Comparator.comparingInt(q -> (q.no == null ? 0 : q.no)));

                for (ParsedWorkbook.WritingRow w : incoming.writings) {
                    ParsedWorkbook.WritingRow existing = currentWb.writings.stream()
                            .filter(ex -> Objects.equals(ex.no, w.no))
                            .findFirst().orElse(null);
                    if (existing != null) {
                        if ((blank(w.chartJson) || isImagePlaceholder(w.chartJson)) && !blank(existing.chartJson)) {
                            w.chartJson = existing.chartJson;
                        }
                        currentWb.writings.remove(existing);
                    }
                    currentWb.writings.add(w);
                }
                currentWb.writings.sort(Comparator.comparingInt(w -> (w.no == null ? 0 : w.no)));
                if (!incoming.writings.isEmpty()) {
                    currentWb.hasWritingSheet = true;
                }
                importedCount = incoming.questions.size() + incoming.writings.size();
                autoMatchAudioFromMediaAssets(source, currentWb);
                autoMatchImagesFromMediaAssets(source, currentWb);
                syncManualCards(source, currentWb, null);
            }

            Path storedPath;
            try (InputStream in = Files.newInputStream(tempPath)) {
                storedPath = storage.storeExcel(sourceId, in);
            }
            prevJobOpt.ifPresent(prev -> {
                if (prev.getStatus() != ImportJobStatus.ACTIVATED) prev.setStatus(ImportJobStatus.SUPERSEDED);
            });

            ImportJob job = new ImportJob();
            job.setSource(source);
            job.setFileName(name);
            job.setFilePath(MANUAL_PATH); // Giữ nguyên form nhập đề mặc định
            job.setCreatedBy(actor.name());
            jobs.save(job);

            storeDraft(job, currentWb);
            runValidation(job);

            int totalRows = job.getMcqRows() + job.getWritingRows();
            String logSkill = skill != null ? (" kỹ năng " + skill.getLabel()) : " toàn bộ đề";
            log(actor, "CONFIRM_EXCEL_IMPORT", "Xác nhận nạp file " + name + logSkill + " nguồn đề #" + sourceId
                    + " → " + job.getErrorCount() + " lỗi, " + job.getWarningCount() + " cảnh báo (đã import " + importedCount + " câu hợp lệ)");
            return new ConfirmResult(job, skill, importedCount > 0 ? importedCount : totalRows);
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean issueMatchesQuestion(ImportIssue issue, ParsedWorkbook.QuestionRow q) {
        if (issue == null || q == null) return false;
        if ("MEDIA".equalsIgnoreCase(issue.sheet()) || "FILE".equalsIgnoreCase(issue.sheet())) return false;

        // Nếu issue có kỹ năng mà khác kỹ năng của câu hỏi trắc nghiệm thì không khớp
        if (issue.skill() != null && q.skill != null && !issue.skill().equalsIgnoreCase(q.skill.name())) {
            return false;
        }

        // Nếu issue thuộc sheet câu hỏi khác (ví dụ: issue ở sheet NGHE mà câu ở sheet DOC, hoặc issue ở sheet VIET)
        if (issue.sheet() != null) {
            String sheet = issue.sheet().trim().toUpperCase(Locale.ROOT);
            if ("VIET".equals(sheet) || "VIẾT".equals(sheet) || "WRITING".equals(sheet)) {
                return false;
            }
            if (q.skill == Skill.LISTENING && ("DOC".equals(sheet) || "ĐỌC".equals(sheet) || "READING".equals(sheet))) {
                return false;
            }
            if (q.skill == Skill.READING && ("NGHE".equals(sheet) || "LISTENING".equals(sheet))) {
                return false;
            }
        }

        // Khớp theo số thứ tự câu hỏi (ưu tiên chính xác nhất)
        if (issue.no() != null && q.no != null && Objects.equals(issue.no(), q.no)) {
            if (issue.skill() != null && q.skill != null) {
                return issue.skill().equalsIgnoreCase(q.skill.name());
            }
            return true;
        }

        // Chỉ khớp theo số dòng nếu cùng kỹ năng/sheet và issue không ghi rõ số câu
        if (issue.row() != null && issue.row() > 0 && q.row > 0 && issue.row() == q.row) {
            if (issue.skill() != null && q.skill != null) {
                return issue.skill().equalsIgnoreCase(q.skill.name());
            }
            return issue.no() == null;
        }

        return false;
    }

    private static boolean issueMatchesWriting(ImportIssue issue, ParsedWorkbook.WritingRow w) {
        if (issue == null || w == null) return false;
        if ("MEDIA".equalsIgnoreCase(issue.sheet()) || "FILE".equalsIgnoreCase(issue.sheet())) return false;

        // Câu Viết chỉ khớp với issue thuộc WRITING
        if (issue.skill() != null && !"WRITING".equalsIgnoreCase(issue.skill())) {
            return false;
        }

        if (issue.sheet() != null) {
            String sheet = issue.sheet().trim().toUpperCase(Locale.ROOT);
            if (!"VIET".equals(sheet) && !"VIẾT".equals(sheet) && !"WRITING".equals(sheet)
                    && !ParsedWorkbook.SHEET_QUESTIONS.equals(sheet)) {
                return false;
            }
        }

        // Khớp theo số thứ tự câu hỏi viết (51-54)
        if (issue.no() != null && w.no != null && Objects.equals(issue.no(), w.no)) {
            return true;
        }

        // Khớp theo số dòng nếu issue thuộc sheet Viết và không có số câu
        if (issue.row() != null && issue.row() > 0 && w.row > 0 && issue.row() == w.row) {
            if (issue.skill() != null) {
                return "WRITING".equalsIgnoreCase(issue.skill());
            }
            if (issue.sheet() != null) {
                String sheet = issue.sheet().trim().toUpperCase(Locale.ROOT);
                return "VIET".equals(sheet) || "VIẾT".equals(sheet) || "WRITING".equals(sheet);
            }
            return issue.no() == null;
        }

        return false;
    }

    public static boolean isImagePlaceholder(String val) {
        if (val == null) return false;
        String clean = val.trim().toLowerCase(Locale.ROOT);
        if (clean.isEmpty()) return false;
        if (clean.startsWith("[") && clean.endsWith("]")) clean = clean.substring(1, clean.length() - 1).trim();
        if (clean.startsWith("(") && clean.endsWith(")")) clean = clean.substring(1, clean.length() - 1).trim();
        if (clean.endsWith(".png") || clean.endsWith(".jpg") || clean.endsWith(".jpeg")
                || clean.endsWith(".gif") || clean.endsWith(".webp") || clean.endsWith(".mp3")) {
            return false;
        }
        return clean.equals("có hình ảnh") || clean.equals("co hinh anh")
                || clean.equals("có ảnh") || clean.equals("co anh")
                || clean.equals("hình ảnh") || clean.equals("hinh anh")
                || clean.equals("ảnh") || clean.equals("anh")
                || clean.equals("có") || clean.equals("co")
                || clean.equals("yes") || clean.equals("true")
                || clean.equals("x") || clean.equals("v")
                || clean.contains("hình ảnh") || clean.contains("hinh anh")
                || clean.contains("có ảnh") || clean.contains("co anh");
    }

    public static String cleanImage(String val) {
        if (val == null) return null;
        String trimmed = val.trim();
        if (trimmed.isEmpty() || isImagePlaceholder(trimmed)) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        boolean hasExt = lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".webp");
        if (!hasExt) {
            return null;
        }
        return trimmed;
    }

    public static String cleanOptionText(String val) {
        if (val == null) return "";
        String trimmed = val.trim();
        if (isImagePlaceholder(trimmed)) {
            return "";
        }
        return trimmed;
    }

    public static String cleanPromptText(String val) {
        if (val == null) return "";
        String trimmed = val.trim();
        if (isImagePlaceholder(trimmed)) {
            return "";
        }
        return trimmed;
    }

    public void syncManualCards(ExamSource source, ParsedWorkbook wb, Skill targetSkill) {
        if (wb == null) return;
        Track track = source.getTrack();

        List<Skill> skillsToUpdate = targetSkill != null
                ? List.of(targetSkill)
                : (track == Track.TOPIK_II ? List.of(Skill.LISTENING, Skill.READING, Skill.WRITING) : List.of(Skill.LISTENING, Skill.READING));

        Map<Integer, String> audioAssetMap = (skillsToUpdate.contains(Skill.LISTENING))
                ? buildAudioMapFromMediaAssets(source)
                : Map.of();

        // Trích xuất ảnh từ manualCards hiện có trước khi xóa để bảo tồn khi sync (phân tách theo skill#no)
        Map<String, String> prevCardImages = new HashMap<>();
        for (ParsedWorkbook.ManualCard c : wb.manualCards) {
            QuestionTemplate t = blank(c.templateCode) ? null : templateRepo.findByCode(c.templateCode).orElse(null);
            Skill cardSkill = t != null ? t.getSkill() : null;
            if (cardSkill == null && !blank(c.typeCode)) {
                QuestionTypeDef td = QuestionTypeCatalog.byCode(c.typeCode).orElse(null);
                if (td != null) cardSkill = td.skill();
            }
            for (ParsedWorkbook.ManualAsk a : c.asks) {
                if (a.no != null) {
                    Skill s = cardSkill;
                    if (s == null && a.no >= 51 && a.no <= 54) s = Skill.WRITING;
                    String key = (s != null ? s.name() : "") + "#" + a.no;
                    String img = !blank(a.chart) ? a.chart : c.imageFile;
                    if (!blank(img) && !isImagePlaceholder(img) && !img.trim().startsWith("[")) {
                        prevCardImages.put(key, img.trim());
                    }
                }
            }
        }

        // 1. Xóa các card cũ thuộc các kỹ năng cần cập nhật
        for (Skill sk : skillsToUpdate) {
            wb.manualCards.removeIf(c -> {
                QuestionTemplate t = blank(c.templateCode) ? null : templateRepo.findByCode(c.templateCode).orElse(null);
                if (t != null) return t.getSkill() == sk;
                QuestionTypeDef td = blank(c.typeCode) ? null : QuestionTypeCatalog.byCode(c.typeCode).orElse(null);
                if (td != null) return td.skill() == sk;
                if (!c.asks.isEmpty() && c.asks.get(0).no != null) {
                    int no = c.asks.get(0).no;
                    return QuestionTypeCatalog.section(track, sk).map(s -> s.contains(no)).orElse(false);
                }
                return false;
            });
        }

        // 2. Chuyển đổi các câu trắc nghiệm (Nghe, Đọc) sang ManualCard
        for (Skill sk : skillsToUpdate) {
            if (sk == Skill.WRITING) continue;

            List<ParsedWorkbook.QuestionRow> skillQuestions = wb.questions.stream()
                    .filter(q -> q.skill == sk && q.no != null)
                    .sorted(Comparator.comparingInt(q -> q.no))
                    .toList();
            if (skillQuestions.isEmpty()) continue;

            // Gom nhóm câu tự động: 2 hoặc 3 câu dùng chung đoạn văn (Đọc) hoặc 2 câu dùng chung phụ đề/audio (Nghe)
            // hoặc theo cột dạng (type_code / type_name) hoặc group_id
            List<List<ParsedWorkbook.QuestionRow>> groups = groupQuestionsIntoCards(track, sk, skillQuestions);
            for (List<ParsedWorkbook.QuestionRow> groupQs : groups) {
                if (groupQs.isEmpty()) continue;
                ParsedWorkbook.QuestionRow headQ = groupQs.get(0);
                QuestionTypeDef type = resolveQuestionType(track, sk, headQ);
                String typeCode = type != null ? type.code() : headQ.typeCode;

                // Tìm tài nguyên dùng chung trong toàn bộ nhóm
                String sharedAudio = null;
                String sharedScript = null;
                String sharedPassage = null;
                String sharedImage = null;

                for (ParsedWorkbook.QuestionRow q : groupQs) {
                    if (sharedAudio == null && !blank(q.audioFile) && !isImagePlaceholder(q.audioFile)) {
                        sharedAudio = q.audioFile.trim();
                    }
                    if (sharedScript == null && !blank(q.scriptKo)) {
                        sharedScript = cleanPromptText(q.scriptKo);
                    }
                    if (sharedPassage == null && !blank(q.passageKo)) {
                        sharedPassage = cleanPromptText(q.passageKo);
                    }
                    if (sharedImage == null && !blank(q.imageFile) && !isImagePlaceholder(q.imageFile)) {
                        sharedImage = cleanImage(q.imageFile);
                    }
                    if (sharedImage == null && q.no != null && prevCardImages.containsKey(sk.name() + "#" + q.no)) {
                        sharedImage = prevCardImages.get(sk.name() + "#" + q.no);
                    }
                }

                // Nếu là bài Nghe mà chưa có sharedAudio, tự động khớp từ tệp đính kèm đã tải lên
                if (sharedAudio == null && sk == Skill.LISTENING && !audioAssetMap.isEmpty()) {
                    for (ParsedWorkbook.QuestionRow q : groupQs) {
                        if (q.no != null && audioAssetMap.containsKey(q.no)) {
                            sharedAudio = audioAssetMap.get(q.no);
                            break;
                        }
                    }
                }

                // Cập nhật ngược lại cho các QuestionRow trong nhóm để đồng bộ dữ liệu
                for (ParsedWorkbook.QuestionRow q : groupQs) {
                    if (blank(q.audioFile) && sharedAudio != null) q.audioFile = sharedAudio;
                    if (blank(q.scriptKo) && sharedScript != null) q.scriptKo = sharedScript;
                    if (blank(q.passageKo) && sharedPassage != null) q.passageKo = sharedPassage;
                    if (blank(q.imageFile) && sharedImage != null) q.imageFile = sharedImage;
                    if (typeCode != null) {
                        q.typeCode = typeCode;
                        q.resolvedType = typeCode;
                    }
                }

                // Nếu typeCode chưa xác định được nhưng nhóm có 2 hoặc 3 câu:
                if (typeCode == null) {
                    if (sk == Skill.READING) {
                        if (groupQs.size() >= 3) {
                            typeCode = (track == Track.TOPIK_I) ? "I-D10" : "II-D12";
                        } else if (groupQs.size() == 2) {
                            typeCode = (track == Track.TOPIK_I) ? "I-D6" : "II-D7";
                        }
                    } else if (sk == Skill.LISTENING && groupQs.size() >= 2) {
                        typeCode = (track == Track.TOPIK_I) ? "I-N8" : "II-N6";
                    }
                }

                String tplCode = typeCode != null ? QuestionTemplateCatalog.templateCode(typeCode) : null;
                if (tplCode == null) {
                    List<QuestionTemplate> moldList = templateRepo.findByTrackAndSkillOrderByCodeAsc(track, sk);
                    if (!moldList.isEmpty()) tplCode = moldList.get(0).getCode();
                }

                ParsedWorkbook.ManualCard card = new ParsedWorkbook.ManualCard();
                card.id = "card-" + sk.name().toLowerCase(Locale.ROOT) + "-" + headQ.no;
                card.templateCode = tplCode;
                card.typeCode = typeCode;
                card.audioFile = emptyToNull(sharedAudio);
                card.scriptKo = emptyToNull(sharedScript);
                card.passageKo = emptyToNull(sharedPassage);
                card.imageFile = emptyToNull(sharedImage);

                for (ParsedWorkbook.QuestionRow q : groupQs) {
                    q.groupId = card.id;
                    ParsedWorkbook.ManualAsk ask = new ParsedWorkbook.ManualAsk();
                    ask.no = q.no;
                    ask.promptKo = emptyToNull(cleanPromptText(q.promptKo));
                    ask.options = new String[]{
                            cleanOptionText(q.options != null && q.options.length > 0 ? q.options[0] : ""),
                            cleanOptionText(q.options != null && q.options.length > 1 ? q.options[1] : ""),
                            cleanOptionText(q.options != null && q.options.length > 2 ? q.options[2] : ""),
                            cleanOptionText(q.options != null && q.options.length > 3 ? q.options[3] : "")
                    };
                    ask.answer = q.answer;
                    ask.answerRaw = q.answerRaw != null ? q.answerRaw : (q.answer != null ? String.valueOf(q.answer) : "");
                    ask.explanationVi = emptyToNull(q.explanationVi);
                    String askImg = cleanImage(q.imageFile);
                    if (askImg == null && q.no != null) askImg = prevCardImages.get(sk.name() + "#" + q.no);
                    ask.chart = emptyToNull(askImg);
                    ask.optImgs = new String[4];
                    card.asks.add(ask);
                }
                wb.manualCards.add(card);
            }
        }

        // 3. Chuyển đổi các câu Viết (51-54) sang ManualCard
        if (skillsToUpdate.contains(Skill.WRITING)) {
            for (ParsedWorkbook.WritingRow w : wb.writings) {
                if (w.no == null) continue;
                QuestionTypeDef type = QuestionTypeCatalog.resolve(track, Skill.WRITING, w.no).orElse(null);
                String typeCode = type != null ? type.code() : ("II-V" + (w.no - 50));
                String tplCode = QuestionTemplateCatalog.templateCode(typeCode);
                if (tplCode == null) tplCode = "TPL2-WRI-" + String.format("%02d", w.no - 50);

                ParsedWorkbook.ManualCard card = new ParsedWorkbook.ManualCard();
                card.id = "card-writing-" + w.no;
                card.templateCode = tplCode;
                card.typeCode = typeCode;
                card.passageKo = emptyToNull(cleanPromptText(w.passageKo));

                ParsedWorkbook.ManualAsk ask = new ParsedWorkbook.ManualAsk();
                ask.no = w.no;
                ask.promptKo = emptyToNull(cleanPromptText(w.promptKo));
                ask.keys1 = emptyToNull(w.keys1);
                ask.keys2 = emptyToNull(w.keys2);
                ask.modelAnswer = emptyToNull(w.modelAnswer);
                ask.rubricNote = emptyToNull(w.rubricNote);
                String chart = cleanImage(w.chartJson);
                if (chart == null && prevCardImages.containsKey("WRITING#" + w.no)) chart = prevCardImages.get("WRITING#" + w.no);
                ask.chart = emptyToNull(chart);
                card.asks.add(ask);

                wb.manualCards.add(card);
            }
        }

        // 4. Sắp xếp thứ tự các card theo thứ tự hiển thị chuẩn của đề
        List<QuestionTypeCatalog.Section> sections = QuestionTypeCatalog.sections(track);
        wb.manualCards.sort(Comparator.comparingInt((ParsedWorkbook.ManualCard c) -> {
            if (c.asks.isEmpty() || c.asks.get(0).no == null) return 999;
            int firstNo = c.asks.get(0).no;
            for (int i = 0; i < sections.size(); i++) {
                if (sections.get(i).contains(firstNo)) {
                    return i * 1000 + firstNo;
                }
            }
            return firstNo;
        }));
    }

    private QuestionTypeDef resolveQuestionType(Track track, Skill sk, ParsedWorkbook.QuestionRow q) {
        if (q == null) return null;
        QuestionTypeDef type = null;
        if (!blank(q.typeCode)) {
            type = QuestionTypeCatalog.byCode(q.typeCode).orElse(null);
            if (type == null) {
                type = QuestionTypeCatalog.forTrackAndSkill(track, sk).stream()
                        .filter(t -> t.name().equalsIgnoreCase(q.typeCode.trim())
                                || t.code().equalsIgnoreCase(q.typeCode.trim())
                                || t.name().toLowerCase(Locale.ROOT).contains(q.typeCode.trim().toLowerCase(Locale.ROOT)))
                        .findFirst().orElse(null);
            }
        }
        if (type == null && q.no != null) {
            type = QuestionTypeCatalog.resolve(track, sk, q.no).orElse(null);
        }
        return type;
    }

    private static String normalizeStimulus(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private List<List<ParsedWorkbook.QuestionRow>> groupQuestionsIntoCards(
            Track track, Skill sk, List<ParsedWorkbook.QuestionRow> skillQuestions) {
        List<List<ParsedWorkbook.QuestionRow>> result = new ArrayList<>();
        if (skillQuestions == null || skillQuestions.isEmpty()) return result;

        int n = skillQuestions.size();
        int i = 0;
        while (i < n) {
            ParsedWorkbook.QuestionRow q0 = skillQuestions.get(i);
            QuestionTypeDef type0 = resolveQuestionType(track, sk, q0);

            int groupSize = 1;
            int maxAllowed = (sk == Skill.READING) ? 3 : 2;

            // 1. Kiểm tra theo group_id rõ ràng nếu có
            if (!blank(q0.groupId)) {
                int j = i + 1;
                while (j < n && (j - i) < maxAllowed) {
                    ParsedWorkbook.QuestionRow qNext = skillQuestions.get(j);
                    if (!blank(qNext.groupId) && qNext.groupId.trim().equalsIgnoreCase(q0.groupId.trim())) {
                        j++;
                    } else {
                        break;
                    }
                }
                if (j - i > 1) {
                    groupSize = j - i;
                }
            }

            // 2. Kiểm tra theo block chuẩn TOPIK nếu q0.no thuộc block chuẩn
            if (groupSize == 1 && type0 != null && q0.no != null) {
                List<Integer> block = type0.blockOf(q0.no);
                if (block != null && block.size() > 1 && block.get(0).equals(q0.no)) {
                    int expectedSize = Math.min(block.size(), maxAllowed);
                    int j = i + 1;
                    while (j < n && (j - i) < expectedSize) {
                        ParsedWorkbook.QuestionRow qNext = skillQuestions.get(j);
                        if (qNext.no != null && block.contains(qNext.no)) {
                            j++;
                        } else {
                            break;
                        }
                    }
                    if (j - i > 1) {
                        groupSize = j - i;
                    }
                }
            }

            // 3. Kiểm tra theo Cột Dạng (type_code / type_name) và Ngữ liệu dùng chung (passage_ko / script_ko / audio_file)
            if (groupSize == 1) {
                boolean isGroupedType = (type0 != null && type0.isGrouped());
                String code0 = type0 != null ? type0.code() : (q0.typeCode != null ? q0.typeCode : "");

                if (i + 1 < n) {
                    ParsedWorkbook.QuestionRow q1 = skillQuestions.get(i + 1);
                    QuestionTypeDef type1 = resolveQuestionType(track, sk, q1);
                    String code1 = type1 != null ? type1.code() : (q1.typeCode != null ? q1.typeCode : "");

                    boolean sameTypeOrBlank = isGroupedType && (blank(code1) || code1.equalsIgnoreCase(code0));

                    boolean stimulusShared = false;
                    if (sk == Skill.READING) {
                        String pass0 = normalizeStimulus(q0.passageKo);
                        String pass1 = normalizeStimulus(q1.passageKo);
                        boolean samePassage = !pass0.isEmpty() && pass0.equals(pass1);
                        boolean q1PassageBlank = !pass0.isEmpty() && pass1.isEmpty();
                        stimulusShared = samePassage || (sameTypeOrBlank && q1PassageBlank);
                    } else if (sk == Skill.LISTENING) {
                        String sc0 = normalizeStimulus(q0.scriptKo);
                        String sc1 = normalizeStimulus(q1.scriptKo);
                        boolean sameScript = !sc0.isEmpty() && sc0.equals(sc1);
                        boolean q1ScriptBlank = !sc0.isEmpty() && sc1.isEmpty();

                        String au0 = q0.audioFile != null ? q0.audioFile.trim() : "";
                        String au1 = q1.audioFile != null ? q1.audioFile.trim() : "";
                        boolean sameAudio = !au0.isEmpty() && au0.equalsIgnoreCase(au1);
                        boolean q1AudioBlank = !au0.isEmpty() && au1.isEmpty();

                        stimulusShared = sameScript || sameAudio || (sameTypeOrBlank && (q1ScriptBlank || q1AudioBlank));
                    }

                    if (sameTypeOrBlank || stimulusShared) {
                        int potentialSize = 2;

                        // Nếu là Đọc (Reading) và có thể là 3 câu (dạng II-D12 hoặc 3 câu cùng đoạn văn)
                        if (sk == Skill.READING && i + 2 < n) {
                            ParsedWorkbook.QuestionRow q2 = skillQuestions.get(i + 2);
                            QuestionTypeDef type2 = resolveQuestionType(track, sk, q2);
                            String code2 = type2 != null ? type2.code() : (q2.typeCode != null ? q2.typeCode : "");
                            boolean q2SameTypeOrBlank = isGroupedType && (blank(code2) || code2.equalsIgnoreCase(code0));

                            String pass0 = normalizeStimulus(q0.passageKo);
                            String pass2 = normalizeStimulus(q2.passageKo);
                            boolean samePassage2 = !pass0.isEmpty() && pass0.equals(pass2);
                            boolean q2PassageBlank = !pass0.isEmpty() && pass2.isEmpty();

                            boolean stimulusShared2 = samePassage2 || (q2SameTypeOrBlank && q2PassageBlank);

                            if (("II-D12".equalsIgnoreCase(code0) && (q2SameTypeOrBlank || stimulusShared2))
                                    || (samePassage2 && !pass0.isEmpty())) {
                                potentialSize = 3;
                            }
                        }

                        groupSize = potentialSize;
                    }
                }
            }

            List<ParsedWorkbook.QuestionRow> group = new ArrayList<>();
            for (int k = 0; k < groupSize && (i + k) < n; k++) {
                group.add(skillQuestions.get(i + k));
            }
            result.add(group);
            i += group.size();
        }
        return result;
    }

    public void cancelTempImport(String token) {
        TempImportEntry entry = tempImports.remove(token);
        if (entry != null) {
            try {
                Files.deleteIfExists(entry.filePath());
            } catch (Exception ignored) {
            }
        }
    }

    private final Map<String, TempBulkAudioEntry> tempAudioBatches = new ConcurrentHashMap<>();

    private void cleanExpiredTempAudioBatches() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        tempAudioBatches.entrySet().removeIf(entry -> {
            if (entry.getValue().createdAt().isBefore(cutoff)) {
                try {
                    deleteDirectoryRecursively(entry.getValue().tempDir());
                } catch (Exception ignored) {}
                return true;
            }
            return false;
        });
    }

    private static void deleteDirectoryRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public static List<Integer> extractQuestionNumbers(String filename) {
        if (filename == null || filename.isBlank()) return List.of();
        String name = MediaStorageService.baseName(filename);
        if (name.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
            name = name.substring(0, name.length() - 4);
        }
        name = name.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) return List.of();

        // 1. Dải câu hỏi nối bằng dấu gạch ngang hoặc ngã: e.g. 21-22, 21~22, 21 - 22, cau_21-22, q21-22
        java.util.regex.Matcher rm = java.util.regex.Pattern.compile("(?:^|[^0-9])0*(\\d{1,2})\\s*[-~]\\s*0*(\\d{1,2})(?:[^0-9]|$)").matcher(name);
        if (rm.find()) {
            try {
                int start = Integer.parseInt(rm.group(1));
                int end = Integer.parseInt(rm.group(2));
                if (start > 0 && end >= start && (end - start) <= 10) {
                    List<Integer> list = new ArrayList<>();
                    for (int i = start; i <= end; i++) list.add(i);
                    return list;
                }
            } catch (NumberFormatException ignored) {}
        }

        // 2. Liệt kê các câu dùng chung phân cách bởi phẩy, và, chấm: e.g. "21, 22", "21,22", "21 & 22", "cau 21, 22", "21. 22"
        java.util.regex.Matcher enumMatcher = java.util.regex.Pattern.compile("0*(\\d{1,2})(?:\\s*[,&.]\\s*0*(\\d{1,2}))+").matcher(name);
        if (enumMatcher.find()) {
            java.util.regex.Matcher numMatcher = java.util.regex.Pattern.compile("0*(\\d{1,2})").matcher(enumMatcher.group());
            List<Integer> list = new ArrayList<>();
            while (numMatcher.find()) {
                try {
                    int n = Integer.parseInt(numMatcher.group(1));
                    if (n > 0 && !list.contains(n)) list.add(n);
                } catch (NumberFormatException ignored) {}
            }
            if (!list.isEmpty()) return list;
        }

        // 3. Hai câu liên tiếp phân cách bởi gạch dưới hoặc khoảng trắng: e.g. "21_22", "21 22", "cau_21_22"
        java.util.regex.Matcher pairMatcher = java.util.regex.Pattern.compile("(?:^|[^0-9])0*(\\d{1,2})[_\\s]+0*(\\d{1,2})(?:[^0-9]|$)").matcher(name);
        if (pairMatcher.find()) {
            try {
                int a = Integer.parseInt(pairMatcher.group(1));
                int b = Integer.parseInt(pairMatcher.group(2));
                if (a > 0 && b > a && (b - a) <= 5) {
                    List<Integer> list = new ArrayList<>();
                    for (int i = a; i <= b; i++) list.add(i);
                    return list;
                }
            } catch (NumberFormatException ignored) {}
        }

        // 4. Một câu duy nhất: e.g. 01, 14, cau_04, cau14, q-02, audio_15
        java.util.regex.Matcher sm = java.util.regex.Pattern.compile("^(?:.*?(?:cau|câu|q|question|bai|bài|_|-|\\s|^))?0*(\\d{1,2})(?:[^0-9].*)?$").matcher(name);
        if (sm.matches()) {
            try {
                int n = Integer.parseInt(sm.group(1));
                if (n > 0) return List.of(n);
            } catch (NumberFormatException ignored) {}
        }

        // 5. Dự phòng: tìm số 1-2 chữ số trong tên file
        java.util.regex.Matcher anyNum = java.util.regex.Pattern.compile("(?:^|[^0-9])0*(\\d{1,2})(?:[^0-9]|$)").matcher(name);
        if (anyNum.find()) {
            try {
                int n = Integer.parseInt(anyNum.group(1));
                if (n > 0) return List.of(n);
            } catch (NumberFormatException ignored) {}
        }

        return List.of();
    }

    public static Integer extractQuestionNumber(String filename) {
        List<Integer> list = extractQuestionNumbers(filename);
        return list.isEmpty() ? null : list.get(0);
    }

    public Map<Integer, String> buildAudioMapFromMediaAssets(ExamSource source) {
        if (source == null || source.getId() == null || media == null) return Map.of();
        List<MediaAsset> audioAssets = media.findBySourceIdOrderByFilenameAsc(source.getId()).stream()
                .filter(m -> m.getKind() == MediaKind.AUDIO)
                .toList();
        if (audioAssets.isEmpty()) return Map.of();

        Map<Integer, String> questionToAudioMap = new HashMap<>();
        for (MediaAsset a : audioAssets) {
            String filename = a.getFilename();
            List<Integer> qNos = extractQuestionNumbers(filename);
            for (int no : qNos) {
                questionToAudioMap.putIfAbsent(no, filename);
            }
        }

        int maxListening = source.getTrack() == Track.TOPIK_I ? 30 : 50;
        for (int i = 1; i <= maxListening; i++) {
            if (questionToAudioMap.containsKey(i)) {
                String fn = questionToAudioMap.get(i);
                QuestionTypeDef type = QuestionTypeCatalog.resolve(source.getTrack(), Skill.LISTENING, i).orElse(null);
                if (type != null) {
                    List<Integer> block = type.blockOf(i);
                    if (block != null && block.size() > 1 && block.get(0) == i) {
                        for (int other : block) {
                            questionToAudioMap.putIfAbsent(other, fn);
                        }
                    }
                }
            }
        }
        return questionToAudioMap;
    }

    public void autoMatchAudioFromMediaAssets(ExamSource source, ParsedWorkbook wb) {
        if (source == null || wb == null) return;
        Map<Integer, String> audioMap = buildAudioMapFromMediaAssets(source);
        if (audioMap.isEmpty()) return;

        for (ParsedWorkbook.QuestionRow q : wb.questions) {
            if (q.skill == Skill.LISTENING && q.no != null) {
                if (blank(q.audioFile) || isImagePlaceholder(q.audioFile)) {
                    String matched = audioMap.get(q.no);
                    if (matched != null) {
                        q.audioFile = matched;
                    }
                }
            }
        }

        for (ParsedWorkbook.ManualCard card : wb.manualCards) {
            QuestionTemplate t = blank(card.templateCode) ? null : (templateRepo != null ? templateRepo.findByCode(card.templateCode).orElse(null) : null);
            boolean isListening = (t != null && t.getSkill() == Skill.LISTENING);
            if (!isListening && !card.asks.isEmpty() && card.asks.get(0).no != null) {
                isListening = QuestionTypeCatalog.section(source.getTrack(), Skill.LISTENING)
                        .map(s -> s.contains(card.asks.get(0).no)).orElse(false);
            }
            if (isListening && blank(card.audioFile)) {
                for (ParsedWorkbook.ManualAsk ask : card.asks) {
                    if (ask.no != null && audioMap.containsKey(ask.no)) {
                        card.audioFile = audioMap.get(ask.no);
                        break;
                    }
                }
            }
        }
    }

    public Map<Integer, String> buildImageMapFromMediaAssets(ExamSource source) {
        return Map.of();
    }

    public void autoMatchImagesFromMediaAssets(ExamSource source, ParsedWorkbook wb) {
        // Không tự động gán ảnh từ media assets vào câu hỏi để tránh dán nhầm ảnh phương án hoặc ảnh không liên quan
    }

    private void preserveMediaFromPreviousDraft(ParsedWorkbook currentWb, ParsedWorkbook incoming) {
        if (currentWb == null || incoming == null) return;
        Map<String, ParsedWorkbook.QuestionRow> existingMap = currentWb.questions.stream()
                .filter(q -> q.skill != null && q.no != null)
                .collect(Collectors.toMap(q -> q.skill.name() + "#" + q.no, q -> q, (a, b) -> a));

        // Trích xuất ảnh và audio từ manualCards của draft hiện tại (phân tách theo skill#no)
        Map<String, String> cardImageMap = new HashMap<>();
        Map<String, String> cardAudioMap = new HashMap<>();
        for (ParsedWorkbook.ManualCard c : currentWb.manualCards) {
            QuestionTemplate t = blank(c.templateCode) ? null : (templateRepo != null ? templateRepo.findByCode(c.templateCode).orElse(null) : null);
            Skill cardSkill = t != null ? t.getSkill() : null;
            if (cardSkill == null && !blank(c.typeCode)) {
                QuestionTypeDef td = QuestionTypeCatalog.byCode(c.typeCode).orElse(null);
                if (td != null) cardSkill = td.skill();
            }
            for (ParsedWorkbook.ManualAsk a : c.asks) {
                if (a.no != null) {
                    Skill s = cardSkill;
                    if (s == null && a.no >= 51 && a.no <= 54) s = Skill.WRITING;
                    String key = (s != null ? s.name() : "") + "#" + a.no;
                    String img = !blank(a.chart) ? a.chart : c.imageFile;
                    if (!blank(img) && !isImagePlaceholder(img) && !img.trim().startsWith("[")) {
                        cardImageMap.put(key, img.trim());
                    }
                    if (!blank(c.audioFile)) {
                        cardAudioMap.put(key, c.audioFile.trim());
                    }
                }
            }
        }

        for (ParsedWorkbook.QuestionRow inQ : incoming.questions) {
            if (inQ.skill == null || inQ.no == null) continue;
            String key = inQ.skill.name() + "#" + inQ.no;
            ParsedWorkbook.QuestionRow ex = existingMap.get(key);
            if (ex != null) {
                if ((blank(inQ.audioFile) || isImagePlaceholder(inQ.audioFile)) && !blank(ex.audioFile)) {
                    inQ.audioFile = ex.audioFile;
                }
                if ((blank(inQ.imageFile) || isImagePlaceholder(inQ.imageFile)) && !blank(ex.imageFile) && !isImagePlaceholder(ex.imageFile)) {
                    inQ.imageFile = ex.imageFile;
                }
            }
            if ((blank(inQ.imageFile) || isImagePlaceholder(inQ.imageFile)) && cardImageMap.containsKey(key)) {
                inQ.imageFile = cardImageMap.get(key);
            }
            if ((blank(inQ.audioFile) || isImagePlaceholder(inQ.audioFile)) && cardAudioMap.containsKey(key)) {
                inQ.audioFile = cardAudioMap.get(key);
            }
        }
        Map<Integer, ParsedWorkbook.WritingRow> existingWritingMap = currentWb.writings.stream()
                .filter(w -> w.no != null)
                .collect(Collectors.toMap(w -> w.no, w -> w, (a, b) -> a));
        for (ParsedWorkbook.WritingRow inW : incoming.writings) {
            if (inW.no == null) continue;
            ParsedWorkbook.WritingRow ex = existingWritingMap.get(inW.no);
            if (ex != null) {
                if ((blank(inW.chartJson) || isImagePlaceholder(inW.chartJson)) && !blank(ex.chartJson)) {
                    inW.chartJson = ex.chartJson;
                }
            }
            String wKey = "WRITING#" + inW.no;
            if ((blank(inW.chartJson) || isImagePlaceholder(inW.chartJson)) && cardImageMap.containsKey(wKey)) {
                inW.chartJson = cardImageMap.get(wKey);
            }
        }
    }

    public BulkAudioPreCheckReport preCheckBulkAudio(Long sourceId, MultipartFile file, Actor actor) throws IOException {
        requireContent(actor);
        ExamSource source = draftSource(sourceId);
        if (file == null || file.isEmpty()) {
            throw new ExamDomainException("Vui lòng chọn file nén .zip chứa các file audio .mp3.");
        }
        String originalName = MediaStorageService.baseName(file.getOriginalFilename());
        if (!"zip".equalsIgnoreCase(MediaStorageService.extension(originalName))) {
            throw new ExamDomainException("Chỉ nhận file nén định dạng .zip.");
        }
        if (file.getSize() > MediaStorageService.MAX_ZIP_BYTES) {
            throw new ExamDomainException("File .zip tối đa 300 MB.");
        }

        cleanExpiredTempAudioBatches();

        Path tempDir = Files.createTempDirectory("ht-bulk-audio-" + sourceId + "-");
        List<BulkAudioIssue> issues = new ArrayList<>();
        Map<String, Path> extractedFiles = new LinkedHashMap<>();

        byte[] zipBytes = file.getBytes();
        java.nio.charset.Charset[] charsets = new java.nio.charset.Charset[]{
                StandardCharsets.UTF_8,
                java.nio.charset.Charset.forName("MS949"),
                StandardCharsets.ISO_8859_1
        };
        for (java.nio.charset.Charset cs : charsets) {
            try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes), cs)) {
                ZipEntry entry;
                extractedFiles.clear();
                issues.clear();
                while ((entry = zin.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    String entryName = entry.getName();
                    String base = MediaStorageService.baseName(entryName);
                    if (base.isEmpty() || base.startsWith(".") || entryName.startsWith("__MACOSX")
                            || "Thumbs.db".equalsIgnoreCase(base) || "desktop.ini".equalsIgnoreCase(base)) {
                        continue;
                    }
                    Path dest = tempDir.resolve(base).normalize();
                    if (!dest.startsWith(tempDir)) {
                        issues.add(new BulkAudioIssue(base, null, "ERROR", "Đường dẫn file trong zip không an toàn (zip slip)."));
                        continue;
                    }
                    Files.copy(zin, dest, StandardCopyOption.REPLACE_EXISTING);
                    extractedFiles.put(base, dest);
                }
                if (!extractedFiles.isEmpty()) {
                    break;
                }
            } catch (Exception ignored) {
                // Thử charset kế tiếp
            }
        }

        if (extractedFiles.isEmpty()) {
            deleteDirectoryRecursively(tempDir);
            throw new ExamDomainException("File .zip không chứa file nào hoặc chỉ chứa thư mục rỗng.");
        }

        int maxListening = source.getTrack() == Track.TOPIK_I ? 30 : 50;
        Map<Integer, String> validFiles = new TreeMap<>();
        List<BulkAudioMatch> matches = new ArrayList<>();

        for (Map.Entry<String, Path> item : extractedFiles.entrySet()) {
            String filename = item.getKey();
            Path path = item.getValue();
            long size = Files.size(path);

            if (!MediaStorageService.isAudio(filename)) {
                issues.add(new BulkAudioIssue(filename, null, "ERROR",
                        "File '" + filename + "' không phải file âm thanh .mp3 hợp lệ (bỏ qua file này)."));
                continue;
            }

            if (size > MediaStorageService.MAX_AUDIO_BYTES) {
                issues.add(new BulkAudioIssue(filename, null, "ERROR",
                        "File '" + filename + "' vượt quá kích thước cho phép 15 MB (" + formatBytes(size) + ")."));
                continue;
            }

            List<Integer> qNos = extractQuestionNumbers(filename);
            if (qNos.isEmpty()) {
                issues.add(new BulkAudioIssue(filename, null, "ERROR",
                        "File '" + filename + "' không chứa số thứ tự câu hỏi hợp lệ (Ví dụ: 01.mp3, 21, 22.mp3, cau_14.mp3)."));
                continue;
            }

            // Kiểm tra từng câu trong danh sách xem có nằm ngoài phạm vi đề không
            boolean outOfRange = false;
            for (int qNo : qNos) {
                if (qNo < 1 || qNo > maxListening) {
                    issues.add(new BulkAudioIssue(filename, qNos, "ERROR",
                            "File '" + filename + "' chứa câu số " + qNo + " nằm ngoài phạm vi kỹ năng Nghe của "
                                    + source.getTrack().getLabel() + " (Câu 1 đến Câu " + maxListening + ")."));
                    outOfRange = true;
                    break;
                }
            }
            if (outOfRange) continue;

            // Kiểm tra trùng lặp với file đã nhận diện trước đó
            boolean hasDuplicate = false;
            for (int qNo : qNos) {
                if (validFiles.containsKey(qNo)) {
                    String existing = validFiles.get(qNo);
                    issues.add(new BulkAudioIssue(filename, qNos, "ERROR",
                            "Trùng số câu " + qNo + ": file '" + filename + "' bị trùng câu hỏi với file '" + existing + "'."));
                    hasDuplicate = true;
                    break;
                }
            }
            if (hasDuplicate) continue;

            for (int qNo : qNos) {
                validFiles.put(qNo, filename);
            }

            String qLabel = qNos.size() == 1
                    ? ("Câu " + qNos.get(0))
                    : ("Câu " + qNos.stream().map(String::valueOf).collect(Collectors.joining(", ")));
            matches.add(new BulkAudioMatch(filename, qNos, qLabel, size, formatBytes(size)));
        }

        // Tự động mở rộng cho các bài nghe dùng chung (cặp câu như 21-22, 23-24...) nếu đặt tên đại diện câu đầu và các câu sau chưa có file
        for (BulkAudioMatch match : new ArrayList<>(matches)) {
            if (match.questionNos().size() == 1) {
                int singleNo = match.questionNos().get(0);
                QuestionTypeDef type = QuestionTypeCatalog.resolve(source.getTrack(), Skill.LISTENING, singleNo).orElse(null);
                if (type != null) {
                    List<Integer> block = type.blockOf(singleNo);
                    if (block != null && block.size() > 1 && block.get(0) == singleNo) {
                        boolean othersFree = true;
                        for (int other : block) {
                            if (other != singleNo && validFiles.containsKey(other)) {
                                othersFree = false;
                                break;
                            }
                        }
                        if (othersFree) {
                            for (int other : block) {
                                validFiles.put(other, match.filename());
                            }
                            String newLabel = "Câu " + block.stream().map(String::valueOf).collect(Collectors.joining(", "));
                            matches.remove(match);
                            matches.add(new BulkAudioMatch(match.filename(), block, newLabel, match.sizeBytes(), match.sizeFormatted()));
                        }
                    }
                }
            }
        }

        List<Integer> missingQuestions = new ArrayList<>();
        for (int i = 1; i <= maxListening; i++) {
            if (!validFiles.containsKey(i)) {
                missingQuestions.add(i);
            }
        }

        if (!missingQuestions.isEmpty()) {
            String missingListStr = missingQuestions.stream().map(n -> "Câu " + n).collect(Collectors.joining(", "));
            issues.add(new BulkAudioIssue(null, null, "WARNING",
                    "Đề thi còn " + missingQuestions.size() + " câu chưa có audio: " + missingListStr + "."));
        }

        int errorCount = (int) issues.stream().filter(i -> "ERROR".equals(i.severity())).count();
        boolean ok = errorCount == 0 && missingQuestions.isEmpty();

        String token = UUID.randomUUID().toString();
        tempAudioBatches.put(token, new TempBulkAudioEntry(token, tempDir, originalName, sourceId, source.getTrack(), validFiles, LocalDateTime.now()));

        return new BulkAudioPreCheckReport(
                ok,
                token,
                originalName,
                source.getTrack().getLabel(),
                maxListening,
                validFiles.size(),
                missingQuestions.size(),
                errorCount,
                missingQuestions,
                matches,
                issues
        );
    }

    @Transactional
    public BulkAudioConfirmResult confirmBulkAudio(Long sourceId, String token, Actor actor) throws IOException {
        requireContent(actor);
        ExamSource source = draftSource(sourceId);
        TempBulkAudioEntry entry = tempAudioBatches.remove(token);
        if (entry == null) {
            throw new ExamDomainException("Phiên tải audio đã hết hạn hoặc không tồn tại. Vui lòng chọn lại file .zip.");
        }
        Path tempDir = entry.tempDir();
        Map<Integer, String> validFiles = entry.validFiles();
        if (validFiles.isEmpty()) {
            deleteDirectoryRecursively(tempDir);
            throw new ExamDomainException("Không có file audio hợp lệ nào để nạp.");
        }

        try {
            int storedCount = 0;
            for (Map.Entry<Integer, String> e : validFiles.entrySet()) {
                String filename = e.getValue();
                Path filePath = tempDir.resolve(filename);
                if (!Files.exists(filePath)) continue;
                long size = Files.size(filePath);
                try (InputStream in = Files.newInputStream(filePath)) {
                    Path p = storage.storeMedia(sourceId, filename, in);
                    MediaAsset asset = media.findBySourceIdAndFilename(sourceId, filename).orElseGet(MediaAsset::new);
                    asset.setSource(source);
                    asset.setFilename(filename);
                    asset.setKind(MediaKind.AUDIO);
                    asset.setSizeBytes(size);
                    asset.setStoragePath(storage.toStored(p));
                    media.save(asset);
                    storedCount++;
                }
            }

            Optional<ImportJob> jobOpt = latestJob(sourceId);
            ImportJob job = jobOpt.orElseGet(() -> startManual(sourceId, actor));
            ParsedWorkbook wb = workbookOf(job);

            // Cập nhật vào manualCards
            for (Map.Entry<Integer, String> e : validFiles.entrySet()) {
                int qNo = e.getKey();
                String filename = e.getValue();
                boolean cardFound = false;
                for (ParsedWorkbook.ManualCard card : wb.manualCards) {
                    if (card.asks.stream().anyMatch(a -> a.no != null && a.no == qNo)) {
                        card.audioFile = filename;
                        cardFound = true;
                        break;
                    }
                }
                if (!cardFound) {
                    QuestionTypeDef type = QuestionTypeCatalog.resolve(source.getTrack(), Skill.LISTENING, qNo).orElse(null);
                    List<Integer> block = type != null ? type.blockOf(qNo) : List.of(qNo);
                    int headNo = (block != null && !block.isEmpty()) ? block.get(0) : qNo;

                    ParsedWorkbook.ManualCard blockCard = null;
                    for (ParsedWorkbook.ManualCard card : wb.manualCards) {
                        if (card.asks.stream().anyMatch(a -> a.no != null && block.contains(a.no))) {
                            blockCard = card;
                            break;
                        }
                    }

                    if (blockCard != null) {
                        blockCard.audioFile = filename;
                        if (blockCard.asks.stream().noneMatch(a -> a.no != null && a.no == qNo)) {
                            ParsedWorkbook.ManualAsk ask = new ParsedWorkbook.ManualAsk();
                            ask.no = qNo;
                            blockCard.asks.add(ask);
                        }
                    } else {
                        String typeCode = type != null ? type.code() : ("I-N" + headNo);
                        String tplCode = QuestionTemplateCatalog.templateCode(typeCode);
                        if (tplCode == null) {
                            List<QuestionTemplate> moldList = templateRepo.findByTrackAndSkillOrderByCodeAsc(source.getTrack(), Skill.LISTENING);
                            if (!moldList.isEmpty()) tplCode = moldList.get(0).getCode();
                        }
                        ParsedWorkbook.ManualCard card = new ParsedWorkbook.ManualCard();
                        card.id = "card-listening-" + headNo;
                        card.templateCode = tplCode;
                        card.typeCode = typeCode;
                        card.audioFile = filename;
                        ParsedWorkbook.ManualAsk ask = new ParsedWorkbook.ManualAsk();
                        ask.no = qNo;
                        card.asks.add(ask);
                        wb.manualCards.add(card);
                    }
                }

                // Đồng bộ vào wb.questions
                Optional<ParsedWorkbook.QuestionRow> qRow = wb.questions.stream()
                        .filter(q -> q.skill == Skill.LISTENING && q.no != null && q.no == qNo)
                        .findFirst();
                if (qRow.isPresent()) {
                    qRow.get().audioFile = filename;
                } else {
                    ParsedWorkbook.QuestionRow newQ = new ParsedWorkbook.QuestionRow();
                    newQ.skill = Skill.LISTENING;
                    newQ.no = qNo;
                    newQ.audioFile = filename;
                    newQ.typeCode = QuestionTypeCatalog.resolve(source.getTrack(), Skill.LISTENING, qNo).map(QuestionTypeDef::code).orElse(null);
                    wb.questions.add(newQ);
                }
            }

            // Sắp xếp thứ tự các card
            List<QuestionTypeCatalog.Section> sections = QuestionTypeCatalog.sections(source.getTrack());
            wb.manualCards.sort(Comparator.comparingInt((ParsedWorkbook.ManualCard c) -> {
                if (c.asks.isEmpty() || c.asks.get(0).no == null) return 999;
                int firstNo = c.asks.get(0).no;
                for (int i = 0; i < sections.size(); i++) {
                    if (sections.get(i).contains(firstNo)) {
                        return i * 1000 + firstNo;
                    }
                }
                return firstNo;
            }));
            wb.questions.sort(Comparator.comparingInt(q -> (q.no == null ? 0 : q.no)));

            job.setFilePath(MANUAL_PATH);
            storeDraft(job, wb);
            runValidation(job);

            log(actor, "BULK_UPLOAD_AUDIO", "Upload thành công " + storedCount + " file audio .mp3 từ file "
                    + entry.originalFilename() + " cho kỹ năng Nghe nguồn đề #" + sourceId);

            String redirectUrl = "/content/sources/" + sourceId + "?skill=LISTENING#paper";
            return new BulkAudioConfirmResult(storedCount, validFiles.size(),
                    "Đã tải lên và gán thành công " + validFiles.size() + " file audio vào các câu kỹ năng Nghe.",
                    redirectUrl);
        } finally {
            deleteDirectoryRecursively(tempDir);
        }
    }

    public void cancelBulkAudio(String token) {
        TempBulkAudioEntry entry = tempAudioBatches.remove(token);
        if (entry != null) {
            deleteDirectoryRecursively(entry.tempDir());
        }
    }

    @Transactional
    public MediaUploadReport uploadMedia(Long sourceId, List<MultipartFile> files, Actor actor) throws IOException {
        requireContent(actor);
        ExamSource source = draftSource(sourceId);
        int stored = 0;
        List<String> rejected = new ArrayList<>();
        for (MultipartFile f : files == null ? List.<MultipartFile>of() : files) {
            if (f == null || f.isEmpty()) continue;
            String name = MediaStorageService.baseName(f.getOriginalFilename());
            if ("zip".equals(MediaStorageService.extension(name))) {
                if (f.getSize() > MediaStorageService.MAX_ZIP_BYTES) {
                    rejected.add(name + ": file zip tối đa 300 MB");
                    continue;
                }
                stored += unzip(source, f, rejected);
            } else {
                try (InputStream in = f.getInputStream()) {
                    if (storeOne(source, name, f.getSize(), in, rejected)) stored++;
                }
            }
        }
        jobs.findFirstBySourceIdOrderByIdDesc(sourceId)
                .filter(j -> j.getStatus() != ImportJobStatus.SUPERSEDED && j.getStatus() != ImportJobStatus.ACTIVATED)
                .ifPresent(job -> {
                    ParsedWorkbook wb = workbookOf(job);
                    autoMatchAudioFromMediaAssets(source, wb);
                    autoMatchImagesFromMediaAssets(source, wb);
                    syncManualCards(source, wb, null);
                    storeDraft(job, wb);
                    jobs.save(job);
                    runValidation(job);
                });
        log(actor, "UPLOAD_EXAM_MEDIA", "Tải " + stored + " file âm thanh/ảnh cho nguồn đề #" + sourceId
                + (rejected.isEmpty() ? "" : ", từ chối " + rejected.size() + " file"));
        return new MediaUploadReport(stored, rejected);
    }

    /** Tên riêng cho mỗi lần tải: hai câu dùng ảnh cùng tên gốc không ghi đè ảnh của nhau. */
    @Transactional
    public String uploadQuestionImage(Long sourceId, MultipartFile file, Actor actor) throws IOException {
        requireContent(actor);
        ExamSource source = draftSource(sourceId);
        if (file == null || file.isEmpty()) throw new ExamDomainException("Chưa chọn ảnh.");
        if (!MediaStorageService.isImage(file.getOriginalFilename()))
            throw new ExamDomainException("Chỉ nhận ảnh PNG, JPG, JPEG, WEBP hoặc GIF.");
        String filename = "question-" + UUID.randomUUID() + "." + MediaStorageService.extension(file.getOriginalFilename());
        List<String> rejected = new ArrayList<>();
        try (InputStream in = file.getInputStream()) {
            if (!storeOne(source, filename, file.getSize(), in, rejected))
                throw new ExamDomainException(String.join("; ", rejected));
        }
        return filename;
    }

    /**
     * Upload ảnh hoặc audio cho câu hỏi trong Ngân hàng câu hỏi.
     * Khác với uploadQuestionImage, method này cho phép cả nguồn đề đã ACTIVE.
     */
    @Transactional
    public String uploadMediaForBankEdit(Long sourceId, MultipartFile file, Actor actor, boolean isImage) throws IOException {
        requireContent(actor);
        ExamSource source = getSource(sourceId); // KHÔNG dùng draftSource — cho phép ACTIVE
        if (file == null || file.isEmpty())
            throw new ExamDomainException("Chưa chọn file.");
        if (isImage) {
            if (!MediaStorageService.isImage(file.getOriginalFilename()))
                throw new ExamDomainException("Chỉ nhận ảnh PNG, JPG, JPEG, WEBP hoặc GIF.");
        } else {
            if (!MediaStorageService.isAudio(file.getOriginalFilename()))
                throw new ExamDomainException("Chỉ nhận file audio MP3.");
        }
        String ext = MediaStorageService.extension(file.getOriginalFilename());
        String prefix = isImage ? "question-" : "audio-";
        String filename = prefix + UUID.randomUUID() + "." + ext;
        List<String> rejected = new ArrayList<>();
        try (InputStream in = file.getInputStream()) {
            if (!storeOne(source, filename, file.getSize(), in, rejected))
                throw new ExamDomainException(String.join("; ", rejected));
        }
        return filename;
    }

    private int unzip(ExamSource source, MultipartFile zip, List<String> rejected) throws IOException {
        int stored = 0;
        try (ZipInputStream zin = new ZipInputStream(zip.getInputStream())) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = MediaStorageService.baseName(e.getName());
                if (name.isEmpty() || name.startsWith(".") || e.getName().startsWith("__MACOSX")) continue;
                Path tmp = Files.createTempFile("ht-zip", ".bin");
                try {
                    long size = Files.copy(zin, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    try (InputStream in = Files.newInputStream(tmp)) {
                        if (storeOne(source, name, size, in, rejected)) stored++;
                    }
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
        }
        return stored;
    }

    private boolean storeOne(ExamSource source, String name, long size, InputStream in, List<String> rejected) throws IOException {
        MediaKind kind;
        if (MediaStorageService.isAudio(name)) {
            if (size > MediaStorageService.MAX_AUDIO_BYTES) {
                rejected.add(name + ": mỗi file mp3 tối đa 15 MB");
                return false;
            }
            kind = MediaKind.AUDIO;
        } else if (MediaStorageService.isImage(name)) {
            if (size > MediaStorageService.MAX_IMAGE_BYTES) {
                rejected.add(name + ": mỗi file ảnh tối đa 5 MB");
                return false;
            }
            kind = MediaKind.IMAGE;
        } else {
            rejected.add(name + ": chỉ nhận .mp3, ảnh (.png/.jpg/.webp) hoặc .zip");
            return false;
        }
        Path p = storage.storeMedia(source.getId(), name, in);
        MediaAsset asset = media.findBySourceIdAndFilename(source.getId(), name).orElseGet(MediaAsset::new);
        asset.setSource(source);
        asset.setFilename(name);
        asset.setKind(kind);
        asset.setSizeBytes(size);
        asset.setStoragePath(storage.toStored(p));
        media.save(asset);
        return true;
    }

    @Transactional
    public void deleteMedia(Long sourceId, Long mediaId, Actor actor) {
        requireContent(actor);
        ExamSource source = draftSource(sourceId);
        MediaAsset a = media.findById(mediaId).filter(m -> m.getSource().getId().equals(sourceId))
                .orElseThrow(() -> new ExamDomainException("Không tìm thấy file."));
        String filename = a.getFilename();
        media.delete(a);
        storage.deleteStoredSafe(a.getStoragePath());
        if (filename != null) {
            try {
                Files.deleteIfExists(storage.resolveMedia(sourceId, filename));
            } catch (Exception ignored) {}
            unlinkMediaFromCards(sourceId, filename);
        }
        jobs.findFirstBySourceIdOrderByIdDesc(sourceId)
                .filter(j -> j.getStatus() != ImportJobStatus.SUPERSEDED && j.getStatus() != ImportJobStatus.ACTIVATED)
                .ifPresent(job -> {
                    ParsedWorkbook wb = workbookOf(job);
                    autoMatchAudioFromMediaAssets(source, wb);
                    autoMatchImagesFromMediaAssets(source, wb);
                    syncManualCards(source, wb, null);
                    storeDraft(job, wb);
                    jobs.save(job);
                    runValidation(job);
                });
        log(actor, "DELETE_MEDIA", "Xoá file " + filename + " của nguồn đề #" + sourceId);
    }

    @Transactional
    public int deleteMediaBatch(Long sourceId, List<Long> mediaIds, Actor actor) {
        requireContent(actor);
        ExamSource source = draftSource(sourceId);
        if (mediaIds == null || mediaIds.isEmpty()) return 0;
        List<MediaAsset> assets = media.findAllById(mediaIds).stream()
                .filter(m -> m.getSource().getId().equals(sourceId))
                .toList();
        for (MediaAsset a : assets) {
            String filename = a.getFilename();
            media.delete(a);
            storage.deleteStoredSafe(a.getStoragePath());
            if (filename != null) {
                try {
                    Files.deleteIfExists(storage.resolveMedia(sourceId, filename));
                } catch (Exception ignored) {}
                unlinkMediaFromCards(sourceId, filename);
            }
        }
        jobs.findFirstBySourceIdOrderByIdDesc(sourceId)
                .filter(j -> j.getStatus() != ImportJobStatus.SUPERSEDED && j.getStatus() != ImportJobStatus.ACTIVATED)
                .ifPresent(job -> {
                    ParsedWorkbook wb = workbookOf(job);
                    autoMatchAudioFromMediaAssets(source, wb);
                    autoMatchImagesFromMediaAssets(source, wb);
                    syncManualCards(source, wb, null);
                    storeDraft(job, wb);
                    jobs.save(job);
                    runValidation(job);
                });
        log(actor, "DELETE_MEDIA_BATCH", "Xoá " + assets.size() + " file đính kèm nguồn đề #" + sourceId);
        return assets.size();
    }

    @Transactional
    public int deleteAllMedia(Long sourceId, Actor actor) {
        requireContent(actor);
        ExamSource source = draftSource(sourceId);
        List<MediaAsset> all = media.findBySourceIdOrderByFilenameAsc(sourceId);
        int count = all.size();
        for (MediaAsset a : all) {
            unlinkMediaFromCards(sourceId, a.getFilename());
            media.delete(a);
            storage.deleteStoredSafe(a.getStoragePath());
        }
        storage.deleteMediaFolder(sourceId);
        jobs.findFirstBySourceIdOrderByIdDesc(sourceId)
                .filter(j -> j.getStatus() != ImportJobStatus.SUPERSEDED && j.getStatus() != ImportJobStatus.ACTIVATED)
                .ifPresent(job -> {
                    ParsedWorkbook wb = workbookOf(job);
                    autoMatchAudioFromMediaAssets(source, wb);
                    autoMatchImagesFromMediaAssets(source, wb);
                    syncManualCards(source, wb, null);
                    storeDraft(job, wb);
                    jobs.save(job);
                    runValidation(job);
                });
        log(actor, "DELETE_ALL_MEDIA", "Xoá toàn bộ " + count + " file đính kèm nguồn đề #" + sourceId);
        return count;
    }

    @Transactional
    public void deleteMediaByName(Long sourceId, String filename) {
        if (filename == null || filename.isBlank()) return;
        Optional<MediaAsset> assetOpt = findMedia(sourceId, filename);
        if (assetOpt.isPresent()) {
            MediaAsset a = assetOpt.get();
            if (a.getId() != null) {
                media.delete(a);
            }
            storage.deleteStoredSafe(a.getStoragePath());
        }
        try {
            Files.deleteIfExists(storage.resolveMedia(sourceId, filename));
        } catch (Exception ignored) {}
    }

    public void unlinkMediaFromCards(Long sourceId, String filename) {
        if (filename == null || filename.isBlank()) return;
        latestJob(sourceId).ifPresent(job -> {
            try {
                ParsedWorkbook wb = workbookOf(job);
                boolean changed = false;
                for (ParsedWorkbook.ManualCard card : wb.manualCards) {
                    if (filename.equalsIgnoreCase(card.audioFile)) {
                        card.audioFile = null;
                        changed = true;
                    }
                    if (filename.equalsIgnoreCase(card.imageFile)) {
                        card.imageFile = null;
                        changed = true;
                    }
                    for (ParsedWorkbook.ManualAsk ask : card.asks) {
                        if (filename.equalsIgnoreCase(ask.chart)) {
                            ask.chart = null;
                            changed = true;
                        }
                    }
                }
                for (ParsedWorkbook.QuestionRow q : wb.questions) {
                    if (filename.equalsIgnoreCase(q.audioFile)) {
                        q.audioFile = null;
                        changed = true;
                    }
                    if (filename.equalsIgnoreCase(q.imageFile)) {
                        q.imageFile = null;
                        changed = true;
                    }
                }
                for (ParsedWorkbook.WritingRow w : wb.writings) {
                    if (filename.equalsIgnoreCase(w.chartJson)) {
                        w.chartJson = null;
                        changed = true;
                    }
                }
                if (changed) {
                    storeDraft(job, wb);
                    jobs.save(job);
                }
            } catch (Exception ignored) {}
        });
    }

    @Transactional
    public boolean clearCardAudio(Long sourceId, String cardId, String filename, Actor actor) {
        requireContent(actor);
        draftSource(sourceId);
        ImportJob job = manualJob(sourceId);
        ParsedWorkbook wb = workbookOf(job);
        boolean changed = false;
        if (cardId != null && !cardId.isBlank()) {
            for (ParsedWorkbook.ManualCard card : wb.manualCards) {
                if (cardId.equals(card.id)) {
                    card.audioFile = null;
                    changed = true;
                }
            }
        }
        if (filename != null && !filename.isBlank()) {
            for (ParsedWorkbook.QuestionRow q : wb.questions) {
                if (filename.equalsIgnoreCase(q.audioFile)) {
                    q.audioFile = null;
                    changed = true;
                }
            }
        }
        boolean deletedMedia = false;
        if (filename != null && !filename.isBlank()) {
            boolean stillUsed = wb.manualCards.stream()
                    .anyMatch(c -> filename.equalsIgnoreCase(c.audioFile));
            if (!stillUsed) {
                deleteMediaByName(sourceId, filename);
                deletedMedia = true;
            }
        }
        if (changed) {
            storeDraft(job, wb);
            jobs.save(job);
            runValidation(job);
        }
        return deletedMedia;
    }

    // ------------------------------------------------------------------ Bước 3: kiểm tra lỗi

    @Transactional
    public ImportJob revalidate(Long sourceId, Actor actor) {
        requireContent(actor);
        draftSource(sourceId);
        ImportJob job = latestJob(sourceId).orElseThrow(() -> new ExamDomainException("Chưa tải file Excel."));
        runValidation(job);
        return job;
    }

    private ParsedWorkbook parseJobFile(ImportJob job) {
        if (MANUAL_PATH.equals(job.getFilePath())) return new ParsedWorkbook();
        try (InputStream in = Files.newInputStream(storage.resolveStored(job.getFilePath()))) {
            return ExamWorkbookParser.parse(in, job.getSource().getTrack());
        } catch (IOException e) {
            ParsedWorkbook wb = new ParsedWorkbook();
            wb.issues.add(ImportIssue.error("FILE", null, null, null, "Không mở được file đã tải, hãy tải lại."));
            return wb;
        }
    }

    /** Bản đang sửa trên web. Lần đầu lấy từ Excel (nếu có), các lần sau giữ phần đã sửa. */
    private ParsedWorkbook workbookOf(ImportJob job) {
        if (job.getDraftJson() != null && !job.getDraftJson().isBlank()) {
            try {
                return DRAFT_JSON.readValue(job.getDraftJson(), ParsedWorkbook.class);
            } catch (Exception e) {
                throw new ExamDomainException("Không đọc được bản đề đã lưu. Tải lại file Excel để lấy lại nội dung, chưa ghi đè.");
            }
        }
        ParsedWorkbook wb = parseJobFile(job);
        List<ImportIssue> kept = new ArrayList<>(wb.issues);
        storeDraft(job, wb);
        wb.issues.addAll(kept);
        return wb;
    }

    private void storeDraft(ImportJob job, ParsedWorkbook wb) {
        wb.issues.clear();
        try {
            job.setDraftJson(DRAFT_JSON.writeValueAsString(wb));
        } catch (Exception e) {
            throw new ExamDomainException("Không lưu được bản nháp.");
        }
    }

    private Map<String, Long> mediaSizes(Long sourceId) {
        return media.findBySourceIdOrderByFilenameAsc(sourceId).stream()
                .collect(Collectors.toMap(m -> ExamImportValidator.key(m.getFilename()), MediaAsset::getSizeBytes, (a, b) -> a));
    }

    private Map<String, String> existingHashLookup(Long sourceId, Set<String> hashes) {
        return questions.findByContentHashIn(hashes).stream()
                .filter(q -> q.getSource() == null || !q.getSource().getId().equals(sourceId))
                .collect(Collectors.toMap(Question::getContentHash, Question::getCode, (a, b) -> a));
    }

    private ExamImportValidator.Result validate(ExamSource source, ParsedWorkbook wb) {
        ExamImportValidator.Context ctx = new ExamImportValidator.Context(source.getTrack(), source.isFullExam(),
                source.isAssessment(), mediaSizes(source.getId()), hashes -> existingHashLookup(source.getId(), hashes));
        return ExamImportValidator.validate(wb, ctx);
    }

    public boolean isManual(ImportJob job) {
        if (job == null) return false;
        if (MANUAL_PATH.equals(job.getFilePath())) return true;
        try {
            ParsedWorkbook wb = workbookOf(job);
            return wb != null && !wb.manualCards.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    void runValidation(ImportJob job) {
        ParsedWorkbook wb = workbookOf(job);
        if (isManual(job)) {
            applyManualResult(job, wb, validateManual(job.getSource(), wb));
            return;
        }
        ExamImportValidator.Result r = validate(job.getSource(), wb);
        job.setMcqRows(wb.questions.size());
        job.setWritingRows(wb.writings.size());
        job.setErrorCount((int) r.errorCount());
        job.setWarningCount((int) r.warningCount());
        try {
            job.setIssuesJson(JSON.writeValueAsString(r.issues()));
        } catch (Exception e) {
            job.setIssuesJson("[]");
        }
        job.setStatus(r.errorCount() > 0 ? ImportJobStatus.HAS_ERRORS : ImportJobStatus.VALID);
        job.setValidatedAt(LocalDateTime.now());
        jobs.save(job);
    }

    public List<ImportIssue> issues(ImportJob job) {
        if (job == null || job.getIssuesJson() == null) return List.of();
        try {
            return JSON.readValue(job.getIssuesJson(), new TypeReference<List<ImportIssue>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Validate manual drafts for display without persisting or changing their lifecycle. */
    public List<ImportIssue> currentIssues(ImportJob job) {
        if (job != null && isManual(job)) {
            return validateManual(getSource(job.getSource().getId()), workbookOf(job));
        }
        return issues(job);
    }

    public record PaperForm(boolean writing, Skill skill, int no, String prompt,
                            String opt1, String opt2, String opt3, String opt4, String answer,
                            String explanation, String audio, String script, String passage, String image,
                            String keys1, String keys2, String chart, String modelAnswer, String rubric) {
    }

    public record PaperSlot(String key, Skill skill, int no, String typeCode, String typeName, boolean writing,
                            boolean filled, String prompt, String opt1, String opt2, String opt3, String opt4,
                            String answer, String explanation, String audio, String script, String passage, String image,
                            String keys1, String keys2, String chart, String modelAnswer, String rubric,
                            List<String> problems) {
    }

    /** Tạo bản đề trống để nhập từng câu trên web, không cần Excel. */
    @Transactional
    public ImportJob startManual(Long sourceId, Actor actor) {
        requireContent(actor);
        ExamSource source = draftSource(sourceId);
        Optional<ImportJob> current = latestJob(sourceId);
        if (current.isPresent() && current.get().getStatus() != ImportJobStatus.SUPERSEDED
                && current.get().getStatus() != ImportJobStatus.ACTIVATED) {
            return current.get();
        }
        ImportJob job = new ImportJob();
        job.setSource(source);
        job.setFileName("Nhập trên web");
        job.setFilePath(MANUAL_PATH);
        job.setCreatedBy(actor.name());
        ParsedWorkbook blank = new ParsedWorkbook();
        blank.hasWritingSheet = source.getTrack() == Track.TOPIK_II;
        storeDraft(job, blank);
        jobs.save(job);
        runValidation(job);
        log(actor, "MANUAL_EXAM_DRAFT", "Tạo bản nhập tay cho nguồn đề #" + sourceId);
        return job;
    }

    public record CardForm(String cardId, String templateCode, String audio, String image, String passage, String script,
                           String scriptVi, List<String> prompt, List<String> opt1, List<String> opt2, List<String> opt3,
                           List<String> opt4, List<String> answer, List<String> explanation, List<String> keys1,
                           List<String> keys2, List<String> modelAnswer, List<String> rubric, List<String> chart,
                           List<String> opt1Img, List<String> opt2Img, List<String> opt3Img, List<String> opt4Img,
                           String typeCode, List<Integer> qno) {
        public CardForm(String cardId, String templateCode, String audio, String image, String passage, String script,
                        String scriptVi, List<String> prompt, List<String> opt1, List<String> opt2, List<String> opt3,
                        List<String> opt4, List<String> answer, List<String> explanation, List<String> keys1,
                        List<String> keys2, List<String> modelAnswer, List<String> rubric, List<String> chart,
                        String typeCode, List<Integer> qno) {
            this(cardId, templateCode, audio, image, passage, script, scriptVi, prompt, opt1, opt2, opt3, opt4,
                    answer, explanation, keys1, keys2, modelAnswer, rubric, chart, null, null, null, null, typeCode, qno);
        }
    }

    public record AskView(String prompt, String opt1, String opt2, String opt3, String opt4, String answer,
                          String explanation, String keys1, String keys2, String modelAnswer, String rubric, String chart,
                          String opt1Img, String opt2Img, String opt3Img, String opt4Img,
                          Integer no) {
        public static AskView blank() {
            return new AskView("", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", null);
        }

        public AskView withNo(Integer no) {
            return new AskView(prompt, opt1, opt2, opt3, opt4, answer, explanation, keys1, keys2, modelAnswer, rubric, chart, opt1Img, opt2Img, opt3Img, opt4Img, no);
        }
    }

    public record CardView(String id, String templateCode, String templateName, String skillLabel, int askCount,
                           boolean ready, String audio, String image, String passage, String script, String scriptVi,
                           List<AskView> asks, List<String> problems, String typeCode, String skillName,
                           List<Integer> nos) {
    }

    /**
     * Lưu một bài theo khuôn. Thay đúng bài đang sửa, không đụng các bài khác.
     * Ô trống được ghi đè, vì đây là form của cả bài chứ không phải một ô Excel.
     */
    @Transactional
    public String saveCard(Long sourceId, CardForm form, Actor actor) {
        requireContent(actor);
        ExamSource source = draftSource(sourceId);
        ImportJob job = manualJob(sourceId);
        String typeCode = emptyToNull(form.typeCode());
        String templateCode = emptyToNull(form.templateCode());
        if (templateCode == null && typeCode != null) templateCode = QuestionTemplateCatalog.templateCode(typeCode);
        if (templateCode == null) throw new ExamDomainException("Chọn dạng câu trước khi lưu.");
        final String moldCode = templateCode;
        QuestionTemplate template = templateRepo.findByCode(moldCode)
                .orElseThrow(() -> new ExamDomainException("Không có dạng câu " + moldCode + "."));
        if (template.getTrack() != source.getTrack()) {
            throw new ExamDomainException("Khuôn " + templateCode + " không thuộc " + source.getTrack().getLabel() + ".");
        }
        if (template.getStatus() != TemplateStatus.ACTIVE) {
            throw new ExamDomainException("Khuôn " + templateCode + " đang không dùng.");
        }
        int count = askCount(form);
        QuestionTypeDef chosen = typeCode == null ? null : QuestionTypeCatalog.byCode(typeCode).orElse(null);
        List<Integer> numbers = form.qno() == null ? List.of() : form.qno().stream().filter(Objects::nonNull).toList();
        if (chosen != null) {
            if (chosen.track() != source.getTrack() || chosen.skill() != template.getSkill()) {
                throw new ExamDomainException("Dạng " + chosen.code() + " không thuộc phần đang nhập.");
            }
            if (numbers.size() != count) throw new ExamDomainException("Mỗi câu cần một ô số trên lưới.");
            List<Integer> block = chosen.blockOf(numbers.get(0));
            if (block.isEmpty() || !new HashSet<>(block).equals(new HashSet<>(numbers))) {
                throw new ExamDomainException(chosen.isGrouped()
                        ? "Câu dùng chung phải chọn đủ các ô " + block + "."
                        : "Ô " + numbers.get(0) + " không thuộc dạng " + chosen.code() + ".");
            }
        } else if (count < template.getChildMin() || count > template.getChildMax()) {
            throw new ExamDomainException(template.getChildMax() == 1
                    ? "Dạng này là một câu. Hãy chọn một ô trên lưới."
                    : "Dạng " + template.getCode() + " nhập " + template.childLabel() + ".");
        } else if (!numbers.isEmpty() && (numbers.size() != count || new HashSet<>(numbers).size() != numbers.size())) {
            throw new ExamDomainException("Mỗi câu cần một ô số riêng.");
        } else if (!numbers.isEmpty()) {
            QuestionTypeCatalog.section(source.getTrack(), template.getSkill()).ifPresent(section -> {
                if (numbers.stream().anyMatch(n -> !section.contains(n))) {
                    throw new ExamDomainException("Ô số không thuộc phần " + template.getSkill().getLabel() + ".");
                }
            });
        }
        ParsedWorkbook wb = workbookOf(job);
        ParsedWorkbook.ManualCard card;
        String cardId = emptyToNull(form.cardId());
        if (cardId != null) {
            card = wb.manualCards.stream().filter(c -> cardId.equals(c.id)).findFirst()
                    .orElseThrow(() -> new ExamDomainException("Không còn bài này trên tờ đề."));
        } else {
            card = new ParsedWorkbook.ManualCard();
            card.id = nextCardId(wb);
            wb.manualCards.add(card);
        }
        if (!numbers.isEmpty()) {
            Set<Integer> incoming = new HashSet<>(numbers);
            for (ParsedWorkbook.ManualCard other : wb.manualCards) {
                if (other.id != null && other.id.equals(card.id)) continue;
                QuestionTemplate otherTemplate = blank(other.templateCode) ? null : templateRepo.findByCode(other.templateCode).orElse(null);
                if (otherTemplate == null || otherTemplate.getSkill() != template.getSkill()) continue;
                for (ParsedWorkbook.ManualAsk otherAsk : other.asks) {
                    if (otherAsk.no != null && incoming.contains(otherAsk.no)) {
                        throw new ExamDomainException("Câu " + otherAsk.no + " đã có nội dung. Bấm ô đó để sửa.");
                    }
                }
            }
        }
        String oldAudio = card.audioFile;
        card.templateCode = template.getCode();
        card.typeCode = chosen == null ? null : chosen.code();
        card.audioFile = emptyToNull(form.audio());
        if (oldAudio != null && !oldAudio.equalsIgnoreCase(card.audioFile)) {
            boolean stillUsed = wb.manualCards.stream()
                    .anyMatch(c -> c != card && oldAudio.equalsIgnoreCase(c.audioFile));
            if (!stillUsed) {
                deleteMediaByName(sourceId, oldAudio);
                for (ParsedWorkbook.QuestionRow q : wb.questions) {
                    if (oldAudio.equalsIgnoreCase(q.audioFile)) {
                        q.audioFile = null;
                    }
                }
            }
        }
        card.imageFile = emptyToNull(form.image());
        card.passageKo = emptyToNull(form.passage());
        card.scriptKo = emptyToNull(form.script());
        card.scriptVi = emptyToNull(form.scriptVi());
        card.asks.clear();
        for (int i = 0; i < count; i++) {
            ParsedWorkbook.ManualAsk ask = new ParsedWorkbook.ManualAsk();
            ask.promptKo = emptyToNull(at(form.prompt(), i));
            ask.options = new String[]{
                    nz(emptyToNull(at(form.opt1(), i))), nz(emptyToNull(at(form.opt2(), i))),
                    nz(emptyToNull(at(form.opt3(), i))), nz(emptyToNull(at(form.opt4(), i)))
            };
            String answer = emptyToNull(at(form.answer(), i));
            ask.answerRaw = answer;
            ask.answer = parseChoice(answer);
            ask.explanationVi = emptyToNull(at(form.explanation(), i));
            ask.keys1 = emptyToNull(at(form.keys1(), i));
            ask.keys2 = emptyToNull(at(form.keys2(), i));
            ask.modelAnswer = emptyToNull(at(form.modelAnswer(), i));
            ask.rubricNote = emptyToNull(at(form.rubric(), i));
            ask.chart = emptyToNull(at(form.chart(), i));
            if (ask.optImgs == null) ask.optImgs = new String[4];
            ask.optImgs[0] = emptyToNull(at(form.opt1Img(), i));
            ask.optImgs[1] = emptyToNull(at(form.opt2Img(), i));
            ask.optImgs[2] = emptyToNull(at(form.opt3Img(), i));
            ask.optImgs[3] = emptyToNull(at(form.opt4Img(), i));
            ask.no = form.qno() == null || i >= form.qno().size() ? null : form.qno().get(i);
            card.asks.add(ask);
        }
        if (card.asks.size() == 1) {
            String c = card.asks.get(0).chart != null ? card.asks.get(0).chart.trim() : null;
            if (c != null && !c.startsWith("[")) {
                card.imageFile = c;
            } else {
                card.imageFile = null;
            }
        }
        // Đồng bộ ngược lại vào wb.questions và wb.writings để các luồng khác (validate, activate, re-import) có đầy đủ ảnh và audio
        for (ParsedWorkbook.ManualAsk ask : card.asks) {
            if (ask.no != null) {
                String img = !blank(ask.chart) ? ask.chart : card.imageFile;
                for (ParsedWorkbook.QuestionRow q : wb.questions) {
                    if (Objects.equals(q.no, ask.no)) {
                        if (!blank(img) && !img.trim().startsWith("[")) {
                            q.imageFile = img.trim();
                        } else {
                            q.imageFile = null;
                        }
                        if (card.audioFile != null) {
                            q.audioFile = card.audioFile;
                        }
                    }
                }
                for (ParsedWorkbook.WritingRow w : wb.writings) {
                    if (Objects.equals(w.no, ask.no)) {
                        if (!blank(img)) {
                            w.chartJson = img.trim();
                        } else {
                            w.chartJson = null;
                        }
                    }
                }
            }
        }
        storeDraft(job, wb);
        jobs.save(job);
        runValidation(job);
        log(actor, "EDIT_EXAM_DRAFT", "Lưu bài " + card.id + " khuôn " + template.getCode() + " của nguồn đề #" + sourceId);
        return card.id;
    }

    @Transactional
    public void deleteCard(Long sourceId, String cardId, Actor actor) {
        requireContent(actor);
        draftSource(sourceId);
        ImportJob job = manualJob(sourceId);
        ParsedWorkbook wb = workbookOf(job);
        boolean removed = wb.manualCards.removeIf(c -> cardId != null && cardId.equals(c.id));
        if (!removed) throw new ExamDomainException("Không còn bài này trên tờ đề.");
        storeDraft(job, wb);
        jobs.save(job);
        runValidation(job);
        log(actor, "EDIT_EXAM_DRAFT", "Xóa bài " + cardId + " của nguồn đề #" + sourceId);
    }

    public List<CardView> cards(Long sourceId) {
        Optional<ImportJob> job = latestJob(sourceId);
        if (job.isEmpty() || !isManual(job.get())) return List.of();
        ParsedWorkbook wb = workbookOf(job.get());
        List<ImportIssue> found = currentIssues(job.get());
        List<CardView> out = new ArrayList<>();
        int index = 0;
        for (ParsedWorkbook.ManualCard card : wb.manualCards) {
            index++;
            int at = index;
            QuestionTemplate template = blank(card.templateCode) ? null : templateRepo.findByCode(card.templateCode).orElse(null);
            List<String> problems = found.stream()
                    .filter(i -> i.isError() && "CARD".equals(i.sheet()) && Objects.equals(i.no(), at))
                    .map(ImportIssue::message)
                    .toList();
            List<AskView> asks = new ArrayList<>();
            for (ParsedWorkbook.ManualAsk ask : card.asks) asks.add(toView(ask));
            List<Integer> nos = card.asks.stream().map(a -> a.no).filter(Objects::nonNull).toList();
            out.add(new CardView(card.id, nz(card.templateCode),
                    template == null ? "Chưa chọn dạng" : template.getName(),
                    template == null ? "" : template.getSkill().getLabel(),
                    card.asks.size(), problems.isEmpty() && !card.asks.isEmpty(),
                    nz(card.audioFile), nz(card.imageFile), nz(card.passageKo), nz(card.scriptKo), nz(card.scriptVi),
                    asks, problems, nz(card.typeCode),
                    template == null ? "" : template.getSkill().name(), nos));
        }
        return out;
    }

    private ImportJob manualJob(Long sourceId) {
        ImportJob job = latestJob(sourceId).orElseThrow(() -> new ExamDomainException("Chưa có tờ đề. Hãy mở trang nhập."));
        if (!isManual(job)) {
            throw new ExamDomainException("Bộ đề này đang theo file Excel. Tờ nhập tay không ghi đè file đó.");
        }
        return job;
    }

    private List<ImportIssue> validateManual(ExamSource source, ParsedWorkbook wb) {
        List<ImportIssue> issues = new ArrayList<>();
        if (wb.manualCards.isEmpty()) {
            issues.add(ImportIssue.error("CARD", null, null, null, "Chưa có câu nào. Chọn dạng câu, rồi bấm số câu trên lưới."));
            return issues;
        }
        Map<String, Long> sizes = media.findBySourceIdOrderByFilenameAsc(source.getId()).stream()
                .collect(Collectors.toMap(m -> ExamImportValidator.key(m.getFilename()), MediaAsset::getSizeBytes, (a, b) -> a));
        int index = 0;
        for (ParsedWorkbook.ManualCard card : wb.manualCards) {
            index++;
            QuestionTemplate template = blank(card.templateCode) ? null : templateRepo.findByCode(card.templateCode).orElse(null);
            String skill = template == null ? null : template.getSkill().name();
            if (template == null || template.getTrack() != source.getTrack()) {
                issues.add(ImportIssue.error("CARD", index, skill, index,
                        "Bài " + index + " chưa chọn khuôn của " + source.getTrack().getLabel() + "."));
                continue;
            }
            if (template.getStatus() != TemplateStatus.ACTIVE) {
                issues.add(ImportIssue.error("CARD", index, skill, index, "Khuôn " + template.getCode() + " đang không dùng."));
            }
            int cap = template.getChildMax() > 1 ? 8 : 1;
            if (card.asks.isEmpty()) {
                issues.add(ImportIssue.error("CARD", index, skill, index, "Bài " + index + " chưa có câu hỏi."));
            } else if (card.asks.size() > cap) {
                issues.add(ImportIssue.error("CARD", index, skill, index, "Bài " + index + " tối đa " + cap + " câu."));
            } else if (template.getChildMax() > 1 && card.asks.size() < template.getChildMin()) {
                issues.add(ImportIssue.warning("CARD", index, skill, index,
                        "Khuôn gợi ý từ " + template.getChildMin() + " câu. Đang có " + card.asks.size()
                                + ". Số câu không cố định, vẫn lưu được."));
            }
            if (template.isAudioRequired() && blank(card.audioFile)) {
                issues.add(ImportIssue.error("CARD", index, skill, index, "Bài " + index + " thiếu file nghe dùng chung."));
            } else if (!blank(card.audioFile) && !sizes.containsKey(ExamImportValidator.key(card.audioFile))) {
                issues.add(ImportIssue.error("CARD", index, skill, index, "Chưa tải file nghe " + card.audioFile + "."));
            }
            if (template.getImageScope() == ResourceScope.GROUP && !blank(card.imageFile)
                    && !sizes.containsKey(ExamImportValidator.key(card.imageFile))) {
                issues.add(ImportIssue.error("CARD", index, skill, index, "Chưa tải ảnh " + card.imageFile + "."));
            }
            if (template.getSkill() != Skill.WRITING && template.isPassageRequired() && blank(card.passageKo)) {

                issues.add(ImportIssue.error("CARD", index, skill, index, "Bài " + index + " thiếu đoạn văn dùng chung."));
            }
            if (template.getSkill() == Skill.LISTENING) {
                if (blank(card.scriptKo)) {
                    issues.add(ImportIssue.error("CARD", index, skill, index, "Bài " + index + " thiếu lời thoại tiếng Hàn."));
                }
                if (blank(card.scriptVi)) {
                    issues.add(ImportIssue.warning("CARD", index, skill, index, "Bài " + index + " chưa có bản dịch lời thoại."));
                }
            }
            int askNo = 0;
            for (ParsedWorkbook.ManualAsk ask : card.asks) {
                askNo++;
                String where = "Bài " + index + ", câu " + askNo;
                boolean writing = template.getSkill() == Skill.WRITING;
                int writingNo = ask.no != null ? ask.no : officialWritingNo(template);
                boolean choice = template.getOptionCount() != null;
                if (!writing && !choice && template.isPromptRequired() && blank(ask.promptKo)) {
                    issues.add(ImportIssue.error("CARD", index, skill, index, where + " thiếu câu hỏi.").forQuestion(ask.no));
                }
                if (!writing && choice) {
                    String[] options = ask.options == null ? new String[4] : ask.options;
                    boolean pictures = template.getImageScope() == ResourceScope.OPTIONS;
                    for (int i = 0; i < 4; i++) {
                        String option = i < options.length ? options[i] : null;
                        if (blank(option) && ask.optImgs != null && !blank(at(ask.optImgs, i))) {
                            option = at(ask.optImgs, i);
                        }
                        if (blank(option)) {
                            issues.add(ImportIssue.error("CARD", index, skill, index, where + " thiếu phương án " + (i + 1) + ".").forQuestion(ask.no));
                        } else if (pictures && !MediaStorageService.isImage(option)) {
                            issues.add(ImportIssue.error("CARD", index, skill, index,
                                    where + " phương án " + (i + 1) + " phải là file ảnh đã tải.").forQuestion(ask.no));
                        } else if (pictures && !sizes.containsKey(ExamImportValidator.key(option))) {
                            issues.add(ImportIssue.error("CARD", index, skill, index,
                                    where + " chưa tải ảnh phương án " + option + ".").forQuestion(ask.no));
                        }
                    }
                    if (ask.answer == null) {
                        issues.add(ImportIssue.error("CARD", index, skill, index, where + " chưa chọn đáp án đúng.").forQuestion(ask.no));
                    }
                    if (blank(ask.explanationVi)) {
                        issues.add(ImportIssue.error("CARD", index, skill, index, where + " thiếu giải thích.").forQuestion(ask.no));
                    }
                }
                // Form Viết lấy cấu trúc theo số câu, kể cả khi khuôn cũ còn cấu hình ACCEPT_KEYS cho 53/54.
                boolean needsKeys = writing ? writingNo == 51 || writingNo == 52
                        : template.getAnswerType() == AnswerType.ACCEPT_KEYS;
                if (needsKeys && (blank(ask.keys1) || blank(ask.keys2))) {
                    issues.add(ImportIssue.error("CARD", index, skill, index, where + " thiếu đáp án chấp nhận cho cả hai chỗ trống.").forQuestion(ask.no));
                }
                if (template.getSkill() == Skill.WRITING && !blank(ask.chart)
                        && !ask.chart.trim().startsWith("[") && !sizes.containsKey(ExamImportValidator.key(ask.chart))) {
                    issues.add(ImportIssue.error("CARD", index, skill, index, where + " chưa tải ảnh " + ask.chart + ".").forQuestion(ask.no));
                }
            }
        }
        return issues;
    }

    private void applyManualResult(ImportJob job, ParsedWorkbook wb, List<ImportIssue> issues) {
        int mcq = 0;
        int writing = 0;
        for (ParsedWorkbook.ManualCard card : wb.manualCards) {
            QuestionTemplate template = blank(card.templateCode) ? null : templateRepo.findByCode(card.templateCode).orElse(null);
            if (template != null && template.getSkill() == Skill.WRITING) writing += card.asks.size();
            else mcq += card.asks.size();
        }
        job.setMcqRows(mcq);
        job.setWritingRows(writing);
        long errors = issues.stream().filter(ImportIssue::isError).count();
        job.setErrorCount((int) errors);
        job.setWarningCount((int) (issues.size() - errors));
        try {
            job.setIssuesJson(JSON.writeValueAsString(issues));
        } catch (Exception e) {
            job.setIssuesJson("[]");
        }
        job.setStatus(errors > 0 ? ImportJobStatus.HAS_ERRORS : ImportJobStatus.VALID);
        job.setValidatedAt(LocalDateTime.now());
        jobs.save(job);
    }

    private static AskView toView(ParsedWorkbook.ManualAsk ask) {
        String[] options = ask.options == null ? new String[4] : ask.options;
        String[] imgs = ask.optImgs == null ? new String[4] : ask.optImgs;
        return new AskView(nz(ask.promptKo), nz(at(options, 0)), nz(at(options, 1)), nz(at(options, 2)), nz(at(options, 3)),
                ask.answer == null ? nz(ask.answerRaw) : String.valueOf(ask.answer),
                nz(ask.explanationVi), nz(ask.keys1), nz(ask.keys2), nz(ask.modelAnswer), nz(ask.rubricNote), nz(ask.chart),
                nz(at(imgs, 0)), nz(at(imgs, 1)), nz(at(imgs, 2)), nz(at(imgs, 3)),
                ask.no);
    }

    private static int askCount(CardForm form) {
        if (form.qno() != null && !form.qno().isEmpty()) {
            return (int) form.qno().stream().filter(java.util.Objects::nonNull).count();
        }
        int n = 0;
        n = Math.max(n, len(form.prompt()));
        n = Math.max(n, len(form.opt1()));
        n = Math.max(n, len(form.answer()));
        n = Math.max(n, len(form.keys1()));
        n = Math.max(n, len(form.modelAnswer()));
        return Math.max(n, 1);
    }

    private static int len(List<String> values) {
        return values == null ? 0 : values.size();
    }

    private static String at(List<String> values, int index) {
        if (values == null || index < 0 || index >= values.size()) return null;
        return values.get(index);
    }

    private static String at(String[] values, int index) {
        if (values == null || index < 0 || index >= values.length) return null;
        return values[index];
    }

    private static String nextCardId(ParsedWorkbook wb) {
        Set<String> ids = wb.manualCards.stream().map(c -> c.id).collect(Collectors.toSet());
        int n = wb.manualCards.size() + 1;
        while (ids.contains("b" + n)) n++;
        return "b" + n;
    }

    /** Sửa một câu bất kỳ trên bản nháp. Có lỗi vẫn lưu được. */
    @Transactional
    public void savePaper(Long sourceId, PaperForm form, Actor actor) {
        requireContent(actor);
        ExamSource source = draftSource(sourceId);
        ImportJob job = latestJob(sourceId).orElseThrow(() -> new ExamDomainException("Chưa có bản đề. Hãy bấm Nhập trên web hoặc tải Excel."));
        ParsedWorkbook wb = workbookOf(job);
        if (form.writing()) {
            upsertWriting(wb, form);
        } else {
            if (form.skill() == null || form.skill() == Skill.WRITING) {
                throw new ExamDomainException("Chọn phần Nghe hoặc Đọc.");
            }
            upsertQuestion(source.getTrack(), wb, form);
        }
        storeDraft(job, wb);
        jobs.save(job);
        runValidation(job);
        log(actor, "EDIT_EXAM_DRAFT", "Sửa " + (form.writing() ? "Viết " : form.skill().getLabel() + " ") + form.no()
                + " của nguồn đề #" + sourceId);
    }

    public List<PaperSlot> paper(Long sourceId) {
        ExamSource source = getSource(sourceId);
        Optional<ImportJob> job = latestJob(sourceId);
        if (job.isEmpty()) return List.of();
        ParsedWorkbook wb = workbookOf(job.get());
        Map<String, List<String>> problems = new HashMap<>();
        for (ImportIssue issue : issues(job.get())) {
            if (issue.no() == null || issue.skill() == null) continue;
            if (issue.message() != null && issue.message().startsWith("Không đủ số câu")) continue;
            problems.computeIfAbsent(issue.skill() + "-" + issue.no(), k -> new ArrayList<>()).add(issue.message());
        }
        List<PaperSlot> slots = new ArrayList<>();
        for (QuestionTypeCatalog.Section section : QuestionTypeCatalog.sections(source.getTrack())) {
            if (section.skill() == Skill.WRITING) {
                for (int no = 51; no <= 54; no++) slots.add(writingSlot(wb, no, problems.get("WRITING-" + no)));
            } else {
                for (int no = section.firstNo(); no <= section.lastNo(); no++) {
                    QuestionTypeDef type = QuestionTypeCatalog.resolve(source.getTrack(), section.skill(), no).orElseThrow();
                    slots.add(questionSlot(wb, type, no, problems.get(section.skill().name() + "-" + no)));
                }
            }
        }
        return slots;
    }

    public record LearnerAsk(int no, String prompt, String opt1, String opt2, String opt3, String opt4,
                             String answer, String explanation, String image) {
    }

    /** Một bài trên màn học viên: hướng dẫn dạng, nguyên liệu chung, rồi các câu của bài. */
    public record LearnerBai(String key, Skill skill, String typeCode, String typeName, String instructionKo,
                             String pointsLine, boolean showGuide, boolean writing,
                             String audio, String image, String passage, String script, String scriptVi,
                             List<LearnerAsk> asks) {
    }

    /** Ghép các câu cùng một đoạn nghe hoặc đoạn văn thành một bài. Bỏ bài chưa nhập. */
    public List<LearnerBai> learnerBais(Long sourceId) {
        ExamSource source = getSource(sourceId);
        Optional<ImportJob> job = latestJob(sourceId);
        if (job.isPresent()) {
            ParsedWorkbook wb = workbookOf(job.get());
            if (!wb.manualCards.isEmpty()) return baisFromCards(wb);
        }
        List<PaperSlot> slots = paper(sourceId);
        List<LearnerBai> out = new ArrayList<>();
        String lastType = "";
        Set<String> seen = new HashSet<>();
        for (PaperSlot slot : slots) {
            if (slot.writing()) {
                if (!slot.filled() || !seen.add(slot.key())) continue;
                boolean guide = !slot.typeCode().equals(lastType);
                lastType = slot.typeCode();
                String explain = joinNonBlank(slot.modelAnswer(), slot.rubric());
                out.add(new LearnerBai(slot.key(), Skill.WRITING, slot.typeCode(), slot.typeName(),
                        instructionOf(source.getTrack(), Skill.WRITING, slot.no()),
                        "Phần Viết · 4 câu · 100 điểm", guide, true,
                        "", slot.chart().startsWith("[") ? "" : slot.chart(), slot.passage(), "", "",
                        List.of(new LearnerAsk(slot.no(), slot.prompt(), "", "", "", "", "", explain, slot.chart()))));
                continue;
            }
            QuestionTypeDef type = QuestionTypeCatalog.resolve(source.getTrack(), slot.skill(), slot.no()).orElse(null);
            if (type == null) continue;
            List<Integer> block = type.blockOf(slot.no());
            if (block.isEmpty()) block = List.of(slot.no());
            String key = slot.skill().name() + "-" + block.get(0);
            if (!seen.add(key)) continue;
            List<PaperSlot> members = new ArrayList<>();
            for (int n : block) {
                slots.stream().filter(s -> s.skill() == slot.skill() && s.no() == n).findFirst().ifPresent(members::add);
            }
            String audio = firstText(members, PaperSlot::audio);
            String image = firstText(members, PaperSlot::image);
            String passage = firstText(members, PaperSlot::passage);
            String script = firstText(members, PaperSlot::script);
            if (members.stream().noneMatch(PaperSlot::filled) && audio.isEmpty() && passage.isEmpty() && image.isEmpty()) {
                continue;
            }
            boolean guide = !type.code().equals(lastType);
            lastType = type.code();
            int count = QuestionTypeCatalog.section(source.getTrack(), slot.skill()).map(QuestionTypeCatalog.Section::count).orElse(0);
            String points = "Phần " + slot.skill().getLabel() + " · " + count + " câu · 100 điểm";
            List<LearnerAsk> asks = members.stream()
                    .map(s -> new LearnerAsk(s.no(), s.prompt(), s.opt1(), s.opt2(), s.opt3(), s.opt4(), s.answer(), s.explanation(), s.chart()))
                    .toList();
            out.add(new LearnerBai(key, slot.skill(), type.code(), type.name(), type.instructionKo(), points, guide, false,
                    audio, image, passage, script, "", asks));
        }
        return out;
    }

    private List<LearnerBai> baisFromCards(ParsedWorkbook wb) {
        List<LearnerBai> out = new ArrayList<>();
        String lastCode = "";
        int running = 0;
        for (ParsedWorkbook.ManualCard card : wb.manualCards) {
            QuestionTemplate template = blank(card.templateCode) ? null : templateRepo.findByCode(card.templateCode).orElse(null);
            Skill skill = template == null ? Skill.READING : template.getSkill();
            boolean writing = skill == Skill.WRITING;
            String image = nz(card.imageFile);
            if (card.asks.size() == 1 && !blank(card.asks.get(0).chart)) {
                String chart = card.asks.get(0).chart.trim();
                if (!chart.startsWith("[")) {
                    image = chart;
                }
            }
            List<LearnerAsk> asks = new ArrayList<>();
            for (ParsedWorkbook.ManualAsk ask : card.asks) {
                running++;
                String[] options = ask.options == null ? new String[4] : ask.options;
                String[] optImgs = ask.optImgs == null ? new String[4] : ask.optImgs;
                String opt1 = !blank(at(options, 0)) ? at(options, 0) : nz(at(optImgs, 0));
                String opt2 = !blank(at(options, 1)) ? at(options, 1) : nz(at(optImgs, 1));
                String opt3 = !blank(at(options, 2)) ? at(options, 2) : nz(at(optImgs, 2));
                String opt4 = !blank(at(options, 3)) ? at(options, 3) : nz(at(optImgs, 3));
                String answer = ask.answer == null ? nz(ask.answerRaw) : String.valueOf(ask.answer);
                String explain = writing
                        ? joinNonBlank(ask.explanationVi, joinNonBlank(ask.modelAnswer, ask.rubricNote))
                        : nz(ask.explanationVi);
                String askChart = blank(ask.chart) ? "" : ask.chart.trim();
                String askImg = !askChart.startsWith("[") && !askChart.isEmpty() ? askChart : image;
                asks.add(new LearnerAsk(writing && ask.no != null ? ask.no : running, nz(ask.promptKo), opt1, opt2,
                        opt3, opt4, answer, explain, askImg));
            }
            if (asks.isEmpty()) continue;
            String code = template == null ? nz(card.templateCode) : template.getCode();
            boolean guide = !code.equals(lastCode);
            lastCode = code;
            String points = "Phần " + skill.getLabel() + " · " + asks.size() + " câu trong bài này";
            out.add(new LearnerBai(card.id, skill, code, template == null ? "Bài" : template.getName(),
                    template == null ? "" : template.getDescription(), points, guide, writing,
                    nz(card.audioFile), image, nz(card.passageKo), nz(card.scriptKo), nz(card.scriptVi), asks));
        }
        return out;
    }

    private static String instructionOf(Track track, Skill skill, int no) {
        return QuestionTypeCatalog.resolve(track, skill, no).map(QuestionTypeDef::instructionKo).orElse("");
    }

    private static String firstText(List<PaperSlot> members, java.util.function.Function<PaperSlot, String> field) {
        for (PaperSlot slot : members) {
            if (!blank(field.apply(slot))) return field.apply(slot);
        }
        return "";
    }

    private static String joinNonBlank(String a, String b) {
        if (blank(a)) return blank(b) ? "" : b;
        if (blank(b)) return a;
        return a + "\n" + b;
    }

    private static PaperSlot questionSlot(ParsedWorkbook wb, QuestionTypeDef type, int no, List<String> problems) {
        QuestionRow row = wb.questions.stream()
                .filter(q -> q.skill == type.skill() && Objects.equals(q.no, no)).findFirst().orElse(null);
        String[] opts = row == null || row.options == null ? new String[4] : row.options;
        boolean filled = row != null;
        return new PaperSlot(type.skill().name() + "-" + no, type.skill(), no, type.code(), type.name(), false, filled,
                row == null ? "" : nz(row.promptKo),
                nz(opts.length > 0 ? opts[0] : null), nz(opts.length > 1 ? opts[1] : null),
                nz(opts.length > 2 ? opts[2] : null), nz(opts.length > 3 ? opts[3] : null),
                row == null || row.answer == null ? nz(row == null ? null : row.answerRaw) : String.valueOf(row.answer),
                row == null ? "" : nz(row.explanationVi),
                shared(wb, type, no, r -> r.audioFile), shared(wb, type, no, r -> r.scriptKo),
                shared(wb, type, no, r -> r.passageKo), shared(wb, type, no, r -> r.imageFile),
                "", "", "", "", "",
                problems == null ? List.of() : problems);
    }

    private static PaperSlot writingSlot(ParsedWorkbook wb, int no, List<String> problems) {
        WritingRow row = wb.writings.stream().filter(w -> Objects.equals(w.no, no)).findFirst().orElse(null);
        QuestionTypeDef type = QuestionTypeCatalog.resolve(Track.TOPIK_II, Skill.WRITING, no).orElse(null);
        boolean filled = row != null;
        return new PaperSlot("WRITING-" + no, Skill.WRITING, no,
                type == null ? "" : type.code(), type == null ? "Viết" : type.name(), true, filled,
                row == null ? "" : nz(row.promptKo), "", "", "", "", "",
                "", "", "", row == null ? "" : nz(row.passageKo), "",
                row == null ? "" : nz(row.keys1), row == null ? "" : nz(row.keys2),
                row == null ? "" : nz(row.chartJson), row == null ? "" : nz(row.modelAnswer),
                row == null ? "" : nz(row.rubricNote),
                problems == null ? List.of() : problems);
    }

    private static void upsertQuestion(Track track, ParsedWorkbook wb, PaperForm form) {
        QuestionRow row = wb.questions.stream()
                .filter(q -> q.skill == form.skill() && Objects.equals(q.no, form.no())).findFirst().orElseGet(() -> {
                    QuestionRow created = new QuestionRow();
                    created.skill = form.skill();
                    created.no = form.no();
                    created.noRaw = String.valueOf(form.no());
                    created.options = new String[4];
                    wb.questions.add(created);
                    return created;
                });
        row.skill = form.skill();
        row.skillRaw = form.skill().name();
        row.no = form.no();
        row.noRaw = String.valueOf(form.no());
        row.promptKo = keep(row.promptKo, form.prompt());
        row.options[0] = keep(row.options[0], form.opt1());
        row.options[1] = keep(row.options[1], form.opt2());
        row.options[2] = keep(row.options[2], form.opt3());
        row.options[3] = keep(row.options[3], form.opt4());
        String answer = emptyToNull(form.answer());
        if (answer != null) {
            row.answerRaw = answer;
            row.answer = parseChoice(answer);
        }
        String explanation = emptyToNull(form.explanation());
        if (explanation != null) row.explanationVi = explanation;
        row.audioFile = keep(row.audioFile, form.audio());
        row.scriptKo = keep(row.scriptKo, form.script());
        row.passageKo = keep(row.passageKo, form.passage());
        row.imageFile = emptyToNull(form.image());
        QuestionTypeCatalog.resolve(track, form.skill(), form.no()).ifPresent(type -> {
            List<Integer> block = type.blockOf(form.no());
            if (block.size() > 1 && (row.groupId == null || row.groupId.isBlank())) {
                row.groupId = (form.skill() == Skill.LISTENING ? "L" : "R") + block.get(0);
            }
        });
    }

    private static void upsertWriting(ParsedWorkbook wb, PaperForm form) {
        WritingRow row = wb.writings.stream().filter(w -> Objects.equals(w.no, form.no())).findFirst().orElseGet(() -> {
            WritingRow created = new WritingRow();
            created.no = form.no();
            created.noRaw = String.valueOf(form.no());
            wb.writings.add(created);
            return created;
        });
        row.no = form.no();
        row.noRaw = String.valueOf(form.no());
        row.promptKo = keep(row.promptKo, form.prompt());
        row.passageKo = keep(row.passageKo, form.passage());
        row.keys1 = keep(row.keys1, form.keys1());
        row.keys2 = keep(row.keys2, form.keys2());
        row.chartJson = emptyToNull(form.chart());
        row.modelAnswer = keep(row.modelAnswer, form.modelAnswer());
        String rubric = emptyToNull(form.rubric());
        if (rubric != null) row.rubricNote = rubric;
        wb.hasWritingSheet = true;
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Integer parseChoice(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return switch (s) {
            case "1", "A" -> 1;
            case "2", "B" -> 2;
            case "3", "C" -> 3;
            case "4", "D" -> 4;
            default -> null;
        };
    }

    /** Ô trống khi lưu không xóa nội dung đã có từ Excel. Chỉ ghi đè khi người dùng nhập giá trị mới. */
    private static String keep(String current, String submitted) {
        String next = emptyToNull(submitted);
        return next != null ? next : current;
    }

    private static String shared(ParsedWorkbook wb, QuestionTypeDef type, int no, java.util.function.Function<QuestionRow, String> field) {
        List<Integer> block = type.blockOf(no);
        if (block.isEmpty()) block = List.of(no);
        for (int n : block) {
            for (QuestionRow row : wb.questions) {
                if (row.skill == type.skill() && Objects.equals(row.no, n) && !blank(field.apply(row))) {
                    return field.apply(row);
                }
            }
        }
        return "";
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ------------------------------------------------------------------ Bước 4: kích hoạt

    @Transactional
    public ActivationResult activate(Long sourceId, Actor actor) {
        requireContent(actor);
        ExamSource source = getSource(sourceId);
        ImportJob job = latestJob(sourceId).orElseThrow(() -> new ExamDomainException("Chưa có nội dung đề. Hãy nhập trên web hoặc tải Excel."));
        if (job.getStatus() == ImportJobStatus.ACTIVATED) {
            throw new ExamDomainException("Nguồn đề đã được đưa vào ngân hàng. Không thể kích hoạt lần nữa.");
        }
        ParsedWorkbook wb = workbookOf(job);
        if (isManual(job)) {
            List<ImportIssue> manualIssues = validateManual(source, wb);
            applyManualResult(job, wb, manualIssues);
            long manualErrors = manualIssues.stream().filter(ImportIssue::isError).count();
            if (manualErrors > 0) {
                throw new ExamDomainException("Còn lỗi, chưa kích hoạt được (" + manualErrors + " lỗi).");
            }
            return activateCards(source, job, wb, actor);
        }
        ExamImportValidator.Result r = validate(source, wb);
        if (r.errorCount() > 0) {
            runValidation(job);
            throw new ExamDomainException("Còn lỗi, chưa kích hoạt được (" + r.errorCount() + " lỗi).");
        }

        List<QuestionRow> rows = new ArrayList<>(wb.questions.stream().filter(q -> q.resolvedType != null).toList());
        List<QuestionTypeCatalog.Section> order = QuestionTypeCatalog.sections(source.getTrack());
        rows.sort(Comparator.comparingInt((QuestionRow q) -> order.indexOf(QuestionTypeCatalog.section(source.getTrack(), q.skill).orElseThrow()))
                .thenComparingInt(q -> q.no));

        Map<String, QuestionGroup> blockGroups = new HashMap<>();
        List<Question> created = new ArrayList<>();
        for (QuestionRow row : rows) {
            QuestionTypeDef type = QuestionTypeCatalog.byCode(row.resolvedType).orElseThrow();
            List<Integer> block = type.blockOf(row.no);
            String blockKey;
            if (block != null && !block.isEmpty()) {
                blockKey = row.skill + "#" + block.get(0);
            } else if (!blank(row.groupId)) {
                blockKey = row.skill + "#G#" + row.groupId.trim();
            } else {
                blockKey = row.skill + "#" + row.no;
            }
            QuestionGroup group = blockGroups.computeIfAbsent(blockKey, k -> createGroup(source, type, block, rows, row));

            Question q = new Question();
            q.setSource(source);
            q.setTypeCode(type.code());
            q.setTrack(source.getTrack());
            q.setSkill(row.skill);
            q.setOriginalNo(row.no);
            q.setGroup(group);
            q.setPromptKo(nz(row.promptKo));
            q.setOption1(row.options[0]);
            q.setOption2(row.options[1]);
            q.setOption3(row.options[2]);
            q.setOption4(row.options[3]);
            q.setAnswerIndex(row.answer);
            q.setExplanationVi(row.explanationVi);
            q.setPurpose(source.isAssessment() ? QuestionPurpose.ASSESSMENT
                    : row.purpose != null ? QuestionPurpose.valueOf(row.purpose) : QuestionPurpose.EXAM);
            q.setStatus(QuestionStatus.ACTIVE);
            q.setCauseHint(row.causeHint == null ? null : row.causeHint.toLowerCase(Locale.ROOT));
            q.setVocabIds(row.vocabIds);
            q.setGrammarIds(row.grammarIds);
            q.setContentHash(ExamImportValidator.contentHash(row));
            q.setCode(Codes.temporary());
            questions.save(q);
            q.setCode(String.format("E%04d", q.getId()));
            created.add(q);
        }

        List<WritingPrompt> createdWriting = new ArrayList<>();
        if (source.getTrack() == Track.TOPIK_II) {
            for (WritingRow w : wb.writings.stream().sorted(Comparator.comparingInt(x -> x.no)).toList()) {
                WritingPrompt p = new WritingPrompt();
                p.setSource(source);
                p.setNo(w.no);
                p.setPromptKo(joinNonBlank(w.passageKo, w.promptKo));
                p.setKeys1(w.keys1);
                p.setKeys2(w.keys2);
                if (w.chartJson != null && w.chartJson.trim().startsWith("[")) p.setChartJson(w.chartJson.trim());
                else p.setChartImage(w.chartJson);
                p.setModelAnswer(nz(w.modelAnswer));
                p.setRubricNote(w.rubricNote);
                p.setStatus(QuestionStatus.ACTIVE);
                p.setCode(Codes.temporary());
                writingPrompts.save(p);
                p.setCode(String.format("W%04d", p.getId()));
                createdWriting.add(p);
            }
        }

        source.setStatus(SourceStatus.ACTIVE);
        source.setActivatedAt(LocalDateTime.now());
        job.setStatus(ImportJobStatus.ACTIVATED);

        Long examId = null;
        if (source.isFullExam() && !source.isAssessment()) {
            examId = composer.createFullMockFromSource(source, created, createdWriting, actor).getId();
        }
        log(actor, "ACTIVATE_EXAM_SOURCE", "Kích hoạt nguồn đề \"" + source.getName() + "\": " + created.size()
                + " câu trắc nghiệm, " + createdWriting.size() + " đề Viết" + (examId != null ? ", tạo Full Mock nháp #" + examId : ""));
        return new ActivationResult(created.size(), createdWriting.size(), examId);
    }

    /** Đưa các bài nhập theo khuôn vào ngân hàng. Không tạo Full Mock, không kiểm số câu chuẩn. */
    private ActivationResult activateCards(ExamSource source, ImportJob job, ParsedWorkbook wb, Actor actor) {
        List<Question> created = new ArrayList<>();
        List<WritingPrompt> createdWriting = new ArrayList<>();
        Map<Skill, Integer> nextNo = new EnumMap<>(Skill.class);
        int writingSeq = 0;
        for (ParsedWorkbook.ManualCard card : wb.manualCards) {
            QuestionTemplate template = templateRepo.findByCode(card.templateCode).orElseThrow();
            if (template.getSkill() == Skill.WRITING) {
                writingSeq++;
                for (ParsedWorkbook.ManualAsk ask : card.asks) {
                    WritingPrompt prompt = new WritingPrompt();
                    prompt.setSource(source);
                    int official = officialWritingNo(template);
                    prompt.setNo(ask.no != null ? ask.no : (official > 0 ? official : 50 + writingSeq));
                    prompt.setPromptKo(joinNonBlank(card.passageKo, ask.promptKo));
                    prompt.setKeys1(ask.keys1);
                    prompt.setKeys2(ask.keys2);
                    String chart = !blank(ask.chart) ? ask.chart : card.imageFile;
                    if (chart != null && chart.trim().startsWith("[")) prompt.setChartJson(chart.trim());
                    else prompt.setChartImage(emptyToNull(chart));
                    String model = emptyToNull(ask.modelAnswer);
                    if (model == null) model = joinNonBlank(ask.keys1, ask.keys2);
                    prompt.setModelAnswer(nz(model));
                    prompt.setRubricNote(ask.rubricNote);
                    prompt.setStatus(QuestionStatus.ACTIVE);
                    prompt.setCode(Codes.temporary());
                    writingPrompts.save(prompt);
                    prompt.setCode(String.format("W%04d", prompt.getId()));
                    createdWriting.add(prompt);
                }
                continue;
            }
            String typeCode = !blank(card.typeCode) ? card.typeCode : structureCode(template);
            QuestionGroup group = new QuestionGroup();
            group.setSource(source);
            group.setTypeCode(typeCode);
            group.setSkill(template.getSkill());
            group.setSize(card.asks.size());
            group.setGroupKey(card.id);
            group.setScriptKo(card.scriptKo);
            group.setScriptVi(card.scriptVi);
            group.setPassageKo(card.passageKo);
            group.setAudioFile(card.audioFile);
            String groupImg = template.getImageScope() == ResourceScope.OPTIONS ? null : card.imageFile;
            if (blank(groupImg) && !card.asks.isEmpty() && !blank(card.asks.get(0).chart)) {
                String chart = card.asks.get(0).chart.trim();
                if (!chart.startsWith("[")) {
                    groupImg = chart;
                }
            }
            group.setImageFile(groupImg);
            groups.save(group);
            for (ParsedWorkbook.ManualAsk ask : card.asks) {
                int no = ask.no != null ? ask.no : nextNo.merge(template.getSkill(), 1, Integer::sum);
                Question question = new Question();
                question.setSource(source);
                question.setTypeCode(typeCode);
                question.setTemplateCode(template.getCode());
                question.setTrack(source.getTrack());
                question.setSkill(template.getSkill());
                question.setOriginalNo(no);
                question.setGroup(group);
                question.setPromptKo(nz(ask.promptKo));
                question.setOption1(optionAt(ask, 0));
                question.setOption2(optionAt(ask, 1));
                question.setOption3(optionAt(ask, 2));
                question.setOption4(optionAt(ask, 3));
                question.setAnswerIndex(ask.answer == null ? 1 : ask.answer);
                question.setExplanationVi(ask.explanationVi);
                question.setPurpose(source.isAssessment() ? QuestionPurpose.ASSESSMENT : QuestionPurpose.EXAM);
                question.setStatus(QuestionStatus.ACTIVE);
                question.setContentHash(ExamImportValidator.contentHash(ask.promptKo, card.passageKo, card.scriptKo, ask.options));
                question.setCode(Codes.temporary());
                questions.save(question);
                question.setCode(String.format("E%04d", question.getId()));
                created.add(question);
            }
        }
        if (created.isEmpty() && createdWriting.isEmpty()) {
            throw new ExamDomainException("Chưa có câu nào để đưa vào ngân hàng.");
        }
        source.setStatus(SourceStatus.ACTIVE);
        source.setActivatedAt(LocalDateTime.now());
        job.setStatus(ImportJobStatus.ACTIVATED);
        Exam exam = composer.createFullMockFromSource(source, created, createdWriting, actor);
        exam.setName(source.getName());
        log(actor, "ACTIVATE_EXAM_SOURCE", "Kích hoạt nguồn đề \"" + source.getName() + "\": " + created.size()
                + " câu trắc nghiệm, " + createdWriting.size() + " đề Viết, mở trong thư viện đề");
        return new ActivationResult(created.size(), createdWriting.size(), exam.getId());
    }

    private static String structureCode(QuestionTemplate template) {
        List<String> codes = QuestionTemplateCatalog.typeCodes(template.getCode());
        String code = codes.isEmpty() ? "CUSTOM" : codes.get(0);
        return code.length() > 10 ? code.substring(0, 10) : code;
    }

    private static int officialWritingNo(QuestionTemplate template) {
        List<String> codes = QuestionTemplateCatalog.typeCodes(template.getCode());
        if (codes.isEmpty()) return 0;
        String code = codes.get(0);
        if (code.startsWith("II-V") && code.length() == 5) {
            int digit = Character.digit(code.charAt(4), 10);
            return digit < 0 ? 0 : 50 + digit;
        }
        return 0;
    }

    private static String optionAt(ParsedWorkbook.ManualAsk ask, int index) {
        String value = at(ask.options, index);
        if (blank(value) && ask.optImgs != null) {
            String img = at(ask.optImgs, index);
            if (!blank(img)) return img;
        }
        return blank(value) ? "-" : value;
    }

    private QuestionGroup createGroup(ExamSource source, QuestionTypeDef type, List<Integer> block, List<QuestionRow> rows, QuestionRow currentRow) {
        List<QuestionRow> members;
        if (block != null && !block.isEmpty()) {
            members = rows.stream()
                    .filter(r -> r.skill == type.skill() && block.contains(r.no)).toList();
        } else if (!blank(currentRow.groupId)) {
            members = rows.stream()
                    .filter(r -> r.skill == currentRow.skill && currentRow.groupId.equalsIgnoreCase(r.groupId)).toList();
        } else {
            members = List.of(currentRow);
        }
        QuestionGroup g = new QuestionGroup();
        g.setSource(source);
        g.setTypeCode(type.code());
        g.setSkill(type.skill());
        g.setSize(members.size());
        g.setGroupKey(first(members, r -> r.groupId));
        g.setScriptKo(first(members, r -> r.scriptKo));
        g.setPassageKo(first(members, r -> r.passageKo));
        g.setAudioFile(first(members, r -> r.audioFile));
        g.setImageFile(first(members, r -> r.imageFile));
        return groups.save(g);
    }

    private static String first(List<QuestionRow> rows, java.util.function.Function<QuestionRow, String> f) {
        return rows.stream().map(f).filter(v -> v != null && !v.isBlank()).findFirst().orElse(null);
    }

    @Transactional
    public void deleteDraftSource(Long sourceId, Actor actor) {
        requireContent(actor);
        ExamSource s = draftSource(sourceId);
        jobs.deleteAll(jobs.findBySourceIdOrderByIdDesc(sourceId));
        media.deleteAll(media.findBySourceIdOrderByFilenameAsc(sourceId));
        sources.delete(s);
        log(actor, "DELETE_EXAM_SOURCE", "Xoá nguồn đề nháp \"" + s.getName() + "\"");
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.deleteSourceDir(sourceId);
                }
            });
        } else {
            storage.deleteSourceDir(sourceId);
        }
    }

    // ------------------------------------------------------------------ đọc

    public List<ExamSource> listSources() {
        return sources.findAllByOrderByIdDesc();
    }

    public ExamSource getSource(Long id) {
        return sources.findById(id).orElseThrow(() -> new ExamDomainException("Không tìm thấy nguồn đề #" + id + "."));
    }

    public Optional<ImportJob> latestJob(Long sourceId) {
        return jobs.findFirstBySourceIdOrderByIdDesc(sourceId);
    }

    public List<ImportJob> jobHistory(Long sourceId) {
        return jobs.findBySourceIdOrderByIdDesc(sourceId);
    }

    public List<MediaAsset> mediaOf(Long sourceId) {
        List<MediaAsset> list = media.findBySourceIdOrderByFilenameAsc(sourceId);
        for (MediaAsset a : list) {
            if (a.getStoragePath() != null && a.getStoragePath().contains("?")) {
                try {
                    Path real = storage.resolveMedia(sourceId, a.getFilename());
                    if (Files.exists(real)) {
                        a.setStoragePath(storage.toStored(real));
                        media.save(a);
                    }
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    /** Ảnh tải riêng bởi uploadQuestionImage không thuộc kho file dùng chung, kể cả bản ảnh đã thay thế. */
    public List<MediaAsset> sharedMediaOf(Long sourceId) {
        return mediaOf(sourceId).stream()
                .filter(asset -> !(asset.getKind() == MediaKind.IMAGE && asset.getFilename().matches(
                        "question-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?i:png|jpg|jpeg|webp|gif)")))
                .toList();
    }

    public Optional<MediaAsset> findMedia(Long sourceId, String filename) {
        if (filename == null || filename.isBlank()) return Optional.empty();
        String raw = filename.trim();
        Optional<MediaAsset> a = media.findBySourceIdAndFilename(sourceId, raw);
        if (a.isPresent()) return a;

        // Thử URL decode nếu tên file bị percent-encode (ví dụ: %20, %2C, tiếng Hàn)
        String decoded = null;
        try {
            decoded = java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (!decoded.equals(raw)) {
                a = media.findBySourceIdAndFilename(sourceId, decoded);
                if (a.isPresent()) return a;
            }
        } catch (Exception ignored) {}

        // Thử tìm trong danh sách media của nguồn đề theo tên không phân biệt hoa thường hoặc sau khi decode
        List<MediaAsset> list = media.findBySourceIdOrderByFilenameAsc(sourceId);
        for (MediaAsset m : list) {
            if (m.getFilename() != null) {
                if (m.getFilename().equalsIgnoreCase(raw)) return Optional.of(m);
                if (decoded != null && m.getFilename().equalsIgnoreCase(decoded)) return Optional.of(m);
                try {
                    String decM = java.net.URLDecoder.decode(m.getFilename(), java.nio.charset.StandardCharsets.UTF_8).trim();
                    if (decM.equalsIgnoreCase(raw) || (decoded != null && decM.equalsIgnoreCase(decoded))) {
                        return Optional.of(m);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Dự phòng: nếu file thực tế có trên đĩa nhưng chưa có bản ghi DB
        try {
            Path p = storage.resolveMedia(sourceId, raw);
            if (!Files.exists(p) && decoded != null) {
                p = storage.resolveMedia(sourceId, decoded);
            }
            if (Files.exists(p)) {
                MediaAsset fallback = new MediaAsset();
                fallback.setSource(sources.findById(sourceId).orElse(null));
                fallback.setFilename(p.getFileName().toString());
                fallback.setKind(MediaStorageService.isAudio(fallback.getFilename()) ? MediaKind.AUDIO : MediaKind.IMAGE);
                fallback.setSizeBytes(Files.size(p));
                fallback.setStoragePath(storage.toStored(p));
                return Optional.of(fallback);
            }
        } catch (Exception ignored) {}

        return Optional.empty();
    }

    public Path mediaPath(MediaAsset a) {
        if (a == null) return storage.resolveStored("");
        // 1. Ưu tiên tìm file theo filename thực tế trên đĩa (tránh lỗi nếu storagePath bị hỏng ký tự '?')
        if (a.getSource() != null && a.getFilename() != null && !a.getFilename().isBlank()) {
            try {
                Path p = storage.resolveMedia(a.getSource().getId(), a.getFilename());
                if (Files.exists(p)) {
                    if (a.getStoragePath() != null && a.getStoragePath().contains("?")) {
                        a.setStoragePath(storage.toStored(p));
                        media.save(a);
                    }
                    return p;
                }
            } catch (Exception ignored) {}
        }
        // 2. Thử đường dẫn storagePath đã lưu nếu không chứa '?'
        if (a.getStoragePath() != null && !a.getStoragePath().isBlank() && !a.getStoragePath().contains("?")) {
            try {
                Path p = storage.resolveStored(a.getStoragePath());
                if (Files.exists(p)) return p;
            } catch (Exception ignored) {}
        }
        return storage.resolveMediaUnchecked(a.getSource() != null ? a.getSource().getId() : 0L, a.getFilename());
    }

    public List<Question> questionsOf(Long sourceId) {
        return questions.findBySourceIdOrderBySkillAscOriginalNoAsc(sourceId);
    }

    public List<WritingPrompt> writingOf(Long sourceId) {
        return writingPrompts.findBySourceIdOrderByNoAsc(sourceId);
    }

    @Transactional
    public void updateSourceStatus(Long sourceId, SourceStatus newStatus, Actor actor) {
        requireContent(actor);
        ExamSource source = getSource(sourceId);
        ImportJob job = latestJob(sourceId).orElse(null);
        if (newStatus == SourceStatus.DRAFT && job != null && job.getStatus() == ImportJobStatus.ACTIVATED) {
            throw new ExamDomainException("Nguồn đề đã vào ngân hàng. Hãy sửa từng câu trong Ngân hàng câu hỏi.");
        }
        if (newStatus == SourceStatus.ACTIVE) {
            if (job == null || job.getStatus() != ImportJobStatus.ACTIVATED) {
                activate(sourceId, actor);
                return;
            }
        }
        source.setStatus(newStatus);
        if (newStatus == SourceStatus.ACTIVE && source.getActivatedAt() == null) {
            source.setActivatedAt(LocalDateTime.now());
        }
        sources.save(source);
        log(actor, "UPDATE_STATUS", "Cập nhật trạng thái nguồn đề " + source.getName() + " thành " + newStatus);
    }

    // ------------------------------------------------------------------ helpers

    private ExamSource draftSource(Long id) {
        ExamSource s = getSource(id);
        if (s.getStatus() != SourceStatus.DRAFT) {
            throw new ExamDomainException("Nguồn đề đã kích hoạt, không thay file được nữa. Sửa từng câu trong Ngân hàng câu hỏi.");
        }
        return s;
    }

    private static void requireContent(Actor actor) {
        if (actor == null || !actor.isContentManager()) {
            throw new ExamDomainException("Chỉ Content Manager được nhập đề (Admin chỉ xem).");
        }
    }

    private void log(Actor actor, String action, String details) {
        if (audit != null) {
            audit.logAction(actor.name(), actor.role(), action, "EXAM_SOURCE", details, "127.0.0.1");
        }
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
