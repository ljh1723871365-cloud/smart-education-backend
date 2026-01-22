package com.ljh.smarteducation.repository;

import com.ljh.smarteducation.entity.AiParseCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AiParseCacheRepository extends JpaRepository<AiParseCache, Long> {
    
    /**
     * 根据文本哈希和科目查找缓存
     */
    Optional<AiParseCache> findByTextHashAndSubject(String textHash, String subject);
    
    /**
     * 删除过期的缓存
     */
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}
