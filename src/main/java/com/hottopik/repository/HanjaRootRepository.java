package com.hottopik.repository;

import com.hottopik.entity.HanjaRoot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HanjaRootRepository extends JpaRepository<HanjaRoot, Integer> {

    Optional<HanjaRoot> findByHanjaChar(String hanjaChar);

    boolean existsByHanjaChar(String hanjaChar);

    List<HanjaRoot> findByStatusOrderBySortOrderAscIdAsc(String status);

    @Query("SELECT r FROM HanjaRoot r WHERE " +
           "(:status IS NULL OR :status = '' OR r.status = :status) AND " +
           "(:query IS NULL OR :query = '' OR " +
           " LOWER(r.hanjaChar) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(r.sinoVi) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(r.koReading) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(r.meaningVi) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY r.sortOrder ASC, r.id ASC")
    List<HanjaRoot> searchRoots(@Param("query") String query, @Param("status") String status);

    long countByStatus(String status);
}
