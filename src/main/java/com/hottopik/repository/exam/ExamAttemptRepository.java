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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExamAttempt a where a.completedAt is not null and exists (select i.id from ExamItem i where i.exam = a.exam and i.question.id = :qid) order by a.id")
    List<ExamAttempt> findCompletedWithQuestion(@Param("qid") Long qid);
    @Query("select a from ExamAttempt a where a.completedAt is not null and a.answersJson like :key")
    List<ExamAttempt> findCompletedWriting(@Param("key") String key);
    List<ExamAttempt> findByUserIdOrderByStartedAtDescIdDesc(Long userId);

    @Query("select a from ExamAttempt a join fetch a.exam e where a.userId = :userId and a.completedAt is not null and e.kind = com.hottopik.entity.exam.ExamKind.FULL order by a.completedAt asc")
    List<ExamAttempt> findCompletedFullMockByUser(@Param("userId") Long userId);

    boolean existsByExamId(Long examId);

    /*
     * Khóa cùng lượt làm khi save/submit/play để các yêu cầu đồng thời không ghi đè trạng thái; query kèm userId chống đọc/sửa lượt người khác.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExamAttempt a where a.id = :id and a.userId = :userId")
    Optional<ExamAttempt> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
