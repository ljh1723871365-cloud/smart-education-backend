package com.ljh.smarteducation.service;

import java.util.List;

/**
 * 文档分段处理服务接口
 * 将大文档拆分成小段，避免AI输出截断
 */
public interface DocumentSegmentService {
    /**
     * 将文档文本分段
     * @param fullText 完整文档文本
     * @param maxCharsPerSegment 每段最大字符数
     * @return 分段后的文本列表
     */
    List<String> segmentDocument(String fullText, int maxCharsPerSegment);
    
    /**
     * 智能分段（按题目边界分割）
     * @param fullText 完整文档文本
     * @return 分段后的文本列表
     */
    List<String> smartSegmentByQuestions(String fullText);
}
