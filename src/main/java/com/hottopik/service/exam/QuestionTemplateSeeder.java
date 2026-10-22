package com.hottopik.service.exam;

import com.hottopik.entity.exam.QuestionTemplate;
import com.hottopik.entity.exam.TemplateStatus;
import com.hottopik.repository.exam.QuestionTemplateRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seed khuôn hệ thống. Chạy lại không tạo trùng và không đè bản admin đã sửa. */
@Component
@Order(10)
public class QuestionTemplateSeeder implements ApplicationRunner {

    private final QuestionTemplateRepository templates;

    public QuestionTemplateSeeder(QuestionTemplateRepository templates) {
        this.templates = templates;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (QuestionTemplateCatalog.Seed seed : QuestionTemplateCatalog.all()) {
            QuestionTemplate row = templates.findByCode(seed.code()).orElse(null);
            if (row != null && row.isCustomized()) continue;
            if (row == null) {
                row = new QuestionTemplate();
                row.setCode(seed.code());
                row.setCreatedBy("Hệ thống");
                row.setSystemOwned(true);
                row.setCustomized(false);
                row.setVersion(1);
            }
            apply(row, seed);
            row.setUpdatedBy("Hệ thống");
            templates.save(row);
        }
    }

    static void apply(QuestionTemplate row, QuestionTemplateCatalog.Seed seed) {
        row.setName(seed.name());
        row.setDescription(seed.description());
        row.setTrack(seed.track());
        row.setSkill(seed.skill());
        row.setZoneMin(seed.zoneMin());
        row.setZoneMax(seed.zoneMax());
        row.setTags(seed.tags());
        row.setDisplayMode(seed.displayMode());
        row.setAnswerType(seed.answerType());
        row.setPromptRequired(true);
        row.setAudioScope(seed.audioScope());
        row.setAudioRequired(seed.audioRequired());
        row.setImageScope(seed.imageScope());
        row.setImageRequired(seed.imageRequired());
        row.setPassageScope(seed.passageScope());
        row.setPassageRequired(seed.passageRequired());
        row.setScriptScope(seed.scriptScope());
        row.setOptionCount(seed.optionCount());
        row.setChildMin(seed.childMin());
        row.setChildMax(seed.childMax());
        row.setGrading(seed.grading());
        row.setDefaultPoints(null);
        row.setMinChars(seed.minChars());
        row.setMaxChars(seed.maxChars());
        row.setRubric(seed.rubric());
        row.setAllowMiniTest(true);
        row.setAllowPractice(true);
        row.setAllowRoadmap(true);
        row.setStatus(TemplateStatus.ACTIVE);
        row.setSystemOwned(true);
    }
}
