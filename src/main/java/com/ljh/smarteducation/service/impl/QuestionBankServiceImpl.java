package com.ljh.smarteducation.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ljh.smarteducation.entity.Question;
import com.ljh.smarteducation.entity.QuestionSet; // 1. (新增) 导入
import com.ljh.smarteducation.repository.QuestionBankRepository;
import com.ljh.smarteducation.repository.QuestionSetRepository; // 2. (新增) 导入
import com.ljh.smarteducation.service.LlmService;
import com.ljh.smarteducation.service.QuestionBankService;
import com.ljh.smarteducation.service.WordParserService;
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

    private final WordParserService wordParserService;
    private final LlmService llmService;
    private final QuestionBankRepository questionRepository;
    private final ObjectMapper objectMapper;

    // --- ↓↓↓ 3. (新增) 注入套题仓库 ↓↓↓ ---
    private final QuestionSetRepository questionSetRepository;

    public QuestionBankServiceImpl(WordParserService wordParserService, LlmService llmService,
                                   QuestionBankRepository questionRepository, ObjectMapper objectMapper,
                                   QuestionSetRepository questionSetRepository) { // 4. (新增)
        this.wordParserService = wordParserService;
        this.llmService = llmService;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
        this.questionSetRepository = questionSetRepository; // 5. (新增)
    }
    // --- ↑↑↑ 3, 4, 5 修改结束 ↑↑↑ ---

    @Override
    @Transactional
    public void importQuestionsFromWord(MultipartFile file, String subject) throws IOException {
        String rawText = wordParserService.parseWord(file);
        String jsonContentString = llmService.getStructuredQuestions(rawText, subject).block();

        System.out.println("================ AI RAW RESPONSE START ================");
        System.out.println(jsonContentString);
        System.out.println("================= AI RAW RESPONSE END =================");

        try {
            // --- ↓↓↓ 6. (核心修改) 创建并保存套题 ↓↓↓ ---
            QuestionSet newSet = new QuestionSet();
            newSet.setTitle(file.getOriginalFilename()); // 使用文件名作为标题
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
                fullJsonStructure = objectMapper.readValue(jsonContentString, new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                // JSON解析失败，尝试修复常见问题
                System.err.println(">>> JSON parsing failed. Attempting to fix common issues...");
                System.err.println(">>> Error details: " + e.getMessage());
                
                // 尝试修复：移除可能的控制字符（但保留换行符等常见的转义字符）
                String fixedJson = jsonContentString
                    .replaceAll("[\\x00-\\x08\\x0B-\\x0C\\x0E-\\x1F\\x7F]", ""); // 移除控制字符，但保留 \n (0x0A) 和 \r (0x0D) 和 \t (0x09)
                
                try {
                    // 再次尝试解析
                    fullJsonStructure = objectMapper.readValue(fixedJson, new TypeReference<Map<String, Object>>() {});
                    System.out.println(">>> JSON fixed and parsed successfully after repair attempt.");
                } catch (JsonProcessingException e2) {
                    // 修复失败，提供详细的错误信息
                    int errorLine = 887; // 默认值
                    int errorColumn = 329; // 默认值
                    String errorMsg = e.getMessage();
                    
                    // 尝试从错误消息中提取行号和列号
                    if (errorMsg.contains("line:") && errorMsg.contains("column:")) {
                        try {
                            String[] parts = errorMsg.split("line:")[1].split("column:");
                            errorLine = Integer.parseInt(parts[0].trim());
                            errorColumn = Integer.parseInt(parts[1].split(",")[0].trim());
                        } catch (Exception ignored) {}
                    }
                    
                    // 计算错误位置附近的文本
                    String[] lines = jsonContentString.split("\n");
                    StringBuilder errorContext = new StringBuilder();
                    if (errorLine > 0 && errorLine <= lines.length) {
                        int startLine = Math.max(0, errorLine - 3);
                        int endLine = Math.min(lines.length - 1, errorLine + 2);
                        errorContext.append("Error context around line ").append(errorLine).append(":\n");
                        for (int i = startLine; i <= endLine; i++) {
                            errorContext.append(String.format("Line %d: %s\n", i + 1, lines[i]));
                        }
                    }
                    
                    System.err.println(">>> JSON parsing failed after repair attempt.");
                    System.err.println(">>> " + errorContext.toString());
                    System.err.println(">>> JSON Response length: " + jsonContentString.length());
                    
                    throw new IOException("JSON解析失败：在第" + errorLine + "行第" + errorColumn + "列附近，字符串值中的引号未正确转义。" +
                        "这通常是因为AI返回的JSON中包含了未转义的特殊字符。请尝试：" +
                        "1. 检查上传的Word文件内容是否包含特殊字符；" +
                        "2. 尝试重新上传文件；" +
                        "3. 如果问题持续，请联系技术支持。错误详情: " + e2.getMessage(), e2);
                }
            }
            Object questionsObject = fullJsonStructure.get("questions");
            List<Map<String, Object>> questionMaps = Collections.emptyList();

            if (questionsObject instanceof List) {
                try {
                    // 使用 ObjectMapper 安全转换，避免未检查的类型转换警告
                    questionMaps = objectMapper.convertValue(questionsObject, new TypeReference<List<Map<String, Object>>>() {});
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
                throw new RuntimeException("AI processing succeeded, but no valid questions were found in the response.");
            }
        } catch (IOException e) {
            System.err.println("Error processing JSON from LLM: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("Unexpected error during question import: " + e.getMessage());
            throw new RuntimeException("Unexpected error during import.", e);
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
}
