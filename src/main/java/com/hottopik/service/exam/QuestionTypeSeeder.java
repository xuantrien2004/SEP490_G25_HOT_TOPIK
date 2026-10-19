package com.hottopik.service.exam;

import com.hottopik.entity.exam.QuestionTypeEntity;
import com.hottopik.repository.exam.QuestionTypeRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Ghi 42 dạng câu vào bảng question_types để các module khác (lộ trình, cẩm nang) JOIN được. */
@Component
@Order(20)
public class QuestionTypeSeeder implements ApplicationRunner {

    private final QuestionTypeRepository repo;

    public QuestionTypeSeeder(QuestionTypeRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (QuestionTypeDef d : QuestionTypeCatalog.all()) {
            QuestionTypeEntity e = repo.findById(d.code()).orElseGet(QuestionTypeEntity::new);
            e.setCode(d.code());
            e.setTrack(d.track());
            e.setSkill(d.skill());
            e.setName(d.name());
            e.setRanges(d.rangeLabel());
            e.setZoneMin(d.zoneMin());
            e.setZoneMax(d.zoneMax());
            e.setRequiredCount(d.requiredCount());
            e.setInstructionKo(d.instructionKo());
            e.setSortOrder(d.sortOrder());
            e.setTemplateCode(QuestionTemplateCatalog.templateCode(d.code()));
            repo.save(e);
        }
    }
}
