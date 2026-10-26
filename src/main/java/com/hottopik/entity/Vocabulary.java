package com.hottopik.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "vocabularies", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vocabulary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "topic_id", nullable = false)
    @Builder.Default
    private Integer topicId = 1;

    @Column(name = "level", nullable = false)
    @Builder.Default
    private Short level = 1;

    @Column(name = "word", length = 100, nullable = false)
    @Nationalized
    private String word;

    @Column(name = "romanization", length = 100)
    @Nationalized
    private String romanization;

    @Column(name = "pos", length = 30)
    @Nationalized
    private String pos;

    @Column(name = "meaning_vi", length = 300, nullable = false)
    @Nationalized
    private String meaningVi;

    @Column(name = "definition_ko", length = 500)
    @Nationalized
    private String definitionKo;

    @Column(name = "hanja", length = 20)
    @Nationalized
    private String hanja;

    @Column(name = "sino_vi", length = 100)
    @Nationalized
    private String sinoVi;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Column(name = "topic", length = 100)
    @Nationalized
    private String topic;

    @Column(name = "example_kr", length = 500)
    @Nationalized
    private String exampleKr;

    @Column(name = "example_vi", length = 500)
    @Nationalized
    private String exampleVi;

    @Column(name = "status", length = 10, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
