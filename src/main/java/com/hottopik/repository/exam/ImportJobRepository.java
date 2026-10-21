package com.hottopik.repository.exam;

import com.hottopik.entity.exam.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
    Optional<ImportJob> findFirstBySourceIdOrderByIdDesc(Long sourceId);

    List<ImportJob> findBySourceIdOrderByIdDesc(Long sourceId);
}
