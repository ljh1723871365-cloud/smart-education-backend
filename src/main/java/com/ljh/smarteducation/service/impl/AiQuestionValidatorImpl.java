package com.ljh.smarteducation.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ljh.smarteducation.service.AiQuestionValidator;
import com.ljh.smarteducation.service.QuestionFormatDetector.FormatDetectionResult;
import com.ljh.smarteducation.service.QuestionStructureExtractor.ExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * AI题目验证服务实现类
 * 第三层：使用SiliconFlow大模型辅助验证和优化识别结果
 */
@Service
public class AiQuestionValidatorImpl implements AiQuestionValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(AiQuestionValidatorImpl.class);
    
    @Value("${siliconflow.api.base-url}")
    private String apiBaseUrl;
    
    @Value("${siliconflow.api.key}")
    private String apiKey;
    
    @Value("${siliconflow.api.model}")
    private String model;
    
    // 置信度阈值：低于此值则使用AI优化
    private static final double CONFIDENCE_THRESHOLD = 0.7;
    
    // HTTP客户端
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public ValidationResult validate(String originalText, 
                                    FormatDetectionResult formatResult,
                                    ExtractionResult extractionResult) {
        ValidationResult result = new ValidationResult();
        List<String> issues = new ArrayList<>();
        
        // 1. 基础验证
        if (extractionResult == null) {
            result.setValid(false);
            result.setConfidence(0.0);
            result.setValidationMethod("基础验证");
            issues.add("提取结果为空");
            result.setIssues(issues);
            return result;
        }
        
        // 2. 检查必要字段
        if (extractionResult.getQuestionText() == null || 
            extractionResult.getQuestionText().trim().isEmpty()) {
            issues.add("题目文本为空");
        }
        
        // 3. 检查选项完整性（如果是选择题）
        if (isChoiceQuestion(extractionResult.getQuestionType())) {
            if (extractionResult.getOptions() == null || extractionResult.getOptions().isEmpty()) {
                issues.add("选择题缺少选项");
            } else if (extractionResult.getOptions().size() < 2) {
                issues.add("选项数量不足");
            }
        }
        
        // 4. 检查答案
        if (extractionResult.getCorrectAnswer() == null || 
            extractionResult.getCorrectAnswer().trim().isEmpty()) {
            issues.add("缺少答案");
        }
        
        // 5. 计算初步置信度
        double baseConfidence = extractionResult.getConfidence();
        double validationConfidence = calculateValidationConfidence(extractionResult, issues);
        double finalConfidence = (baseConfidence + validationConfidence) / 2.0;
        
        result.setConfidence(finalConfidence);
        result.setIssues(issues);
        result.setValid(issues.isEmpty());
        result.setValidationMethod("规则验证");
        
        // 6. 如果置信度低于阈值，使用AI优化
        if (finalConfidence < CONFIDENCE_THRESHOLD) {
            logger.info("置信度{}低于阈值{}，启用AI优化", finalConfidence, CONFIDENCE_THRESHOLD);
            try {
                ExtractionResult optimized = optimizeWithAi(originalText, extractionResult);
                if (optimized != null && optimized.getConfidence() > finalConfidence) {
                    result.setOptimizedResult(optimized);
                    result.setConfidence(optimized.getConfidence());
                    result.setValidationMethod("AI优化验证");
                    result.setValid(true);
                    logger.info("AI优化成功，置信度从{}提升到{}", finalConfidence, optimized.getConfidence());
                }
            } catch (Exception e) {
                logger.error("AI优化失败", e);
                result.setAiSuggestion("AI优化失败: " + e.getMessage());
            }
        }
        
        // 7. 如果没有优化结果，使用原始结果
        if (result.getOptimizedResult() == null) {
            result.setOptimizedResult(extractionResult);
        }
        
        return result;
    }
    
    @Override
    public List<ValidationResult> validateBatch(List<String> originalTexts,
                                               List<FormatDetectionResult> formatResults,
                                               List<ExtractionResult> extractionResults) {
        List<ValidationResult> results = new ArrayList<>();
        
        for (int i = 0; i < extractionResults.size(); i++) {
            String text = (originalTexts != null && i < originalTexts.size()) ? originalTexts.get(i) : "";
            FormatDetectionResult formatResult = (formatResults != null && i < formatResults.size()) 
                ? formatResults.get(i) : null;
            ExtractionResult extractionResult = extractionResults.get(i);
            
            ValidationResult result = validate(text, formatResult, extractionResult);
            results.add(result);
        }
        
        return results;
    }
    
    @Override
    public ExtractionResult optimizeWithAi(String originalText, ExtractionResult extractionResult) {
        try {
            // 构建提示词
            String prompt = buildOptimizationPrompt(originalText, extractionResult);
            
            // 调用SiliconFlow API
            String aiResponse = callSiliconFlowApi(prompt);
            
            // 解析AI响应
            ExtractionResult optimized = parseAiResponse(aiResponse, extractionResult);
            
            return optimized;
            
        } catch (Exception e) {
            logger.error("AI优化失败", e);
            return null;
        }
    }
    
    /**
     * 构建AI优化提示词
     */
    private String buildOptimizationPrompt(String originalText, ExtractionResult extractionResult) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位英语考试题目分析专家。请分析以下题目并提取结构化信息。\n\n");
        prompt.append("原始题目文本：\n").append(originalText).append("\n\n");
        
        prompt.append("当前识别结果：\n");
        prompt.append("- 题型：").append(extractionResult.getQuestionType()).append("\n");
        prompt.append("- 子类型：").append(extractionResult.getSubType()).append("\n");
        prompt.append("- 题目文本：").append(extractionResult.getQuestionText()).append("\n");
        
        if (extractionResult.getOptions() != null && !extractionResult.getOptions().isEmpty()) {
            prompt.append("- 选项：\n");
            char optionLabel = 'A';
            for (String option : extractionResult.getOptions()) {
                prompt.append("  ").append(optionLabel++).append(") ").append(option).append("\n");
            }
        }
        
        if (extractionResult.getCorrectAnswer() != null) {
            prompt.append("- 答案：").append(extractionResult.getCorrectAnswer()).append("\n");
        }
        
        prompt.append("\n请按照以下JSON格式输出优化后的结构化信息：\n");
        prompt.append("{\n");
        prompt.append("  \"questionText\": \"题目文本\",\n");
        prompt.append("  \"options\": [\"选项A\", \"选项B\", \"选项C\", \"选项D\"],\n");
        prompt.append("  \"correctAnswer\": \"正确答案\",\n");
        prompt.append("  \"confidence\": 0.95\n");
        prompt.append("}\n\n");
        prompt.append("注意：\n");
        prompt.append("1. 如果是选择题，请完整提取所有选项\n");
        prompt.append("2. 如果是填空题，答案部分请提取关键词\n");
        prompt.append("3. 如果是翻译题，答案部分请提取参考译文\n");
        prompt.append("4. 置信度请根据提取的完整性和准确性评估（0-1之间）\n");
        prompt.append("5. 只返回JSON，不要包含其他说明文字\n");
        
        return prompt.toString();
    }
    
    /**
     * 调用SiliconFlow API
     */
    private String callSiliconFlowApi(String prompt) throws Exception {
        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 2000);
        requestBody.put("temperature", 0.3);  // 降低温度以获得更确定的结果
        
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.put("messages", messages);
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        
        // 构建HTTP请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBaseUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        
        // 发送请求
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("API调用失败: " + response.statusCode() + " - " + response.body());
        }
        
        // 解析响应
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode firstChoice = choices.get(0);
            JsonNode messageNode = firstChoice.get("message");
            if (messageNode != null) {
                JsonNode content = messageNode.get("content");
                if (content != null) {
                    return content.asText();
                }
            }
        }
        
        throw new RuntimeException("无法从API响应中提取内容");
    }
    
    /**
     * 解析AI响应
     */
    private ExtractionResult parseAiResponse(String aiResponse, ExtractionResult originalResult) {
        try {
            // 提取JSON部分（可能被包含在其他文本中）
            String jsonContent = extractJsonFromText(aiResponse);
            
            // 解析JSON
            JsonNode jsonNode = objectMapper.readTree(jsonContent);
            
            // 创建优化后的结果
            ExtractionResult optimized = new ExtractionResult();
            optimized.setQuestionType(originalResult.getQuestionType());
            optimized.setSubType(originalResult.getSubType());
            optimized.setExtractionRule("AI优化");
            
            // 提取字段
            if (jsonNode.has("questionText")) {
                optimized.setQuestionText(jsonNode.get("questionText").asText());
            } else {
                optimized.setQuestionText(originalResult.getQuestionText());
            }
            
            if (jsonNode.has("options")) {
                JsonNode optionsNode = jsonNode.get("options");
                if (optionsNode.isArray()) {
                    List<String> options = new ArrayList<>();
                    optionsNode.forEach(option -> options.add(option.asText()));
                    optimized.setOptions(options);
                }
            } else {
                optimized.setOptions(originalResult.getOptions());
            }
            
            if (jsonNode.has("correctAnswer")) {
                optimized.setCorrectAnswer(jsonNode.get("correctAnswer").asText());
            } else {
                optimized.setCorrectAnswer(originalResult.getCorrectAnswer());
            }
            
            if (jsonNode.has("confidence")) {
                optimized.setConfidence(jsonNode.get("confidence").asDouble());
            } else {
                optimized.setConfidence(0.8);  // 默认AI优化置信度
            }
            
            // 保留原始元数据
            optimized.setMetadata(originalResult.getMetadata());
            
            return optimized;
            
        } catch (Exception e) {
            logger.error("解析AI响应失败: {}", aiResponse, e);
            return null;
        }
    }
    
    /**
     * 从文本中提取JSON
     */
    private String extractJsonFromText(String text) {
        // 尝试找到JSON的开始和结束位置
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        
        return text;
    }
    
    /**
     * 计算验证置信度
     */
    private double calculateValidationConfidence(ExtractionResult result, List<String> issues) {
        double confidence = 1.0;
        
        // 每个问题降低0.15的置信度
        confidence -= issues.size() * 0.15;
        
        // 检查字段完整性
        if (result.getQuestionText() == null || result.getQuestionText().trim().isEmpty()) {
            confidence -= 0.3;
        }
        
        if (isChoiceQuestion(result.getQuestionType())) {
            if (result.getOptions() == null || result.getOptions().isEmpty()) {
                confidence -= 0.3;
            } else if (result.getOptions().size() < 2) {
                confidence -= 0.2;
            }
        }
        
        if (result.getCorrectAnswer() == null || result.getCorrectAnswer().trim().isEmpty()) {
            confidence -= 0.2;
        }
        
        return Math.max(confidence, 0.0);
    }
    
    /**
     * 判断是否为选择题
     */
    private boolean isChoiceQuestion(String questionType) {
        if (questionType == null) return false;
        
        return questionType.equals("LISTENING") || 
               questionType.equals("READING") || 
               questionType.equals("CHOICE") ||
               questionType.contains("CHOICE") ||
               questionType.contains("MULTIPLE");
    }
}
