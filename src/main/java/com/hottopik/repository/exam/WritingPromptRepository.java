package com.hottopik.repository.exam;

import com.hottopik.entity.exam.QuestionStatus;
import com.hottopik.entity.exam.WritingPrompt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WritingPromptRepository extends JpaRepository<WritingPrompt, Long> {
    @EntityGraph(attributePaths = "source")
    List<WritingPrompt> findByNoAndStatusOrderByIdDesc(int no, QuestionStatus status);

    List<WritingPrompt> findBySourceIdOrderByNoAsc(Long sourceId);

    List<WritingPrompt> findAllByOrderByNoAscIdDesc();
}
