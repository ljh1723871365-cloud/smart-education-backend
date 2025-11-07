package com.ljh.smarteducation.repository;

import com.ljh.smarteducation.entity.StudentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentSubmissionRepository extends JpaRepository<StudentSubmission, Long> {
    
    // 查找所有未被人工批改的答卷
    List<StudentSubmission> findByGraded(Boolean graded);

    // --- ↓↓↓ (新增) 用于统计待批改数量的方法 ↓↓↓ ---
    long countByGraded(Boolean graded);
    // --- ↑↑↑ (新增) 方法结束 ↑↑↑ ---
}
