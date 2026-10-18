package com.hottopik.repository;

import com.hottopik.entity.Vocabulary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VocabularyRepository extends JpaRepository<Vocabulary, Long> {

    Optional<Vocabulary> findByWord(String word);

    Optional<Vocabulary> findFirstByWordAndHanja(String word, String hanja);

    boolean existsByWord(String word);
}
