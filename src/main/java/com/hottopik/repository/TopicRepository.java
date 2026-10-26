package com.hottopik.repository;

import com.hottopik.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findByLevel(Integer level);

    List<Topic> findByLevelAndStatus(Integer level, String status);

    Optional<Topic> findByLevelAndName(Integer level, String name);

    boolean existsByLevelAndName(Integer level, String name);

    void deleteByLevelAndName(Integer level, String name);
}
