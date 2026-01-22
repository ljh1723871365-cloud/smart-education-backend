package com.ljh.smarteducation.service.impl;

import com.ljh.smarteducation.service.ExamTemplate;
import com.ljh.smarteducation.service.ExamStructureRule;
import com.ljh.smarteducation.service.ExamStructureRulesRegistry;
import com.ljh.smarteducation.service.LlmService;
import com.ljh.smarteducation.service.QuestionFormatDetector;
import com.ljh.smarteducation.service.QuestionStructureExtractor;
import com.ljh.smarteducation.service.AiQuestionValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.LinkedHashMap;

@Service
public class LlmServiceImpl implements LlmService {

    // 注入三层混合式识别架构服务
    @Autowired
    private QuestionFormatDetector formatDetector;
    
    @Autowired
    private QuestionStructureExtractor structureExtractor;
    
    @Autowired
    private AiQuestionValidator aiValidator;


    // SiliconFlow (Qwen) API Configuration
    @Value("${siliconflow.api.base-url:https://api.siliconflow.cn/v1/chat/completions}")
    private String siliconflowBaseUrl;

    @Value("${siliconflow.api.key:}")
    private String siliconflowApiKey;

    @Value("${siliconflow.api.model:Qwen/Qwen2.5-72B-Instruct}")
    private String siliconflowModelName;

    @Override
    public Mono<String> getStructuredQuestions(String rawText, String subject) {
        // 预处理：去除重复的选项行
        String preprocessedText = removeRepeatedOptions(rawText);

        // Phase C-2: 自动根据试卷文本选择模板（高考 / 四级 / 六级 / Generic）
        java.util.List<ExamTemplate> candidates = java.util.Arrays.asList(
                ExamTemplate.GAOKAO_ENGLISH_A,
                ExamTemplate.GAOKAO_ENGLISH_A_GROUPED,
                ExamTemplate.Template_CET4_Generic,
                ExamTemplate.Template_CET6_Generic,
                ExamTemplate.GENERIC
        );
        ExamTemplate template = chooseTemplateForText(preprocessedText, candidates, 0.7);
        
        // 优先：只要检测到试卷分段标题（I/II/III/IV 等），就启用按 Part 分段处理
        int textLength = preprocessedText.length();
        System.out.println(">>> 文档长度: " + textLength + " 字符");
        try {
            java.util.Map<String, String> probe = segmentByPart(preprocessedText);
            if (probe != null && !probe.isEmpty()) {
                System.out.println(">>> 检测到试卷分段标记，启用按 Part 分段处理模式");
                return processMultipleParts(preprocessedText, subject, template);
            }
        } catch (Exception ignore) {
            // 忽略分段探测异常，继续采用单次处理兜底
        }
        
        // 兜底：对于非常大的文本也启用分段模式
        if (textLength > 40000) {
            System.out.println(">>> 文档较大（兜底），启用按 Part 分段处理模式");
            return processMultipleParts(preprocessedText, subject, template);
        }
        
        // 正常处理流程（小文档）
        System.out.println(">>> 未检测到分段标记，使用单次处理模式（兜底）");
        
        // 限制输入文本长度，避免请求过大导致连接中断
        int maxTextLength = 40000; // 限制为40000字符
        final String finalRawText;
        if (preprocessedText != null && preprocessedText.length() > maxTextLength) {
            System.out.println(">>> Warning: Text too long (" + preprocessedText.length() + " chars), truncating to " + maxTextLength + " chars");
            finalRawText = preprocessedText.substring(0, maxTextLength) + "\n\n[注意：文本已截断，仅处理前" + maxTextLength + "个字符]";
        } else {
            finalRawText = preprocessedText;
        }
        
        // 底层已从 Gemini 切换为 SiliconFlow（Qwen）
        return Mono.fromCallable(() -> callGeminiSyncWithRetry(finalRawText, subject, 3))
                .onErrorResume(throwable -> {
                    String errorMessage = (throwable.getMessage() != null) ? throwable.getMessage() : "Unknown AI service error.";
                    System.err.println(">>> Error calling LLM API after retries: " + errorMessage);
                    return Mono.error(new RuntimeException("LLM service call failed: " + errorMessage, throwable));
                });
    }

    /**
     * 对 LLM 原始返回进行强力净化，尽最大可能产出可被 ObjectMapper 解析的 JSON。
     */
    private String sanitizeJsonResponse(String raw) {
        if (raw == null) return "{\"questions\":[]}";
        String s = raw.trim();

        // 1) 去除 Markdown 代码围栏 ```json ... ``` 或 ```
        s = s.replaceAll("(?s)```json\\s*", "");
        s = s.replaceAll("(?s)```\n?", "");

        // 2) 尝试截取最外层 JSON 对象：第一个{ 到 最后一个}
        int firstBrace = s.indexOf('{');
        int lastBrace = s.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            s = s.substring(firstBrace, lastBrace + 1);
        }

        // 3) 若仍不包含 questions 字段，尝试从 questions 数组恢复
        if (!s.contains("\"questions\"")) {
            int qStartKey = s.indexOf("\"questions\"");
            if (qStartKey < 0) {
                // 直接返回空结构
                return "{\"questions\":[]}";
            }
        }

        // 4) 去除可能混入的 markdown 横线/注释行
        s = s.replaceAll("(?m)^---+$", "");
        s = s.replaceAll("(?m)^=+.*$", "");

        // 5) 常见尾随逗号修复：在 ] 或 } 前的多余逗号
        s = s.replaceAll(",\\s*([}\"])", "$1");

        // 6) 修复对象字段间缺逗号：如  "sequenceNumber": 21"questionText": "..."
        // 在值后紧跟下一个键名引号时补逗号
        s = s.replaceAll("(\\d)(\\s*\")", "$1,$2");
        s = s.replaceAll("(true|false|null)(\\s*\")", "$1,$2");
        // 值为字符串的情况："...""nextKey"
        s = s.replaceAll("(\"[^\"]*\")(\\s*\")", "$1,$2");
        // 修复对象之间缺逗号： '}{' -> '},{'
        s = s.replaceAll("\\}(\\s*)\\{", "},$1{");

        // 7) 针对常见字段缺逗号的专门修复：sequenceNumber/questionText/questionType/difficulty/knowledgePoint/options
        s = s.replaceAll("(\"sequenceNumber\"\\s*:\\s*[-]?\\d+)\\s*(\")", "$1,$2");
        s = s.replaceAll("(\"questionText\"\\s*:\\s*\"[\\s\\S]*?\")\\s*(\")", "$1,$2");
        s = s.replaceAll("(\"questionType\"\\s*:\\s*\"[A-Z_]+\")\\s*(\")", "$1,$2");
        s = s.replaceAll("(\"difficulty\"\\s*:\\s*\"[A-Za-z]+\")\\s*(\")", "$1,$2");
        s = s.replaceAll("(\"knowledgePoint\"\\s*:\\s*\"[\\s\\S]*?\")\\s*(\")", "$1,$2");
        s = s.replaceAll("(\"options\"\\s*:\\s*\\[[\\s\\S]*?\\])\\s*(\")", "$1,$2");

        // 8) 括号平衡简单修复：若以 } 结尾缺少 ]
        int lastBracket = s.lastIndexOf(']');
        lastBrace = s.lastIndexOf('}');
        if (lastBrace > lastBracket) {
            int qIdx = s.indexOf("\"questions\"");
            if (qIdx >= 0) {
                int arrOpen = s.indexOf('[', qIdx);
                if (arrOpen >= 0) {
                    long openCount = s.substring(arrOpen).chars().filter(ch -> ch == '[').count();
                    long closeCount = s.substring(arrOpen).chars().filter(ch -> ch == ']').count();
                    if (openCount > closeCount) {
                        // 在最后一个 } 之前补一个 ]
                        int lastCloseBrace = s.lastIndexOf('}');
                        if (lastCloseBrace > 0) {
                            s = s.substring(0, lastCloseBrace) + "]" + s.substring(lastCloseBrace);
                        }
                    }
                }
            }
        }

        // 9) 若依然不是以 } 结尾，强制闭合
        if (!s.endsWith("}")) {
            s = s + "}";
        }

        // 10) 若仍无 questions 数组，尝试从多个题目对象重建
        if (!s.contains("\"questions\"")) {
            // 粗略提取以 sequenceNumber 开头的对象块
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\{\\s*\\\"sequenceNumber\\\"[\\s\\S]*?\\}(?=\\s*,|\\s*\\]|\\s*\\})");
            java.util.regex.Matcher m = p.matcher(s);
            java.util.List<String> objs = new java.util.ArrayList<>();
            while (m.find()) {
                objs.add(m.group());
            }
            if (!objs.isEmpty()) {
                String joined = String.join(",", objs);
                return "{\"questions\":[" + joined + "]}";
            }
        }

