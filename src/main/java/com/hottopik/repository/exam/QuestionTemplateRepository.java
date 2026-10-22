package com.hottopik.repository.exam;

import com.hottopik.entity.exam.QuestionTemplate;
import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionTemplateRepository extends JpaRepository<QuestionTemplate, Long> {

    Optional<QuestionTemplate> findByCode(String code);

    List<QuestionTemplate> findAllByOrderByCodeAsc();

    List<QuestionTemplate> findByTrackAndSkillOrderByCodeAsc(Track track, Skill skill);
}
