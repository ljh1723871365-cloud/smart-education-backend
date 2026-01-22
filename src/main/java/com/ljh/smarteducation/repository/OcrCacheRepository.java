package com.ljh.smarteducation.repository;

import com.ljh.smarteducation.entity.OcrCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OcrCacheRepository extends JpaRepository<OcrCache, Long> {
    
    /**
     * 根据图片哈希查找缓存
     */
    Optional<OcrCache> findByImageHash(String imageHash);
    
    /**
     * 删除过期的缓存
     */
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}
