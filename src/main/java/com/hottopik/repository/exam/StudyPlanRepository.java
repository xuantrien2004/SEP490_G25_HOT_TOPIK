package com.hottopik.repository.exam;

import com.hottopik.entity.exam.StudyPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

    Optional<StudyPlan> findByOwnerEmail(String ownerEmail);

    /** Khóa dòng lịch đến hết transaction: hai lần đánh dấu gần nhau không ghi đè doneKeys của nhau. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from StudyPlan p where p.ownerEmail = :owner")
    Optional<StudyPlan> lockByOwnerEmail(@Param("owner") String owner);
}
