package com.ljh.smarteducation.service;

import java.awt.image.BufferedImage;

/**
 * 图片预处理服务接口 - 提高OCR识别准确率
 */
public interface ImagePreprocessService {
    
    /**
     * 预处理图片（去噪、二值化、增强对比度等）
     * @param image 原始图片
     * @return 预处理后的图片
     */
    BufferedImage preprocessImage(BufferedImage image);
    
    /**
     * 自动旋转图片（纠正倾斜）
     * @param image 原始图片
     * @return 旋转后的图片
     */
    BufferedImage autoRotate(BufferedImage image);
    
    /**
     * 二值化处理（黑白化）
     * @param image 原始图片
     * @return 二值化后的图片
     */
    BufferedImage binarize(BufferedImage image);
    
    /**
     * 去噪处理
     * @param image 原始图片
     * @return 去噪后的图片
     */
    BufferedImage denoise(BufferedImage image);
    
    /**
     * 增强对比度
     * @param image 原始图片
     * @return 增强后的图片
     */
    BufferedImage enhanceContrast(BufferedImage image);
}
