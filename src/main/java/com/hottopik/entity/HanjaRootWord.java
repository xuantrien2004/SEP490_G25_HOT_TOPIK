package com.hottopik.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hanja_root_words", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HanjaRootWord {

    @EmbeddedId
    private HanjaRootWordId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("rootId")
    @JoinColumn(name = "root_id")
    private HanjaRoot hanjaRoot;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("vocabularyId")
    @JoinColumn(name = "vocabulary_id")
    private Vocabulary vocabulary;

    @Column(name = "hanja_word", length = 20, nullable = false)
    private String hanjaWord;
}
