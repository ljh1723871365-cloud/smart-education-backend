package com.ljh.smarteducation.controller;

import com.ljh.smarteducation.entity.Question;
import com.ljh.smarteducation.entity.QuestionSet;
import com.ljh.smarteducation.entity.ResourceFile;
import com.ljh.smarteducation.repository.QuestionSetRepository;
import com.ljh.smarteducation.repository.ResourceFileRepository;
import com.ljh.smarteducation.service.QuestionBankService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/questions") // 统一了资源路径为 "questions"
@CrossOrigin(origins = "http://localhost:5173")
@PreAuthorize("hasRole('ADMIN')") // 重新启用，与其他 Controller 保持一致
public class QuestionBankController {

    private final QuestionBankService questionBankService;
    
    public QuestionBankController(QuestionBankService questionBankService) {
        this.questionBankService = questionBankService;
    }

    // --- (自动化录入接口保持不变，但路径稍作调整以符合RESTful风格) ---
    @PostMapping("/upload")
    public ResponseEntity<String> uploadQuestionBank(
            @RequestParam("file") MultipartFile file,
            @RequestParam("subject") String subject) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please upload a file.");
        }
        try {
            questionBankService.importQuestionsFromWord(file, subject);
            return ResponseEntity.ok("File uploaded and processed successfully.");
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to parse Word file: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("An error occurred during processing: " + e.getMessage());
        }
    }

    // --- ↓↓↓ 以下是新增的CRUD API端点 ↓↓↓ ---

    /**
     * GET /api/admin/questions - 获取所有问题
     */
    @GetMapping
    public ResponseEntity<List<Question>> getAllQuestions() {
        System.out.println(">>> [QuestionBankController] getAllQuestions 被调用");
        System.out.println(">>> [QuestionBankController] SecurityContext认证状态: " + (org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null ? "已设置" : "未设置"));
        if (org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null) {
            System.out.println(">>> [QuestionBankController] 当前认证对象: " + org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
            System.out.println(">>> [QuestionBankController] 用户权限: " + org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getAuthorities());
        }
        List<Question> questions = questionBankService.getAllQuestions();
        System.out.println(">>> [QuestionBankController] 返回 " + questions.size() + " 个问题");
        return ResponseEntity.ok(questions);
    }

    /**
     * GET /api/admin/questions/{id} - 获取单个问题
     */
    @GetMapping("/{id}")
    public ResponseEntity<Question> getQuestionById(@PathVariable Long id) {
        return questionBankService.getQuestionById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * PUT /api/admin/questions/{id} - 更新一个问题
     */
    @PutMapping("/{id}")
    public ResponseEntity<Question> updateQuestion(@PathVariable Long id, @RequestBody Question questionDetails) {
        try {
            Question updatedQuestion = questionBankService.updateQuestion(id, questionDetails);
            return ResponseEntity.ok(updatedQuestion);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * DELETE /api/admin/questions/{id} - 删除一个问题
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id) {
        try {
            questionBankService.deleteQuestion(id);
            return ResponseEntity.noContent().build(); // 204 No Content 是删除成功的标准HTTP响应
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}