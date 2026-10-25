package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một dạng và số câu trong đề kiểm tra trình độ 20 câu. */
@Entity
@Table(name = "placement_lines", schema = "dbo", uniqueConstraints = @UniqueConstraint(
        name = "uq_placement_line", columnNames = {"paper_code", "type_code"}))
@Getter
@Setter
@NoArgsConstructor
public class PlacementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_code", nullable = false, length = 20)
    private String paperCode;

    @Column(name = "type_code", nullable = false, length = 10)
    private String typeCode;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
