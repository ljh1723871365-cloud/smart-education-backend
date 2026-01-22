package com.ljh.smarteducation.service.impl;

import com.ljh.smarteducation.service.QuestionFormatDetector;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 题目格式检测器实现 - 混合式识别架构第一层
 * 实现140+种识别规则，覆盖各类题型
 */
@Service
public class QuestionFormatDetectorImpl implements QuestionFormatDetector {
    
    // 正则表达式规则库
    private final Map<String, List<DetectionRule>> ruleRegistry = new HashMap<>();
    
    public QuestionFormatDetectorImpl() {
        initializeRules();
    }
    
    @Override
    public FormatDetectionResult detectFormat(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new FormatDetectionResult("UNKNOWN", "未知", 0.0);
        }
        
        text = text.trim();
        
        // 遍历所有规则类别
        List<FormatDetectionResult> candidates = new ArrayList<>();
        
        for (Map.Entry<String, List<DetectionRule>> entry : ruleRegistry.entrySet()) {
            String category = entry.getKey();
            List<DetectionRule> rules = entry.getValue();
            
            for (DetectionRule rule : rules) {
                FormatDetectionResult result = rule.match(text);
                if (result != null && result.getConfidence() > 0.3) {
                    candidates.add(result);
                }
            }
        }
        
        // 如果没有匹配结果
        if (candidates.isEmpty()) {
            return new FormatDetectionResult("UNKNOWN", "未知", 0.0);
        }
        
