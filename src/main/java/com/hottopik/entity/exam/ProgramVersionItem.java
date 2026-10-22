package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một dạng trong thứ tự lộ trình. orderNo tăng trong cả bản, mỗi track giữ thứ tự riêng khi đổi chỗ. */
@Entity
@Table(name = "program_version_items", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "uq_program_item_template", columnNames = {"program_id", "template_id"}))
@Getter
@Setter
@NoArgsConstructor
public class ProgramVersionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private ProgramVersion program;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private QuestionTemplate template;

    @Column(name = "order_no", nullable = false)
    private int orderNo;
}
