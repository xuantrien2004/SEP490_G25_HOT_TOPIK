package com.hottopik.repository.exam;

import com.hottopik.entity.exam.ExamAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {
    List<ExamAttempt> findByUserIdOrderByStartedAtDescIdDesc(Long userId);

    boolean existsByExamId(Long examId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExamAttempt a where a.id = :id and a.userId = :userId")
    Optional<ExamAttempt> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
