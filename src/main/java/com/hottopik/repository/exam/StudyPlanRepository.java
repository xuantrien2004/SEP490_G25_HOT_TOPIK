package com.hottopik.repository.exam;

import com.hottopik.entity.exam.StudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

    Optional<StudyPlan> findByOwnerEmail(String ownerEmail);
}
