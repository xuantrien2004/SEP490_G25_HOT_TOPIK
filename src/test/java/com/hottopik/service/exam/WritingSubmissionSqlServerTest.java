package com.hottopik.service.exam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hottopik.dto.UserDTO;
import com.hottopik.repository.exam.WritingPromptRepository;
import com.hottopik.service.WritingSubmissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Chỉ chạy khi yêu cầu rõ ràng; không nạp application/seeder/schema patch, mọi bài thử đều rollback. */
@EnabledIfSystemProperty(named="hottopik.verify-sqlserver-writing",matches="true")
@SpringJUnitConfig(WritingSubmissionSqlServerTest.Config.class)
@Transactional
class WritingSubmissionSqlServerTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages="com.hottopik.repository")
    @Import(WritingSubmissionService.class)
    static class Config {
        @Bean DataSource dataSource() throws Exception {
            var p=new Properties();
            try(var in=Config.class.getResourceAsStream("/application.properties")){p.load(in);}
            return new DriverManagerDataSource(p.getProperty("spring.datasource.url"),p.getProperty("spring.datasource.username"),p.getProperty("spring.datasource.password"));
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(ds);
            factory.setPackagesToScan("com.hottopik.entity");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.dialect","org.hibernate.dialect.SQLServerDialect"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory){return new JpaTransactionManager(factory);}
        @Bean ObjectMapper objectMapper(){return new ObjectMapper();}
        @Bean JdbcTemplate jdbcTemplate(DataSource ds){return new JdbcTemplate(ds);}
    }
    @Autowired WritingSubmissionService writing;
    @Autowired WritingPromptRepository prompts;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;
    private int countBefore;

    @org.springframework.test.context.transaction.BeforeTransaction
    void rememberSubmissionCount(){countBefore=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.writing_submissions",Integer.class);}

    @org.springframework.test.context.transaction.AfterTransaction
    void verifyRollback(){assertEquals(countBefore,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.writing_submissions",Integer.class));}

    @Test void realSqlServerAcceptsPracticeSubmissionsAndTheirUpdates() {
        var row=jdbc.queryForMap("SELECT TOP(1) id,email,display_name FROM dbo.users WHERE role='STUDENT' AND status='ACTIVE' ORDER BY id");
        var learner=new UserDTO();learner.setId(((Number)row.get("id")).longValue());learner.setEmail((String)row.get("email"));
        learner.setFullName((String)row.get("display_name"));learner.setRole("STUDENT");learner.setVip(true);
        for(int no:List.of(51,52,53,54)) {
            var prompt=prompts.findByNoAndStatusOrderByIdDesc(no,com.hottopik.entity.exam.QuestionStatus.ACTIVE).stream().findFirst().orElseThrow();
            var self=writing.submit(learner,prompt.getId(),"",false);em.flush();em.clear();
            assertEquals("submitted",writing.owned(self.getId(),learner.getId()).getStatus());
            var teacher=writing.submit(learner,prompt.getId(),no<=52?"㉠ 답안\n㉡ ":"시험 답안",true);em.flush();em.clear();
            assertEquals("waiting",writing.owned(teacher.getId(),learner.getId()).getStatus());
            assertEquals("WAITING_MENTOR",jdbc.queryForObject("SELECT status FROM dbo.writing_submissions WHERE id=?",String.class,Long.valueOf(teacher.getId())));
        }
    }
}
