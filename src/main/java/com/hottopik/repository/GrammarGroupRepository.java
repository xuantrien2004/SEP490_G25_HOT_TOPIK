
package com.hottopik.repository;

import com.hottopik.entity.GrammarGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrammarGroupRepository
        extends JpaRepository<GrammarGroup, Integer> {

    // Lấy danh sách nhóm theo cấp TOPIK
    List<GrammarGroup> findByLevelOrderByGroupNoAsc(Short level);

    // Lấy toàn bộ nhóm, sắp xếp theo cấp TOPIK và số thứ tự
    List<GrammarGroup> findAllByOrderByLevelAscGroupNoAsc();

    // Tìm nhóm theo cấp TOPIK và số thứ tự nhóm
    Optional<GrammarGroup> findByLevelAndGroupNo(
            Short level,
            Short groupNo
    );
}
