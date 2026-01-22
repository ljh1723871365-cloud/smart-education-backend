package com.ljh.smarteducation.repository;

import com.ljh.smarteducation.entity.ApiUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, Long> {
    
    /**
     * 查找某个套题的调用记录
     */
    List<ApiUsageLog> findByQuestionSetId(Long questionSetId);
    
    /**
     * 查找某个用户的调用记录
     */
    List<ApiUsageLog> findByUserId(Long userId);
    
    /**
     * 查找成功的调用记录
     */
    Page<ApiUsageLog> findBySuccessTrue(Pageable pageable);
    
    /**
     * 查找失败的调用记录
     */
    Page<ApiUsageLog> findBySuccessFalse(Pageable pageable);
    
    /**
     * 统计总 Token 使用量
     */
    @Query("SELECT COALESCE(SUM(a.totalTokens), 0) FROM ApiUsageLog a WHERE a.success = true")
    Long sumTotalTokens();
    
    /**
     * 统计总成本
     */
    @Query("SELECT COALESCE(SUM(a.totalCost), 0.0) FROM ApiUsageLog a WHERE a.success = true")
    Double sumTotalCost();
    
    /**
     * 统计总题目数
     */
    @Query("SELECT COALESCE(SUM(a.questionCount), 0) FROM ApiUsageLog a WHERE a.success = true")
    Integer sumQuestionCount();
    
    /**
     * 统计成功调用次数
     */
    @Query("SELECT COUNT(a) FROM ApiUsageLog a WHERE a.success = true")
    Long countSuccessCalls();
    
    /**
     * 按日期统计成本趋势
     */
    @Query(value = "SELECT DATE(created_at) as date, " +
                   "COUNT(*) as call_count, " +
                   "SUM(total_tokens) as total_tokens, " +
                   "SUM(total_cost) as total_cost, " +
                   "SUM(question_count) as question_count " +
                   "FROM api_usage_log " +
                   "WHERE success = true AND created_at >= :startDate " +
                   "GROUP BY DATE(created_at) " +
                   "ORDER BY DATE(created_at) DESC", 
           nativeQuery = true)
    List<Map<String, Object>> getCostTrendByDay(@Param("startDate") LocalDateTime startDate);
    
    /**
     * 按模型统计使用情况
     */
    @Query("SELECT a.modelName as modelName, " +
           "COUNT(a) as callCount, " +
           "SUM(a.totalTokens) as totalTokens, " +
           "SUM(a.totalCost) as totalCost " +
           "FROM ApiUsageLog a " +
           "WHERE a.success = true " +
           "GROUP BY a.modelName")
    List<Map<String, Object>> getStatisticsByModel();
    
    /**
     * 查询指定时间范围的记录
     */
    @Query("SELECT a FROM ApiUsageLog a WHERE a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    List<ApiUsageLog> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate);
    
    /**
     * 查询最近 N 条记录
     */
    Page<ApiUsageLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

