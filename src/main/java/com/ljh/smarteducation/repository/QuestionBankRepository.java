package com.ljh.smarteducation.repository;

import com.ljh.smarteducation.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionBankRepository extends JpaRepository<Question, Long> {
    // Spring Data JPA 会自动提供 save, findById, findAll, deleteById 等方法
    // 对于 JSON 字段，这些基本方法通常可以直接工作

    // --- ↓↓↓ (新增方法) ↓↓↓ ---
    /**
     * 查找某一套试卷下的所有题目，并按序号升序排列
     */
    List<Question> findByQuestionSetIdOrderBySequenceNumberAsc(Long questionSetId);

    /**
     * 根据套题ID列表，查找所有关联的题目
     */
    List<Question> findByQuestionSetIdIn(List<Long> questionSetIds);

    // --- ↓↓↓ (核心修改) 使用 @Modifying 和 @Query 来确保执行删除操作 ---
    @Modifying
    @Query("DELETE FROM Question q WHERE q.questionSet.id IN :questionSetIds")
    void deleteByQuestionSetIdInBulk(@Param("questionSetIds") List<Long> questionSetIds);
    // --- ↑↑↑ (修改结束) ↑↑↑ ---
}