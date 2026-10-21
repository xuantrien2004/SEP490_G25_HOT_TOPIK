package com.hottopik.repository.exam;

import com.hottopik.entity.exam.Exam;
import com.hottopik.entity.exam.ExamKind;
import com.hottopik.entity.exam.ExamStatus;
import com.hottopik.entity.exam.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findAllByOrderByIdDesc();

    /** Loads every association rendered by the exam library while Open Session in View is disabled. */
    @EntityGraph(attributePaths = {"source", "parent", "items", "writing51", "writing52", "writing53", "writing54"})
    @Query("select distinct e from Exam e order by e.id desc")
    List<Exam> findAllForLibrary();

    @EntityGraph(attributePaths = {
            "source", "parent", "items", "items.question", "items.question.group", "items.question.source",
            "writing51", "writing51.source", "writing52", "writing52.source",
            "writing53", "writing53.source", "writing54", "writing54.source"
    })
    @Query("select distinct e from Exam e where e.id = :id")
    Optional<Exam> findForComposerById(@Param("id") Long id);

    List<Exam> findByParentId(Long parentId);

    long countByStatus(ExamStatus status);

    @Query("select coalesce(max(e.seqNo), 0) from Exam e where e.track = ?1 and e.kind = ?2")
    int maxSeqNo(Track track, ExamKind kind);
}
