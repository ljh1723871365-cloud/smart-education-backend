package com.ljh.smarteducation.service;

import com.ljh.smarteducation.entity.UploadTask;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * 上传任务服务接口 - 异步处理
 */
public interface UploadTaskService {
    
    /**
     * 创建上传任务
     */
    UploadTask createTask(MultipartFile file, String subject);
    
    /**
     * 根据任务ID查询任务
     */
    Optional<UploadTask> getTaskById(String taskId);
    
    /**
     * 更新任务进度
     */
    void updateProgress(String taskId, int progress, String message);
    
    /**
     * 标记任务为处理中
     */
    void markAsProcessing(String taskId);
    
    /**
     * 标记任务为完成
     */
    void markAsCompleted(String taskId, Long questionSetId, Integer questionCount);
    
    /**
     * 标记任务为失败
     */
    void markAsFailed(String taskId, String errorMessage);
    
    /**
     * 获取所有待处理的任务
     */
    List<UploadTask> getPendingTasks();
    
    /**
     * 异步处理上传任务
     */
    void processTaskAsync(String taskId, MultipartFile file, String subject);
}
