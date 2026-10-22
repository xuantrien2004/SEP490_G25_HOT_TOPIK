package com.hottopik.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.PracticeAttemptRepository;
import com.hottopik.repository.exam.QuestionRepository;
import com.hottopik.service.exam.QuestionTypeCatalog;
import com.hottopik.service.exam.QuestionTypeDef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PracticeService {

    public record QuestionView(Long id, int no, String prompt, List<String> options, int answer,
                               String explanation, String passage, String script, String audio, String image) {}
    public record AnswerView(QuestionView question, Integer selected, boolean correct) {}
    public record ResultView(PracticeAttempt attempt, QuestionTypeDef type, List<AnswerView> answers,
                             String duration, List<PracticeAttempt> history) {}
    public record TypeSummary(QuestionTypeDef type, long available, long completed, Integer bestPercent,
                              Integer latestPercent) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final QuestionRepository questions;
    private final PracticeAttemptRepository attempts;
    private final com.hottopik.repository.exam.WritingPromptRepository writingPrompts;
    private final WritingSubmissionService writingSubmissions;

    public PracticeService(QuestionRepository questions,
                           PracticeAttemptRepository attempts,
                           com.hottopik.repository.exam.WritingPromptRepository writingPrompts,
                           WritingSubmissionService writingSubmissions) {
        this.questions = questions;
        this.attempts = attempts;
        this.writingPrompts = writingPrompts;
        this.writingSubmissions = writingSubmissions;
    }

    @Transactional(readOnly = true)
    public List<TypeSummary> summaries(Long userId, Track track, Skill skill) {
        return QuestionTypeCatalog.forTrackAndSkill(track, skill).stream().map(type -> {
            List<Question> pool = type.isWriting() ? List.of() : pool(type);
            List<PracticeAttempt> history = userId == null ? List.of()
                    : attempts.findByUserIdAndTypeCodeAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId, type.code());
            Integer best = history.stream().mapToInt(a -> percent(a.getScore(), a.getTotal())).max().stream().boxed().findFirst().orElse(null);
            Integer latest = history.isEmpty() ? null : percent(history.get(0).getScore(), history.get(0).getTotal());
            return new TypeSummary(type, pool.size(), history.size(), best, latest);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<TypeSummary> allSummaries(Long userId) {
        return allSummaries(userId, null);
    }

    @Transactional(readOnly = true)
    public List<TypeSummary> allSummaries(Long userId, String email) {
        return QuestionTypeCatalog.all().stream().map(type -> {
            if (type.isWriting()) {
                int qNo = type.numbers().get(0);
                long available = writingPrompts != null
                        ? writingPrompts.findByNoAndStatusOrderByIdDesc(qNo, QuestionStatus.ACTIVE).size()
                        : 0;
                List<com.hottopik.dto.WritingSubmissionDTO> mySubs = (email != null && writingSubmissions != null)
                        ? writingSubmissions.getSubmissionsByLearner(email).stream().filter(s -> s.getQuestionNo() == qNo).toList()
                        : List.of();
                long completed = mySubs.size();
                Integer latestPercent = null;
                Integer bestPercent = null;
                if (!mySubs.isEmpty()) {
                    List<Integer> percents = mySubs.stream().map(s -> {
                        int score = s.getTotalScore() != null ? s.getTotalScore() : (s.getAiScore() != null ? s.getAiScore() : 0);
                        int max = s.getMaxScore() > 0 ? s.getMaxScore() : (qNo == 53 ? 30 : (qNo == 54 ? 50 : 10));
                        return percent(score, max);
                    }).toList();
                    latestPercent = percents.get(0);
                    bestPercent = percents.stream().max(Integer::compareTo).orElse(null);
                }
                return new TypeSummary(type, available, completed, bestPercent, latestPercent);
            }
            List<Question> pool = pool(type);
            List<PracticeAttempt> history = userId == null ? List.of()
                    : attempts.findByUserIdAndTypeCodeAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId, type.code());
            Integer best = history.stream().mapToInt(a -> percent(a.getScore(), a.getTotal())).max().stream().boxed().findFirst().orElse(null);
            Integer latest = history.isEmpty() ? null : percent(history.get(0).getScore(), history.get(0).getTotal());
            return new TypeSummary(type, pool.size(), history.size(), best, latest);
        }).toList();
    }

    @Transactional
    public PracticeAttempt start(Long userId, String email, String typeCode) {
        if (userId == null) {
            userId = 1L;
            email = "learner@hottopik.com";
        }
        QuestionTypeDef type = type(typeCode);
        if (type.isWriting()) throw new IllegalArgumentException("Dạng Viết được làm trong khu vực luyện Viết.");
        List<Question> available = pool(type);
        if (available.isEmpty()) throw new IllegalStateException("Ngân hàng câu hỏi chưa có câu hỏi đang hoạt động cho dạng " + type.name() + " (" + typeCode + "). Vui lòng liên hệ Content Manager hoặc nạp đề.");

        // Prioritize unseen question groups while preserving the catalog's original question slots.
        Set<Long> seen = attempts.findByUserIdAndTypeCodeAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId, typeCode)
                .stream().flatMap(a -> parseIds(a.getQuestionIds()).stream()).collect(Collectors.toSet());
        Map<Long, List<Question>> byGroup = available.stream().collect(Collectors.groupingBy(q -> q.getGroup().getId()));
        List<Question> selected = new ArrayList<>();
        for (List<Integer> block : type.blocks()) {
            Set<Integer> blockNumbers = new HashSet<>(block);
            List<List<Question>> matchingGroups = byGroup.values().stream()
                    .filter(group -> group.stream().map(Question::getOriginalNo).collect(Collectors.toSet()).containsAll(blockNumbers))
                    .map(group -> group.stream().filter(q -> blockNumbers.contains(q.getOriginalNo()))
                            .sorted(Comparator.comparingInt(Question::getOriginalNo)).toList())
                    .filter(group -> group.stream().map(Question::getOriginalNo).distinct().count() == block.size())
                    .collect(Collectors.toCollection(ArrayList::new));
            if (matchingGroups.isEmpty()) continue;
            List<List<Question>> unseenGroups = matchingGroups.stream()
                    .filter(group -> group.stream().noneMatch(q -> seen.contains(q.getId())))
                    .collect(Collectors.toCollection(ArrayList::new));
            List<List<Question>> choices = unseenGroups.isEmpty() ? matchingGroups : unseenGroups;
            Collections.shuffle(choices);
            selected.addAll(choices.get(0));
        }
        selected.sort(Comparator.comparingInt(Question::getOriginalNo));
        if (selected.size() != type.requiredCount()) {
            throw new IllegalStateException("Dạng câu này hiện chưa có bài luyện đầy đủ. Bạn có thể chọn dạng câu khác.");
        }

        PracticeAttempt attempt = new PracticeAttempt();
        attempt.setUserId(userId);
        attempt.setUserEmail(email);
        attempt.setTrack(type.track());
        attempt.setSkill(type.skill());
        attempt.setTypeCode(type.code());
        attempt.setQuestionIds(selected.stream().map(q -> q.getId().toString()).collect(Collectors.joining(",")));
        attempt.setTotal(selected.size());
        attempt.setAnswersJson("{}");
        attempt.setScore(0);
        return attempts.save(attempt);
    }

    /**
     * Làm lại đúng bài luyện tập này (giữ nguyên danh sách câu hỏi cũ để học viên ôn tập kiểm tra lại kiến thức)
     */
    @Transactional
    public PracticeAttempt retry(Long userId, String email, Long attemptId) {
        if (userId == null) {
            userId = 1L;
            email = "learner@hottopik.com";
        }
        PracticeAttempt original = attempts.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài làm cần làm lại."));

        PracticeAttempt newAttempt = new PracticeAttempt();
        newAttempt.setUserId(userId);
        newAttempt.setUserEmail(email);
        newAttempt.setTrack(original.getTrack());
        newAttempt.setSkill(original.getSkill());
        newAttempt.setTypeCode(original.getTypeCode());
        newAttempt.setQuestionIds(original.getQuestionIds());
        newAttempt.setTotal(original.getTotal());
        newAttempt.setAnswersJson("{}");
        newAttempt.setScore(0);
        return attempts.save(newAttempt);
    }

    @Transactional
    public PracticeAttempt submit(Long userId, Long attemptId, Map<String, String> submitted) {
        if (userId == null) userId = 1L;
        PracticeAttempt attempt = attempts.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lượt luyện tập."));
        if (attempt.getCompletedAt() != null) return attempt;

        List<Long> ids = parseIds(attempt.getQuestionIds());
        List<Question> questions = this.questions.findAllById(ids);
        Map<Long, Question> byId = questions.stream().collect(Collectors.toMap(Question::getId, q -> q));
        Map<String, Integer> answers = new LinkedHashMap<>();
        int score = 0;
        for (Long id : ids) {
            String raw = submitted.get(String.valueOf(id));
            Integer selected = null;
            try { if (raw != null) selected = Integer.parseInt(raw); } catch (NumberFormatException ignored) { }
            if (selected != null && (selected < 1 || selected > 4)) selected = null;
            if (selected != null) answers.put(id.toString(), selected);
            Question q = byId.get(id);
            if (q != null && selected != null && q.getAnswerIndex() == selected) score++;
        }
        attempt.setAnswersJson(writeJson(answers));
        attempt.setScore(score);
        attempt.setCompletedAt(LocalDateTime.now());
        return attempts.save(attempt);
    }

    @Transactional(readOnly = true)
    public List<QuestionView> questions(PracticeAttempt attempt) {
        Map<Long, Question> byId = this.questions.findAllById(parseIds(attempt.getQuestionIds())).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        return parseIds(attempt.getQuestionIds()).stream().map(byId::get).filter(Objects::nonNull).map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public ResultView result(Long userId, Long attemptId) {
        if (userId == null) userId = 1L;
        PracticeAttempt attempt = attempts.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kết quả."));
        Map<String, Integer> selected = readAnswers(attempt.getAnswersJson());
        Map<Long, Question> byId = questions.findAllById(parseIds(attempt.getQuestionIds())).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        List<AnswerView> answerViews = parseIds(attempt.getQuestionIds()).stream().map(byId::get).filter(Objects::nonNull)
                .map(q -> new AnswerView(view(q), selected.get(q.getId().toString()), selected.get(q.getId().toString()) != null
                        && selected.get(q.getId().toString()) == q.getAnswerIndex())).toList();
        String duration = attempt.getStartedAt() == null || attempt.getCompletedAt() == null ? "00:00"
                : formatDuration(Duration.between(attempt.getStartedAt(), attempt.getCompletedAt()).toSeconds());
        return new ResultView(attempt, type(attempt.getTypeCode()), answerViews,
                duration, attempts.findByUserIdAndTypeCodeAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId, attempt.getTypeCode()));
    }

    @Transactional(readOnly = true)
    public List<PracticeAttempt> historyOfType(Long userId, String typeCode) {
        if (userId == null) userId = 1L;
        return attempts.findByUserIdAndTypeCodeAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId, typeCode);
    }

    /**
     * Tìm kiếm câu hỏi trong ngân hàng: linh hoạt theo type_code, template_code hoặc dải số câu chuẩn
     */
    private List<Question> pool(QuestionTypeDef type) {
        List<Question> list = questions.findByTrackAndSkillAndTypeCodeAndStatus(
                type.track(), type.skill(), type.code(), QuestionStatus.ACTIVE);
        return list.stream()
                .sorted(Comparator.comparingInt(Question::getOriginalNo))
                .toList();
    }

    private QuestionView view(Question q) {
        QuestionGroup g = q.getGroup();
        String audio = g != null ? g.getAudioFile() : null;
        String image = g != null ? g.getImageFile() : null;
        String mediaBase = (g != null && g.getSource() != null) ? "/content/media/" + g.getSource().getId() + "/" : null;
        return new QuestionView(q.getId(), q.getOriginalNo(), q.getPromptKo(), q.options(), q.getAnswerIndex(),
                q.getExplanationVi(), g != null ? g.getPassageKo() : null, g != null ? g.getScriptKo() : null,
                audio == null || mediaBase == null ? null : mediaBase + audio,
                image == null || mediaBase == null ? null : mediaBase + image);
    }

    private QuestionTypeDef type(String code) {
        return QuestionTypeCatalog.byCode(code).orElseThrow(() -> new IllegalArgumentException("Dạng câu không hợp lệ: " + code));
    }

    private static int percent(int score, int total) { return total == 0 ? 0 : Math.round(score * 100f / total); }
    private static List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(",")).map(Long::parseLong).toList();
    }
    private static String writeJson(Object value) {
        try { return JSON.writeValueAsString(value); } catch (Exception e) { return "{}"; }
    }
    private static Map<String, Integer> readAnswers(String raw) {
        try { return JSON.readValue(raw, new TypeReference<>() {}); } catch (Exception e) { return Map.of(); }
    }
    private static String formatDuration(long seconds) {
        long elapsed = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%02d:%02d", elapsed / 60, elapsed % 60);
    }
}
