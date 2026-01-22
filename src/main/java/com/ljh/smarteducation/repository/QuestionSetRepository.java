package com.ljh.smarteducation.repository;

import com.ljh.smarteducation.entity.QuestionSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionSetRepository extends JpaRepository<QuestionSet, Long> {
    
    // 查找某个学科下的所有套题
    List<QuestionSet> findBySubject(String subject);
    
    // 根据标题查找所有套题（用于查找重复）
    List<QuestionSet> findByTitle(String title);
}
