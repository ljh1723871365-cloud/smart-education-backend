package com.ljh.smarteducation.service.impl;

import com.ljh.smarteducation.service.QuestionFormatDetector.FormatDetectionResult;
import com.ljh.smarteducation.service.QuestionStructureExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 题目结构化提取器实现类
 * 第二层：根据格式检测结果，使用对应的提取规则解析题目结构
 * 包含30+种结构化提取规则
 */
@Service
public class QuestionStructureExtractorImpl implements QuestionStructureExtractor {
    
    private static final Logger logger = LoggerFactory.getLogger(QuestionStructureExtractorImpl.class);
    
    // 提取规则注册表
    private final Map<String, ExtractionRule> extractionRules = new HashMap<>();
    
    /**
     * 提取规则内部类
     */
    private static class ExtractionRule {
        String ruleName;
        Pattern questionPattern;
        Pattern optionsPattern;
        Pattern answerPattern;
        Pattern metadataPattern;
        
        public ExtractionRule(String ruleName, Pattern questionPattern, 
                            Pattern optionsPattern, Pattern answerPattern,
                            Pattern metadataPattern) {
            this.ruleName = ruleName;
            this.questionPattern = questionPattern;
            this.optionsPattern = optionsPattern;
            this.answerPattern = answerPattern;
            this.metadataPattern = metadataPattern;
        }
    }
    
    public QuestionStructureExtractorImpl() {
        initExtractionRules();
    }
    
    /**
     * 初始化所有提取规则
     */
    private void initExtractionRules() {
        // 听力类提取规则（5种）
        initListeningExtractionRules();
        // 阅读类提取规则（6种）
        initReadingExtractionRules();
        // 选择类提取规则（4种）
        initChoiceExtractionRules();
        // 填空类提取规则（4种）
        initFillBlankExtractionRules();
        // 翻译类提取规则（3种）
        initTranslationExtractionRules();
        // 写作类提取规则（5种）
        initWritingExtractionRules();
        // 配对类提取规则（3种）
        initMatchingExtractionRules();
        
        logger.info("初始化了 {} 种结构化提取规则", extractionRules.size());
    }
    
    /**
     * 初始化听力类提取规则
     */
    private void initListeningExtractionRules() {
        // 1. 短对话提取规则
        extractionRules.put("LISTENING_SHORT_CONVERSATION", new ExtractionRule(
            "短对话提取",
            Pattern.compile("(?s)(M:|W:|Man:|Woman:).+?(?=Questions?|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[A-D]\\)\\s*(.+?)(?=[A-D]\\)|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-D])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Questions?\\s+(\\d+)\\s+(?:to|~)\\s+(\\d+)", Pattern.CASE_INSENSITIVE)
        ));
        
        // 2. 长对话提取规则
        extractionRules.put("LISTENING_LONG_CONVERSATION", new ExtractionRule(
            "长对话提取",
            Pattern.compile("(?s)Conversation.+?(?=Questions?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[A-D]\\)\\s*(.+?)(?=[A-D]\\)|Question|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-D])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Questions?\\s+(\\d+)\\s+to\\s+(\\d+)", Pattern.CASE_INSENSITIVE)
        ));
        
        // 3. 短文听力提取规则
        extractionRules.put("LISTENING_PASSAGE", new ExtractionRule(
            "短文听力提取",
            Pattern.compile("(?s)Passage.+?(?=Questions?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[A-D]\\)\\s*(.+?)(?=[A-D]\\)|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-D])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Passage\\s+(\\d+)", Pattern.CASE_INSENSITIVE)
        ));
        
        // 4. 听写填空提取规则
        extractionRules.put("LISTENING_DICTATION", new ExtractionRule(
            "听写填空提取",
            Pattern.compile("(?s).+?(?=S\\d+:|\\d+\\.|$)"),
            Pattern.compile("(S\\d+:|\\d+\\.)\\s*_{3,}"),
            Pattern.compile("(?:Answer|答案)[:\\s]*(.+?)(?=S\\d+:|\\d+\\.|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Compound\\s+Dictation|复合式听写", Pattern.CASE_INSENSITIVE)
        ));
        
        // 5. 讲座/讲话提取规则
        extractionRules.put("LISTENING_LECTURE", new ExtractionRule(
            "讲座提取",
            Pattern.compile("(?s)(?:Lecture|Talk).+?(?=Questions?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[A-D]\\)\\s*(.+?)(?=[A-D]\\)|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-D])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:Lecture|Talk)\\s+(\\d+)", Pattern.CASE_INSENSITIVE)
        ));
    }
    
