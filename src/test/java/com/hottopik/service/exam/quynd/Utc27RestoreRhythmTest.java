package com.hottopik.service.exam.quynd;

import com.hottopik.entity.exam.StudyPlan;
import com.hottopik.entity.exam.Track;
import com.hottopik.repository.exam.StudyPlanRepository;
import com.hottopik.service.exam.LearnerRoadmapService;
import com.hottopik.service.exam.LearnerRoadmapService.RhythmOffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** UTC27 – LearnerRoadmapService.restoreRhythm(String owner): khôi phục nhịp học sau ít nhất 3 ngày học bị bỏ lỡ. */
class Utc27RestoreRhythmTest extends QuyndTestSupport {

    static final String OWNER = "qa.rhythm@example.com";

    @Autowired LearnerRoadmapService roadmaps;
    @Autowired StudyPlanRepository plans;

    /** Lịch 90 ngày TOPIK I bắt đầu từ {@code daysAgo} ngày trước. */
    private StudyPlan startedDaysAgo(int daysAgo) {
        StudyPlan p = roadmaps.open(OWNER, Track.TOPIK_I, 90, 20);
        p.setStartDate(LocalDate.now().minusDays(daysAgo));
        return plans.save(p);
    }

    @Test @DisplayName("UTCID01 N: bắt đầu 10 ngày trước, chưa học bài nào → dời ngày bắt đầu đúng số ngày nghỉ")
    void utcid01() {
        StudyPlan p = startedDaysAgo(10);
        LocalDate before = p.getStartDate();
        RhythmOffer offer = roadmaps.rhythmOffer(p);
        assertNotNull(offer);
        assertTrue(offer.days() >= 3);
        StudyPlan after = roadmaps.restoreRhythm(OWNER);
        assertEquals(before.plusDays(offer.days()), after.getStartDate());
    }

    @Test @DisplayName("UTCID02 N: sau khi khôi phục → không còn lời nhắc, tiến độ đã học giữ nguyên")
    void utcid02() {
        startedDaysAgo(10);
        roadmaps.setDone(OWNER, 1, 0, true);
        StudyPlan p = plans.findByOwnerEmail(OWNER).orElseThrow();
        assertNotNull(roadmaps.rhythmOffer(p), "học ngày 1 rồi nghỉ 9 ngày phải có lời nhắc");
        StudyPlan after = roadmaps.restoreRhythm(OWNER);
        assertEquals("1:0", after.getDoneKeys());
        assertNull(roadmaps.rhythmOffer(after));
    }

    @Test @DisplayName("UTCID03 N: lời nhắc cho biết số bài bị dời và ngày kết thúc mới lùi đúng số ngày nghỉ")
    void utcid03() {
        StudyPlan p = startedDaysAgo(10);
        RhythmOffer offer = roadmaps.rhythmOffer(p);
        assertTrue(offer.lessons() > 0);
        LocalDate end = p.getStartDate().plusDays(p.getTotalDays() - 1L);
        assertEquals(end.plusDays(offer.days()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")), offer.nextEnd());
    }

    @Test @DisplayName("UTCID04 B: lịch bắt đầu hôm nay → chưa cần khôi phục")
    void utcid04() {
        startedDaysAgo(0);
        expectError("Chưa cần khôi phục nhịp học.", () -> roadmaps.restoreRhythm(OWNER));
    }

    @Test @DisplayName("UTCID05 B: nghỉ 2 ngày học (ngay dưới mốc 3) → chưa cần khôi phục")
    void utcid05() {
        startedDaysAgo(2);
        expectError("Chưa cần khôi phục nhịp học.", () -> roadmaps.restoreRhythm(OWNER));
    }

    @Test @DisplayName("UTCID06 N: học đều đến hôm qua → chưa cần khôi phục")
    void utcid06() {
        startedDaysAgo(5);
        for (int day = 1; day <= 5; day++) roadmaps.setDone(OWNER, day, 0, true);
        expectError("Chưa cần khôi phục nhịp học.", () -> roadmaps.restoreRhythm(OWNER));
    }

    @Test @DisplayName("UTCID07 A: học viên chưa có lộ trình")
    void utcid07() {
        expectError("Bạn chưa có lộ trình để khôi phục nhịp học.", () -> roadmaps.restoreRhythm("nobody@example.com"));
    }

    @Test @DisplayName("UTCID08 B: nghỉ đúng 3 ngày học (mốc) → khôi phục, ngày 1 của lịch trở thành hôm nay")
    void utcid08() {
        startedDaysAgo(3);
        StudyPlan after = roadmaps.restoreRhythm(OWNER);
        assertEquals(LocalDate.now(), after.getStartDate());
    }
}
