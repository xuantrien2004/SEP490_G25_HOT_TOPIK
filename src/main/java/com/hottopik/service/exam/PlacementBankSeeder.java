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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
        Map<String, Integer> needed = new HashMap<>();
        for (Paper paper : Paper.values()) {
            PlacementRules.demo(paper).forEach((code, count) -> needed.merge(code, count, Math::max));
        }
        topUp(donors, needed);

        for (Paper paper : Paper.values()) {
            if (!lines.findByPaperCodeOrderBySortOrderAsc(paper.name()).isEmpty()) continue;
            placement.save(paper.name(), PlacementRules.demo(paper));
            log.info("Đã soạn {}: 10 Nghe + 10 Đọc.", paper.title());
        }
    }

    /** Đủ câu demo cho từng dạng. Thiếu thì chép từ đề mẫu, vẫn thiếu thì viết câu tạm. */
    private void topUp(Map<String, List<Question>> donors, Map<String, Integer> needed) {
        Map<String, Integer> have = assessmentCounts();
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : needed.entrySet()) {
            int gap = entry.getValue() - have.getOrDefault(entry.getKey(), 0);
            if (gap > 0) missing.put(entry.getKey(), gap);
        }
        if (missing.isEmpty()) return;

        Map<String, Integer> fromDonors = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : missing.entrySet()) {
            int copy = Math.min(entry.getValue(), donors.getOrDefault(entry.getKey(), List.of()).size());
            if (copy > 0) fromDonors.put(entry.getKey(), copy);
        }
        if (!fromDonors.isEmpty()) copyBank(donors, fromDonors);

        questions.flush();
        have = assessmentCounts();
        for (String code : missing.keySet()) {
            int still = needed.get(code) - have.getOrDefault(code, 0);
            if (still <= 0) continue;
            QuestionTypeDef type = QuestionTypeCatalog.byCode(code).orElseThrow();
            ExamSource target = sourceFor(type.track());
            ensureDemoMedia(target);
            int already = have.getOrDefault(code, 0);
            for (int i = 0; i < still; i++) synthesize(type, target, already + i);
            log.info("Đã thêm {} câu demo tạm cho {}.", still, type.code());
        }
    }

    private Map<String, Integer> assessmentCounts() {
        Map<String, Integer> have = new HashMap<>();
        for (Object[] row : questions.countActiveAssessmentByType()) {
            have.put(String.valueOf(row[0]), ((Number) row[1]).intValue());
        }
        return have;
    }

    private ExamSource sourceFor(Track track) {
        for (ExamSource source : sources.findAll()) {
            if (MARK.equals(source.getNote()) && source.getTrack() == track) return source;
        }
        return newSource(track);
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

    private void synthesize(QuestionTypeDef type, ExamSource target, int index) {
        boolean image = type.imageRequired();
        QuestionGroup group = new QuestionGroup();
        group.setSource(target);
        group.setTypeCode(type.code());
        group.setSkill(type.skill());
        group.setSize(1);
        group.setGroupKey("D-" + type.code() + "-" + index);
        if (type.skill() == Skill.LISTENING) {
            group.setAudioFile("nghe.mp3");
            group.setScriptKo("가: 안녕하세요. 나: 네, 안녕하세요.");
        } else {
            group.setPassageKo("이것은 예시 지문입니다. 학생들이 도서관에서 책을 읽습니다.");
        }
        groups.save(group);

        String[] options = image
                ? new String[]{"a1.png", "a2.png", "a3.png", "a4.png"}
                : new String[]{"네, 맞아요.", "아니요.", "잘 모르겠어요.", "내일 가요."};
        int answer = (index % 4) + 1;
        String prompt = "(Demo) " + type.name() + " · câu " + (index + 1);
        Question question = new Question();
        question.setSource(target);
        question.setGroup(group);
        question.setTypeCode(type.code());
        question.setTemplateCode(QuestionTemplateCatalog.templateCode(type.code()));
        question.setTrack(type.track());
        question.setSkill(type.skill());
        question.setOriginalNo(type.numbers().get(Math.min(index, type.numbers().size() - 1)));
        question.setPromptKo(prompt);
        question.setOption1(options[0]);
        question.setOption2(options[1]);
        question.setOption3(options[2]);
        question.setOption4(options[3]);
        question.setAnswerIndex(answer);
        question.setPurpose(QuestionPurpose.ASSESSMENT);
        question.setStatus(QuestionStatus.ACTIVE);
        question.setExplanationVi("Câu demo tạm. Đáp án " + answer + ".");
        question.setContentHash(ExamImportValidator.contentHash(prompt + "\n" + type.code() + "\n" + index,
                group.getPassageKo(), group.getScriptKo(), options));
        question.setCode(Codes.temporary());
        questions.save(question);
        question.setCode(String.format("E%04d", question.getId()));
        target.setExpectedCount(target.getExpectedCount() + 1);
    }

    private void ensureDemoMedia(ExamSource source) {
        putMedia(source, "nghe.mp3", MediaKind.AUDIO, mpegFrame());
        putMedia(source, "a1.png", MediaKind.IMAGE, png(new Color(196, 60, 50), "1"));
        putMedia(source, "a2.png", MediaKind.IMAGE, png(new Color(40, 90, 170), "2"));
        putMedia(source, "a3.png", MediaKind.IMAGE, png(new Color(46, 125, 80), "3"));
        putMedia(source, "a4.png", MediaKind.IMAGE, png(new Color(210, 130, 40), "4"));
    }

    private void putMedia(ExamSource source, String filename, MediaKind kind, byte[] bytes) {
        if (media.findBySourceIdAndFilename(source.getId(), filename).isPresent()) return;
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            Path stored = storage.storeMedia(source.getId(), filename, in);
            MediaAsset asset = new MediaAsset();
            asset.setSource(source);
            asset.setFilename(filename);
            asset.setKind(kind);
            asset.setSizeBytes(bytes.length);
            asset.setStoragePath(storage.toStored(stored));
            media.save(asset);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] mpegFrame() {
        byte[] frame = new byte[417];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        frame[2] = (byte) 0x90;
        byte[] all = new byte[frame.length * 4];
        for (int i = 0; i < 4; i++) System.arraycopy(frame, 0, all, i * frame.length, frame.length);
        return all;
    }

    private static byte[] png(Color color, String label) {
        BufferedImage image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 80, 80);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));
        g.drawString(label, 28, 50);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
