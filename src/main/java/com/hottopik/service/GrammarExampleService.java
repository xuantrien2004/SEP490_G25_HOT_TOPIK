
package com.hottopik.service;

import com.hottopik.dto.GrammarExampleForm;
import com.hottopik.entity.Grammar;
import com.hottopik.entity.GrammarExample;
import com.hottopik.repository.GrammarExampleRepository;
import com.hottopik.repository.GrammarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GrammarExampleService {

    private final GrammarExampleRepository grammarExampleRepository;
    private final GrammarRepository grammarRepository;

    public GrammarExampleService(
            GrammarExampleRepository grammarExampleRepository,
            GrammarRepository grammarRepository) {
        this.grammarExampleRepository = grammarExampleRepository;
        this.grammarRepository = grammarRepository;
    }

    // Lấy danh sách ví dụ của một ngữ pháp
    @Transactional(readOnly = true)
    public List<GrammarExample> getExamplesByGrammarId(Long grammarId) {
        if (!grammarRepository.existsById(grammarId)) {
            throw new IllegalArgumentException(
                    "Không tìm thấy ngữ pháp."
            );
        }

        return grammarExampleRepository
                .findByGrammar_IdOrderBySortOrderAscIdAsc(grammarId);
    }

    // Thêm hoặc cập nhật một câu ví dụ
    @Transactional
    public GrammarExample saveExample(
            Long grammarId,
            GrammarExampleForm form) {

        validateForm(form);

        Grammar grammar = grammarRepository.findById(grammarId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy ngữ pháp."
                ));

        GrammarExample example;

        if (form.getId() == null) {
            example = new GrammarExample();
        } else {
            example = grammarExampleRepository.findById(form.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy câu ví dụ."
                    ));

            if (!example.getGrammar().getId().equals(grammarId)) {
                throw new IllegalArgumentException(
                        "Câu ví dụ không thuộc ngữ pháp đã chọn."
                );
            }
        }

        example.setGrammar(grammar);
        example.setSentenceKo(form.getSentenceKo().trim());
        example.setSentenceVi(form.getSentenceVi().trim());
        example.setSortOrder(
                form.getSortOrder() != null
                        ? form.getSortOrder()
                        : (short) 0
        );

        return grammarExampleRepository.save(example);
    }

    // Xóa một câu ví dụ
    @Transactional
    public void deleteExample(Long grammarId, Long exampleId) {
        GrammarExample example = grammarExampleRepository
                .findById(exampleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy câu ví dụ."
                ));

        if (!example.getGrammar().getId().equals(grammarId)) {
            throw new IllegalArgumentException(
                    "Câu ví dụ không thuộc ngữ pháp đã chọn."
            );
        }

        grammarExampleRepository.delete(example);
    }

    private void validateForm(GrammarExampleForm form) {
        if (form == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu câu ví dụ không được để trống."
            );
        }

        if (form.getSentenceKo() == null
                || form.getSentenceKo().isBlank()
                || form.getSentenceKo().trim().length() > 500) {
            throw new IllegalArgumentException(
                    "Câu tiếng Hàn không được để trống, tối đa 500 ký tự."
            );
        }

        if (form.getSentenceVi() == null
                || form.getSentenceVi().isBlank()
                || form.getSentenceVi().trim().length() > 500) {
            throw new IllegalArgumentException(
                    "Câu tiếng Việt không được để trống, tối đa 500 ký tự."
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
}
