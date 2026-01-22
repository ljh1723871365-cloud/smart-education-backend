package com.ljh.smarteducation.controller;

import com.ljh.smarteducation.entity.Question;
import com.ljh.smarteducation.entity.QuestionSet;
import com.ljh.smarteducation.entity.UploadTask;
import com.ljh.smarteducation.repository.QuestionBankRepository;
import com.ljh.smarteducation.repository.QuestionSetRepository;
import com.ljh.smarteducation.service.QuestionBankService;
import com.ljh.smarteducation.service.UploadTaskService;
import com.ljh.smarteducation.util.InputValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/questions") // 统一了资源路径为 "questions"
@CrossOrigin(origins = "http://localhost:5173")
@PreAuthorize("hasRole('ADMIN')") // 重新启用，与其他 Controller 保持一致
public class QuestionBankController {

    private final QuestionBankService questionBankService;
    private final QuestionSetRepository questionSetRepository;
    private final QuestionBankRepository questionBankRepository;
    private final UploadTaskService uploadTaskService;

    public QuestionBankController(QuestionBankService questionBankService,
                                   QuestionSetRepository questionSetRepository,
                                   QuestionBankRepository questionBankRepository,
                                   UploadTaskService uploadTaskService) {
        this.questionBankService = questionBankService;
        this.questionSetRepository = questionSetRepository;
        this.questionBankRepository = questionBankRepository;
        this.uploadTaskService = uploadTaskService;
    }

    // --- 同步上传接口（保留用于小文件或测试） ---
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

    /**
     * 异步上传接口 - 立即返回任务ID，后台处理
     */
    @PostMapping("/upload/async")
    public ResponseEntity<?> uploadQuestionBankAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam("subject") String subject) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please upload a file.");
        }

        try {
            // 创建任务
            UploadTask task = uploadTaskService.createTask(file, subject);

            // 异步处理
            uploadTaskService.processTaskAsync(task.getTaskId(), file, subject);

            // 立即返回任务ID
            return ResponseEntity.ok(new java.util.HashMap<String, Object>() {{
                put("taskId", task.getTaskId());
                put("status", task.getStatus());
                put("message", "任务已创建，正在后台处理...");
            }});
        } catch (Exception e) {
            return ResponseEntity.status(500).body("创建任务失败: " + e.getMessage());
        }
    }

    /**
     * 查询任务进度
     */
    @GetMapping("/upload/task/{taskId}")
    public ResponseEntity<?> getTaskStatus(@PathVariable String taskId) {
        return uploadTaskService.getTaskById(taskId)
                .map(task -> {
                    java.util.Map<String, Object> response = new java.util.HashMap<>();
                    response.put("taskId", task.getTaskId());
                    response.put("status", task.getStatus());
                    response.put("progress", task.getProgress());
                    response.put("progressMessage", task.getProgressMessage());
                    response.put("fileName", task.getFileName());
                    response.put("questionSetId", task.getQuestionSetId());
                    response.put("questionCount", task.getQuestionCount());
                    response.put("errorMessage", task.getErrorMessage());
                    response.put("createdAt", task.getCreatedAt());
                    response.put("completedAt", task.getCompletedAt());
                    response.put("totalTimeMs", task.getTotalTimeMs());
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

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

    /**
     * DELETE /api/admin/questions/sets/duplicates - 删除重复的套题
     * 根据标题删除重复的套题，保留最新的一个（按上传时间）
     */
    @DeleteMapping("/sets/duplicates")
    @Transactional
    public ResponseEntity<?> deleteDuplicateQuestionSets(@RequestParam String title) {
        // 输入验证：验证标题格式
        if (!InputValidator.isValidTitle(title)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "标题格式不正确", "code", "INVALID_TITLE"));
        }
        
        try {
            // 查找所有相同标题的套题
            List<QuestionSet> duplicateSets = questionSetRepository.findByTitle(title);
            
            if (duplicateSets.size() <= 1) {
                return ResponseEntity.ok("没有找到重复的套题，或只有一个套题。");
            }
            
            // 按上传时间排序，保留最新的一个
            List<QuestionSet> sortedSets = duplicateSets.stream()
                    .sorted(Comparator.comparing(QuestionSet::getUploadTime).reversed())
                    .collect(Collectors.toList());
            
            QuestionSet keepSet = sortedSets.get(0); // 保留最新的
            List<QuestionSet> toDelete = sortedSets.subList(1, sortedSets.size()); // 删除其他的
            
            int deletedCount = 0;
            for (QuestionSet set : toDelete) {
                // 先删除关联的题目
                questionBankRepository.deleteByQuestionSetIdInBulk(List.of(set.getId()));
                // 再删除套题
                questionSetRepository.delete(set);
                deletedCount++;
            }
            
            return ResponseEntity.ok(String.format("成功删除 %d 个重复的套题，保留了最新的一个（ID: %d）", 
                    deletedCount, keepSet.getId()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("删除重复套题时出错: " + e.getMessage());
        }
    }

    /**
     * GET /api/admin/questions/upload/supported-types
     * 返回当前后端支持的上传文件类型及状态，供前端展示提示文案。
     */
    @GetMapping("/upload/supported-types")
    public ResponseEntity<java.util.Map<String, Object>> getSupportedUploadTypes() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();

        result.put("docx", java.util.Map.of(
                "status", "supported",
                "note", "DOCX fully supported"
        ));

        result.put("pdf", java.util.Map.of(
                "status", "experimental",
                "note", "Simple text-based PDFs only; complex PDFs may fail"
        ));

        result.put("image", java.util.Map.of(
                "status", "unsupported",
                "note", "OCR image extraction not implemented yet"
        ));

        return ResponseEntity.ok(result);
    }
}