package com.hottopik.service.exam;

import com.hottopik.entity.exam.*;
import com.hottopik.repository.exam.QuestionTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuestionTemplateService {

    private final QuestionTemplateRepository templates;

    public QuestionTemplateService(QuestionTemplateRepository templates) {
        this.templates = templates;
    }

    public List<QuestionTemplate> list(Track track, Skill skill) {
        if (track != null && skill != null) return templates.findByTrackAndSkillOrderByCodeAsc(track, skill);
        return templates.findAllByOrderByCodeAsc().stream()
                .filter(t -> track == null || t.getTrack() == track)
                .filter(t -> skill == null || t.getSkill() == skill)
                .toList();
    }

    public QuestionTemplate get(String code) {
        return templates.findByCode(code).orElseThrow(() -> new ExamDomainException("Không có dạng câu " + code + "."));
    }

    /** Các ô form nhập câu phải hiện khi admin chọn khuôn này. */
    public List<String> fields(QuestionTemplate t) {
        List<String> lines = new ArrayList<>();
        lines.add("Đề dẫn của dạng lấy từ hướng dẫn chuẩn, hiện một lần khi sang dạng mới.");
        if (t.isPromptRequired()) lines.add("Câu hỏi hoặc đề bài: bắt buộc, riêng từng câu.");
        resource(lines, "File nghe", t.getAudioScope(), t.isAudioRequired());
        resource(lines, "Ảnh hoặc biểu đồ", t.getImageScope(), t.isImageRequired());
        resource(lines, "Đoạn văn", t.getPassageScope(), t.isPassageRequired());
        resource(lines, "Phụ đề", t.getScriptScope(), false);
        if (t.getScriptScope() == ResourceScope.GROUP) {
            lines.add("Phụ đề thuộc nhóm, giấu khi đang làm bài, chỉ mở trong phần giải thích.");
        }
        if (t.getOptionCount() != null) {
            lines.add(t.getOptionCount() + " lựa chọn. Đáp án là một phương án, kiểu " + t.getAnswerType().getLabel() + ".");
        } else {
            lines.add("Không có bốn lựa chọn A/B/C/D. Đáp án kiểu " + t.getAnswerType().getLabel() + ".");
        }
        if (t.allowsSeveralChildren()) {
            lines.add("Một bài có " + t.childLabel() + ". File nghe hoặc đoạn văn lưu một lần ở nhóm. Mỗi câu con vẫn tính là một câu.");
        } else {
            lines.add("Câu độc lập: nhóm chỉ có 1 câu, không cần schema nhóm riêng.");
        }
        if (t.getMinChars() != null || t.getMaxChars() != null) {
            lines.add("Độ dài bài viết " + t.getMinChars() + "–" + t.getMaxChars() + " chữ, theo hướng dẫn đề chuẩn.");
        }
        lines.add("Điểm trong một đề gắn ở vị trí câu. Dạng câu không ghi đè điểm đó.");
        lines.add("Chấm: " + t.getGrading().getLabel() + ".");
        return lines;
    }

    private static void resource(List<String> lines, String label, ResourceScope scope, boolean required) {
        if (scope == ResourceScope.NONE) return;
        lines.add(label + ": " + scope.getLabel() + (required ? ", bắt buộc." : ", thêm khi bài có."));
    }

    @Transactional
    public void update(String code, String name, String description, TemplateStatus status,
                       boolean allowMiniTest, boolean allowPractice, boolean allowRoadmap, Actor actor) {
        if (!actor.isContentManager()) throw new ExamDomainException("Bạn chỉ có quyền xem dạng câu.");
        QuestionTemplate t = get(code);
        String nextName = name == null ? "" : name.trim();
        if (nextName.length() < 3 || nextName.length() > 200) {
            throw new ExamDomainException("Tên dạng câu dài từ 3 đến 200 ký tự.");
        }
        String nextDescription = description == null ? "" : description.trim();
        if (nextDescription.length() < 5) throw new ExamDomainException("Mô tả dạng câu cần ít nhất 5 ký tự.");
        if (status == null) throw new ExamDomainException("Chọn trạng thái dạng câu.");
        t.setName(nextName);
        t.setDescription(nextDescription);
        t.setStatus(status);
        t.setAllowMiniTest(allowMiniTest);
        t.setAllowPractice(allowPractice);
        t.setAllowRoadmap(allowRoadmap);
        t.setCustomized(true);
        t.setVersion(t.getVersion() + 1);
        t.setUpdatedBy(actor.name());
    }
}
