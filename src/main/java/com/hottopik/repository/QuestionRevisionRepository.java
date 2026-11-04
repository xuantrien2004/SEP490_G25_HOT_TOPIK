package com.hottopik.repository;
import com.hottopik.entity.QuestionRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface QuestionRevisionRepository extends JpaRepository<QuestionRevision,Long> {
    Optional<QuestionRevision> findTopByReportIdOrderByIdDesc(Long reportId);
}
