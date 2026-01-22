package com.ljh.smarteducation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ljh.smarteducation.entity.SecurityEventLog;
import com.ljh.smarteducation.repository.SecurityEventLogRepository;
import com.ljh.smarteducation.util.InputValidator;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * SQL 注入检测过滤器
 * 在请求进入业务层之前实时检测和拦截 SQL 注入攻击
 * 
 * @author Smart Education Team
 */
@Slf4j
@Component
@Order(1) // 设置最高优先级，在其他过滤器之前执行
public class SqlInjectionFilter implements Filter {

    private final SecurityEventLogRepository securityEventLogRepository;
    private final ObjectMapper objectMapper;

    // ==================== 配置项 ====================

    @Value("${sql.injection.filter.enabled:true}")
    private boolean filterEnabled;

    @Value("${sql.injection.filter.mode:STRICT}")
    private String filterMode;

    @Value("${sql.injection.filter.whitelist:}")
    private String whitelistPaths;

    private Set<String> whitelist;

    // ==================== 构造函数 ====================

    public SqlInjectionFilter(SecurityEventLogRepository securityEventLogRepository) {
        this.securityEventLogRepository = securityEventLogRepository;
        this.objectMapper = new ObjectMapper();
    }

    // ==================== Filter 接口方法 ====================

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // 初始化白名单
        whitelist = new HashSet<>();
        if (whitelistPaths != null && !whitelistPaths.trim().isEmpty()) {
            String[] paths = whitelistPaths.split(",");
            for (String path : paths) {
                whitelist.add(path.trim());
            }
        }
        
        log.info("========================================");
        log.info("SQL 注入检测过滤器已启动");
        log.info("启用状态: {}", filterEnabled);
        log.info("检测模式: {}", filterMode);
        log.info("白名单路径: {}", whitelist);
        log.info("========================================");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // 1. 如果过滤器未启用，直接放行
            if (!filterEnabled) {
                chain.doFilter(request, response);
                return;
            }

            // 2. 检查是否在白名单中
            String requestPath = httpRequest.getRequestURI();
            if (whitelist.contains(requestPath)) {
                log.debug("请求路径在白名单中，跳过检测: {}", requestPath);
                chain.doFilter(request, response);
                return;
            }

            // 3. 检测 URL 参数
            Map<String, String[]> parameterMap = httpRequest.getParameterMap();
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                String paramName = entry.getKey();
                String[] paramValues = entry.getValue();
                
