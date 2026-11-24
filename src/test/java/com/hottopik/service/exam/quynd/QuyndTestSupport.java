package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.ExamSource;
import com.hottopik.entity.exam.Track;
import com.hottopik.service.exam.Actor;
import com.hottopik.service.exam.ExamDomainException;
import com.hottopik.service.exam.ExamImportService;
import com.hottopik.service.exam.ExamImportService.CardForm;
import com.hottopik.service.exam.ExamImportService.SourceForm;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phần dùng chung cho unit test của QuyND (Report 5, UTC12–UTC27).
 * Chạy trên H2 in-memory (profile "test"); mỗi test tự rollback.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
abstract class QuyndTestSupport {

    static final Actor CONTENT = new Actor("Lê Thu Hà", Actor.ROLE_CONTENT, 2L);
    static final Actor ADMIN = new Actor("Quản trị viên", Actor.ROLE_ADMIN, 1L);

    /** 2 câu mỗi dạng: Nghe 10 (I-N1, I-N2, I-N3, I-N4, I-N6), Đọc 10 (I-D1 … I-D5). */
    static final Map<String, Integer> DEFAULT_PLACEMENT = Map.of(
            "I-N1", 2, "I-N2", 2, "I-N3", 2, "I-N4", 2, "I-N6", 2,
            "I-D1", 2, "I-D2", 2, "I-D3", 2, "I-D4", 2, "I-D5", 2);

    @Autowired ExamImportService imports;
    @Autowired com.hottopik.service.AdminSystemService audit;
    @Autowired com.hottopik.repository.exam.ExamSourceRepository bankSources;
    @Autowired com.hottopik.repository.exam.QuestionGroupRepository bankGroups;
    @Autowired com.hottopik.repository.exam.QuestionRepository bankQuestions;

    /**
     * Ngân hàng đánh giá cho bài kiểm tra trình độ: {@code perType} câu ACTIVE, purpose ASSESSMENT
     * cho mỗi dạng trong DEFAULT_PLACEMENT. Đáp án đúng xoay vòng 1–4.
     */
    void assessmentBank(int perType) {
        ExamSource source = new ExamSource();
        source.setName("Ngân hàng đánh giá kiểm thử");
        source.setTrack(Track.TOPIK_I);
        source.setExpectedCount(20);
        source.setAssessment(true);
        source.setCopyrightNote("Câu kiểm thử nội bộ");
        source.setStatus(com.hottopik.entity.exam.SourceStatus.ACTIVE);
        bankSources.save(source);
        int n = 0;
        for (String code : DEFAULT_PLACEMENT.keySet()) {
            com.hottopik.service.exam.QuestionTypeDef type = com.hottopik.service.exam.QuestionTypeCatalog.byCode(code).orElseThrow();
            for (int i = 0; i < perType; i++) {
                n++;
                com.hottopik.entity.exam.QuestionGroup group = new com.hottopik.entity.exam.QuestionGroup();
                group.setSource(source);
                group.setTypeCode(code);
                group.setSkill(type.skill());
                group.setSize(1);
                group.setScriptKo(type.skill() == com.hottopik.entity.exam.Skill.LISTENING ? "대본 " + n : null);
                bankGroups.save(group);
                com.hottopik.entity.exam.Question q = new com.hottopik.entity.exam.Question();
                q.setSource(source);
                q.setGroup(group);
                q.setTypeCode(code);
                q.setTrack(Track.TOPIK_I);
                q.setSkill(type.skill());
                q.setOriginalNo(type.numbers().get(0));
                q.setPromptKo("문제 " + n);
                q.setOption1("A" + n);
                q.setOption2("B" + n);
                q.setOption3("C" + n);
                q.setOption4("D" + n);
                q.setAnswerIndex(n % 4 + 1);
                q.setExplanationVi("Giải thích câu " + n);
                q.setPurpose(com.hottopik.entity.exam.QuestionPurpose.ASSESSMENT);
                q.setStatus(com.hottopik.entity.exam.QuestionStatus.ACTIVE);
                q.setContentHash("kiem-thu-placement-" + n + "-" + System.nanoTime());
                q.setCode("UT" + (System.nanoTime() % 1_000_000_000L) + n);
                bankQuestions.save(q);
            }
        }
    }

    ExamSource newSource(Track track) {
        return imports.createSource(new SourceForm("Nguồn kiểm thử " + track.getLabel(), track, 2024, 1, null,
                false, "Đề mẫu nội bộ dùng để kiểm thử", null), CONTENT);
    }

    /** Một câu Đọc I-D1 ở ô {@code no}, đủ 4 phương án, đáp án 2, có giải thích. */
    static CardForm readingCard(Integer no) {
        return choiceCard(null, "I-D1", no, "giải thích");
    }

    static CardForm choiceCard(String cardId, String typeCode, Integer no, String explanation) {
        return new CardForm(cardId, null, null, null, null, null, null,
                List.of("다음은 무엇에 대한 내용입니까?"), List.of("A"), List.of("B"), List.of("C"), List.of("D"),
                List.of("2"), List.of(explanation), null, null, null, null, null, typeCode,
                no == null ? null : List.of(no));
    }

    /** Câu Viết 51 (khuôn II-V1); passage = null để thử thiếu đoạn văn dùng chung. */
    static CardForm writing51(String passage) {
        return new CardForm(null, null, null, null, passage, null, null,
                List.of("( ㉠ ) ( ㉡ )"), null, null, null, null, null, null,
                List.of("참가할 수 있습니다"), List.of("신청하시기 바랍니다"), List.of("㉠ 참가할 수 있습니다 ㉡ 신청하시기 바랍니다"),
                List.of("Đúng văn phong trang trọng"), null, "II-V1", List.of(51));
    }

    /** Dòng nhật ký hệ thống mới nhất (AdminSystemService ghi dòng mới lên đầu danh sách). */
    com.hottopik.dto.AuditLogDTO lastAudit() {
        return audit.getAuditLogs().get(0);
    }

    int auditCount() {
        return audit.getAuditLogs().size();
    }

    static void expectError(String message, Executable call) {
        ExamDomainException e = assertThrows(ExamDomainException.class, call);
        assertEquals(message, e.getMessage());
    }
}
