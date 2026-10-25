package com.hottopik.service.exam;

import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.*;
import com.hottopik.service.PlacementService;
import com.hottopik.service.exam.PlacementRules.Paper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Profile local: chép câu đơn từ đề mẫu và soạn sẵn bài TOPIK I, TOPIK II nếu chưa có. */
@Component
@Profile("local")
@Order(40)
public class PlacementBankSeeder implements ApplicationRunner {

    static final String MARK = "PLACEMENT_BANK";

    private static final Logger log = LoggerFactory.getLogger(PlacementBankSeeder.class);

    private final QuestionRepository questions;
    private final ExamSourceRepository sources;
    private final QuestionGroupRepository groups;
    private final MediaAssetRepository media;
    private final MediaStorageService storage;
    private final PlacementLineRepository lines;
    private final PlacementService placement;

    public PlacementBankSeeder(QuestionRepository questions, ExamSourceRepository sources,
                               QuestionGroupRepository groups, MediaAssetRepository media,
                               MediaStorageService storage, PlacementLineRepository lines,
                               PlacementService placement) {
        this.questions = questions;
        this.sources = sources;
        this.groups = groups;
        this.media = media;
        this.storage = storage;
        this.lines = lines;
        this.placement = placement;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, List<Question>> donors = new LinkedHashMap<>();
        for (Question question : questions.findPlacementDonors()) {
            QuestionTypeDef type = QuestionTypeCatalog.byCode(question.getTypeCode()).orElse(null);
            if (!PlacementRules.singleQuestion(type)) continue;
            donors.computeIfAbsent(type.code(), key -> new ArrayList<>()).add(question);
        }
        Map<String, Integer> available = new HashMap<>();
        donors.forEach((code, list) -> available.put(code, list.size()));

        Map<Paper, Map<String, Integer>> plans = new EnumMap<>(Paper.class);
        Map<String, Integer> needed = new HashMap<>();
        for (Paper paper : Paper.values()) {
            Map<String, Integer> plan = PlacementRules.demo(paper);
            if (!PlacementRules.covered(plan, available)) {
                log.warn("Chưa đủ câu đơn để soạn {}. Bỏ qua.", paper.title());
                plan = Map.of();
            }
            plans.put(paper, plan);
            plan.forEach((code, count) -> needed.merge(code, count, Math::max));
        }
        boolean bankReady = sources.findAll().stream().anyMatch(source -> MARK.equals(source.getNote()));
        if (!bankReady && !needed.isEmpty()) copyBank(donors, needed);

        for (Paper paper : Paper.values()) {
            if (!lines.findByPaperCodeOrderBySortOrderAsc(paper.name()).isEmpty()) continue;
            Map<String, Integer> plan = plans.get(paper);
            if (plan == null || plan.isEmpty()) continue;
            placement.save(paper.name(), plan);
            log.info("Đã soạn {}: 10 Nghe + 10 Đọc.", paper.title());
        }
    }

    private void copyBank(Map<String, List<Question>> donors, Map<String, Integer> needed) {
        Map<Track, ExamSource> bank = new EnumMap<>(Track.class);
        Map<Track, ExamSource> copiedFrom = new EnumMap<>(Track.class);
        List<String> codes = needed.keySet().stream()
                .sorted(Comparator.comparingInt(code -> QuestionTypeCatalog.byCode(code).map(QuestionTypeDef::sortOrder).orElse(0)))
                .toList();
        for (String code : codes) {
            List<Question> pool = donors.getOrDefault(code, List.of());
            int count = Math.min(needed.get(code), pool.size());
            for (int i = 0; i < count; i++) {
                Question donor = pool.get(i);
                ExamSource target = bank.computeIfAbsent(donor.getTrack(), this::newSource);
                ExamSource origin = donor.getSource();
                if (origin != null && copiedFrom.putIfAbsent(donor.getTrack(), origin) == null) copyMedia(origin, target);
                cloneQuestion(donor, target);
            }
        }
        log.info("Đã chép câu đơn từ đề mẫu sang ngân hàng đánh giá.");
    }

    private ExamSource newSource(Track track) {
        ExamSource source = new ExamSource();
        source.setName("Ngân hàng đánh giá · " + track.getLabel());
        source.setTrack(track);
        source.setExpectedCount(0);
        source.setAssessment(true);
        source.setCopyrightNote("Câu đơn lấy từ đề mẫu local, dùng cho kiểm tra trình độ.");
        source.setNote(MARK);
        source.setStatus(SourceStatus.ACTIVE);
        source.setCreatedBy("Hệ thống");
        source.setActivatedAt(LocalDateTime.now());
        return sources.save(source);
    }

    private void copyMedia(ExamSource from, ExamSource to) {
        for (MediaAsset asset : media.findBySourceIdOrderByFilenameAsc(from.getId())) {
            if (media.findBySourceIdAndFilename(to.getId(), asset.getFilename()).isPresent()) continue;
            Path path = storage.resolveStored(asset.getStoragePath());
            if (!Files.exists(path)) continue;
            try (InputStream in = Files.newInputStream(path)) {
                Path stored = storage.storeMedia(to.getId(), asset.getFilename(), in);
                MediaAsset copy = new MediaAsset();
                copy.setSource(to);
                copy.setFilename(asset.getFilename());
                copy.setKind(asset.getKind());
                copy.setSizeBytes(asset.getSizeBytes());
                copy.setStoragePath(storage.toStored(stored));
                media.save(copy);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void cloneQuestion(Question donor, ExamSource target) {
        QuestionGroup origin = donor.getGroup();
        QuestionGroup group = new QuestionGroup();
        group.setSource(target);
        group.setTypeCode(donor.getTypeCode());
        group.setSkill(donor.getSkill());
        group.setSize(1);
        group.setGroupKey("P-" + donor.getId());
        if (origin != null) {
            group.setPassageKo(origin.getPassageKo());
            group.setScriptKo(origin.getScriptKo());
            group.setAudioFile(origin.getAudioFile());
            group.setImageFile(origin.getImageFile());
        }
        groups.save(group);

        Question copy = new Question();
        copy.setSource(target);
        copy.setGroup(group);
        copy.setTypeCode(donor.getTypeCode());
        copy.setTemplateCode(donor.getTemplateCode());
        copy.setTrack(donor.getTrack());
        copy.setSkill(donor.getSkill());
        copy.setOriginalNo(donor.getOriginalNo());
        copy.setPromptKo(donor.getPromptKo());
        copy.setOption1(donor.getOption1());
        copy.setOption2(donor.getOption2());
        copy.setOption3(donor.getOption3());
        copy.setOption4(donor.getOption4());
        copy.setAnswerIndex(donor.getAnswerIndex());
        copy.setPurpose(QuestionPurpose.ASSESSMENT);
        copy.setStatus(QuestionStatus.ACTIVE);
        copy.setExplanationVi(donor.getExplanationVi());
        copy.setContentHash(ExamImportValidator.contentHash(
                donor.getPromptKo() + "\n" + donor.getId(),
                origin == null ? null : origin.getPassageKo(),
                origin == null ? null : origin.getScriptKo(),
                new String[]{donor.getOption1(), donor.getOption2(), donor.getOption3(), donor.getOption4()}));
        copy.setCode(Codes.temporary());
        questions.save(copy);
        copy.setCode(String.format("E%04d", copy.getId()));
        target.setExpectedCount(target.getExpectedCount() + 1);
    }
}
