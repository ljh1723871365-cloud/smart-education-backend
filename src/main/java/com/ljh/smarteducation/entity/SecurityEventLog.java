package com.ljh.smarteducation.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 安全事件日志实体类
 * 用于记录系统中的各类安全事件，包括SQL注入攻击、登录失败、未授权访问等
 */
@Entity
@Data
@Table(name = "security_event_log")
public class SecurityEventLog {

    /**
     * 主键ID，自增
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 事件类型
     * 可选值：SQL_INJECTION_ATTEMPT（SQL注入尝试）、LOGIN_FAILURE（登录失败）、
     * UNAUTHORIZED_ACCESS（未授权访问）等
     */
    @Column(name = "event_type", length = 50, nullable = false)
    private String eventType;

    /**
     * 事件描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 用户ID（可为空，未登录用户的操作没有用户ID）
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * 用户名（可为空）
     */
    @Column(name = "username", length = 100)
    private String username;

    /**
     * IP地址
     */
    @Column(name = "ip_address", length = 50, nullable = false)
    private String ipAddress;

    /**
     * 请求URL
     */
    @Column(name = "request_url", length = 500, nullable = false)
    private String requestUrl;

    /**
     * 请求方法（GET、POST、PUT、DELETE等）
     */
    @Column(name = "request_method", length = 10, nullable = false)
    private String requestMethod;

    /**
     * 请求参数（JSON格式）
     */
    @Column(name = "request_params", columnDefinition = "TEXT")
    private String requestParams;

    /**
     * SQL语句片段（可为空，仅SQL注入事件有此字段）
     */
    @Column(name = "sql_fragment", columnDefinition = "TEXT")
    private String sqlFragment;

    /**
     * 处理结果
     * 可选值：BLOCKED（已拦截）、ALLOWED（已允许）、WARNING（警告）
     */
    @Column(name = "action_taken", length = 20, nullable = false)
    private String actionTaken;

    /**
     * 严重程度
     * 可选值：HIGH（高）、MEDIUM（中）、LOW（低）
     */
    @Column(name = "severity", length = 20, nullable = false)
    private String severity;

    /**
     * 事件时间（自动生成）
     */
    @CreationTimestamp
    @Column(name = "event_time", updatable = false, nullable = false)
    private LocalDateTime eventTime;

    /**
     * 用户代理（浏览器信息，可为空）
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * 备注（可为空）
     */
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;
}
