package com.ljh.smarteducation.service;

import java.util.List;
import java.util.Map;

/**
 * 题目格式检测器接口 - 混合式识别架构第一层
 * 负责识别试卷文本的题型和格式
 */
public interface QuestionFormatDetector {
    
    /**
     * 检测文本的题型格式
     * @param text 待检测的文本
     * @return 检测结果，包含题型、置信度等信息
     */
    FormatDetectionResult detectFormat(String text);
    
    /**
     * 批量检测多个文本段落的格式
     * @param textSegments 文本段落列表
     * @return 检测结果列表
     */
    List<FormatDetectionResult> detectFormats(List<String> textSegments);
    
    /**
     * 格式检测结果
     */
    class FormatDetectionResult {
        private String questionType;        // 题型（如：LISTENING_CONVERSATION, READING_CHOICE等）
        private String subType;             // 子类型（如：短对话、长对话等）
        private double confidence;          // 置信度 0-1
        private Map<String, Object> features; // 特征信息
        private String detectionRule;       // 命中的检测规则
        
        public FormatDetectionResult() {}
        
        public FormatDetectionResult(String questionType, String subType, double confidence) {
            this.questionType = questionType;
            this.subType = subType;
            this.confidence = confidence;
        }
        
        // Getters and Setters
        public String getQuestionType() {
            return questionType;
        }
        
        public void setQuestionType(String questionType) {
            this.questionType = questionType;
        }
        
        public String getSubType() {
            return subType;
        }
        
        public void setSubType(String subType) {
            this.subType = subType;
        }
        
        public double getConfidence() {
            return confidence;
        }
        
        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }
        
        public Map<String, Object> getFeatures() {
            return features;
        }
        
        public void setFeatures(Map<String, Object> features) {
            this.features = features;
        }
        
        public String getDetectionRule() {
            return detectionRule;
        }
        
        public void setDetectionRule(String detectionRule) {
            this.detectionRule = detectionRule;
        }
    }
}
