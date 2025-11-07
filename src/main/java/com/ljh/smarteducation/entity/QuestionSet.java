package com.ljh.smarteducation.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "`question_set`")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class QuestionSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title; // 套题标题 (例如: "2024.12四级真题第1套.docx")

    private String subject; // 学科 (例如: "English")
    
    // --- ↓↓↓ (新增) 关联到音频文件 ↓↓↓ ---
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_file_id")
    private ResourceFile resourceFile;
    // --- ↑↑↑ 新增结束 ↑↑↑ ---

    @CreationTimestamp
    private LocalDateTime uploadTime;
    
    // (未来可以添加: private Integer totalQuestions;)
}
