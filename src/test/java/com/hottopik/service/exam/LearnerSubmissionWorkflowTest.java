package com.hottopik.service.exam;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.*;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.*;
import com.hottopik.repository.exam.*;
import com.hottopik.service.*;
import com.hottopik.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.jpa.open-in-view=false","logging.level.org.hibernate.SQL=WARN"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LearnerSubmissionWorkflowTest {
    @Autowired WritingSubmissionService writing;
    @Autowired WritingSubmissionRepository submissions;
    @Autowired WritingPromptRepository prompts;
    @Autowired QuestionRepository questions;
    @Autowired PracticeAttemptRepository practices;
    @Autowired ExamAttemptRepository attempts;
    @Autowired ExamRepository exams;
    @Autowired UserNotificationRepository notifications;
    @Autowired QuestionReportService reports;
    @Autowired QuestionCorrectionService corrections;
    @Autowired QuestionBankService bank;
    @Autowired PracticeService practice;
    @Autowired LearnerExamService learnerExams;
    @Autowired ExamImportService imports;
    @Autowired QuestionTemplateRepository templates;
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager em;
    private static final Actor CM=new Actor("Content Manager",Actor.ROLE_CONTENT);
    UserDTO user(long id,String role,boolean vip){UserDTO u=new UserDTO();u.setId(id);u.setEmail("workflow"+id+"@example.com");u.setFullName("Người kiểm thử "+id);u.setRole(role);u.setVip(vip);
        em.createNativeQuery("INSERT INTO users (id,email,password_hash,display_name,role,status,timezone,coin_balance,daily_goal_minutes,studied_days,failed_login_count,created_at,updated_at) SELECT ?1,?2,'test-only',?3,?4,'ACTIVE','Asia/Ho_Chi_Minh',0,20,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM users WHERE id=?1)")
                .setParameter(1,id).setParameter(2,u.getEmail()).setParameter(3,u.getFullName()).setParameter(4,role).executeUpdate();
        return u;}
    WritingPrompt prompt(int no){WritingPrompt p=new WritingPrompt();p.setCode("T"+UUID.randomUUID().toString().substring(0,18));p.setNo(no);p.setPromptKo("문제 "+UUID.randomUUID());p.setKeys1("답 첫째");p.setKeys2("답 둘째");p.setModelAnswer("모범 답안");return prompts.saveAndFlush(p);}
    Question question(){return questions.findAll().stream().filter(q->q.getGroup()!=null).findFirst().orElseThrow();}
    PracticeAttempt attempt(Question q,long uid,Integer selected,boolean complete){PracticeAttempt a=new PracticeAttempt();a.setUserId(uid);a.setUserEmail("workflow"+uid+"@example.com");a.setTrack(q.getTrack());a.setSkill(q.getSkill());a.setTypeCode(q.getTypeCode());a.setQuestionIds(q.getId().toString());a.setTotal(1);a.setAnswersJson(selected==null?"{}":"{\""+q.getId()+"\":"+selected+"}");a.setScore(Objects.equals(selected,q.getAnswerIndex())?1:0);if(complete)a.setCompletedAt(LocalDateTime.now());return practices.saveAndFlush(a);}
    Exam exam(Question q){Exam e=new Exam();e.setName("Workflow exam");e.setTrack(q.getTrack());e.setKind(q.getSkill()==Skill.LISTENING?ExamKind.LISTENING:ExamKind.READING);e.setStatus(ExamStatus.PUBLISHED);e.setReadingMinutes(10);e.setListeningMinutes(10);e.getItems().add(new ExamItem(e,q.getSkill(),q.getOriginalNo(),q.getTypeCode(),q));return exams.saveAndFlush(e);}
    @Test void writingSubmissionsAreDurableLockedAndVipProtected() throws Exception {
        UserDTO learner=user(88001,"STUDENT",false);WritingPrompt p=prompt(51);
        var s=writing.submit(learner,p.getId(),"㉠ 답변\n㉡ 답변",false);em.flush();em.clear();
        var restored=writing.owned(s.getId(),learner.getId());assertEquals("submitted",restored.getStatus());assertNull(restored.getAiScore());assertTrue(restored.getModelAnswer().contains("답 첫째"));
        mvc.perform(get("/writing/feedback").param("id",s.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk()).andExpect(content().string(containsString("đăng ký VIP"))).andExpect(content().string(containsString("답 첫째")));
        mvc.perform(post("/writing/submit-mentor").param("promptId",p.getId().toString()).param("answer","답변").sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isForbidden());
        assertEquals(1,writing.getSubmissionsByLearner(learner.getId()).size());
    }
    @Test void foreignLearnerCannotReadFeedbackOrAppeal() throws Exception {
        var owner=user(88002,"STUDENT",true);var stranger=user(88003,"STUDENT",true);var s=writing.submit(owner,prompt(52).getId(),"답변",true);
        mvc.perform(get("/writing/feedback").param("id",s.getId()).sessionAttr(AuthHelper.SESSION_KEY,stranger)).andExpect(status().isNotFound());
    }
    @Test void teacherGradesAndRevisesAppealExactlyOnce() throws Exception {
        var learner=user(88004,"STUDENT",true);var teacher=user(88005,"MENTOR",false);var s=writing.submit(learner,prompt(53).getId(),"학생 답안",true);
        assertTrue(writing.claimSubmission(s.getId(),teacher));assertEquals("claimed",writing.getById(s.getId()).orElseThrow().getStatus());
        mvc.perform(get("/teacher").param("sel",s.getId()).sessionAttr(AuthHelper.SESSION_KEY,teacher)).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"totalScore\"")))
                .andExpect(content().string(containsString("Hàng chờ")))
                .andExpect(content().string(not(containsString("GiÃ¡o"))));
        assertTrue(writing.gradeSubmission(s.getId(),teacher,Map.of(),20,"Nhận xét ban đầu",null,null,null));
        assertTrue(writing.appealSubmission(s.getId(),learner.getId(),"Xin kiểm tra lại điểm nội dung."));
        assertTrue(writing.gradeSubmission(s.getId(),teacher,Map.of(),25,"Nhận xét sau khi kiểm tra",null,"revise","Đã bổ sung điểm nội dung."));
        assertFalse(writing.appealSubmission(s.getId(),learner.getId(),"Khiếu nại lần thứ hai không được."));
        em.flush();em.clear();var result=writing.owned(s.getId(),learner.getId());assertEquals(20,result.getOriginalScore());assertEquals(25,result.getTotalScore());assertTrue(result.isAppealResolved());assertEquals("Nhận xét ban đầu",result.getOriginalFeedback());
        mvc.perform(get("/writing/feedback").param("id",s.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk()).andExpect(content().string(containsString("Nhận xét ban đầu"))).andExpect(content().string(containsString("Nhận xét sau khi kiểm tra")));
        mvc.perform(get("/teacher").param("tab","done").param("sel",s.getId()).sessionAttr(AuthHelper.SESSION_KEY,teacher)).andExpect(status().isOk()).andExpect(content().string(containsString("Sau khi xử lý khiếu nại")));
        assertEquals(2,notifications.findByUserIdOrderByCreatedAtDesc(learner.getId()).size());
    }
    @Test void upholdPreservesScoreAndFeedbackWithTeacherMessage() {
        var learner=user(88006,"STUDENT",true);var teacher=user(88007,"MENTOR",false);var s=writing.submit(learner,prompt(54).getId(),"학생 답안",true);
        writing.claimSubmission(s.getId(),teacher);writing.gradeSubmission(s.getId(),teacher,Map.of(),40,"Nhận xét được giữ nguyên",null,null,null);
        writing.appealSubmission(s.getId(),learner.getId(),"Xin đối chiếu lại bài viết.");
        assertTrue(writing.gradeSubmission(s.getId(),teacher,Map.of(),null,null,null,"uphold","Bài đã được đối chiếu, điểm phù hợp."));
        var result=writing.getById(s.getId()).orElseThrow();assertEquals(40,result.getTotalScore());assertEquals("Nhận xét được giữ nguyên",result.getOverallFeedback());assertEquals("uphold",result.getAppealDecision());
    }
    @Test void teacherCannotExceedScoreCapOrGradeAnotherTeachersClaim() {
        var learner=user(88008,"STUDENT",true);var teacher=user(88009,"MENTOR",false);var other=user(88010,"MENTOR",false);var s=writing.submit(learner,prompt(53).getId(),"학생 답안",true);
        writing.claimSubmission(s.getId(),teacher);assertFalse(writing.gradeSubmission(s.getId(),other,Map.of(),30,"Không được ghi",null,null,null));
        assertThrows(IllegalArgumentException.class,()->writing.gradeSubmission(s.getId(),teacher,Map.of(),31,"Vượt điểm tối đa",null,null,null));
    }
    @Test void writingManualEntryPreservesCommasInPromptKeysAndModel() throws Exception {
        var source=imports.createSource(new ExamImportService.SourceForm("Viết "+UUID.randomUUID(),Track.TOPIK_II,2026,null,null,false,"Nội bộ",null),CM);imports.startManual(source.getId(),CM);
        mvc.perform(post("/content/sources/{id}/cards",source.getId()).sessionAttr(AuthHelper.SESSION_KEY,user(88011,Actor.ROLE_CONTENT,false))
                .param("templateCode","TPL2-WRI-01").param("typeCode","II-V1").param("qno","51").param("prompt","공지, 모임, 안내").param("keys1","신청, 연락").param("keys2","참석, 가능").param("modelAnswer","모범, 답안").param("answer", "").param("opt1", ""))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("flashOk"));
        var cards=imports.cards(source.getId());assertEquals(1,cards.size());assertEquals(1,cards.get(0).asks().size());assertEquals("공지, 모임, 안내",cards.get(0).asks().get(0).prompt());assertEquals("신청, 연락",cards.get(0).asks().get(0).keys1());
        assertEquals("모범, 답안",cards.get(0).asks().get(0).modelAnswer());
    }
    @Test void checkedAnswerSurvivesTamperedPracticeAndExamSubmission() throws Exception {
        var q=question();var a=attempt(q,88012,null,false);int wrong=q.getAnswerIndex()%4+1;
        assertFalse(practice.check(a.getUserId(),a.getId(),q.getId(),wrong).correct());
        practice.submit(a.getUserId(),a.getId(),Map.of(q.getId().toString(),String.valueOf(q.getAnswerIndex())));assertEquals(0,practices.findById(a.getId()).orElseThrow().getScore());
        var e=exam(q);var ea=new ExamAttempt();ea.setExam(e);ea.setUserId(88013L);ea.setRemainingSeconds(600);ea.setLastActiveAt(LocalDateTime.now());ea.setTotal(1);attempts.saveAndFlush(ea);
        String key="q"+e.getItems().get(0).getId();learnerExams.check(ea.getUserId(),ea.getId(),key,wrong);
        learnerExams.save(ea.getUserId(),ea.getId(),Map.of(key,String.valueOf(q.getAnswerIndex())),false,true);assertEquals(0,ea.getCorrect());
    }
    @Test void correctionRegradesOnlyCompletedAnsweredAttemptsAndProtectsComparison() throws Exception {
        var q=question();int old=q.getAnswerIndex(), corrected=old%4+1;
        var a=attempt(q,88014,corrected,true);attempt(q,88015,null,true);attempt(q,88016,corrected,false);var formerlyCorrect=attempt(q,88027,old,true);
        var before=corrections.snapshot(q);q.setAnswerIndex(corrected);q.setPromptKo(q.getPromptKo()+" 수정");
        var r=corrections.record(q,before,null,"ANSWER","RECALCULATE","Đã sửa đáp án");assertNotNull(r);em.flush();assertEquals(1,a.getScore());
        assertEquals(0,formerlyCorrect.getScore());
        assertEquals(1,notifications.findByUserIdOrderByCreatedAtDesc(88014L).size());assertTrue(notifications.findByUserIdOrderByCreatedAtDesc(88015L).isEmpty());assertTrue(notifications.findByUserIdOrderByCreatedAtDesc(88016L).isEmpty());
        assertEquals(0,corrections.adjustments(r.getId(),88014L).get(0).before());assertEquals(1,corrections.adjustments(r.getId(),88014L).get(0).after());
        mvc.perform(get("/cau-hoi-da-sua/{id}",r.getId()).sessionAttr(AuthHelper.SESSION_KEY,user(88014,"STUDENT",false))).andExpect(status().isOk()).andExpect(content().string(containsString("Trước khi sửa"))).andExpect(content().string(containsString("Sau khi sửa")));
        mvc.perform(get("/cau-hoi-da-sua/{id}",r.getId()).sessionAttr(AuthHelper.SESSION_KEY,user(88015,"STUDENT",false))).andExpect(status().isNotFound());
    }
    @Test void spellingOnlyNotifiesReporterAndDoesNotRegrade() {
        var q=question();var a=attempt(q,88017,q.getAnswerIndex()%4+1,true);var reporter=user(88018,"STUDENT",false);
        var report=reports.createReport(reporter.getId(),reporter.getFullName(),reporter.getEmail(),q.getId(),null,null,null,null,null,"Sai chính tả","Sai chính tả ở câu hỏi.","MC");
        reports.updateMcAndResolve(q.getId(),q.getPromptKo()+" 철자",q.options(),q.getAnswerIndex(),q.getExplanationVi(),"Sửa chính tả",CM,report.getId(),"AUTO","NO_SCORE_CHANGE","Đã sửa chính tả",null);
        var r=corrections.byReport(report.getId()).orElseThrow();
        assertEquals(0,a.getScore());assertTrue(notifications.findByUserIdOrderByCreatedAtDesc(a.getUserId()).isEmpty());assertEquals(1,notifications.findByUserIdOrderByCreatedAtDesc(reporter.getId()).size());assertNotNull(corrections.owned(r.getId(),reporter.getId(),false));
    }
    @Test void expiredListeningAttemptAutoFinishesWithoutLateAnswers() {
        var q=question();var e=exam(q);var a=new ExamAttempt();a.setExam(e);a.setUserId(88019L);a.setRemainingSeconds(1);a.setLastActiveAt(LocalDateTime.now().minusSeconds(5));a.setTotal(1);attempts.saveAndFlush(a);
        var result=learnerExams.save(a.getUserId(),a.getId(),Map.of("q"+e.getItems().get(0).getId(),String.valueOf(q.getAnswerIndex())),false,true);
        assertTrue(result.completed());assertNotNull(a.getCompletedAt());assertEquals(0,a.getCorrect());
    }

    @Test void writingPagesShowOnlyCanonicalDataAndTeacherProfileHasOnlyRequestedFields() throws Exception {
        var learner=user(88020,"STUDENT",false);var vip=user(88021,"STUDENT",true);
        for(int no=51;no<=54;no++) {
            var p=prompt(no);
            if(no==53){p.setChartJson("[[\"독서\",35],[\"운동\",65]]");prompts.saveAndFlush(p);}
            var response=mvc.perform(get("/writing").param("question",String.valueOf(no)).param("promptId",p.getId().toString()).sessionAttr(AuthHelper.SESSION_KEY,learner))
                .andExpect(status().isOk()).andExpect(content().string(containsString(p.getPromptKo())))
                .andExpect(content().string(not(containsString("onclick=\"submitWriting(true)\""))))
                .andExpect(content().string(not(containsString("모범 답안"))))
                .andExpect(content().string(not(containsString("lượt AI chấm")))).andReturn();
            java.nio.file.Files.writeString(java.nio.file.Path.of("target","writing-page-"+no+".html"),response.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        }
        mvc.perform(get("/writing").param("question","51").sessionAttr(AuthHelper.SESSION_KEY,vip)).andExpect(status().isOk()).andExpect(content().string(containsString("onclick=\"submitWriting(true)\"")));
        mvc.perform(get("/teacher/profile").sessionAttr(AuthHelper.SESSION_KEY,user(88022,"MENTOR",false)))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Về Bàn Chấm thi")))
            .andExpect(content().string(not(containsString("teacherTimezone"))))
            .andExpect(content().string(not(containsString("Tiêu chuẩn Barem"))))
            .andExpect(content().string(not(containsString("Bài đã chấm hoàn tất"))))
            .andExpect(content().string(not(containsString("Cổng Học viên"))));
    }

    @Test void submittedBlankAnswersRemainUnchangedWhenSendingToTeacherAndCanUseRubric() throws Exception {
        var learner=user(88023,"STUDENT",true);var teacher=user(88024,"MENTOR",false);var p=prompt(52);
        var s=writing.submit(learner,p.getId(),"㉠ 원본\n㉡ 원본",false);
        mvc.perform(post("/writing/send-teacher").param("id",s.getId()).param("answer","변경할 수 없는 답변").sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().is3xxRedirection());
        assertEquals("㉠ 원본\n㉡ 원본",writing.getById(s.getId()).orElseThrow().getStudentAnswer());
        assertTrue(writing.claimSubmission(s.getId(),teacher));
        assertTrue(writing.gradeSubmission(s.getId(),teacher,Map.of("Chỗ trống ㉠",5,"Chỗ trống ㉡",4),null,"Nhận xét hai chỗ trống",null,null,null));
        mvc.perform(get("/writing/feedback").param("id",s.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk())
            .andExpect(content().string(containsString("답 첫째"))).andExpect(content().string(containsString("Nhận xét hai chỗ trống")));
        assertEquals(9,writing.getById(s.getId()).orElseThrow().getTotalScore());
    }

    @Test void reportCorrectionAndExamScoresCommitTogetherWithBeforeAfterDetails() throws Exception {
        var q=question();int corrected=q.getAnswerIndex()%4+1;var learner=user(88025,"STUDENT",false);
        var p=attempt(q,learner.getId(),corrected,true);var e=exam(q);
        var a=new ExamAttempt();a.setExam(e);a.setUserId(learner.getId());a.setRemainingSeconds(0);a.setCompletedAt(LocalDateTime.now());a.setTotal(1);a.setAnswersJson("{\"q"+e.getItems().get(0).getId()+"\":\""+corrected+"\"}");attempts.saveAndFlush(a);
        var report=reports.createReport(learner.getId(),learner.getFullName(),learner.getEmail(),q.getId(),null,null,null,null,null,"Sai đáp án","Đáp án đúng chưa chính xác.","MC");
        String original=q.getPromptKo();
        reports.updateMcAndResolve(q.getId(),original+" sửa",q.options(),corrected,"Giải thích mới","Đổi lại đáp án đúng",CM,report.getId(),"ANSWER","RECALCULATE","Đã đối chiếu đáp án",null);
        assertEquals(1,p.getScore());assertEquals(1,a.getCorrect());assertEquals("RESOLVED",report.getStatus());
        assertEquals(2,corrections.adjustments(corrections.byReport(report.getId()).orElseThrow().getId(),learner.getId()).size());
        mvc.perform(get("/bao-loi/{id}",report.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Trước khi sửa"))).andExpect(content().string(containsString("Sau khi sửa")))
            .andExpect(content().string(containsString("0 → 1 / 1")));
        mvc.perform(get("/ket-qua/{id}",a.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk());
    }

    @Test void sharedPassageCorrectionNotifiesLearnersOfSiblingQuestions() {
        var q=questions.findAll().stream().filter(it->it.getGroup()!=null && it.getGroup().getQuestions().size()>1).findFirst().orElseThrow();
        var sibling=q.getGroup().getQuestions().stream().filter(it->!it.getId().equals(q.getId())).findFirst().orElseThrow();
        var a=attempt(sibling,88026,sibling.getAnswerIndex(),true);
        bank.update(q.getId(),q.getPromptKo(),q.options(),q.getAnswerIndex(),q.getExplanationVi(),"Sửa đoạn văn dùng chung",CM,null,"CONTENT","NO_SCORE_CHANGE",
            new QuestionBankService.GroupChange("지문 수정",null,null,null));
        assertEquals(1,a.getScore());assertEquals(1,notifications.findByUserIdOrderByCreatedAtDesc(a.getUserId()).size());
    }

    @Test void contentCompensationIsPersistedAndDoesNotAwardTwice() throws Exception {
        var q=question();var a=attempt(q,88028,q.getAnswerIndex()%4+1,true);var before=corrections.snapshot(q);q.setPromptKo(q.getPromptKo()+" 수정");
        corrections.record(q,before,null,"CONTENT","CREDIT_ANSWERED","Đề gây hiểu sai, công nhận điểm cho câu đã trả lời.");
        assertEquals(1,a.getScore());assertTrue(CheckedAnswers.read(a.getCreditedAnswersJson()).containsKey(q.getId().toString()));
        before=corrections.snapshot(q);q.setExplanationVi("Giải thích bổ sung");corrections.record(q,before,null,"CONTENT","CREDIT_ANSWERED","Bổ sung giải thích.");assertEquals(1,a.getScore());
        assertTrue(practice.result(a.getUserId(),a.getId()).answers().get(0).correct());
        mvc.perform(get("/practice/result/{id}",a.getId()).sessionAttr(AuthHelper.SESSION_KEY,user(a.getUserId(),"STUDENT",false))).andExpect(status().isOk())
            .andExpect(content().string(containsString("được công nhận điểm")));
    }

    @Test void emptyAndPartialWritingCanBeSubmittedButEmptyCannotBeSentToTeacher() throws Exception {
        var learner=user(88101,"STUDENT",true);
        for (int no=51; no<=54; no++) {
            var p=prompt(no);
            var response=mvc.perform(post("/writing/submit").param("promptId",p.getId().toString())
                    .param("answer", "").param("teacher", "false").sessionAttr(AuthHelper.SESSION_KEY,learner))
                    .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage")).andReturn();
            String id=response.getResponse().getRedirectedUrl().split("id=")[1];
            em.flush();em.clear();
            assertEquals("",writing.owned(id,learner.getId()).getStudentAnswer());
            var resultPage=mvc.perform(get("/mentor-reviews").param("id",id).sessionAttr(AuthHelper.SESSION_KEY,learner))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Bài viết gửi mentor")))
                    .andExpect(content().string(not(containsString("<button class=\"btn btn-success\">Gửi Giáo viên chấm"))))
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            if(no<=52) { assertTrue(resultPage.contains("답 첫째"));assertTrue(resultPage.contains("답 둘째")); }
            assertTrue(resultPage.contains("promptId="+p.getId()));
            mvc.perform(post("/writing/send-teacher").param("id",id).sessionAttr(AuthHelper.SESSION_KEY,learner))
                    .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage"));
            assertEquals("submitted",writing.owned(id,learner.getId()).getStatus());
            mvc.perform(post("/writing/submit").param("promptId",p.getId().toString()).param("answer"," ")
                    .param("teacher","true").sessionAttr(AuthHelper.SESSION_KEY,learner))
                    .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage"));
        }
        var p=prompt(51);var partial=writing.submit(learner,p.getId(),"㉠ 답변\n㉡ ",false);
        assertTrue(partial.getModelAnswer().contains("답 첫째"));
        assertTrue(partial.getModelAnswer().contains("답 둘째"));
        mvc.perform(post("/writing/send-teacher").param("id",partial.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage"));
        assertEquals("waiting",writing.owned(partial.getId(),learner.getId()).getStatus());
        var teacherSubmit=mvc.perform(post("/writing/submit").param("promptId",prompt(54).getId().toString())
                .param("answer","짧은 글").param("teacher","true").sessionAttr(AuthHelper.SESSION_KEY,learner))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("successMessage")).andReturn();
        mvc.perform(get(teacherSubmit.getResponse().getRedirectedUrl()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk());
        assertFalse(WritingSubmissionService.hasAnswer(51,"㉠ \n㉡ "));
    }

    @Test void writingQuestionImagesAreUploadedPerQuestionAndAppearBelowText() throws Exception {
        var cm=user(88102,Actor.ROLE_CONTENT,false);
        var source=imports.createSource(new ExamImportService.SourceForm("Ảnh riêng "+UUID.randomUUID(),Track.TOPIK_II,2026,null,null,false,"Nội bộ",null),CM);
        imports.startManual(source.getId(),CM);
        for(int no=53;no<=54;no++) {
            var image=new org.springframework.mock.web.MockMultipartFile("questionImage","chart.png","image/png",new byte[]{(byte)no,2,3});
            mvc.perform(multipart("/content/sources/{id}/cards",source.getId()).file(image)
                    .param("templateCode","TPL2-WRI-0"+(no-50)).param("qno",String.valueOf(no))
                    .param("prompt",no==53?"질문":"").param("passage","지문 "+no)
                    .sessionAttr(AuthHelper.SESSION_KEY,cm))
                    .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("flashOk"));
        }
        var cards=imports.cards(source.getId());assertEquals(2,cards.size());
        String image53=cards.get(0).asks().get(0).chart(), image54=cards.get(1).asks().get(0).chart();
        assertNotEquals(image53,image54);
        assertEquals(53,java.nio.file.Files.readAllBytes(imports.mediaPath(imports.findMedia(source.getId(),image53).orElseThrow()))[0]);
        assertEquals(54,java.nio.file.Files.readAllBytes(imports.mediaPath(imports.findMedia(source.getId(),image54).orElseThrow()))[0]);
        String originalImage53=image53;
        mvc.perform(multipart("/content/sources/{id}/cards",source.getId())
                .file(new org.springframework.mock.web.MockMultipartFile("questionImage","chart.png","image/png",new byte[]{53,2,3}))
                .param("cardId",cards.get(0).id()).param("templateCode","TPL2-WRI-03").param("qno","53")
                .param("prompt","질문").param("passage","지문 53").param("chart",image53).sessionAttr(AuthHelper.SESSION_KEY,cm))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("flashOk"));
        cards=imports.cards(source.getId());image53=cards.get(0).asks().get(0).chart();
        assertNotEquals(originalImage53,image53);
        assertEquals(image54,cards.get(1).asks().get(0).chart());
        assertTrue(java.nio.file.Files.exists(imports.mediaPath(imports.findMedia(source.getId(),originalImage53).orElseThrow())));
        mvc.perform(post("/content/sources/{id}/cards",source.getId()).param("cardId",cards.get(0).id())
                .param("templateCode","TPL2-WRI-03").param("qno","53").param("prompt","질문").param("passage","지문 53")
                .param("chart",image53).sessionAttr(AuthHelper.SESSION_KEY,cm))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("flashOk"));
        assertEquals(image53,imports.cards(source.getId()).get(0).asks().get(0).chart());
        for(var card:cards) {
            var html=mvc.perform(get("/content/sources/{id}",source.getId()).param("skill","WRITING").param("card",card.id())
                    .param("type",card.templateCode()).param("focus",card.nos().get(0).toString()).sessionAttr(AuthHelper.SESSION_KEY,cm))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(html.contains("enctype=\"multipart/form-data\""));
            assertTrue(html.contains("data-question-image"));
            assertFalse(html.contains("<textarea name=\"modelAnswer\""));
            assertFalse(html.contains("name=\"keys1\""));assertFalse(html.contains("name=\"keys2\""));
            assertFalse(html.contains("id=\"media\""));assertFalse(html.contains("id=\"media-names\""));
            assertFalse(html.contains(card.nos().get(0)==53 ? image54 : image53));
            java.nio.file.Files.writeString(java.nio.file.Path.of("target","writing-source-"+card.nos().get(0)+".html"),html);
        }
        for(int no=51;no<=52;no++) {
            var html=mvc.perform(get("/content/sources/{id}",source.getId()).param("skill","WRITING")
                    .param("type","TPL2-WRI-0"+(no-50)).param("nos",String.valueOf(no)).param("focus",String.valueOf(no))
                    .sessionAttr(AuthHelper.SESSION_KEY,cm)).andExpect(status().isOk()).andReturn()
                    .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(html.contains("name=\"keys1\""));assertTrue(html.contains("name=\"keys2\""));
            assertFalse(html.contains("id=\"media\""));assertFalse(html.contains("id=\"media-names\""));
            assertFalse(html.contains(image53));assertFalse(html.contains(image54));
            assertFalse(html.contains(originalImage53));
        }
        imports.uploadMedia(source.getId(),List.of(new org.springframework.mock.web.MockMultipartFile(
                "files","reading.png","image/png",new byte[]{1,2,3})),CM);
        for(String skill:List.of("LISTENING","READING")) {
            var result=mvc.perform(get("/content/sources/{id}",source.getId()).param("skill",skill)
                    .sessionAttr(AuthHelper.SESSION_KEY,cm)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("id=\"media\"")))
                    .andExpect(content().string(containsString("reading.png"))).andReturn();
            var html=result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(html.contains(image53));assertFalse(html.contains(image54));assertFalse(html.contains(originalImage53));
            assertEquals(1L,result.getModelAndView().getModel().get("imageCount"));
        }
        var preview=mvc.perform(get("/content/sources/{id}/preview",source.getId()).sessionAttr(AuthHelper.SESSION_KEY,cm))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(preview.contains(image53));assertTrue(preview.contains(image54));
        assertTrue(preview.indexOf("지문 53")<preview.indexOf(image53));
        assertTrue(preview.indexOf("질문")<preview.indexOf(image53));
        assertEquals(0,imports.revalidate(source.getId(),CM).getErrorCount());
        imports.activate(source.getId(),CM);em.flush();em.clear();
        var activated=prompts.findBySourceIdOrderByNoAsc(source.getId());assertEquals(2,activated.size());
        assertEquals("지문 53\n질문",activated.get(0).getPromptKo());
        assertEquals("지문 54",activated.get(1).getPromptKo());
        assertEquals(image53,activated.get(0).getChartImage());assertEquals(image54,activated.get(1).getChartImage());
        var learner=user(88103,"STUDENT",false);
        var page=mvc.perform(get("/writing").param("question","53").param("promptId",activated.get(0).getId().toString())
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(page.indexOf("id=\"promptText\"")<page.indexOf("id=\"chartContainer\""));
        mvc.perform(get("/writing/prompt/{id}/chart",activated.get(0).getId()).sessionAttr(AuthHelper.SESSION_KEY,learner))
                .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{53,2,3}));
    }

    @Test void writingLongQuestionNeedsNeitherPromptModelNorImageToActivate() {
        var source=imports.createSource(new ExamImportService.SourceForm("Viết tối giản "+UUID.randomUUID(),Track.TOPIK_II,2026,null,null,false,"Nội bộ",null),CM);
        imports.startManual(source.getId(),CM);
        imports.saveCard(source.getId(),new ExamImportService.CardForm(null,"TPL2-WRI-03",null,null,"지문",null,null,
                List.of(""),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),null,List.of(53)),CM);
        assertEquals(0,imports.revalidate(source.getId(),CM).getErrorCount());
        imports.activate(source.getId(),CM);
        var p=prompts.findBySourceIdOrderByNoAsc(source.getId()).get(0);
        assertEquals("지문",p.getPromptKo());assertNull(p.getChartImage());
    }

    @Test void writingAnswerValidationFollowsQuestionNumberInsteadOfStaleTemplateMetadata() throws Exception {
        var cm=user(88108,Actor.ROLE_CONTENT,false);
        var source=imports.createSource(new ExamImportService.SourceForm("Kiểm tra đáp án Viết "+UUID.randomUUID(),Track.TOPIK_II,2026,null,null,false,"Nội bộ",null),CM);
        imports.startManual(source.getId(),CM);
        for(int no=53;no<=54;no++) {
            var template=templates.findByCode("TPL2-WRI-0"+(no-50)).orElseThrow();
            template.setAnswerType(AnswerType.ACCEPT_KEYS);template.setOptionCount(4);templates.saveAndFlush(template);
            mvc.perform(post("/content/sources/{id}/cards",source.getId()).param("templateCode",template.getCode())
                    .param("qno",String.valueOf(no)).param("passage","지문 "+no)
                    .sessionAttr(AuthHelper.SESSION_KEY,cm)).andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("flashOk"));
        }
        assertEquals(0,imports.revalidate(source.getId(),CM).getErrorCount());
        imports.activate(source.getId(),CM);
        assertEquals(List.of(53,54),imports.writingOf(source.getId()).stream().map(WritingPrompt::getNo).toList());
        var shortSource=imports.createSource(new ExamImportService.SourceForm("Chỗ trống Viết "+UUID.randomUUID(),Track.TOPIK_II,2026,null,null,false,"Nội bộ",null),CM);
        imports.startManual(shortSource.getId(),CM);
        for(int no=51;no<=52;no++) {
            var template=templates.findByCode("TPL2-WRI-0"+(no-50)).orElseThrow();
            template.setAnswerType(AnswerType.FREE_TEXT);templates.saveAndFlush(template);
            mvc.perform(post("/content/sources/{id}/cards",shortSource.getId()).param("templateCode",template.getCode())
                    .param("qno",String.valueOf(no)).param("passage","지문 "+no)
                    .sessionAttr(AuthHelper.SESSION_KEY,cm)).andExpect(status().is3xxRedirection());
        }
        assertEquals(2,imports.revalidate(shortSource.getId(),CM).getErrorCount());
        assertThrows(ExamDomainException.class,()->imports.activate(shortSource.getId(),CM));
    }

    @Test void optionalMcQuestionTextSurvivesActivationAndLearnerRendering() throws Exception {
        var cm=user(88106,Actor.ROLE_CONTENT,false);
        var source=imports.createSource(new ExamImportService.SourceForm("Câu đọc không có câu hỏi "+UUID.randomUUID(),Track.TOPIK_I,2026,null,1,false,"Nội bộ",null),CM);
        imports.startManual(source.getId(),CM);
        mvc.perform(post("/content/sources/{id}/cards",source.getId()).sessionAttr(AuthHelper.SESSION_KEY,cm)
                .param("templateCode","TPL1-REA-01").param("qno","31").param("prompt","").param("passage","지문과 빈칸 ( )")
                .param("opt1","가").param("opt2","나").param("opt3","다").param("opt4","라").param("answer","2"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("flashOk"));
        assertEquals(0,imports.revalidate(source.getId(),CM).getErrorCount());
        imports.activate(source.getId(),CM);em.flush();em.clear();
        var q=imports.questionsOf(source.getId()).get(0);assertEquals("",q.getPromptKo());
        var learner=user(88107,"STUDENT",false);var a=attempt(q,learner.getId(),null,false);
        mvc.perform(get("/practice/play/{id}",a.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner))
                .andExpect(status().isOk()).andExpect(content().string(containsString("지문과 빈칸 ( )")));
    }

    @Test void importedWritingEditorSavesOptionalQuestionAndOwnImage() throws Exception {
        var cm=user(88105,Actor.ROLE_CONTENT,false);
        var source=imports.createSource(new ExamImportService.SourceForm("Nhập đề Viết "+UUID.randomUUID(),Track.TOPIK_II,2026,null,2,false,"Nội bộ",null),CM);
        byte[] excel;
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();var bytes=new java.io.ByteArrayOutputStream()) {
            var qs=book.createSheet(ParsedWorkbook.SHEET_QUESTIONS);var qh=qs.createRow(0);
            for(int i=0;i<ExamWorkbookParser.QUESTION_COLUMNS.size();i++)qh.createCell(i).setCellValue(ExamWorkbookParser.QUESTION_COLUMNS.get(i));
            var ws=book.createSheet(ParsedWorkbook.SHEET_WRITING);var wh=ws.createRow(0);
            for(int i=0;i<ExamWorkbookParser.WRITING_COLUMNS.size();i++)wh.createCell(i).setCellValue(ExamWorkbookParser.WRITING_COLUMNS.get(i));
            ws.createRow(1).createCell(0).setCellValue(53);ws.createRow(2).createCell(0).setCellValue(54);
            book.write(bytes);excel=bytes.toByteArray();
        }
        imports.uploadExcel(source.getId(),new org.springframework.mock.web.MockMultipartFile("file","writing.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",excel),CM);
        mvc.perform(multipart("/content/sources/{id}/paper",source.getId())
                .file(new org.springframework.mock.web.MockMultipartFile("questionImage","image.png","image/png",new byte[]{53,4,5}))
                .param("writing","true").param("no","53").param("prompt","").param("passage","지문 53")
                .sessionAttr(AuthHelper.SESSION_KEY,cm)).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("flashOk"));
        assertEquals(0,imports.revalidate(source.getId(),CM).getErrorCount());
        var slots=imports.paper(source.getId());var q53=slots.stream().filter(s->s.writing() && s.no()==53).findFirst().orElseThrow();
        assertEquals("지문 53",q53.passage());assertFalse(q53.chart().isBlank());
        assertEquals("",slots.stream().filter(s->s.writing() && s.no()==54).findFirst().orElseThrow().chart());
        var html=mvc.perform(get("/content/sources/{id}",source.getId()).param("slot","WRITING-53").sessionAttr(AuthHelper.SESSION_KEY,cm))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(html.contains("data-question-image"));assertTrue(html.contains(q53.chart()));
        assertFalse(html.contains("name=\"keys1\""));assertFalse(html.contains("name=\"keys2\""));
        assertFalse(html.contains("id=\"media\""));assertFalse(html.contains("id=\"media-names\""));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target","writing-imported-editor.html"),html);
        for(int no=51;no<=54;no++) {
            var other=mvc.perform(get("/content/sources/{id}",source.getId()).param("slot","WRITING-"+no)
                    .sessionAttr(AuthHelper.SESSION_KEY,cm)).andExpect(status().isOk()).andReturn()
                    .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(no<=52,other.contains("name=\"keys1\""));
            assertEquals(no<=52,other.contains("name=\"keys2\""));
            assertFalse(other.contains("id=\"media\""));assertFalse(other.contains("id=\"media-names\""));
            if(no!=53)assertFalse(other.contains(q53.chart()));
        }
        for(String slot:List.of("LISTENING-1","READING-1")) {
            var result=mvc.perform(get("/content/sources/{id}",source.getId()).param("slot",slot)
                    .sessionAttr(AuthHelper.SESSION_KEY,cm)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("id=\"media\""))).andReturn();
            assertFalse(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains(q53.chart()));
            assertEquals(0L,result.getModelAndView().getModel().get("imageCount"));
        }
        imports.activate(source.getId(),CM);em.flush();em.clear();
        var p=prompts.findBySourceIdOrderByNoAsc(source.getId()).get(0);
        assertEquals("지문 53",p.getPromptKo());assertEquals(q53.chart(),p.getChartImage());
    }

    @Test void examHasOneSubmitButtonAndSupportsWritingOnly() throws Exception {
        var learner=user(88104,"STUDENT",false);var q=question();var exam=exam(q);
        for(boolean writingOnly:List.of(false,true)) {
            if(writingOnly) {
                exam=new Exam();exam.setName("Writing only");exam.setKind(ExamKind.WRITING);exam.setTrack(Track.TOPIK_II);exam.setStatus(ExamStatus.PUBLISHED);exam.setWritingMinutes(50);
                exam.setCode("W"+UUID.randomUUID().toString().substring(0,18));
                exam.setWriting53(prompt(53));exam=exams.saveAndFlush(exam);
            }
            var a=new ExamAttempt();a.setExam(exam);a.setUserId(learner.getId());a.setRemainingSeconds(600);a.setLastActiveAt(LocalDateTime.now());a.setTotal(1);attempts.saveAndFlush(a);
            var html=mvc.perform(get("/exam/attempt/{id}",a.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            // Confirmation modal has its own confirm button; the page itself has one entry point in the sheet.
            assertEquals(1,java.util.regex.Pattern.compile("class=\"[^\"]*qp-submit(?:\s|\")").matcher(html).results().count());
            java.nio.file.Files.writeString(java.nio.file.Path.of("target",writingOnly?"exam-writing-play.html":"exam-reading-play.html"),html);
        }
    }

    @Test void writingSubmitAndTeacherSubmissionUseDatabaseGeneratedIdentity() throws Exception {
        var learner=user(88201,"STUDENT",true);var shortPrompt=prompt(51);var longPrompt=prompt(53);
        for(var p:List.of(shortPrompt,longPrompt)) {
            boolean teacher=p.getNo()==53;
            mvc.perform(post(teacher?"/writing/submit-mentor":"/writing/submit")
                    .param("promptId",p.getId().toString()).param("answer",teacher?"학생 답안":"")
                    .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("/writing/feedback?id=*"));
        }
        em.flush();em.clear();
        var saved=writing.getSubmissionsByLearner(learner.getId());assertEquals(2,saved.size());
        for(var sub:saved) {
            assertTrue(Long.parseLong(sub.getId())>0);
            var entity=submissions.findById(Long.valueOf(sub.getId())).orElseThrow();
            assertEquals(sub.getId(),new com.fasterxml.jackson.databind.ObjectMapper().readTree(entity.getPayload()).path("id").asText());
            assertEquals(sub.getQuestionNo()==53?"WAITING_MENTOR":"DRAFT",entity.getStatus());
            mvc.perform(get("/writing/feedback").param("id",sub.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk());
        }
        assertTrue(writing.getById("invalid-id").isEmpty());
    }

    @Test void staleWritingSessionIsRejectedAndTheLearnersDraftIsPreserved() throws Exception {
        var learner=new UserDTO();learner.setId(999999L);learner.setEmail("unpersisted@example.com");learner.setRole("STUDENT");learner.setVip(true);
        for(int no:List.of(51,53)) {
            var p=prompt(no);String answer=no==51?"㉠ 답안\n㉡ ":"학생 작성 내용";
            var response=mvc.perform(post("/writing/submit").param("promptId",p.getId().toString()).param("answer",answer)
                    .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("errorMessage")).andExpect(flash().attribute("draftAnswer",answer)).andReturn();
            mvc.perform(get("/writing").param("question",String.valueOf(no)).param("promptId",p.getId().toString())
                    .flashAttrs(response.getFlashMap()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk())
                    .andExpect(content().string(containsString(answer)));
        }
        assertTrue(writing.getSubmissionsByLearner(learner.getId()).isEmpty());
        var persisted=user(88210,"STUDENT",false);persisted.setEmail("different-account@example.com");
        assertThrows(IllegalArgumentException.class,()->writing.submit(persisted,prompt(53).getId(),"답안",false));
    }

    ExamAttempt writingExamAttempt(long userId, WritingPrompt... ps) {
        var exam=new Exam();exam.setName("Đề thi Viết");exam.setCode("W"+UUID.randomUUID().toString().substring(0,18));
        exam.setTrack(Track.TOPIK_II);exam.setKind(ExamKind.WRITING);exam.setStatus(ExamStatus.PUBLISHED);exam.setWritingMinutes(50);
        for(var p:ps)switch(p.getNo()){case 51->exam.setWriting51(p);case 52->exam.setWriting52(p);case 53->exam.setWriting53(p);case 54->exam.setWriting54(p);}
        exams.saveAndFlush(exam);
        var a=new ExamAttempt();a.setExam(exam);a.setUserId(userId);a.setRemainingSeconds(3000);a.setLastActiveAt(LocalDateTime.now());
        return attempts.saveAndFlush(a);
    }

    @Test void writingExamSharesPracticeFormAndSendsLockedAnswersWithoutDuplicates() throws Exception {
        var learner=user(88202,"STUDENT",true);
        var p51=prompt(51);var p52=prompt(52);var p53=prompt(53);var p54=prompt(54);
        var a=writingExamAttempt(learner.getId(),p51,p52,p53,p54);
        var html=mvc.perform(get("/exam/attempt/{id}",a.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(4,java.util.regex.Pattern.compile("<[^>]+data-writing-editor").matcher(html).results().count());
        assertTrue(html.contains("name=\"w"+p51.getId()+"\""));assertTrue(html.contains("/submit-teacher"));
        assertTrue(html.contains("Nộp bài và gửi Giáo viên chấm?"));assertFalse(html.contains("chÆ°a"));
        assertFalse(html.contains("답 첫째"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target","exam-writing-shared.html"),html);
        var practicePage=mvc.perform(get("/writing").param("question","51").param("promptId",p51.getId().toString())
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(practicePage.contains("data-writing-editor"));assertTrue(practicePage.contains("id=\"blankAnsA\""));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target","practice-writing-shared.html"),practicePage);
        mvc.perform(post("/exam/attempt/{id}/submit-teacher",a.getId()).param("w"+p51.getId(),"㉠ 일부\n㉡ ")
                .param("w"+p52.getId(),"").param("w"+p53.getId(),"학생 답안")
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(redirectedUrl("/ket-qua/"+a.getId()));
        em.flush();em.clear();
        assertNotNull(attempts.findById(a.getId()).orElseThrow().getCompletedAt());
        var reviews=writing.examSubmissions(learner.getId(),a.getId());assertEquals(4,reviews.size());
        assertEquals("",reviews.get(p52.getId()).getStudentAnswer());assertEquals("",reviews.get(p54.getId()).getStudentAnswer());
        assertEquals("㉠ 일부\n㉡",reviews.get(p51.getId()).getStudentAnswer());assertEquals("waiting",reviews.get(p53.getId()).getStatus());
        assertEquals("Đề thi",reviews.get(p53.getId()).getSource());
        mvc.perform(post("/exam/attempt/{id}/submit-teacher",a.getId()).param("w"+p51.getId(),"Không được thay đổi")
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(redirectedUrl("/ket-qua/"+a.getId()));
        assertEquals(4,writing.examSubmissions(learner.getId(),a.getId()).size());
        assertEquals("㉠ 일부\n㉡",writing.examSubmissions(learner.getId(),a.getId()).get(p51.getId()).getStudentAnswer());
        mvc.perform(get("/ket-qua/{id}",a.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("답 첫째"))))
                .andExpect(content().string(containsString("đang chờ kết quả chấm")))
                .andExpect(content().string(containsString("/writing/feedback?id="+reviews.get(p51.getId()).getId())));
        mvc.perform(get("/writing/feedback").param("id",reviews.get(p51.getId()).getId()).sessionAttr(AuthHelper.SESSION_KEY,learner))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("답 첫째"))));
        var teacher=user(88212,"MENTOR",false);String reviewId=reviews.get(p51.getId()).getId();
        assertTrue(writing.claimSubmission(reviewId,teacher));
        assertTrue(writing.gradeSubmission(reviewId,teacher,Map.of("Chỗ trống ㉠",3,"Chỗ trống ㉡",0),null,"Đã chấm câu 51",null,null,null));
        mvc.perform(get("/ket-qua/{id}",a.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Đã chấm câu 51")))
                .andExpect(content().string(containsString("3/10")))
                .andExpect(content().string(containsString("답 첫째")));
        mvc.perform(post("/exam/attempt/{id}/send-teacher",a.getId()).sessionAttr(AuthHelper.SESSION_KEY,user(88203,"STUDENT",true)))
                .andExpect(status().isNotFound());
    }

    @Test void writingExamAllowsEmptySelfSubmitButEnforcesVipAndNonemptyTeacherSubmission() throws Exception {
        var learner=user(88204,"STUDENT",false);var p=prompt(53);var a=writingExamAttempt(learner.getId(),p);
        mvc.perform(get("/exam/attempt/{id}",a.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("formaction=\"/exam/attempt/"+a.getId()+"/submit-teacher\""))))
                .andExpect(content().string(containsString("đăng ký VIP")));
        mvc.perform(post("/exam/attempt/{id}/submit-teacher",a.getId()).param("w"+p.getId(),"학생 답안")
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isForbidden());
        assertNull(a.getCompletedAt());
        mvc.perform(post("/exam/attempt/{id}/submit",a.getId()).param("w"+p.getId(),"")
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(redirectedUrl("/ket-qua/"+a.getId()));
        assertNotNull(a.getCompletedAt());
        mvc.perform(get("/ket-qua/{id}",a.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk());
        var vip=user(88205,"STUDENT",true);var empty=writingExamAttempt(vip.getId(),p);
        mvc.perform(post("/exam/attempt/{id}/submit-teacher",empty.getId()).param("w"+p.getId(),"")
                .sessionAttr(AuthHelper.SESSION_KEY,vip)).andExpect(redirectedUrl("/lam-bai/"+empty.getId()))
                .andExpect(flash().attributeExists("errorMessage"));
        assertTrue(writing.getSubmissionsByLearner(vip.getId()).isEmpty());
    }

    @Test void completedExamCanSendSavedWritingToTeacherWithoutEditingIt() throws Exception {
        var learner=user(88206,"STUDENT",true);var p=prompt(52);var a=writingExamAttempt(learner.getId(),prompt(51),p,prompt(53),prompt(54));
        mvc.perform(post("/exam/attempt/{id}/submit",a.getId()).param("w"+p.getId(),"㉠ 답안\n㉡ ")
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(redirectedUrl("/ket-qua/"+a.getId()));
        assertTrue(writing.examSubmissions(learner.getId(),a.getId()).isEmpty());
        mvc.perform(get("/ket-qua/{id}",a.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk())
                .andExpect(content().string(containsString("bài chưa gửi Giáo viên chấm")))
                .andExpect(content().string(containsString("/send-teacher")))
                .andExpect(content().string(containsString("㉠ 답안")))
                .andExpect(content().string(not(containsString("답 첫째"))))
                .andExpect(content().string(not(containsString("모범 답안"))));
        mvc.perform(post("/exam/attempt/{id}/send-teacher",a.getId()).param("w"+p.getId(),"Tampered answer")
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(redirectedUrl("/ket-qua/"+a.getId()));
        var result=writing.examSubmissions(learner.getId(),a.getId()).get(p.getId());
        assertEquals(4,writing.examSubmissions(learner.getId(),a.getId()).size());
        assertEquals("㉠ 답안\n㉡",result.getStudentAnswer());
        mvc.perform(get("/ket-qua/{id}",a.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/send-teacher"))));
    }

    @Test void rejectedExamWritingNeedsRedoAndGradedWritingCannotBeResent() throws Exception {
        var learner=user(88230,"STUDENT",true);var teacher=user(88231,"MENTOR",false);var p=prompt(52);
        var a1=writingExamAttempt(learner.getId(),p);
        mvc.perform(post("/exam/attempt/{id}/submit",a1.getId()).param("w"+p.getId(),"㉠ 답\n㉡ 답")
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(redirectedUrl("/ket-qua/"+a1.getId()));
        mvc.perform(post("/exam/attempt/{id}/send-teacher-q",a1.getId()).param("promptId",p.getId().toString())
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(flash().attributeCount(1)).andExpect(flash().attributeExists("successMessage"));
        String rejectedId=writing.examSubmissions(learner.getId(),a1.getId()).get(p.getId()).getId();
        assertTrue(writing.rejectSubmission(rejectedId,teacher,"Không thích"));

        // Bị từ chối: không còn nút gửi (từng câu lẫn cả bài), gửi lại bằng request cũng bị chặn.
        mvc.perform(get("/ket-qua/{id}",a1.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/send-teacher"))))
                .andExpect(content().string(containsString("Giáo viên đã từ chối chấm câu này")))
                .andExpect(content().string(containsString("Không thích")));
        mvc.perform(post("/exam/attempt/{id}/send-teacher-q",a1.getId()).param("promptId",p.getId().toString())
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(flash().attributeExists("errorMessage"));
        mvc.perform(post("/exam/attempt/{id}/send-teacher",a1.getId())
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(flash().attributeExists("errorMessage"));
        assertThrows(IllegalArgumentException.class,()->writing.sendToTeacher(rejectedId,learner));
        assertEquals("submitted",writing.owned(rejectedId,learner.getId()).getStatus());
        assertEquals(1,writing.getSubmissionsByLearner(learner.getId()).size());

        // Làm lại đề (lượt mới, kể cả cùng nội dung) thì được gửi lại.
        var a2=writingExamAttempt(learner.getId(),p);
        mvc.perform(post("/exam/attempt/{id}/submit",a2.getId()).param("w"+p.getId(),"㉠ 답\n㉡ 답")
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(redirectedUrl("/ket-qua/"+a2.getId()));
        mvc.perform(get("/ket-qua/{id}",a2.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk())
                .andExpect(content().string(containsString("/send-teacher-q")));
        mvc.perform(post("/exam/attempt/{id}/send-teacher",a2.getId())
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(flash().attributeExists("successMessage"));
        var resent=writing.examSubmissions(learner.getId(),a2.getId()).get(p.getId());
        assertEquals("waiting",resent.getStatus());
        assertTrue(writing.claimSubmission(resent.getId(),teacher));
        assertTrue(writing.gradeSubmission(resent.getId(),teacher,Map.of("Chỗ trống ㉠",4,"Chỗ trống ㉡",5),null,"Tốt",null,null,null));

        // Đã chấm: không gửi được nữa, kể cả ở lượt làm sau.
        var a3=writingExamAttempt(learner.getId(),p);
        mvc.perform(post("/exam/attempt/{id}/submit",a3.getId()).param("w"+p.getId(),"㉠ 새 답\n㉡ 새 답")
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(redirectedUrl("/ket-qua/"+a3.getId()));
        for (var a : List.of(a2,a3))
            mvc.perform(get("/ket-qua/{id}",a.getId()).sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("/send-teacher"))));
        mvc.perform(post("/exam/attempt/{id}/send-teacher-q",a3.getId()).param("promptId",p.getId().toString())
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(flash().attributeExists("errorMessage"));
        assertTrue(writing.examSubmissions(learner.getId(),a3.getId()).isEmpty());
    }

    @Test void writingBankDetailsEditTheWritingPromptAndProtectSnapshots() throws Exception {
        var cm=user(88207,Actor.ROLE_CONTENT,false);var learner=user(88208,"STUDENT",false);var p=prompt(53);
        var old=writing.submit(learner,p.getId(),"Bài làm đã nộp",false);String oldPrompt=old.getPrompt();
        var bankPage=mvc.perform(get("/content/questions").param("skill","WRITING").sessionAttr(AuthHelper.SESSION_KEY,cm))
                .andExpect(status().isOk()).andExpect(content().string(containsString("js-open-writing")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.writeString(java.nio.file.Path.of("target","writing-bank.html"),bankPage);
        mvc.perform(get("/content/writing-questions/{id}/detail",p.getId()).sessionAttr(AuthHelper.SESSION_KEY,cm))
                .andExpect(status().isOk()).andExpect(jsonPath("$.no").value(53));
        mvc.perform(post("/content/writing-questions/{id}",p.getId()).param("promptKo","Đề bài đã sửa")
                .param("modelAnswer","").param("chartJson","").param("chartImage","").param("rubricNote","Hướng dẫn mới")
                .sessionAttr(AuthHelper.SESSION_KEY,cm)).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("flashOk"));
        em.flush();em.clear();
        assertEquals("Đề bài đã sửa",prompts.findById(p.getId()).orElseThrow().getPromptKo());
        assertEquals(oldPrompt,writing.owned(old.getId(),learner.getId()).getPrompt());
        mvc.perform(post("/content/writing-questions/{id}",p.getId()).param("promptKo","Không được sửa").param("modelAnswer","")
                .sessionAttr(AuthHelper.SESSION_KEY,learner)).andExpect(status().is3xxRedirection());
        assertEquals("Đề bài đã sửa",prompts.findById(p.getId()).orElseThrow().getPromptKo());
    }

    @Test void writingBankUpdatesShortAnswersAndRejectsAnImageFromAnotherSource() throws Exception {
        var cm=user(88209,Actor.ROLE_CONTENT,false);var p=prompt(51);
        mvc.perform(post("/content/writing-questions/{id}",p.getId()).param("promptKo","Đề bài câu 51")
                .param("keys1","답안 하나").param("keys2","답안 둘").param("modelAnswer","")
                .sessionAttr(AuthHelper.SESSION_KEY,cm)).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("flashOk"));
        em.flush();em.clear();
        var updated=prompts.findById(p.getId()).orElseThrow();assertEquals("답안 하나",updated.getKeys1());assertEquals("답안 둘",updated.getKeys2());
        mvc.perform(post("/content/writing-questions/{id}",p.getId()).param("promptKo","Không được lưu")
                .param("keys1","답안 하나").param("keys2","답안 둘").param("chartImage","other-source.png").param("modelAnswer","")
                .sessionAttr(AuthHelper.SESSION_KEY,cm)).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("flashError"));
        assertEquals("Đề bài câu 51",prompts.findById(p.getId()).orElseThrow().getPromptKo());
    }
}
