package com.ljh.smarteducation.service;

import com.ljh.smarteducation.entity.AiParseCache;
import com.ljh.smarteducation.entity.DocumentCache;
import com.ljh.smarteducation.entity.OcrCache;

import java.util.Optional;

/**
 * 缓存服务接口
 */
public interface CacheService {
    
    /**
     * 查找文档缓存
     */
    Optional<DocumentCache> findDocumentCache(String fileHash);
    
    /**
     * 保存文档缓存
     */
    void saveDocumentCache(DocumentCache cache);
    
    /**
     * 查找OCR缓存
     */
    Optional<OcrCache> findOcrCache(String imageHash);
    
    /**
     * 保存OCR缓存
     */
    void saveOcrCache(OcrCache cache);
    
    /**
     * 查找AI解析缓存
     */
    Optional<AiParseCache> findAiParseCache(String textHash, String subject);
    
    /**
     * 保存AI解析缓存
     */
    void saveAiParseCache(AiParseCache cache);
    
    /**
     * 清理过期缓存
     */
    void cleanExpiredCaches();
}
