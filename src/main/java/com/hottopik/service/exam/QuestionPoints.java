package com.hottopik.service.exam;

import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;

/**
 * Điểm in trên đề chuẩn. TOPIK II nghe và đọc là 2 điểm mỗi câu.
 * TOPIK I đổi điểm trong cùng một dạng, nên chỉ ghi mức đề đang dùng.
 */
public final class QuestionPoints {

    private QuestionPoints() {
    }

    public static String line(QuestionTypeDef type) {
        if (type == null) return "";
        return switch (type.code()) {
            case "II-V1", "II-V2" -> "10 điểm · mỗi chỗ trống 5 điểm";
            case "II-V3" -> "30 điểm · bài 200–300 chữ";
            case "II-V4" -> "50 điểm · bài 600–700 chữ";
            case "I-N5" -> "Mỗi câu 4 điểm";
            case "I-N6", "I-N7" -> "Mỗi câu 3 điểm";
            default -> {
                if (type.track() == Track.TOPIK_II && type.skill() != Skill.WRITING) yield "Mỗi câu 2 điểm";
                if (type.skill() == Skill.LISTENING) yield "Mỗi câu 3 hoặc 4 điểm trên đề chuẩn";
                yield "Mỗi câu 2 hoặc 3 điểm trên đề chuẩn";
            }
        };
    }

    /** Hậu tố in cạnh hướng dẫn tiếng Hàn, ví dụ 각 2점. Rỗng khi điểm không cố định. */
    public static String mark(QuestionTypeDef type) {
        if (type == null) return "";
        return switch (type.code()) {
            case "II-V1", "II-V2" -> "10점";
            case "II-V3" -> "30점";
            case "II-V4" -> "50점";
            case "I-N5" -> "각 4점";
            case "I-N6", "I-N7" -> "각 3점";
            default -> type.track() == Track.TOPIK_II && type.skill() != Skill.WRITING ? "각 2점" : "";
        };
    }
}
