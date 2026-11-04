package com.hottopik.service.exam;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.WritingPrompt;
import com.hottopik.repository.exam.WritingPromptRepository;
import com.hottopik.service.WritingSubmissionService;
import com.hottopik.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:writing-schema-compat;MODE=MSSQLServer;DATABASE_TO_UPPER=FALSE;INIT=CREATE SCHEMA IF NOT EXISTS dbo;DB_CLOSE_DELAY=-1",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WritingSubmissionSchemaCompatibilityTest {
    @Autowired WritingSubmissionService writing;
    @Autowired WritingPromptRepository prompts;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired com.hottopik.repository.UserRepository accounts;

    UserDTO user(long id, String role) {
        var user = new UserDTO(); user.setRole(role); user.setVip(true);
        user.setEmail("writing-schema-" + id + "@example.com"); user.setFullName("Kiểm thử cấu trúc Viết");
        var account = com.hottopik.entity.User.builder().email(user.getEmail()).displayName(user.getFullName())
                .passwordHash("test-only").role(role).status("ACTIVE").build();
        user.setId(accounts.saveAndFlush(account).getId());
        return user;
    }

    WritingPrompt prompt(int no) {
        var p = new WritingPrompt(); p.setCode("S" + UUID.randomUUID().toString().substring(0,18));
        p.setNo(no); p.setPromptKo("Đề bài " + no); p.setModelAnswer(""); p.setKeys1("답안"); p.setKeys2("답안");
        return prompts.saveAndFlush(p);
    }

    // Mô phỏng đúng các cột bắt buộc và CHECK đã đọc từ dbo.writing_submissions trong SQL Server.
    @Test
    @Sql(statements = {
            "ALTER TABLE dbo.writing_submissions ADD COLUMN IF NOT EXISTS user_id BIGINT NOT NULL",
            "ALTER TABLE dbo.writing_submissions ADD COLUMN IF NOT EXISTS prompt_id BIGINT NOT NULL",
            "ALTER TABLE dbo.writing_submissions ADD COLUMN IF NOT EXISTS mode NVARCHAR(10) NOT NULL",
            "ALTER TABLE dbo.writing_submissions ADD CONSTRAINT ck_writing_compat_mode CHECK (mode IN ('EXAM','PRACTICE'))",
            "ALTER TABLE dbo.writing_submissions ADD CONSTRAINT ck_writing_compat_status CHECK (status IN ('FAILED','GRADED','WAITING_MENTOR','GRADING','DRAFT'))",
            "ALTER TABLE dbo.writing_submissions ADD CONSTRAINT fk_writing_compat_user FOREIGN KEY (user_id) REFERENCES PUBLIC.users(id)",
            "ALTER TABLE dbo.writing_submissions ADD CONSTRAINT fk_writing_compat_prompt FOREIGN KEY (prompt_id) REFERENCES dbo.writing_prompts(id)"
    })
    void submissionAndTeacherWorkflowRespectExistingSqlServerColumnsAndChecks() throws Exception {
        var learner = user(88301, "STUDENT"); var teacher = user(88302, "MENTOR");
        var shortPrompt = prompt(51); var essay = prompt(53);
        mvc.perform(post("/writing/submit").param("promptId", shortPrompt.getId().toString()).param("answer", "")
                .sessionAttr(AuthHelper.SESSION_KEY, learner)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/writing/feedback?id=*"));
        mvc.perform(post("/writing/submit-mentor").param("promptId", essay.getId().toString()).param("answer", "학생 답안")
                .sessionAttr(AuthHelper.SESSION_KEY, learner)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/writing/feedback?id=*"));
        em.flush(); em.clear();
        var saved = writing.getSubmissionsByLearner(learner.getId()); assertEquals(2, saved.size());
        for (var s : saved) {
            var row = jdbc.queryForMap("SELECT user_id, learner_id, prompt_id, mode, status FROM dbo.writing_submissions WHERE id = ?", Long.valueOf(s.getId()));
            assertEquals(learner.getId(), row.get("user_id")); assertEquals(learner.getId(), row.get("learner_id"));
            assertEquals(s.getPromptId(), row.get("prompt_id")); assertEquals("PRACTICE", row.get("mode"));
            assertEquals(s.getQuestionNo() == 53 ? "WAITING_MENTOR" : "DRAFT", row.get("status"));
        }
        var examSubmission = writing.submitExam(learner, essay.getId(), "학생 시험 답안", 987654L, "Đề thi Viết");
        em.flush();
        assertEquals("EXAM", jdbc.queryForObject("SELECT mode FROM dbo.writing_submissions WHERE id = ?", String.class, Long.valueOf(examSubmission.getId())));
        var s = saved.stream().filter(row -> row.getQuestionNo() == 53).findFirst().orElseThrow();
        assertTrue(writing.claimSubmission(s.getId(), teacher)); em.flush();
        assertEquals("GRADING", jdbc.queryForObject("SELECT status FROM dbo.writing_submissions WHERE id = ?", String.class, Long.valueOf(s.getId())));
        assertTrue(writing.gradeSubmission(s.getId(), teacher, Map.of(), 20, "Nhận xét ban đầu", null, null, null)); em.flush();
        assertEquals("GRADED", jdbc.queryForObject("SELECT status FROM dbo.writing_submissions WHERE id = ?", String.class, Long.valueOf(s.getId())));
        assertTrue(writing.appealSubmission(s.getId(), learner.getId(), "Xin kiểm tra lại bài viết.")); em.flush();
        assertEquals("WAITING_MENTOR", jdbc.queryForObject("SELECT status FROM dbo.writing_submissions WHERE id = ?", String.class, Long.valueOf(s.getId())));
        assertTrue(writing.claimSubmission(s.getId(), teacher));
        assertTrue(writing.gradeSubmission(s.getId(), teacher, Map.of(), null, null, null, "uphold", "Điểm chấm phù hợp."));
        em.flush(); em.clear(); assertEquals("graded", writing.owned(s.getId(), learner.getId()).getStatus());
    }
}