    /**
     * 初始化阅读类提取规则
     */
    private void initReadingExtractionRules() {
        // 1. 仔细阅读提取规则
        extractionRules.put("READING_CAREFUL", new ExtractionRule(
            "仔细阅读提取",
            Pattern.compile("(?s)Passage.+?(?=\\d+\\.)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[A-D]\\)\\s*(.+?)(?=[A-D]\\)|\\d+\\.|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-D])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Questions?\\s+(\\d+)\\s+to\\s+(\\d+)", Pattern.CASE_INSENSITIVE)
        ));
        
        // 2. 快速阅读提取规则
        extractionRules.put("READING_SKIMMING", new ExtractionRule(
            "快速阅读提取",
            Pattern.compile("(?s).+?(?=\\d+\\.)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:[A-D]\\)|Y|N|NG)\\s*(.+?)(?=[A-D]\\)|\\d+\\.|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-D]|Y|N|NG)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Skimming\\s+and\\s+Scanning", Pattern.CASE_INSENSITIVE)
        ));
        
        // 3. 选词填空提取规则
        extractionRules.put("READING_BANKED_CLOZE", new ExtractionRule(
            "选词填空提取",
            Pattern.compile("(?s).+?(?=\\d+\\.\\s*[A-O]\\))", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[A-O]\\)\\s*(.+?)(?=[A-O]\\)|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-O])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Section\\s+B", Pattern.CASE_INSENSITIVE)
        ));
        
        // 4. 长篇阅读匹配提取规则
        extractionRules.put("READING_MATCHING", new ExtractionRule(
            "长篇阅读匹配提取",
            Pattern.compile("(?s)\\[([A-P])\\].+?(?=\\[|\\d+\\.)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\d+\\.\\s*(.+?)(?=\\d+\\.|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-P])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Section\\s+B", Pattern.CASE_INSENSITIVE)
        ));
        
        // 5. 段落信息匹配提取规则
        extractionRules.put("READING_PARAGRAPH_MATCHING", new ExtractionRule(
            "段落信息匹配提取",
            Pattern.compile("(?s)\\[([A-Z])\\].+?(?=\\[|Questions?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\d+\\.\\s*(.+?)(?=\\d+\\.|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-Z])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Paragraph\\s+Information\\s+Matching", Pattern.CASE_INSENSITIVE)
        ));
        
        // 6. 深度阅读提取规则
        extractionRules.put("READING_DEEP", new ExtractionRule(
            "深度阅读提取",
            Pattern.compile("(?s)Passage\\s+\\d+.+?(?=Questions?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[A-D]\\)\\s*(.+?)(?=[A-D]\\)|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-D])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Section\\s+C", Pattern.CASE_INSENSITIVE)
        ));
    }
    
    /**
     * 初始化选择类提取规则
     */
    private void initChoiceExtractionRules() {
        // 1. 单选题提取规则
        extractionRules.put("CHOICE_SINGLE", new ExtractionRule(
            "单选题提取",
            Pattern.compile("\\d+\\.\\s*(.+?)(?=[A-D]\\))", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[A-D]\\)\\s*(.+?)(?=[A-D]\\)|\\d+\\.|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-D])", Pattern.CASE_INSENSITIVE),
            null
        ));
        
        // 2. 多选题提取规则
        extractionRules.put("CHOICE_MULTIPLE", new ExtractionRule(
            "多选题提取",
            Pattern.compile("\\d+\\.\\s*(.+?)(?=[A-F]\\))", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[A-F]\\)\\s*(.+?)(?=[A-F]\\)|\\d+\\.|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-F,\\s]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\(多选\\)|Multiple\\s+Choice", Pattern.CASE_INSENSITIVE)
        ));
        
        // 3. 判断题提取规则
        extractionRules.put("CHOICE_TRUE_FALSE", new ExtractionRule(
            "判断题提取",
            Pattern.compile("\\d+\\.\\s*(.+?)(?=\\(|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:True|False|正确|错误)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*(True|False|T|F|正确|错误)", Pattern.CASE_INSENSITIVE),
            null
        ));
        
        // 4. 完形填空提取规则
        extractionRules.put("CHOICE_CLOZE", new ExtractionRule(
            "完形填空提取",
            Pattern.compile("(?s).+?(?=\\d+\\.\\s*[A-D]\\))"),
            Pattern.compile("[A-D]\\)\\s*(.+?)(?=[A-D]\\)|\\d+\\.|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-D])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Cloze|完形填空", Pattern.CASE_INSENSITIVE)
        ));
    }
    
    /**
     * 初始化填空类提取规则
     */
    private void initFillBlankExtractionRules() {
        // 1. 简答填空提取规则
        extractionRules.put("FILL_SHORT_ANSWER", new ExtractionRule(
            "简答填空提取",
            Pattern.compile("\\d+\\.\\s*(.+?)(?=_{3,}|$)"),
            null,
            Pattern.compile("(?:Answer|答案)[:\\s]*(.+?)(?=\\d+\\.|$)", Pattern.CASE_INSENSITIVE),
            null
        ));
        
        // 2. 句子填空提取规则
        extractionRules.put("FILL_SENTENCE", new ExtractionRule(
            "句子填空提取",
            Pattern.compile("\\d+\\.\\s*(.+?_{3,}.+?)(?=\\d+\\.|$)"),
            null,
            Pattern.compile("(?:Answer|答案)[:\\s]*(.+?)(?=\\d+\\.|$)", Pattern.CASE_INSENSITIVE),
            null
        ));
        
        // 3. 完成句子提取规则
        extractionRules.put("FILL_SENTENCE_COMPLETION", new ExtractionRule(
            "完成句子提取",
            Pattern.compile("\\d+\\.\\s*Complete.+?[:：]\\s*(.+?)(?=\\d+\\.|$)", Pattern.CASE_INSENSITIVE),
            null,
            Pattern.compile("(?:Answer|答案)[:\\s]*(.+?)(?=\\d+\\.|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Sentence\\s+Completion", Pattern.CASE_INSENSITIVE)
        ));
        
        // 4. 摘要填空提取规则
        extractionRules.put("FILL_SUMMARY", new ExtractionRule(
            "摘要填空提取",
            Pattern.compile("(?s)Summary.+?(?=\\d+\\.|$)", Pattern.CASE_INSENSITIVE),
            null,
            Pattern.compile("(?:Answer|答案)[:\\s]*(.+?)(?=\\d+\\.|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Summary\\s+Completion", Pattern.CASE_INSENSITIVE)
        ));
    }
    
    /**
     * 初始化翻译类提取规则
     */
    private void initTranslationExtractionRules() {
        // 1. 段落翻译提取规则
        extractionRules.put("TRANSLATION_PARAGRAPH", new ExtractionRule(
            "段落翻译提取",
            Pattern.compile("(?s)Directions:.+?\\n\\n(.+?)(?=参考译文|Reference|$)", Pattern.CASE_INSENSITIVE),
            null,
            Pattern.compile("(?s)(?:参考译文|Reference\\s+Translation)[:\\s]*(.+?)(?=$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Part\\s+IV|Translation", Pattern.CASE_INSENSITIVE)
        ));
        
        // 2. 句子翻译提取规则
        extractionRules.put("TRANSLATION_SENTENCE", new ExtractionRule(
            "句子翻译提取",
            Pattern.compile("\\d+\\.\\s*(.+?)(?=\\d+\\.|参考译文|$)"),
            null,
            Pattern.compile("(?:参考译文|Reference)[:\\s]*(.+?)(?=\\d+\\.|$)", Pattern.CASE_INSENSITIVE),
            null
        ));
        
        // 3. 词组翻译提取规则
        extractionRules.put("TRANSLATION_PHRASE", new ExtractionRule(
            "词组翻译提取",
            Pattern.compile("\\d+\\.\\s*(.+?)(?=_{3,}|\\d+\\.|$)"),
            null,
            Pattern.compile("(?:Answer|答案)[:\\s]*(.+?)(?=\\d+\\.|$)", Pattern.CASE_INSENSITIVE),
            null
        ));
    }
    
    /**
     * 初始化写作类提取规则
     */
    private void initWritingExtractionRules() {
        // 1. 图表作文提取规则
        extractionRules.put("WRITING_CHART", new ExtractionRule(
            "图表作文提取",
            Pattern.compile("(?s)Directions:.+?(?=参考范文|Sample|$)", Pattern.CASE_INSENSITIVE),
            null,
            Pattern.compile("(?s)(?:参考范文|Sample\\s+Essay)[:\\s]*(.+?)(?=$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("chart|graph|图表", Pattern.CASE_INSENSITIVE)
        ));
        
        // 2. 议论文提取规则
        extractionRules.put("WRITING_ARGUMENTATIVE", new ExtractionRule(
            "议论文提取",
            Pattern.compile("(?s)Directions:.+?(?=参考范文|Sample|$)", Pattern.CASE_INSENSITIVE),
            null,
            Pattern.compile("(?s)(?:参考范文|Sample\\s+Essay)[:\\s]*(.+?)(?=$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("argumentative|议论文", Pattern.CASE_INSENSITIVE)
        ));
        
        // 3. 应用文提取规则
        extractionRules.put("WRITING_PRACTICAL", new ExtractionRule(
            "应用文提取",
            Pattern.compile("(?s)Directions:.+?(?=参考范文|Sample|$)", Pattern.CASE_INSENSITIVE),
            null,
            Pattern.compile("(?s)(?:参考范文|Sample)[:\\s]*(.+?)(?=$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("letter|email|notice|邀请函|通知", Pattern.CASE_INSENSITIVE)
        ));
        
        // 4. 看图作文提取规则
        extractionRules.put("WRITING_PICTURE", new ExtractionRule(
            "看图作文提取",
            Pattern.compile("(?s)Directions:.+?(?=参考范文|Sample|$)", Pattern.CASE_INSENSITIVE),
            null,
            Pattern.compile("(?s)(?:参考范文|Sample)[:\\s]*(.+?)(?=$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("picture|cartoon|漫画|图画", Pattern.CASE_INSENSITIVE)
        ));
        
        // 5. 续写作文提取规则
        extractionRules.put("WRITING_CONTINUATION", new ExtractionRule(
            "续写作文提取",
            Pattern.compile("(?s)Directions:.+?(?=参考范文|Sample|$)", Pattern.CASE_INSENSITIVE),
            null,
            Pattern.compile("(?s)(?:参考范文|Sample)[:\\s]*(.+?)(?=$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("continuation|续写", Pattern.CASE_INSENSITIVE)
        ));
    }
    
    /**
     * 初始化配对类提取规则
     */
    private void initMatchingExtractionRules() {
        // 1. 人物匹配提取规则
        extractionRules.put("MATCHING_PEOPLE", new ExtractionRule(
            "人物匹配提取",
            Pattern.compile("(?s)List\\s+of\\s+People.+?(?=Questions?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\d+\\.\\s*(.+?)(?=\\d+\\.|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-Z])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Match.*people|人物配对", Pattern.CASE_INSENSITIVE)
        ));
        
        // 2. 标题匹配提取规则
        extractionRules.put("MATCHING_HEADINGS", new ExtractionRule(
            "标题匹配提取",
            Pattern.compile("(?s)List\\s+of\\s+Headings.+?(?=\\d+\\.)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[A-Z]\\.\\s*(.+?)(?=[A-Z]\\.|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-Z])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Match.*headings|标题配对", Pattern.CASE_INSENSITIVE)
        ));
        
        // 3. 信息匹配提取规则
        extractionRules.put("MATCHING_INFORMATION", new ExtractionRule(
            "信息匹配提取",
            Pattern.compile("(?s)Paragraphs.+?(?=Questions?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\d+\\.\\s*(.+?)(?=\\d+\\.|$)"),
            Pattern.compile("(?:Answer|答案)[:\\s]*([A-Z])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Match.*information|信息配对", Pattern.CASE_INSENSITIVE)
        ));
    }
    
    @Override
    public ExtractionResult extractStructure(String text, FormatDetectionResult formatResult) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("文本为空，无法提取结构");
            return createEmptyResult();
        }
        
        if (formatResult == null) {
            logger.warn("格式检测结果为空，使用通用提取规则");
            return extractWithGenericRule(text);
        }
        
        // 根据格式检测结果选择提取规则
        String ruleKey = formatResult.getQuestionType() + "_" + formatResult.getSubType();
        ExtractionRule rule = extractionRules.get(ruleKey);
        
        if (rule == null) {
            logger.warn("未找到对应的提取规则: {}", ruleKey);
            return extractWithGenericRule(text);
        }
        
        return applyExtractionRule(text, rule, formatResult);
    }
    
    @Override
    public List<ExtractionResult> extractStructures(List<String> textSegments, 
                                                   List<FormatDetectionResult> formatResults) {
        if (textSegments == null || textSegments.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<ExtractionResult> results = new ArrayList<>();
        
        for (int i = 0; i < textSegments.size(); i++) {
            FormatDetectionResult formatResult = (formatResults != null && i < formatResults.size()) 
                ? formatResults.get(i) : null;
            
            ExtractionResult result = extractStructure(textSegments.get(i), formatResult);
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * 应用提取规则
     */
    private ExtractionResult applyExtractionRule(String text, ExtractionRule rule, 
                                                 FormatDetectionResult formatResult) {
        ExtractionResult result = new ExtractionResult();
        result.setQuestionType(formatResult.getQuestionType());
        result.setSubType(formatResult.getSubType());
        result.setExtractionRule(rule.ruleName);
        
        try {
            // 提取题目文本
            if (rule.questionPattern != null) {
                Matcher questionMatcher = rule.questionPattern.matcher(text);
                if (questionMatcher.find()) {
                    result.setQuestionText(questionMatcher.group(1).trim());
                }
            }
            
            // 提取选项
            if (rule.optionsPattern != null) {
                List<String> options = new ArrayList<>();
                Matcher optionsMatcher = rule.optionsPattern.matcher(text);
                while (optionsMatcher.find()) {
                    String option = optionsMatcher.group(1);
                    if (option != null && !option.trim().isEmpty()) {
                        options.add(option.trim());
                    }
                }
                result.setOptions(options);
            }
            
            // 提取答案
            if (rule.answerPattern != null) {
                Matcher answerMatcher = rule.answerPattern.matcher(text);
                if (answerMatcher.find()) {
                    result.setCorrectAnswer(answerMatcher.group(1).trim());
                }
            }
            
            // 提取元数据
            if (rule.metadataPattern != null) {
                Map<String, Object> metadata = new HashMap<>();
                Matcher metadataMatcher = rule.metadataPattern.matcher(text);
                if (metadataMatcher.find()) {
                    metadata.put("section", metadataMatcher.group(0));
                }
                result.setMetadata(metadata);
            }
            
            // 计算置信度
            double confidence = calculateExtractionConfidence(result);
            result.setConfidence(confidence);
            
        } catch (Exception e) {
            logger.error("应用提取规则失败: {}", rule.ruleName, e);
            result.setConfidence(0.3);
        }
        
        return result;
    }
    
    /**
     * 使用通用提取规则
     */
    private ExtractionResult extractWithGenericRule(String text) {
        ExtractionResult result = new ExtractionResult();
        result.setQuestionType("UNKNOWN");
        result.setSubType("GENERIC");
        result.setExtractionRule("通用提取规则");
        
        // 尝试提取题目编号和文本
        Pattern questionPattern = Pattern.compile("(\\d+)\\.\\s*(.+?)(?=[A-D]\\)|\\d+\\.|$)", 
                                                 Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher questionMatcher = questionPattern.matcher(text);
        if (questionMatcher.find()) {
            result.setQuestionText(questionMatcher.group(2).trim());
        } else {
            result.setQuestionText(text.length() > 200 ? text.substring(0, 200) + "..." : text);
        }
        
        // 尝试提取选项
        Pattern optionsPattern = Pattern.compile("[A-D]\\)\\s*(.+?)(?=[A-D]\\)|\\d+\\.|$)");
        Matcher optionsMatcher = optionsPattern.matcher(text);
        List<String> options = new ArrayList<>();
        while (optionsMatcher.find()) {
            options.add(optionsMatcher.group(1).trim());
        }
        result.setOptions(options);
        
        // 尝试提取答案
        Pattern answerPattern = Pattern.compile("(?:Answer|答案)[:\\s]*([A-D])", Pattern.CASE_INSENSITIVE);
        Matcher answerMatcher = answerPattern.matcher(text);
        if (answerMatcher.find()) {
            result.setCorrectAnswer(answerMatcher.group(1).trim());
        }
        
        result.setConfidence(0.5); // 通用规则置信度较低
        return result;
    }
    
    /**
     * 计算提取置信度
     */
    private double calculateExtractionConfidence(ExtractionResult result) {
        double confidence = 0.0;
        
        // 题目文本存在: +0.3
        if (result.getQuestionText() != null && !result.getQuestionText().trim().isEmpty()) {
            confidence += 0.3;
        }
        
        // 选项存在且数量合理: +0.3
        if (result.getOptions() != null && !result.getOptions().isEmpty()) {
            int optionCount = result.getOptions().size();
            if (optionCount >= 2 && optionCount <= 6) {
                confidence += 0.3;
            } else if (optionCount == 1 || optionCount > 6) {
                confidence += 0.1;
            }
        }
        
        // 答案存在: +0.2
        if (result.getCorrectAnswer() != null && !result.getCorrectAnswer().trim().isEmpty()) {
            confidence += 0.2;
        }
        
        // 元数据存在: +0.1
        if (result.getMetadata() != null && !result.getMetadata().isEmpty()) {
            confidence += 0.1;
        }
        
        // 题目文本长度合理: +0.1
        if (result.getQuestionText() != null) {
            int length = result.getQuestionText().length();
            if (length >= 10 && length <= 500) {
                confidence += 0.1;
            }
        }
        
        return Math.min(confidence, 1.0);
    }
    
    /**
     * 创建空结果
     */
    private ExtractionResult createEmptyResult() {
        ExtractionResult result = new ExtractionResult();
        result.setQuestionType("UNKNOWN");
        result.setSubType("EMPTY");
        result.setConfidence(0.0);
        result.setExtractionRule("空文本");
        return result;
    }
}
