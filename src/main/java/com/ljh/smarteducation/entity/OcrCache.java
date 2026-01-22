package com.ljh.smarteducation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * OCR结果缓存实体 - 避免重复识别相同图片
 */
@Entity
@Table(name = "ocr_cache", indexes = {
    @Index(name = "idx_image_hash", columnList = "imageHash"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
public class OcrCache {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 图片MD5哈希值
     */
    @Column(nullable = false, unique = true, length = 32)
    private String imageHash;
    
    /**
     * OCR识别结果文本
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String recognizedText;
    
    /**
     * 图片文件名
     */
    @Column(nullable = false)
    private String fileName;
    
    /**
     * 图片大小（字节）
     */
    @Column(nullable = false)
    private Long fileSize;
    
    /**
     * OCR引擎类型（tesseract, aliyun, baidu等）
     */
    @Column(nullable = false, length = 50)
    private String ocrEngine;
    
    /**
     * 识别耗时（毫秒）
     */
    private Long processingTimeMs;
    
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
    public OcrCache() {
        this.createdAt = LocalDateTime.now();
    }

    public OcrCache(String imageHash, String recognizedText, String fileName, 
                   Long fileSize, String ocrEngine, Long processingTimeMs) {
        this.imageHash = imageHash;
        this.recognizedText = recognizedText;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.ocrEngine = ocrEngine;
        this.processingTimeMs = processingTimeMs;
        this.createdAt = LocalDateTime.now();
        // OCR结果缓存60天
        this.expiresAt = LocalDateTime.now().plusDays(60);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getImageHash() {
        return imageHash;
    }

    public void setImageHash(String imageHash) {
        this.imageHash = imageHash;
    }

    public String getRecognizedText() {
        return recognizedText;
    }

    public void setRecognizedText(String recognizedText) {
        this.recognizedText = recognizedText;
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

    public String getOcrEngine() {
        return ocrEngine;
    }

    public void setOcrEngine(String ocrEngine) {
        this.ocrEngine = ocrEngine;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
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
