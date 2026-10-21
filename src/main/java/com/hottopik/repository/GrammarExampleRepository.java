
package com.hottopik.repository;

import com.hottopik.entity.GrammarExample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GrammarExampleRepository
        extends JpaRepository<GrammarExample, Long> {

    // Lấy toàn bộ câu ví dụ của một ngữ pháp
    List<GrammarExample> findByGrammar_IdOrderBySortOrderAscIdAsc(
            Long grammarId
    );

    // Đếm số câu ví dụ của một ngữ pháp
    long countByGrammar_Id(Long grammarId);

    // Xóa toàn bộ câu ví dụ của một ngữ pháp
    void deleteByGrammar_Id(Long grammarId);
}
