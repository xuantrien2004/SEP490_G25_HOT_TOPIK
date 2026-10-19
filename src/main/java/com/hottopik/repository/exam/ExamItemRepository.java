package com.hottopik.repository.exam;

import com.hottopik.entity.exam.ExamItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ExamItemRepository extends JpaRepository<ExamItem, Long> {

    @Query("select i.exam.id, i.exam.name, i.exam.status from ExamItem i where i.question.id = ?1")
    List<Object[]> findExamUsages(Long questionId);
}
