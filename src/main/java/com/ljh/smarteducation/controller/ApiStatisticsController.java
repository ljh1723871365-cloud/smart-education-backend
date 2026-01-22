package com.ljh.smarteducation.controller;

import com.ljh.smarteducation.entity.ApiUsageLog;
import com.ljh.smarteducation.repository.ApiUsageLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API 使用统计控制器
 * 提供 Token 使用和成本统计查询接口
 */
@RestController
@RequestMapping("/api/admin/statistics")
public class ApiStatisticsController {
    
    @Autowired
    private ApiUsageLogRepository apiUsageLogRepository;
    
    /**
     * 获取 API 使用总体统计
     */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getSummary() {
        try {
            Map<String, Object> summary = new HashMap<>();
            
            // 总调用次数（仅统计成功的）
            long totalCalls = apiUsageLogRepository.countSuccessCalls();
            
            // 总 Token 使用量
            Long totalTokens = apiUsageLogRepository.sumTotalTokens();
            
            // 总成本
            Double totalCost = apiUsageLogRepository.sumTotalCost();
            
            // 总题目数
            Integer totalQuestions = apiUsageLogRepository.sumQuestionCount();
            
            // 计算平均值
            double avgTokensPerSet = totalCalls > 0 ? totalTokens / (double) totalCalls : 0;
            double avgCostPerSet = totalCalls > 0 ? totalCost / totalCalls : 0;
            double avgTokensPerQuestion = totalQuestions > 0 ? totalTokens / (double) totalQuestions : 0;
            double avgCostPerQuestion = totalQuestions > 0 ? totalCost / totalQuestions : 0;
            
            summary.put("totalCalls", totalCalls);
            summary.put("totalTokens", totalTokens);
            summary.put("totalCost", String.format("%.2f", totalCost));
            summary.put("totalCostRaw", totalCost);
            summary.put("totalQuestions", totalQuestions);
            summary.put("avgTokensPerSet", (int)avgTokensPerSet);
            summary.put("avgCostPerSet", String.format("%.4f", avgCostPerSet));
            summary.put("avgTokensPerQuestion", (int)avgTokensPerQuestion);
            summary.put("avgCostPerQuestion", String.format("%.4f", avgCostPerQuestion));
            
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取统计数据失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 获取最近的 API 调用日志
     */
    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "all") String status) {
        
        try {
            Pageable pageable = PageRequest.of(page, size, 
                Sort.by("createdAt").descending());
            
            Page<ApiUsageLog> logsPage;
            
            if ("success".equals(status)) {
                logsPage = apiUsageLogRepository.findBySuccessTrue(pageable);
            } else if ("failed".equals(status)) {
                logsPage = apiUsageLogRepository.findBySuccessFalse(pageable);
            } else {
                logsPage = apiUsageLogRepository.findAllByOrderByCreatedAtDesc(pageable);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("logs", logsPage.getContent());
            response.put("totalElements", logsPage.getTotalElements());
            response.put("totalPages", logsPage.getTotalPages());
            response.put("currentPage", page);
            response.put("pageSize", size);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取日志失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 获取成本趋势（按天统计）
     */
    @GetMapping("/cost-trend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getCostTrend(
            @RequestParam(required = false, defaultValue = "7") Integer days) {
        
        try {
            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            
            List<Map<String, Object>> trend = 
                apiUsageLogRepository.getCostTrendByDay(startDate);
            
            Map<String, Object> response = new HashMap<>();
            response.put("trend", trend);
            response.put("days", days);
            response.put("startDate", startDate.toString());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取趋势数据失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 按模型统计使用情况
     */
    @GetMapping("/by-model")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStatisticsByModel() {
        try {
            List<Map<String, Object>> modelStats = 
                apiUsageLogRepository.getStatisticsByModel();
            
            Map<String, Object> response = new HashMap<>();
            response.put("statistics", modelStats);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取模型统计失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 获取指定时间范围的统计
     */
    @GetMapping("/date-range")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStatsByDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        
        try {
            LocalDateTime start = LocalDateTime.parse(startDate);
            LocalDateTime end = LocalDateTime.parse(endDate);
            
            List<ApiUsageLog> logs = apiUsageLogRepository.findByDateRange(start, end);
            
            // 计算该时间段的统计
            long totalCalls = logs.stream().filter(ApiUsageLog::getSuccess).count();
            int totalTokens = logs.stream()
                .filter(ApiUsageLog::getSuccess)
                .mapToInt(log -> log.getTotalTokens() != null ? log.getTotalTokens() : 0)
                .sum();
            double totalCost = logs.stream()
                .filter(ApiUsageLog::getSuccess)
                .mapToDouble(log -> log.getTotalCost() != null ? log.getTotalCost() : 0.0)
                .sum();
            int totalQuestions = logs.stream()
                .filter(ApiUsageLog::getSuccess)
                .mapToInt(log -> log.getQuestionCount() != null ? log.getQuestionCount() : 0)
                .sum();
            
            Map<String, Object> response = new HashMap<>();
            response.put("startDate", startDate);
            response.put("endDate", endDate);
            response.put("totalCalls", totalCalls);
            response.put("totalTokens", totalTokens);
            response.put("totalCost", String.format("%.2f", totalCost));
            response.put("totalQuestions", totalQuestions);
            response.put("logs", logs);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取时间段统计失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 获取单个日志详情
     */
    @GetMapping("/logs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getLogDetail(@PathVariable Long id) {
        try {
            ApiUsageLog log = apiUsageLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("日志不存在"));
            
            Map<String, Object> response = new HashMap<>();
            response.put("log", log);
            
            // 添加额外的计算信息
            if (log.getQuestionCount() != null && log.getQuestionCount() > 0) {
                response.put("avgTokensPerQuestion", log.getAvgTokensPerQuestion());
                response.put("avgCostPerQuestion", log.getAvgCostPerQuestion());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取日志详情失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}

