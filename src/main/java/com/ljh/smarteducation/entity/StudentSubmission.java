package com.ljh.smarteducation.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Data
@Table(name = "`student_submission`")
public class StudentSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String username;

    // --- ↓↓↓ (新增) 关联套题 ↓↓↓ ---
    /**
     * 这份答卷属于哪一套试卷
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_set_id")
    private QuestionSet questionSet;
    // --- ↑↑↑ (新增结束) ↑↑↑ ---

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json", nullable = false)
    private Map<String, String> answers; // Key: QuestionID, Value: Answer

    private Integer score; // 自动批改得分
    private Integer totalQuestions; // 总题数

    @Column(nullable = false)
    private Boolean graded = false; // 是否已人工批改
    private String feedback; // 教师评语
    private Integer manualScore; // 教师给出的总分

    @CreationTimestamp
    private LocalDateTime submissionTime;
}

