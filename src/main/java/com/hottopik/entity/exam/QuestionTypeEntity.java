package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/** Bản ghi DB của 42 dạng câu, đồng bộ từ QuestionTypeCatalog lúc khởi động. */
@Entity
@Table(name = "question_types", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class QuestionTypeEntity {

    @Id
    @Column(length = 10)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Track track;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Skill skill;

    @Nationalized
    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 60)
    private String ranges;

    @Column(name = "zone_min", nullable = false)
    private int zoneMin;

    @Column(name = "zone_max", nullable = false)
    private int zoneMax;

    @Column(name = "required_count", nullable = false)
    private int requiredCount;

    @Nationalized
    @Column(name = "instruction_ko", length = 500)
    private String instructionKo;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Khuôn hiển thị. Nhiều dạng chuẩn có thể dùng chung một khuôn. */
    @Column(name = "template_code", length = 20)
    private String templateCode;
}
