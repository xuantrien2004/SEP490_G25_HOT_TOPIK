package com.hottopik.service.exam;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.ExamSourceRepository;
import com.hottopik.repository.exam.QuestionGroupRepository;
import com.hottopik.repository.exam.QuestionRepository;
import com.hottopik.service.PlacementService;
import com.hottopik.service.exam.PlacementRules.Paper;
import com.hottopik.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ba lối vào kiểm tra trình độ: đề 20 câu, điểm thật, rồi mới chọn mục tiêu. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlacementFlowTest {

    private static final UserDTO LEARNER = new UserDTO(9L, "minh", "learner@hottopik.com", "", "Phạm Minh Anh",
            "ROLE_USER", "Học viên", "ACTIVE", "", "", "TOPIK_2", "TOPIK_0", false, null);
    private static final UserDTO CONTENT = new UserDTO(2L, "content_manager", "content@hottopik.com", "", "Lê Thu Hà",
            "ROLE_CONTENT_MANAGER", "Content Manager", "ACTIVE", "", "", "TOPIK_6", "TOPIK_5", false, null);

    @Autowired MockMvc mvc;
    @Autowired PlacementService placement;
    @Autowired QuestionRepository questions;
    @Autowired QuestionGroupRepository groups;
    @Autowired ExamSourceRepository sources;

    @Test
    void threeEntryCardsDoNotAskForALevel() throws Exception {
        mvc.perform(get("/onboarding"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mình chưa biết trình độ")))
                .andExpect(content().string(containsString("Mình đã qua TOPIK I")))
                .andExpect(content().string(containsString("Mình biết trình độ")))
                .andExpect(content().string(containsString("/placement/khai")))
                .andExpect(content().string(not(containsString("<select"))));

        mvc.perform(get("/placement")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/placement/khai").sessionAttr(AuthHelper.SESSION_KEY, LEARNER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"current\"")))
                .andExpect(content().string(containsString("name=\"target\"")))
                .andExpect(content().string(containsString("name=\"days\"")))
                .andExpect(content().string(containsString("name=\"date\"")))
                .andExpect(content().string(containsString("50 ngày")))
                .andExpect(content().string(containsString("không chọn ở màn ba thẻ")));
    }

    @Test
    void contentManagerLocksTenAndTenAndHidesGroupedTypes() throws Exception {
        mvc.perform(get("/content/placement").sessionAttr(AuthHelper.SESSION_KEY, CONTENT))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("I-N1")))
                .andExpect(content().string(containsString("Chọn câu trả lời phù hợp")))
                .andExpect(content().string(not(containsString("Một đoạn nghe, 2 câu hỏi"))))
                .andExpect(content().string(not(containsString("II-V1"))));

        mvc.perform(post("/content/placement").sessionAttr(AuthHelper.SESSION_KEY, CONTENT)
                        .param("paper", "TOPIK_I")
                        .param("I-N1", "10")
                        .param("I-D1", "5"))
                .andExpect(redirectedUrl("/content/placement"))
                .andExpect(flash().attribute("flashError", containsString("Đọc")));
    }

    @Test
    void topikOneShowsPointsAndOffersTopikTwoAtEightyPercent() throws Exception {
        bank(Paper.TOPIK_I);
        mvc.perform(get("/placement").param("paper", "TOPIK_I").sessionAttr(AuthHelper.SESSION_KEY, LEARNER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bài TOPIK I")))
                .andExpect(content().string(containsString("20 câu")))
                .andExpect(content().string(containsString("Xác nhận làm bài")))
                .andExpect(content().string(not(containsString("Chọn câu trả lời phù hợp"))))
                .andExpect(content().string(not(containsString("I-N1"))));

        long id = start("TOPIK_I");
        mvc.perform(get("/placement/play/" + id).sessionAttr(AuthHelper.SESSION_KEY, LEARNER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("(Đề mẫu")))
                .andExpect(content().string(containsString("answer_")));

        var submit = post("/placement/submit/" + id).sessionAttr(AuthHelper.SESSION_KEY, LEARNER);
        for (Question question : placed(id)) {
            submit.param("answer_" + question.getId(), String.valueOf(question.getAnswerIndex()));
        }
        mvc.perform(submit).andExpect(redirectedUrl("/placement/result/" + id));

        mvc.perform(get("/placement/result/" + id).sessionAttr(AuthHelper.SESSION_KEY, LEARNER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("200/200")))
                .andExpect(content().string(containsString("80%")))
                .andExpect(content().string(containsString("Làm bài TOPIK II")))
                .andExpect(content().string(containsString("Chọn mục tiêu và sinh lộ trình")));

        mvc.perform(get("/placement/muc-tieu").param("attempt", String.valueOf(id)).param("track", "TOPIK_II")
                        .sessionAttr(AuthHelper.SESSION_KEY, LEARNER))
                .andExpect(redirectedUrl("/placement/result/" + id))
                .andExpect(flash().attribute("placementError", containsString("TOPIK II")));

        mvc.perform(get("/placement/muc-tieu").param("attempt", String.valueOf(id)).param("track", "TOPIK_I")
                        .sessionAttr(AuthHelper.SESSION_KEY, LEARNER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TOPIK 2")))
                .andExpect(content().string(containsString("Số ngày")))
                .andExpect(content().string(containsString("50 ngày")))
                .andExpect(content().string(containsString("Sinh lộ trình")));
    }

    @Test
    void lowTopikTwoScoreSendsTheLearnerBackToTopikOne() throws Exception {
        bank(Paper.TOPIK_II);
        long id = start("TOPIK_II");
        mvc.perform(post("/placement/submit/" + id).sessionAttr(AuthHelper.SESSION_KEY, LEARNER))
                .andExpect(redirectedUrl("/placement/result/" + id));
        mvc.perform(get("/placement/result/" + id).sessionAttr(AuthHelper.SESSION_KEY, LEARNER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("0/200")))
                .andExpect(content().string(containsString("Nên về lộ trình TOPIK I")))
                .andExpect(content().string(containsString("track=TOPIK_I")))
                .andExpect(content().string(not(containsString("Làm bài TOPIK II"))));
    }

    @Test
    void knownLevelBuildsTheRoadmapOnlyAfterTargetAndDate() throws Exception {
        mvc.perform(post("/placement/sinh").sessionAttr(AuthHelper.SESSION_KEY, LEARNER)
                        .param("current", "2").param("target", "2")
                        .param("date", LocalDate.now().plusDays(60).toString()))
                .andExpect(redirectedUrl("/placement/khai"))
                .andExpect(flash().attribute("placementError", containsString("cao hơn")));

        mvc.perform(post("/placement/sinh").sessionAttr(AuthHelper.SESSION_KEY, LEARNER)
                        .param("current", "0").param("target", "2")
                        .param("date", LocalDate.now().plusDays(10).toString()).param("minutes", "20"))
                .andExpect(redirectedUrl("/placement/khai"))
                .andExpect(flash().attribute("placementError", containsString("31")));

        mvc.perform(post("/placement/sinh").sessionAttr(AuthHelper.SESSION_KEY, LEARNER)
                        .param("current", "0").param("target", "2")
                        .param("date", LocalDate.now().plusDays(60).toString()).param("minutes", "20"))
                .andExpect(redirectedUrl("/lo-trinh"));

        mvc.perform(get("/lo-trinh").sessionAttr(AuthHelper.SESSION_KEY, LEARNER))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TOPIK I")))
                .andExpect(content().string(containsString("TOPIK 0 → TOPIK 2")));
    }

    private long start(String paper) throws Exception {
        MvcResult result = mvc.perform(post("/placement/start").param("paper", paper)
                        .sessionAttr(AuthHelper.SESSION_KEY, LEARNER))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertTrue(location != null && location.startsWith("/placement/play/"), location);
        return Long.parseLong(location.substring("/placement/play/".length()));
    }

    private List<Question> placed(long attemptId) {
        List<Long> ids = new ArrayList<>();
        for (String raw : placement.require(attemptId).getQuestionIds().split(",")) ids.add(Long.parseLong(raw));
        Map<Long, Question> byId = new LinkedHashMap<>();
        for (Question question : questions.findWithMediaByIdIn(ids)) byId.put(question.getId(), question);
        return ids.stream().map(byId::get).toList();
    }

    private void bank(Paper paper) {
        ExamSource source = new ExamSource();
        source.setName("Ngân hàng đánh giá · " + paper.title());
        source.setTrack(paper.track());
        source.setExpectedCount(20);
        source.setAssessment(true);
        source.setCopyrightNote("Câu demo cho kiểm tra trình độ.");
        source.setStatus(SourceStatus.ACTIVE);
        sources.save(source);

        Map<String, List<Question>> donors = new LinkedHashMap<>();
        for (Question question : questions.findPlacementDonors()) {
            QuestionTypeDef type = QuestionTypeCatalog.byCode(question.getTypeCode()).orElse(null);
            if (!PlacementRules.singleQuestion(type) || type.track() != paper.track()) continue;
            donors.computeIfAbsent(type.code(), key -> new ArrayList<>()).add(question);
        }
        Map<String, Integer> plan = PlacementRules.demo(paper);
        for (Map.Entry<String, Integer> entry : plan.entrySet()) {
            List<Question> pool = donors.getOrDefault(entry.getKey(), List.of());
            assertTrue(pool.size() >= entry.getValue(), entry.getKey() + " chỉ có " + pool.size());
            for (int i = 0; i < entry.getValue(); i++) copy(pool.get(i), source);
        }
        placement.save(paper.name(), plan);
    }

    private void copy(Question donor, ExamSource target) {
        QuestionGroup origin = donor.getGroup();
        QuestionGroup group = new QuestionGroup();
        group.setSource(target);
        group.setTypeCode(donor.getTypeCode());
        group.setSkill(donor.getSkill());
        group.setSize(1);
        group.setPassageKo(origin == null ? null : origin.getPassageKo());
        group.setScriptKo(origin == null ? null : origin.getScriptKo());
        groups.save(group);

        Question copy = new Question();
        copy.setSource(target);
        copy.setGroup(group);
        copy.setTypeCode(donor.getTypeCode());
        copy.setTrack(donor.getTrack());
        copy.setSkill(donor.getSkill());
        copy.setOriginalNo(donor.getOriginalNo());
        copy.setPromptKo(donor.getPromptKo());
        copy.setOption1(donor.getOption1());
        copy.setOption2(donor.getOption2());
        copy.setOption3(donor.getOption3());
        copy.setOption4(donor.getOption4());
        copy.setAnswerIndex(donor.getAnswerIndex());
        copy.setPurpose(QuestionPurpose.ASSESSMENT);
        copy.setStatus(QuestionStatus.ACTIVE);
        copy.setContentHash(ExamImportValidator.contentHash(
                donor.getPromptKo() + "\n" + donor.getId(),
                origin == null ? null : origin.getPassageKo(),
                origin == null ? null : origin.getScriptKo(),
                new String[]{donor.getOption1(), donor.getOption2(), donor.getOption3(), donor.getOption4()}));
        copy.setCode(Codes.temporary());
        questions.save(copy);
    }
}
