package com.hottopik.repository.exam;

import com.hottopik.entity.exam.QuestionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionGroupRepository extends JpaRepository<QuestionGroup, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select g from QuestionGroup g where g.id=:id")
    java.util.Optional<QuestionGroup> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
}
