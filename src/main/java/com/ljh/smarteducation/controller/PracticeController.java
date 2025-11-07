package com.ljh.smarteducation.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ljh.smarteducation.dto.AnswerSubmission;
import com.ljh.smarteducation.dto.SubmissionResult;
import com.ljh.smarteducation.dto.QuestionSetDetailDto;
import com.ljh.smarteducation.entity.Question;
import com.ljh.smarteducation.entity.QuestionSet; // 1. 导入
import com.ljh.smarteducation.entity.ResourceFile;
import com.ljh.smarteducation.entity.StudentSubmission;
import com.ljh.smarteducation.entity.User;
import com.ljh.smarteducation.repository.QuestionSetRepository; // 2. 导入
import com.ljh.smarteducation.repository.StudentSubmissionRepository;
import com.ljh.smarteducation.service.QuestionBankService;
import com.ljh.smarteducation.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/practice")
@CrossOrigin(origins = "*") // 允许学生端访问
public class PracticeController {

    @Autowired
    private QuestionBankService questionBankService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StudentSubmissionRepository submissionRepository;
    @Autowired
    private UserService userService;

    // --- ↓↓↓ 3. (新增) 注入套题仓库 ↓↓↓ ---
    @Autowired
    private QuestionSetRepository questionSetRepository;
    // --- ↑↑↑ 3. 新增结束 ↑↑↑ ---


    /**
     * (旧) GET /api/practice/questions - (保持不变, 用于随机练习)
     */
    @GetMapping("/questions")
    public ResponseEntity<List<Question>> getPracticeQuestions() {
        List<Question> allQuestions = questionBankService.getAllQuestions();
        Collections.shuffle(allQuestions);
        List<Question> practiceQuestions = allQuestions.subList(0, Math.min(5, allQuestions.size()));
        return ResponseEntity.ok(practiceQuestions);
    }

    // --- ↓↓↓ 4. (新增) API: 获取所有套题 ↓↓↓ ---
    @GetMapping("/sets")
    public ResponseEntity<List<QuestionSet>> getQuestionSets() {
        // (简单起见，我们先返回所有学科的套题)
        List<QuestionSet> sets = questionSetRepository.findAll();
        return ResponseEntity.ok(sets);
    }
    // --- ↑↑↑ 4. 新增结束 ↑↑↑ ---

    // --- ↓↓↓ 5. (核心修改) API: 获取指定套题的所有题目和音频 ---
    @GetMapping("/set/{setId}")
    public ResponseEntity<QuestionSetDetailDto> getQuestionsForSet(@PathVariable Long setId) {
        // 使用 service 中按序号排序的方法
        List<Question> questions = questionBankService.getQuestionsBySetId(setId);
        
        // (新增) 获取套题信息，并从中得到关联的音频文件
        QuestionSet questionSet = questionSetRepository.findById(setId).orElse(null);
        ResourceFile resourceFile = null;
        if (questionSet != null) {
            resourceFile = questionSet.getResourceFile();
        }
        
        // (新增) 组装成 DTO 返回
        QuestionSetDetailDto dto = new QuestionSetDetailDto();
        dto.setQuestions(questions);
        dto.setResourceFile(resourceFile);

        return ResponseEntity.ok(dto);
    }
    // --- ↑↑↑ 5. 修改结束 ↑↑↑ ---


