
package com.hottopik.service;

import com.hottopik.dto.GrammarGroupForm;
import com.hottopik.entity.GrammarGroup;
import com.hottopik.repository.GrammarGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GrammarGroupService {

    private final GrammarGroupRepository grammarGroupRepository;

    public GrammarGroupService(
            GrammarGroupRepository grammarGroupRepository) {
        this.grammarGroupRepository = grammarGroupRepository;
    }

    // Lấy tất cả nhóm, sắp xếp theo cấp TOPIK và số nhóm
    @Transactional(readOnly = true)
    public List<GrammarGroup> getAllGroups() {
        return grammarGroupRepository
                .findAllByOrderByLevelAscGroupNoAsc();
    }

    // Lấy danh sách nhóm theo cấp TOPIK
    @Transactional(readOnly = true)
    public List<GrammarGroup> getGroupsByLevel(Short level) {
        validateLevel(level);

        return grammarGroupRepository
                .findByLevelOrderByGroupNoAsc(level);
    }

    // Thêm hoặc cập nhật nhóm
    @Transactional
    public GrammarGroup saveGroup(GrammarGroupForm form) {
        validateForm(form);

        Short level = form.getLevel();
        Short groupNo = form.getGroupNo();

        grammarGroupRepository.findByLevelAndGroupNo(level, groupNo)
                .ifPresent(existing -> {
                    if (form.getId() == null
                            || !existing.getId().equals(form.getId())) {
                        throw new IllegalArgumentException(
                                "Cấp TOPIK và số nhóm đã tồn tại."
                        );
                    }
                });

        GrammarGroup group;

        if (form.getId() == null) {
            group = new GrammarGroup();
        } else {
            group = grammarGroupRepository.findById(form.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy nhóm ngữ pháp."
                    ));
        }

        group.setLevel(level);
        group.setGroupNo(groupNo);
        group.setNameVi(form.getNameVi().trim());

        return grammarGroupRepository.save(group);
    }

    // Xóa nhóm theo ID
    @Transactional
    public void deleteGroup(Integer id) {
        GrammarGroup group = grammarGroupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy nhóm ngữ pháp."
                ));

        // Nếu nhóm đang có ngữ pháp, khóa ngoại sẽ ngăn việc xóa.
        if (group.getGrammars() != null
                && !group.getGrammars().isEmpty()) {
            throw new IllegalStateException(
                    "Không thể xóa nhóm đang chứa ngữ pháp."
            );
        }

        grammarGroupRepository.delete(group);
    }

    private void validateForm(GrammarGroupForm form) {
        if (form == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu nhóm ngữ pháp không được để trống."
            );
        }

        validateLevel(form.getLevel());

        if (form.getGroupNo() == null
                || form.getGroupNo() < 1
                || form.getGroupNo() > 14) {
            throw new IllegalArgumentException(
                    "Số nhóm phải nằm trong khoảng 1 đến 14."
            );
        }

        if (form.getNameVi() == null
                || form.getNameVi().isBlank()
                || form.getNameVi().trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Tên nhóm không được để trống và tối đa 100 ký tự."
            );
        }
    }

    private void validateLevel(Short level) {
        if (level == null || level < 1 || level > 6) {
            throw new IllegalArgumentException(
                    "Cấp TOPIK phải nằm trong khoảng 1 đến 6."
            );
        }
    }
}
