package com.hottopik.service.exam;

import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.*;
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
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Bốn đề mẫu cho profile local: mỗi dạng TOPIK I và TOPIK II xuất hiện trong đúng hai đề.
 * Đề mẫu 1 đã phát hành (kèm Mini Test của phần có câu). Đề mẫu 2 để nháp để gửi duyệt thử.
 * Chạy lại không tạo trùng.
 */
@Component
@Profile("local")
@Order(30)
public class SamplePaperSeeder implements ApplicationRunner {

    static final String MARK = "SAMPLE_PAPERS";

    private static final Logger log = LoggerFactory.getLogger(SamplePaperSeeder.class);

    private final ExamSourceRepository sources;
    private final ExamRepository exams;
    private final QuestionRepository questions;
    private final QuestionGroupRepository groups;
    private final WritingPromptRepository writings;
    private final MediaAssetRepository media;
    private final MediaStorageService storage;

    public SamplePaperSeeder(ExamSourceRepository sources, ExamRepository exams, QuestionRepository questions,
                              QuestionGroupRepository groups, WritingPromptRepository writings,
                              MediaAssetRepository media, MediaStorageService storage) {
        this.sources = sources;
        this.exams = exams;
        this.questions = questions;
        this.groups = groups;
        this.writings = writings;
        this.media = media;
        this.storage = storage;
    }

    /** Một dạng trong một đề mẫu, kèm số câu đã gán trong phần thi. */
    record Slot(String templateCode, Skill skill, List<Integer> numbers) {
    }

    static List<Slot> plan(Track track, int paper) {
        int listen = 1;
        int read = track == Track.TOPIK_I ? 31 : 1;
        List<Slot> slots = new ArrayList<>();
        for (QuestionTemplateCatalog.Seed seed : QuestionTemplateCatalog.all()) {
            if (seed.track() != track) continue;
            if (seed.skill() == Skill.WRITING) {
                int no = 50 + Integer.parseInt(seed.code().substring(seed.code().length() - 2));
                slots.add(checked(track, seed, List.of(no)));
                continue;
            }
            int count = paper == 1 ? seed.childMin() : seed.childMax();
            int start = seed.skill() == Skill.LISTENING ? listen : read;
            List<Integer> nos = new ArrayList<>();
            for (int i = 0; i < count; i++) nos.add(start + i);
            if (seed.skill() == Skill.LISTENING) listen += count;
            else read += count;
            slots.add(checked(track, seed, nos));
        }
        return List.copyOf(slots);
    }

    private static Slot checked(Track track, QuestionTemplateCatalog.Seed seed, List<Integer> nos) {
        QuestionTypeCatalog.Section section = QuestionTypeCatalog.section(track, seed.skill())
                .orElseThrow(() -> new IllegalStateException("Không có phần " + seed.skill() + " của " + track));
        for (int no : nos) {
            if (no < section.firstNo() || no > section.lastNo()) {
                throw new IllegalStateException(seed.code() + " rơi vào câu " + no + ", ngoài " + section.firstNo() + "–" + section.lastNo());
            }
        }
        return new Slot(seed.code(), seed.skill(), List.copyOf(nos));
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean already = sources.findAll().stream().anyMatch(s -> MARK.equals(s.getNote()));
        if (already) {
            log.info("Đề mẫu đã có ({}), bỏ qua.", MARK);
            return;
        }
        for (Track track : Track.values()) {
            for (int paper = 1; paper <= 2; paper++) {
                seedPaper(track, paper, paper == 1);
            }
        }
        log.info("Đã thêm 4 đề mẫu. Mỗi dạng TOPIK I và TOPIK II có trong Đề mẫu 1 (đã phát hành) và Đề mẫu 2 (nháp).");
    }

