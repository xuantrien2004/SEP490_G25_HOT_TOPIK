package com.hottopik.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hottopik.dto.UserDTO;
import com.hottopik.dto.WritingSubmissionDTO;
import com.hottopik.entity.User;
import com.hottopik.entity.exam.QuestionStatus;
import com.hottopik.entity.exam.WritingPrompt;
import com.hottopik.entity.WritingSubmission;
import com.hottopik.repository.UserNotificationRepository;
import com.hottopik.repository.UserRepository;
import com.hottopik.repository.WritingSubmissionRepository;
import com.hottopik.repository.exam.WritingPromptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class WritingSubmissionDraftUnitTest {

    private WritingSubmissionService service;
    private final List<WritingSubmission> inMemorySubmissions = new ArrayList<>();
    private final AtomicLong idGen = new AtomicLong(100);
    private WritingPrompt prompt51;
    private User account;

    @BeforeEach
    void setUp() {
        inMemorySubmissions.clear();

        prompt51 = new WritingPrompt();
        prompt51.setId(5101L);
        prompt51.setNo(51);
        prompt51.setStatus(QuestionStatus.ACTIVE);
        prompt51.setPromptKo("Đề bài câu 51");
        prompt51.setModelAnswer("㉠ 정답1\n㉡ 정답2");
        prompt51.setKeys1("정답1");
        prompt51.setKeys2("정답2");

        account = User.builder().id(999L).email("learner@test.com").displayName("Learner").role("STUDENT").status("ACTIVE").build();

        WritingSubmissionRepository subRepo = (WritingSubmissionRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{WritingSubmissionRepository.class},
                (proxy, method, args) -> {
                    String m = method.getName();
                    if ("findByLearnerId".equals(m)) {
                        Long uid = (Long) args[0];
                        return inMemorySubmissions.stream().filter(s -> Objects.equals(s.getLearnerId(), uid)).toList();
                    }
                    if ("findForUpdate".equals(m) || "findById".equals(m)) {
                        Long sid = (Long) args[0];
                        return inMemorySubmissions.stream().filter(s -> Objects.equals(s.getId(), sid)).findFirst();
                    }
                    if ("save".equals(m)) {
                        WritingSubmission entity = (WritingSubmission) args[0];
                        if (entity.getId() == null) {
                            entity.setId(idGen.incrementAndGet());
                            inMemorySubmissions.add(entity);
                        } else {
                            for (int i = 0; i < inMemorySubmissions.size(); i++) {
                                if (Objects.equals(inMemorySubmissions.get(i).getId(), entity.getId())) {
                                    inMemorySubmissions.set(i, entity);
                                    return entity;
                                }
                            }
                            inMemorySubmissions.add(entity);
                        }
                        return entity;
                    }
                    if ("deleteAll".equals(m)) {
                        Iterable<?> it = (Iterable<?>) args[0];
                        it.forEach(inMemorySubmissions::remove);
                        return null;
                    }
                    if ("delete".equals(m)) {
                        inMemorySubmissions.remove(args[0]);
                        return null;
                    }
                    return null;
                }
        );

        WritingPromptRepository promptRepo = (WritingPromptRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{WritingPromptRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        Long pid = (Long) args[0];
                        if (Objects.equals(pid, prompt51.getId())) return Optional.of(prompt51);
                        return Optional.empty();
                    }
                    return Optional.empty();
                }
        );

        UserRepository userRepo = (UserRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        Long uid = (Long) args[0];
                        if (Objects.equals(uid, account.getId())) return Optional.of(account);
                        return Optional.empty();
                    }
                    return Optional.empty();
                }
        );

        UserNotificationRepository notifRepo = (UserNotificationRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{UserNotificationRepository.class},
                (proxy, method, args) -> null
        );

        service = new WritingSubmissionService(subRepo, promptRepo, userRepo, notifRepo, new ObjectMapper());
    }

    @Test
    void testDraftLifecycleAndNoDuplicateOnSubmit() {
        UserDTO user = new UserDTO();
        user.setId(999L);
        user.setEmail("learner@test.com");
        user.setFullName("Learner");
        user.setRole("STUDENT");

        // 1. Khi đang làm dở, gọi saveDraft
        WritingSubmissionDTO draft = service.saveDraft(user, prompt51.getId(), "㉠ 첫째\n㉡ 둘째", 120);
        assertNotNull(draft);
        assertEquals("draft", draft.getStatus());
        assertEquals("Đang làm dở", draft.getDueDeadline());

        // getDraft trả về đúng bài nháp
        WritingSubmissionDTO fetchedDraft = service.getDraft(user.getId(), 51);
        assertNotNull(fetchedDraft);
        assertEquals("㉠ 첫째\n㉡ 둘째", fetchedDraft.getStudentAnswer());

        // 2. Nộp bài và xem đáp án (teacher = false)
        WritingSubmissionDTO submitted = service.submit(user, prompt51.getId(), "㉠ 첫째\n㉡ 둘째", false);
        assertNotNull(submitted);
        assertEquals("submitted", submitted.getStatus());

        // Sau khi nộp, bản nháp phải bị xoá bỏ
        WritingSubmissionDTO afterSubmitDraft = service.getDraft(user.getId(), 51);
        assertNull(afterSubmitDraft, "Sau khi nộp bài, không được lưu bản nháp của lần làm này");

        // Danh sách bài nộp của học viên chỉ có đúng 1 bản ghi duy nhất có trạng thái submitted, không có bản ghi draft
        List<WritingSubmissionDTO> allSubs = service.getSubmissionsByLearner(user.getId());
        assertEquals(1, allSubs.size(), "Chỉ có 1 bản ghi duy nhất sau khi nộp");
        assertEquals("submitted", allSubs.get(0).getStatus());

        // 3. Nếu browser gửi beacon với nội dung vừa submit, saveDraft phải bỏ qua (trả về null)
        WritingSubmissionDTO beaconDraft = service.saveDraft(user, prompt51.getId(), "㉠ 첫째\n㉡ 둘째", 125);
        assertNull(beaconDraft, "Beacon lưu nháp sau khi submit phải bị bỏ qua");

        // 4. Lần làm mới hoàn toàn trống
        WritingSubmissionDTO freshDraft = service.getDraft(user.getId(), 51);
        assertNull(freshDraft, "Lần làm mới không còn lưu nội dung bài làm của lần trước");
    }

    @Test
    void testStaleDraftFromPreviousSessionIsCleaned() throws Exception {
        UserDTO user = new UserDTO();
        user.setId(999L);
        user.setEmail("learner@test.com");
        user.setFullName("Learner");
        user.setRole("STUDENT");

        // Giả lập trạng thái lỗi cũ: đã nộp bài nhưng vẫn còn 1 bản ghi draft cùng nội dung trong DB
        service.submit(user, prompt51.getId(), "㉠ 정답A\n㉡ 정답B", false);
        assertEquals(1, inMemorySubmissions.size());

        // Chèn thêm 1 bản ghi draft cũ cùng nội dung do beacon trước kia lưu
        WritingSubmission staleDraft = new WritingSubmission();
        staleDraft.setId(idGen.incrementAndGet());
        staleDraft.setUserId(user.getId());
        staleDraft.setLearnerId(user.getId());
        staleDraft.setPromptId(prompt51.getId());
        staleDraft.setStatus("DRAFT");
        WritingSubmissionDTO staleDto = new WritingSubmissionDTO();
        staleDto.setId(staleDraft.getId().toString());
        staleDto.setLearnerId(user.getId());
        staleDto.setPromptId(prompt51.getId());
        staleDto.setQuestionNo(51);
        staleDto.setStatus("draft");
        staleDto.setStudentAnswer("㉠ 정답A\n㉡ 정답B");
        staleDto.setSubmittedAt("18/11/2026 16:50");
        try {
            staleDraft.setPayload(new ObjectMapper().writeValueAsString(staleDto));
        } catch (Exception ignored) {}
        inMemorySubmissions.add(staleDraft);

        assertEquals(2, inMemorySubmissions.size(), "Giả lập có 2 bản ghi: 1 submitted và 1 draft cũ");

        // Khi gọi getDraft(user.getId(), 51) -> hệ thống phải tự động phát hiện trùng lặp với bài đã nộp, xoá draft và trả về null
        WritingSubmissionDTO result = service.getDraft(user.getId(), 51);
        assertNull(result, "Phải tự động dọn dẹp bản nháp cũ trùng lặp và trả về null");

        // Bản ghi stale draft trong DB đã bị xoá
        assertEquals(1, inMemorySubmissions.size(), "DB chỉ còn 1 bản ghi duy nhất đã nộp");
        WritingSubmissionDTO remaining = new ObjectMapper().readValue(inMemorySubmissions.get(0).getPayload(), WritingSubmissionDTO.class);
        assertEquals("submitted", remaining.getStatus());
    }

    @Test
    void testDraftDoesNotCrossPrompts() {
        UserDTO user = new UserDTO();
        user.setId(999L);
        user.setEmail("learner@test.com");
        user.setFullName("Learner");
        user.setRole("STUDENT");

        // Lưu nháp cho prompt 5101L
        service.saveDraft(user, prompt51.getId(), "Bài nháp của đề 5101", 60);

        // Đề 5101 có draft
        WritingSubmissionDTO draft5101 = service.getDraft(user.getId(), 51, 5101L);
        assertNotNull(draft5101);
        assertEquals("Bài nháp của đề 5101", draft5101.getStudentAnswer());

        // Đề khác (5102L) cùng là câu 51 nhưng KHÔNG được lấy bài nháp của 5101
        WritingSubmissionDTO draft5102 = service.getDraft(user.getId(), 51, 5102L);
        assertNull(draft5102, "Đề khác không được hiển thị bài nháp của đề trước");
    }

    @Test
    void testSaveGradingDraftKeepsClaimedStatusAndPreservesPartialWork() {
        UserDTO learner = new UserDTO();
        learner.setId(999L);
        learner.setEmail("learner@test.com");
        learner.setFullName("Learner");
        learner.setRole("STUDENT");
        learner.setVip(true);

        UserDTO teacher = new UserDTO();
        teacher.setId(777L);
        teacher.setEmail("teacher@test.com");
        teacher.setFullName("Cô Mai");
        teacher.setRole("MENTOR");

        WritingSubmissionDTO submitted = service.submit(learner, prompt51.getId(), "㉠ 첫째\n㉡ 둘째", true);
        assertTrue(service.claimSubmission(submitted.getId(), teacher));

        Map<String, Integer> partialCriteria = new LinkedHashMap<>();
        partialCriteria.put("Chỗ trống ㉠", 4);
        boolean saved = service.saveGradingDraft(
                submitted.getId(), teacher, partialCriteria, null,
                "Nhận xét tạm thời...", "Đáp án mẫu nháp", null, null);
        assertTrue(saved);

        WritingSubmissionDTO reloaded = service.getById(submitted.getId()).orElseThrow();
        assertEquals("claimed", reloaded.getStatus(), "Lưu nháp không được đổi sang trạng thái graded");
        assertEquals(4, reloaded.getTotalScore());
        assertEquals("Nhận xét tạm thời...", reloaded.getOverallFeedback());
        assertEquals("Đáp án mẫu nháp", reloaded.getModelAnswer());
        assertEquals(4, reloaded.getCriteriaScores().get(0).getScore());
        assertEquals(0, reloaded.getCriteriaScores().get(1).getScore());
    }
}
