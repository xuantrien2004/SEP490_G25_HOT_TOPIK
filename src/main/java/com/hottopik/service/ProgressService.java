package com.hottopik.service;

import com.hottopik.dto.ExamScoreDTO;
import com.hottopik.entity.exam.ExamAttempt;
import com.hottopik.entity.exam.PracticeAttempt;
import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;
import com.hottopik.repository.exam.ExamAttemptRepository;
import com.hottopik.repository.exam.PracticeAttemptRepository;
import com.hottopik.service.exam.CheckedAnswers;
import com.hottopik.service.exam.ExamScoreCalculator;
import com.hottopik.service.exam.LearnerExamService;
import com.hottopik.service.exam.QuestionTypeDef;
import com.hottopik.service.exam.QuestionTypeCatalog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ProgressService {

    private final ExamAttemptRepository examAttempts;
    private final PracticeAttemptRepository practiceAttempts;
    private final LearnerExamService learnerExamService;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ProgressService(ExamAttemptRepository examAttempts,
                           PracticeAttemptRepository practiceAttempts,
                           LearnerExamService learnerExamService) {
        this.examAttempts = examAttempts;
        this.practiceAttempts = practiceAttempts;
        this.learnerExamService = learnerExamService;
    }

    // ---- DTOs ----

    public record MockScorePoint(String label, String date, int score, int total, boolean hasScore) {}

    public record MockBarStat(
            Long attemptId,
            Long examId,
            String examName,
            String shortName,
            String track,
            double listenScore,
            double readScore,
            double writingScore,
            double totalScore,
            String completedDate,
            int attemptCount
    ) {}

    public record TypeStat(
            String code, String name, Skill skill, String rangeLabel,
            int done, int correct, int total,
            double accuracy,        // 0..1
            String practiceUrl
    ) {}

    public record ProgressData(
            List<MockScorePoint> mockScores,
            List<MockBarStat> topikIScores,
            List<MockBarStat> topikIIScores,
            List<TypeStat> listeningTypes,
            List<TypeStat> readingTypes,
            List<TypeStat> writingTypes
    ) {
        public boolean hasAnyMockScores() {
            return (topikIScores != null && !topikIScores.isEmpty())
                    || (topikIIScores != null && !topikIIScores.isEmpty());
        }
    }

    // ---- Public API ----

    public ProgressData load(Long userId) {
        List<MockScorePoint> mockScores = buildMockScores(userId);
        Map<String, List<MockBarStat>> barStats = buildMockBarStats(userId);
        List<TypeStat> all = buildTypeStats(userId);
        List<TypeStat> listening = all.stream().filter(t -> t.skill() == Skill.LISTENING).toList();
        List<TypeStat> reading   = all.stream().filter(t -> t.skill() == Skill.READING).toList();
        List<TypeStat> writing   = all.stream().filter(t -> t.skill() == Skill.WRITING).toList();
        return new ProgressData(mockScores, barStats.get("TOPIK_I"), barStats.get("TOPIK_II"), listening, reading, writing);
    }

    // ---- Biểu đồ Cột chồng Full Mock (Điểm cao nhất theo từng đề) ----

    private Map<String, List<MockBarStat>> buildMockBarStats(Long userId) {
        Map<String, List<MockBarStat>> map = new LinkedHashMap<>();
        map.put("TOPIK_I", new ArrayList<>());
        map.put("TOPIK_II", new ArrayList<>());
        if (userId == null) return map;

        List<ExamAttempt> attempts = examAttempts.findCompletedFullMockByUser(userId);
        if (attempts.isEmpty()) return map;

        Map<Long, List<ExamAttempt>> grouped = new LinkedHashMap<>();
        for (ExamAttempt a : attempts) {
            grouped.computeIfAbsent(a.getExam().getId(), k -> new ArrayList<>()).add(a);
        }

        var sentByPrompt = learnerExamService.sentToTeacherByPrompt(userId);

        for (Map.Entry<Long, List<ExamAttempt>> entry : grouped.entrySet()) {
            List<ExamAttempt> examAttemptsList = entry.getValue();
            int attemptCount = examAttemptsList.size();

            ExamAttempt bestAttempt = null;
            ExamScoreDTO bestScore = null;

            for (ExamAttempt a : examAttemptsList) {
                try {
                    LearnerExamService.Paper paper = learnerExamService.result(userId, a.getId());
                    var reviews = learnerExamService.writingReviews(userId, a.getId());
                    Map<String, String> credited = CheckedAnswers.read(a.getCreditedAnswersJson());
                    ExamScoreDTO score = ExamScoreCalculator.calculate(paper, reviews, credited, sentByPrompt);

                    if (bestScore == null || score.getTotalEarnedScore() > bestScore.getTotalEarnedScore()
                            || (Double.compare(score.getTotalEarnedScore(), bestScore.getTotalEarnedScore()) == 0
                            && (bestAttempt == null || a.getCompletedAt().isAfter(bestAttempt.getCompletedAt())))) {
                        bestAttempt = a;
                        bestScore = score;
                    }
                } catch (Exception ignored) {
                }
            }

            if (bestAttempt != null && bestScore != null) {
                var ex = bestAttempt.getExam();
                String trackStr = ex.getTrack() == Track.TOPIK_I ? "TOPIK_I" : "TOPIK_II";
                String shortName = shortenExamName(ex.getName(), ex.getTrack());
                String completedDate = bestAttempt.getCompletedAt() != null
                        ? bestAttempt.getCompletedAt().format(DATE_FMT) : "";
                double writingScore = bestScore.getWritingTotalScore() != null ? bestScore.getWritingTotalScore() : 0.0;

                MockBarStat stat = new MockBarStat(
                        bestAttempt.getId(),
                        ex.getId(),
                        ex.getName(),
                        shortName,
                        trackStr,
                        bestScore.getListenScore(),
                        bestScore.getReadScore(),
                        writingScore,
                        bestScore.getTotalEarnedScore(),
                        completedDate,
                        attemptCount
                );
                map.get(trackStr).add(stat);
            }
        }

        return map;
    }

    private static String shortenExamName(String name, Track track) {
        if (name == null || name.isBlank()) return "Đề thi";
        String trimmed = name.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("((?:Kỳ|Đề|Test)\\s*(?:số\\s*)?\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(trimmed);
        if (m.find()) {
            return m.group(1).trim();
        }
        if (trimmed.length() > 16) {
            return trimmed.substring(0, 14) + "…";
        }
        return trimmed;
    }

    // ---- Biểu đồ Full Mock cũ (giữ tương thích) ----

    private List<MockScorePoint> buildMockScores(Long userId) {
        // Dùng JOIN FETCH để tránh LazyInitializationException khi đọc exam.kind
        List<ExamAttempt> attempts = examAttempts.findCompletedFullMockByUser(userId);

        List<MockScorePoint> result = new ArrayList<>();
        int idx = 1;
        for (ExamAttempt a : attempts) {
            String label = "Full Mock " + idx++;
            String date = a.getCompletedAt().format(DATE_FMT);
            boolean hasScore = a.getTotal() > 0;
            result.add(new MockScorePoint(label, date, a.getCorrect(), a.getTotal(), hasScore));
        }
        return result;
    }

    // ---- Các dạng câu ----

    private List<TypeStat> buildTypeStats(Long userId) {
        // Lấy tất cả practice attempts đã hoàn thành của user
        List<PracticeAttempt> pas = practiceAttempts.findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId);

        // Map typeCode -> (totalDone, totalCorrect)
        Map<String, int[]> statsMap = new LinkedHashMap<>();
        for (PracticeAttempt pa : pas) {
            String code = pa.getTypeCode();
            statsMap.computeIfAbsent(code, k -> new int[2]);
            statsMap.get(code)[0] += pa.getTotal();
            statsMap.get(code)[1] += pa.getScore();
        }

        // Exam attempts cũng tính vào (theo typeCode của từng câu dùng score/total per question-type)
        // Lấy từ PracticeAttempt gắn với exam trước (practice đã đủ)

        List<QuestionTypeDef> defs = QuestionTypeCatalog.all();
        List<TypeStat> result = new ArrayList<>();

        for (QuestionTypeDef def : defs) {
            int[] s = statsMap.getOrDefault(def.code(), new int[]{0, 0});
            int done    = s[0];
            int correct = s[1];
            double acc  = done == 0 ? 0.0 : (double) correct / done;

            String practiceUrl = "/practice/prepare?typeCode=" + def.code();
            result.add(new TypeStat(
                    def.code(), def.name(), def.skill(), def.rangeLabel(),
                    done, correct, done, acc, practiceUrl
            ));
        }
        return result;
    }
}
