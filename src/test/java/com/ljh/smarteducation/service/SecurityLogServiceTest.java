package com.ljh.smarteducation.service;

import com.ljh.smarteducation.entity.SecurityEventLog;
import com.ljh.smarteducation.repository.SecurityEventLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SecurityLogService 的单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityLogService 测试")
class SecurityLogServiceTest {

    @Mock
    private SecurityEventLogRepository repository;

    @InjectMocks
    private SecurityLogService service;

    // ==================== 测试日志记录方法 ====================

    @Test
    @DisplayName("测试记录 SQL 注入攻击 - 应该成功保存日志")
    void testLogSqlInjectionAttempt_Success() {
        // 准备测试数据
        String ipAddress = "192.168.1.100";
        String requestUrl = "/api/practice/questions";
        String requestMethod = "GET";
        String sqlFragment = "' OR '1'='1";
        String requestParams = "{\"search\":\"' OR '1'='1\"}";
        String username = "user123";

        // 模拟保存操作
        SecurityEventLog savedLog = new SecurityEventLog();
        savedLog.setId(1L);
        when(repository.save(any(SecurityEventLog.class))).thenReturn(savedLog);

        // 执行测试
        SecurityEventLog result = service.logSqlInjectionAttempt(
            ipAddress, requestUrl, requestMethod, sqlFragment, requestParams, username
        );

        // 验证结果
        assertNotNull(result);
        assertEquals(1L, result.getId());

        // 验证 repository.save() 被调用
        ArgumentCaptor<SecurityEventLog> captor = ArgumentCaptor.forClass(SecurityEventLog.class);
        verify(repository, times(1)).save(captor.capture());

        // 验证保存的对象字段正确
        SecurityEventLog capturedLog = captor.getValue();
        assertEquals("SQL_INJECTION_ATTEMPT", capturedLog.getEventType());
        assertEquals("HIGH", capturedLog.getSeverity());
        assertEquals("BLOCKED", capturedLog.getActionTaken());
        assertEquals(ipAddress, capturedLog.getIpAddress());
        assertEquals(requestUrl, capturedLog.getRequestUrl());
        assertEquals(requestMethod, capturedLog.getRequestMethod());
        assertEquals(sqlFragment, capturedLog.getSqlFragment());
        assertEquals(requestParams, capturedLog.getRequestParams());
        assertEquals(username, capturedLog.getUsername());
    }

    @Test
    @DisplayName("测试记录登录失败 - 应该成功保存日志")
    void testLogLoginFailure_Success() {
        // 准备测试数据
        String ipAddress = "192.168.1.101";
        String username = "testuser";
        String requestUrl = "/api/auth/login";

        // 模拟保存操作
        SecurityEventLog savedLog = new SecurityEventLog();
        savedLog.setId(2L);
        when(repository.save(any(SecurityEventLog.class))).thenReturn(savedLog);

        // 执行测试
        SecurityEventLog result = service.logLoginFailure(ipAddress, username, requestUrl);

        // 验证结果
        assertNotNull(result);
        assertEquals(2L, result.getId());

        // 验证保存的对象字段
        ArgumentCaptor<SecurityEventLog> captor = ArgumentCaptor.forClass(SecurityEventLog.class);
        verify(repository, times(1)).save(captor.capture());

        SecurityEventLog capturedLog = captor.getValue();
        assertEquals("LOGIN_FAILURE", capturedLog.getEventType());
        assertEquals("MEDIUM", capturedLog.getSeverity());
        assertEquals("ALLOWED", capturedLog.getActionTaken());
        assertEquals(ipAddress, capturedLog.getIpAddress());
        assertEquals(username, capturedLog.getUsername());
        assertEquals(requestUrl, capturedLog.getRequestUrl());
    }

    @Test
    @DisplayName("测试记录未授权访问 - 应该成功保存日志")
    void testLogUnauthorizedAccess_Success() {
        // 准备测试数据
        String ipAddress = "192.168.1.102";
        String requestUrl = "/api/admin/users";
        String requestMethod = "GET";
        String username = "student1";

        // 模拟保存操作
        SecurityEventLog savedLog = new SecurityEventLog();
        savedLog.setId(3L);
        when(repository.save(any(SecurityEventLog.class))).thenReturn(savedLog);

        // 执行测试
        SecurityEventLog result = service.logUnauthorizedAccess(
            ipAddress, requestUrl, requestMethod, username
        );

        // 验证结果
        assertNotNull(result);
        assertEquals(3L, result.getId());

        // 验证保存的对象字段
        ArgumentCaptor<SecurityEventLog> captor = ArgumentCaptor.forClass(SecurityEventLog.class);
        verify(repository, times(1)).save(captor.capture());

        SecurityEventLog capturedLog = captor.getValue();
        assertEquals("UNAUTHORIZED_ACCESS", capturedLog.getEventType());
        assertEquals("HIGH", capturedLog.getSeverity());
        assertEquals("BLOCKED", capturedLog.getActionTaken());
    }

