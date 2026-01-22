package com.ljh.smarteducation.repository;

import com.ljh.smarteducation.entity.UploadTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UploadTaskRepository extends JpaRepository<UploadTask, Long> {
    
    /**
     * 根据任务ID查找
     */
    Optional<UploadTask> findByTaskId(String taskId);
    
    /**
     * 查找所有待处理的任务
     */
    List<UploadTask> findByStatusOrderByCreatedAtAsc(String status);
    
    /**
     * 删除旧任务（清理历史记录）
     */
    void deleteByCreatedAtBefore(LocalDateTime dateTime);
}
