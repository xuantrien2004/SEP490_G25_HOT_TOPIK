
package com.hottopik.service;

import com.hottopik.dto.GrammarGroupForm;
import com.hottopik.entity.GrammarGroup;
import com.hottopik.repository.GrammarGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import com.hottopik.service.VocabularySyncService;

@Service
public class GrammarGroupService {

    private final GrammarGroupRepository grammarGroupRepository;

    @Autowired(required = false)
    private VocabularySyncService syncService;

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

    @Transactional(readOnly = true)
    public List<GrammarGroup> getActiveGroupsByLevel(Short level) {
        validateLevel(level);
        return grammarGroupRepository.findByLevelAndStatusOrderByGroupNoAsc(level, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public List<GrammarGroup> getActiveGroups() {
        return grammarGroupRepository.findAllByOrderByLevelAscGroupNoAsc().stream()
                .filter(g -> "ACTIVE".equalsIgnoreCase(g.getStatus()))
                .toList();
    }

    // Thêm hoặc cập nhật nhóm
    @Transactional
    public GrammarGroup saveGroup(GrammarGroupForm form) {
        validateForm(form);

        Short level = form.getLevel();
        String name = form.getNameVi().trim();
        List<GrammarGroup> sameLevel = grammarGroupRepository.findByLevelOrderByGroupNoAsc(level);

        // Chỉ chống trùng tên trong cùng cấp TOPIK; cấp khác được phép trùng tên.
        boolean duplicateName = sameLevel.stream().anyMatch(g ->
                !g.getId().equals(form.getId())
                        && g.getNameVi() != null
                        && g.getNameVi().trim().equalsIgnoreCase(name));
        if (duplicateName) {
            throw new IllegalArgumentException(
                    "Tên dạng/nhóm \"" + name + "\" đã tồn tại trong TOPIK " + level + ".");
        }

        GrammarGroup group;

        if (form.getId() == null) {
            group = new GrammarGroup();
            group.setLevel(level);
            group.setGroupNo(nextFreeGroupNo(sameLevel));
        } else {
            group = grammarGroupRepository.findById(form.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy nhóm ngữ pháp."
                    ));
            if (!level.equals(group.getLevel())) {
                throw new IllegalArgumentException("Không thể đổi cấp TOPIK của dạng/nhóm.");
            }
        }

        group.setNameVi(name);
        if (group.getStatus() == null || group.getStatus().isBlank()) {
            group.setStatus("ACTIVE");
        }
        GrammarGroup saved = grammarGroupRepository.save(group);
        grammarGroupRepository.flush();
        broadcast("GRAMMAR_GROUP_CHANGED", Map.of(
                "actionType", form.getId() == null ? "CREATE" : "UPDATE",
                "id", saved.getId(), "level", saved.getLevel(), "groupNo", saved.getGroupNo(),
                "name", saved.getNameVi(), "active", "ACTIVE".equalsIgnoreCase(saved.getStatus())));
        return saved;
    }

    @Transactional
    public GrammarGroup toggleStatus(Integer id, boolean active) {
        GrammarGroup group = getGroupById(id);
        group.setStatus(active ? "ACTIVE" : "HIDDEN");
        GrammarGroup saved = grammarGroupRepository.save(group);
        grammarGroupRepository.flush();
        broadcast("GRAMMAR_GROUP_CHANGED", Map.of(
                "actionType", "TOGGLE", "id", saved.getId(), "level", saved.getLevel(),
                "name", saved.getNameVi(), "active", active));
        return saved;
    }

    private void broadcast(String action, Map<String, Object> payload) {
        if (syncService != null) syncService.broadcast(action, payload);
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

        if (form.getNameVi() == null
                || form.getNameVi().isBlank()
                || form.getNameVi().trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Tên nhóm không được để trống và tối đa 100 ký tự."
            );
        }
    }

    // Số nhóm không còn nhập tay: lấy số nhỏ nhất còn trống. DB giới hạn 1..14 (CK_grammar_groups_no)
    // và duy nhất theo (level, group_no) (UQ_grammar_groups).
    private static short nextFreeGroupNo(List<GrammarGroup> sameLevel) {
        java.util.Set<Short> used = new java.util.HashSet<>();
        sameLevel.forEach(g -> used.add(g.getGroupNo()));
        for (short no = 1; no <= MAX_GROUP_NO; no++) {
            if (!used.contains(no)) return no;
        }
        throw new IllegalArgumentException(
                "Mỗi cấp TOPIK chỉ có tối đa " + MAX_GROUP_NO + " dạng/nhóm ngữ pháp.");
    }

    private static final short MAX_GROUP_NO = 14;

    private void validateLevel(Short level) {
        if (level == null || level < 1 || level > 6) {
            throw new IllegalArgumentException(
                    "Cấp TOPIK phải nằm trong khoảng 1 đến 6."
            );
        }
    }
    // Lấy chi tiết nhóm ngữ pháp theo ID
    @Transactional(readOnly = true)
    public GrammarGroup getGroupById(Integer id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("ID nhóm ngữ pháp không hợp lệ.");
        }

        return grammarGroupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm ngữ pháp."));
    }
}
