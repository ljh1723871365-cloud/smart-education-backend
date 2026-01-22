package com.ljh.smarteducation.service;

import java.util.List;
import java.util.Map;

/**
 * 题目结构化提取器接口 - 混合式识别架构第二层
 * 负责从文本中提取结构化的题目信息
 */
public interface QuestionStructureExtractor {
    
    /**
     * 提取题目结构化信息
     * @param text 待提取的文本
     * @param formatResult 格式检测结果
     * @return 提取结果
     */
    ExtractionResult extractStructure(String text, QuestionFormatDetector.FormatDetectionResult formatResult);
    
    /**
     * 批量提取题目结构化信息
     * @param textSegments 文本段落列表
     * @param formatResults 格式检测结果列表
     * @return 提取结果列表
     */
    List<ExtractionResult> extractStructures(List<String> textSegments, 
                                             List<QuestionFormatDetector.FormatDetectionResult> formatResults);
    
    /**
     * 提取结果
     */
    class ExtractionResult {
        private String questionType;        // 题型
        private String subType;             // 子类型
        private String questionText;        // 题目文本
        private List<String> options;       // 选项列表（如果有）
        private String correctAnswer;       // 正确答案（如果已知）
        private Map<String, Object> metadata; // 元数据
        private double confidence;          // 提取置信度
        private String extractionRule;      // 使用的提取规则
        
        public ExtractionResult() {}
        
        public ExtractionResult(String questionType, String subType, double confidence) {
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
        
        public String getQuestionText() {
            return questionText;
        }
        
        public void setQuestionText(String questionText) {
            this.questionText = questionText;
        }
        
        public List<String> getOptions() {
            return options;
        }
        
        public void setOptions(List<String> options) {
            this.options = options;
        }
        
        public String getCorrectAnswer() {
            return correctAnswer;
        }
        
        public void setCorrectAnswer(String correctAnswer) {
            this.correctAnswer = correctAnswer;
        }
        
        public Map<String, Object> getMetadata() {
            return metadata;
        }
        
        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
        
        public double getConfidence() {
            return confidence;
        }
        
        public void setConfidence(double confidence) {
            this.confidence = confidence;
        }
        
        public String getExtractionRule() {
            return extractionRule;
        }
        
        public void setExtractionRule(String extractionRule) {
            this.extractionRule = extractionRule;
        }
        
        /**
         * 获取提取的完整数据（包含所有字段）
         * @return 提取的数据Map
         */
        public Map<String, Object> getExtractedData() {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("questionType", questionType);
            data.put("subType", subType);
            data.put("questionText", questionText);
            data.put("options", options);
            data.put("correctAnswer", correctAnswer);
            if (metadata != null) {
                data.putAll(metadata);
            }
            return data;
        }
        
        /**
         * 计算字段完整性评分（用于置信度计算）
         * @return 字段完整性分数 (0-1)
         */
        public double getFieldCompleteness() {
            int totalFields = 0;
            int filledFields = 0;
            
            // 必填字段
            totalFields += 3;
            if (questionType != null && !questionType.isEmpty()) filledFields++;
            if (subType != null && !subType.isEmpty()) filledFields++;
            if (questionText != null && !questionText.isEmpty()) filledFields++;
            
            // 可选字段
            totalFields += 2;
            if (options != null && !options.isEmpty()) filledFields++;
            if (correctAnswer != null && !correctAnswer.isEmpty()) filledFields++;
            
            return totalFields > 0 ? (double) filledFields / totalFields : 0.0;
        }
    }
}
