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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
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
        jobs.findFirstBySourceIdOrderByIdDesc(sourceId).ifPresent(prev -> {
            if (prev.getStatus() != ImportJobStatus.ACTIVATED) prev.setStatus(ImportJobStatus.SUPERSEDED);
        });
        Path path = storage.storeExcel(sourceId, file);
        ImportJob job = new ImportJob();
        job.setSource(source);
        job.setFileName(name);
        job.setFilePath(storage.toStored(path));
        job.setCreatedBy(actor.name());
        jobs.save(job);
        runValidation(job);
        log(actor, "UPLOAD_EXAM_EXCEL", "Tải file " + name + " cho nguồn đề #" + sourceId
                + " → " + job.getErrorCount() + " lỗi, " + job.getWarningCount() + " cảnh báo");
        return job;
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
                .ifPresent(this::runValidation);
        log(actor, "UPLOAD_EXAM_MEDIA", "Tải " + stored + " file âm thanh/ảnh cho nguồn đề #" + sourceId
                + (rejected.isEmpty() ? "" : ", từ chối " + rejected.size() + " file"));
        return new MediaUploadReport(stored, rejected);
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
        draftSource(sourceId);
        MediaAsset a = media.findById(mediaId).filter(m -> m.getSource().getId().equals(sourceId))
                .orElseThrow(() -> new ExamDomainException("Không tìm thấy file."));
        media.delete(a);
        try {
            Files.deleteIfExists(storage.resolveStored(a.getStoragePath()));
        } catch (IOException ignored) {
            // file mồ côi trên đĩa không ảnh hưởng nghiệp vụ
        }
        jobs.findFirstBySourceIdOrderByIdDesc(sourceId)
                .filter(j -> j.getStatus() != ImportJobStatus.SUPERSEDED && j.getStatus() != ImportJobStatus.ACTIVATED)
                .ifPresent(this::runValidation);
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
            return ExamWorkbookParser.parse(in);
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

    private ExamImportValidator.Result validate(ExamSource source, ParsedWorkbook wb) {
        Map<String, Long> sizes = media.findBySourceIdOrderByFilenameAsc(source.getId()).stream()
                .collect(Collectors.toMap(m -> ExamImportValidator.key(m.getFilename()), MediaAsset::getSizeBytes, (a, b) -> a));
        ExamImportValidator.Context ctx = new ExamImportValidator.Context(source.getTrack(), source.isFullExam(),
                source.isAssessment(), sizes, hashes -> questions.findByContentHashIn(hashes).stream()
                .filter(q -> q.getSource() == null || !q.getSource().getId().equals(source.getId()))
                .collect(Collectors.toMap(Question::getContentHash, Question::getCode, (a, b) -> a)));
        return ExamImportValidator.validate(wb, ctx);
    }

    public boolean isManual(ImportJob job) {
        return job != null && MANUAL_PATH.equals(job.getFilePath());
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
                           List<String> prompt, List<String> opt1, List<String> opt2, List<String> opt3, List<String> opt4,
                           List<String> answer, List<String> explanation, List<String> keys1, List<String> keys2,
                           List<String> modelAnswer, List<String> rubric, List<String> chart,
                           String typeCode, List<Integer> qno) {
    }

    public record AskView(String prompt, String opt1, String opt2, String opt3, String opt4, String answer,
                          String explanation, String keys1, String keys2, String modelAnswer, String rubric, String chart,
                          Integer no) {
        public static AskView blank() {
            return new AskView("", "", "", "", "", "", "", "", "", "", "", "", null);
        }

        public AskView withNo(Integer no) {
            return new AskView(prompt, opt1, opt2, opt3, opt4, answer, explanation, keys1, keys2, modelAnswer, rubric, chart, no);
        }
    }

    public record CardView(String id, String templateCode, String templateName, String skillLabel, int askCount,
                           boolean ready, String audio, String image, String passage, String script,
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
        card.templateCode = template.getCode();
        card.typeCode = chosen == null ? null : chosen.code();
        card.audioFile = emptyToNull(form.audio());
        card.imageFile = emptyToNull(form.image());
        card.passageKo = emptyToNull(form.passage());
        card.scriptKo = emptyToNull(form.script());
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
            ask.no = form.qno() == null || i >= form.qno().size() ? null : form.qno().get(i);
            card.asks.add(ask);
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
        List<ImportIssue> found = issues(job.get());
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
                    nz(card.audioFile), nz(card.imageFile), nz(card.passageKo), nz(card.scriptKo),
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
            if (template.getImageScope() == ResourceScope.GROUP && template.isImageRequired() && blank(card.imageFile)) {
                issues.add(ImportIssue.error("CARD", index, skill, index, "Bài " + index + " thiếu ảnh hoặc biểu đồ."));
            } else if (template.getImageScope() == ResourceScope.GROUP && !blank(card.imageFile)
                    && !sizes.containsKey(ExamImportValidator.key(card.imageFile))) {
                issues.add(ImportIssue.error("CARD", index, skill, index, "Chưa tải ảnh " + card.imageFile + "."));
            }
            if (template.isPassageRequired() && blank(card.passageKo)) {
                issues.add(ImportIssue.error("CARD", index, skill, index, "Bài " + index + " thiếu đoạn văn dùng chung."));
            }
            if (template.getScriptScope() == ResourceScope.GROUP && blank(card.scriptKo)) {
                issues.add(ImportIssue.warning("CARD", index, skill, index,
                        "Bài " + index + " chưa có phụ đề. Phụ đề chỉ hiện khi xem giải thích."));
            }
            int askNo = 0;
            for (ParsedWorkbook.ManualAsk ask : card.asks) {
                askNo++;
                String where = "Bài " + index + ", câu " + askNo;
                if (template.isPromptRequired() && blank(ask.promptKo)) {
                    issues.add(ImportIssue.error("CARD", index, skill, index, where + " thiếu câu hỏi."));
                }
                if (template.getOptionCount() != null) {
                    String[] options = ask.options == null ? new String[4] : ask.options;
                    for (int i = 0; i < 4; i++) {
                        String option = i < options.length ? options[i] : null;
                        if (blank(option)) {
                            issues.add(ImportIssue.error("CARD", index, skill, index, where + " thiếu phương án " + (i + 1) + "."));
                        } else if (template.getImageScope() == ResourceScope.OPTIONS
                                && !sizes.containsKey(ExamImportValidator.key(option))) {
                            issues.add(ImportIssue.error("CARD", index, skill, index,
                                    where + " chưa tải ảnh phương án " + option + "."));
                        }
                    }
                    if (ask.answer == null) {
                        issues.add(ImportIssue.error("CARD", index, skill, index, where + " chưa chọn đáp án 1–4."));
                    }
                    if (blank(ask.explanationVi)) {
                        issues.add(ImportIssue.warning("CARD", index, skill, index,
                                where + " chưa có giải thích tiếng Việt. Điền sau cũng được."));
                    }
                }
                if (template.getAnswerType() == AnswerType.ACCEPT_KEYS && (blank(ask.keys1) || blank(ask.keys2))) {
                    issues.add(ImportIssue.error("CARD", index, skill, index, where + " thiếu đáp án chấp nhận cho cả hai chỗ trống."));
                }
                if (template.getAnswerType() == AnswerType.FREE_TEXT && blank(ask.modelAnswer)) {
                    issues.add(ImportIssue.error("CARD", index, skill, index, where + " thiếu bài mẫu."));
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
        return new AskView(nz(ask.promptKo), nz(at(options, 0)), nz(at(options, 1)), nz(at(options, 2)), nz(at(options, 3)),
                ask.answer == null ? nz(ask.answerRaw) : String.valueOf(ask.answer),
                nz(ask.explanationVi), nz(ask.keys1), nz(ask.keys2), nz(ask.modelAnswer), nz(ask.rubricNote), nz(ask.chart),
                ask.no);
    }

    private static int askCount(CardForm form) {
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
                             String answer, String explanation) {
    }

    /** Một bài trên màn học viên: hướng dẫn dạng, nguyên liệu chung, rồi các câu của bài. */
    public record LearnerBai(String key, Skill skill, String typeCode, String typeName, String instructionKo,
                             String pointsLine, boolean showGuide, boolean writing,
                             String audio, String image, String passage, String script,
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
                        "", slot.image(), "", "",
                        List.of(new LearnerAsk(slot.no(), slot.prompt(), "", "", "", "", "", explain))));
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
                    .map(s -> new LearnerAsk(s.no(), s.prompt(), s.opt1(), s.opt2(), s.opt3(), s.opt4(), s.answer(), s.explanation()))
                    .toList();
            out.add(new LearnerBai(key, slot.skill(), type.code(), type.name(), type.instructionKo(), points, guide, false,
                    audio, image, passage, script, asks));
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
            List<LearnerAsk> asks = new ArrayList<>();
            for (ParsedWorkbook.ManualAsk ask : card.asks) {
                running++;
                String[] options = ask.options == null ? new String[4] : ask.options;
                String answer = ask.answer == null ? nz(ask.answerRaw) : String.valueOf(ask.answer);
                String explain = writing
                        ? joinNonBlank(ask.explanationVi, joinNonBlank(ask.modelAnswer, ask.rubricNote))
                        : nz(ask.explanationVi);
                asks.add(new LearnerAsk(running, nz(ask.promptKo), nz(at(options, 0)), nz(at(options, 1)),
                        nz(at(options, 2)), nz(at(options, 3)), answer, explain));
            }
            if (asks.isEmpty()) continue;
            String code = template == null ? nz(card.templateCode) : template.getCode();
            boolean guide = !code.equals(lastCode);
            lastCode = code;
            String points = "Phần " + skill.getLabel() + " · " + asks.size() + " câu trong bài này";
            out.add(new LearnerBai(card.id, skill, code, template == null ? "Bài" : template.getName(),
                    template == null ? "" : template.getDescription(), points, guide, writing,
                    nz(card.audioFile), nz(card.imageFile), nz(card.passageKo), nz(card.scriptKo), asks));
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
        boolean filled = row != null && !blank(row.promptKo);
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
        boolean filled = row != null && !blank(row.promptKo);
        return new PaperSlot("WRITING-" + no, Skill.WRITING, no,
                type == null ? "" : type.code(), type == null ? "Viết" : type.name(), true, filled,
                row == null ? "" : nz(row.promptKo), "", "", "", "", "",
                "", "", "", "", "",
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
        row.imageFile = keep(row.imageFile, form.image());
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
        row.keys1 = keep(row.keys1, form.keys1());
        row.keys2 = keep(row.keys2, form.keys2());
        row.chartJson = keep(row.chartJson, form.chart());
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
        ExamSource source = draftSource(sourceId);
        ImportJob job = latestJob(sourceId).orElseThrow(() -> new ExamDomainException("Chưa có nội dung đề. Hãy nhập trên web hoặc tải Excel."));
        ParsedWorkbook wb = workbookOf(job);
        if (isManual(job)) {
            List<ImportIssue> manualIssues = validateManual(source, wb);
            long manualErrors = manualIssues.stream().filter(ImportIssue::isError).count();
            if (manualErrors > 0) {
                applyManualResult(job, wb, manualIssues);
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
            String blockKey = row.skill + "#" + block.get(0);
            QuestionGroup group = blockGroups.computeIfAbsent(blockKey, k -> createGroup(source, type, block, rows));

            Question q = new Question();
            q.setSource(source);
            q.setTypeCode(type.code());
            q.setTrack(source.getTrack());
            q.setSkill(row.skill);
            q.setOriginalNo(row.no);
            q.setGroup(group);
            q.setPromptKo(row.promptKo);
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
                p.setPromptKo(w.promptKo);
                p.setKeys1(w.keys1);
                p.setKeys2(w.keys2);
                if (w.chartJson != null && w.chartJson.trim().startsWith("[")) p.setChartJson(w.chartJson.trim());
                else p.setChartImage(w.chartJson);
                p.setModelAnswer(w.modelAnswer);
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
                    prompt.setPromptKo(blank(ask.promptKo) ? template.getName() : ask.promptKo);
                    prompt.setKeys1(ask.keys1);
                    prompt.setKeys2(ask.keys2);
                    String chart = !blank(ask.chart) ? ask.chart : card.imageFile;
                    if (chart != null && chart.trim().startsWith("[")) prompt.setChartJson(chart.trim());
                    else prompt.setChartImage(emptyToNull(chart));
                    String model = emptyToNull(ask.modelAnswer);
                    if (model == null) model = joinNonBlank(ask.keys1, ask.keys2);
                    if (model == null || model.isBlank()) model = prompt.getPromptKo();
                    prompt.setModelAnswer(model);
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
            group.setPassageKo(card.passageKo);
            group.setAudioFile(card.audioFile);
            group.setImageFile(template.getImageScope() == ResourceScope.OPTIONS ? null : card.imageFile);
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
                question.setPromptKo(blank(ask.promptKo) ? template.getName() : ask.promptKo);
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
        return blank(value) ? "-" : value;
    }

    private QuestionGroup createGroup(ExamSource source, QuestionTypeDef type, List<Integer> block, List<QuestionRow> rows) {
        List<QuestionRow> members = rows.stream()
                .filter(r -> r.skill == type.skill() && block.contains(r.no)).toList();
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
        return media.findBySourceIdOrderByFilenameAsc(sourceId);
    }

    public Optional<MediaAsset> findMedia(Long sourceId, String filename) {
        return media.findBySourceIdAndFilename(sourceId, filename);
    }

    public Path mediaPath(MediaAsset a) {
        return storage.resolveStored(a.getStoragePath());
    }

    public List<Question> questionsOf(Long sourceId) {
        return questions.findBySourceIdOrderBySkillAscOriginalNoAsc(sourceId);
    }

    public List<WritingPrompt> writingOf(Long sourceId) {
        return writingPrompts.findBySourceIdOrderByNoAsc(sourceId);
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
        audit.logAction(actor.name(), actor.role(), action, "EXAM_SOURCE", details, "127.0.0.1");
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
