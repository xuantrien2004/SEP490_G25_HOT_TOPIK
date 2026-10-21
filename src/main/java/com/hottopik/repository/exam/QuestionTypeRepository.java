package com.hottopik.repository.exam;

import com.hottopik.entity.exam.QuestionTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionTypeRepository extends JpaRepository<QuestionTypeEntity, String> {
}
