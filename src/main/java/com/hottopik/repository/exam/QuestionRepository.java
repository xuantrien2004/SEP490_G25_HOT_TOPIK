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

    List<Question> findBySourceIdOrderBySkillAscOriginalNoAsc(Long sourceId);

    List<Question> findByContentHashIn(Collection<String> hashes);

    long countByStatus(QuestionStatus status);

    @Query("select q.typeCode, count(q) from Question q where q.status = com.hottopik.entity.exam.QuestionStatus.ACTIVE group by q.typeCode")
    List<Object[]> countActiveByType();

    @Query("select q.templateCode, q.typeCode from Question q where q.status = com.hottopik.entity.exam.QuestionStatus.ACTIVE")
    List<Object[]> activeTemplateCodes();
}
