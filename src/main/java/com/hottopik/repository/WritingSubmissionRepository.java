package com.hottopik.repository;

import com.hottopik.entity.WritingSubmission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface WritingSubmissionRepository extends JpaRepository<WritingSubmission, Long> {
    @Query("select s from WritingSubmission s where s.payload like :key")
    List<WritingSubmission> findByPayloadKey(@Param("key") String key);
    List<WritingSubmission> findByLearnerId(Long learnerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WritingSubmission s where s.id = :id")
    Optional<WritingSubmission> findForUpdate(@Param("id") Long id);
}
