package com.ljh.smarteducation.repository;

import com.ljh.smarteducation.entity.SecurityEventLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 安全事件日志 Repository 接口
 * 提供安全事件日志的数据访问功能
 */
@Repository
public interface SecurityEventLogRepository extends JpaRepository<SecurityEventLog, Long> {

    /**
     * 根据事件类型查询安全日志
     *
     * @param eventType 事件类型（如：SQL_INJECTION_ATTEMPT、LOGIN_FAILURE等）
     * @return 安全事件日志列表
     */
    List<SecurityEventLog> findByEventType(String eventType);

    /**
     * 根据IP地址查询安全日志
     *
     * @param ipAddress IP地址
     * @return 安全事件日志列表
     */
    List<SecurityEventLog> findByIpAddress(String ipAddress);

    /**
     * 根据严重程度查询安全日志
     *
     * @param severity 严重程度（HIGH、MEDIUM、LOW）
     * @return 安全事件日志列表
     */
    List<SecurityEventLog> findBySeverity(String severity);

    /**
     * 查询所有安全日志，按事件时间降序排列（分页）
     *
     * @param pageable 分页参数
     * @return 分页的安全事件日志
     */
    Page<SecurityEventLog> findAllByOrderByEventTimeDesc(Pageable pageable);

    /**
     * 根据时间范围查询安全日志
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 安全事件日志列表，按事件时间降序排列
     */
    @Query("SELECT s FROM SecurityEventLog s WHERE s.eventTime BETWEEN :start AND :end ORDER BY s.eventTime DESC")
    List<SecurityEventLog> findByEventTimeBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 统计指定时间范围内的SQL注入攻击次数
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return SQL注入攻击次数
     */
    @Query("SELECT COUNT(s) FROM SecurityEventLog s WHERE s.eventType = 'SQL_INJECTION_ATTEMPT' AND s.eventTime BETWEEN :start AND :end")
    Long countSqlInjectionAttempts(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 查询最近的SQL注入攻击记录
     *
     * @param pageable 分页参数（用于限制返回数量）
     * @return SQL注入攻击记录列表，按事件时间降序排列
     */
    @Query("SELECT s FROM SecurityEventLog s WHERE s.eventType = 'SQL_INJECTION_ATTEMPT' ORDER BY s.eventTime DESC")
    List<SecurityEventLog> findRecentSqlInjectionAttempts(Pageable pageable);
}
