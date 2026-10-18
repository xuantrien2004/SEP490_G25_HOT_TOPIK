package com.hottopik.repository.exam;

import com.hottopik.entity.exam.Exam;
import com.hottopik.entity.exam.ExamKind;
import com.hottopik.entity.exam.ExamStatus;
import com.hottopik.entity.exam.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findAllByOrderByIdDesc();

    List<Exam> findByParentId(Long parentId);

    long countByStatus(ExamStatus status);

    @Query("select coalesce(max(e.seqNo), 0) from Exam e where e.track = ?1 and e.kind = ?2")
    int maxSeqNo(Track track, ExamKind kind);
}
