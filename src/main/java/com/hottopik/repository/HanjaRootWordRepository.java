package com.hottopik.repository;

import com.hottopik.entity.HanjaRootWord;
import com.hottopik.entity.HanjaRootWordId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HanjaRootWordRepository extends JpaRepository<HanjaRootWord, HanjaRootWordId> {

    List<HanjaRootWord> findByHanjaRootId(Integer rootId);

    @Modifying
    @Query("DELETE FROM HanjaRootWord hrw WHERE hrw.hanjaRoot.id = :rootId")
    void deleteByRootId(@Param("rootId") Integer rootId);

    @Modifying
    @Query("DELETE FROM HanjaRootWord hrw WHERE hrw.hanjaRoot.id = :rootId AND hrw.vocabulary.id = :vocabId")
    void deleteByRootIdAndVocabId(@Param("rootId") Integer rootId, @Param("vocabId") Long vocabId);
}
