package com.ljh.smarteducation.service.impl;

import com.ljh.smarteducation.entity.UploadTask;
import com.ljh.smarteducation.repository.UploadTaskRepository;
import com.ljh.smarteducation.service.QuestionBankService;
import com.ljh.smarteducation.service.UploadTaskService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UploadTaskServiceImpl implements UploadTaskService {
    
    private final UploadTaskRepository uploadTaskRepository;
    private final QuestionBankService questionBankService;
    
    public UploadTaskServiceImpl(UploadTaskRepository uploadTaskRepository,
                                QuestionBankService questionBankService) {
        this.uploadTaskRepository = uploadTaskRepository;
        this.questionBankService = questionBankService;
    }
    
    @Override
    @Transactional
    public UploadTask createTask(MultipartFile file, String subject) {
        String taskId = UUID.randomUUID().toString();
        UploadTask task = new UploadTask(
            taskId,
            file.getOriginalFilename(),
            file.getSize(),
            subject
        );
        return uploadTaskRepository.save(task);
    }
    
    @Override
    public Optional<UploadTask> getTaskById(String taskId) {
        return uploadTaskRepository.findByTaskId(taskId);
    }
    
    @Override
    @Transactional
    public void updateProgress(String taskId, int progress, String message) {
        Optional<UploadTask> taskOpt = uploadTaskRepository.findByTaskId(taskId);
        if (taskOpt.isPresent()) {
            UploadTask task = taskOpt.get();
            task.setProgress(progress);
            task.setProgressMessage(message);
            uploadTaskRepository.save(task);
        }
    }
    
    @Override
    @Transactional
    public void markAsProcessing(String taskId) {
        Optional<UploadTask> taskOpt = uploadTaskRepository.findByTaskId(taskId);
        if (taskOpt.isPresent()) {
            UploadTask task = taskOpt.get();
            task.setStatus("PROCESSING");
            task.setStartedAt(LocalDateTime.now());
            task.setProgress(0);
            task.setProgressMessage("开始处理文档...");
            uploadTaskRepository.save(task);
        }
    }
    
    @Override
    @Transactional
    public void markAsCompleted(String taskId, Long questionSetId, Integer questionCount) {
        Optional<UploadTask> taskOpt = uploadTaskRepository.findByTaskId(taskId);
        if (taskOpt.isPresent()) {
            UploadTask task = taskOpt.get();
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setProgressMessage("处理完成");
            task.setQuestionSetId(questionSetId);
            task.setQuestionCount(questionCount);
            task.setCompletedAt(LocalDateTime.now());
            
            if (task.getStartedAt() != null) {
                long totalTime = java.time.Duration.between(task.getStartedAt(), task.getCompletedAt()).toMillis();
                task.setTotalTimeMs(totalTime);
            }
            
            uploadTaskRepository.save(task);
        }
    }
    
    @Override
    @Transactional
    public void markAsFailed(String taskId, String errorMessage) {
        Optional<UploadTask> taskOpt = uploadTaskRepository.findByTaskId(taskId);
        if (taskOpt.isPresent()) {
            UploadTask task = taskOpt.get();
            task.setStatus("FAILED");
            task.setErrorMessage(errorMessage);
            task.setCompletedAt(LocalDateTime.now());
            
            if (task.getStartedAt() != null) {
                long totalTime = java.time.Duration.between(task.getStartedAt(), task.getCompletedAt()).toMillis();
                task.setTotalTimeMs(totalTime);
            }
            
            uploadTaskRepository.save(task);
        }
    }
    
    @Override
    public List<UploadTask> getPendingTasks() {
        return uploadTaskRepository.findByStatusOrderByCreatedAtAsc("PENDING");
    }
    
    @Override
    @Async("taskExecutor")
    public void processTaskAsync(String taskId, MultipartFile file, String subject) {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("🚀 异步任务开始: " + taskId);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        File tempFile = null;
        try {
            // 标记为处理中
            markAsProcessing(taskId);
            
            // ⚠️ 重要：MultipartFile在异步方法中可能失效，需要先保存到临时文件
            tempFile = File.createTempFile("upload-", "-" + file.getOriginalFilename());
            file.transferTo(tempFile);
            System.out.println(">>> 文件已保存到临时位置: " + tempFile.getAbsolutePath());
            
            // 创建一个新的MultipartFile包装临时文件
            MultipartFile tempMultipartFile = new org.springframework.mock.web.MockMultipartFile(
                file.getName(),
                file.getOriginalFilename(),
                file.getContentType(),
                new java.io.FileInputStream(tempFile)
            );
            
            // 更新进度：文档解析
            updateProgress(taskId, 10, "正在解析文档...");
            
            // 调用原有的导入逻辑
            questionBankService.importQuestionsFromWord(tempMultipartFile, subject);
            
            // 更新进度：完成
            updateProgress(taskId, 90, "正在保存数据...");
            
            // 获取最新创建的套题ID（简化处理，实际应该从importQuestionsFromWord返回）
            // 这里需要修改QuestionBankService接口返回套题ID
            markAsCompleted(taskId, null, null);
            
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("✅ 异步任务完成: " + taskId);
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
        } catch (Exception e) {
            System.err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.err.println("❌ 异步任务失败: " + taskId);
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
            System.err.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            markAsFailed(taskId, e.getMessage());
        } finally {
            // 清理临时文件
            if (tempFile != null && tempFile.exists()) {
                try {
                    tempFile.delete();
                    System.out.println(">>> 临时文件已删除: " + tempFile.getAbsolutePath());
                } catch (Exception e) {
                    System.err.println(">>> 删除临时文件失败: " + e.getMessage());
                }
            }
        }
    }
}
