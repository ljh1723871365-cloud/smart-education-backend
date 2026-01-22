package com.ljh.smarteducation.repository;

import com.ljh.smarteducation.entity.DocumentCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface DocumentCacheRepository extends JpaRepository<DocumentCache, Long> {
    
    /**
     * 根据文件哈希查找缓存
     */
    Optional<DocumentCache> findByFileHash(String fileHash);
    
    /**
     * 删除过期的缓存
     */
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}
