
package com.hottopik.repository;

import com.hottopik.entity.Grammar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GrammarRepository extends JpaRepository<Grammar, Long> {

    // Lấy ngữ pháp thuộc một nhóm
    List<Grammar> findByGrammarGroup_IdOrderBySortOrderAscIdAsc(
            Integer groupId
    );

    // Lấy ngữ pháp theo cấp TOPIK
    List<Grammar> findByLevelOrderBySortOrderAscIdAsc(Short level);

    // Lấy ngữ pháp theo trạng thái
    List<Grammar> findByStatusOrderByLevelAscSortOrderAscIdAsc(
            String status
    );

    // Lấy ngữ pháp thường gặp
    List<Grammar> findByIsFrequentTrueOrderByLevelAscSortOrderAscIdAsc();

    // Tìm kiếm theo mẫu ngữ pháp hoặc bản dịch
    @Query("""
        SELECT g
        FROM Grammar g
        WHERE LOWER(g.pattern) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(g.translation) LIKE LOWER(CONCAT('%', :keyword, '%'))
        ORDER BY g.level ASC, g.sortOrder ASC, g.id ASC
        """)
    List<Grammar> searchByKeyword(
            @Param("keyword") String keyword
    );

    // Lọc kết hợp theo cấp TOPIK, nhóm, trạng thái và từ khóa
    @Query("""
        SELECT g
        FROM Grammar g
        WHERE (:level IS NULL OR g.level = :level)
          AND (:groupId IS NULL OR g.grammarGroup.id = :groupId)
          AND (:status IS NULL OR g.status = :status)
          AND (
              :keyword IS NULL
              OR LOWER(g.pattern) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR LOWER(g.translation) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY g.level ASC, g.sortOrder ASC, g.id ASC
        """)
    List<Grammar> filterGrammars(
            @Param("level") Short level,
            @Param("groupId") Integer groupId,
            @Param("status") String status,
            @Param("keyword") String keyword
    );

    // Lấy ngữ pháp theo nhóm và trạng thái
    List<Grammar> findByGrammarGroup_IdAndStatusOrderBySortOrderAscIdAsc(
            Integer groupId,
            String status
    );
}
