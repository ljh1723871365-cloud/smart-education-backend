package com.ljh.smarteducation.service.impl;

import com.ljh.smarteducation.service.ImagePreprocessService;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

/**
 * 图片预处理服务实现
 */
@Service
public class ImagePreprocessServiceImpl implements ImagePreprocessService {
    
    @Override
    public BufferedImage preprocessImage(BufferedImage image) {
        System.out.println(">>> 开始图片预处理...");
        
        // 1. 转换为灰度图
        BufferedImage grayImage = toGrayscale(image);
        
        // 2. 增强对比度
        BufferedImage enhancedImage = enhanceContrast(grayImage);
        
        // 3. 去噪
        BufferedImage denoisedImage = denoise(enhancedImage);
        
        // 4. 二值化
        BufferedImage binarizedImage = binarize(denoisedImage);
        
        System.out.println(">>> 图片预处理完成");
        return binarizedImage;
    }
    
    @Override
    public BufferedImage autoRotate(BufferedImage image) {
        // 简单实现：检测图片方向并旋转
        // 实际项目中可以使用更复杂的算法
        return image;
    }
    
    @Override
    public BufferedImage binarize(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage binarized = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        
        // 使用Otsu算法计算最佳阈值
        int threshold = calculateOtsuThreshold(image);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int gray = (rgb >> 16) & 0xFF; // 获取灰度值
                
                // 二值化：大于阈值为白色，小于阈值为黑色
                int newRgb = gray > threshold ? 0xFFFFFF : 0x000000;
                binarized.setRGB(x, y, newRgb);
            }
        }
        
        return binarized;
    }
    
    @Override
    public BufferedImage denoise(BufferedImage image) {
        // 使用中值滤波去噪
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage denoised = new BufferedImage(width, height, image.getType());
        
        int radius = 1; // 滤波半径
        
        for (int y = radius; y < height - radius; y++) {
            for (int x = radius; x < width - radius; x++) {
                int[] pixels = new int[(2 * radius + 1) * (2 * radius + 1)];
                int index = 0;
                
                // 收集邻域像素
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int rgb = image.getRGB(x + dx, y + dy);
                        pixels[index++] = (rgb >> 16) & 0xFF; // 灰度值
                    }
                }
                
                // 排序并取中值
                java.util.Arrays.sort(pixels);
                int median = pixels[pixels.length / 2];
                
                int newRgb = (median << 16) | (median << 8) | median;
                denoised.setRGB(x, y, newRgb);
            }
        }
        
        return denoised;
    }
    
    @Override
    public BufferedImage enhanceContrast(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage enhanced = new BufferedImage(width, height, image.getType());
        
        // 计算直方图
        int[] histogram = new int[256];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int gray = (rgb >> 16) & 0xFF;
                histogram[gray]++;
            }
        }
        
        // 直方图均衡化
        int[] cdf = new int[256];
        cdf[0] = histogram[0];
        for (int i = 1; i < 256; i++) {
            cdf[i] = cdf[i - 1] + histogram[i];
        }
        
        int totalPixels = width * height;
        int[] equalized = new int[256];
        for (int i = 0; i < 256; i++) {
            equalized[i] = (int) ((cdf[i] * 255.0) / totalPixels);
        }
        
        // 应用均衡化
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int gray = (rgb >> 16) & 0xFF;
                int newGray = equalized[gray];
                int newRgb = (newGray << 16) | (newGray << 8) | newGray;
                enhanced.setRGB(x, y, newRgb);
            }
        }
        
        return enhanced;
    }
    
    /**
     * 转换为灰度图
     */
    private BufferedImage toGrayscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        
        Graphics2D g = gray.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        
        return gray;
    }
    
    /**
     * 使用Otsu算法计算最佳二值化阈值
     */
    private int calculateOtsuThreshold(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // 计算直方图
        int[] histogram = new int[256];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int gray = (rgb >> 16) & 0xFF;
                histogram[gray]++;
            }
        }
        
        int total = width * height;
        float sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * histogram[i];
        }
        
        float sumB = 0;
        int wB = 0;
        int wF = 0;
        float maxVariance = 0;
        int threshold = 0;
        
        for (int i = 0; i < 256; i++) {
            wB += histogram[i];
            if (wB == 0) continue;
            
            wF = total - wB;
            if (wF == 0) break;
            
            sumB += i * histogram[i];
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;
            
            float variance = wB * wF * (mB - mF) * (mB - mF);
            
            if (variance > maxVariance) {
                maxVariance = variance;
                threshold = i;
            }
        }
        
        return threshold;
    }
}
