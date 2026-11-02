package com.hottopik.service.exam;

import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.ProgramVersionItemRepository;
import com.hottopik.repository.exam.ProgramVersionRepository;
import com.hottopik.repository.exam.QuestionTemplateRepository;
import com.hottopik.repository.exam.StudyPlanRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bản lộ trình đầu tiên: mọi dạng Nghe và Đọc theo thứ tự thư viện khuôn. Viết không vào lịch ngày. */
@Component
@Order(25)
public class ProgramVersionSeeder implements ApplicationRunner {

    private final ProgramVersionRepository programs;
    private final ProgramVersionItemRepository items;
    private final QuestionTemplateRepository templates;
    private final JdbcTemplate jdbc;
    private final StudyPlanRepository plans;

    public ProgramVersionSeeder(ProgramVersionRepository programs, ProgramVersionItemRepository items,
                                QuestionTemplateRepository templates, JdbcTemplate jdbc, StudyPlanRepository plans) {
        this.programs = programs;
        this.items = items;
        this.templates = templates;
        this.jdbc = jdbc;
        this.plans = plans;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrateLegacyItems();
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

    /** Giữ thứ tự đã phát hành trong bảng cũ, không lấy cấu hình từ bản nháp hoặc thêm dạng mới. */
    void migrateLegacyItems() {
        boolean legacy = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            try (var tables = connection.getMetaData().getTables(null, null, null, new String[]{"TABLE"})) {
                while (tables.next()) {
                    if ("dbo".equalsIgnoreCase(tables.getString("TABLE_SCHEM"))
                            && "program_version_types".equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
                }
                return false;
            }
        }));
        if (!legacy) return;
        for (ProgramVersion program : programs.findAll()) {
            // Một bản đã có cấu hình mới phải được giữ nguyên, kể cả khi CM đã bỏ một số dạng.
            if (!items.findWithTemplate(program.getId()).isEmpty()) continue;
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT t.code FROM dbo.program_version_types p
                    JOIN dbo.question_types t ON t.id = p.question_type_id
                    WHERE p.program_version_id = ? ORDER BY p.order_no, t.id
                    """, program.getId());
            Map<String, QuestionTemplate> ordered = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String typeCode = row.get("code").toString();
                String code = QuestionTemplateCatalog.templateCode(typeCode);
                QuestionTemplate template = code == null ? null : templates.findByCode(code).orElse(null);
                if (template == null) throw new IllegalStateException("Không ánh xạ được dạng lộ trình cũ: " + typeCode);
                if (template.getSkill() != Skill.WRITING) ordered.putIfAbsent(code, template);
            }
            int order = 1;
            for (QuestionTemplate template : ordered.values()) {
                ProgramVersionItem item = new ProgramVersionItem();
                item.setProgram(program);
                item.setTemplate(template);
                item.setOrderNo(order++);
                items.save(item);
            }
            if (ordered.isEmpty()) continue;
            for (StudyPlan plan : plans.findByProgramId(program.getId())) {
                int[] reviews = ProgramRules.reviews(program.getReviewOffsets());
                int[] phases = ProgramRules.phases(program.getPhaseSplit());
                RoadmapSketch.Result previous = RoadmapSketch.build(List.of(), plan.getTotalDays(),
                        plan.getItemsPerDay(), reviews, program.getSessionsPerType(), phases);
                List<RoadmapSketch.TypeRef> types = ordered.values().stream()
                        .filter(t -> t.getTrack() == plan.getTrack())
                        .map(t -> new RoadmapSketch.TypeRef(t.getCode(), t.getName())).toList();
                RoadmapSketch.Result next = RoadmapSketch.build(types, plan.getTotalDays(), plan.getItemsPerDay(),
                        reviews, program.getSessionsPerType(), phases);
                plan.setDoneKeys(LearnerRoadmapService.remapDoneKeys(plan, previous, next));
            }
        }
    }
}
