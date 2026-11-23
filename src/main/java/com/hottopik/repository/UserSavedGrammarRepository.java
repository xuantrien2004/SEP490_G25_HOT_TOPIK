package com.hottopik.repository;

import com.hottopik.entity.UserSavedGrammar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSavedGrammarRepository extends JpaRepository<UserSavedGrammar, Long> {

    Optional<UserSavedGrammar> findByUserIdAndGrammar_Id(Long userId, Long grammarId);

    // Chỉ trả về mẫu đang hiển thị cho học viên (mẫu và nhóm đều ACTIVE); nạp sẵn nhóm + ví dụ vì open-in-view=false.
    @Query("SELECT DISTINCT s FROM UserSavedGrammar s JOIN FETCH s.grammar g JOIN FETCH g.grammarGroup grp "
            + "LEFT JOIN FETCH g.examples WHERE s.userId = :userId AND g.status = 'ACTIVE' AND grp.status = 'ACTIVE' "
            + "ORDER BY s.createdAt DESC")
    List<UserSavedGrammar> findVisibleByUserId(@Param("userId") Long userId);

    @Query("SELECT s.grammar.id FROM UserSavedGrammar s WHERE s.userId = :userId")
    List<Long> findGrammarIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM UserSavedGrammar s WHERE s.grammar.id = :grammarId")
    void deleteByGrammarId(@Param("grammarId") Long grammarId);
}
