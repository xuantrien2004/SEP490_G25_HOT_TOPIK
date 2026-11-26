package com.hottopik.dto;

import com.hottopik.entity.exam.Skill;
import com.hottopik.service.PracticeService;
import com.hottopik.service.exam.ExamImportService;
import com.hottopik.service.exam.LearnerExamService;
import java.util.List;
import java.util.Locale;

/** Presentation adapter; form keys and submission endpoints stay with their existing flows. */
public record QuestionPlayerItem(String key, Long id, int no, String skill, String label,
        String instruction, String prompt, String passage, String audio, String image,
        List<LearnerExamService.Choice> options, String selected, String checkUrl,
        Integer answer, String explanation, String script, String groupKey) {

    public QuestionPlayerItem(String key, Long id, int no, String skill, String label,
            String instruction, String prompt, String passage, String audio, String image,
            List<LearnerExamService.Choice> options, String selected, String checkUrl,
            Integer answer, String explanation, String script) {
        this(key, id, no, skill, label, instruction, prompt, passage, audio, image, options, selected, checkUrl, answer, explanation, script, null);
    }

    public String resolveGroupKey() {
        if (groupKey != null && !groupKey.isBlank()) return groupKey;
        if (audio != null && !audio.isBlank()) return audio;
        if (passage != null && !passage.isBlank()) return "p_" + Math.abs(passage.trim().replaceAll("\\s+", " ").hashCode());
        if (image != null && !image.isBlank()) return image;
        return "ng_" + key;
    }

    /*
     * Adapter giao diện luyện dạng: giữ key answer_<questionId>, nhận diện phương án ảnh và cấp URL Kiểm tra; không nhúng đáp án ban đầu.
     */
    public static QuestionPlayerItem practice(PracticeService.QuestionView q, Skill skill,
                                              String instruction, Long attemptId, String selected, String groupKey) {
        return new QuestionPlayerItem("answer_" + q.id(), q.id(), q.no(), skill.name(),
                skill.getLabel(), instruction, q.prompt(), q.passage(), q.audio(), q.image(),
                q.options().stream().map(o -> choice(o, q.mediaBase())).toList(), selected,
                "/practice/check/" + attemptId + "/" + q.id(), null, null, null, groupKey);
    }

    public static QuestionPlayerItem practice(PracticeService.QuestionView q, Skill skill,
                                              String instruction, Long attemptId) {
        return practice(q, skill, instruction, attemptId, null, null);
    }

    /*
     * Adapter đề thi giữ nguyên key q<ExamItem.id> và lựa chọn đã autosave; che tên file ở phương án ảnh, không thay logic chấm.
     */
    public static QuestionPlayerItem exam(LearnerExamService.QuestionView q, Long attemptId) {
        return new QuestionPlayerItem(q.key(), q.id(), q.no(), q.skill().name(), q.skill().getLabel(),
                q.instruction(), q.prompt(), q.passage(), q.audio(), q.image(),
                q.options().stream().map(o -> new LearnerExamService.Choice(o.image() == null ? o.text() : null, o.image())).toList(), q.selected(),
                "/exam/attempt/" + attemptId + "/check/" + q.key(), null, null, null);
    }

    /*
     * Bản xem như học viên lấy trực tiếp dữ liệu biên soạn, dùng key preview riêng; đáp án/lời giải có sẵn để kiểm tra tại chỗ, không tạo attempt.
     */
    public static QuestionPlayerItem preview(ExamImportService.LearnerBai bai,
                                            ExamImportService.LearnerAsk ask, Long sourceId, int index) {
        String base = "/content/media/" + sourceId + "/";
        Integer answer = ask.answer() != null && ask.answer().matches("[1-4]") ? Integer.valueOf(ask.answer()) : null;
        String img = (ask.image() != null && !ask.image().isBlank()) ? ask.image() : bai.image();
        return new QuestionPlayerItem("preview_" + index, null, ask.no(), bai.skill().name(),
                bai.skill().getLabel(), bai.instructionKo(), ask.prompt(), bai.passage(),
                media(base, bai.audio()), media(base, img),
                java.util.Arrays.asList(ask.opt1(), ask.opt2(), ask.opt3(), ask.opt4()).stream().map(o -> choice(o, base)).toList(),
                null, null, answer, ask.explanation(), bai.script());
    }

    private static LearnerExamService.Choice choice(String text, String base) {
        boolean image = text != null && text.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|gif|webp)$");
        return new LearnerExamService.Choice(image ? null : text, image ? media(base, text) : null);
    }

    private static String media(String base, String filename) {
        return base == null || filename == null || filename.isBlank() ? null : base
                + org.springframework.web.util.UriUtils.encodePathSegment(filename, java.nio.charset.StandardCharsets.UTF_8);
    }
}
