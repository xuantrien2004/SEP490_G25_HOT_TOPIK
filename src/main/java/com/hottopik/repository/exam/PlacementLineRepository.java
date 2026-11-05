package com.hottopik.repository.exam;

import com.hottopik.entity.exam.PlacementLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlacementLineRepository extends JpaRepository<PlacementLine, Long> {
    List<PlacementLine> findByPaperCodeOrderBySortOrderAsc(String paperCode);

    void deleteByPaperCode(String paperCode);
}
