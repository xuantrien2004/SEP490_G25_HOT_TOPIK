package com.hottopik.repository;

import com.hottopik.entity.Vocabulary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface VocabularyRepository extends JpaRepository<Vocabulary, Long> {

    Optional<Vocabulary> findByWord(String word);

    Optional<Vocabulary> findFirstByWordAndHanja(String word, String hanja);

    Optional<Vocabulary> findFirstByLevelAndTopicAndWord(Short level, String topic, String word);

    boolean existsByWord(String word);

    List<Vocabulary> findByLevel(Short level);

    List<Vocabulary> findByTopic(String topic);

    List<Vocabulary> findByLevelAndTopic(Short level, String topic);

    List<Vocabulary> findByLevelAndTopicAndStatus(Short level, String topic, String status);

    List<Vocabulary> findByLevelAndStatus(Short level, String status);

    List<Vocabulary> findByTopicAndStatus(String topic, String status);

    List<Vocabulary> findByStatus(String status);

    long countByStatus(String status);

    long countByLevel(Short level);

    long countByLevelAndTopic(Short level, String topic);

    long countByLevelAndTopicAndStatus(Short level, String topic, String status);

    @Modifying
    @Transactional
    @Query("UPDATE Vocabulary v SET v.status = :status WHERE v.level = :level AND LOWER(v.topic) = LOWER(:topic)")
    int updateStatusByLevelAndTopic(@Param("level") Short level, @Param("topic") String topic, @Param("status") String status);

    @Modifying
    @Transactional
    @Query("UPDATE Vocabulary v SET v.status = :status WHERE v.level = :level")
    int updateStatusByLevel(@Param("level") Short level, @Param("status") String status);

    @Modifying
    @Transactional
    @Query("UPDATE Vocabulary v SET v.topic = :newTopic WHERE v.level = :level AND LOWER(v.topic) = LOWER(:oldTopic)")
    int updateTopicName(@Param("level") Short level, @Param("oldTopic") String oldTopic, @Param("newTopic") String newTopic);

    @Modifying
    @Transactional
    @Query("DELETE FROM Vocabulary v WHERE v.level = :level AND LOWER(v.topic) = LOWER(:topic)")
    int deleteByLevelAndTopic(@Param("level") Short level, @Param("topic") String topic);
}
