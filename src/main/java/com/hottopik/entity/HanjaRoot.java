package com.hottopik.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hanja_roots", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HanjaRoot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "hanja_char", length = 4, nullable = false, unique = true)
    private String hanjaChar;

    @Column(name = "sino_vi", length = 30, nullable = false)
    private String sinoVi;

    @Column(name = "ko_reading", length = 10, nullable = false)
    private String koReading;

    @Column(name = "meaning_vi", length = 200)
    private String meaningVi;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Short sortOrder = 0;

    @Column(name = "status", length = 10, nullable = false)
    @Builder.Default
    private String status = "DRAFT";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "hanjaRoot", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<HanjaRootWord> rootWords = new ArrayList<>();
}
