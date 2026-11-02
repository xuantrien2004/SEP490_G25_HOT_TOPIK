package com.hottopik.service.exam;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.PracticeAttempt;
import com.hottopik.entity.exam.Track;
import com.hottopik.service.PracticeService;
import com.hottopik.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

/** Người học tạo lịch từ bản lộ trình đã phát hành và đánh dấu bài xong. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LearnerRoadmapFlowTest {

    @Autowired MockMvc mvc;
    @Autowired LearnerRoadmapService roadmaps;
    @Autowired PracticeService practice;

    @Test
    void learnerBuildsScheduleFromPublishedProgram() throws Exception {
        UserDTO learner = new UserDTO(9L, "minh", "learner@hottopik.com", "", "Phạm Minh Anh",
                "ROLE_USER", "Học viên", "ACTIVE", "", "", "TOPIK_4", "TOPIK_3", false, null);

        mvc.perform(get("/lo-trinh").sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hành trình của")))
                .andExpect(content().string(containsString("TOPIK 3 → TOPIK 4")))
                .andExpect(content().string(containsString("Sắp nhập lộ trình")))
                .andExpect(content().string(not(containsString("Đánh dấu xong"))))
                .andExpect(content().string(containsString("Minh Anh")))
                .andExpect(content().string(containsString("TPL2-LIS-01")))
                .andExpect(content().string(not(containsString("Nghe 1"))))
                .andExpect(content().string(containsString("Mini test")))
                .andExpect(content().string(containsString("Full test")));

        mvc.perform(get("/hom-nay").sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bài hôm nay")))
                .andExpect(content().string(containsString("Chào")))
                .andExpect(content().string(containsString("Minh Anh")))
                .andExpect(content().string(containsString("Nghe và chọn tranh hoặc biểu đồ")))
                .andExpect(content().string(not(containsString("Nghe 1–"))))
                .andExpect(content().string(containsString("Bắt đầu")));

        mvc.perform(get("/lo-trinh/tao").param("track", "TOPIK_II").param("days", "60")
                        .sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nghe và chọn tranh hoặc biểu đồ")))
                .andExpect(content().string(not(containsString("TPL2-WRI-01"))));

        mvc.perform(post("/lo-trinh").param("track", "TOPIK_II").param("days", "60").param("minutes", "20")
                        .sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/lo-trinh"));

        mvc.perform(get("/lo-trinh").sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("TPL2-LIS-01")))
                .andExpect(content().string(containsString("TOPIK II")))
                .andExpect(content().string(not(containsString("TPL2-WRI-01"))));

        mvc.perform(post("/lo-trinh/tien-do").param("day", "1").param("slot", "0").param("done", "1")
                        .sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1:0")));

        mvc.perform(get("/lo-trinh/dieu-chinh").sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2,7,14")))
                .andExpect(content().string(containsString("40,40,20")))
                .andExpect(content().string(containsString("Mỗi dạng học")))
                .andExpect(content().string(not(containsString("Số bài mỗi ngày"))));

        mvc.perform(post("/lo-trinh/dieu-chinh").param("track", "TOPIK_II").param("days", "31").param("minutes", "20")
                        .sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().is3xxRedirection());

        mvc.perform(post("/lo-trinh/tien-do").param("day", "1").param("slot", "0").param("done", "1")
                        .sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk());

        int shortest = roadmaps.minimumDays(Track.TOPIK_II);
        assertTrue(roadmaps.lessonsFor(roadmaps.published(), Track.TOPIK_II, shortest) > 2);
        assertEquals(2, roadmaps.lessonsFor(roadmaps.published(), Track.TOPIK_II, 120));

        mvc.perform(post("/lo-trinh/dieu-chinh").param("track", "TOPIK_II").param("days", "120").param("minutes", "30")
                        .sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/lo-trinh"));

        mvc.perform(get("/lo-trinh").sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"totalDays\":120")))
                .andExpect(content().string(containsString("\"minutes\":30")))
                .andExpect(content().string(containsString("\"itemsPerDay\":2")))
                .andExpect(content().string(containsString("1:0")));

        int[] position = practiceSlot(learner, "TPL2-LIS-01");
        String day = String.valueOf(position[0]);
        String slot = String.valueOf(position[1]);
        String doneKey = day + ":" + slot;
        MockHttpSession practiceSession = new MockHttpSession();
        practiceSession.setAttribute(AuthHelper.SESSION_KEY, learner);
        mvc.perform(get("/hom-nay").session(practiceSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/practice/prepare?typeCode=")));

        mvc.perform(get("/practice/prepare").param("typeCode", "II-N1").param("day", day).param("slot", slot)
                        .session(practiceSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(QuestionTypeCatalog.byCode("II-N1").orElseThrow().name())))
                .andExpect(content().string(containsString("name=\"day\"")))
                .andExpect(content().string(containsString("name=\"slot\"")));

        // Opening a URL without an actual completed attempt must not grant roadmap progress.
        mvc.perform(get("/practice/result").param("day", day).param("slot", slot).session(practiceSession))
                .andExpect(status().isNotFound());
        assertFalse(doneKeys(learner).contains(doneKey));

        String playUrl = mvc.perform(post("/practice/start").param("typeCode", "II-N1")
                        .param("day", day).param("slot", slot).session(practiceSession))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        Long attemptId = Long.valueOf(playUrl.substring("/practice/play/".length()));
        mvc.perform(get("/practice/result/{id}", attemptId).session(practiceSession))
                .andExpect(redirectedUrl(playUrl));
        assertFalse(doneKeys(learner).contains(doneKey));

        PracticeAttempt attempt = practice.result(learner.getId(), attemptId).attempt();
        assertEquals(QuestionTypeCatalog.byCode("II-N1").orElseThrow().requiredCount(), attempt.getTotal());
        var submission = post("/practice/submit/{id}", attemptId).session(practiceSession);
        for (var question : practice.questions(attempt)) {
            submission.param("answer_" + question.id(), String.valueOf(question.answer()));
        }
        var submitted = mvc.perform(submission)
                .andExpect(redirectedUrl("/practice/result/" + attemptId))
                .andExpect(flash().attribute("roadmapLinked", true))
                .andReturn();
        assertEquals(attempt.getTotal(), practice.result(learner.getId(), attemptId).attempt().getScore());
        mvc.perform(get("/practice/result/{id}", attemptId).session(practiceSession).flashAttrs(submitted.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("đã được tính vào lộ trình")))
                .andExpect(content().string(not(containsString("Đánh dấu xong"))));
        assertTrue(doneKeys(learner).contains(doneKey));
    }

    @Test
    void mismatchedPracticeCannotBeCountedForARoadmapSlot() throws Exception {
        UserDTO learner = new UserDTO(9L, "minh", "learner@hottopik.com", "", "Phạm Minh Anh",
                "ROLE_USER", "Học viên", "ACTIVE", "", "", "TOPIK_4", "TOPIK_3", false, null);
        roadmaps.ensure(learner.getEmail(), Track.TOPIK_II);
        int[] position = practiceSlot(learner, "TPL2-LIS-01");
        mvc.perform(post("/practice/start").param("typeCode", "I-N1")
                        .param("day", String.valueOf(position[0])).param("slot", String.valueOf(position[1]))
                        .sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(redirectedUrl("/practice/prepare?typeCode=I-N1"))
                .andExpect(flash().attributeExists("practiceError"));
        assertFalse(doneKeys(learner).contains(position[0] + ":" + position[1]));
    }

    @SuppressWarnings("unchecked")
    private List<String> doneKeys(UserDTO learner) {
        return (List<String>) roadmaps.view(roadmaps.ensure(learner.getEmail(), Track.TOPIK_II)).get("done");
    }

    @SuppressWarnings("unchecked")
    private int[] practiceSlot(UserDTO learner, String templateCode) {
        var view = roadmaps.view(roadmaps.ensure(learner.getEmail(), Track.TOPIK_II));
        List<String> done = (List<String>) view.get("done");
        for (var day : (List<Map<String, Object>>) view.get("days")) {
            var slots = (List<Map<String, Object>>) day.get("slots");
            for (int i = 0; i < slots.size(); i++) {
                if (templateCode.equals(slots.get(i).get("code")) && !done.contains(day.get("day") + ":" + i)) {
                    assertEquals("/practice/prepare?typeCode=II-N1", slots.get(i).get("href"));
                    return new int[]{(int) day.get("day"), i};
                }
            }
        }
        throw new AssertionError("No unfinished roadmap slot for " + templateCode);
    }
}
