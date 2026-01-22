package com.ljh.smarteducation.service.impl;

import com.ljh.smarteducation.entity.AiParseCache;
import com.ljh.smarteducation.entity.DocumentCache;
import com.ljh.smarteducation.entity.OcrCache;
import com.ljh.smarteducation.repository.AiParseCacheRepository;
import com.ljh.smarteducation.repository.DocumentCacheRepository;
import com.ljh.smarteducation.repository.OcrCacheRepository;
import com.ljh.smarteducation.service.CacheService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CacheServiceImpl implements CacheService {
    
    private final DocumentCacheRepository documentCacheRepository;
    private final OcrCacheRepository ocrCacheRepository;
    private final AiParseCacheRepository aiParseCacheRepository;
    
    public CacheServiceImpl(DocumentCacheRepository documentCacheRepository,
                           OcrCacheRepository ocrCacheRepository,
                           AiParseCacheRepository aiParseCacheRepository) {
        this.documentCacheRepository = documentCacheRepository;
        this.ocrCacheRepository = ocrCacheRepository;
        this.aiParseCacheRepository = aiParseCacheRepository;
    }
    
    @Override
    public Optional<DocumentCache> findDocumentCache(String fileHash) {
        return documentCacheRepository.findByFileHash(fileHash);
    }
    
    @Override
    @Transactional
    public void saveDocumentCache(DocumentCache cache) {
        documentCacheRepository.save(cache);
    }
    
    @Override
    public Optional<OcrCache> findOcrCache(String imageHash) {
        return ocrCacheRepository.findByImageHash(imageHash);
    }
    
    @Override
    @Transactional
    public void saveOcrCache(OcrCache cache) {
        ocrCacheRepository.save(cache);
    }
    
    @Override
    public Optional<AiParseCache> findAiParseCache(String textHash, String subject) {
        return aiParseCacheRepository.findByTextHashAndSubject(textHash, subject);
    }
    
    @Override
    @Transactional
    public void saveAiParseCache(AiParseCache cache) {
        aiParseCacheRepository.save(cache);
    }
    
    @Override
    @Transactional
    public void cleanExpiredCaches() {
        LocalDateTime now = LocalDateTime.now();
        System.out.println(">>> 开始清理过期缓存...");
        
        documentCacheRepository.deleteByExpiresAtBefore(now);
        ocrCacheRepository.deleteByExpiresAtBefore(now);
        aiParseCacheRepository.deleteByExpiresAtBefore(now);
        
        System.out.println(">>> 过期缓存清理完成");
    }
}
