package com.hottopik.entity.exam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.util.ArrayList;
import java.util.List;

/**
 * Phần dẫn (đoạn nghe / đoạn văn / ảnh) của một hoặc nhiều câu.
 * Câu 1 câu/bài cũng có group riêng kích thước 1, nhờ vậy soạn đề luôn lấy trọn group.
 */
@Entity
@Table(name = "question_groups", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
public class QuestionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private ExamSource source;

    @Column(name = "group_key", length = 50)
    private String groupKey;

    @Column(name = "type_code", nullable = false, length = 10)
    private String typeCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Skill skill;

    @Nationalized
    @Column(name = "script_ko", length = 4000)
    private String scriptKo;

    @Nationalized
    @Column(name = "script_vi", length = 4000)
    private String scriptVi;

    @Nationalized
    @Column(name = "passage_ko", length = 4000)
    private String passageKo;

    @Nationalized
    @Column(name = "audio_file", length = 255)
    private String audioFile;

    @Nationalized
    @Column(name = "image_file", length = 255)
    private String imageFile;

    @Column(nullable = false)
    private int size;

    @OneToMany(mappedBy = "group")
    @OrderBy("originalNo ASC")
    private List<Question> questions = new ArrayList<>();
}
