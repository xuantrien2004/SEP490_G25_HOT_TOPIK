
package com.hottopik.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Table(
        name = "grammar_groups",
        schema = "dbo",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UQ_grammar_groups",
                        columnNames = {"level", "group_no"}
                )
        }
)
public class GrammarGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "level", nullable = false)
    private Short level;

    @Column(name = "group_no", nullable = false)
    private Short groupNo;

    @Column(name = "name_vi", nullable = false, length = 100)
    private String nameVi;

    @Column(
            name = "created_at",
            nullable = false

    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false

    )
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    // Tự động cập nhật thời gian trước khi UPDATE vào Database
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @JsonIgnore
    @OneToMany(mappedBy = "grammarGroup", fetch = FetchType.LAZY)
    private List<Grammar> grammars = new ArrayList<>();

    public GrammarGroup() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Short getLevel() {
        return level;
    }

    public void setLevel(Short level) {
        this.level = level;
    }

    public Short getGroupNo() {
        return groupNo;
    }

    public void setGroupNo(Short groupNo) {
        this.groupNo = groupNo;
    }

    public String getNameVi() {
        return nameVi;
    }

    public void setNameVi(String nameVi) {
        this.nameVi = nameVi;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<Grammar> getGrammars() {
        return grammars;
    }

    public void setGrammars(List<Grammar> grammars) {
        this.grammars = grammars;
    }
}
