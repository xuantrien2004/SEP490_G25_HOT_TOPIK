package com.hottopik.service.exam;

import com.hottopik.dto.UserDTO;
import com.hottopik.entity.exam.Track;
import com.hottopik.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Người học tạo lịch từ bản lộ trình đã phát hành và đánh dấu bài xong. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LearnerRoadmapFlowTest {

    @Autowired MockMvc mvc;
    @Autowired LearnerRoadmapService roadmaps;

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

        mvc.perform(get("/luyen-tap/chuan-bi").param("name", "Nghe và chọn tranh hoặc biểu đồ").param("questions", "8")
                        .param("day", "2").param("slot", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nghe và chọn tranh hoặc biểu đồ")))
                .andExpect(content().string(containsString("8 câu")))
                .andExpect(content().string(containsString("day=2")));

        mvc.perform(get("/practice/result").param("day", "3").param("slot", "0")
                        .sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("đã được tính vào lộ trình")))
                .andExpect(content().string(not(containsString("Đánh dấu xong"))));

        mvc.perform(get("/lo-trinh").sessionAttr(AuthHelper.SESSION_KEY, learner))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("3:0")));
    }
}
