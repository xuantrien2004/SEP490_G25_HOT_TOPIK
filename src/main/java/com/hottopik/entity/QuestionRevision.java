package com.hottopik.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;
import java.time.LocalDateTime;

@Entity
@Table(name="question_revisions", schema="dbo")
@Getter @Setter
public class QuestionRevision {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    private Long questionId;
    private Long reportId;
    @Column(length=10) private String kind;
    @Column(length=30) private String correctionType;
    @Column(length=30) private String scorePolicy;
    @Nationalized @Column(columnDefinition="nvarchar(max)") private String beforeJson;
    @Nationalized @Column(columnDefinition="nvarchar(max)") private String afterJson;
    @Nationalized @Column(length=2000) private String note;
    private LocalDateTime createdAt = LocalDateTime.now();
}
