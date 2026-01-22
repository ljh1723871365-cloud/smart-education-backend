package com.ljh.smarteducation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 上传任务实体 - 用于异步处理
 */
@Entity
@Table(name = "upload_task", indexes = {
    @Index(name = "idx_task_id", columnList = "taskId"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
public class UploadTask {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 任务唯一标识（UUID）
     */
    @Column(nullable = false, unique = true, length = 36)
    private String taskId;
    
    /**
     * 文件名
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
     * 任务状态：PENDING, PROCESSING, COMPLETED, FAILED
     */
    @Column(nullable = false, length = 20)
    private String status;
    
    /**
     * 当前进度（0-100）
     */
    @Column(nullable = false)
    private Integer progress;
    
    /**
     * 进度描述
     */
    private String progressMessage;
    
    /**
     * 关联的套题ID（完成后）
     */
    private Long questionSetId;
    
    /**
     * 题目数量（完成后）
     */
    private Integer questionCount;
    
    /**
     * 错误信息（失败时）
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    /**
     * 创建时间
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * 开始处理时间
     */
    private LocalDateTime startedAt;
    
    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
    
    /**
     * 总耗时（毫秒）
     */
    private Long totalTimeMs;

    // Constructors
    public UploadTask() {
        this.createdAt = LocalDateTime.now();
        this.status = "PENDING";
        this.progress = 0;
    }

    public UploadTask(String taskId, String fileName, Long fileSize, String subject) {
        this.taskId = taskId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.subject = subject;
        this.status = "PENDING";
        this.progress = 0;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String getProgressMessage() {
        return progressMessage;
    }

    public void setProgressMessage(String progressMessage) {
        this.progressMessage = progressMessage;
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Long getTotalTimeMs() {
        return totalTimeMs;
    }

    public void setTotalTimeMs(Long totalTimeMs) {
        this.totalTimeMs = totalTimeMs;
    }
}