    /**
     * POST /api/practice/submit - (核心修改)
     * 现在此接口会保存学生的答卷
     */
    @PostMapping("/submit")
    public ResponseEntity<SubmissionResult> submitAnswers(@RequestBody AnswerSubmission submission, Authentication authentication) {

        // (获取当前登录用户)
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String username = userDetails.getUsername();
        User currentUser = userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<Long, String> studentAnswers = submission.getAnswers();
        SubmissionResult result = new SubmissionResult();
        Map<Long, Boolean> correctnessMap = new HashMap<>();
        int score = 0;
        int totalQuestions = studentAnswers.size();
        boolean needsManualGrading = false; // 标记是否包含作文题

        // (自动批改逻辑 - 保持不变)
        for (Map.Entry<Long, String> entry : studentAnswers.entrySet()) {
            Long questionId = entry.getKey();
            String studentAnswerString = entry.getValue();

            Optional<Question> questionOptional = questionBankService.getQuestionById(questionId);
            boolean isCorrect = false;

            if (questionOptional.isPresent()) {
                Question question = questionOptional.get();
                Map<String, Object> content = question.getContent();
                String questionType = (String) content.getOrDefault("questionType", "MULTIPLE_CHOICE");

                if ("MULTIPLE_CHOICE".equals(questionType) || "LISTENING".equals(questionType)) {
                    try {
                        Long selectedOptionIdByStudent = Long.parseLong(studentAnswerString);
                        Long correctOptionIdFromDB = getCorrectOptionId(content);
                        if (selectedOptionIdByStudent.equals(correctOptionIdFromDB)) {
                            score++;
                            isCorrect = true;
                        }
                    } catch (NumberFormatException e) {
                        isCorrect = false;
                    }
                } else if ("WRITING".equals(questionType) || "TRANSLATION".equals(questionType)) {
                    needsManualGrading = true;
                    isCorrect = false; // 默认为 false，等待人工批改
                }
            }
            correctnessMap.put(questionId, isCorrect);
        }

        // (保存答卷记录 - 核心修改)
        StudentSubmission newSubmission = new StudentSubmission();
        newSubmission.setUserId(currentUser.getId());
        newSubmission.setUsername(currentUser.getUsername());

        Map<String, String> answersAsStringMap = studentAnswers.entrySet().stream()
                .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue));
        newSubmission.setAnswers(answersAsStringMap);

        newSubmission.setScore(score);
        newSubmission.setTotalQuestions(totalQuestions);
        newSubmission.setGraded(!needsManualGrading);

        // --- ↓↓↓ 6. (新增) 关联答卷到套题 ↓↓↓ ---
        if (submission.getQuestionSetId() != null) {
            // 从数据库查找 QuestionSet 以确保关联的是一个受管实体
            questionSetRepository.findById(submission.getQuestionSetId())
                    .ifPresent(newSubmission::setQuestionSet);
        }
        // --- ↑↑↑ 6. 新增结束 ↑↑↑ ---

        submissionRepository.save(newSubmission);

        // (返回结果 - 保持不变)
        result.setScore(score);
        result.setTotalQuestions(totalQuestions);
        result.setCorrectnessMap(correctnessMap);

        return ResponseEntity.ok(result);
    }

    /**
     * 辅助方法 (已修复笔误)
     */
    private Long getCorrectOptionId(Map<String, Object> content) {
        Object optionsObject = content.get("options");
        Long correctOptionIdFromDB = -1L;

        if (optionsObject instanceof List) {
            try {
                List<Map<String, Object>> optionsList = objectMapper.convertValue(
                        optionsObject,
                        new TypeReference<List<Map<String, Object>>>() {}
                );

                for (Map<String, Object> optionMap : optionsList) {
                    // (已修复) 使用 optionMap.get
                    Object correctValue = optionMap.getOrDefault("correct", optionMap.get("isCorrect"));
                    if (Boolean.TRUE.equals(correctValue)) {
                        Object optionIdObj = optionMap.get("id");
                        if (optionIdObj instanceof Number) {
                            correctOptionIdFromDB = ((Number) optionIdObj).longValue();
                        } else if (optionIdObj instanceof String) {
                            try {
                                correctOptionIdFromDB = Long.parseLong((String) optionIdObj);
                            } catch (NumberFormatException e) {
                                // 忽略解析错误
                            }
                        }
                        break; // 找到即跳出循环
                    }
                }
            } catch (IllegalArgumentException e) {
                System.err.println("Error converting options list: " + e.getMessage());
            }
        }
        return correctOptionIdFromDB;
    }
}