        // 选择置信度最高的结果
        candidates.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
        return candidates.get(0);
    }
    
    @Override
    public List<FormatDetectionResult> detectFormats(List<String> textSegments) {
        List<FormatDetectionResult> results = new ArrayList<>();
        for (String segment : textSegments) {
            results.add(detectFormat(segment));
        }
        return results;
    }
    
    /**
     * 初始化所有检测规则
     */
    private void initializeRules() {
        initListeningRules();
        initReadingRules();
        initChoiceRules();
        initFillBlankRules();
        initTranslationRules();
        initWritingRules();
        initMatchingRules();
    }
    
    /**
     * 听力类题型规则（17种）
     */
    private void initListeningRules() {
        List<DetectionRule> rules = new ArrayList<>();
        
        // 1. 短对话
        rules.add(new DetectionRule(
            "LISTENING_SHORT_CONVERSATION",
            "短对话",
            Arrays.asList(
                Pattern.compile("(?i)(short\\s+conversation|短对话)"),
                Pattern.compile("Questions?\\s+\\d+\\s+to\\s+\\d+\\s+are\\s+based\\s+on\\s+the\\s+conversation", Pattern.CASE_INSENSITIVE),
                Pattern.compile("[A-D]\\)\\s*.{10,50}"),
                Pattern.compile("M:|W:|Man:|Woman:")
            ),
            0.85
        ));
        
        // 2. 长对话
        rules.add(new DetectionRule(
            "LISTENING_LONG_CONVERSATION",
            "长对话",
            Arrays.asList(
                Pattern.compile("(?i)(long\\s+conversation|长对话)"),
                Pattern.compile("Questions?\\s+\\d+\\s+to\\s+\\d+\\s+are\\s+based\\s+on\\s+the\\s+conversation", Pattern.CASE_INSENSITIVE),
                Pattern.compile("[A-D]\\)\\s*.{10,50}"),
                Pattern.compile("(M:|W:|Man:|Woman:).{100,}")
            ),
            0.85
        ));
        
        // 3. 新闻听力
        rules.add(new DetectionRule(
            "LISTENING_NEWS",
            "新闻听力",
            Arrays.asList(
                Pattern.compile("(?i)(news\\s+report|新闻)"),
                Pattern.compile("Questions?\\s+\\d+\\s+to\\s+\\d+\\s+are\\s+based\\s+on\\s+the\\s+news", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 4. 短文听力
        rules.add(new DetectionRule(
            "LISTENING_PASSAGE",
            "短文听力",
            Arrays.asList(
                Pattern.compile("(?i)(passage|短文听力|听力短文)"),
                Pattern.compile("Questions?\\s+\\d+\\s+to\\s+\\d+\\s+are\\s+based\\s+on\\s+the\\s+passage", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        // 5. 讲座听力
        rules.add(new DetectionRule(
            "LISTENING_LECTURE",
            "讲座听力",
            Arrays.asList(
                Pattern.compile("(?i)(lecture|讲座|演讲)"),
                Pattern.compile("Questions?\\s+\\d+\\s+to\\s+\\d+\\s+are\\s+based\\s+on\\s+the\\s+lecture", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 6. 听力填空（复合式听写）
        rules.add(new DetectionRule(
            "LISTENING_DICTATION",
            "听力填空",
            Arrays.asList(
                Pattern.compile("(?i)(dictation|compound\\s+dictation|复合式听写|听力填空)"),
                Pattern.compile("\\(\\d+\\)\\s*_{3,}"),
                Pattern.compile("S\\d+\\.")
            ),
            0.9
        ));
        
        // 7. 听力选择题（单选）
        rules.add(new DetectionRule(
            "LISTENING_SINGLE_CHOICE",
            "听力单选题",
            Arrays.asList(
                Pattern.compile("(?i)Section\\s+[A-C].*Listening", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\d+\\.\\s+A\\).*B\\).*C\\).*D\\)")
            ),
            0.8
        ));
        
        // 8. 听力选择题（多选）
        rules.add(new DetectionRule(
            "LISTENING_MULTIPLE_CHOICE",
            "听力多选题",
            Arrays.asList(
                Pattern.compile("(?i)(choose\\s+(two|three)|选择(两|三)个)"),
                Pattern.compile("\\d+\\.\\s+A\\).*E\\)")
            ),
            0.85
        ));
        
        // 9. 听力判断题
        rules.add(new DetectionRule(
            "LISTENING_TRUE_FALSE",
            "听力判断题",
            Arrays.asList(
                Pattern.compile("(?i)(True\\s+or\\s+False|判断正误)"),
                Pattern.compile("T\\s+F")
            ),
            0.9
        ));
        
        // 10. 听力配对题
        rules.add(new DetectionRule(
            "LISTENING_MATCHING",
            "听力配对题",
            Arrays.asList(
                Pattern.compile("(?i)(match|matching|配对)"),
                Pattern.compile("\\d+\\..*[A-F]\\)")
            ),
            0.85
        ));
        
        // 11. 听力图表题
        rules.add(new DetectionRule(
            "LISTENING_CHART",
            "听力图表题",
            Arrays.asList(
                Pattern.compile("(?i)(chart|table|diagram|图表|表格)"),
                Pattern.compile("complete\\s+the\\s+(chart|table)", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 12. 听力地图题
        rules.add(new DetectionRule(
            "LISTENING_MAP",
            "听力地图题",
            Arrays.asList(
                Pattern.compile("(?i)(map|地图)"),
                Pattern.compile("label\\s+the\\s+map", Pattern.CASE_INSENSITIVE)
            ),
            0.95
        ));
        
        // 13. 听力流程图
        rules.add(new DetectionRule(
            "LISTENING_FLOWCHART",
            "听力流程图",
            Arrays.asList(
                Pattern.compile("(?i)(flowchart|flow\\s+chart|流程图)"),
                Pattern.compile("complete\\s+the\\s+flowchart", Pattern.CASE_INSENSITIVE)
            ),
            0.95
        ));
        
        // 14. 听力简答题
        rules.add(new DetectionRule(
            "LISTENING_SHORT_ANSWER",
            "听力简答题",
            Arrays.asList(
                Pattern.compile("(?i)(short\\s+answer|简答)"),
                Pattern.compile("answer\\s+the\\s+questions?.*no\\s+more\\s+than", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 15. 听力句子完成
        rules.add(new DetectionRule(
            "LISTENING_SENTENCE_COMPLETION",
            "听力句子完成",
            Arrays.asList(
                Pattern.compile("(?i)(complete\\s+the\\s+sentence|句子完成)"),
                Pattern.compile("\\d+\\..*_{3,}")
            ),
            0.85
        ));
        
        // 16. 听力笔记完成
        rules.add(new DetectionRule(
            "LISTENING_NOTE_COMPLETION",
            "听力笔记完成",
            Arrays.asList(
                Pattern.compile("(?i)(complete\\s+the\\s+notes?|笔记完成)"),
                Pattern.compile("Notes?:"),
                Pattern.compile("•.*_{3,}")
            ),
            0.9
        ));
        
        // 17. 听力摘要完成
        rules.add(new DetectionRule(
            "LISTENING_SUMMARY_COMPLETION",
            "听力摘要完成",
            Arrays.asList(
                Pattern.compile("(?i)(complete\\s+the\\s+summary|摘要完成)"),
                Pattern.compile("Summary:"),
                Pattern.compile("_{3,}")
            ),
            0.9
        ));
        
        ruleRegistry.put("LISTENING", rules);
    }
    
    /**
     * 阅读类题型规则（25种）
     */
    private void initReadingRules() {
        List<DetectionRule> rules = new ArrayList<>();
        
        // 1. 选词填空
        rules.add(new DetectionRule(
            "READING_BANKED_CLOZE",
            "选词填空",
            Arrays.asList(
                Pattern.compile("(?i)(banked\\s+cloze|选词填空)"),
                Pattern.compile("[A-O]\\)\\s+\\w+"),
                Pattern.compile("\\d+\\.\\s+[A-O]")
            ),
            0.9
        ));
        
        // 2. 长篇阅读（匹配题）
        rules.add(new DetectionRule(
            "READING_MATCHING",
            "长篇阅读",
            Arrays.asList(
                Pattern.compile("(?i)(matching|匹配|段落信息匹配)"),
                Pattern.compile("\\d+\\..*\\[A-P\\]"),
                Pattern.compile("^[A-P]\\.")
            ),
            0.85
        ));
        
        // 3. 仔细阅读（单选）
        rules.add(new DetectionRule(
            "READING_COMPREHENSION_CHOICE",
            "仔细阅读",
            Arrays.asList(
                Pattern.compile("(?i)(passage|仔细阅读|深度阅读)"),
                Pattern.compile("Questions?\\s+\\d+\\s+to\\s+\\d+\\s+are\\s+based\\s+on\\s+the\\s+passage", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\d+\\.\\s+A\\).*B\\).*C\\).*D\\)")
            ),
            0.85
        ));
        
        // 4. 快速阅读
        rules.add(new DetectionRule(
            "READING_FAST_READING",
            "快速阅读",
            Arrays.asList(
                Pattern.compile("(?i)(fast\\s+reading|skimming\\s+and\\s+scanning|快速阅读)"),
                Pattern.compile("Y\\s+N\\s+NG")
            ),
            0.9
        ));
        
        // 5. 是非判断题（Y/N/NG）
        rules.add(new DetectionRule(
            "READING_TRUE_FALSE_NOT_GIVEN",
            "是非判断题",
            Arrays.asList(
                Pattern.compile("(?i)(TRUE|FALSE|NOT\\s+GIVEN)"),
                Pattern.compile("Y.*N.*NG")
            ),
            0.9
        ));
        
        // 6. 标题匹配题
        rules.add(new DetectionRule(
            "READING_HEADING_MATCHING",
            "标题匹配题",
            Arrays.asList(
                Pattern.compile("(?i)(choose.*heading|标题匹配)"),
                Pattern.compile("List\\s+of\\s+Headings", Pattern.CASE_INSENSITIVE)
            ),
            0.95
        ));
        
        // 7. 人名观点配对
        rules.add(new DetectionRule(
            "READING_NAME_MATCHING",
            "人名观点配对",
            Arrays.asList(
                Pattern.compile("(?i)(match.*name|人名.*配对)"),
                Pattern.compile("\\d+\\..*[A-E]\\s+[A-Z][a-z]+\\s+[A-Z][a-z]+")
            ),
            0.9
        ));
        
        // 8. 特征配对题
        rules.add(new DetectionRule(
            "READING_FEATURE_MATCHING",
            "特征配对题",
            Arrays.asList(
                Pattern.compile("(?i)(match.*feature|特征配对)"),
                Pattern.compile("\\d+\\..*[A-F]")
            ),
            0.85
        ));
        
        // 9. 句子完成题
        rules.add(new DetectionRule(
            "READING_SENTENCE_COMPLETION",
            "句子完成题",
            Arrays.asList(
                Pattern.compile("(?i)(complete.*sentence|句子完成)"),
                Pattern.compile("\\d+\\..*_{3,}")
            ),
            0.85
        ));
        
        // 10. 摘要填空
        rules.add(new DetectionRule(
            "READING_SUMMARY_COMPLETION",
            "摘要填空",
            Arrays.asList(
                Pattern.compile("(?i)(complete.*summary|摘要填空)"),
                Pattern.compile("Summary"),
                Pattern.compile("_{3,}")
            ),
            0.9
        ));
        
        // 11. 笔记填空
        rules.add(new DetectionRule(
            "READING_NOTE_COMPLETION",
            "笔记填空",
            Arrays.asList(
                Pattern.compile("(?i)(complete.*note|笔记填空)"),
                Pattern.compile("Notes?:"),
                Pattern.compile("•.*_{3,}")
            ),
            0.9
        ));
        
        // 12. 表格填空
        rules.add(new DetectionRule(
            "READING_TABLE_COMPLETION",
            "表格填空",
            Arrays.asList(
                Pattern.compile("(?i)(complete.*table|表格填空)"),
                Pattern.compile("\\|.*\\|"),
                Pattern.compile("_{3,}")
            ),
            0.9
        ));
        
        // 13. 流程图填空
        rules.add(new DetectionRule(
            "READING_FLOWCHART_COMPLETION",
            "流程图填空",
            Arrays.asList(
                Pattern.compile("(?i)(complete.*flowchart|流程图填空)"),
                Pattern.compile("→"),
                Pattern.compile("_{3,}")
            ),
            0.95
        ));
        
        // 14. 图表标注
        rules.add(new DetectionRule(
            "READING_DIAGRAM_LABELING",
            "图表标注",
            Arrays.asList(
                Pattern.compile("(?i)(label.*diagram|图表标注)"),
                Pattern.compile("\\d+\\.\\s+_{3,}")
            ),
            0.9
        ));
        
        // 15. 简答题
        rules.add(new DetectionRule(
            "READING_SHORT_ANSWER",
            "简答题",
            Arrays.asList(
                Pattern.compile("(?i)(short\\s+answer|简答题)"),
                Pattern.compile("answer.*no\\s+more\\s+than", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 16. 多选题
        rules.add(new DetectionRule(
            "READING_MULTIPLE_CHOICE",
            "多选题",
            Arrays.asList(
                Pattern.compile("(?i)(choose\\s+(two|three)|选择(两|三)个)"),
                Pattern.compile("[A-E]\\)")
            ),
            0.85
        ));
        
        // 17. 细节理解题
        rules.add(new DetectionRule(
            "READING_DETAIL_UNDERSTANDING",
            "细节理解题",
            Arrays.asList(
                Pattern.compile("(?i)(according\\s+to|细节)"),
                Pattern.compile("\\d+\\.\\s+What.*\\?")
            ),
            0.75
        ));
        
        // 18. 主旨大意题
        rules.add(new DetectionRule(
            "READING_MAIN_IDEA",
            "主旨大意题",
            Arrays.asList(
                Pattern.compile("(?i)(main\\s+idea|best\\s+title|主旨|大意)"),
                Pattern.compile("\\d+\\.\\s+(What|Which).*main.*\\?", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        // 19. 推理判断题
        rules.add(new DetectionRule(
            "READING_INFERENCE",
            "推理判断题",
            Arrays.asList(
                Pattern.compile("(?i)(infer|imply|suggest|推理|推断)"),
                Pattern.compile("\\d+\\.\\s+What\\s+can\\s+(be\\s+)?infer", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        // 20. 词义猜测题
        rules.add(new DetectionRule(
            "READING_VOCABULARY",
            "词义猜测题",
            Arrays.asList(
                Pattern.compile("(?i)(underlined\\s+word|词义|猜测)"),
                Pattern.compile("\\d+\\.\\s+What\\s+does.*mean", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 21. 观点态度题
        rules.add(new DetectionRule(
            "READING_ATTITUDE",
            "观点态度题",
            Arrays.asList(
                Pattern.compile("(?i)(attitude|tone|观点|态度)"),
                Pattern.compile("\\d+\\.\\s+What.*attitude", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        // 22. 写作意图题
        rules.add(new DetectionRule(
            "READING_PURPOSE",
            "写作意图题",
            Arrays.asList(
                Pattern.compile("(?i)(purpose|intention|意图)"),
                Pattern.compile("\\d+\\.\\s+(Why|What).*purpose", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        // 23. 例证题
        rules.add(new DetectionRule(
            "READING_EXAMPLE",
            "例证题",
            Arrays.asList(
                Pattern.compile("(?i)(example|illustrate|例证)"),
                Pattern.compile("\\d+\\.\\s+.*example.*show", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        // 24. 指代题
        rules.add(new DetectionRule(
            "READING_REFERENCE",
            "指代题",
            Arrays.asList(
                Pattern.compile("(?i)(refer\\s+to|指代)"),
                Pattern.compile("\\d+\\.\\s+What\\s+does.*refer", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 25. 结构题
        rules.add(new DetectionRule(
            "READING_STRUCTURE",
            "结构题",
            Arrays.asList(
                Pattern.compile("(?i)(structure|organization|结构)"),
                Pattern.compile("\\d+\\.\\s+How.*organized", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        ruleRegistry.put("READING", rules);
    }
    
    /**
     * 选择类题型规则（4种）
     */
    private void initChoiceRules() {
        List<DetectionRule> rules = new ArrayList<>();
        
        // 1. 单项选择题
        rules.add(new DetectionRule(
            "CHOICE_SINGLE",
            "单项选择题",
            Arrays.asList(
                Pattern.compile("\\d+\\.\\s+.+\\?"),
                Pattern.compile("[A-D]\\)\\s+.+"),
                Pattern.compile("(?i)(choose.*one|单项选择)")
            ),
            0.8
        ));
        
        // 2. 多项选择题
        rules.add(new DetectionRule(
            "CHOICE_MULTIPLE",
            "多项选择题",
            Arrays.asList(
                Pattern.compile("(?i)(choose.*(two|three|more)|多项选择)"),
                Pattern.compile("[A-E]\\)\\s+.+")
            ),
            0.85
        ));
        
        // 3. 判断选择题
        rules.add(new DetectionRule(
            "CHOICE_TRUE_FALSE",
            "判断题",
            Arrays.asList(
                Pattern.compile("(?i)(true.*false|判断|T.*F)"),
                Pattern.compile("\\d+\\.\\s+.+\\s+\\(\\s*[TF]\\s*\\)")
            ),
            0.9
        ));
        
        // 4. 配对选择题
        rules.add(new DetectionRule(
            "CHOICE_MATCHING",
            "配对选择题",
            Arrays.asList(
                Pattern.compile("(?i)(match|配对)"),
                Pattern.compile("\\d+\\..*[A-F]\\)")
            ),
            0.85
        ));
        
        ruleRegistry.put("CHOICE", rules);
    }
    
    /**
     * 填空类题型规则（12种）
     */
    private void initFillBlankRules() {
        List<DetectionRule> rules = new ArrayList<>();
        
        // 1. 单词填空
        rules.add(new DetectionRule(
            "FILL_WORD",
            "单词填空",
            Arrays.asList(
                Pattern.compile("(?i)(word|单词填空)"),
                Pattern.compile("\\d+\\.\\s+_{3,}")
            ),
            0.8
        ));
        
        // 2. 短语填空
        rules.add(new DetectionRule(
            "FILL_PHRASE",
            "短语填空",
            Arrays.asList(
                Pattern.compile("(?i)(phrase|短语填空)"),
                Pattern.compile("_{5,}")
            ),
            0.8
        ));
        
        // 3. 语法填空
        rules.add(new DetectionRule(
            "FILL_GRAMMAR",
            "语法填空",
            Arrays.asList(
                Pattern.compile("(?i)(grammar|语法填空)"),
                Pattern.compile("\\(\\d+\\)\\s*_{3,}")
            ),
            0.85
        ));
        
        // 4. 完形填空
        rules.add(new DetectionRule(
            "FILL_CLOZE",
            "完形填空",
            Arrays.asList(
                Pattern.compile("(?i)(cloze|完形填空)"),
                Pattern.compile("\\d+\\.\\s+A\\).*B\\).*C\\).*D\\)"),
                Pattern.compile("_{3,}")
            ),
            0.9
        ));
        
        // 5. 综合填空
        rules.add(new DetectionRule(
            "FILL_COMPREHENSIVE",
            "综合填空",
            Arrays.asList(
                Pattern.compile("(?i)(comprehensive|综合填空)"),
                Pattern.compile("\\d+\\.\\s+_{3,}")
            ),
            0.8
        ));
        
        // 6. 首字母填空
        rules.add(new DetectionRule(
            "FILL_INITIAL_LETTER",
            "首字母填空",
            Arrays.asList(
                Pattern.compile("(?i)(initial\\s+letter|首字母)"),
                Pattern.compile("\\d+\\.\\s+[a-z]_{2,}")
            ),
            0.9
        ));
        
        // 7. 根据提示填空
        rules.add(new DetectionRule(
            "FILL_WITH_HINT",
            "根据提示填空",
            Arrays.asList(
                Pattern.compile("(?i)(hint|提示)"),
                Pattern.compile("\\d+\\.\\s+_{3,}\\s+\\(.+\\)")
            ),
            0.85
        ));
        
        // 8. 选词填空（给定词库）
        rules.add(new DetectionRule(
            "FILL_WORD_BANK",
            "选词填空",
            Arrays.asList(
                Pattern.compile("(?i)(word\\s+bank|词库)"),
                Pattern.compile("[A-O]\\)\\s+\\w+"),
                Pattern.compile("\\d+\\.\\s+_{3,}")
            ),
            0.9
        ));
        
        // 9. 动词填空
        rules.add(new DetectionRule(
            "FILL_VERB",
            "动词填空",
            Arrays.asList(
                Pattern.compile("(?i)(verb|动词)"),
                Pattern.compile("\\d+\\.\\s+_{3,}\\s+\\(v\\.\\)")
            ),
            0.85
        ));
        
        // 10. 介词填空
        rules.add(new DetectionRule(
            "FILL_PREPOSITION",
            "介词填空",
            Arrays.asList(
                Pattern.compile("(?i)(preposition|介词)"),
                Pattern.compile("\\d+\\.\\s+_{3,}\\s+\\(prep\\.\\)")
            ),
            0.85
        ));
        
        // 11. 冠词填空
        rules.add(new DetectionRule(
            "FILL_ARTICLE",
            "冠词填空",
            Arrays.asList(
                Pattern.compile("(?i)(article|冠词)"),
                Pattern.compile("\\d+\\.\\s+_{3,}\\s+(a|an|the)")
            ),
            0.85
        ));
        
        // 12. 时态填空
        rules.add(new DetectionRule(
            "FILL_TENSE",
            "时态填空",
            Arrays.asList(
                Pattern.compile("(?i)(tense|时态)"),
                Pattern.compile("\\d+\\.\\s+\\w+\\s+\\(_{3,}\\)")
            ),
            0.85
        ));
        
        ruleRegistry.put("FILL_BLANK", rules);
    }
    
    /**
     * 翻译类题型规则（9种）
     */
    private void initTranslationRules() {
        List<DetectionRule> rules = new ArrayList<>();
        
        // 1. 英译汉（句子）
        rules.add(new DetectionRule(
            "TRANSLATION_E2C_SENTENCE",
            "英译汉（句子）",
            Arrays.asList(
                Pattern.compile("(?i)(translate.*Chinese|英译汉)"),
                Pattern.compile("\\d+\\.\\s+[A-Z].+\\.")
            ),
            0.85
        ));
        
        // 2. 英译汉（段落）
        rules.add(new DetectionRule(
            "TRANSLATION_E2C_PARAGRAPH",
            "英译汉（段落）",
            Arrays.asList(
                Pattern.compile("(?i)(translate.*Chinese|英译汉)"),
                Pattern.compile("[A-Z].+\\..+\\..+\\.")
            ),
            0.85
        ));
        
        // 3. 汉译英（句子）
        rules.add(new DetectionRule(
            "TRANSLATION_C2E_SENTENCE",
            "汉译英（句子）",
            Arrays.asList(
                Pattern.compile("(?i)(translate.*English|汉译英)"),
                Pattern.compile("\\d+\\.\\s+[\\u4e00-\\u9fa5]+。")
            ),
            0.85
        ));
        
        // 4. 汉译英（段落）
        rules.add(new DetectionRule(
            "TRANSLATION_C2E_PARAGRAPH",
            "汉译英（段落）",
            Arrays.asList(
                Pattern.compile("(?i)(translate.*English|汉译英)"),
                Pattern.compile("[\\u4e00-\\u9fa5]+。.+。.+。")
            ),
            0.85
        ));
        
        // 5. 词汇翻译
        rules.add(new DetectionRule(
            "TRANSLATION_VOCABULARY",
            "词汇翻译",
            Arrays.asList(
                Pattern.compile("(?i)(translate.*word|词汇翻译)"),
                Pattern.compile("\\d+\\.\\s+\\w+\\s+_{3,}")
            ),
            0.9
        ));
        
        // 6. 短语翻译
        rules.add(new DetectionRule(
            "TRANSLATION_PHRASE",
            "短语翻译",
            Arrays.asList(
                Pattern.compile("(?i)(translate.*phrase|短语翻译)"),
                Pattern.compile("\\d+\\.\\s+\\w+\\s+\\w+\\s+_{3,}")
            ),
            0.9
        ));
        
        // 7. 理解翻译
        rules.add(new DetectionRule(
            "TRANSLATION_COMPREHENSION",
            "理解翻译",
            Arrays.asList(
                Pattern.compile("(?i)(comprehension|理解)"),
                Pattern.compile("translate.*understanding", Pattern.CASE_INSENSITIVE)
            ),
            0.8
        ));
        
        // 8. 应用翻译
        rules.add(new DetectionRule(
            "TRANSLATION_APPLICATION",
            "应用翻译",
            Arrays.asList(
                Pattern.compile("(?i)(application|应用)"),
                Pattern.compile("translate.*context", Pattern.CASE_INSENSITIVE)
            ),
            0.8
        ));
        
        // 9. 专业翻译
        rules.add(new DetectionRule(
            "TRANSLATION_SPECIALIZED",
            "专业翻译",
            Arrays.asList(
                Pattern.compile("(?i)(specialized|technical|专业)"),
                Pattern.compile("(medical|legal|business|科技|法律|商务)", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        ruleRegistry.put("TRANSLATION", rules);
    }
    
    /**
     * 写作类题型规则（30种）
     */
    private void initWritingRules() {
        List<DetectionRule> rules = new ArrayList<>();
        
        // 1. 图表作文
        rules.add(new DetectionRule(
            "WRITING_CHART",
            "图表作文",
            Arrays.asList(
                Pattern.compile("(?i)(chart|graph|table|图表)"),
                Pattern.compile("describe.*chart", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 2. 议论文
        rules.add(new DetectionRule(
            "WRITING_ARGUMENTATIVE",
            "议论文",
            Arrays.asList(
                Pattern.compile("(?i)(argument|discuss|debate|议论)"),
                Pattern.compile("agree\\s+or\\s+disagree", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        // 3. 说明文
        rules.add(new DetectionRule(
            "WRITING_EXPOSITORY",
            "说明文",
            Arrays.asList(
                Pattern.compile("(?i)(explain|describe|说明)"),
                Pattern.compile("how\\s+to", Pattern.CASE_INSENSITIVE)
            ),
            0.8
        ));
        
        // 4. 记叙文
        rules.add(new DetectionRule(
            "WRITING_NARRATIVE",
            "记叙文",
            Arrays.asList(
                Pattern.compile("(?i)(narrative|story|记叙)"),
                Pattern.compile("tell.*story", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        // 5. 应用文（书信）
        rules.add(new DetectionRule(
            "WRITING_LETTER",
            "应用文（书信）",
            Arrays.asList(
                Pattern.compile("(?i)(letter|write\\s+to|书信)"),
                Pattern.compile("Dear")
            ),
            0.9
        ));
        
        // 6. 应用文（邮件）
        rules.add(new DetectionRule(
            "WRITING_EMAIL",
            "应用文（邮件）",
            Arrays.asList(
                Pattern.compile("(?i)(email|e-mail|邮件)"),
                Pattern.compile("Subject:")
            ),
            0.95
        ));
        
        // 7. 应用文（通知）
        rules.add(new DetectionRule(
            "WRITING_NOTICE",
            "应用文（通知）",
            Arrays.asList(
                Pattern.compile("(?i)(notice|announcement|通知)"),
                Pattern.compile("Notice|Announcement")
            ),
            0.95
        ));
        
        // 8. 应用文（邀请函）
        rules.add(new DetectionRule(
            "WRITING_INVITATION",
            "应用文（邀请函）",
            Arrays.asList(
                Pattern.compile("(?i)(invitation|invite|邀请)"),
                Pattern.compile("cordially\\s+invite", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 9. 应用文（感谢信）
        rules.add(new DetectionRule(
            "WRITING_THANK_YOU",
            "应用文（感谢信）",
            Arrays.asList(
                Pattern.compile("(?i)(thank\\s+you|gratitude|感谢)"),
                Pattern.compile("thank\\s+you\\s+for", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 10. 应用文（道歉信）
        rules.add(new DetectionRule(
            "WRITING_APOLOGY",
            "应用文（道歉信）",
            Arrays.asList(
                Pattern.compile("(?i)(apology|apologize|道歉)"),
                Pattern.compile("sorry\\s+for", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 11. 应用文（建议信）
        rules.add(new DetectionRule(
            "WRITING_SUGGESTION",
            "应用文（建议信）",
            Arrays.asList(
                Pattern.compile("(?i)(suggest|advice|建议)"),
                Pattern.compile("suggest\\s+that", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        // 12. 应用文（投诉信）
        rules.add(new DetectionRule(
            "WRITING_COMPLAINT",
            "应用文（投诉信）",
            Arrays.asList(
                Pattern.compile("(?i)(complaint|complain|投诉)"),
                Pattern.compile("complain\\s+about", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 13. 应用文（申请信）
        rules.add(new DetectionRule(
            "WRITING_APPLICATION",
            "应用文（申请信）",
            Arrays.asList(
                Pattern.compile("(?i)(application|apply|申请)"),
                Pattern.compile("apply\\s+for", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 14. 应用文（求职信）
        rules.add(new DetectionRule(
            "WRITING_JOB_APPLICATION",
            "应用文（求职信）",
            Arrays.asList(
                Pattern.compile("(?i)(job\\s+application|求职)"),
                Pattern.compile("position\\s+of", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 15. 应用文（推荐信）
        rules.add(new DetectionRule(
            "WRITING_RECOMMENDATION",
            "应用文（推荐信）",
            Arrays.asList(
                Pattern.compile("(?i)(recommendation|recommend|推荐)"),
                Pattern.compile("recommend.*for", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 16. 应用文（请假条）
        rules.add(new DetectionRule(
            "WRITING_LEAVE_NOTE",
            "应用文（请假条）",
            Arrays.asList(
                Pattern.compile("(?i)(leave|absence|请假)"),
                Pattern.compile("ask\\s+for\\s+leave", Pattern.CASE_INSENSITIVE)
            ),
            0.95
        ));
        
        // 17. 应用文（便条）
        rules.add(new DetectionRule(
            "WRITING_NOTE",
            "应用文（便条）",
            Arrays.asList(
                Pattern.compile("(?i)(note|message|便条)"),
                Pattern.compile("leave.*note", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        // 18. 应用文（海报）
        rules.add(new DetectionRule(
            "WRITING_POSTER",
            "应用文（海报）",
            Arrays.asList(
                Pattern.compile("(?i)(poster|海报)"),
                Pattern.compile("Poster")
            ),
            0.95
        ));
        
        // 19. 应用文（广告）
        rules.add(new DetectionRule(
            "WRITING_ADVERTISEMENT",
            "应用文（广告）",
            Arrays.asList(
                Pattern.compile("(?i)(advertisement|ad|广告)"),
                Pattern.compile("For\\s+Sale", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 20. 应用文（报告）
        rules.add(new DetectionRule(
            "WRITING_REPORT",
            "应用文（报告）",
            Arrays.asList(
                Pattern.compile("(?i)(report|报告)"),
                Pattern.compile("Report\\s+on")
            ),
            0.9
        ));
        
        // 21. 看图作文
        rules.add(new DetectionRule(
            "WRITING_PICTURE",
            "看图作文",
            Arrays.asList(
                Pattern.compile("(?i)(picture|image|cartoon|图画)"),
                Pattern.compile("describe.*picture", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 22. 漫画作文
        rules.add(new DetectionRule(
            "WRITING_CARTOON",
            "漫画作文",
            Arrays.asList(
                Pattern.compile("(?i)(cartoon|comic|漫画)"),
                Pattern.compile("describe.*cartoon", Pattern.CASE_INSENSITIVE)
            ),
            0.95
        ));
        
        // 23. 续写作文
        rules.add(new DetectionRule(
            "WRITING_CONTINUATION",
            "续写作文",
            Arrays.asList(
                Pattern.compile("(?i)(continue|continuation|续写)"),
                Pattern.compile("continue\\s+the\\s+story", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 24. 缩写作文
        rules.add(new DetectionRule(
            "WRITING_SUMMARY",
            "缩写作文",
            Arrays.asList(
                Pattern.compile("(?i)(summary|summarize|缩写)"),
                Pattern.compile("write.*summary", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 25. 改写作文
        rules.add(new DetectionRule(
            "WRITING_REWRITE",
            "改写作文",
            Arrays.asList(
                Pattern.compile("(?i)(rewrite|paraphrase|改写)"),
                Pattern.compile("rewrite.*following", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 26. 观点对比
        rules.add(new DetectionRule(
            "WRITING_COMPARISON",
            "观点对比",
            Arrays.asList(
                Pattern.compile("(?i)(compare|contrast|对比)"),
                Pattern.compile("compare.*with", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        // 27. 利弊分析
        rules.add(new DetectionRule(
            "WRITING_PROS_CONS",
            "利弊分析",
            Arrays.asList(
                Pattern.compile("(?i)(advantage.*disadvantage|pros.*cons|利弊)"),
                Pattern.compile("advantages\\s+and\\s+disadvantages", Pattern.CASE_INSENSITIVE)
            ),
            0.9
        ));
        
        // 28. 问题解决
        rules.add(new DetectionRule(
            "WRITING_PROBLEM_SOLUTION",
            "问题解决",
            Arrays.asList(
                Pattern.compile("(?i)(problem.*solution|问题.*解决)"),
                Pattern.compile("solve\\s+the\\s+problem", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        // 29. 原因分析
        rules.add(new DetectionRule(
            "WRITING_CAUSE_ANALYSIS",
            "原因分析",
            Arrays.asList(
                Pattern.compile("(?i)(cause|reason|原因)"),
                Pattern.compile("analyze.*cause", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        // 30. 影响分析
        rules.add(new DetectionRule(
            "WRITING_EFFECT_ANALYSIS",
            "影响分析",
            Arrays.asList(
                Pattern.compile("(?i)(effect|impact|influence|影响)"),
                Pattern.compile("analyze.*effect", Pattern.CASE_INSENSITIVE)
            ),
            0.85
        ));
        
        ruleRegistry.put("WRITING", rules);
    }
    
    /**
     * 配对类题型规则（10种）
     */
    private void initMatchingRules() {
        List<DetectionRule> rules = new ArrayList<>();
        
        // 1. 词义配对
        rules.add(new DetectionRule(
            "MATCHING_WORD_DEFINITION",
            "词义配对",
            Arrays.asList(
                Pattern.compile("(?i)(match.*word.*definition|词义配对)"),
                Pattern.compile("\\d+\\.\\s+\\w+\\s+[A-F]\\)")
            ),
            0.9
        ));
        
        // 2. 句子配对
        rules.add(new DetectionRule(
            "MATCHING_SENTENCE",
            "句子配对",
            Arrays.asList(
                Pattern.compile("(?i)(match.*sentence|句子配对)"),
                Pattern.compile("\\d+\\..*[A-F]\\)")
            ),
            0.85
        ));
        
        // 3. 段落配对
        rules.add(new DetectionRule(
            "MATCHING_PARAGRAPH",
            "段落配对",
            Arrays.asList(
                Pattern.compile("(?i)(match.*paragraph|段落配对)"),
                Pattern.compile("\\d+\\..*\\[A-P\\]")
            ),
            0.85
        ));
        
        // 4. 图文配对
        rules.add(new DetectionRule(
            "MATCHING_PICTURE_TEXT",
            "图文配对",
            Arrays.asList(
                Pattern.compile("(?i)(match.*picture|图文配对)"),
                Pattern.compile("Picture\\s+[A-F]")
            ),
            0.9
        ));
        
        // 5. 人物配对
        rules.add(new DetectionRule(
            "MATCHING_PERSON",
            "人物配对",
            Arrays.asList(
                Pattern.compile("(?i)(match.*person|人物配对)"),
                Pattern.compile("[A-Z][a-z]+\\s+[A-Z][a-z]+")
            ),
            0.85
        ));
        
        // 6. 事件配对
        rules.add(new DetectionRule(
            "MATCHING_EVENT",
            "事件配对",
            Arrays.asList(
                Pattern.compile("(?i)(match.*event|事件配对)"),
                Pattern.compile("\\d+\\..*Event.*[A-F]")
            ),
            0.85
        ));
        
        // 7. 因果配对
        rules.add(new DetectionRule(
            "MATCHING_CAUSE_EFFECT",
            "因果配对",
            Arrays.asList(
                Pattern.compile("(?i)(match.*cause.*effect|因果配对)"),
                Pattern.compile("Cause.*Effect")
            ),
            0.9
        ));
        
        // 8. 问答配对
        rules.add(new DetectionRule(
            "MATCHING_QUESTION_ANSWER",
            "问答配对",
            Arrays.asList(
                Pattern.compile("(?i)(match.*question.*answer|问答配对)"),
                Pattern.compile("Q:\\s+.*A:\\s+")
            ),
            0.9
        ));
        
        // 9. 标题配对
        rules.add(new DetectionRule(
            "MATCHING_HEADING",
            "标题配对",
            Arrays.asList(
                Pattern.compile("(?i)(match.*heading|标题配对)"),
                Pattern.compile("Heading\\s+[A-F]")
            ),
            0.9
        ));
        
        // 10. 特征配对
        rules.add(new DetectionRule(
            "MATCHING_FEATURE",
            "特征配对",
            Arrays.asList(
                Pattern.compile("(?i)(match.*feature|特征配对)"),
                Pattern.compile("Feature\\s+[A-F]")
            ),
            0.85
        ));
        
        ruleRegistry.put("MATCHING", rules);
    }
    
    /**
     * 检测规则内部类
     */
    private static class DetectionRule {
        private final String questionType;
        private final String subType;
        private final List<Pattern> patterns;
        private final double baseConfidence;
        
        public DetectionRule(String questionType, String subType, List<Pattern> patterns, double baseConfidence) {
            this.questionType = questionType;
            this.subType = subType;
            this.patterns = patterns;
            this.baseConfidence = baseConfidence;
        }
        
        /**
         * 匹配文本
         * @param text 待匹配文本
         * @return 匹配结果，如果不匹配返回null
         */
        public FormatDetectionResult match(String text) {
            int matchCount = 0;
            Map<String, Object> features = new HashMap<>();
            
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    matchCount++;
                    features.put("pattern_" + matchCount, matcher.group());
                }
            }
            
            if (matchCount == 0) {
                return null;
            }
            
            // 计算置信度：匹配的模式越多，置信度越高
            double confidence = baseConfidence * (matchCount / (double) patterns.size());
            
            // 文本长度调整
            if (text.length() < 50) {
                confidence *= 0.9;  // 短文本降低置信度
            } else if (text.length() > 500) {
                confidence *= 1.05; // 长文本提高置信度
            }
            
            // 置信度上限为1.0
            confidence = Math.min(confidence, 1.0);
            
            FormatDetectionResult result = new FormatDetectionResult(questionType, subType, confidence);
            result.setFeatures(features);
            result.setDetectionRule(questionType + "_RULE");
            
            return result;
        }
    }
}
