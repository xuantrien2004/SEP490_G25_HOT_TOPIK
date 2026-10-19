
package com.hottopik.service;

import com.hottopik.dto.GrammarExampleForm;
import com.hottopik.dto.GrammarForm;
import com.hottopik.entity.Grammar;
import com.hottopik.entity.GrammarExample;
import com.hottopik.entity.GrammarGroup;
import com.hottopik.repository.GrammarGroupRepository;
import com.hottopik.repository.GrammarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class GrammarService {

    private static final List<String> ALLOWED_STATUSES =
            List.of("DRAFT", "ACTIVE", "HIDDEN");

    private final GrammarRepository grammarRepository;
    private final GrammarGroupRepository grammarGroupRepository;

    public GrammarService(
            GrammarRepository grammarRepository,
            GrammarGroupRepository grammarGroupRepository) {
        this.grammarRepository = grammarRepository;
        this.grammarGroupRepository = grammarGroupRepository;
    }

    // Lấy toàn bộ ngữ pháp
    @Transactional(readOnly = true)
    public List<Grammar> getAllGrammars() {
        return grammarRepository.findAll();
    }

    // Lấy chi tiết một ngữ pháp
    @Transactional(readOnly = true)
    public Grammar getGrammarById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "ID ngữ pháp không được để trống."
            );
        }

        return grammarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy ngữ pháp."
                ));
    }

    // Tìm kiếm theo mẫu ngữ pháp hoặc bản dịch
    @Transactional(readOnly = true)
    public List<Grammar> searchGrammars(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getAllGrammars();
        }

        return grammarRepository.searchByKeyword(keyword.trim());
    }

    // Lọc theo cấp TOPIK, nhóm, trạng thái và từ khóa
    @Transactional(readOnly = true)
    public List<Grammar> filterGrammars(
            Short level,
            Integer groupId,
            String status,
            String keyword) {

        // Cấp TOPIK không bắt buộc khi lọc.
        validateOptionalLevelIfPresent(level);

        if (groupId != null && groupId <= 0) {
            throw new IllegalArgumentException(
                    "Nhóm ngữ pháp không hợp lệ."
            );
        }

        if (status != null && !status.isBlank()) {
            status = normalizeStatus(status);
            validateStatus(status);
        } else {
            status = null;
        }

        if (keyword != null) {
            keyword = keyword.trim();

            if (keyword.isBlank()) {
                keyword = null;
            }
        }

        return grammarRepository.filterGrammars(
                level,
                groupId,
                status,
                keyword
        );
    }

    // Lấy ngữ pháp theo nhóm
    @Transactional(readOnly = true)
    public List<Grammar> getGrammarsByGroup(Integer groupId) {
        if (groupId == null || groupId <= 0) {
            throw new IllegalArgumentException(
                    "ID nhóm ngữ pháp không hợp lệ."
            );
        }

        if (!grammarGroupRepository.existsById(groupId)) {
            throw new IllegalArgumentException(
                    "Không tìm thấy nhóm ngữ pháp."
            );
        }

        return grammarRepository
                .findByGrammarGroup_IdOrderBySortOrderAscIdAsc(groupId);
    }

    // Thêm hoặc cập nhật ngữ pháp cùng các câu ví dụ
    @Transactional
    public Grammar saveGrammar(GrammarForm form) {
        validateForm(form);

        GrammarGroup group = grammarGroupRepository
                .findById(form.getGroupId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nhóm ngữ pháp không tồn tại."
                ));

        if (!group.getLevel().equals(form.getLevel())) {
            throw new IllegalArgumentException(
                    "Cấp TOPIK của ngữ pháp phải trùng với "
                            + "cấp TOPIK của nhóm."
            );
        }

        Grammar grammar;

        if (form.getId() == null) {
            grammar = new Grammar();
        } else {
            grammar = grammarRepository.findById(form.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy ngữ pháp cần cập nhật."
                    ));
        }

        grammar.setGrammarGroup(group);
        grammar.setLevel(form.getLevel());
        grammar.setPattern(form.getPattern().trim());
        grammar.setTranslation(form.getTranslation().trim());
        grammar.setMeaning(trimToNull(form.getMeaning()));
        grammar.setExplanation(trimToNull(form.getExplanation()));
        grammar.setNotes(trimToNull(form.getNotes()));

        grammar.setIsFrequent(
                Boolean.TRUE.equals(form.getIsFrequent())
        );

        grammar.setSortOrder(
                form.getSortOrder() != null
                        ? form.getSortOrder()
                        : (short) 0
        );

        grammar.setStatus(normalizeStatus(form.getStatus()));

        // Chuẩn bị danh sách câu ví dụ sau khi cập nhật.
        List<GrammarExample> updatedExamples = new ArrayList<>();

        if (form.getExamples() != null) {
            for (GrammarExampleForm exampleForm : form.getExamples()) {
                validateExample(exampleForm);

                GrammarExample example;

                if (exampleForm.getId() == null) {
                    // Câu ví dụ mới
                    example = new GrammarExample();
                } else {
                    // Chỉ được sửa câu ví dụ thuộc ngữ pháp hiện tại.
                    example = grammar.getExamples().stream()
                            .filter(item -> item.getId() != null
                                    && item.getId().equals(
                                    exampleForm.getId()
                            ))
                            .findFirst()
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Câu ví dụ không thuộc "
                                                    + "ngữ pháp này."
                                    )
                            );
                }

                example.setGrammar(grammar);
                example.setSentenceKo(
                        exampleForm.getSentenceKo().trim()
                );
                example.setSentenceVi(
                        exampleForm.getSentenceVi().trim()
                );
                example.setSortOrder(
                        exampleForm.getSortOrder() != null
                                ? exampleForm.getSortOrder()
                                : (short) 0
                );

                updatedExamples.add(example);
            }
        }

        // Đồng bộ hai chiều với quan hệ trong entity Grammar.
        // Các câu cũ không còn trong danh sách sẽ bị loại bỏ.
        grammar.setExamples(updatedExamples);

        return grammarRepository.save(grammar);
    }

    // Đánh dấu hoặc bỏ đánh dấu ngữ pháp thường gặp
    @Transactional
    public Grammar updateFrequent(Long id, boolean frequent) {
        Grammar grammar = getGrammarById(id);
        grammar.setIsFrequent(frequent);

        return grammarRepository.save(grammar);
    }

    // Cập nhật trạng thái ngữ pháp
    @Transactional
    public Grammar updateStatus(Long id, String status) {
        String normalizedStatus = normalizeStatus(status);
        validateStatus(normalizedStatus);

        Grammar grammar = getGrammarById(id);
        grammar.setStatus(normalizedStatus);

        return grammarRepository.save(grammar);
    }

    // Xóa ngữ pháp
    @Transactional
    public void deleteGrammar(Long id) {
        Grammar grammar = getGrammarById(id);

        // Nếu question_options đang tham chiếu đến ngữ pháp này,
        // khóa ngoại trong SQL Server có thể ngăn việc xóa.
        grammarRepository.delete(grammar);
    }

    // Kiểm tra dữ liệu ngữ pháp
    private void validateForm(GrammarForm form) {
        if (form == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu ngữ pháp không được để trống."
            );
        }

        // Khi thêm hoặc sửa, cấp TOPIK là bắt buộc.
        validateRequiredLevel(form.getLevel());

        if (form.getGroupId() == null || form.getGroupId() <= 0) {
            throw new IllegalArgumentException(
                    "Vui lòng chọn nhóm ngữ pháp hợp lệ."
            );
        }

        if (form.getPattern() == null
                || form.getPattern().isBlank()
                || form.getPattern().trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Mẫu ngữ pháp không được để trống, "
                            + "tối đa 100 ký tự."
            );
        }

        if (form.getTranslation() == null
                || form.getTranslation().isBlank()
                || form.getTranslation().trim().length() > 200) {
            throw new IllegalArgumentException(
                    "Bản dịch không được để trống, "
                            + "tối đa 200 ký tự."
            );
        }

        if (form.getMeaning() != null
                && form.getMeaning().length() > 1000) {
            throw new IllegalArgumentException(
                    "Ý nghĩa không được vượt quá 1000 ký tự."
            );
        }

        if (form.getSortOrder() != null
                && form.getSortOrder() < 0) {
            throw new IllegalArgumentException(
                    "Thứ tự sắp xếp không được âm."
            );
        }

        if (form.getStatus() == null || form.getStatus().isBlank()) {
            form.setStatus("DRAFT");
        }

        form.setStatus(normalizeStatus(form.getStatus()));
        validateStatus(form.getStatus());

        if (form.getExamples() != null) {
            for (GrammarExampleForm example : form.getExamples()) {
                validateExample(example);
            }
        }
    }

    // Kiểm tra dữ liệu câu ví dụ
    private void validateExample(GrammarExampleForm form) {
        if (form == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu câu ví dụ không hợp lệ."
            );
        }

        if (form.getSentenceKo() == null
                || form.getSentenceKo().isBlank()
                || form.getSentenceKo().trim().length() > 500) {
            throw new IllegalArgumentException(
                    "Câu tiếng Hàn không được để trống, "
                            + "tối đa 500 ký tự."
            );
        }

        if (form.getSentenceVi() == null
                || form.getSentenceVi().isBlank()
                || form.getSentenceVi().trim().length() > 500) {
            throw new IllegalArgumentException(
                    "Câu tiếng Việt không được để trống, "
                            + "tối đa 500 ký tự."
            );
        }

        if (form.getSortOrder() != null
                && (form.getSortOrder() < 0
                || form.getSortOrder() > 255)) {
            throw new IllegalArgumentException(
                    "Thứ tự câu ví dụ phải nằm trong khoảng 0 đến 255."
            );
        }
    }

    // Kiểm tra cấp TOPIK bắt buộc
    private void validateRequiredLevel(Short level) {
        if (level == null || level < 1 || level > 6) {
            throw new IllegalArgumentException(
                    "Cấp TOPIK phải nằm trong khoảng 1 đến 6."
            );
        }
    }

    // Kiểm tra cấp TOPIK tùy chọn khi lọc
    private void validateOptionalLevelIfPresent(Short level) {
        if (level != null && (level < 1 || level > 6)) {
            throw new IllegalArgumentException(
                    "Cấp TOPIK phải nằm trong khoảng 1 đến 6."
            );
        }
    }

    // Chuẩn hóa trạng thái
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                    "Trạng thái ngữ pháp không được để trống."
            );
        }

        return status.trim().toUpperCase(Locale.ROOT);
    }

    // Chỉ cho phép ba trạng thái theo database
    private void validateStatus(String status) {
        if (status == null
                || !ALLOWED_STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "Trạng thái chỉ được là DRAFT, ACTIVE hoặc HIDDEN."
            );
        }
    }

    // Chuyển chuỗi rỗng thành null
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
    public void updateGrammar(GrammarForm form) {
        if (form.getId() == null) {
            throw new IllegalArgumentException("ID ngữ pháp không được để trống khi cập nhật.");
        }

        Grammar grammar = grammarRepository.findById(form.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy mẫu ngữ pháp với ID: " + form.getId()));

        grammar.setPattern(form.getPattern());
        grammar.setTranslation(form.getTranslation());
        grammar.setMeaning(form.getMeaning());
        grammar.setExplanation(form.getExplanation());
        grammar.setStatus(form.getStatus());
        grammar.setIsFrequent(form.getIsFrequent());

        grammarRepository.save(grammar);
    }
}
