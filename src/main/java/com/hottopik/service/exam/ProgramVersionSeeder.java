package com.hottopik.service.exam;

import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.ProgramVersionItemRepository;
import com.hottopik.repository.exam.ProgramVersionRepository;
import com.hottopik.repository.exam.QuestionTemplateRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Bản lộ trình đầu tiên: mọi dạng Nghe và Đọc theo thứ tự thư viện khuôn. Viết không vào lịch ngày. */
@Component
@Order(20)
public class ProgramVersionSeeder implements ApplicationRunner {

    private final ProgramVersionRepository programs;
    private final ProgramVersionItemRepository items;
    private final QuestionTemplateRepository templates;

    public ProgramVersionSeeder(ProgramVersionRepository programs, ProgramVersionItemRepository items,
                                QuestionTemplateRepository templates) {
        this.programs = programs;
        this.items = items;
        this.templates = templates;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (programs.count() > 0) return;
        ProgramVersion program = new ProgramVersion();
        program.setVersionCode("v1");
        program.setStatus(ProgramStatus.PUBLISHED);
        program.setNote("Bản khởi tạo. Học viên học Nghe và Đọc theo thứ tự này. Viết luyện ở mục riêng.");
        program.setReviewOffsets("2,7,14");
        program.setPhaseSplit("40,40,20");
        program.setSessionsPerType(4);
        program.setQuestionsPerSession(8);
        program.setWeeklyReviewQuestions(20);
        // System-created versions have no user account to reference.
        program.setCreatedBy(null);
        program.setPublishedAt(LocalDateTime.now());
        programs.save(program);

        int order = 1;
        for (QuestionTemplateCatalog.Seed seed : QuestionTemplateCatalog.all()) {
            if (seed.skill() == Skill.WRITING) continue;
            QuestionTemplate template = templates.findByCode(seed.code()).orElse(null);
            if (template == null || !template.isAllowRoadmap()) continue;
            ProgramVersionItem item = new ProgramVersionItem();
            item.setProgram(program);
            item.setTemplate(template);
            item.setOrderNo(order++);
            items.save(item);
        }
    }
}
