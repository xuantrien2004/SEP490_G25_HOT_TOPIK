package com.hottopik.repository.exam;

import com.hottopik.entity.exam.ProgramStatus;
import com.hottopik.entity.exam.ProgramVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProgramVersionRepository extends JpaRepository<ProgramVersion, Long> {

    List<ProgramVersion> findAllByOrderByIdDesc();

    Optional<ProgramVersion> findFirstByStatusOrderByIdDesc(ProgramStatus status);

    Optional<ProgramVersion> findFirstByStatusInOrderByIdDesc(Collection<ProgramStatus> statuses);

    boolean existsByStatusIn(Collection<ProgramStatus> statuses);

    List<ProgramVersion> findByStatus(ProgramStatus status);
}
