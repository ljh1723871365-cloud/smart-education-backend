package com.ljh.smarteducation.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * API 使用日志实体
 * 记录每次 API 调用的 Token 使用情况和成本
 */
@Entity
@Data
@Table(name = "api_usage_log")
public class ApiUsageLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // ==================== 关联信息 ====================
    
    /**
     * 题目套题 ID
     */
    @Column(name = "question_set_id")
    private Long questionSetId;
    
    /**
     * 套题标题
     */
    @Column(name = "question_set_title", length = 500)
    private String questionSetTitle;
    
    /**
     * 上传用户 ID（后续扩展）
     */
    @Column(name = "user_id")
    private Long userId;
    
    // ==================== Token 统计 ====================
    
    /**
     * 输入 Token 数
     */
    @Column(name = "input_tokens")
    private Integer inputTokens;
    
    /**
     * 输出 Token 数
     */
    @Column(name = "output_tokens")
    private Integer outputTokens;
    
    /**
     * 总 Token 数
     */
    @Column(name = "total_tokens")
    private Integer totalTokens;
    
    // ==================== 题目信息 ====================
    
    /**
     * 解析出的题目数量
     */
    @Column(name = "question_count")
    private Integer questionCount;
    
    /**
     * Word 文件大小（KB）
     */
    @Column(name = "file_size_kb")
    private Integer fileSizeKb;
    
    // ==================== 成本信息 ====================
    
    /**
     * 使用的模型名称
     */
    @Column(name = "model_name", length = 50)
    private String modelName;
    
    /**
     * 输入成本（元）
     */
    @Column(name = "input_cost")
    private Double inputCost;
    
    /**
     * 输出成本（元）
     */
    @Column(name = "output_cost")
    private Double outputCost;
    
    /**
     * 总成本（元）
     */
    @Column(name = "total_cost")
    private Double totalCost;
    
    // ==================== 性能信息 ====================
    
    /**
     * API 调用耗时（毫秒）
     */
    @Column(name = "duration_ms")
    private Long durationMs;
    
    /**
     * 是否成功
     */
    @Column(name = "success")
    private Boolean success;
    
    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    // ==================== 时间信息 ====================
    
    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 计算平均每道题消耗的 Token
     */
    public Double getAvgTokensPerQuestion() {
        if (questionCount == null || questionCount == 0) {
            return 0.0;
        }
        return totalTokens / (double) questionCount;
    }
    
    /**
     * 计算平均每道题的成本
     */
    public Double getAvgCostPerQuestion() {
        if (questionCount == null || questionCount == 0) {
            return 0.0;
        }
        return totalCost / questionCount;
    }
}

