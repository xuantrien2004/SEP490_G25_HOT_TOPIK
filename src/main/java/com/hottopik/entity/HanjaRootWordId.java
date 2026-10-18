package com.hottopik.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HanjaRootWordId implements Serializable {

    @Column(name = "root_id")
    private Integer rootId;

    @Column(name = "vocabulary_id")
    private Long vocabularyId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HanjaRootWordId that = (HanjaRootWordId) o;
        return Objects.equals(rootId, that.rootId) && Objects.equals(vocabularyId, that.vocabularyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootId, vocabularyId);
    }
}
