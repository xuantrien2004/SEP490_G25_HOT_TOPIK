package com.hottopik.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * "Sổ ngữ pháp" của học viên: mỗi dòng là một mẫu ngữ pháp người học đánh dấu ⭐ để ôn lại.
 * Lưu ở DB (không dùng localStorage như Sổ từ chưa thuộc) để giữ được khi đổi máy và để sau này
 * quiz ngữ pháp đọc được danh sách từ server. Mỗi người chỉ lưu một mẫu một lần (unique user_id + grammar_id).
 */
@Entity
@Table(name = "user_saved_grammars", schema = "dbo",
        uniqueConstraints = @UniqueConstraint(name = "UQ_user_saved_grammars", columnNames = {"user_id", "grammar_id"}),
        indexes = @Index(name = "ix_saved_grammars_user", columnList = "user_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class UserSavedGrammar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grammar_id", nullable = false, foreignKey = @ForeignKey(name = "FK_saved_grammars_grammar"))
    private Grammar grammar;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