        // 11) 最终兜底：若仍找不到 questions 数组，返回空结构
        if (!s.contains("\"questions\"")) {
            return "{\"questions\":[]}";
        }

        return s;
    }

    /**
     * 基于分段对题目进行裁剪与规范化，避免LLM越界输出破坏整体计数。
     */
    @SuppressWarnings("unchecked")
    private java.util.List<java.util.Map<String, Object>> filterQuestionsByPart(
            String partName,
            java.util.List<java.util.Map<String, Object>> questions) {
        if (questions == null) return java.util.Collections.emptyList();

        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();

        // 工具：规范题型、清空选项
        java.util.function.Consumer<java.util.Map<String, Object>> enforceWriting = q -> {
            q.put("questionType", "WRITING");
            q.put("options", new java.util.ArrayList<>());
        };
        java.util.function.Consumer<java.util.Map<String, Object>> enforceTranslation = q -> {
            q.put("questionType", "TRANSLATION");
            q.put("options", new java.util.ArrayList<>());
        };

        if ("Writing_Summary".equals(partName)) {
            // 只保留1题，强制WRITING
            if (!questions.isEmpty()) {
                java.util.Map<String, Object> q = new java.util.LinkedHashMap<>(questions.get(0));
                enforceWriting.accept(q);
                result.add(q);
            }
            return result;
        }

        if ("Writing_Translation".equals(partName)) {
            // 只保留前3题，强制TRANSLATION
            for (int i = 0; i < questions.size() && i < 3; i++) {
                java.util.Map<String, Object> q = new java.util.LinkedHashMap<>(questions.get(i));
                enforceTranslation.accept(q);
                result.add(q);
            }
            return result;
        }

        if ("Writing_Guided".equals(partName) || "Writing".equals(partName)) {
            // 只保留1题，强制WRITING
            if (!questions.isEmpty()) {
                java.util.Map<String, Object> q = new java.util.LinkedHashMap<>(questions.get(0));
                enforceWriting.accept(q);
                result.add(q);
            }
            return result;
        }

        // 其他分段做轻量防护：去空对象
        for (java.util.Map<String, Object> q : questions) {
            if (q != null && !q.isEmpty()) {
                result.add(q);
            }
        }
        return result;
    }
    
    /**
     * 按 Part 分段处理大文档
     */
    private Mono<String> processMultipleParts(String rawText, String subject, ExamTemplate template) {
        return Mono.fromCallable(() -> {
            java.util.Map<String, String> parts = segmentByPart(rawText);
            java.util.List<java.util.Map<String, Object>> allQuestions = new java.util.ArrayList<>();
            java.util.Map<String, SectionMeta> sectionMetaByPart = new java.util.HashMap<>();
            int questionCounter = 1;
            boolean writingGuidedProduced = false;
            String writingGuidedRawText = parts.get("Writing_Guided");
            if (writingGuidedRawText == null || writingGuidedRawText.isBlank()) {
                writingGuidedRawText = parts.get("Writing");
            }

            java.util.List<ListeningGroupRange> listeningGroups = new java.util.ArrayList<>();

            // 按顺序处理每个 Part
            for (java.util.Map.Entry<String, String> entry : parts.entrySet()) {
                String partName = entry.getKey();
                String partText = entry.getValue();
                SectionMeta sectionMeta = extractSectionMeta(partText);
                sectionMetaByPart.put(partName, sectionMeta);

                System.out.println(">>> 处理 Part: " + partName);

                try {
                    String effectiveText = addPartConstraints(partName, partText);
                    String jsonResult = callGeminiSyncWithRetry(effectiveText, subject, 3);

                    // 解析 JSON 并提取题目
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> jsonMap = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(sanitizeJsonResponse(jsonResult), java.util.Map.class);

                    @SuppressWarnings("unchecked")
                    java.util.List<java.util.Map<String, Object>> questions =
                            (java.util.List<java.util.Map<String, Object>>) jsonMap.get("questions");

                    if (questions != null) {
                        // 基于分段对题目进行裁剪与规范化，防止越界或模型跑题
                        questions = filterQuestionsByPart(partName, questions);

                        if (("Writing_Guided".equals(partName) || "Writing".equals(partName))
                                && !questions.isEmpty()) {
                            writingGuidedProduced = true;
                        }

                        // 重新编号题目，确保连续
                        for (java.util.Map<String, Object> question : questions) {
                            if (sectionMeta != null) {
                                if (sectionMeta.heading != null && !sectionMeta.heading.isBlank()) {
                                    question.put("sectionHeading", sectionMeta.heading);
                                }
                                if (sectionMeta.directions != null && !sectionMeta.directions.isBlank()) {
                                    question.put("sectionDirections", sectionMeta.directions);
                                }
                            }
                            question.put("partName", partName);
                            int currentSeq = questionCounter++;
                            question.put("sequenceNumber", currentSeq);

                            if (template == ExamTemplate.GAOKAO_ENGLISH_A_GROUPED
                                    && ("Listening_A".equals(partName)
                                        || "Listening_B".equals(partName)
                                        || "Listening_C".equals(partName)
                                        || "Listening".equals(partName))) {
                                for (ListeningGroupRange grp : listeningGroups) {
                                    if (currentSeq >= grp.start && currentSeq <= grp.end) {
                                        question.put("groupId", grp.groupId);
                                        question.put("groupType", "LISTENING_SHARED_MATERIAL");
                                        break;
                                    }
                                }
                            }

                            allQuestions.add(question);
                        }
                        System.out.println(">>> " + partName + " 处理完成，提取 " + questions.size() + " 道题目");
                    } else {
                        System.err.println(">>> 警告: " + partName + " 未返回 'questions' 字段，跳过该段");
                    }
                } catch (Exception e) {
                    System.err.println(">>> 处理 " + partName + " 时出错（不中断流程）: " + e.getMessage());
                    // 单段失败不致中断，继续处理下一段
                }
            }

            // 若 Guided Writing 未成功解析，使用原文段落兜底构造第75题
            if (!writingGuidedProduced && writingGuidedRawText != null && !writingGuidedRawText.isBlank()) {
                java.util.Map<String, Object> fallback = buildFallbackGuidedWritingQuestion(
                        writingGuidedRawText,
                        questionCounter++
                );
                if (fallback != null) {
                    allQuestions.add(fallback);
                    System.out.println(">>> Fallback: 手动构造 Guided Writing 题目，作为第 " + fallback.get("sequenceNumber") + " 题");
                }
            }

            // 合并所有题目为最终 JSON
            java.util.Map<String, Object> finalResult = new java.util.HashMap<>();
            finalResult.put("questions", allQuestions);
            java.util.List<java.util.Map<String, Object>> sectionSummaries = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, SectionMeta> entry : sectionMetaByPart.entrySet()) {
                SectionMeta meta = entry.getValue();
                if (meta == null) continue;
                if ((meta.heading == null || meta.heading.isBlank())
                        && (meta.directions == null || meta.directions.isBlank())) {
                    continue;
                }
                java.util.Map<String, Object> section = new java.util.LinkedHashMap<>();
                section.put("partName", entry.getKey());
                section.put("sectionHeading", meta.heading);
                section.put("sectionDirections", meta.directions);
                sectionSummaries.add(section);
            }
            if (!sectionSummaries.isEmpty()) {
                finalResult.put("sections", sectionSummaries);
            }

            // Phase A-4: structural validation based on template rules (no mutation of questions list)
            java.util.Map<String, Object> structureResult = validateStructure(allQuestions, template);

            // For GAOKAO_ENGLISH_A_GROUPED, add Listening group-level checks on top of normal structure validation
            if (template == ExamTemplate.GAOKAO_ENGLISH_A_GROUPED) {
                applyListeningGroupChecks(structureResult, listeningGroups, allQuestions);
            }

            if (structureResult != null) {
                Object status = structureResult.get("structureStatus");
                Object issues = structureResult.get("structureIssues");
                if (status != null) {
                    finalResult.put("structureStatus", status);
                }
                if (issues != null) {
                    finalResult.put("structureIssues", issues);
                }
            }

            System.out.println(">>> 所有 Part 处理完成，总共 " + allQuestions.size() + " 道题目");

                        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(finalResult);
        }).onErrorResume(throwable -> {
            String errorMessage = (throwable.getMessage() != null) ? throwable.getMessage() : "Unknown error";
            System.err.println("Error in multi-part processing: " + errorMessage);
            return Mono.error(new RuntimeException("Multi-part processing failed: " + errorMessage));
        });
    }

    private String callGeminiSyncWithRetry(String rawText, String subject, int maxRetries) throws Exception {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < maxRetries) {
            try {
                System.out.println(">>> Attempting LLM API call (attempt " + (retryCount + 1) + "/" + maxRetries + ")");
                return callGeminiSync(rawText, subject);
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                if (retryCount < maxRetries) {
                    System.err.println(">>> LLM API call failed, retrying in " + (retryCount * 2) + " seconds...");
                    try {
                        Thread.sleep(retryCount * 2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                } else {
                    throw new RuntimeException("Failed after " + maxRetries + " retries", e);
                }
            }
        }
        throw new RuntimeException("Failed after " + maxRetries + " retries", lastException);
    }

    private String callGeminiSync(String rawText, String subject) throws Exception {
        System.out.println(">>> 开始混合式识别架构处理流程...");
        
        // 第一层：格式检测引擎
        System.out.println(">>> [第一层] 格式检测引擎 - 分析题目格式...");
        QuestionFormatDetector.FormatDetectionResult formatResult = formatDetector.detectFormat(rawText);
        System.out.println(">>> 格式检测结果 - 题型: " + formatResult.getQuestionType() + 
                         ", 置信度: " + String.format("%.2f", formatResult.getConfidence()));
        
        // 第二层：结构化提取器
        System.out.println(">>> [第二层] 结构化提取器 - 提取题目结构...");
        QuestionStructureExtractor.ExtractionResult extractionResult = 
            structureExtractor.extractStructure(rawText, formatResult);
        System.out.println(">>> 结构提取结果 - 置信度: " + String.format("%.2f", extractionResult.getConfidence()) +
                         ", 字段完整性: " + extractionResult.getFieldCompleteness() + "%");
        
        // 第三层：AI辅助验证与优化
        double confidenceThreshold = 0.7;
        if (extractionResult.getConfidence() < confidenceThreshold) {
            System.out.println(">>> [第三层] AI辅助优化 - 置信度低于阈值(" + confidenceThreshold + 
                             "), 调用AI优化...");
            
            // 使用AI进行优化
            QuestionStructureExtractor.ExtractionResult optimizedResult = 
                aiValidator.optimizeWithAi(rawText, extractionResult);
            
            System.out.println(">>> AI优化完成 - 新置信度: " + String.format("%.2f", optimizedResult.getConfidence()));
            
            // 使用优化后的结果
            extractionResult = optimizedResult;
        } else {
            System.out.println(">>> [决策] 规则引擎置信度达标(" + String.format("%.2f", extractionResult.getConfidence()) + 
                             " >= " + confidenceThreshold + "), 跳过AI调用");
        }
        
        // 将提取结果转换为JSON格式返回
        System.out.println(">>> 混合式识别流程完成，生成最终JSON结果...");
        return convertExtractionResultToJson(extractionResult);
    }
    
    /**
     * 将提取结果转换为JSON格式
     * 此方法将结构化提取的结果转换为LlmService期望的JSON格式
     */
    private String convertExtractionResultToJson(QuestionStructureExtractor.ExtractionResult extractionResult) 
            throws Exception {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        java.util.List<java.util.Map<String, Object>> questions = new java.util.ArrayList<>();
        
        // 从提取结果中获取题目数据
        java.util.Map<String, Object> extractedData = extractionResult.getExtractedData();
        
        if (extractedData != null && !extractedData.isEmpty()) {
            // 创建题目对象
            java.util.Map<String, Object> question = new java.util.LinkedHashMap<>();
            
            // 设置基本字段
            Object seqNum = extractedData.get("sequenceNumber");
            if (seqNum != null) {
                question.put("sequenceNumber", seqNum);
            }
            
            Object questionText = extractedData.get("questionText");
            if (questionText != null) {
                question.put("questionText", questionText);
            }
            
            Object questionType = extractedData.get("questionType");
            if (questionType != null) {
                question.put("questionType", questionType);
            }
            
            Object difficulty = extractedData.get("difficulty");
            if (difficulty != null) {
                question.put("difficulty", difficulty);
            }
            
            Object knowledgePoint = extractedData.get("knowledgePoint");
            if (knowledgePoint != null) {
                question.put("knowledgePoint", knowledgePoint);
            }
            
            Object options = extractedData.get("options");
            if (options != null) {
                question.put("options", options);
            } else {
                question.put("options", new java.util.ArrayList<>());
            }
            
            Object correctOptions = extractedData.get("correctOptions");
            if (correctOptions != null) {
                question.put("correctOptions", correctOptions);
            }
            
            Object answer = extractedData.get("answer");
            if (answer != null) {
                question.put("answer", answer);
            }
            
            // 添加置信度信息（可选，用于调试）
            question.put("_confidence", extractionResult.getConfidence());
            question.put("_fieldCompleteness", extractionResult.getFieldCompleteness());
            
            questions.add(question);
        }
        
        result.put("questions", questions);
        
        // 转换为JSON字符串
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.writeValueAsString(result);
    }

    /**
     * Phase A-4: Perform structural validation based on the configured rules for the
     * given template. This method only reports status and issues; it does NOT mutate
     * the questions list.
     */
    private java.util.Map<String, Object> validateStructure(
            java.util.List<java.util.Map<String, Object>> questions,
            ExamTemplate template) {

        java.util.Map<String, Object> result = new java.util.HashMap<>();

        // GENERIC 模板：实现一套简单、与具体考试无关的通用健康检查
        if (template == ExamTemplate.GENERIC) {
            java.util.List<java.util.Map<String, Object>> issues = new java.util.ArrayList<>();
            String status = "OK";

            // 1) 基础结构合法性：questions 数组存在
            if (questions == null) {
                status = "ERROR";
                java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                issue.put("code", "GENERIC_MISSING_QUESTIONS_ARRAY");
                issue.put("message", "Missing questions array in GENERIC template result.");
                issue.put("ruleId", "GENERIC_BASIC_STRUCTURE");
                issues.add(issue);
            } else {
                // 2) 遍历每道题，检查必要字段 & 选项数量
                int idx = 0;
                for (java.util.Map<String, Object> q : questions) {
                    idx++;
                    if (q == null) {
                        status = "ERROR";
                        java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                        issue.put("code", "GENERIC_NULL_QUESTION");
                        issue.put("message", "Question #" + idx + " is null.");
                        issue.put("ruleId", "GENERIC_BASIC_STRUCTURE");
                        issues.add(issue);
                        continue;
                    }

                    Object qt = q.get("questionText");
                    Object qtype = q.get("questionType");
                    if (!(qt instanceof String) || ((String) qt).trim().isEmpty()) {
                        status = "ERROR";
                        java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                        issue.put("code", "GENERIC_MISSING_QUESTION_TEXT");
                        issue.put("message", "Question #" + idx + " is missing questionText.");
                        issue.put("ruleId", "GENERIC_BASIC_STRUCTURE");
                        issues.add(issue);
                    }
                    if (!(qtype instanceof String) || ((String) qtype).trim().isEmpty()) {
                        status = "ERROR";
                        java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                        issue.put("code", "GENERIC_MISSING_QUESTION_TYPE");
                        issue.put("message", "Question #" + idx + " is missing questionType.");
                        issue.put("ruleId", "GENERIC_BASIC_STRUCTURE");
                        issues.add(issue);
                    }

                    // 选择题：至少需要若干选项（这里使用>=2 的宽松下限）
                    if (qtype instanceof String) {
                        String typeStr = ((String) qtype).toUpperCase();
                        if (typeStr.contains("CHOICE") || typeStr.contains("SELECT") || typeStr.contains("MCQ")) {
                            Object opts = q.get("options");
                            if (!(opts instanceof java.util.List)) {
                                status = "ERROR";
                                java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                                issue.put("code", "GENERIC_OPTIONS_NOT_ARRAY");
                                issue.put("message", "Question #" + idx + " has choice-like type but options is not an array.");
                                issue.put("ruleId", "GENERIC_OPTIONS_STRUCTURE");
                                issues.add(issue);
                            } else {
                                int size = ((java.util.List<?>) opts).size();
                                if (size < 2) {
                                    status = "ERROR";
                                    java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                                    issue.put("code", "GENERIC_TOO_FEW_OPTIONS");
                                    issue.put("message", "Question #" + idx + " has only " + size + " options, expected at least 2.");
                                    issue.put("ruleId", "GENERIC_OPTIONS_STRUCTURE");
                                    issues.add(issue);
                                }
                            }
                        }
                    }
                }

                // 3) 总题数非常宽松的范围检查（主要用于捕捉明显的解析失败）
                int totalQuestions = questions.size();
                int minTotal = 1;
                int maxTotal = 1000;
                if (totalQuestions < minTotal) {
                    status = "ERROR";
                    java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                    issue.put("code", "GENERIC_TOTAL_QUESTIONS_TOO_FEW");
                    issue.put("message", "Total questions (" + totalQuestions + ") is below generic minimum (" + minTotal + ").");
                    issue.put("ruleId", "GENERIC_TOTAL_RANGE");
                    issues.add(issue);
                } else if (totalQuestions > maxTotal) {
                    status = "ERROR";
                    java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                    issue.put("code", "GENERIC_TOTAL_QUESTIONS_TOO_MANY");
                    issue.put("message", "Total questions (" + totalQuestions + ") exceeds generic maximum (" + maxTotal + ").");
                    issue.put("ruleId", "GENERIC_TOTAL_RANGE");
                    issues.add(issue);
                }
            }

            result.put("structureStatus", status);
            result.put("structureIssues", issues);
            return result;
        }

        // CET4/CET6 粗粒度模板：仅按 Writing / Listening / Reading / Translation 四大板块做题量和题型检查
        if (template == ExamTemplate.Template_CET4_Generic || template == ExamTemplate.Template_CET6_Generic) {
            java.util.List<ExamStructureRule> rules = ExamStructureRulesRegistry.getRulesForTemplate(template);
            if (rules == null || rules.isEmpty()) {
                result.put("structureStatus", "OK");
                result.put("structureIssues", java.util.Collections.emptyList());
                return result;
            }

            java.util.List<java.util.Map<String, Object>> issues = new java.util.ArrayList<>();
            String status = "OK";

            // 为每个规则计算题量：根据 questionType 是否在 allowedQuestionTypes 中
            java.util.Map<String, Integer> ruleToCount = new java.util.HashMap<>();
            if (questions != null) {
                for (java.util.Map<String, Object> q : questions) {
                    if (q == null) continue;
                    Object typeObj = q.get("questionType");
                    if (!(typeObj instanceof String)) continue;
                    String qtype = ((String) typeObj).trim();
                    if (qtype.isEmpty()) continue;

                    for (ExamStructureRule rule : rules) {
                        java.util.List<String> allowed = rule.getAllowedQuestionTypes();
                        if (allowed == null || allowed.isEmpty()) continue;
                        if (allowed.contains(qtype)) {
                            String ruleId = rule.getId() != null ? rule.getId() : "UNKNOWN";
                            ruleToCount.put(ruleId, ruleToCount.getOrDefault(ruleId, 0) + 1);
                        }
                    }
                }
            }

            // 对每个大板块检查题量是否在规则区间内
            for (ExamStructureRule rule : rules) {
                String ruleId = rule.getId() != null ? rule.getId() : "UNKNOWN";
                int count = ruleToCount.getOrDefault(ruleId, 0);
                Integer secMin = rule.getMinQuestionCount();
                Integer secMax = rule.getMaxQuestionCount();
                if (secMin == null && secMax == null) {
                    continue;
                }

                if (secMin != null && count < secMin) {
                    status = "ERROR";
                    java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                    issue.put("code", "SECTION_TOO_FEW_QUESTIONS");
                    issue.put("message", "Section " + ruleId + " has " + count + " questions, below expected minimum (" + secMin + ") for template " + template + ".");
                    issue.put("ruleId", ruleId);
                    issues.add(issue);
                } else if (secMax != null && count > secMax) {
                    status = "ERROR";
                    java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                    issue.put("code", "SECTION_TOO_MANY_QUESTIONS");
                    issue.put("message", "Section " + ruleId + " has " + count + " questions, exceeding expected maximum (" + secMax + ") for template " + template + ".");
                    issue.put("ruleId", ruleId);
                    issues.add(issue);
                }
            }

            result.put("structureStatus", status);
            result.put("structureIssues", issues);
            return result;
        }

        // 高考模板：使用 ExamStructureRule 和 Registry 做更精细的结构检查
        java.util.List<ExamStructureRule> rules = ExamStructureRulesRegistry.getRulesForTemplate(template);
        if (rules == null || rules.isEmpty()) {
            result.put("structureStatus", "OK");
            result.put("structureIssues", java.util.Collections.emptyList());
            return result;
        }

        int totalQuestions = (questions != null) ? questions.size() : 0;

        int expectedMin = 0;
        int expectedMax = 0;
        for (ExamStructureRule rule : rules) {
            if (rule.getMinQuestionCount() != null) {
                expectedMin += rule.getMinQuestionCount();
            }
            if (rule.getMaxQuestionCount() != null) {
                expectedMax += rule.getMaxQuestionCount();
            }
        }

        java.util.List<java.util.Map<String, Object>> issues = new java.util.ArrayList<>();

        // Use a conservative default if the rules do not define a range
        if (expectedMin == 0 && expectedMax == 0) {
            expectedMin = 1;
            expectedMax = 200;
        }

        String status = "OK";

        // 1) 全局总题量检查
        if (totalQuestions < expectedMin) {
            status = "ERROR";
            java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
            issue.put("code", "TOTAL_QUESTIONS_TOO_FEW");
            issue.put("message", "Total questions (" + totalQuestions + ") is below expected minimum (" + expectedMin + ") for template " + template + ".");
            issue.put("ruleId", "GLOBAL");
            issues.add(issue);
        } else if (totalQuestions > expectedMax) {
            status = "ERROR";
            java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
            issue.put("code", "TOTAL_QUESTIONS_TOO_MANY");
            issue.put("message", "Total questions (" + totalQuestions + ") exceeds expected maximum (" + expectedMax + ") for template " + template + ".");
            issue.put("ruleId", "GLOBAL");
            issues.add(issue);
        }

        // 2) 按 partName 粗分 Section 级题量检查
        java.util.Map<String, Integer> partCount = new java.util.HashMap<>();
        if (questions != null) {
            for (java.util.Map<String, Object> q : questions) {
                Object pn = q.get("partName");
                if (pn instanceof String) {
                    String partName = (String) pn;
                    partCount.put(partName, partCount.getOrDefault(partName, 0) + 1);
                }
            }
        }

        // Listening_A / Listening_B 直接与对应规则对齐
        for (ExamStructureRule rule : rules) {
            String ruleId = rule.getId();
            if (ruleId == null) continue;

            String mappedPart = null;
            if ("LISTENING_SECTION_A".equals(ruleId)) {
                mappedPart = "Listening_A";
            } else if ("LISTENING_SECTION_B".equals(ruleId)) {
                mappedPart = "Listening_B";
            } else if ("SUMMARY_WRITING".equals(ruleId)) {
                mappedPart = "Writing_Summary";
            } else if ("TRANSLATION".equals(ruleId)) {
                mappedPart = "Writing_Translation";
            } else if ("GUIDED_WRITING".equals(ruleId)) {
                // GuidedWriting 可能出现在 Writing_Guided 或 Writing 中
                mappedPart = partCount.containsKey("Writing_Guided") ? "Writing_Guided" : "Writing";
            }

            if (mappedPart == null) {
                continue;
            }

            int count = partCount.getOrDefault(mappedPart, 0);
            Integer secMin = rule.getMinQuestionCount();
            Integer secMax = rule.getMaxQuestionCount();
            if (secMin == null && secMax == null) {
                continue;
            }

            if (secMin != null && count < secMin) {
                status = "ERROR";
                java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                issue.put("code", "SECTION_TOO_FEW_QUESTIONS");
                issue.put("message", "Section for rule " + ruleId + " has " + count + " questions, below expected minimum (" + secMin + ").");
                issue.put("ruleId", ruleId);
                issues.add(issue);
            } else if (secMax != null && count > secMax) {
                status = "ERROR";
                java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                issue.put("code", "SECTION_TOO_MANY_QUESTIONS");
                issue.put("message", "Section for rule " + ruleId + " has " + count + " questions, exceeding expected maximum (" + secMax + ").");
                issue.put("ruleId", ruleId);
                issues.add(issue);
            }
        }

        // Grammar / Reading 使用聚合检查
        int grammarCount = partCount.getOrDefault("Grammar", 0);
        int grammarMin = 0;
        int grammarMax = 0;
        for (ExamStructureRule rule : rules) {
            if (rule.getId() == null) continue;
            if (rule.getId().startsWith("GRAMMAR_SECTION")) {
                if (rule.getMinQuestionCount() != null) {
                    grammarMin += rule.getMinQuestionCount();
                }
                if (rule.getMaxQuestionCount() != null) {
                    grammarMax += rule.getMaxQuestionCount();
                }
            }
        }
        if (grammarMin > 0 || grammarMax > 0) {
            if (grammarMin == 0) grammarMin = 1;
            if (grammarMax == 0) grammarMax = 200;
            if (grammarCount < grammarMin) {
                status = "ERROR";
                java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                issue.put("code", "SECTION_TOO_FEW_QUESTIONS");
                issue.put("message", "Grammar sections combined have " + grammarCount + " questions, below expected minimum (" + grammarMin + ").");
                issue.put("ruleId", "GRAMMAR_ALL");
                issues.add(issue);
            } else if (grammarCount > grammarMax) {
                status = "ERROR";
                java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                issue.put("code", "SECTION_TOO_MANY_QUESTIONS");
                issue.put("message", "Grammar sections combined have " + grammarCount + " questions, exceeding expected maximum (" + grammarMax + ").");
                issue.put("ruleId", "GRAMMAR_ALL");
                issues.add(issue);
            }
        }

        int readingCount = partCount.getOrDefault("Reading", 0);
        int readingMin = 0;
        int readingMax = 0;
        for (ExamStructureRule rule : rules) {
            if (rule.getId() == null) continue;
            if (rule.getId().startsWith("READING_SECTION")) {
                if (rule.getMinQuestionCount() != null) {
                    readingMin += rule.getMinQuestionCount();
                }
                if (rule.getMaxQuestionCount() != null) {
                    readingMax += rule.getMaxQuestionCount();
                }
            }
        }
        if (readingMin > 0 || readingMax > 0) {
            if (readingMin == 0) readingMin = 1;
            if (readingMax == 0) readingMax = 200;
            if (readingCount < readingMin) {
                status = "ERROR";
                java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                issue.put("code", "SECTION_TOO_FEW_QUESTIONS");
                issue.put("message", "Reading sections combined have " + readingCount + " questions, below expected minimum (" + readingMin + ").");
                issue.put("ruleId", "READING_ALL");
                issues.add(issue);
            } else if (readingCount > readingMax) {
                status = "ERROR";
                java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                issue.put("code", "SECTION_TOO_MANY_QUESTIONS");
                issue.put("message", "Reading sections combined have " + readingCount + " questions, exceeding expected maximum (" + readingMax + ").");
                issue.put("ruleId", "READING_ALL");
                issues.add(issue);
            }
        }

        // 基于 passageId 的 Reading 文章级基础检查（仅对高考类模板启用）
        if (questions != null && !questions.isEmpty()) {
            java.util.Map<String, java.util.List<Integer>> passageToSeqs = new java.util.HashMap<>();
            java.util.Set<String> emptyPassageIds = new java.util.HashSet<>();

            for (java.util.Map<String, Object> q : questions) {
                if (q == null) continue;
                Object partNameObj = q.get("partName");
                if (!(partNameObj instanceof String) || !"Reading".equals(partNameObj)) {
                    continue;
                }
                Object pidObj = q.get("passageId");
                Object seqObj = q.get("sequenceNumber");

                if (!(pidObj instanceof String)) {
                    // 没有 passageId 或类型不对，记为空 passage 问题
                    emptyPassageIds.add("<MISSING>");
                    continue;
                }
                String passageId = ((String) pidObj).trim();
                if (passageId.isEmpty()) {
                    emptyPassageIds.add("<EMPTY>");
                    continue;
                }

                if (!(seqObj instanceof Number)) {
                    // 没有有效题号，先跳过，不参与区间检查
                    continue;
                }
                int seq = ((Number) seqObj).intValue();
                passageToSeqs
                        .computeIfAbsent(passageId, k -> new java.util.ArrayList<>())
                        .add(seq);
            }

            // 1）出现 passageId 却没有任何题绑定到有效区间（这里主要针对缺失/空的情况）
            for (String bad : emptyPassageIds) {
                status = "ERROR";
                java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                issue.put("code", "READING_PASSAGE_EMPTY");
                issue.put("message", "Found Reading questions without a valid passageId (marker=" + bad + ").");
                issue.put("ruleId", "READING_PASSAGE");
                issues.add(issue);
            }

            // 2）检查同一 passageId 下题目的分布区间，避免严重交叉
            if (!passageToSeqs.isEmpty()) {
                java.util.List<java.util.Map<String, Object>> spans = new java.util.ArrayList<>();
                for (java.util.Map.Entry<String, java.util.List<Integer>> e : passageToSeqs.entrySet()) {
                    String pid = e.getKey();
                    java.util.List<Integer> seqs = e.getValue();
                    if (seqs.isEmpty()) continue;
                    int min = Integer.MAX_VALUE;
                    int max = Integer.MIN_VALUE;
                    for (int s : seqs) {
                        if (s < min) min = s;
                        if (s > max) max = s;
                    }
                    java.util.Map<String, Object> span = new java.util.HashMap<>();
                    span.put("passageId", pid);
                    span.put("start", min);
                    span.put("end", max);
                    spans.add(span);
                }

                // 简单检测区间交叉：若两个 passage 的 [a,b] 和 [c,d] 交叉但不包含，视为分布可疑
                for (int i = 0; i < spans.size(); i++) {
                    String pid1 = (String) spans.get(i).get("passageId");
                    int a = (Integer) spans.get(i).get("start");
                    int b = (Integer) spans.get(i).get("end");
                    for (int j = i + 1; j < spans.size(); j++) {
                        String pid2 = (String) spans.get(j).get("passageId");
                        int c = (Integer) spans.get(j).get("start");
                        int d = (Integer) spans.get(j).get("end");

                        boolean cross = (a < c && b > c && b < d) || (c < a && d > a && d < b);
                        if (cross) {
                            status = "ERROR";
                            java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                            issue.put("code", "READING_PASSAGE_DISTRIBUTION_SUSPECT");
                            issue.put("message", "Passages " + pid1 + " [" + a + "," + b + "] and "
                                    + pid2 + " [" + c + "," + d + "] have heavily interleaved question ranges.");
                            issue.put("ruleId", pid1 + "|" + pid2);
                            issues.add(issue);
                        }
                    }
                }
            }
        }

        result.put("structureStatus", status);
        result.put("structureIssues", issues);
        return result;
    }
    
    private java.util.Map<String, Object> buildFallbackGuidedWritingQuestion(String rawText, int sequenceNumber) {
        String prompt = extractGuidedWritingPrompt(rawText);
        if (prompt == null || prompt.isBlank()) {
            System.err.println(">>> Guided Writing fallback失败：原始文本为空或无法提取有效内容");
            return null;
        }
        java.util.Map<String, Object> question = new LinkedHashMap<>();
        question.put("sequenceNumber", sequenceNumber);
        question.put("questionText", prompt);
        question.put("questionType", "WRITING");
        question.put("difficulty", "Medium");
        question.put("knowledgePoint", "Guided Writing");
        question.put("options", new java.util.ArrayList<>());
        return question;
    }

    private String extractGuidedWritingPrompt(String rawText) {
        if (rawText == null) return null;
        String normalized = rawText.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String[] lines = normalized.split("\n");
        int start = 0;
        java.util.regex.Pattern titlePattern = java.util.regex.Pattern.compile("(?i)^(VI\\.?\\s*)?Guided\\s+Writing.*$");
        while (start < lines.length) {
            String line = lines[start].trim();
            if (line.isEmpty()) {
                start++;
                continue;
            }
            if (titlePattern.matcher(line).matches()) {
                start++;
                continue;
            }
            break;
        }
        if (start >= lines.length) {
            return normalized;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line);
        }
        return sb.toString().trim();
    }

    private SectionMeta extractSectionMeta(String partText) {
        if (partText == null || partText.isBlank()) {
            return new SectionMeta(null, null);
        }
        String normalized = partText.replace("\r\n", "\n");
        String[] lines = normalized.split("\n");
        java.util.List<String> headingLines = new java.util.ArrayList<>();
        String directions = null;
        for (String rawLine : lines) {
            if (rawLine == null) continue;
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (directions == null && line.toLowerCase().startsWith("directions")) {
                directions = line;
                break;
            }
            if (line.matches("(?i)^(Questions?|Q)\\s+\\d+.*")) {
                break;
            }
            headingLines.add(line);
            // 避免 heading 过长
            if (headingLines.size() > 5) break;
        }
        String heading = headingLines.isEmpty() ? null : String.join(" ", headingLines);
        return new SectionMeta(heading, directions);
    }

    /**
     * 模板匹配度评分：根据标题、Directions 和关键字，为每个模板打一个 0~1 分数。
     * 仅使用简单规则，不做机器学习。
     */
    private ExamTemplate chooseTemplateForText(String text, java.util.List<ExamTemplate> candidates, double threshold) {
        if (candidates == null || candidates.isEmpty()) {
            return ExamTemplate.GENERIC;
        }
        if (text == null) {
            return ExamTemplate.GENERIC;
        }

        String normalized = text.replace("\r\n", "\n");
        String lower = normalized.toLowerCase();

        // 提取前几行作为“标题 + 大纲”区域
        String[] lines = normalized.split("\n");
        StringBuilder titleBuf = new StringBuilder();
        int titleLines = Math.min(lines.length, 8);
        for (int i = 0; i < titleLines; i++) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                if (titleBuf.length() > 0) titleBuf.append(' ');
                titleBuf.append(line);
            }
        }
        String titleArea = titleBuf.toString().toLowerCase();

        java.util.Map<ExamTemplate, Double> scores = new java.util.HashMap<>();

        for (ExamTemplate tpl : candidates) {
            double score = 0.0;

            switch (tpl) {
                case GAOKAO_ENGLISH_A:
                case GAOKAO_ENGLISH_A_GROUPED:
                    if (titleArea.contains("senior high school") || titleArea.contains("national matriculation")) {
                        score += 0.4;
                    }
                    if (titleArea.contains("english") && (titleArea.contains("quality test") || titleArea.contains("mock examination") || titleArea.contains("高三") || titleArea.contains("高考"))) {
                        score += 0.3;
                    }
                    if (lower.contains("i. listening") && lower.contains("ii. grammar") && lower.contains("iii. reading")) {
                        score += 0.3;
                    }
                    break;

                case Template_CET4_Generic:
                    if (titleArea.contains("cet-4") || titleArea.contains("band 4") || titleArea.contains("college english test (cet-4)")) {
                        score += 0.6;
                    }
                    if (lower.contains("part i writing") && lower.contains("part ii listening comprehension")) {
                        score += 0.2;
                    }
                    if (lower.contains("part iii reading comprehension") || lower.contains("part iv translation")) {
                        score += 0.2;
                    }
                    break;

                case Template_CET6_Generic:
                    if (titleArea.contains("cet-6") || titleArea.contains("band 6") || titleArea.contains("college english test (cet-6)")) {
                        score += 0.6;
                    }
                    if (lower.contains("part i writing") && lower.contains("part ii listening comprehension")) {
                        score += 0.2;
                    }
                    if (lower.contains("part iii reading comprehension") || lower.contains("part iv translation")) {
                        score += 0.2;
                    }
                    break;

                case GENERIC:
                default:
                    // Generic: 基础得分很低，只作为兜底
                    score = 0.1;
                    break;
            }

            // 根据 Directions 关键字加一点泛用权重
            if (lower.contains("directions:") || lower.contains("read the following") || lower.contains("answer the following questions")) {
                score += 0.05;
            }

            if (score > 1.0) score = 1.0;
            scores.put(tpl, score);
        }

        // 选出最高分模板
        ExamTemplate best = ExamTemplate.GENERIC;
        double bestScore = 0.0;
        for (java.util.Map.Entry<ExamTemplate, Double> e : scores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                best = e.getKey();
            }
        }

        // 调试日志：输出各模板得分与最终选择
        System.out.println(">>> 模板匹配度评分结果：");
        for (java.util.Map.Entry<ExamTemplate, Double> e : scores.entrySet()) {
            System.out.println("    - " + e.getKey() + " => " + String.format("%.3f", e.getValue()));
        }
        System.out.println(">>> 模板自动选择：best=" + best + ", score=" + String.format("%.3f", bestScore));

        // 若最高分低于阈值，则退回 GENERIC
        if (bestScore < threshold) {
            return ExamTemplate.GENERIC;
        }
        return best;
    }

    private static class ListeningGroupRange {
        final String partName;
        final int start;
        final int end;
        final String groupId;

        ListeningGroupRange(String partName, int start, int end, String groupId) {
            this.partName = partName;
            this.start = start;
            this.end = end;
            this.groupId = groupId;
        }
    }

    /**
     * 仅用于 GAOKAO_ENGLISH_A_GROUPED：从 Listening 原文中抽取共享材料题组范围。
     * 支持模式："Questions x and y are based on ..." / "Questions x to y are based on ..."。
     */
    private java.util.List<ListeningGroupRange> extractListeningGroups(String partName, String partText) {
        java.util.List<ListeningGroupRange> result = new java.util.ArrayList<>();
        if (partText == null || partText.isBlank()) {
            return result;
        }

        String normalized = partText.replace("\r\n", "\n");
        // x and y / x to y 这两种形式
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "Questions\\s+(\\d+)\\s+(?:and|to)\\s+(\\d+)\\s+are\\s+based\\s+on",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher m = p.matcher(normalized);
        while (m.find()) {
            try {
                int start = Integer.parseInt(m.group(1));
                int end = Integer.parseInt(m.group(2));
                if (end < start) {
                    int tmp = start;
                    start = end;
                    end = tmp;
                }
                String gid = String.format("%s_Q%d_%d", partName, start, end);
                result.add(new ListeningGroupRange(partName, start, end, gid));
            } catch (NumberFormatException ignore) {
                // 单个匹配异常不影响整体
            }
        }

        if (!result.isEmpty()) {
            System.out.println(">>> Listening groups detected for " + partName + ": " + result.size());
        }
        return result;
    }

    /**
     * 针对 GAOKAO_ENGLISH_A_GROUPED 的 Listening Group 级别结构检查：
     * 1）每个 groupId 至少要有一题绑定；
     * 2）每个组内题目的 sequenceNumber 必须落在 [start, end] 范围内。
     *
     * 仅在调用方传入 GAOKAO_ENGLISH_A_GROUPED 时使用，不修改 questions 列表本身。
     */
    @SuppressWarnings("unchecked")
    private void applyListeningGroupChecks(
            java.util.Map<String, Object> structureResult,
            java.util.List<ListeningGroupRange> listeningGroups,
            java.util.List<java.util.Map<String, Object>> questions) {

        if (structureResult == null) {
            structureResult = new java.util.HashMap<>();
        }

        Object existingStatus = structureResult.get("structureStatus");
        String status = (existingStatus instanceof String) ? (String) existingStatus : "OK";

        Object issuesObj = structureResult.get("structureIssues");
        java.util.List<java.util.Map<String, Object>> issues;
        if (issuesObj instanceof java.util.List) {
            issues = (java.util.List<java.util.Map<String, Object>>) issuesObj;
        } else {
            issues = new java.util.ArrayList<>();
        }

        if (listeningGroups == null || listeningGroups.isEmpty() || questions == null || questions.isEmpty()) {
            // 没有识别到 Listening 题组或没有题目时，不做额外检查
            structureResult.put("structureStatus", status);
            structureResult.put("structureIssues", issues);
            return;
        }

        // 预先按 groupId 聚合题目，便于统计和范围检查
        java.util.Map<String, java.util.List<java.util.Map<String, Object>>> questionsByGroupId = new java.util.HashMap<>();
        for (java.util.Map<String, Object> q : questions) {
            if (q == null) continue;
            Object type = q.get("groupType");
            Object gid = q.get("groupId");
            if (!(gid instanceof String)) continue;
            if (!(type instanceof String) || !"LISTENING_SHARED_MATERIAL".equals(type)) continue;
            String groupId = (String) gid;
            questionsByGroupId
                    .computeIfAbsent(groupId, k -> new java.util.ArrayList<>())
                    .add(q);
        }

        for (ListeningGroupRange grp : listeningGroups) {
            java.util.List<java.util.Map<String, Object>> boundQuestions = questionsByGroupId.get(grp.groupId);
            int boundCount = (boundQuestions != null) ? boundQuestions.size() : 0;

            // 1）检查是否至少有一题绑定
            if (boundCount == 0) {
                status = "ERROR";
                java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                issue.put("code", "LISTENING_GROUP_EMPTY");
                issue.put("message", "Listening group " + grp.groupId + " has no questions bound to it.");
                issue.put("ruleId", grp.groupId);
                issues.add(issue);
                continue; // 没有题就不需要做范围检查
            }

            // 2）检查绑定题目的 sequenceNumber 是否都在 [start, end] 内
            for (java.util.Map<String, Object> q : boundQuestions) {
                Object seqObj = q.get("sequenceNumber");
                if (!(seqObj instanceof Number)) {
                    status = "ERROR";
                    java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                    issue.put("code", "LISTENING_GROUP_RANGE_MISMATCH");
                    issue.put("message", "Question in group " + grp.groupId + " is missing or has non-numeric sequenceNumber.");
                    issue.put("ruleId", grp.groupId);
                    issues.add(issue);
                    continue;
                }
                int seq = ((Number) seqObj).intValue();
                if (seq < grp.start || seq > grp.end) {
                    status = "ERROR";
                    java.util.Map<String, Object> issue = new java.util.LinkedHashMap<>();
                    issue.put("code", "LISTENING_GROUP_RANGE_MISMATCH");
                    issue.put("message", "Question with sequenceNumber " + seq
                            + " is bound to group " + grp.groupId
                            + " but is outside declared range [" + grp.start + ", " + grp.end + "].");
                    issue.put("ruleId", grp.groupId);
                    issues.add(issue);
                }
            }
        }

        structureResult.put("structureStatus", status);
        structureResult.put("structureIssues", issues);
    }

    private static class SectionMeta {
        final String heading;
        final String directions;

        SectionMeta(String heading, String directions) {
            this.heading = heading;
            this.directions = directions;
        }
    }

    /**
     * 针对不同 Part 附加更强约束，限制题号范围与输出类型，减少串段与结构性错误
     */
    private String addPartConstraints(String partName, String partText) {
        String constraints = null;
        if ("Listening_A".equals(partName)) {
            constraints = "Only extract questions 1-10. Return pure JSON with key 'questions'. Do not include any other parts.";
        } else if ("Listening_B".equals(partName) || "Listening_C".equals(partName) || "Listening".equals(partName)) {
            constraints = "Only extract questions 11-20. Return pure JSON with key 'questions'. Do not include any other parts.";
        } else if ("Grammar".equals(partName)) {
            constraints = "Only extract questions 21-40 as GRAMMAR. Options must be an empty array []. Return pure JSON only.";
        } else if ("Reading".equals(partName)) {
            constraints = "Only extract questions 41-70 as READING. Each question must be one object. Return pure JSON only.";
        } else if ("Writing_Summary".equals(partName)) {
            constraints = "Only extract question 71 as WRITING (Summary Writing). Options must be empty []. Return pure JSON only.";
        } else if ("Writing_Translation".equals(partName)) {
            constraints = "Only extract questions 72-74 as TRANSLATION. Options must be empty []. Return pure JSON only.";
        } else if ("Writing_Guided".equals(partName) || "Writing".equals(partName)) {
            constraints = "Only extract question 75 as WRITING (Guided Writing). Options must be empty []. Return pure JSON only.";
        }
        if (constraints == null) {
            return partText; // 未知段落，原样返回
        }
        String header = "[Part Constraints]\n" + constraints + "\n---\n";
        return header + partText;
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
     * 预处理：移除重复的选项行
     * 对于听力题目，经常出现重复的 A B C D 选项，需要去重
     * 保留最后一次出现的选项，删除之前重复的
     */
    private String removeRepeatedOptions(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return rawText;
        }
        
        String[] lines = rawText.split("\n");
        StringBuilder result = new StringBuilder();
        
        // 用于跟踪最近的选项块
        java.util.List<String> optionLines = new java.util.ArrayList<>();
        int lastOptionLineIndex = -1;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            // 检查是否是选项行（A. B. C. D. 或 A) B) C) D) 等格式）
            if (isOptionLine(line)) {
                optionLines.add(line);
                lastOptionLineIndex = i;
            } else {
                // 如果遇到非选项行，检查是否需要去重
                if (!optionLines.isEmpty()) {
                    // 检查是否有重复的选项块
                    if (i > 0 && hasRepeatedOptionBlock(lines, lastOptionLineIndex)) {
                        // 跳过这个重复的选项块，不添加到结果中
                        System.out.println(">>> Detected and removed duplicate options around line " + (lastOptionLineIndex + 1));
                    } else {
                        // 添加选项块
                        for (String optLine : optionLines) {
                            result.append(optLine).append("\n");
                        }
                    }
                    optionLines.clear();
                }
                
                // 添加非选项行
                result.append(line).append("\n");
            }
        }
        
        // 处理最后的选项块
        if (!optionLines.isEmpty()) {
            for (String optLine : optionLines) {
                result.append(optLine).append("\n");
            }
        }
        
        return result.toString();
    }
    
    /**
     * 检查一行是否是选项行
     */
    private boolean isOptionLine(String line) {
        // 匹配 A. B. C. D. 或 A) B) C) D) 或 A B C D 等格式
        return line.matches("^[A-D][.\\)\\s].*") || 
               line.matches("^[A-D]\\s+.*") ||
               line.matches("^\\([A-D]\\).*");
    }
    
    /**
     * 检查是否存在重复的选项块
     * 如果在最近的选项块之前不远处出现了相同的选项，则认为是重复
     */
    private boolean hasRepeatedOptionBlock(String[] lines, int optionEndIndex) {
        if (optionEndIndex < 4) {
            return false; // 不足4行，无法形成完整的选项块
        }
        
        // 获取最近的4行选项（A B C D）
        java.util.List<String> recentOptions = new java.util.ArrayList<>();
        for (int i = Math.max(0, optionEndIndex - 3); i <= optionEndIndex; i++) {
            String line = lines[i].trim();
            if (isOptionLine(line)) {
                recentOptions.add(extractOptionLetter(line));
            }
        }
        
        // 检查之前是否出现过相同的选项序列
        if (recentOptions.size() == 4) {
            for (int i = Math.max(0, optionEndIndex - 20); i < optionEndIndex - 4; i++) {
                String line = lines[i].trim();
                if (isOptionLine(line)) {
                    String letter = extractOptionLetter(line);
                    // 如果找到相同的选项字母，可能是重复
                    if (letter.equals(recentOptions.get(0))) {
                        return true; // 可能是重复的选项块
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * 从选项行中提取选项字母（A、B、C、D）
     */
    private String extractOptionLetter(String line) {
        if (line.length() > 0) {
            char firstChar = line.charAt(0);
            if (firstChar >= 'A' && firstChar <= 'D') {
                return String.valueOf(firstChar);
            }
        }
        return "";
    }
    
    /**
     * 按 Part 分段处理文档
     * 将文档分为 Part I (Listening), Part II (Grammar), Part III (Reading), Part IV (Writing)
     * 分别处理每个 Part，然后合并结果
     */
    private java.util.Map<String, String> segmentByPart(String rawText) {
        java.util.Map<String, String> parts = new java.util.LinkedHashMap<>();
        String[] partMarkers = {
            "I.\\s+Listening(\\s+Comprehension)?",
            "II.\\s+Grammar(\\s+and\\s+Vocabulary)?",
            "III.\\s+Reading(\\s+Comprehension)?",
            "IV.\\s+Summary\\s+Writing|V.\\s+Translation|VI.\\s+Guided\\s+Writing"
        };
        String[] partNames = {"Listening", "Grammar", "Reading", "Writing"};
        int[] startIndices = new int[4];
        int[] endIndices = new int[4];
        for (int i = 0; i < partMarkers.length; i++) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(partMarkers[i]);
            java.util.regex.Matcher matcher = pattern.matcher(rawText);
            if (matcher.find()) {
                startIndices[i] = matcher.start();
            } else {
                startIndices[i] = -1;
            }
        }
        if (startIndices[0] == -1) {
            int idx = rawText.indexOf("I.\tListening Comprehension");
            if (idx == -1) idx = rawText.indexOf("I. Listening Comprehension");
            startIndices[0] = idx;
        }
        if (startIndices[1] == -1) {
            int idx = rawText.indexOf("II.\tGrammar and Vocabulary");
            if (idx == -1) idx = rawText.indexOf("II. Grammar and Vocabulary");
            startIndices[1] = idx;
        }
        if (startIndices[2] == -1) {
            int idx = rawText.indexOf("III.\tReading Comprehension");
            if (idx == -1) idx = rawText.indexOf("III. Reading Comprehension");
            startIndices[2] = idx;
        }
        if (startIndices[3] == -1) {
            int idx4 = rawText.indexOf("IV.\tSummary Writing");
            if (idx4 == -1) idx4 = rawText.indexOf("IV. Summary Writing");
            int idx5 = rawText.indexOf("V.\tTranslation");
            if (idx5 == -1) idx5 = rawText.indexOf("V. Translation");
            int idx6 = rawText.indexOf("VI.\tGuided Writing");
            if (idx6 == -1) idx6 = rawText.indexOf("VI. Guided Writing");
            int min = Integer.MAX_VALUE;
            if (idx4 >= 0) min = Math.min(min, idx4);
            if (idx5 >= 0) min = Math.min(min, idx5);
            if (idx6 >= 0) min = Math.min(min, idx6);
            startIndices[3] = (min == Integer.MAX_VALUE) ? -1 : min;
        }
        for (int i = 0; i < 4; i++) {
            if (startIndices[i] != -1) {
                endIndices[i] = rawText.length();
                for (int j = i + 1; j < 4; j++) {
                    if (startIndices[j] != -1) {
                        endIndices[i] = startIndices[j];
                        break;
                    }
                }
            }
        }

        for (int i = 0; i < 4; i++) {
            if (startIndices[i] != -1) {
                String partText = rawText.substring(startIndices[i], endIndices[i]);
                if (i == 0) {
                    int sa = partText.indexOf("Section A");
                    int sb = partText.indexOf("Section B");
                    int sc = partText.indexOf("Section C");
                    if (sa >= 0 || sb >= 0) {
                        int aStart = sa >= 0 ? sa : -1;
                        int bStart = sb >= 0 ? sb : -1;
                        int cStart = sc >= 0 ? sc : -1;
                        if (aStart >= 0) {
                            int aEnd = bStart >= 0 ? bStart : (cStart >= 0 ? cStart : partText.length());
                            String aText = partText.substring(0, aEnd);
                            parts.put("Listening_A", aText);
                            System.out.println(">>> 提取 Part 1 (Listening_A): " + aText.length() + " 字符");
                        }
                        if (bStart >= 0) {
                            int bEnd = cStart >= 0 ? cStart : partText.length();
                            String bText = partText.substring(bStart, bEnd);
                            parts.put("Listening_B", bText);
                            System.out.println(">>> 提取 Part 1 (Listening_B): " + bText.length() + " 字符");
                        }
                        if (cStart >= 0) {
                            String cText = partText.substring(cStart);
                            parts.put("Listening_C", cText);
                            System.out.println(">>> 提取 Part 1 (Listening_C): " + cText.length() + " 字符");
                        }
                        if (aStart == -1 && bStart == -1 && cStart == -1) {
                            parts.put(partNames[i], partText);
                            System.out.println(">>> 提取 Part " + (i + 1) + " (" + partNames[i] + "): " + partText.length() + " 字符");
                        }
                    } else {
                        parts.put(partNames[i], partText);
                        System.out.println(">>> 提取 Part " + (i + 1) + " (" + partNames[i] + "): " + partText.length() + " 字符");
                    }
                } else if (i == 3) {
                    int idxIV = indexOfAny(partText, new String[]{"IV.\tSummary Writing", "IV. Summary Writing"});
                    int idxV = indexOfAny(partText, new String[]{"V.\tTranslation", "V. Translation"});
                    int idxVI = indexOfAny(partText, new String[]{"VI.\tGuided Writing", "VI. Guided Writing"});
                    int len = partText.length();
                    boolean wroteAny = false;
                    if (idxIV >= 0) {
                        int endIV = (idxV >= 0 ? idxV : (idxVI >= 0 ? idxVI : len));
                        String tIV = partText.substring(idxIV, endIV);
                        parts.put("Writing_Summary", tIV);
                        System.out.println(">>> 提取 Part 4 (Writing_Summary): " + tIV.length() + " 字符");
                        wroteAny = true;
                    }
                    if (idxV >= 0) {
                        int endV = (idxVI >= 0 ? idxVI : len);
                        String tV = partText.substring(idxV, endV);
                        parts.put("Writing_Translation", tV);
                        System.out.println(">>> 提取 Part 4 (Writing_Translation): " + tV.length() + " 字符");
                        wroteAny = true;
                    }
                    if (idxVI >= 0) {
                        String tVI = partText.substring(idxVI);
                        parts.put("Writing_Guided", tVI);
                        System.out.println(">>> 提取 Part 4 (Writing_Guided): " + tVI.length() + " 字符");
                        wroteAny = true;
                    }
                    if (!wroteAny) {
                        parts.put(partNames[i], partText);
                        System.out.println(">>> 提取 Part " + (i + 1) + " (" + partNames[i] + "): " + partText.length() + " 字符");
                    }
                } else {
                    parts.put(partNames[i], partText);
                    System.out.println(">>> 提取 Part " + (i + 1) + " (" + partNames[i] + "): " + partText.length() + " 字符");
                }
            }
        }
        if (parts.isEmpty()) {
            java.util.regex.Pattern roman = java.util.regex.Pattern.compile("(?m)^[IVX]+\\.\\s+.*$");
            java.util.regex.Matcher m = roman.matcher(rawText);
            java.util.List<Integer> idxs = new java.util.ArrayList<>();
            while (m.find()) idxs.add(m.start());
            for (int k = 0; k < idxs.size(); k++) {
                int s = idxs.get(k);
                int e = (k + 1 < idxs.size()) ? idxs.get(k + 1) : rawText.length();
                String seg = rawText.substring(s, e);
                if (seg.contains("Listening")) {
                    parts.put("Listening", seg);
                } else if (seg.contains("Grammar")) {
                    parts.put("Grammar", seg);
                } else if (seg.contains("Reading")) {
                    parts.put("Reading", seg);
                } else {
                    parts.put("Writing", seg);
                }
            }
        }
        return parts;
    }

    private int indexOfAny(String text, String[] candidates) {
        if (text == null) return -1;
        int min = Integer.MAX_VALUE;
        for (String c : candidates) {
            int idx = text.indexOf(c);
            if (idx >= 0 && idx < min) min = idx;
        }
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    /**
     * 优化版 Prompt - 处理复杂听力结构和重复选项
     */
    private String buildPrompt(String rawText, String subject) {
        return String.format("""
            You are an expert in educational content analysis for complex exam papers.
            Analyze the following text from a %s exam paper and convert it into a structured JSON object.
            The JSON object must have a single key "questions" which is an array of question objects.

            **CRITICAL: Each question must be ONE separate object. Do NOT mix content from different sources.**

            Each question object must have the following fields:
            
            - "sequenceNumber": The sequential order of the question (e.g., 1, 2, 3...).
            - "questionText": ONLY the question prompt itself. Do NOT include:
                * Dialogue/conversation text (that's background context, not the question)
                * Section instructions (those go in the first question of each section)
                * Multiple choice options (those go in "options" field)
            - "questionType": One of: "WRITING", "LISTENING", "MULTIPLE_CHOICE", "TRANSLATION", "GRAMMAR", "VOCABULARY", "READING".
            - "difficulty": "Easy", "Medium", or "Hard".
            - "knowledgePoint": The knowledge point being tested.
            - "options": An array of option objects (empty [] for WRITING/TRANSLATION/FILL-IN questions).
            - "correctOptions": (optional) an array of option letters that are correct, such as ["B"] for single-choice.
            - "answer": (optional) for fill-in-the-blank, translation or writing tasks, the reference answer text.

            **RULES FOR DIFFERENT QUESTION TYPES:**

            1. **LISTENING questions (Part I)**:
               - Treat each numbered question (Q1-10/20) as ONE object.
               - questionText MUST ONLY contain the question number, e.g. "Q1." or "Q11.".
                 Do NOT repeat the option texts inside questionText.
               - Put all option texts ONLY into the "options" array.
               - For each listening question:
                 * options: Array of 4 options
                 * correctOptions: ["A"] / ["B"] / ["C"] / ["D"] depending on the correct answer
               - For Section B (Q11-20), you MAY include the section description once in a separate field or in the first question of that section, but still keep options only in the options array.

            2. **GRAMMAR questions (Part II)**:
               - Each blank is ONE question
               - questionText: "Fill in blank 21: ..."
               - options: Empty array []
               - answer: the correct word or phrase that should fill the blank.

            3. **READING questions (Part III)**:
               - Each question is ONE object
               - questionText: a clear textual question such as "Q56. What is the main idea of the passage?"
               - options: Array of 4 options (for multiple choice questions)
               - correctOptions: array of correct option letters (usually a single letter, e.g. ["C"]).
               - passageId: A string identifying the passage this question belongs to.
                 * All questions for the SAME passage must share the SAME passageId.
                 * Use simple identifiers such as "R_A_1", "R_A_2", "R_B_1" etc.

            4. **WRITING/TRANSLATION (Part IV/V)**:
               - Each task is ONE question
               - questionText: The full task description
               - options: Empty array []
               - answer: (optional) a sample or reference answer.

            5. **OPTIONAL GROUP FIELDS (for future grouping logic):**
               - groupId (optional, string or null):
                 * For listening shared-material groups (e.g., "Questions 1-2 are based on the following...") you MAY assign a shared groupId to those questions.
                 * For reading passage groups (multiple questions based on one passage) you MAY assign a shared groupId to those questions.
               - groupType (optional, string or null):
                 * If you use groupId for listening shared-material questions, set groupType to "LISTENING_SHARED_MATERIAL".
                 * If you use groupId for reading passage-based questions, set groupType to "READING_PASSAGE".
                 * Otherwise, you can omit groupType or set it to null.

            **RULES FOR OPTIONS:**
            - Each option object: {"id": 1, "optionIdentifier": "A", "optionText": "...", "correct": false}
            - Extract the correct answer from the answer key if provided and fill correctOptions accordingly.
            - If duplicate options exist in the text, keep ONLY the last/most complete version.

            Here is the text to analyze:
            ---
            %s
            ---
            
            **CRITICAL REQUIREMENTS:**
            1. Return ONLY valid JSON, nothing else.
            2. Start with {"questions": [ and end with ]}
            3. **EXTRACT ALL QUESTIONS** - do not skip any.
            4. Each question must be a separate object in the array.
            5. Do NOT mix content from different sources in one questionText.
            6. All string values MUST escape special characters properly in JSON, for example:
               - Escape double quotes inside strings.
               - Represent newlines as \\n.
               - Represent carriage returns as \\r.
               - Represent tabs as \\t.
               - Escape backslashes as \\\\.
            7. Verify: Count the questions before returning. If you see 75 questions, return exactly 75.
            
            Return ONLY the JSON object.
            """, subject, rawText);
    }
}
