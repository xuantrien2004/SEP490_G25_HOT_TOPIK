package com.hottopik.repository.exam;

import com.hottopik.entity.exam.PracticeAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PracticeAttemptRepository extends JpaRepository<PracticeAttempt, Long> {
    List<PracticeAttempt> findByUserIdAndTypeCodeAndCompletedAtIsNotNullOrderByCompletedAtDesc(Long userId, String typeCode);
    List<PracticeAttempt> findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(Long userId);
    Optional<PracticeAttempt> findByIdAndUserId(Long id, Long userId);
}
