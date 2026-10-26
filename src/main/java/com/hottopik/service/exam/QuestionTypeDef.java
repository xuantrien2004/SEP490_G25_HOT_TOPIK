package com.hottopik.service.exam;

import com.hottopik.entity.exam.Skill;
import com.hottopik.entity.exam.Track;

import java.util.List;

/**
 * Một dạng câu trong đề thật (BRD 11.3).
 * blocks: mỗi block là một nhóm số câu dùng chung một đoạn nghe/đoạn văn.
 * Dạng 1 câu/bài có mọi block kích thước 1.
 */
public record QuestionTypeDef(
        String code,
        Track track,
        Skill skill,
        String name,
        int zoneMin,
        int zoneMax,
        String instructionKo,
        List<List<Integer>> blocks,
        boolean passageRequired,
        boolean imageRequired,
        int sortOrder
) {

    public int requiredCount() {
        return blocks.stream().mapToInt(List::size).sum();
    }

    public String learnerInstructionKo() {
        return instructionKo == null ? null
                : instructionKo.replaceAll("\\[\\s*\\d+\\s*[~–-]\\s*\\d+\\s*\\]\\s*", "").trim();
    }

    public boolean isGrouped() {
        return blocks.stream().anyMatch(b -> b.size() > 1);
    }

    public boolean isWriting() {
        return skill == Skill.WRITING;
    }

    public List<Integer> numbers() {
        return blocks.stream().flatMap(List::stream).sorted().toList();
    }

    public List<Integer> blockOf(int no) {
        return blocks.stream().filter(b -> b.contains(no)).findFirst().orElse(List.of());
    }

    public String rangeLabel() {
        List<Integer> nums = numbers();
        StringBuilder sb = new StringBuilder();
        int start = nums.get(0);
        int prev = start;
        for (int i = 1; i <= nums.size(); i++) {
            Integer cur = i < nums.size() ? nums.get(i) : null;
            if (cur != null && cur == prev + 1) {
                prev = cur;
                continue;
            }
            if (sb.length() > 0) sb.append(", ");
            sb.append(start == prev ? String.valueOf(start) : start + "–" + prev);
            if (cur != null) {
                start = cur;
                prev = cur;
            }
        }
        return sb.toString();
    }

    public String zoneLabel() {
        return zoneMin == zoneMax ? String.valueOf(zoneMin) : zoneMin + "–" + zoneMax;
    }

    public String groupLabel() {
        if (!isGrouped()) return "1 câu";
        if (skill == Skill.LISTENING) return "1 đoạn nghe, nhiều câu";
        if (skill == Skill.READING) return "1 đoạn văn, nhiều câu";
        return "nhiều câu";
    }
}
