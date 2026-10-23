package com.hottopik.service.exam;

import com.hottopik.entity.exam.*;

import java.util.*;
import java.util.stream.Collectors;

/** One complete block per official type, using the parent's actual question positions. */
final class MiniTestSelector {
    private MiniTestSelector() {}

    static List<ExamItem> select(Exam exam, Skill skill) {
        List<ExamItem> selected = new ArrayList<>();
        Map<Integer, ExamItem> byNo = exam.getItems().stream().filter(i -> i.getSection() == skill)
                .collect(Collectors.toMap(ExamItem::getNo, i -> i));
        for (QuestionTypeDef type : QuestionTypeCatalog.forTrackAndSkill(exam.getTrack(), skill)) {
            List<List<ExamItem>> candidates = type.blocks().stream()
                    .filter(block -> block.stream().allMatch(byNo::containsKey))
                    .map(block -> block.stream().map(byNo::get).toList())
                    .filter(MiniTestSelector::completeBlock).toList();
            if (candidates.isEmpty()) return List.of();
            selected.addAll(candidates.get(new Random().nextInt(candidates.size())));
        }
        return selected.stream().sorted(Comparator.comparingInt(ExamItem::getNo)).toList();
    }

    static boolean compact(Exam mini) {
        if (mini.getKind() == ExamKind.FULL) return false;
        if (mini.getKind() == ExamKind.WRITING) return mini.hasFullWritingSet();
        Skill skill = Skill.valueOf(mini.getKind().name());
        if (mini.getItems().stream().anyMatch(i -> i.getSection() != skill)) return false;
        for (QuestionTypeDef type : QuestionTypeCatalog.forTrackAndSkill(mini.getTrack(), skill)) {
            List<ExamItem> items = mini.getItems().stream().filter(i -> type.numbers().contains(i.getNo()))
                    .sorted(Comparator.comparingInt(ExamItem::getNo)).toList();
            if (type.blocks().stream().noneMatch(b -> b.equals(items.stream().map(ExamItem::getNo).toList()))
                    || !completeBlock(items)) return false;
        }
        return mini.getItems().stream().allMatch(i -> QuestionTypeCatalog.resolve(mini.getTrack(), skill, i.getNo()).isPresent());
    }

    static boolean fromParent(Exam mini, Exam parent) {
        if (mini.getKind() == ExamKind.WRITING) {
            return mini.writingPrompts().stream().map(WritingPrompt::getId).toList()
                    .equals(parent.writingPrompts().stream().map(WritingPrompt::getId).toList());
        }
        return mini.getItems().stream().allMatch(child -> parent.getItems().stream().anyMatch(i ->
                i.getSection() == child.getSection() && i.getNo() == child.getNo()
                        && i.getQuestion().getId().equals(child.getQuestion().getId())));
    }

    private static boolean completeBlock(List<ExamItem> items) {
        if (items.stream().anyMatch(i -> i.getQuestion().getStatus() != QuestionStatus.ACTIVE
                || i.getQuestion().getPurpose() == QuestionPurpose.ASSESSMENT)) return false;
        Map<Long, List<Question>> groups = items.stream().map(ExamItem::getQuestion)
                .collect(Collectors.groupingBy(q -> q.getGroup().getId()));
        if (groups.values().stream().anyMatch(qs -> qs.size() != qs.get(0).getGroup().getSize())) return false;
        // Separately entered children may form a block only within the same source paper.
        return items.size() == 1 || items.stream().map(i -> i.getQuestion().getSource())
                .filter(Objects::nonNull).map(ExamSource::getId).distinct().count() == 1;
    }
}
