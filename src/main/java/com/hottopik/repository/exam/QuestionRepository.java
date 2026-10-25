package com.hottopik.repository.exam;

import com.hottopik.entity.exam.Question;
import com.hottopik.entity.exam.QuestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {

    List<Question> findByTypeCodeAndStatus(String typeCode, QuestionStatus status);

    List<Question> findByTemplateCodeAndStatus(String templateCode, QuestionStatus status);

    @EntityGraph(attributePaths = "group")
    List<Question> findByTrackAndSkillAndTypeCodeAndStatus(com.hottopik.entity.exam.Track track,
                                                           com.hottopik.entity.exam.Skill skill,
                                                           String typeCode, QuestionStatus status);

    @EntityGraph(attributePaths = {"source", "group"})
    @Query("select q from Question q where q.track = :track and q.skill = :skill "
            + "and q.originalNo in :numbers and q.status = com.hottopik.entity.exam.QuestionStatus.ACTIVE "
            + "and q.source.status = com.hottopik.entity.exam.SourceStatus.ACTIVE "
            + "and q.source.assessment = false "
            + "and q.purpose <> com.hottopik.entity.exam.QuestionPurpose.ASSESSMENT")
    List<Question> findPracticeCandidates(com.hottopik.entity.exam.Track track,
                                          com.hottopik.entity.exam.Skill skill, Collection<Integer> numbers);

    @EntityGraph(attributePaths = {"source", "group"})
    List<Question> findBySourceIdOrderBySkillAscOriginalNoAsc(Long sourceId);

    List<Question> findByContentHashIn(Collection<String> hashes);

    long countByStatus(QuestionStatus status);

    @Query("select q.typeCode, count(q) from Question q where q.status = com.hottopik.entity.exam.QuestionStatus.ACTIVE group by q.typeCode")
    List<Object[]> countActiveByType();

    @Query("select q.templateCode, q.typeCode from Question q where q.status = com.hottopik.entity.exam.QuestionStatus.ACTIVE")
    List<Object[]> activeTemplateCodes();

    @Query("select q.typeCode, count(q) from Question q "
            + "where q.status = com.hottopik.entity.exam.QuestionStatus.ACTIVE "
            + "and q.purpose = com.hottopik.entity.exam.QuestionPurpose.ASSESSMENT "
            + "and q.source.status = com.hottopik.entity.exam.SourceStatus.ACTIVE "
            + "group by q.typeCode")
    List<Object[]> countActiveAssessmentByType();

    @EntityGraph(attributePaths = {"source", "group"})
    @Query("select q from Question q where q.typeCode = :typeCode "
            + "and q.status = com.hottopik.entity.exam.QuestionStatus.ACTIVE "
            + "and q.purpose = com.hottopik.entity.exam.QuestionPurpose.ASSESSMENT "
            + "and q.source.status = com.hottopik.entity.exam.SourceStatus.ACTIVE")
    List<Question> findAssessmentPool(@org.springframework.data.repository.query.Param("typeCode") String typeCode);

    @EntityGraph(attributePaths = {"source", "group"})
    @Query("select q from Question q where q.id in :ids")
    List<Question> findWithMediaByIdIn(@org.springframework.data.repository.query.Param("ids") Collection<Long> ids);

    @EntityGraph(attributePaths = {"source", "group"})
    @Query("select q from Question q where q.status = com.hottopik.entity.exam.QuestionStatus.ACTIVE "
            + "and q.purpose <> com.hottopik.entity.exam.QuestionPurpose.ASSESSMENT "
            + "and q.source.status = com.hottopik.entity.exam.SourceStatus.ACTIVE "
            + "order by q.id asc")
    List<Question> findPlacementDonors();
}
