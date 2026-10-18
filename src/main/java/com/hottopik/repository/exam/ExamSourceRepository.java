package com.hottopik.repository.exam;

import com.hottopik.entity.exam.ExamSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamSourceRepository extends JpaRepository<ExamSource, Long> {
    List<ExamSource> findAllByOrderByIdDesc();
}