    private void seedPaper(Track track, int paper, boolean publish) {
        List<Slot> slots = plan(track, paper);
        int count = slots.stream().mapToInt(s -> s.numbers().size()).sum();
        ExamSource source = new ExamSource();
        source.setName(track.getLabel() + " · Đề mẫu " + paper);
        source.setTrack(track);
        source.setExpectedCount(count);
        source.setAssessment(false);
        source.setCopyrightNote("Đề mẫu nội bộ, dùng để xem ngân hàng và thư viện.");
        source.setNote(MARK);
        source.setStatus(SourceStatus.ACTIVE);
        source.setCreatedBy("Hệ thống");
        source.setActivatedAt(LocalDateTime.now());
        sources.save(source);
        storeMedia(source);

        List<Question> created = new ArrayList<>();
        List<WritingPrompt> createdWriting = new ArrayList<>();
        for (Slot slot : slots) {
            QuestionTemplateCatalog.Seed seed = QuestionTemplateCatalog.all().stream()
                    .filter(s -> s.code().equals(slot.templateCode()))
                    .findFirst()
                    .orElseThrow();
            if (seed.skill() == Skill.WRITING) {
                createdWriting.add(writing(source, seed, paper, slot.numbers().get(0)));
            } else {
                created.addAll(questions(source, seed, paper, slot.numbers()));
            }
        }

        Exam exam = new Exam();
        exam.setCode(Codes.temporary());
        exam.setName(source.getName());
        exam.setTrack(track);
        exam.setKind(ExamKind.FULL);
        exam.setSeqNo(exams.maxSeqNo(track, ExamKind.FULL) + 1);
        exam.setSource(source);
        exam.setCreatedBy("Hệ thống");
        for (QuestionTypeCatalog.Section section : QuestionTypeCatalog.sections(track)) {
            switch (section.skill()) {
                case LISTENING -> exam.setListeningMinutes(section.minutes());
                case READING -> exam.setReadingMinutes(section.minutes());
                case WRITING -> exam.setWritingMinutes(section.minutes());
            }
        }
        if (publish) {
            exam.setStatus(ExamStatus.PUBLISHED);
            exam.setPublishedAt(LocalDateTime.now());
        } else {
            exam.setStatus(ExamStatus.DRAFT);
        }
        exams.save(exam);
        exam.setCode(String.format("EX%04d", exam.getId()));
        for (Question q : created) {
            exam.getItems().add(new ExamItem(exam, q.getSkill(), q.getOriginalNo(), q.getTypeCode(), q));
        }
        for (WritingPrompt p : createdWriting) {
            switch (p.getNo()) {
                case 51 -> exam.setWriting51(p);
                case 52 -> exam.setWriting52(p);
                case 53 -> exam.setWriting53(p);
                case 54 -> exam.setWriting54(p);
                default -> throw new IllegalStateException("Đề Viết không thuộc 51–54: " + p.getNo());
            }
        }
        exams.save(exam);
        if (publish) publishMinis(exam);
    }

    private void publishMinis(Exam parent) {
        for (QuestionTypeCatalog.Section section : QuestionTypeCatalog.sections(parent.getTrack())) {
            ExamKind kind = ExamKind.valueOf(section.skill().name());
            if (kind == ExamKind.WRITING && parent.writingPrompts().isEmpty()) continue;
            if (kind != ExamKind.WRITING && parent.getItems().stream().noneMatch(it -> it.getSection() == section.skill())) {
                continue;
            }
            Exam mini = new Exam();
            mini.setCode(Codes.temporary());
            mini.setKind(kind);
            mini.setTrack(parent.getTrack());
            mini.setParent(parent);
            mini.setAuto(true);
            mini.setSource(parent.getSource());
            mini.setSeqNo(parent.getSeqNo());
            mini.setName("Mini " + section.skill().getLabel() + " " + parent.getTrack().getLabel() + " · Đề " + parent.getSeqNo());
            mini.setStatus(ExamStatus.PUBLISHED);
            mini.setPublishedAt(parent.getPublishedAt());
            mini.setListeningMinutes(parent.getListeningMinutes());
            mini.setReadingMinutes(parent.getReadingMinutes());
            mini.setWritingMinutes(parent.getWritingMinutes());
            mini.setCreatedBy("Hệ thống");
            exams.save(mini);
            mini.setCode(parent.getCode() + "-" + section.skill().name().charAt(0));
            if (kind == ExamKind.WRITING) {
                mini.setWriting51(parent.getWriting51());
                mini.setWriting52(parent.getWriting52());
                mini.setWriting53(parent.getWriting53());
                mini.setWriting54(parent.getWriting54());
            } else {
                for (ExamItem it : parent.getItems()) {
                    if (it.getSection() == section.skill()) {
                        mini.getItems().add(new ExamItem(mini, it.getSection(), it.getNo(), it.getTypeCode(), it.getQuestion()));
                    }
                }
            }
            exams.save(mini);
        }
    }

    private List<Question> questions(ExamSource source, QuestionTemplateCatalog.Seed seed, int paper, List<Integer> nos) {
        String typeCode = typeCode(seed.code());
        QuestionGroup group = new QuestionGroup();
        group.setSource(source);
        group.setTypeCode(typeCode);
        group.setSkill(seed.skill());
        group.setSize(nos.size());
        group.setGroupKey("S" + paper + "-" + seed.code());
        if (seed.skill() == Skill.LISTENING) {
            group.setAudioFile("nghe.mp3");
            group.setScriptKo("가: 샘플 " + paper + " " + seed.code() + " 입니다. 나: 네, 알겠습니다.");
        }
        if (seed.skill() == Skill.READING) {
            group.setPassageKo("샘플 " + paper + ". " + seed.code() + ". 학생들이 도서관에서 책을 읽습니다.");
            if (seed.imageScope() == ResourceScope.GROUP) group.setImageFile("bieu-do.png");
        }
        groups.save(group);
        List<Question> saved = new ArrayList<>();
        String[] options = options(seed);
        for (int no : nos) {
            int answer = (no % 4) + 1;
            Question q = new Question();
            q.setSource(source);
            q.setTypeCode(typeCode);
            q.setTemplateCode(seed.code());
            q.setTrack(source.getTrack());
            q.setSkill(seed.skill());
            q.setOriginalNo(no);
            q.setGroup(group);
            q.setPromptKo("(Đề mẫu " + paper + ") " + seed.name() + " — câu " + no);
            q.setOption1(options[0]);
            q.setOption2(options[1]);
            q.setOption3(options[2]);
            q.setOption4(options[3]);
            q.setAnswerIndex(answer);
            q.setExplanationVi("Đề mẫu " + paper + " · " + seed.code() + " · " + seed.name() + ". Đáp án " + answer + ".");
            q.setPurpose(QuestionPurpose.EXAM);
            q.setStatus(QuestionStatus.ACTIVE);
            q.setContentHash(ExamImportValidator.contentHash(q.getPromptKo(), group.getPassageKo(), group.getScriptKo(), options));
            q.setCode(Codes.temporary());
            questions.save(q);
            q.setCode(String.format("E%04d", q.getId()));
            saved.add(q);
        }
        return saved;
    }

