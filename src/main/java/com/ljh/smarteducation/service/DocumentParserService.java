package com.ljh.smarteducation.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

/**
 * 文档解析服务接口 - 支持Word、PDF和图片
 */
public interface DocumentParserService {
    /**
     * 解析文档并提取文本
     * @param file 上传的文件（Word、PDF或图片）
     * @return 提取的文本内容
     * @throws IOException 解析失败时抛出异常
     */
    String parseDocument(MultipartFile file) throws IOException;
}
