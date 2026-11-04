package com.hottopik.repository.exam;

import com.hottopik.entity.exam.QuestionStatus;
import com.hottopik.entity.exam.WritingPrompt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WritingPromptRepository extends JpaRepository<WritingPrompt, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from WritingPrompt p where p.id = :id")
    java.util.Optional<WritingPrompt> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    @EntityGraph(attributePaths = "source")
    List<WritingPrompt> findByNoAndStatusOrderByIdDesc(int no, QuestionStatus status);

    @EntityGraph(attributePaths = "source")
    List<WritingPrompt> findBySourceIdOrderByNoAsc(Long sourceId);

    @EntityGraph(attributePaths = "source")
    List<WritingPrompt> findAllByOrderByNoAscIdDesc();
}
