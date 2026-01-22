package com.ljh.smarteducation.controller;

import com.ljh.smarteducation.entity.SecurityEventLog;
import com.ljh.smarteducation.service.SecurityLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 安全日志查询接口
 * 提供 REST API 供管理员查询安全日志
 * 
 * @author Smart Education Team
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/security-logs")
@CrossOrigin(origins = "http://localhost:5173")
@PreAuthorize("hasRole('ADMIN')") // 仅管理员可访问
public class SecurityLogController {

    private final SecurityLogService securityLogService;
    
    // 时间格式化器
    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public SecurityLogController(SecurityLogService securityLogService) {
        this.securityLogService = securityLogService;
    }

    // ==================== 查询接口 ====================

    /**
     * GET /api/admin/security-logs - 分页查询所有安全日志
     * 
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 分页的安全日志数据
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllSecurityLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Page<SecurityEventLog> logPage = securityLogService.findAllPaged(PageRequest.of(page, size));
            
            Map<String, Object> response = new HashMap<>();
            response.put("logs", logPage.getContent());
            response.put("totalElements", logPage.getTotalElements());
            response.put("totalPages", logPage.getTotalPages());
            response.put("currentPage", logPage.getNumber());
            response.put("pageSize", logPage.getSize());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("查询安全日志失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "查询失败", "code", "QUERY_ERROR"));
        }
    }

    /**
     * GET /api/admin/security-logs/type/{eventType} - 按事件类型查询
     * 
     * @param eventType 事件类型
     * @return 安全日志列表
     */
    @GetMapping("/type/{eventType}")
    public ResponseEntity<?> getLogsByEventType(@PathVariable String eventType) {
        try {
            List<SecurityEventLog> logs = securityLogService.findByEventType(eventType);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("按事件类型查询失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "查询失败", "code", "QUERY_ERROR"));
        }
    }

    /**
     * GET /api/admin/security-logs/ip/{ipAddress} - 按IP地址查询
     * 
     * @param ipAddress IP地址
     * @return 安全日志列表
     */
    @GetMapping("/ip/{ipAddress}")
    public ResponseEntity<?> getLogsByIpAddress(@PathVariable String ipAddress) {
        try {
            List<SecurityEventLog> logs = securityLogService.findByIpAddress(ipAddress);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("按IP地址查询失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "查询失败", "code", "QUERY_ERROR"));
        }
    }

    /**
     * GET /api/admin/security-logs/time-range - 按时间范围查询
     * 
     * @param startTime 开始时间（格式：yyyy-MM-dd HH:mm:ss）
     * @param endTime 结束时间（格式：yyyy-MM-dd HH:mm:ss）
     * @return 安全日志列表
     */
    @GetMapping("/time-range")
    public ResponseEntity<?> getLogsByTimeRange(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            // 解析时间字符串
            LocalDateTime start = LocalDateTime.parse(startTime, DATE_TIME_FORMATTER);
            LocalDateTime end = LocalDateTime.parse(endTime, DATE_TIME_FORMATTER);
            
            List<SecurityEventLog> logs = securityLogService.findByTimeRange(start, end);
            return ResponseEntity.ok(logs);
        } catch (DateTimeParseException e) {
            log.error("时间格式错误: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", "时间格式错误，请使用格式：yyyy-MM-dd HH:mm:ss", "code", "INVALID_TIME_FORMAT"));
        } catch (Exception e) {
            log.error("按时间范围查询失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "查询失败", "code", "QUERY_ERROR"));
        }
    }

    /**
     * GET /api/admin/security-logs/sql-injection - 查询SQL注入攻击记录
     * 
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return SQL注入攻击记录和统计信息
     */
    @GetMapping("/sql-injection")
    public ResponseEntity<?> getSqlInjectionLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            // 查询最近的SQL注入攻击记录
            List<SecurityEventLog> logs = securityLogService.findRecentSqlInjectionAttempts(size);
            
            // 计算最近24小时的攻击次数
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime yesterday = now.minusDays(1);
            Long count24h = securityLogService.countSqlInjectionAttempts(yesterday, now);
            
            // 计算总攻击次数（所有时间）
            Long totalCount = (long) securityLogService.findByEventType("SQL_INJECTION_ATTEMPT").size();
            
            Map<String, Object> response = new HashMap<>();
            response.put("logs", logs);
            response.put("count24h", count24h);
            response.put("totalCount", totalCount);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("查询SQL注入攻击记录失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "查询失败", "code", "QUERY_ERROR"));
        }
    }

    // ==================== 统计接口 ====================

    /**
     * GET /api/admin/security-logs/statistics - 获取安全统计信息
     * 
     * @param startTime 开始时间（可选，格式：yyyy-MM-dd HH:mm:ss）
     * @param endTime 结束时间（可选，格式：yyyy-MM-dd HH:mm:ss）
     * @return 安全统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getSecurityStatistics(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        try {
            LocalDateTime start;
            LocalDateTime end;
            
            // 如果时间参数为空，默认查询最近7天
            if (startTime == null || endTime == null) {
                end = LocalDateTime.now();
                start = end.minusDays(7);
            } else {
                // 解析时间字符串
                start = LocalDateTime.parse(startTime, DATE_TIME_FORMATTER);
                end = LocalDateTime.parse(endTime, DATE_TIME_FORMATTER);
            }
            
            Map<String, Object> statistics = securityLogService.getSecurityStatistics(start, end);
            return ResponseEntity.ok(statistics);
        } catch (DateTimeParseException e) {
            log.error("时间格式错误: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", "时间格式错误，请使用格式：yyyy-MM-dd HH:mm:ss", "code", "INVALID_TIME_FORMAT"));
        } catch (Exception e) {
            log.error("获取安全统计信息失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "查询失败", "code", "QUERY_ERROR"));
        }
    }
}
