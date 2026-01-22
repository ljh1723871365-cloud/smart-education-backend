package com.ljh.smarteducation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * AI解析结果缓存实体 - 避免重复解析相同文本
 */
@Entity
@Table(name = "ai_parse_cache", indexes = {
    @Index(name = "idx_text_hash", columnList = "textHash"),
    @Index(name = "idx_subject", columnList = "subject"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
public class AiParseCache {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 输入文本的MD5哈希值
     */
    @Column(nullable = false, unique = true, length = 32)
    private String textHash;
    
    /**
     * 科目
     */
    @Column(nullable = false, length = 50)
    private String subject;
    
    /**
     * AI返回的JSON结果
     */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String aiJsonResult;
    
    /**
     * 输入文本长度
     */
    @Column(nullable = false)
    private Integer textLength;
    
    /**
     * 解析出的题目数量
     */
    @Column(nullable = false)
    private Integer questionCount;
    
    /**
     * AI模型名称
     */
    @Column(nullable = false, length = 100)
    private String modelName;
    
    /**
     * 处理耗时（毫秒）
     */
    private Long processingTimeMs;
    
    /**
     * Token使用量
     */
    private Integer tokensUsed;
    
    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * 过期时间
     */
    private LocalDateTime expiresAt;

    // Constructors
    public AiParseCache() {
        this.createdAt = LocalDateTime.now();
    }

    public AiParseCache(String textHash, String subject, String aiJsonResult, 
                       Integer textLength, Integer questionCount, String modelName,
                       Long processingTimeMs, Integer tokensUsed) {
        this.textHash = textHash;
        this.subject = subject;
        this.aiJsonResult = aiJsonResult;
        this.textLength = textLength;
        this.questionCount = questionCount;
        this.modelName = modelName;
        this.processingTimeMs = processingTimeMs;
        this.tokensUsed = tokensUsed;
        this.createdAt = LocalDateTime.now();
        // AI结果缓存90天
        this.expiresAt = LocalDateTime.now().plusDays(90);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTextHash() {
        return textHash;
    }

    public void setTextHash(String textHash) {
        this.textHash = textHash;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getAiJsonResult() {
        return aiJsonResult;
    }

    public void setAiJsonResult(String aiJsonResult) {
        this.aiJsonResult = aiJsonResult;
    }

    public Integer getTextLength() {
        return textLength;
    }

    public void setTextLength(Integer textLength) {
        this.textLength = textLength;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public Integer getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(Integer tokensUsed) {
        this.tokensUsed = tokensUsed;
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