    // ==================== 测试查询方法 ====================

    @Test
    @DisplayName("测试按事件类型查询 - 应该返回正确的日志列表")
    void testFindByEventType_Success() {
        // 准备测试数据
        String eventType = "SQL_INJECTION_ATTEMPT";
        List<SecurityEventLog> mockLogs = Arrays.asList(
            createMockLog(1L, eventType),
            createMockLog(2L, eventType)
        );

        // 模拟查询操作
        when(repository.findByEventType(eventType)).thenReturn(mockLogs);

        // 执行测试
        List<SecurityEventLog> result = service.findByEventType(eventType);

        // 验证结果
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(eventType, result.get(0).getEventType());

        // 验证 repository 方法被调用
        verify(repository, times(1)).findByEventType(eventType);
    }

    @Test
    @DisplayName("测试按 IP 地址查询 - 应该返回正确的日志列表")
    void testFindByIpAddress_Success() {
        // 准备测试数据
        String ipAddress = "192.168.1.100";
        List<SecurityEventLog> mockLogs = Arrays.asList(
            createMockLog(1L, "SQL_INJECTION_ATTEMPT")
        );

        // 模拟查询操作
        when(repository.findByIpAddress(ipAddress)).thenReturn(mockLogs);

        // 执行测试
        List<SecurityEventLog> result = service.findByIpAddress(ipAddress);

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.size());

        verify(repository, times(1)).findByIpAddress(ipAddress);
    }

    @Test
    @DisplayName("测试分页查询 - 应该返回正确的分页数据")
    void testFindAllPaged_Success() {
        // 准备测试数据
        Pageable pageable = PageRequest.of(0, 10);
        List<SecurityEventLog> mockLogs = Arrays.asList(
            createMockLog(1L, "SQL_INJECTION_ATTEMPT"),
            createMockLog(2L, "LOGIN_FAILURE")
        );
        Page<SecurityEventLog> mockPage = new PageImpl<>(mockLogs, pageable, 2);

        // 模拟查询操作
        when(repository.findAllByOrderByEventTimeDesc(pageable)).thenReturn(mockPage);

        // 执行测试
        Page<SecurityEventLog> result = service.findAllPaged(pageable);

        // 验证结果
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(2, result.getTotalElements());

        verify(repository, times(1)).findAllByOrderByEventTimeDesc(pageable);
    }

    // ==================== 测试统计方法 ====================

    @Test
    @DisplayName("测试统计 SQL 注入攻击次数 - 应该返回正确的数量")
    void testCountSqlInjectionAttempts_Success() {
        // 准备测试数据
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        Long expectedCount = 15L;

        // 模拟统计操作
        when(repository.countSqlInjectionAttempts(start, end)).thenReturn(expectedCount);

        // 执行测试
        Long result = service.countSqlInjectionAttempts(start, end);

        // 验证结果
        assertNotNull(result);
        assertEquals(expectedCount, result);

        verify(repository, times(1)).countSqlInjectionAttempts(start, end);
    }

    @Test
    @DisplayName("测试获取事件类型统计 - 应该返回正确的统计数据")
    void testGetEventTypeCount_Success() {
        // 准备测试数据
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        List<SecurityEventLog> mockLogs = Arrays.asList(
            createMockLog(1L, "SQL_INJECTION_ATTEMPT"),
            createMockLog(2L, "SQL_INJECTION_ATTEMPT"),
            createMockLog(3L, "LOGIN_FAILURE"),
            createMockLog(4L, "UNAUTHORIZED_ACCESS")
        );

        // 模拟查询操作
        when(repository.findByEventTimeBetween(start, end)).thenReturn(mockLogs);

        // 执行测试
        var result = service.getEventTypeCount(start, end);

        // 验证结果
        assertNotNull(result);
        assertEquals(2L, result.get("SQL_INJECTION_ATTEMPT"));
        assertEquals(1L, result.get("LOGIN_FAILURE"));
        assertEquals(1L, result.get("UNAUTHORIZED_ACCESS"));
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建模拟的 SecurityEventLog 对象
     */
    private SecurityEventLog createMockLog(Long id, String eventType) {
        SecurityEventLog log = new SecurityEventLog();
        log.setId(id);
        log.setEventType(eventType);
        log.setIpAddress("192.168.1.100");
        log.setRequestUrl("/api/test");
        log.setRequestMethod("GET");
        log.setSeverity("HIGH");
        log.setActionTaken("BLOCKED");
        return log;
    }
}
