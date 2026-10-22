package com.hottopik.repository.exam;

import com.hottopik.entity.exam.ProgramVersionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProgramVersionItemRepository extends JpaRepository<ProgramVersionItem, Long> {

    @Query("select i from ProgramVersionItem i join fetch i.template where i.program.id = :programId order by i.orderNo")
    List<ProgramVersionItem> findWithTemplate(Long programId);

    @Query("select i.program.id, count(i) from ProgramVersionItem i group by i.program.id")
    List<Object[]> countByProgram();
}
