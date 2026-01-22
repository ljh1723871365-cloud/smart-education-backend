package com.ljh.smarteducation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 文档缓存实体 - 用于避免重复处理相同文档
 */
@Entity
@Table(name = "document_cache", indexes = {
    @Index(name = "idx_file_hash", columnList = "fileHash"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
public class DocumentCache {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 文件MD5哈希值（用于识别重复文件）
     */
    @Column(nullable = false, unique = true, length = 32)
    private String fileHash;
    
    /**
     * 原始文件名
     */
    @Column(nullable = false)
    private String fileName;
    
    /**
     * 文件大小（字节）
     */
    @Column(nullable = false)
    private Long fileSize;
    
    /**
     * 科目
     */
    @Column(nullable = false, length = 50)
    private String subject;
    
    /**
     * 关联的套题ID
     */
    @Column(nullable = false)
    private Long questionSetId;
    
    /**
     * 题目数量
     */
    @Column(nullable = false)
    private Integer questionCount;
    
    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * 过期时间（可选，用于自动清理旧缓存）
     */
    private LocalDateTime expiresAt;

    // Constructors
    public DocumentCache() {
        this.createdAt = LocalDateTime.now();
    }

    public DocumentCache(String fileHash, String fileName, Long fileSize, String subject, 
                        Long questionSetId, Integer questionCount) {
        this.fileHash = fileHash;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.subject = subject;
        this.questionSetId = questionSetId;
        this.questionCount = questionCount;
        this.createdAt = LocalDateTime.now();
        // 默认缓存30天
        this.expiresAt = LocalDateTime.now().plusDays(30);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Long getQuestionSetId() {
        return questionSetId;
    }

    public void setQuestionSetId(Long questionSetId) {
        this.questionSetId = questionSetId;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
