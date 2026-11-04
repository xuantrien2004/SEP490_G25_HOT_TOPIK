package com.hottopik.service;

import com.hottopik.entity.exam.ExamAttempt;
import com.hottopik.entity.exam.PracticeAttempt;
import com.hottopik.entity.exam.Skill;
import com.hottopik.repository.exam.ExamAttemptRepository;
import com.hottopik.repository.exam.PracticeAttemptRepository;
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
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ProgressService(ExamAttemptRepository examAttempts,
                           PracticeAttemptRepository practiceAttempts) {
        this.examAttempts = examAttempts;
        this.practiceAttempts = practiceAttempts;
    }

    // ---- DTOs ----

    public record MockScorePoint(String label, String date, int score, int total, boolean hasScore) {}

    public record TypeStat(
            String code, String name, Skill skill, String rangeLabel,
            int done, int correct, int total,
            double accuracy,        // 0..1
            String practiceUrl
    ) {}

    public record ProgressData(
            List<MockScorePoint> mockScores,
            List<TypeStat> listeningTypes,
            List<TypeStat> readingTypes,
            List<TypeStat> writingTypes
    ) {}

    // ---- Public API ----

    public ProgressData load(Long userId) {
        List<MockScorePoint> mockScores = buildMockScores(userId);
        List<TypeStat> all = buildTypeStats(userId);
        List<TypeStat> listening = all.stream().filter(t -> t.skill() == Skill.LISTENING).toList();
        List<TypeStat> reading   = all.stream().filter(t -> t.skill() == Skill.READING).toList();
        List<TypeStat> writing   = all.stream().filter(t -> t.skill() == Skill.WRITING).toList();
        return new ProgressData(mockScores, listening, reading, writing);
    }

    // ---- Biểu đồ Full Mock ----

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
