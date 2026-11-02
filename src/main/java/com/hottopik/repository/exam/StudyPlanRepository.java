package com.hottopik.repository.exam;

import com.hottopik.entity.exam.StudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

    Optional<StudyPlan> findByOwnerEmail(String ownerEmail);

    List<StudyPlan> findByProgramId(Long programId);
}