    private WritingPrompt writing(ExamSource source, QuestionTemplateCatalog.Seed seed, int paper, int no) {
        WritingPrompt p = new WritingPrompt();
        p.setSource(source);
        p.setNo(no);
        p.setPromptKo("(Đề mẫu " + paper + ") " + seed.name() + " — câu " + no);
        if (seed.answerType() == AnswerType.ACCEPT_KEYS) {
            p.setKeys1("오후에 만나요");
            p.setKeys2("도서관 앞에서");
            p.setModelAnswer("오후에 만나요. 도서관 앞에서.");
        } else {
            p.setModelAnswer(no == 53
                    ? "도서관 이용자가 계속 늘고 있습니다."
                    : "한국어 공부는 매일 조금씩 하는 것이 좋습니다.");
        }
        if ("TPL2-WRI-03".equals(seed.code())) p.setChartImage("bieu-do.png");
        p.setRubricNote(seed.rubric());
        p.setStatus(QuestionStatus.ACTIVE);
        p.setCode(Codes.temporary());
        writings.save(p);
        p.setCode(String.format("W%04d", p.getId()));
        return p;
    }

    private void storeMedia(ExamSource source) {
        byte[] mp3 = mpegFrame();
        put(source, "nghe.mp3", MediaKind.AUDIO, mp3);
        put(source, "a1.png", MediaKind.IMAGE, png(new Color(196, 60, 50), "1"));
        put(source, "a2.png", MediaKind.IMAGE, png(new Color(40, 90, 170), "2"));
        put(source, "a3.png", MediaKind.IMAGE, png(new Color(46, 125, 80), "3"));
        put(source, "a4.png", MediaKind.IMAGE, png(new Color(210, 130, 40), "4"));
        put(source, "bieu-do.png", MediaKind.IMAGE, chartPng());
    }

    private void put(ExamSource source, String filename, MediaKind kind, byte[] bytes) {
        try {
            Path path = storage.storeMedia(source.getId(), filename, new ByteArrayInputStream(bytes));
            MediaAsset asset = new MediaAsset();
            asset.setSource(source);
            asset.setFilename(filename);
            asset.setKind(kind);
            asset.setSizeBytes(bytes.length);
            asset.setStoragePath(storage.toStored(path));
            media.save(asset);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String typeCode(String template) {
        List<String> codes = QuestionTemplateCatalog.typeCodes(template);
        String code = codes.isEmpty() ? "CUSTOM" : codes.get(0);
        return code.length() > 10 ? code.substring(0, 10) : code;
    }

    private static String[] options(QuestionTemplateCatalog.Seed seed) {
        if (seed.displayMode() == DisplayMode.IMAGE_CHOICE) {
            return new String[]{"a1.png", "a2.png", "a3.png", "a4.png"};
        }
        if (seed.displayMode() == DisplayMode.ORDERING) {
            return new String[]{"가-나-다-라", "나-가-다-라", "다-가-나-라", "라-다-나-가"};
        }
        if (seed.displayMode() == DisplayMode.INSERT) {
            return new String[]{"(가)", "(나)", "(다)", "(라)"};
        }
        return new String[]{"네, 맞아요.", "아니요.", "잘 모르겠어요.", "내일 가요."};
    }

    /** Một khung MPEG-1 Layer III để trình duyệt nhận ra file nghe. */
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
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        g.drawString(label, 28, 50);
        g.dispose();
        return writePng(image);
    }

    private static byte[] chartPng() {
        BufferedImage image = new BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 160, 90);
        int[] heights = {30, 50, 70};
        Color[] colors = {new Color(40, 90, 170), new Color(46, 125, 80), new Color(196, 60, 50)};
        for (int i = 0; i < heights.length; i++) {
            g.setColor(colors[i]);
            g.fillRect(20 + i * 45, 80 - heights[i], 28, heights[i]);
        }
        g.dispose();
        return writePng(image);
    }

    private static byte[] writePng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) throw new IOException("Không ghi được PNG.");
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