                for (String paramValue : paramValues) {
                    if (InputValidator.containsSqlInjection(paramValue)) {
                        log.warn("检测到 SQL 注入攻击 - URL 参数: {} = {}", paramName, paramValue);
                        handleSqlInjectionDetected(httpRequest, httpResponse, paramValue);
                        return; // 拦截请求，不继续处理
                    }
                }
            }

            // 4. 检测请求体（仅 POST/PUT 请求）
            String method = httpRequest.getMethod();
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                String contentType = httpRequest.getContentType();
                
                // 仅检测 JSON 请求体
                if (contentType != null && contentType.contains("application/json")) {
                    // 缓存请求体以支持多次读取
                    CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(httpRequest);
                    String requestBody = cachedRequest.getBody();
                    
                    if (requestBody != null && !requestBody.trim().isEmpty()) {
                        if (InputValidator.containsSqlInjection(requestBody)) {
                            log.warn("检测到 SQL 注入攻击 - 请求体: {}", requestBody.substring(0, Math.min(100, requestBody.length())));
                            handleSqlInjectionDetected(cachedRequest, httpResponse, requestBody);
                            return; // 拦截请求，不继续处理
                        }
                    }
                    
                    // 使用缓存的请求继续处理
                    chain.doFilter(cachedRequest, response);
                    return;
                }
            }

            // 5. 没有检测到注入，放行请求
            chain.doFilter(request, response);

        } catch (Exception e) {
            // 异常情况下放行请求，避免影响正常业务
            log.error("SQL 注入检测过滤器异常: {}", e.getMessage(), e);
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        log.info("SQL 注入检测过滤器已销毁");
    }

    // ==================== 处理 SQL 注入检测 ====================

    /**
     * 处理检测到的 SQL 注入攻击
     * 
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param sqlFragment SQL 片段
     * @throws IOException IO 异常
     */
    private void handleSqlInjectionDetected(HttpServletRequest request, HttpServletResponse response, String sqlFragment)
            throws IOException {
        
        // 1. 创建安全事件日志
        SecurityEventLog eventLog = new SecurityEventLog();
        eventLog.setEventType("SQL_INJECTION_ATTEMPT");
        eventLog.setSeverity("HIGH");
        eventLog.setActionTaken("BLOCKED");
        eventLog.setDescription("检测到 SQL 注入攻击尝试");
        
        // 2. 设置请求信息
        eventLog.setIpAddress(getClientIpAddress(request));
        eventLog.setRequestUrl(request.getRequestURI());
        eventLog.setRequestMethod(request.getMethod());
        eventLog.setUserAgent(request.getHeader("User-Agent"));
        
        // 3. 设置 SQL 片段（截取前 500 字符）
        if (sqlFragment != null && sqlFragment.length() > 500) {
            eventLog.setSqlFragment(sqlFragment.substring(0, 500) + "...");
        } else {
            eventLog.setSqlFragment(sqlFragment);
        }
        
        // 4. 设置请求参数（转为 JSON，截取前 2000 字符）
        try {
            Map<String, String[]> paramMap = request.getParameterMap();
            String paramsJson = objectMapper.writeValueAsString(paramMap);
            if (paramsJson.length() > 2000) {
                eventLog.setRequestParams(paramsJson.substring(0, 2000) + "...");
            } else {
                eventLog.setRequestParams(paramsJson);
            }
        } catch (Exception e) {
            eventLog.setRequestParams("无法序列化参数");
        }
        
        // 5. 设置用户信息（如果已认证）
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() 
                && !"anonymousUser".equals(authentication.getPrincipal())) {
                eventLog.setUsername(authentication.getName());
            }
        } catch (Exception e) {
            log.debug("获取用户信息失败: {}", e.getMessage());
        }
        
        // 6. 异步保存日志（使用新线程，避免阻塞请求）
        new Thread(() -> {
            try {
                securityEventLogRepository.save(eventLog);
                log.info("SQL 注入攻击日志已保存 - IP: {}, URL: {}", eventLog.getIpAddress(), eventLog.getRequestUrl());
            } catch (Exception e) {
                log.error("保存安全日志失败: {}", e.getMessage(), e);
            }
        }).start();
        
        // 7. 返回错误响应
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "请求包含非法字符，已被拒绝");
        errorResponse.put("code", "INVALID_INPUT");
        
        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取客户端真实 IP 地址
     * 支持代理和负载均衡场景
     * 
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // 1. 优先从 X-Forwarded-For 获取（经过代理的情况）
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能包含多个 IP，取第一个
            int index = ip.indexOf(',');
            if (index != -1) {
                return ip.substring(0, index).trim();
            }
            return ip.trim();
        }
        
        // 2. 其次从 X-Real-IP 获取
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        
        // 3. 最后从 RemoteAddr 获取
        return request.getRemoteAddr();
    }

    // ==================== 请求体缓存类 ====================

    /**
     * 缓存请求体的 HttpServletRequest 包装类
     * 用于支持多次读取请求体
     */
    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        
        private final byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            // 读取并缓存请求体
            InputStream inputStream = request.getInputStream();
            this.cachedBody = inputStream.readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new CachedBodyServletInputStream(this.cachedBody);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
            return new BufferedReader(new InputStreamReader(byteArrayInputStream, StandardCharsets.UTF_8));
        }

        /**
         * 获取请求体内容
         * 
         * @return 请求体字符串
         */
        public String getBody() {
            return new String(this.cachedBody, StandardCharsets.UTF_8);
        }
    }

    /**
     * 缓存的 ServletInputStream 实现
     */
    private static class CachedBodyServletInputStream extends ServletInputStream {
        
        private final ByteArrayInputStream byteArrayInputStream;

        public CachedBodyServletInputStream(byte[] cachedBody) {
            this.byteArrayInputStream = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished() {
            return byteArrayInputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("不支持异步读取");
        }

        @Override
        public int read() throws IOException {
            return byteArrayInputStream.read();
        }
    }
}
