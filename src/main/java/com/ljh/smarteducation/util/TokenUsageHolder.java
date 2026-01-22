package com.ljh.smarteducation.util;

import lombok.Data;

/**
 * Token 使用信息持有者
 * 使用 ThreadLocal 在同一个线程中传递 Token 使用信息
 */
public class TokenUsageHolder {
    
    private static final ThreadLocal<TokenUsage> holder = new ThreadLocal<>();
    
    /**
     * 设置 Token 使用信息
     */
    public static void set(Integer inputTokens, Integer outputTokens, 
                          Long durationMs, String modelName) {
        TokenUsage usage = new TokenUsage();
        usage.setInputTokens(inputTokens);
        usage.setOutputTokens(outputTokens);
        usage.setDurationMs(durationMs);
        usage.setModelName(modelName);
        holder.set(usage);
    }
    
    /**
     * 获取 Token 使用信息
     */
    public static TokenUsage get() {
        return holder.get();
    }
    
    /**
     * 清除 Token 使用信息（防止内存泄漏）
     */
    public static void clear() {
        holder.remove();
    }
    
    /**
     * Token 使用信息内部类
     */
    @Data
    public static class TokenUsage {
        /**
         * 输入 Token 数
         */
        private Integer inputTokens;
        
        /**
         * 输出 Token 数
         */
        private Integer outputTokens;
        
        /**
         * API 调用耗时（毫秒）
         */
        private Long durationMs;
        
        /**
         * 使用的模型名称
         */
        private String modelName;
        
        /**
         * 获取总 Token 数
         */
        public Integer getTotalTokens() {
            if (inputTokens == null || outputTokens == null) {
                return 0;
            }
            return inputTokens + outputTokens;
        }
        
        /**
         * 判断是否有效
         */
        public boolean isValid() {
            return inputTokens != null && outputTokens != null && 
                   inputTokens > 0 && outputTokens > 0;
        }
    }
}

