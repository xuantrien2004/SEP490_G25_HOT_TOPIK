package com.hottopik.repository.exam;

import com.hottopik.entity.exam.PracticeAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PracticeAttemptRepository extends JpaRepository<PracticeAttempt, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from PracticeAttempt a where a.completedAt is not null and concat(concat(',', a.questionIds), ',') like concat(concat('%,', :qid), ',%') order by a.id")
    List<PracticeAttempt> findCompletedWithQuestion(@org.springframework.data.repository.query.Param("qid") String qid);
    List<PracticeAttempt> findByUserIdAndTypeCodeAndCompletedAtIsNotNullOrderByCompletedAtDesc(Long userId, String typeCode);
    List<PracticeAttempt> findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(Long userId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from PracticeAttempt a where a.id = :id and a.userId = :userId")
    Optional<PracticeAttempt> findOwnedForUpdate(@org.springframework.data.repository.query.Param("id") Long id, @org.springframework.data.repository.query.Param("userId") Long userId);
    Optional<PracticeAttempt> findByIdAndUserId(Long id, Long userId);
}
