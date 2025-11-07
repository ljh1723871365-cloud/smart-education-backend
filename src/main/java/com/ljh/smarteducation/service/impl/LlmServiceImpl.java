package com.ljh.smarteducation.service.impl;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.ljh.smarteducation.service.LlmService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class LlmServiceImpl implements LlmService {

    @Value("${alibaba.cloud.accessKeySecret}")
    private String accessKeySecret;

    @Value("${dashscope.api.model}") // (注意) 确保您 application.properties 里的键名是 dashscope.api.model
    private String modelName;

    // (不需要 client 字段)

    @Override
    public Mono<String> getStructuredQuestions(String rawText, String subject) {
        // 限制输入文本长度，避免请求过大导致连接中断
        int maxTextLength = 50000; // 限制为50000字符
        final String finalRawText;
        if (rawText != null && rawText.length() > maxTextLength) {
            System.out.println(">>> Warning: Text too long (" + rawText.length() + " chars), truncating to " + maxTextLength + " chars");
            finalRawText = rawText.substring(0, maxTextLength) + "\n\n[注意：文本已截断，仅处理前" + maxTextLength + "个字符]";
        } else {
            finalRawText = rawText;
        }
        
        // 添加重试机制，最多重试3次
        return Mono.fromCallable(() -> callQwenSyncWithRetry(finalRawText, subject, 3))
                .onErrorResume(throwable -> {
                    String errorMessage = (throwable.getMessage() != null) ? throwable.getMessage() : "Unknown AI service error.";
                    System.err.println("Error calling DashScope API after retries: " + errorMessage);
                    
                    // 提供更友好的错误信息
                    if (errorMessage.contains("unexpected end of stream") || errorMessage.contains("end of stream")) {
                        return Mono.error(new RuntimeException("AI服务连接中断，可能是网络不稳定或请求过大。请尝试：" +
                            "1. 检查网络连接；2. 尝试上传较小的文件；3. 稍后重试。"));
                    }
                    
                    return Mono.error(new RuntimeException("AI service call failed: " + errorMessage));
                });
    }
    
    /**
     * 带重试机制的API调用
     */
    private String callQwenSyncWithRetry(String rawText, String subject, int maxRetries) 
            throws NoApiKeyException, InputRequiredException, ApiException {
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount < maxRetries) {
            try {
                System.out.println(">>> Attempting API call (attempt " + (retryCount + 1) + "/" + maxRetries + ")");
                return callQwenSync(rawText, subject);
            } catch (ApiException e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                
                // 如果是连接中断错误，尝试重试
                if (errorMsg.contains("unexpected end of stream") || 
                    errorMsg.contains("end of stream") ||
                    errorMsg.contains("Connection reset") ||
                    errorMsg.contains("500") ||
                    errorMsg.contains("timeout")) {
                    
                    retryCount++;
                    if (retryCount < maxRetries) {
                        System.err.println(">>> API call failed, retrying in " + (retryCount * 2) + " seconds...");
                        try {
                            Thread.sleep(retryCount * 2000); // 递增延迟：2秒、4秒、6秒
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Retry interrupted", ie);
                        }
                        continue;
                    }
                }
                
                // 其他错误直接抛出
                throw e;
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                if (retryCount < maxRetries) {
                    System.err.println(">>> Unexpected error, retrying in " + (retryCount * 2) + " seconds...");
                    try {
                        Thread.sleep(retryCount * 2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                    continue;
                }
                throw new RuntimeException("Failed after " + maxRetries + " retries", e);
            }
        }
        
        // 所有重试都失败了
        throw new RuntimeException("Failed after " + maxRetries + " retries", lastException);
    }

    private String callQwenSync(String rawText, String subject) throws NoApiKeyException, InputRequiredException, ApiException {

        // 使用简单的 Generation 构造函数
        Generation gen = new Generation();

        System.out.println(">>> Calling DashScope API...");
        System.out.println(">>> Input text length: " + (rawText != null ? rawText.length() : 0) + " characters");

        // 使用 List<Message> 替代已弃用的 MessageManager
        List<Message> messages = new ArrayList<>();
        String prompt = buildPrompt(rawText, subject);
        
        // 检查 prompt 长度，如果太长则警告
        if (prompt.length() > 100000) {
            System.err.println(">>> Warning: Prompt is very long (" + prompt.length() + " chars), this may cause connection issues");
        }
        
        Message userMsg = Message.builder().role(Role.USER.getValue()).content(prompt).build();
        messages.add(userMsg);

        // 在 Param 中设置 apiKey 和 maxTokens
        GenerationParam param = GenerationParam.builder()
                .apiKey(accessKeySecret)
                .model(modelName)
                .messages(messages)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .topP(0.8)
                .maxTokens(16384) // 增加到16384，避免JSON被截断
                .build();

        long startTime = System.currentTimeMillis();
        GenerationResult result;
        try {
            result = gen.call(param);
            long duration = System.currentTimeMillis() - startTime;
            System.out.println(">>> API call completed in " + duration + "ms");
        } catch (ApiException e) {
            long duration = System.currentTimeMillis() - startTime;
            System.err.println(">>> API call failed after " + duration + "ms");
            System.err.println(">>> Error message: " + e.getMessage());
            System.err.println(">>> Exception type: " + e.getClass().getSimpleName());
            throw e;
        }

        if (result != null && result.getOutput() != null && !result.getOutput().getChoices().isEmpty()) {
            String jsonOutput = result.getOutput().getChoices().get(0).getMessage().getContent();
            
            // 清理markdown格式
            jsonOutput = jsonOutput.replace("```json", "").replace("```", "").trim();
            
            // 修复JSON字符串中的特殊字符问题
            jsonOutput = fixJsonStringEscaping(jsonOutput);
            
            // 检查是否被截断（检查结尾是否完整）
            jsonOutput = validateAndFixJson(jsonOutput);
            
            System.out.println(">>> JSON Output length: " + jsonOutput.length());
            System.out.println(">>> JSON Output preview (first 500 chars): " + jsonOutput.substring(0, Math.min(500, jsonOutput.length())));
            
            return jsonOutput;
        } else {
            return "{\"error\": \"AI service returned empty or invalid result.\"}";
        }
    }


    /**
     * 修复JSON字符串中的转义问题
     * 处理字符串值中未转义的特殊字符，特别是引号
     */
    private String fixJsonStringEscaping(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }
        
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escapeNext = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (escapeNext) {
                // 当前字符是转义后的字符
                result.append(c);
                escapeNext = false;
                continue;
            }
            
            if (c == '\\') {
                // 转义字符
                escapeNext = true;
                result.append(c);
                continue;
            }
            
            if (c == '"') {
                // 检查是否是字符串的开始/结束引号
                // 如果前面不是冒号、逗号、左括号、左方括号，且不在字符串中，可能是字符串内容中的引号
                if (inString) {
                    // 在字符串内部遇到引号
                    // 检查下一个字符是否是冒号、逗号、右括号等，来判断是否是字符串结束
                    boolean isEndQuote = false;
                    if (i + 1 < json.length()) {
                        char nextChar = json.charAt(i + 1);
                        // 如果下一个字符是: , } ] 或空白，可能是字符串结束
                        if (nextChar == ':' || nextChar == ',' || nextChar == '}' || 
                            nextChar == ']' || Character.isWhitespace(nextChar)) {
                            isEndQuote = true;
                        }
                    } else {
                        // 字符串末尾，应该是结束引号
                        isEndQuote = true;
                    }
                    
                    if (isEndQuote) {
                        // 这是字符串结束引号
                        inString = false;
                        result.append(c);
                    } else {
                        // 这是字符串内容中的引号，需要转义
                        result.append("\\\"");
                    }
                } else {
                    // 不在字符串中，这是字符串开始引号
                    inString = true;
                    result.append(c);
                }
                continue;
            }
            
            if (inString) {
                // 在字符串内部
                // 检查是否有需要转义的特殊字符
                if (c == '\n') {
                    // 换行符需要转义
                    result.append("\\n");
                } else if (c == '\r') {
                    // 回车符需要转义
                    result.append("\\r");
                } else if (c == '\t') {
                    // 制表符需要转义
                    result.append("\\t");
                } else if (c == '\b') {
                    // 退格符需要转义
                    result.append("\\b");
                } else if (c == '\f') {
                    // 换页符需要转义
                    result.append("\\f");
                } else {
                    result.append(c);
                }
            } else {
                // 不在字符串内部
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    /**
     * 验证并修复JSON（如果被截断）
     * 尝试修复不完整的JSON，如果不能修复则返回原始JSON让调用方处理错误
     */
    private String validateAndFixJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "{\"questions\": []}";
        }
        
        json = json.trim();
        
        // 简单检查：如果JSON以"questions"数组开始但没有正确闭合，尝试修复
        // 首先检查是否包含questions键
        if (!json.contains("\"questions\"")) {
            return json; // 如果连questions都没有，返回原样让解析器报告错误
        }
        
        // 检查括号是否匹配（简单计数，不考虑字符串内的括号）
        int lastBrace = json.lastIndexOf('}');
        int lastBracket = json.lastIndexOf(']');
        
        // 如果JSON明显不完整（没有以}或]结尾），尝试添加闭合括号
        if (lastBrace < lastBracket && lastBracket > 0) {
            // 以]结尾，需要检查是否还有外层}
            if (!json.trim().endsWith("}")) {
                json = json.trim();
                if (!json.endsWith("]")) {
                    json += "]";
                }
                json += "}";
            }
        } else if (lastBracket < lastBrace && lastBrace > 0) {
            // 以}结尾，但可能缺少]
            // 找到questions数组的开始
            int questionsIdx = json.indexOf("\"questions\"");
            if (questionsIdx > 0) {
                int arrayStart = json.indexOf('[', questionsIdx);
                if (arrayStart > 0) {
                    // 检查从数组开始到结尾是否有未闭合的]
                    String afterArray = json.substring(arrayStart);
                    long openCount = afterArray.chars().filter(ch -> ch == '[').count();
                    long closeCount = afterArray.chars().filter(ch -> ch == ']').count();
                    if (openCount > closeCount) {
                        // 在最后一个}之前插入缺失的]
                        int lastCloseBrace = json.lastIndexOf('}');
                        if (lastCloseBrace > 0) {
                            json = json.substring(0, lastCloseBrace) + "]" + json.substring(lastCloseBrace);
                        }
                    }
                }
            }
        }
        
        return json;
    }

    /**
     * (保持不变) 最终版 Prompt
     */
    private String buildPrompt(String rawText, String subject) {
        return String.format("""
            You are an expert in educational content analysis for a complex exam paper.
            Analyze the following text from a %s exam paper and convert it into a structured JSON object.
            The JSON object must have a single key "questions" which is an array of question objects.

            Each question object must have the following fields:
            
            - "sequenceNumber": The sequential order of the question in the entire paper (e.g., 1, 2, 3...).
            - "questionText": The full text of the question prompt (e.g., "Directions: For this part...", "Questions 1 and 2 are based on...").
            - "questionType": The type of the question. Must be one of: "WRITING", "LISTENING", "MULTIPLE_CHOICE", "TRANSLATION".
            - "difficulty": A difficulty rating (e.g., "Easy", "Medium", "Hard").
            - "knowledgePoint": The primary knowledge point being tested (e.g., "Composition", "Listening Comprehension").
            - "options": An array of option objects.

            **VERY IMPORTANT RULES FOR `questionType` and `options`:**

            1.  **WRITING / TRANSLATION:** If the text is "Part I Writing" or "Part IV Translation":
                - `questionType` MUST be "WRITING" or "TRANSLATION".
                - `options` MUST be an empty array [].
            
            2.  **LISTENING / MULTIPLE_CHOICE:** If the text is under "Part II Listening" or "Part III Reading":
                - `questionType` MUST be "LISTENING" or "MULTIPLE_CHOICE".
                - `options` MUST contain the A, B, C, D choices for that question.

            **Rules for "options" array (when not empty):**
            - Each option object must have: "id" (integer 1, 2, 3, 4), "optionIdentifier" (e.g., "A"), "optionText", and "correct" (boolean).
            - **You must infer the "correct" answer** based on the provided answer key in the text, or leave all as `false` if no key is provided.

            Here is the text to analyze:
            ---
            %s
            ---
            
            **CRITICAL REQUIREMENTS:**
            1. Ensure your output is a COMPLETE, valid JSON object. Do not truncate the output.
            2. The JSON must start with {"questions": [ and end with ]}
            3. Do not include markdown formatting like ```json or ```.
            4. If the exam paper is very long, you MUST still return ALL questions in a complete JSON array.
            5. Make sure all brackets and braces are properly closed.
            6. **VERY IMPORTANT**: All string values in JSON MUST have special characters properly escaped:
               - Double quotes (") inside string values MUST be escaped as \\"
               - Newlines MUST be escaped as \\n
               - Carriage returns MUST be escaped as \\r
               - Tabs MUST be escaped as \\t
               - Backslashes MUST be escaped as \\\\
               - Example: "questionText": "He said \\"Hello\\" to me" is correct
               - Example: "questionText": "He said "Hello" to me" is WRONG and will cause parsing errors
            
            Return ONLY the JSON object, nothing else.
            """, subject, rawText);
    }
}

