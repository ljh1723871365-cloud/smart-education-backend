package com.ljh.smarteducation.service;

import com.ljh.smarteducation.service.QuestionFormatDetector.FormatDetectionResult;
import com.ljh.smarteducation.service.QuestionStructureExtractor.ExtractionResult;

import java.util.List;

/**
 * AI题目验证服务接口
 * 第三层：使用AI大模型辅助验证和优化识别结果
 */
public interface AiQuestionValidator {
    
    /**
     * 验证结果类
     */
    class ValidationResult {
        private boolean isValid;                    // 是否有效
        private double confidence;                  // 置信度(0-1)
        private String validationMethod;            // 验证方法
        private ExtractionResult optimizedResult;   // 优化后的提取结果
        private String aiSuggestion;               // AI建议
        private List<String> issues;               // 发现的问题
        
        // Getters and Setters
        public boolean isValid() {
            return isValid;
        }
        
        public void setValid(boolean valid) {
            isValid = valid;
        }
        
        public double getConfidence() {
            return confidence;
        }
        
        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }
        
        public String getValidationMethod() {
            return validationMethod;
        }
        
        public void setValidationMethod(String validationMethod) {
            this.validationMethod = validationMethod;
        }
        
        public ExtractionResult getOptimizedResult() {
            return optimizedResult;
        }
        
        public void setOptimizedResult(ExtractionResult optimizedResult) {
            this.optimizedResult = optimizedResult;
        }
        
        public String getAiSuggestion() {
            return aiSuggestion;
        }
        
        public void setAiSuggestion(String aiSuggestion) {
            this.aiSuggestion = aiSuggestion;
        }
        
        public List<String> getIssues() {
            return issues;
        }
        
        public void setIssues(List<String> issues) {
            this.issues = issues;
        }
    }
    
    /**
     * 验证单个提取结果
     * 
     * @param originalText 原始文本
     * @param formatResult 格式检测结果
     * @param extractionResult 结构化提取结果
     * @return 验证结果
     */
    ValidationResult validate(String originalText, 
                            FormatDetectionResult formatResult,
                            ExtractionResult extractionResult);
    
    /**
     * 批量验证提取结果
     * 
     * @param originalTexts 原始文本列表
     * @param formatResults 格式检测结果列表
     * @param extractionResults 结构化提取结果列表
     * @return 验证结果列表
     */
    List<ValidationResult> validateBatch(List<String> originalTexts,
                                        List<FormatDetectionResult> formatResults,
                                        List<ExtractionResult> extractionResults);
    
    /**
     * 使用AI优化提取结果
     * 仅在置信度低于阈值时调用
     * 
     * @param originalText 原始文本
     * @param extractionResult 提取结果
     * @return 优化后的提取结果
     */
    ExtractionResult optimizeWithAi(String originalText, ExtractionResult extractionResult);
}
