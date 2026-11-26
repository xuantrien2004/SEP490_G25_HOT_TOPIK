package com.hottopik.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.PracticeAttemptRepository;
import com.hottopik.repository.exam.QuestionRepository;
import com.hottopik.service.exam.QuestionTypeCatalog;
import com.hottopik.service.exam.QuestionTypeDef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PracticeService {

    public record QuestionView(Long id, int no, String prompt, List<String> options, int answer,
                               String explanation, String passage, String script, String audio, String image,
                               String mediaBase, int originalNo) {
        public QuestionView(Long id, int no, String prompt, List<String> options, int answer,
                            String explanation, String passage, String script, String audio, String image,
                            String mediaBase) {
            this(id, no, prompt, options, answer, explanation, passage, script, audio, image, mediaBase, no);
        }
    }
    public record AnswerView(QuestionView question, Integer selected, boolean correct) {}
    public record ResultView(PracticeAttempt attempt, QuestionTypeDef type, List<AnswerView> answers,
                             String duration, List<PracticeAttempt> history) {}
    public record TypeSummary(QuestionTypeDef type, long available, long completed, Integer bestPercent,
                              Integer latestPercent) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final QuestionRepository questions;
    private final PracticeAttemptRepository attempts;
    private final com.hottopik.repository.exam.WritingPromptRepository writingPrompts;
    private final WritingSubmissionService writingSubmissions;

    public PracticeService(QuestionRepository questions,
                           PracticeAttemptRepository attempts,
                           com.hottopik.repository.exam.WritingPromptRepository writingPrompts,
                           WritingSubmissionService writingSubmissions) {
        this.questions = questions;
        this.attempts = attempts;
        this.writingPrompts = writingPrompts;
        this.writingSubmissions = writingSubmissions;
    }

    @Transactional(readOnly = true)
    public List<TypeSummary> summaries(Long userId, Track track, Skill skill) {
        return QuestionTypeCatalog.forTrackAndSkill(track, skill).stream().map(type -> {
            List<Question> pool = type.isWriting() ? List.of() : pool(type);
            List<PracticeAttempt> history = userId == null ? List.of()
                    : attempts.findByUserIdAndTypeCodeAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId, type.code());
            Integer best = history.stream().mapToInt(a -> percent(a.getScore(), a.getTotal())).max().stream().boxed().findFirst().orElse(null);
            Integer latest = history.isEmpty() ? null : percent(history.get(0).getScore(), history.get(0).getTotal());
            return new TypeSummary(type, pool.size(), history.size(), best, latest);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<TypeSummary> allSummaries(Long userId) {
        return allSummaries(userId, null);
    }

    @Transactional(readOnly = true)
    /*
     * Danh mục dạng luôn lấy từ catalog nên vẫn có dạng để hiển thị dù ngân hàng chưa có câu.
     * Nghe/Đọc: đếm pool hợp lệ và lịch sử PracticeAttempt; Viết: lấy WritingPrompt ACTIVE và bài đã nộp theo userId.
     * best/latest là phần trăm đã tính ở server; available chỉ hỗ trợ kiểm tra khả dụng, không bắt buộc hiện số lượng kho.
     */
    public List<TypeSummary> allSummaries(Long userId, String email) {
        return QuestionTypeCatalog.all().stream().map(type -> {
            if (type.isWriting()) {
                int qNo = type.numbers().get(0);
                long available = writingPrompts != null
                        ? writingPrompts.findByNoAndStatusOrderByIdDesc(qNo, QuestionStatus.ACTIVE).size()
                        : 0;
                List<com.hottopik.dto.WritingSubmissionDTO> mySubs = (email != null && writingSubmissions != null)
                        ? writingSubmissions.getSubmissionsByLearner(userId).stream().filter(s -> s.getQuestionNo() == qNo).toList()
                        : List.of();
                long completed = mySubs.size();
                Integer latestPercent = null;
                Integer bestPercent = null;
                if (!mySubs.isEmpty()) {
                    List<Integer> percents = mySubs.stream().map(s -> {
                        int score = s.getTotalScore() != null ? s.getTotalScore() : (s.getAiScore() != null ? s.getAiScore() : 0);
                        int max = s.getMaxScore() > 0 ? s.getMaxScore() : (qNo == 53 ? 30 : (qNo == 54 ? 50 : 10));
                        return percent(score, max);
                    }).toList();
                    latestPercent = percents.get(0);
                    bestPercent = percents.stream().max(Integer::compareTo).orElse(null);
                }
                return new TypeSummary(type, available, completed, null, null);
            }
            List<Question> pool = pool(type);
            List<PracticeAttempt> history = userId == null ? List.of()
                    : attempts.findByUserIdAndTypeCodeAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId, type.code());
            Integer best = history.stream().mapToInt(a -> percent(a.getScore(), a.getTotal())).max().stream().boxed().findFirst().orElse(null);
            Integer latest = history.isEmpty() ? null : percent(history.get(0).getScore(), history.get(0).getTotal());
            return new TypeSummary(type, pool.size(), history.size(), best, latest);
        }).toList();
    }

    @Transactional
    public PracticeAttempt start(Long userId, String email, String typeCode) {
        return start(userId, email, typeCode, 0);
    }

    /* =========================================================================================
     * LUỒNG NGHIỆP VỤ SERVICE: KHỞI TẠO LƯỢT LUYỆN DẠNG CÂU HỎI (PRACTICE SESSION INITIALIZATION)
     * -----------------------------------------------------------------------------------------
     * 1. XUẤT PHÁT:
     *    - Controller gọi: com.hottopik.controller.PracticeController.startPractice()
     * 2. LOGIC NGHIỆP VỤ SERVICE:
     *    - Kiểm tra loại dạng: không áp dụng cho Dạng Viết (ném IllegalArgumentException).
     *    - Lấy pool câu hỏi ACTIVE của dạng từ com.hottopik.repository.exam.QuestionRepository.
     *    - Loại trừ các câu hỏi đang nằm trong các lượt làm dở (pending attempts có hoạt động).
     *    - Nhóm câu hỏi theo Group ID để bảo toàn tính trọn vẹn ngữ cảnh (bài đọc chung / file nghe chung).
     *    - Ưu tiên chọn các nhóm câu chưa từng làm (unseen groups), nếu thiếu mới lấy nhóm đã làm.
     *    - Đóng gói danh sách Question IDs thành chuỗi phân cách dấu phẩy (vd: "101,102,103...").
     * 3. TRUY VẤN CSDL:
     *    - SELECT câu hỏi từ dbo.questions WHERE type_code = :typeCode AND status = 'ACTIVE'
     *    - SELECT các lượt dở từ dbo.practice_attempts WHERE user_id = :userId AND completed_at IS NULL
     *    - INSERT INTO dbo.practice_attempts (user_id, type_code, question_ids, started_at...)
     * 4. TRẢ KẾT QUẢ VỀ CONTROLLER:
     *    - Trả về đối tượng Entity PracticeAttempt mới được lưu, có attemptId sinh từ sequence.
     * ========================================================================================= */
    @Transactional
    public PracticeAttempt start(Long userId, String email, String typeCode, int questionCount) {
        if (userId == null) {
            userId = 1L;
            email = "learner@hottopik.com";
        }
        QuestionTypeDef type = type(typeCode);
        if (type.isWriting()) throw new IllegalArgumentException("Dạng Viết được làm trong khu vực luyện Viết.");
        List<Question> available = pool(type);
        if (available.isEmpty()) throw new IllegalStateException("Ngân hàng câu hỏi chưa có câu hỏi đang hoạt động cho dạng " + type.name() + " (" + typeCode + "). Vui lòng liên hệ Content Manager hoặc nạp đề.");

        int defaultCount = type.requiredCount();
        int targetCount = (questionCount <= defaultCount) ? defaultCount : questionCount;
        int blockSize = type.blocks().isEmpty() ? 1 : type.blocks().get(0).size();
        int blocksNeeded = (int) Math.ceil((double) targetCount / blockSize);

        // Khong lay cac cau o lan dang lam do (completedAt is null va co hoat dong)
        List<PracticeAttempt> pendingAttempts = attempts.findByUserIdAndTypeCodeAndCompletedAtIsNullOrderByIdDesc(userId, typeCode);
        Set<Long> pendingQuestionIds = pendingAttempts.stream()
                .filter(this::hasActivity)
                .flatMap(a -> parseIds(a.getQuestionIds()).stream()).collect(Collectors.toSet());

        List<Question> candidatePool = available.stream()
                .filter(q -> !pendingQuestionIds.contains(q.getId()))
                .toList();
        if (candidatePool.isEmpty()) {
            candidatePool = available;
        }

        Set<Long> seen = attempts.findByUserIdAndTypeCodeAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId, typeCode)
                .stream().flatMap(a -> parseIds(a.getQuestionIds()).stream()).collect(Collectors.toSet());
        Map<Long, List<Question>> byGroup = candidatePool.stream().collect(Collectors.groupingBy(q -> q.getGroup().getId()));
        Map<Long, List<Question>> bySource = candidatePool.stream().collect(Collectors.groupingBy(q -> q.getSource().getId()));

        List<List<Question>> allMatchingGroups = new ArrayList<>();
        for (List<Integer> block : type.blocks()) {
            Set<Integer> blockNumbers = new HashSet<>(block);
            List<List<Question>> matchingGroups = byGroup.values().stream()
                    .filter(group -> group.stream().map(Question::getOriginalNo).collect(Collectors.toSet()).containsAll(blockNumbers))
                    .map(group -> group.stream().filter(q -> blockNumbers.contains(q.getOriginalNo()))
                            .sorted(Comparator.comparingInt(Question::getOriginalNo)).toList())
                    .filter(group -> group.stream().map(Question::getOriginalNo).distinct().count() == block.size())
                    .collect(Collectors.toCollection(ArrayList::new));
            if (type.skill() == Skill.READING && block.size() > 1) {
                bySource.values().stream()
                        .map(source -> source.stream().filter(q -> blockNumbers.contains(q.getOriginalNo()))
                                .sorted(Comparator.comparingInt(Question::getOriginalNo)).toList())
                        .filter(group -> group.size() == block.size()
                                && group.stream().map(Question::getOriginalNo).distinct().count() == block.size()
                                && group.stream().allMatch(q -> q.getGroup().getSize() == 1))
                        .forEach(matchingGroups::add);
            }
            allMatchingGroups.addAll(matchingGroups);
        }
        if (allMatchingGroups.isEmpty()) {
            throw new IllegalStateException("Dạng câu này hiện chưa có bài luyện đầy đủ. Bạn có thể chọn dạng câu khác.");
        }

        List<List<Question>> unseenGroups = allMatchingGroups.stream()
                .filter(group -> group.stream().noneMatch(q -> seen.contains(q.getId())))
                .collect(Collectors.toCollection(ArrayList::new));
        List<List<Question>> seenGroups = allMatchingGroups.stream()
                .filter(group -> group.stream().anyMatch(q -> seen.contains(q.getId())))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(unseenGroups);
        Collections.shuffle(seenGroups);

        List<List<Question>> chosenGroups = new ArrayList<>();
        Set<Long> usedGroupIds = new HashSet<>();
        for (List<Question> grp : unseenGroups) {
            if (chosenGroups.size() >= blocksNeeded) break;
            long gid = grp.get(0).getGroup().getId();
            if (usedGroupIds.contains(gid)) continue;
            usedGroupIds.add(gid);
            chosenGroups.add(grp);
        }
        // Neu da het cau chua lam trong ngan hang, lay cac nhom tu cac lan da lam khac nhau va tron ngau nhien
        if (chosenGroups.size() < blocksNeeded) {
            for (List<Question> grp : seenGroups) {
                if (chosenGroups.size() >= blocksNeeded) break;
                long gid = grp.get(0).getGroup().getId();
                if (usedGroupIds.contains(gid)) continue;
                usedGroupIds.add(gid);
                chosenGroups.add(grp);
            }
        }
        // Xao tron thu tu cac nhom de khong bi trung lap nguyen de/thu tu cu
        Collections.shuffle(chosenGroups);

        List<Question> selected = new ArrayList<>();
        for (List<Question> grp : chosenGroups) {
            if (selected.size() >= targetCount) break;
            selected.addAll(grp);
        }
        if (targetCount == defaultCount && selected.size() != defaultCount) {
            throw new IllegalStateException("Dạng câu này hiện chưa có bài luyện đầy đủ. Bạn có thể chọn dạng câu khác.");
        }
        if (selected.isEmpty()) {
            throw new IllegalStateException("Ngân hàng câu hỏi chưa đủ câu cho dạng này. Vui lòng chọn số câu nhỏ hơn.");
        }
        if (blockSize == 1 && selected.size() > targetCount) {
            selected = new ArrayList<>(selected.subList(0, targetCount));
        }

        PracticeAttempt attempt = new PracticeAttempt();
        attempt.setUserId(userId);
        attempt.setUserEmail(email);
        attempt.setTrack(type.track());
        attempt.setSkill(type.skill());
        attempt.setTypeCode(type.code());
        attempt.setQuestionIds(selected.stream().map(q -> q.getId().toString()).collect(Collectors.joining(",")));
        attempt.setTotal(selected.size());
        attempt.setAnswersJson("{}");
        attempt.setScore(0);
        return attempts.save(attempt);
    }

    /**
     * Làm lại đúng bài luyện tập này (giữ nguyên danh sách câu hỏi cũ để học viên ôn tập kiểm tra lại kiến thức)
     */
    @Transactional
    /*
     * Tìm lượt cũ theo cả ID và userId, sao chép danh sách câu sang lượt mới; đáp án và điểm bắt đầu rỗng/0.
     */
    public PracticeAttempt retry(Long userId, String email, Long attemptId) {
        if (userId == null) {
            userId = 1L;
            email = "learner@hottopik.com";
        }
        PracticeAttempt original = attempts.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài làm cần làm lại."));

        PracticeAttempt newAttempt = new PracticeAttempt();
        newAttempt.setUserId(userId);
        newAttempt.setUserEmail(email);
        newAttempt.setTrack(original.getTrack());
        newAttempt.setSkill(original.getSkill());
        newAttempt.setTypeCode(original.getTypeCode());
        newAttempt.setQuestionIds(original.getQuestionIds());
        newAttempt.setTotal(original.getTotal());
        newAttempt.setAnswersJson("{}");
        newAttempt.setScore(0);
        return attempts.save(newAttempt);
    }

    @Transactional
    public PracticeAttempt save(Long userId, Long attemptId, Map<String, String> submitted, int elapsedSeconds) {
        return save(userId, attemptId, submitted, List.of(), elapsedSeconds, false);
    }

    @Transactional
    public PracticeAttempt save(Long userId, Long attemptId, Map<String, String> submitted, List<String> flags, int elapsedSeconds) {
        return save(userId, attemptId, submitted, flags, elapsedSeconds, false);
    }

    @Transactional
    public PracticeAttempt save(Long userId, Long attemptId, Map<String, String> submitted, List<String> flags, int elapsedSeconds, boolean pause) {
        if (userId == null) userId = 1L;
        PracticeAttempt attempt = attempts.findOwnedForUpdate(attemptId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lượt luyện tập."));
        if (attempt.getCompletedAt() != null) return attempt;

        List<Long> ids = parseIds(attempt.getQuestionIds());
        Map<String, Integer> answers = new LinkedHashMap<>();
        Map<String, String> checked = com.hottopik.service.exam.CheckedAnswers.read(attempt.getCheckedAnswersJson());
        for (Long id : ids) {
            String raw = checked.getOrDefault(String.valueOf(id), submitted.get(String.valueOf(id)));
            if (raw == null) raw = submitted.get("answer_" + id);
            Integer selected = null;
            try { if (raw != null) selected = Integer.parseInt(raw); } catch (NumberFormatException ignored) { }
            if (selected != null && selected >= 1 && selected <= 4) answers.put(id.toString(), selected);
        }

        List<String> validFlags = flags != null ? flags.stream()
                .filter(f -> f != null && !f.isBlank())
                .filter(f -> {
                    try { return ids.contains(Long.parseLong(f.trim())); } catch (Exception ex) { return false; }
                })
                .distinct().toList() : List.of();

        // CHỈ khi người dùng THOÁT / TẠM DỪNG (pause == true) mà chưa làm câu nào và không có cờ Chưa chắc thì mới xóa lượt
        if (pause && answers.isEmpty() && validFlags.isEmpty()) {
            attempts.delete(attempt);
            return null;
        }

        attempt.setAnswersJson(writeJson(answers));
        attempt.setFlagsJson(writeJson(validFlags));
        if (elapsedSeconds > 0) {
            attempt.setElapsedSeconds(elapsedSeconds);
        }
        return attempts.saveAndFlush(attempt);
    }

    @Transactional
    /*
     * Chấm ở server trên questionIds đã lưu, không tin danh sách câu/điểm client gửi lên.
     * Chỉ nhận lựa chọn 1..4; câu trống/giá trị sai coi như chưa trả lời. Mỗi câu đúng cộng 1.
     * answersJson lưu lựa chọn, score lưu số đúng, completedAt khóa trạng thái đã nộp; gọi nộp lại trả kết quả cũ.
     */
    public PracticeAttempt submit(Long userId, Long attemptId, Map<String, String> submitted) {
        return submit(userId, attemptId, submitted, 0);
    }

    @Transactional
    public PracticeAttempt submit(Long userId, Long attemptId, Map<String, String> submitted, int elapsedSeconds) {
        if (userId == null) userId = 1L;
        PracticeAttempt attempt = attempts.findOwnedForUpdate(attemptId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lượt luyện tập."));
        if (attempt.getCompletedAt() != null) return attempt;

        List<Long> ids = parseIds(attempt.getQuestionIds());
        List<Question> questions = this.questions.findAllById(ids);
        Map<Long, Question> byId = questions.stream().collect(Collectors.toMap(Question::getId, q -> q));
        Map<String, Integer> answers = new LinkedHashMap<>();
        Map<String, String> checked = com.hottopik.service.exam.CheckedAnswers.read(attempt.getCheckedAnswersJson());
        int score = 0;
        for (Long id : ids) {
            String raw = checked.getOrDefault(String.valueOf(id), submitted.get(String.valueOf(id)));
            if (raw == null) raw = submitted.get("answer_" + id);
            Integer selected = null;
            try { if (raw != null) selected = Integer.parseInt(raw); } catch (NumberFormatException ignored) { }
            if (selected != null && (selected < 1 || selected > 4)) selected = null;
            if (selected != null) answers.put(id.toString(), selected);
            Question q = byId.get(id);
            if (q != null && selected != null && q.getAnswerIndex() == selected) score++;
        }
        attempt.setAnswersJson(writeJson(answers));
        attempt.setScore(score);
        attempt.setCompletedAt(LocalDateTime.now());
        if (elapsedSeconds > 0) {
            attempt.setElapsedSeconds(elapsedSeconds);
        }
        return attempts.saveAndFlush(attempt);
    }

    @Transactional(readOnly = true)
    public List<QuestionView> questions(PracticeAttempt attempt) {
        Map<Long, Question> byId = this.questions.findWithMediaByIdIn(parseIds(attempt.getQuestionIds())).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        List<Question> ordered = parseIds(attempt.getQuestionIds()).stream().map(byId::get).filter(Objects::nonNull).toList();
        List<QuestionView> views = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            views.add(view(ordered.get(i), i + 1));
        }
        return views;
    }

    /* =========================================================================================
     * LUỒNG NGHIỆP VỤ SERVICE: CHECK ĐÁP ÁN & KHÓA LỰA CHỌN TỨC THÌ (INSTANT CHECK & LOCK)
     * -----------------------------------------------------------------------------------------
     * 1. XUẤT PHÁT:
     *    - Controller gọi: com.hottopik.controller.PracticeController.check()
     * 2. LOGIC NGHIỆP VỤ SERVICE:
     *    - Khóa bi quan (Pessimistic Lock) lượt làm thông qua findOwnedForUpdate(attemptId, userId)
     *      để ngăn chặn gian lận gửi đồng thời nhiều đáp án từ các tab trình duyệt khác nhau.
     *    - Kiểm tra: lựa chọn hợp lệ (1..4), câu hỏi thuộc lượt làm này, bài chưa nộp (completedAt == null).
     *    - Đọc checked_answers_json hiện tại, khóa vĩnh viễn lựa chọn lần đầu (putIfAbsent).
     *    - Lưu lại chuỗi JSON đã cập nhật vào PracticeAttempt.
     *    - Truy vấn Question để lấy answerIndex, explanationVi và scriptKo (cho bài Nghe).
     * 3. TRUY VẤN CSDL:
     *    - SELECT ... FROM dbo.practice_attempts WITH (UPDLOCK, ROWLOCK) WHERE id = :attemptId AND user_id = :userId
     *    - UPDATE dbo.practice_attempts SET checked_answers_json = :json WHERE id = :attemptId
     *    - SELECT * FROM dbo.questions WHERE id = :questionId
     * 4. TRẢ KẾT QUẢ VỀ CONTROLLER:
     *    - Trả về com.hottopik.dto.QuestionFeedback(answerIndex, isCorrect, explanationVi, scriptKo, audio, selected).
     * ========================================================================================= */
    @Transactional
    public com.hottopik.dto.QuestionFeedback check(Long userId, Long attemptId, Long questionId, int selected) {
        if (userId == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        PracticeAttempt attempt = attempts.findOwnedForUpdate(attemptId, userId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        if (selected < 1 || selected > 4 || !parseIds(attempt.getQuestionIds()).contains(questionId)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (attempt.getCompletedAt() != null) throw new IllegalArgumentException("Bài đã nộp.");
        Map<String,String> locked = com.hottopik.service.exam.CheckedAnswers.read(attempt.getCheckedAnswersJson());
        locked.putIfAbsent(questionId.toString(), String.valueOf(selected));
        selected = Integer.parseInt(locked.get(questionId.toString()));
        attempt.setCheckedAnswersJson(com.hottopik.service.exam.CheckedAnswers.write(locked));
        attempts.save(attempt);
        Question q = questions.findById(questionId).orElseThrow();
        return new com.hottopik.dto.QuestionFeedback(q.getAnswerIndex(), q.getAnswerIndex() == selected,
                q.getExplanationVi(), attempt.getSkill() == Skill.LISTENING && q.getGroup() != null ? q.getGroup().getScriptKo() : null, null, selected);
    }

    @Transactional(readOnly = true)
    /*
     * Ghép đáp án đã lưu với câu trong ngân hàng theo ID để tạo AnswerView(selected, correct).
     * Nội dung/giải thích được đọc từ ngân hàng hiện tại; đây chưa phải bản chụp nội dung bất biến tại thời điểm thi.
     * Tính thời lượng từ mốc server và lấy lịch sử đã nộp cùng userId + typeCode để template hiển thị.
     */
    public ResultView result(Long userId, Long attemptId) {
        if (userId == null) userId = 1L;
        PracticeAttempt attempt = attempts.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kết quả."));
        Map<String, Integer> selected = readAnswers(attempt.getAnswersJson());
        Map<Long, Question> byId = questions.findWithMediaByIdIn(parseIds(attempt.getQuestionIds())).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        List<Question> ordered = parseIds(attempt.getQuestionIds()).stream().map(byId::get).filter(Objects::nonNull).toList();
        List<AnswerView> answerViews = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Question q = ordered.get(i);
            int no = i + 1;
            Integer sel = selected.get(q.getId().toString());
            boolean correct = sel != null
                    && (sel == q.getAnswerIndex() || com.hottopik.service.exam.CheckedAnswers.read(attempt.getCreditedAnswersJson()).containsKey(q.getId().toString()));
            answerViews.add(new AnswerView(view(q, no), sel, correct));
        }
        long dur = attempt.getElapsedSeconds() != null && attempt.getElapsedSeconds() > 0 ? attempt.getElapsedSeconds()
                : (attempt.getStartedAt() == null || attempt.getCompletedAt() == null ? 0
                : Duration.between(attempt.getStartedAt(), attempt.getCompletedAt()).toSeconds());
        String duration = formatDuration(dur);
        return new ResultView(attempt, type(attempt.getTypeCode()), answerViews,
                duration, attempts.findByUserIdAndTypeCodeAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId, attempt.getTypeCode()));
    }

    @Transactional(readOnly = true)
    public Optional<PracticeAttempt> findAttempt(Long userId, Long attemptId) {
        if (userId == null) userId = 1L;
        return attempts.findByIdAndUserId(attemptId, userId);
    }

    @Transactional
    public Optional<PracticeAttempt> findPendingAttempt(Long userId, String typeCode) {
        if (userId == null) userId = 1L;
        List<PracticeAttempt> pendings = attempts.findByUserIdAndTypeCodeAndCompletedAtIsNullOrderByIdDesc(userId, typeCode);
        for (PracticeAttempt p : pendings) {
            if (hasActivity(p)) {
                return Optional.of(p);
            } else {
                attempts.delete(p);
            }
        }
        return Optional.empty();
    }

    @Transactional
    public List<PracticeAttempt> allHistoryOfType(Long userId, String typeCode) {
        if (userId == null) userId = 1L;
        List<PracticeAttempt> list = attempts.findByUserIdAndTypeCodeOrderByStartedAtDescIdDesc(userId, typeCode);
        List<PracticeAttempt> result = new ArrayList<>();
        for (PracticeAttempt a : list) {
            if (a.getCompletedAt() != null || hasActivity(a)) {
                result.add(a);
            } else {
                attempts.delete(a);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<PracticeAttempt> historyOfType(Long userId, String typeCode) {
        if (userId == null) userId = 1L;
        return attempts.findByUserIdAndTypeCodeAndCompletedAtIsNotNullOrderByCompletedAtDesc(userId, typeCode);
    }

    public static List<String> readFlags(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try { return JSON.readValue(raw, new TypeReference<>() {}); } catch (Exception e) { return List.of(); }
    }

    public boolean hasActivity(PracticeAttempt attempt) {
        if (attempt == null) return false;
        Map<String, Integer> ans = readAnswers(attempt.getAnswersJson());
        List<String> flg = readFlags(attempt.getFlagsJson());
        return !ans.isEmpty() || !flg.isEmpty();
    }

    /**
     * Lấy câu theo vị trí chuẩn vì khuôn nhập thủ công có thể dùng ở nhiều số câu.
     */
    /*
     * Lọc theo track + skill + số câu chuẩn; không chỉ dựa typeCode nhập tay vì khuôn nhập có thể dùng ở nhiều vị trí.
     */
    private List<Question> pool(QuestionTypeDef type) {
        List<Question> list = questions.findPracticeCandidates(type.track(), type.skill(), type.numbers());
        return list.stream()
                .sorted(Comparator.comparingInt(Question::getOriginalNo))
                .toList();
    }

    private QuestionView view(Question q, int no) {
        QuestionGroup g = q.getGroup();
        String audio = g != null ? g.getAudioFile() : null;
        String image = g != null ? g.getImageFile() : null;
        ExamSource source = (g != null && g.getSource() != null) ? g.getSource() : q.getSource();
        Long sourceId = source != null ? source.getId() : null;
        String mediaBase = sourceId != null ? "/practice/media/" + sourceId + "/" : null;
        String audioUrl = (audio != null && !audio.isBlank() && mediaBase != null)
                ? mediaBase + org.springframework.web.util.UriUtils.encodePathSegment(audio, java.nio.charset.StandardCharsets.UTF_8)
                : null;
        String imageUrl = (image != null && !image.isBlank() && mediaBase != null)
                ? mediaBase + org.springframework.web.util.UriUtils.encodePathSegment(image, java.nio.charset.StandardCharsets.UTF_8)
                : null;
        return new QuestionView(q.getId(), no, q.getPromptKo(), q.options(), q.getAnswerIndex(),
                q.getExplanationVi(), g != null ? g.getPassageKo() : null, g != null ? g.getScriptKo() : null,
                audioUrl, imageUrl, mediaBase, q.getOriginalNo());
    }

    private QuestionView view(Question q) {
        return view(q, q.getOriginalNo());
    }

    private QuestionTypeDef type(String code) {
        return QuestionTypeCatalog.byCode(code).orElseThrow(() -> new IllegalArgumentException("Dạng câu không hợp lệ: " + code));
    }

    private static int percent(int score, int total) { return total == 0 ? 0 : Math.round(score * 100f / total); }
    private static List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(",")).map(Long::parseLong).toList();
    }
    private static String writeJson(Object value) {
        try { return JSON.writeValueAsString(value); } catch (Exception e) { return "{}"; }
    }
    public static Map<String, Integer> readAnswers(String raw) {
        try { return JSON.readValue(raw, new TypeReference<>() {}); } catch (Exception e) { return Map.of(); }
    }
    private static String formatDuration(long seconds) {
        long elapsed = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%02d:%02d", elapsed / 60, elapsed % 60);
    }
}
