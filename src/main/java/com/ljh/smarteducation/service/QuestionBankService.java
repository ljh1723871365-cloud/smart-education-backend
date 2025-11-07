package com.ljh.smarteducation.service;

import com.ljh.smarteducation.entity.Question;
import com.ljh.smarteducation.entity.QuestionSet; // 1. (新增) 导入
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface QuestionBankService {

    // (保持不变)
    void importQuestionsFromWord(MultipartFile file, String subject) throws IOException;
    List<Question> getAllQuestions();
    Optional<Question> getQuestionById(Long id);
    Question updateQuestion(Long id, Question questionDetails);
    void deleteQuestion(Long id);

    // --- ↓↓↓ (新增方法) ↓↓↓ ---
    /**
     * 2. (新增) 获取某学科下的所有套题
     */
    List<QuestionSet> getQuestionSetsBySubject(String subject);

    /**
     * 3. (新增) 根据套题ID获取所有题目 (按顺序)
     */
    List<Question> getQuestionsBySetId(Long setId);
    // --- ↑↑↑ (新增结束) ↑↑↑ ---
}
