package com.ljh.smarteducation.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ljh.smarteducation.entity.ApiUsageLog;
import com.ljh.smarteducation.entity.Question;
import com.ljh.smarteducation.entity.QuestionSet; // 1. (新增) 导入
import com.ljh.smarteducation.repository.ApiUsageLogRepository;
import com.ljh.smarteducation.repository.QuestionBankRepository;
import com.ljh.smarteducation.repository.QuestionSetRepository; // 2. (新增) 导入
import com.ljh.smarteducation.service.LlmService;
import com.ljh.smarteducation.service.QuestionBankService;
import com.ljh.smarteducation.service.DocumentParserService;
import com.ljh.smarteducation.service.DocumentSegmentService;
import com.ljh.smarteducation.service.TextExtractionResult;
import com.ljh.smarteducation.service.TextExtractionService;
import com.ljh.smarteducation.util.TokenUsageHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class QuestionBankServiceImpl implements QuestionBankService {

    private final DocumentParserService documentParserService;
    private final LlmService llmService;
    private final QuestionBankRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final DocumentSegmentService documentSegmentService;

    // --- ↓↓↓ 3. (新增) 注入套题仓库 ↓↓↓ ---
    private final QuestionSetRepository questionSetRepository;
    private final ApiUsageLogRepository apiUsageLogRepository;
    private final TextExtractionService textExtractionService;

    public QuestionBankServiceImpl(DocumentParserService documentParserService, LlmService llmService,
            QuestionBankRepository questionRepository, ObjectMapper objectMapper,
            DocumentSegmentService documentSegmentService,
            QuestionSetRepository questionSetRepository,
            ApiUsageLogRepository apiUsageLogRepository,
            TextExtractionService textExtractionService) { // 4. (新增)
        this.documentParserService = documentParserService;
        this.llmService = llmService;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
        this.documentSegmentService = documentSegmentService;
        this.questionSetRepository = questionSetRepository; // 5. (新增)
        this.apiUsageLogRepository = apiUsageLogRepository;
        this.textExtractionService = textExtractionService;
    }
    // --- ↑↑↑ 3, 4, 5 修改结束 ↑↑↑ ---

    @Override
    @Transactional
    public void importQuestionsFromWord(MultipartFile file, String subject) throws IOException {
        // ⭐ 获取文件大小（KB）
        long fileSizeKb = file.getSize() / 1024;
        String fileName = file.getOriginalFilename();

        // 使用统一文本抽取服务获取原始文本（当前阶段仅支持 DOCX 简单实现）
        TextExtractionResult extractionResult = textExtractionService.extract(file);
        String rawText = extractionResult != null && extractionResult.getFullText() != null
                ? extractionResult.getFullText()
                : "";

        // ⭐ 打印文档长度，仅用于观测
        int textLength = rawText.length();
        System.out.println(">>> 文档长度: " + textLength + " 字符");
        // 之前这里根据长度 > 100000 走 legacy 的 importQuestionsWithSegmentation 分段管线，
        // 但在接入新的 LlmService 按 Part 分段与模板选择后，该管线更容易出现 0 题导致上传失败。
        // 现在统一走 LlmService 的结构化解析管线，由它内部决定是否按 Part 分段或截断。
        System.out.println(">>> 使用统一 LLM 解析管线处理文档");
        String jsonContentString = llmService.getStructuredQuestions(rawText, subject).block();

        // ⭐ 获取 Token 使用信息
        TokenUsageHolder.TokenUsage tokenUsage = TokenUsageHolder.get();

        System.out.println("================ AI RAW RESPONSE START ================");
        System.out.println(jsonContentString);
        System.out.println("================= AI RAW RESPONSE END =================");

        try {
            // --- ↓↓↓ 6. (核心修改) 创建并保存套题 ↓↓↓ ---
            QuestionSet newSet = new QuestionSet();
            newSet.setTitle(fileName); // 使用文件名作为标题
            newSet.setSubject(subject);
            QuestionSet savedSet = questionSetRepository.save(newSet); // 先保存套题，获取ID
            // --- ↑↑↑ 6. 修改结束 ↑↑↑ ---

            // 验证JSON长度和基本结构
            if (jsonContentString == null || jsonContentString.trim().isEmpty()) {
                throw new IOException("AI returned empty response. Please try again.");
            }

            System.out.println(">>> JSON Response length: " + jsonContentString.length());

            // 尝试解析JSON
            Map<String, Object> fullJsonStructure;
            try {
                fullJsonStructure = objectMapper.readValue(jsonContentString, new TypeReference<Map<String, Object>>() {
                });
            } catch (JsonProcessingException e) {
                // JSON解析失败，尝试修复常见问题
                System.err.println(">>> JSON parsing failed. Attempting to fix common issues...");
                System.err.println(">>> Error details: " + e.getMessage());

                // 第一步：移除控制字符
                String fixedJson = jsonContentString
                        .replaceAll("[\\x00-\\x08\\x0B-\\x0C\\x0E-\\x1F\\x7F]", ""); // 移除控制字符

                // 第二步：尝试修复被截断的JSON
                fixedJson = fixTruncatedJson(fixedJson);

                // 第三步：尝试修复字符串中的未转义引号
                fixedJson = fixUnescapedQuotes(fixedJson);

                try {
                    // 再次尝试解析
                    fullJsonStructure = objectMapper.readValue(fixedJson, new TypeReference<Map<String, Object>>() {
                    });
                    System.out.println(">>> JSON fixed and parsed successfully after repair attempt.");
                } catch (JsonProcessingException e2) {
                    // 如果还是失败，尝试提取部分有效的JSON
                    try {
                        fixedJson = extractValidJsonPortion(fixedJson);
                        fullJsonStructure = objectMapper.readValue(fixedJson, new TypeReference<Map<String, Object>>() {
                        });
                        System.out.println(">>> JSON partially extracted and parsed successfully.");
                    } catch (JsonProcessingException e3) {
                        // 修复失败，提供详细的错误信息
                        int errorLine = 1; // 默认值
                        int errorColumn = 1; // 默认值
                        String errorMsg = e3.getMessage();

                        // 尝试从错误消息中提取行号和列号
                        if (errorMsg != null && errorMsg.contains("line:") && errorMsg.contains("column:")) {
                            try {
                                String[] parts = errorMsg.split("line:")[1].split("column:");
                                errorLine = Integer.parseInt(parts[0].trim());
                                String colPart = parts[1].split("\\]")[0].trim();
                                errorColumn = Integer.parseInt(colPart);
                            } catch (Exception ignored) {
                            }
                        }

                        System.err.println(">>> JSON parsing failed after all repair attempts.");
                        System.err.println(">>> Original JSON Response length: " + jsonContentString.length());
                        System.err.println(">>> Extracted JSON length: " + fixedJson.length());
                        System.err.println(">>> Error at line " + errorLine + ", column " + errorColumn);
                        System.err.println(">>> Error message: " + e3.getMessage());

                        // 输出提取的 JSON 的前 500 个字符和后 500 个字符用于调试
                        if (fixedJson.length() > 1000) {
                            System.err.println(
                                    ">>> Extracted JSON preview (first 500 chars): " + fixedJson.substring(0, 500));
                            System.err.println(">>> Extracted JSON preview (last 500 chars): "
                                    + fixedJson.substring(fixedJson.length() - 500));
                        } else {
                            System.err.println(">>> Extracted JSON: " + fixedJson);
                        }

                        throw new IOException("JSON解析失败：在第" + errorLine + "行第" + errorColumn + "列附近，JSON格式不正确或可能被截断。" +
                                "这通常是因为AI返回的JSON中包含了未转义的特殊字符或JSON被截断。请尝试：" +
                                "1. 检查上传的Word文件内容是否包含特殊字符；" +
                                "2. 尝试重新上传文件；" +
                                "3. 如果问题持续，请联系技术支持。错误详情: " + e3.getMessage(), e3);
                    }
                }
            }
            Object questionsObject = fullJsonStructure.get("questions");
            List<Map<String, Object>> questionMaps = Collections.emptyList();

            if (questionsObject instanceof List) {
                try {
                    // 使用 ObjectMapper 安全转换，避免未检查的类型转换警告
                    questionMaps = objectMapper.convertValue(questionsObject,
                            new TypeReference<List<Map<String, Object>>>() {
                            });
                } catch (IllegalArgumentException e) {
                    throw new IOException("AI returned unexpected JSON structure for questions array.", e);
                }
            } else if (questionsObject != null) {
                throw new IOException("AI returned unexpected JSON structure: 'questions' is not an array.");
            }

            int questionsSaved = 0;
            // --- ↓↓↓ 7. (核心修改) 使用带索引的循环 ↓↓↓ ---
            for (int i = 0; i < questionMaps.size(); i++) {
                Map<String, Object> questionMap = questionMaps.get(i);
                // --- ↑↑↑ 7. 修改结束 ↑↑↑ ---

                // --- ↓↓↓ (新增) 标准化 questionType 字段 ↓↓↓ ---
                String questionType = (String) questionMap.get("questionType");
                // 将各种格式统一转换为标准格式（包含智能推断）
                questionType = normalizeQuestionType(questionType, questionMap);
                questionMap.put("questionType", questionType);
                    // 使用 correctOptions/answer 修正选项，并清洗 Listening 题干
            applyAnswerAndCleanQuestion(questionMap);
                // --- ↑↑↑ 标准化结束 ↑↑↑ ---

                Question questionEntity = new Question();
                questionEntity.setSubject(subject);
                questionEntity.setDifficulty((String) questionMap.getOrDefault("difficulty", "Unknown"));
                questionEntity.setKnowledgePoint((String) questionMap.getOrDefault("knowledgePoint", "Unknown"));
                questionEntity.setContent(questionMap);

                // --- ↓↓↓ 8. (核心修改) 关联套题和序号 ↓↓↓ ---
                questionEntity.setQuestionSet(savedSet); // 关联到刚创建的套题

                // 优先使用AI提取的序号，如果AI没提供，则使用 for 循环的索引
                Object seqNumObj = questionMap.get("sequenceNumber");
                if (seqNumObj instanceof Number) {
                    questionEntity.setSequenceNumber(((Number) seqNumObj).intValue());
                } else {
                    questionEntity.setSequenceNumber(i + 1); // Fallback
                }
                // --- ↑↑↑ 8. 修改结束 ↑↑↑ ---

                questionRepository.save(questionEntity);
                questionsSaved++;
            }

            if (questionsSaved == 0) {
                // (保持不变)
                throw new RuntimeException(
                        "AI processing succeeded, but no valid questions were found in the response.");
            }

            // ⭐ 保存成功的 API 使用日志
            System.out.println("✅ 题目导入成功！解析题目数：" + questionsSaved);
            saveApiUsageLog(savedSet.getId(), fileName, questionsSaved,
                    (int) fileSizeKb, tokenUsage, true, null);

        } catch (IOException e) {
            System.err.println("Error processing JSON from LLM: " + e.getMessage());
            // ⭐ 保存失败的 API 使用日志
            saveApiUsageLog(null, fileName, 0, (int) fileSizeKb,
                    tokenUsage, false, e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("Unexpected error during question import: " + e.getMessage());
            // ⭐ 保存失败的 API 使用日志
            saveApiUsageLog(null, fileName, 0, (int) fileSizeKb,
                    tokenUsage, false, e.getMessage());
            throw new RuntimeException("Unexpected error during import.", e);
        } finally {
            // ⭐ 清理 ThreadLocal，防止内存泄漏
            TokenUsageHolder.clear();
        }
    }

    // (保持不变)
    @Override
    public List<Question> getAllQuestions() {
        System.out.println("Fetching all questions from repository...");
        List<Question> questions = questionRepository.findAll();
        System.out.println("Found " + questions.size() + " questions.");
        return questions;
    }

    // (保持不变)
    @Override
    public Optional<Question> getQuestionById(Long id) {
        return questionRepository.findById(id);
    }

    // (保持不变)
    @Override
    @Transactional
    public Question updateQuestion(Long id, Question questionDetails) {
        Question existingQuestion = questionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question not found with id: " + id));

        // (省略... 保持不变)
        existingQuestion.setSubject(questionDetails.getSubject());
        existingQuestion.setDifficulty(questionDetails.getDifficulty());
        existingQuestion.setKnowledgePoint(questionDetails.getKnowledgePoint());
        existingQuestion.setContent(questionDetails.getContent());
        existingQuestion.setQuestionSet(questionDetails.getQuestionSet()); // (确保更新也保存关联)
        existingQuestion.setSequenceNumber(questionDetails.getSequenceNumber()); // (确保更新也保存序号)

        return questionRepository.save(existingQuestion);
    }

    // (保持不变)
    @Override
    public void deleteQuestion(Long id) {
        if (!questionRepository.existsById(id)) {
            throw new RuntimeException("Question not found with id: " + id);
        }
        questionRepository.deleteById(id);
    }

    // --- ↓↓↓ 9. (新增) 实现新接口 ↓↓↓ ---
    @Override
    public List<QuestionSet> getQuestionSetsBySubject(String subject) {
        return questionSetRepository.findBySubject(subject);
    }

    @Override
    public List<Question> getQuestionsBySetId(Long setId) {
        return questionRepository.findByQuestionSetIdOrderBySequenceNumberAsc(setId);
    }
    // --- ↑↑↑ 9. 新增结束 ↑↑↑ ---

    /**
     * 保存 API 使用日志
     */
    private void saveApiUsageLog(Long questionSetId, String fileName, int questionCount,
            int fileSizeKb, TokenUsageHolder.TokenUsage tokenUsage,
            boolean success, String errorMessage) {
        try {
            ApiUsageLog log = new ApiUsageLog();
            log.setQuestionSetId(questionSetId);
            log.setQuestionSetTitle(fileName);
            log.setQuestionCount(questionCount);
            log.setFileSizeKb(fileSizeKb);
            log.setSuccess(success);
            log.setErrorMessage(errorMessage);

            // 如果有 Token 使用信息，保存详细数据
            if (tokenUsage != null && tokenUsage.isValid()) {
                log.setInputTokens(tokenUsage.getInputTokens());
                log.setOutputTokens(tokenUsage.getOutputTokens());
                log.setTotalTokens(tokenUsage.getTotalTokens());
                log.setModelName(tokenUsage.getModelName());
                log.setDurationMs(tokenUsage.getDurationMs());

                // 计算成本
                double inputCost = calculateInputCost(tokenUsage.getInputTokens(), tokenUsage.getModelName());
                double outputCost = calculateOutputCost(tokenUsage.getOutputTokens(), tokenUsage.getModelName());
                log.setInputCost(inputCost);
                log.setOutputCost(outputCost);
                log.setTotalCost(inputCost + outputCost);

                // 打印平均成本信息
                if (questionCount > 0) {
                    System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    System.out.println("📊 平均成本分析");
                    System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    System.out.println("每套题成本:  ¥" + String.format("%.4f", inputCost + outputCost));
                    System.out.println("每道题成本:  ¥" + String.format("%.4f", (inputCost + outputCost) / questionCount));
                    System.out.println("平均Token:   " + String.format("%,d", tokenUsage.getTotalTokens() / questionCount)
                            + " tokens/题");
                    System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                }
            }

            apiUsageLogRepository.save(log);
            System.out.println("✅ API 使用日志已保存到数据库");

        } catch (Exception e) {
            // 日志保存失败不影响主流程
            System.err.println("⚠️ 保存 API 使用日志失败: " + e.getMessage());
        }
    }

    /**
     * 计算输入 Token 成本
     */
    private double calculateInputCost(int tokens, String model) {
        // Ollama 本地模型（免费）
        if (model != null && (model.contains("qwen2.5:") || model.contains("llama") ||
                model.contains("ollama") || model.startsWith("qwen2.5"))) {
            return 0.0;
        }

        double pricePerThousand;
        if ("qwen3-max".equals(model) || "qwen-max".equals(model)) {
            pricePerThousand = 0.006;
        } else if ("qwen-plus".equals(model)) {
            pricePerThousand = 0.004;
        } else if ("qwen-turbo".equals(model)) {
            pricePerThousand = 0.003;
        } else {
            pricePerThousand = 0.004;
        }
        return (tokens / 1000.0) * pricePerThousand;
    }

    /**
     * 计算输出 Token 成本
     */
    private double calculateOutputCost(int tokens, String model) {
        // Ollama 本地模型（免费）
        if (model != null && (model.contains("qwen2.5:") || model.contains("llama") ||
                model.contains("ollama") || model.startsWith("qwen2.5"))) {
            return 0.0;
        }

        double pricePerThousand;
        if ("qwen3-max".equals(model) || "qwen-max".equals(model)) {
            pricePerThousand = 0.024;
        } else if ("qwen-plus".equals(model)) {
            pricePerThousand = 0.012;
        } else if ("qwen-turbo".equals(model)) {
            pricePerThousand = 0.006;
        } else {
            pricePerThousand = 0.012;
        }
        return (tokens / 1000.0) * pricePerThousand;
    }

    /**
     * 修复被截断的JSON
     * 尝试补全缺失的闭合括号
     */
    private String fixTruncatedJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "{\"questions\": []}";
        }

        json = json.trim();

        // 如果JSON不包含questions键，返回原样
        if (!json.contains("\"questions\"")) {
            return json;
        }

        // 计算括号和方括号的匹配情况
        int openBraces = 0, closeBraces = 0;
        int openBrackets = 0, closeBrackets = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escapeNext) {
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == '{')
                openBraces++;
            else if (c == '}')
                closeBraces++;
            else if (c == '[')
                openBrackets++;
            else if (c == ']')
                closeBrackets++;
        }

        // 补全缺失的闭合括号
        StringBuilder fixed = new StringBuilder(json);

        // 先补全方括号
        for (int i = 0; i < openBrackets - closeBrackets; i++) {
            fixed.append(']');
        }

        // 再补全大括号
        for (int i = 0; i < openBraces - closeBraces; i++) {
            fixed.append('}');
        }

        return fixed.toString();
    }

    /**
     * 修复字符串中未转义的引号
     * 在字符串值内部，将未转义的引号转义
     */
    private String fixUnescapedQuotes(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }

        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escapeNext) {
                result.append(c);
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                result.append(c);
                escapeNext = true;
                continue;
            }

            if (c == '"') {
                if (inString) {
                    // 在字符串内部，检查下一个字符来判断是否是字符串结束
                    boolean isEndQuote = false;
                    if (i + 1 < json.length()) {
                        char nextChar = json.charAt(i + 1);
                        // 如果下一个字符是结构字符或空白，可能是字符串结束
                        if (nextChar == ':' || nextChar == ',' || nextChar == '}' ||
                                nextChar == ']' || Character.isWhitespace(nextChar)) {
                            isEndQuote = true;
                        }
                    } else {
                        // 字符串末尾，应该是结束引号
                        isEndQuote = true;
                    }

                    if (isEndQuote) {
                        inString = false;
                        result.append(c);
                    } else {
                        // 这是字符串内容中的引号，需要转义
                        result.append("\\\"");
                    }
                } else {
                    // 字符串开始
                    inString = true;
                    result.append(c);
                }
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * 提取部分有效的JSON
     * 如果JSON被截断，尝试提取到最后一个完整的题目对象
     */
    private String extractValidJsonPortion(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "{\"questions\": []}";
        }

        // 查找最后一个完整的题目对象
        // 尝试找到最后一个完整的 } 在 questions 数组中
        int questionsIdx = json.indexOf("\"questions\"");
        if (questionsIdx < 0) {
            return "{\"questions\": []}";
        }

        int arrayStart = json.indexOf('[', questionsIdx);
        if (arrayStart < 0) {
            return "{\"questions\": []}";
        }

        // 从数组开始位置，找到最后一个完整的对象
        int braceCount = 0;
        int bracketCount = 0;
        boolean inString = false;
        boolean escapeNext = false;
        int lastCompleteObjectEnd = -1;

        for (int i = arrayStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escapeNext) {
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && bracketCount == 0) {
                    // 找到一个完整的对象（不在嵌套结构中）
                    lastCompleteObjectEnd = i + 1;
                }
            } else if (c == '[') {
                bracketCount++;
            } else if (c == ']') {
                bracketCount--;
                if (bracketCount < 0) {
                    // 数组提前结束，说明被截断了
                    break;
                }
            }
        }

        // 如果找到了完整的对象，提取它
        if (lastCompleteObjectEnd > arrayStart + 1) {
            // 检查最后一个对象后面是否有逗号，如果有需要移除
            String beforeLastObject = json.substring(0, lastCompleteObjectEnd);
            // 移除末尾可能的逗号和空白
            beforeLastObject = beforeLastObject.replaceAll(",\\s*$", "");

            String extracted = beforeLastObject + "]}";
            System.out.println(">>> Extracted valid JSON portion (length: " + extracted.length() + " chars)");
            System.out.println(">>> Original JSON length: " + json.length() + " chars");
            System.out
                    .println(">>> Found " + countCompleteObjects(json.substring(arrayStart + 1, lastCompleteObjectEnd))
                            + " complete question objects");
            return extracted;
        }

        // 如果无法提取，尝试更宽松的策略：找到任何看起来像完整对象的结构
        // 从后往前找最后一个完整的 }
        for (int i = json.length() - 1; i > arrayStart; i--) {
            if (json.charAt(i) == '}') {
                // 检查这个 } 是否是对象的结束
                int testBraceCount = 0;
                boolean testInString = false;
                boolean testEscapeNext = false;

                for (int j = arrayStart + 1; j <= i; j++) {
                    char testC = json.charAt(j);
                    if (testEscapeNext) {
                        testEscapeNext = false;
                        continue;
                    }
                    if (testC == '\\') {
                        testEscapeNext = true;
                        continue;
                    }
                    if (testC == '"') {
                        testInString = !testInString;
                        continue;
                    }
                    if (testInString) {
                        continue;
                    }
                    if (testC == '{') {
                        testBraceCount++;
                    } else if (testC == '}') {
                        testBraceCount--;
                    }
                }

                if (testBraceCount == 0) {
                    // 找到了一个平衡的对象
                    String fallbackExtracted = json.substring(0, i + 1) + "]}";
                    System.out.println(
                            ">>> Using fallback extraction (length: " + fallbackExtracted.length() + " chars)");
                    return fallbackExtracted;
                }
            }
        }

        // 如果完全无法提取，返回空数组
        System.out.println(">>> Could not extract valid JSON portion, returning empty array");
        return "{\"questions\": []}";
    }

    /**
     * 计算字符串中完整对象的数量（简单估算）
     */
    private int countCompleteObjects(String json) {
        int count = 0;
        int braceCount = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escapeNext) {
                escapeNext = false;
                continue;
            }
            if (c == '\\') {
                escapeNext = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 标准化题型字段，将AI返回的各种格式统一转换为系统标准格式
     * 如果AI未识别题型，则基于题目内容智能判断
     * 
     * @param questionType AI返回的题型字符串
     * @param questionMap  题目完整数据（用于智能判断）
     * @return 标准化后的题型
     */
    private String normalizeQuestionType(String questionType, Map<String, Object> questionMap) {
        if (questionType == null || questionType.trim().isEmpty()) {
            // AI未识别题型，尝试基于内容智能判断
            return inferQuestionType(questionMap);
        }

        // 转换为大写并移除下划线、空格等
        String normalized = questionType.toUpperCase()
                .replace("_QUESTION", "")
                .replace("-", "_")
                .replace(" ", "_");

        // 映射各种可能的格式到标准格式
        switch (normalized) {
            case "MULTIPLE_CHOICE":
            case "MULTIPLECHOICE":
            case "MULTIPLE_CHOICE_QUESTION":
            case "CHOICE":
            case "MCQ":
                return "MULTIPLE_CHOICE";

            case "LISTENING":
            case "LISTENING_COMPREHENSION":
            case "LISTENING_QUESTION":
                return "LISTENING";

            case "WRITING":
            case "COMPOSITION":
            case "ESSAY":
            case "WRITING_QUESTION":
                return "WRITING";

            case "TRANSLATION":
            case "TRANSLATE":
            case "TRANSLATION_QUESTION":
                return "TRANSLATION";

            case "FILL_IN_THE_BLANK":
            case "FILL_IN_BLANK":
            case "FILLINTHEBLANK":
            case "CLOZE":
            case "BLANK":
            case "FILL_BLANK":
                return "FILL_IN_THE_BLANK";

            case "GRAMMAR":
            case "GRAMMAR_VOCABULARY":
                return "GRAMMAR";

            case "READING":
            case "READING_COMPREHENSION":
                return "READING";

            default:
                // 如果无法识别，尝试基于内容推断
                System.err.println(
                        ">>> Warning: Unknown question type '" + questionType + "', attempting to infer from content");
                return inferQuestionType(questionMap);
        }
    }

    /**
     * 基于题目内容智能推断题型
     * 
     * @param questionMap 题目数据
     * @return 推断的题型
     */
    private String inferQuestionType(Map<String, Object> questionMap) {
        String questionText = (String) questionMap.getOrDefault("questionText", "");
        Object optionsObj = questionMap.get("options");

        // 1. 检查是否有选项
        boolean hasOptions = false;
        if (optionsObj instanceof List) {
            List<?> options = (List<?>) optionsObj;
            hasOptions = !options.isEmpty();
        }

        // 2. 基于关键词判断
        String lowerText = questionText.toLowerCase();

        // 听力题
        if (lowerText.contains("listen") || lowerText.contains("听力") ||
                lowerText.contains("conversation") || lowerText.contains("对话")) {
            return "LISTENING";
        }

        // 翻译题
        if (lowerText.contains("translate") || lowerText.contains("翻译") ||
                lowerText.contains("英译汉") || lowerText.contains("汉译英")) {
            return "TRANSLATION";
        }

        // 写作题
        if (lowerText.contains("write") || lowerText.contains("essay") ||
                lowerText.contains("composition") || lowerText.contains("写作") ||
                lowerText.contains("作文") || lowerText.length() > 500) {
            return "WRITING";
        }

        // 填空题
        if (lowerText.contains("___") || lowerText.contains("blank") ||
                lowerText.contains("填空") || lowerText.contains("complete")) {
            return "FILL_IN_THE_BLANK";
        }

        // 3. 基于选项判断
        if (hasOptions) {
            // 检查选项格式
            List<?> options = (List<?>) optionsObj;
            boolean hasABCD = false;
            for (Object opt : options) {
                if (opt instanceof Map) {
                    Map<?, ?> optMap = (Map<?, ?>) opt;
                    String identifier = String.valueOf(optMap.get("optionIdentifier"));
                    if (identifier != null && identifier.matches("[A-D]")) {
                        hasABCD = true;
                        break;
                    }
                }
            }
            if (hasABCD) {
                return "MULTIPLE_CHOICE";
            }
        }

        // 4. 默认返回选择题
        System.out.println(">>> Unable to infer question type, defaulting to MULTIPLE_CHOICE");
        return "MULTIPLE_CHOICE";
    }
    /**
 * 使用 LLM 返回的 correctOptions/answer 更新 options[].correct，
 * 并对 Listening 题的 questionText 做清洗（只保留题号）。
 */
@SuppressWarnings("unchecked")
private void applyAnswerAndCleanQuestion(Map<String, Object> questionMap) {
    if (questionMap == null) return;

    String type = (String) questionMap.get("questionType");
    Object optionsObj = questionMap.get("options");

    // 1) 清洗 Listening 题干：去掉里面重复的 A./B./C./D. 选项文本，只保留题号
    if ("LISTENING".equalsIgnoreCase(type)) {
        Object qtObj = questionMap.get("questionText");
        if (qtObj instanceof String) {
            String qt = ((String) qtObj).trim();
            String cleaned = qt;
            // 匹配形如 "Q1." 或 "1."
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(Q?\\d+\\.)")
                    .matcher(qt);
            if (m.find()) {
                cleaned = m.group(1);
            }
            questionMap.put("questionText", cleaned);
        }
    }

    // 2) 根据 correctOptions/answerKey 设置 options[].correct
    if (optionsObj instanceof java.util.List) {
        java.util.List<java.util.Map<String, Object>> options;
        try {
            options = (java.util.List<java.util.Map<String, Object>>) optionsObj;
        } catch (ClassCastException e) {
            return;
        }

        // 默认全部 false
        for (java.util.Map<String, Object> opt : options) {
            if (opt != null) {
                opt.put("correct", Boolean.FALSE);
            }
        }

        java.util.Set<String> correctIds = new java.util.HashSet<>();

        // 优先用 correctOptions: ["B"]
        Object correctOptionsObj = questionMap.get("correctOptions");
        if (correctOptionsObj instanceof java.util.List) {
            for (Object o : (java.util.List<?>) correctOptionsObj) {
                if (o != null) {
                    correctIds.add(String.valueOf(o).trim());
                }
            }
        }

        // 退而求其次，用 answerKey: "C"
        Object answerKeyObj = questionMap.get("answerKey");
        if (answerKeyObj instanceof String && correctIds.isEmpty()) {
            String s = ((String) answerKeyObj).trim();
            if (!s.isEmpty()) {
                correctIds.add(s);
            }
        }

        if (!correctIds.isEmpty()) {
            for (java.util.Map<String, Object> opt : options) {
                if (opt == null) continue;
                Object idObj = opt.get("optionIdentifier");
                if (idObj == null) continue;
                String id = String.valueOf(idObj).trim();
                if (!id.isEmpty() && correctIds.contains(id)) {
                    opt.put("correct", Boolean.TRUE);
                }
            }
        }
    }
}

    /**
     * 分段处理大文档
     * 
     * @param file    上传的文件
     * @param subject 科目
     * @param rawText 已解析的文档文本
     */
    private void importQuestionsWithSegmentation(MultipartFile file, String subject, String rawText)
            throws IOException {
        String fileName = file.getOriginalFilename();
        long fileSizeKb = file.getSize() / 1024;

        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("📄 开始分段处理大文档");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // 1. 创建套题
        QuestionSet newSet = new QuestionSet();
        newSet.setTitle(fileName);
        newSet.setSubject(subject);
        QuestionSet savedSet = questionSetRepository.save(newSet);
        System.out.println(">>> 套题已创建，ID: " + savedSet.getId());

        // 2. 智能分段
        List<String> segments = documentSegmentService.smartSegmentByQuestions(rawText);
        System.out.println(">>> 文档已分为 " + segments.size() + " 段");

        // 3. 逐段处理
        int totalQuestionsSaved = 0;
        int globalSequenceNumber = 1; // 全局题号

        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            String segment = segments.get(segmentIndex);
            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("📝 处理第 " + (segmentIndex + 1) + "/" + segments.size() + " 段");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println(">>> 段落长度: " + segment.length() + " 字符");

            try {
                // 调用AI处理这一段
                String jsonContentString = llmService.getStructuredQuestions(segment, subject).block();

                if (jsonContentString == null || jsonContentString.trim().isEmpty()) {
                    System.err.println(">>> 警告：第 " + (segmentIndex + 1) + " 段AI返回空结果，跳过");
                    continue;
                }

                // 解析JSON
                Map<String, Object> fullJsonStructure = objectMapper.readValue(jsonContentString,
                        new TypeReference<Map<String, Object>>() {
                        });
                Object questionsObject = fullJsonStructure.get("questions");

                if (!(questionsObject instanceof List)) {
                    System.err.println(">>> 警告：第 " + (segmentIndex + 1) + " 段返回的JSON格式不正确，跳过");
                    continue;
                }

                List<Map<String, Object>> questionMaps = objectMapper.convertValue(questionsObject,
                        new TypeReference<List<Map<String, Object>>>() {
                        });
                System.out.println(">>> 第 " + (segmentIndex + 1) + " 段解析到 " + questionMaps.size() + " 道题");

                // 保存题目
                for (Map<String, Object> questionMap : questionMaps) {
                    // 标准化题型
                    String questionType = (String) questionMap.get("questionType");
                    questionType = normalizeQuestionType(questionType, questionMap);
                    questionMap.put("questionType", questionType);

                    // 创建题目实体
                    Question questionEntity = new Question();
                    questionEntity.setSubject(subject);
                    questionEntity.setDifficulty((String) questionMap.getOrDefault("difficulty", "Unknown"));
                    questionEntity.setKnowledgePoint((String) questionMap.getOrDefault("knowledgePoint", "Unknown"));
                    questionEntity.setContent(questionMap);
                    questionEntity.setQuestionSet(savedSet);
                    questionEntity.setSequenceNumber(globalSequenceNumber++); // 使用全局序号

                    questionRepository.save(questionEntity);
                    totalQuestionsSaved++;
                }

                System.out.println(">>> 第 " + (segmentIndex + 1) + " 段处理完成，已保存 " + questionMaps.size() + " 道题");

            } catch (Exception e) {
                System.err.println(">>> 错误：第 " + (segmentIndex + 1) + " 段处理失败: " + e.getMessage());
                e.printStackTrace();
                // 继续处理下一段，不中断整个流程
            }
        }

        // 4. 保存API使用记录
        TokenUsageHolder.TokenUsage tokenUsage = TokenUsageHolder.get();
        if (tokenUsage != null) {
            ApiUsageLog log = new ApiUsageLog();
            log.setQuestionSetId(savedSet.getId());
            log.setQuestionSetTitle(fileName);
            log.setFileSizeKb((int) fileSizeKb);
            log.setInputTokens(tokenUsage.getInputTokens());
            log.setOutputTokens(tokenUsage.getOutputTokens());
            log.setTotalTokens(tokenUsage.getTotalTokens());
            log.setModelName(tokenUsage.getModelName());
            log.setDurationMs(tokenUsage.getDurationMs());
            log.setQuestionCount(totalQuestionsSaved);
            log.setSuccess(true);
            apiUsageLogRepository.save(log);
        }

        System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("✅ 分段处理完成");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println(">>> 总共处理 " + segments.size() + " 段");
        System.out.println(">>> 总共保存 " + totalQuestionsSaved + " 道题");

        if (totalQuestionsSaved == 0) {
            throw new RuntimeException("分段处理完成，但未能提取到任何题目。请检查文档格式。");
        }
    }
}
