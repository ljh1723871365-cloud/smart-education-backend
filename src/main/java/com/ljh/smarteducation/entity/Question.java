package com.ljh.smarteducation.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Data
@Table(name = "`questions`")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- (原有的字段) ---
    private String subject;
    private String difficulty;
    private String knowledgePoint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> content;

    // --- ↓↓↓ (新增字段) ↓↓↓ ---

    /**
     * 这道题属于哪一套试卷
     */
    @ManyToOne(fetch = FetchType.LAZY) // 使用懒加载
    @JoinColumn(name = "question_set_id") // 外键列
    private QuestionSet questionSet;

    /**
     * 这道题在试卷中的序号 (1, 2, 3...)
     */
    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    // --- ↑↑↑ (新增结束) ↑↑↑ ---

    @CreationTimestamp
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;
}
