package com.hottopik.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.PlacementAttemptRepository;
import com.hottopik.repository.exam.PlacementLineRepository;
import com.hottopik.repository.exam.QuestionRepository;
import com.hottopik.service.exam.ExamDomainException;
import com.hottopik.service.exam.PlacementRules;
import com.hottopik.service.exam.PlacementRules.Paper;
import com.hottopik.service.exam.QuestionTypeCatalog;
import com.hottopik.service.exam.QuestionTypeDef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PlacementService {

    public record QuestionView(Long id, int no, String typeName, String skill, String instruction,
                               String prompt, List<String> options, String passage, String audio, String image,
                               String mediaBase) {
    }

    public record EditorRow(QuestionTypeDef type, int count, int available) {
    }

    public record EditorPaper(Paper paper, List<EditorRow> listening, List<EditorRow> reading,
                              int listeningCount, int readingCount, boolean ready) {
    }

    public record ResultView(PlacementAttempt attempt, Paper paper, String scoreLine, int percent,
                             int level, String levelLabel, boolean offerTopikII, String message) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PlacementLineRepository lines;
    private final PlacementAttemptRepository attempts;
    private final QuestionRepository questions;

    public PlacementService(PlacementLineRepository lines, PlacementAttemptRepository attempts, QuestionRepository questions) {
        this.lines = lines;
        this.attempts = attempts;
        this.questions = questions;
    }

    @Transactional(readOnly = true)
    public List<EditorPaper> editor() {
        Map<String, Integer> saved = new HashMap<>();
        for (PlacementLine line : lines.findAll()) {
            saved.put(line.getPaperCode() + ":" + line.getTypeCode(), line.getQuestionCount());
        }
        Map<String, Integer> bank = new HashMap<>();
        for (Object[] row : questions.countActiveAssessmentByType()) {
            bank.put(String.valueOf(row[0]), ((Number) row[1]).intValue());
        }
        List<EditorPaper> papers = new ArrayList<>();
        for (Paper paper : Paper.values()) {
            papers.add(sheet(paper, saved, bank));
        }
        return papers;
    }

    @Transactional(readOnly = true)
    public EditorPaper paper(String paperCode) {
        Paper wanted = Paper.fromCode(paperCode);
        return editor().stream().filter(item -> item.paper() == wanted).findFirst().orElseThrow();
    }

    @Transactional
    public void save(String paperCode, Map<String, Integer> counts) {
        Paper paper = Paper.fromCode(paperCode);
        PlacementRules.check(paper, counts);
        Map<String, Integer> chosen = PlacementRules.normalize(paper, counts);
        lines.deleteByPaperCode(paper.name());
        lines.flush();
        int order = 0;
        for (QuestionTypeDef type : PlacementRules.allowed(paper)) {
            Integer count = chosen.get(type.code());
            if (count == null || count == 0) continue;
            PlacementLine line = new PlacementLine();
            line.setPaperCode(paper.name());
            line.setTypeCode(type.code());
            line.setQuestionCount(count);
            line.setSortOrder(order++);
            lines.save(line);
        }
    }

    @Transactional
    public PlacementAttempt start(Long userId, String email, String paperCode) {
        Paper paper = Paper.fromCode(paperCode);
        List<PlacementLine> plan = lines.findByPaperCodeOrderBySortOrderAsc(paper.name());
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PlacementLine line : plan) counts.put(line.getTypeCode(), line.getQuestionCount());
        PlacementRules.check(paper, counts);

        List<Question> selected = new ArrayList<>();
        int listen = 0;
        int read = 0;
        for (PlacementLine line : plan) {
            QuestionTypeDef type = QuestionTypeCatalog.byCode(line.getTypeCode())
                    .orElseThrow(() -> new ExamDomainException("Dạng câu không còn trong danh mục."));
            List<Question> pool = new ArrayList<>(questions.findAssessmentPool(type.code()));
            if (pool.size() < line.getQuestionCount()) {
                throw new ExamDomainException("Dạng " + type.name() + " cần " + line.getQuestionCount()
                        + " câu trong ngân hàng đánh giá, hiện có " + pool.size() + ".");
            }
            Collections.shuffle(pool);
            selected.addAll(pool.subList(0, line.getQuestionCount()));
            if (type.skill() == Skill.LISTENING) listen += line.getQuestionCount();
            else read += line.getQuestionCount();
        }
        PlacementAttempt attempt = new PlacementAttempt();
        attempt.setUserId(userId);
        attempt.setUserEmail(email);
        attempt.setPaperCode(paper.name());
        attempt.setQuestionIds(selected.stream().map(q -> q.getId().toString()).collect(Collectors.joining(",")));
        attempt.setTotal(selected.size());
        attempt.setListenTotal(listen);
        attempt.setReadTotal(read);
        attempt.setAnswersJson("{}");
        return attempts.save(attempt);
    }

    @Transactional(readOnly = true)
    public List<QuestionView> questions(PlacementAttempt attempt) {
        Map<Long, Question> byId = load(attempt.getQuestionIds()).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        List<QuestionView> views = new ArrayList<>();
        int no = 1;
        for (Long id : parseIds(attempt.getQuestionIds())) {
            Question question = byId.get(id);
            if (question == null) continue;
            views.add(view(question, no++));
        }
        return views;
    }

    @Transactional
    public PlacementAttempt submit(PlacementAttempt attempt, Map<String, String> submitted) {
        if (attempt.getCompletedAt() != null) return attempt;
        Map<Long, Question> byId = load(attempt.getQuestionIds()).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        Map<String, Integer> answers = new LinkedHashMap<>();
        int correct = 0;
        int listenCorrect = 0;
        int readCorrect = 0;
        for (Long id : parseIds(attempt.getQuestionIds())) {
            Integer selected = parseAnswer(submitted.get(String.valueOf(id)));
            if (selected != null) answers.put(id.toString(), selected);
            Question question = byId.get(id);
            if (question == null || selected == null || question.getAnswerIndex() != selected) continue;
            correct++;
            if (question.getSkill() == Skill.LISTENING) listenCorrect++;
            else if (question.getSkill() == Skill.READING) readCorrect++;
        }
        attempt.setAnswersJson(writeJson(answers));
        attempt.setCorrectCount(correct);
        attempt.setListenCorrect(listenCorrect);
        attempt.setReadCorrect(readCorrect);
        attempt.setPoints(PlacementRules.points(correct));
        attempt.setCompletedAt(LocalDateTime.now());
        return attempts.save(attempt);
    }

    @Transactional(readOnly = true)
    public ResultView result(PlacementAttempt attempt) {
        Paper paper = Paper.fromCode(attempt.getPaperCode());
        int level = PlacementRules.suggestedLevel(paper, attempt.getPoints());
        boolean offer = PlacementRules.offerTopikII(paper, attempt.getPoints());
        String message = message(paper, attempt.getPoints(), level);
        return new ResultView(attempt, paper,
                PlacementRules.scoreLine(attempt.getCorrectCount(), attempt.getTotal(), attempt.getPoints()),
                PlacementRules.percent(attempt.getPoints()),
                level, PlacementRules.levelLabel(level), offer, message);
    }

    public PlacementAttempt require(Long id) {
        return attempts.findById(id).orElseThrow(() -> new ExamDomainException("Không tìm thấy bài kiểm tra."));
    }

    private static String message(Paper paper, int points, int level) {
        if (paper == Paper.TOPIK_I && points >= PlacementRules.TOPIK_II_OFFER) {
            return "Bạn đạt từ 80%. Có thể làm tiếp bài TOPIK II để xem mình đang ở cấp 3–6, hoặc giữ TOPIK I và chọn mục tiêu.";
        }
        if (paper == Paper.TOPIK_I && level == 0) {
            return "Bạn chưa đạt cấp 1. Chọn mục tiêu TOPIK 1 hoặc 2 rồi sinh lộ trình TOPIK I.";
        }
        if (paper == Paper.TOPIK_I) {
            return "Trình hiện tại là " + PlacementRules.levelLabel(level) + ". Chọn mục tiêu rồi sinh lộ trình.";
        }
        if (level == 0) {
            return "Bạn chưa đạt cấp 3. Nên về lộ trình TOPIK I, chọn mục tiêu cấp 1 hoặc 2.";
        }
        return "Trình hiện tại là " + PlacementRules.levelLabel(level) + ". Chọn mục tiêu TOPIK II rồi sinh lộ trình. Điểm này chưa tính phần Viết.";
    }

    private static EditorPaper sheet(Paper paper, Map<String, Integer> saved, Map<String, Integer> bank) {
        List<EditorRow> listening = new ArrayList<>();
        List<EditorRow> reading = new ArrayList<>();
        int listen = 0;
        int read = 0;
        for (QuestionTypeDef type : PlacementRules.allowed(paper)) {
            int count = saved.getOrDefault(paper.name() + ":" + type.code(), 0);
            EditorRow row = new EditorRow(type, count, bank.getOrDefault(type.code(), 0));
            if (type.skill() == Skill.LISTENING) {
                listening.add(row);
                listen += count;
            } else if (type.skill() == Skill.READING) {
                reading.add(row);
                read += count;
            }
        }
        boolean ready = listen == PlacementRules.SKILL_QUOTA && read == PlacementRules.SKILL_QUOTA;
        return new EditorPaper(paper, listening, reading, listen, read, ready);
    }

    private static QuestionView view(Question question, int no) {
        QuestionGroup group = question.getGroup();
        QuestionTypeDef type = QuestionTypeCatalog.byCode(question.getTypeCode()).orElse(null);
        String audio = group == null ? null : group.getAudioFile();
        String image = group == null ? null : group.getImageFile();
        String mediaBase = group != null && group.getSource() != null ? "/placement/media/" + group.getSource().getId() + "/" : null;
        return new QuestionView(question.getId(), no,
                type == null ? question.getTypeCode() : type.name(),
                question.getSkill().getLabel(),
                type == null ? "" : type.instructionKo(),
                question.getPromptKo(),
                question.options(),
                group == null ? null : group.getPassageKo(),
                audio == null || mediaBase == null ? null : mediaBase + audio,
                image == null || mediaBase == null ? null : mediaBase + image,
                mediaBase);
    }

    private static Integer parseAnswer(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int selected = Integer.parseInt(raw);
            return selected >= 1 && selected <= 4 ? selected : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Question> load(String raw) {
        List<Long> ids = parseIds(raw);
        if (ids.isEmpty()) return List.of();
        return questions.findWithMediaByIdIn(ids);
    }

    private static List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(",")).map(Long::parseLong).toList();
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
