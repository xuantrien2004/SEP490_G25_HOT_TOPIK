package com.hottopik.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name="question_revision_recipients", schema="dbo", uniqueConstraints=@UniqueConstraint(columnNames={"revision_id","user_id"}))
@Getter @Setter
public class QuestionRevisionRecipient {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="revision_id",nullable=false) private Long revisionId;
    @Column(name="user_id",nullable=false) private Long userId;
    @Nationalized @Column(columnDefinition="nvarchar(max)") private String adjustmentsJson;
}
