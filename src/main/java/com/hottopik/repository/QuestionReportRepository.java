package com.hottopik.repository;

import com.hottopik.entity.QuestionReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionReportRepository extends JpaRepository<QuestionReport, Long> {
    List<QuestionReport> findAllByOrderByCreatedAtDesc();
    List<QuestionReport> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<QuestionReport> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from QuestionReport r where r.id = :id")
    Optional<QuestionReport> findForUpdate(@Param("id") Long id);
}
