package com.ljh.smarteducation.service;

import com.ljh.smarteducation.entity.SecurityEventLog;
import com.ljh.smarteducation.repository.SecurityEventLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 安全日志存储服务
 * 提供安全事件日志的记录、查询和统计功能
 * 
 * @author Smart Education Team
 */
@Slf4j
@Service
public class SecurityLogService {

    private final SecurityEventLogRepository securityEventLogRepository;

    public SecurityLogService(SecurityEventLogRepository securityEventLogRepository) {
        this.securityEventLogRepository = securityEventLogRepository;
    }

    // ==================== 日志记录方法 ====================

    /**
     * 记录 SQL 注入攻击尝试
     * 
     * @param ipAddress 客户端 IP 地址
     * @param requestUrl 请求 URL
     * @param requestMethod 请求方法
     * @param sqlFragment SQL 片段
     * @param requestParams 请求参数
     * @param username 用户名（可为空）
     * @return 保存的安全事件日志
     */
    public SecurityEventLog logSqlInjectionAttempt(String ipAddress, String requestUrl, String requestMethod,
                                                    String sqlFragment, String requestParams, String username) {
        try {
            SecurityEventLog eventLog = new SecurityEventLog();
            eventLog.setEventType("SQL_INJECTION_ATTEMPT");
            eventLog.setSeverity("HIGH");
            eventLog.setActionTaken("BLOCKED");
            eventLog.setDescription("检测到 SQL 注入攻击尝试");
            eventLog.setIpAddress(ipAddress);
            eventLog.setRequestUrl(requestUrl);
            eventLog.setRequestMethod(requestMethod);
            eventLog.setSqlFragment(sqlFragment);
            eventLog.setRequestParams(requestParams);
            eventLog.setUsername(username);

            SecurityEventLog saved = securityEventLogRepository.save(eventLog);
            log.info("SQL 注入攻击日志已记录 - ID: {}, IP: {}, URL: {}", saved.getId(), ipAddress, requestUrl);
            return saved;
        } catch (Exception e) {
            log.error("记录 SQL 注入攻击日志失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 记录登录失败事件
     * 
     * @param ipAddress 客户端 IP 地址
     * @param username 用户名
     * @param requestUrl 请求 URL
     * @return 保存的安全事件日志
     */
    public SecurityEventLog logLoginFailure(String ipAddress, String username, String requestUrl) {
        try {
            SecurityEventLog eventLog = new SecurityEventLog();
            eventLog.setEventType("LOGIN_FAILURE");
            eventLog.setSeverity("MEDIUM");
            eventLog.setActionTaken("ALLOWED");
            eventLog.setDescription("用户登录失败");
            eventLog.setIpAddress(ipAddress);
            eventLog.setUsername(username);
            eventLog.setRequestUrl(requestUrl);
            eventLog.setRequestMethod("POST");

            SecurityEventLog saved = securityEventLogRepository.save(eventLog);
            log.info("登录失败日志已记录 - ID: {}, 用户: {}, IP: {}", saved.getId(), username, ipAddress);
            return saved;
        } catch (Exception e) {
            log.error("记录登录失败日志失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 记录未授权访问尝试
     * 
     * @param ipAddress 客户端 IP 地址
     * @param requestUrl 请求 URL
     * @param requestMethod 请求方法
     * @param username 用户名（可为空）
     * @return 保存的安全事件日志
     */
    public SecurityEventLog logUnauthorizedAccess(String ipAddress, String requestUrl, String requestMethod, String username) {
        try {
            SecurityEventLog eventLog = new SecurityEventLog();
            eventLog.setEventType("UNAUTHORIZED_ACCESS");
            eventLog.setSeverity("HIGH");
            eventLog.setActionTaken("BLOCKED");
            eventLog.setDescription("未授权访问尝试");
            eventLog.setIpAddress(ipAddress);
            eventLog.setRequestUrl(requestUrl);
            eventLog.setRequestMethod(requestMethod);
            eventLog.setUsername(username);

            SecurityEventLog saved = securityEventLogRepository.save(eventLog);
            log.info("未授权访问日志已记录 - ID: {}, IP: {}, URL: {}", saved.getId(), ipAddress, requestUrl);
            return saved;
        } catch (Exception e) {
            log.error("记录未授权访问日志失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 记录通用安全事件
     * 
     * @param eventType 事件类型
     * @param description 事件描述
     * @param ipAddress 客户端 IP 地址
     * @param requestUrl 请求 URL
     * @param requestMethod 请求方法
     * @param severity 严重程度
     * @param username 用户名（可为空）
     * @return 保存的安全事件日志
     */
    public SecurityEventLog logSecurityEvent(String eventType, String description, String ipAddress,
                                              String requestUrl, String requestMethod, String severity, String username) {
        try {
            SecurityEventLog eventLog = new SecurityEventLog();
            eventLog.setEventType(eventType);
            eventLog.setDescription(description);
            eventLog.setIpAddress(ipAddress);
            eventLog.setRequestUrl(requestUrl);
            eventLog.setRequestMethod(requestMethod);
            eventLog.setSeverity(severity);
            eventLog.setUsername(username);
            eventLog.setActionTaken("LOGGED");

            SecurityEventLog saved = securityEventLogRepository.save(eventLog);
            log.info("安全事件日志已记录 - ID: {}, 类型: {}, IP: {}", saved.getId(), eventType, ipAddress);
            return saved;
        } catch (Exception e) {
            log.error("记录安全事件日志失败: {}", e.getMessage(), e);
            return null;
        }
    }

    // ==================== 日志查询方法 ====================

    /**
     * 根据事件类型查询安全日志
     * 
     * @param eventType 事件类型
     * @return 安全事件日志列表
     */
    public List<SecurityEventLog> findByEventType(String eventType) {
        try {
            return securityEventLogRepository.findByEventType(eventType);
        } catch (Exception e) {
            log.error("查询事件类型日志失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 根据 IP 地址查询安全日志
     * 
     * @param ipAddress IP 地址
     * @return 安全事件日志列表
     */
    public List<SecurityEventLog> findByIpAddress(String ipAddress) {
        try {
            return securityEventLogRepository.findByIpAddress(ipAddress);
        } catch (Exception e) {
            log.error("查询 IP 地址日志失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 根据时间范围查询安全日志
     * 
     * @param start 开始时间
     * @param end 结束时间
     * @return 安全事件日志列表
     */
    public List<SecurityEventLog> findByTimeRange(LocalDateTime start, LocalDateTime end) {
        try {
            return securityEventLogRepository.findByEventTimeBetween(start, end);
        } catch (Exception e) {
            log.error("查询时间范围日志失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 根据严重程度查询安全日志
     * 
     * @param severity 严重程度
     * @return 安全事件日志列表
     */
    public List<SecurityEventLog> findBySeverity(String severity) {
        try {
            return securityEventLogRepository.findBySeverity(severity);
        } catch (Exception e) {
            log.error("查询严重程度日志失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 分页查询所有安全日志（按时间降序）
     * 
     * @param pageable 分页参数
     * @return 分页的安全事件日志
     */
    public Page<SecurityEventLog> findAllPaged(Pageable pageable) {
        try {
            return securityEventLogRepository.findAllByOrderByEventTimeDesc(pageable);
        } catch (Exception e) {
            log.error("分页查询日志失败: {}", e.getMessage(), e);
            return Page.empty();
        }
    }

    /**
     * 查询最近的 SQL 注入攻击记录
     * 
     * @param limit 返回数量限制
     * @return SQL 注入攻击记录列表
     */
    public List<SecurityEventLog> findRecentSqlInjectionAttempts(int limit) {
        try {
            Pageable pageable = PageRequest.of(0, limit);
            return securityEventLogRepository.findRecentSqlInjectionAttempts(pageable);
        } catch (Exception e) {
            log.error("查询最近 SQL 注入攻击记录失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ==================== 日志统计方法 ====================

    /**
     * 统计指定时间范围内的 SQL 注入攻击次数
     * 
     * @param start 开始时间
     * @param end 结束时间
     * @return SQL 注入攻击次数
     */
    public Long countSqlInjectionAttempts(LocalDateTime start, LocalDateTime end) {
        try {
            return securityEventLogRepository.countSqlInjectionAttempts(start, end);
        } catch (Exception e) {
            log.error("统计 SQL 注入攻击次数失败: {}", e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * 获取安全统计信息
     * 
     * @param start 开始时间
     * @param end 结束时间
     * @return 安全统计信息 Map
     */
    public Map<String, Object> getSecurityStatistics(LocalDateTime start, LocalDateTime end) {
        try {
            Map<String, Object> statistics = new HashMap<>();

            // SQL 注入攻击次数
            Long sqlInjectionCount = countSqlInjectionAttempts(start, end);
            statistics.put("sqlInjectionCount", sqlInjectionCount);

            // 总事件数
            Long totalEvents = securityEventLogRepository.count();
            statistics.put("totalEvents", totalEvents);

            // 时间范围
            Map<String, LocalDateTime> timeRange = new HashMap<>();
            timeRange.put("start", start);
            timeRange.put("end", end);
            statistics.put("timeRange", timeRange);

            // 各事件类型数量
            Map<String, Long> eventTypeCount = getEventTypeCount(start, end);
            statistics.put("eventTypeCount", eventTypeCount);

            return statistics;
        } catch (Exception e) {
            log.error("获取安全统计信息失败: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * 统计各事件类型的数量
     * 
     * @param start 开始时间
     * @param end 结束时间
     * @return 事件类型及其数量的 Map
     */
    public Map<String, Long> getEventTypeCount(LocalDateTime start, LocalDateTime end) {
        try {
            // 查询时间范围内的所有事件
            List<SecurityEventLog> events = findByTimeRange(start, end);

            // 按事件类型分组统计
            Map<String, Long> eventTypeCount = events.stream()
                    .collect(Collectors.groupingBy(
                            SecurityEventLog::getEventType,
                            Collectors.counting()
                    ));

            return eventTypeCount;
        } catch (Exception e) {
            log.error("统计事件类型数量失败: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
}
