package com.ljh.smarteducation.controller;

import com.ljh.smarteducation.entity.StudentSubmission;
import com.ljh.smarteducation.repository.StudentSubmissionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/grading")
@CrossOrigin(origins = "http://localhost:5173") // 仅允许后台管理端
@PreAuthorize("hasRole('ADMIN')") // 确保只有管理员能访问
public class GradingController {

    private final StudentSubmissionRepository submissionRepository;

    public GradingController(StudentSubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    /**
     * GET /api/admin/grading/pending - 获取所有待批改的答卷
     */
    @GetMapping("/pending")
    public ResponseEntity<List<StudentSubmission>> getPendingSubmissions() {
        // 查找所有 graded = false 的答卷
        List<StudentSubmission> pending = submissionRepository.findByGraded(false);
        return ResponseEntity.ok(pending);
    }

    /**
     * POST /api/admin/grading/{submissionId} - 提交人工批改结果
     */
    @PostMapping("/{submissionId}")
    public ResponseEntity<StudentSubmission> gradeSubmission(
            @PathVariable Long submissionId,
            @RequestBody Map<String, Object> payload) {
        
        StudentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        // 从请求体中获取评语和分数
        String feedback = (String) payload.get("feedback");
        Integer manualScore = (Integer) payload.get("manualScore");

        submission.setFeedback(feedback);
        submission.setManualScore(manualScore);
        submission.setGraded(true); // 标记为已批改

        StudentSubmission savedSubmission = submissionRepository.save(submission);
        return ResponseEntity.ok(savedSubmission);
    }
}
