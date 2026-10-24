package com.hottopik.repository;

import com.hottopik.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findByLevel(Integer level);

    List<Topic> findByLevelAndStatus(Integer level, String status);

    List<Topic> findByLevelAndName(Integer level, String name);

    Optional<Topic> findFirstByLevelAndName(Integer level, String name);

    boolean existsByLevelAndName(Integer level, String name);

    @Modifying
    @Transactional
    @Query("DELETE FROM Topic t WHERE t.level = :level AND LOWER(t.name) = LOWER(:name)")
    int deleteByLevelAndName(@Param("level") Integer level, @Param("name") String name);
}
