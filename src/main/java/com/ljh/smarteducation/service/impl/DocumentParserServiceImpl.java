package com.ljh.smarteducation.service.impl;

import com.ljh.smarteducation.entity.OcrCache;
import com.ljh.smarteducation.service.CacheService;
import com.ljh.smarteducation.service.DocumentParserService;
import com.ljh.smarteducation.service.ImagePreprocessService;
import com.ljh.smarteducation.util.FileHashUtil;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@Service
public class DocumentParserServiceImpl implements DocumentParserService {
    
    private final CacheService cacheService;
    private final ImagePreprocessService imagePreprocessService;
    
    @Value("${ocr.cache.enabled:true}")
    private boolean ocrCacheEnabled;
    
    @Value("${ocr.preprocess.enabled:true}")
    private boolean preprocessEnabled;
    
    public DocumentParserServiceImpl(CacheService cacheService, 
                                    ImagePreprocessService imagePreprocessService) {
        this.cacheService = cacheService;
        this.imagePreprocessService = imagePreprocessService;
    }

    @Override
    public String parseDocument(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IOException("文件名为空");
        }

        String extension = getFileExtension(filename).toLowerCase();
        
        System.out.println(">>> 开始解析文件: " + filename + " (类型: " + extension + ")");
        
        try {
            switch (extension) {
                case "docx":
                case "doc":
                    return parseWord(file);
                case "pdf":
                    return parsePdf(file);
                case "jpg":
                case "jpeg":
                case "png":
                case "bmp":
                case "tiff":
                case "gif":
                    return parseImage(file);
                default:
                    throw new IOException("不支持的文件格式: " + extension + "。支持的格式: Word (.docx, .doc), PDF (.pdf), 图片 (.jpg, .jpeg, .png, .bmp, .tiff, .gif)");
            }
        } catch (Exception e) {
            System.err.println(">>> 文件解析失败: " + e.getMessage());
            throw new IOException("文件解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析Word文档
     */
    private String parseWord(MultipartFile file) throws IOException {
        System.out.println(">>> 使用 Apache POI 解析 Word 文档");
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            System.out.println(">>> Word 解析成功，提取文本长度: " + text.length() + " 字符");
            return text;
        }
    }

    /**
     * 解析PDF文档
     */
    private String parsePdf(MultipartFile file) throws IOException {
        System.out.println(">>> 使用 Apache PDFBox 解析 PDF 文档");
        try (InputStream is = file.getInputStream();
             PDDocument document = PDDocument.load(is)) {
            
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            
            System.out.println(">>> PDF 解析成功，页数: " + document.getNumberOfPages() + "，提取文本长度: " + text.length() + " 字符");
            return text;
        }
    }

    /**
     * 解析图片（使用OCR，支持缓存和预处理）
     */
    private String parseImage(MultipartFile file) throws IOException {
        long startTime = System.currentTimeMillis();
        String fileName = file.getOriginalFilename();
        
        // 1. 计算图片哈希值
        String imageHash = FileHashUtil.calculateMD5(file);
        System.out.println(">>> 图片哈希: " + imageHash);
        
        // 2. 检查缓存
        if (ocrCacheEnabled) {
            Optional<OcrCache> cachedResult = cacheService.findOcrCache(imageHash);
            if (cachedResult.isPresent()) {
                System.out.println(">>> ✅ 命中OCR缓存，直接返回结果");
                System.out.println(">>> 缓存命中节省时间: " + cachedResult.get().getProcessingTimeMs() + "ms");
                return cachedResult.get().getRecognizedText();
            }
            System.out.println(">>> 未命中缓存，开始OCR识别...");
        }
        
        System.out.println(">>> 使用 Tesseract OCR 识别图片文字");
        
        // 创建临时文件
        Path tempFile = Files.createTempFile("ocr-", "-" + fileName);
        
        try {
            // 保存上传的文件到临时位置
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            // 读取图片
            BufferedImage image = ImageIO.read(tempFile.toFile());
            if (image == null) {
                throw new IOException("无法读取图片文件");
            }
            
            // 3. 图片预处理（提高识别准确率）
            if (preprocessEnabled) {
                System.out.println(">>> 执行图片预处理（去噪、二值化、增强对比度）...");
                image = imagePreprocessService.preprocessImage(image);
            }
            
            // 配置 Tesseract
            Tesseract tesseract = new Tesseract();
            
            // 设置语言（中文+英文）
            tesseract.setLanguage("chi_sim+eng");
            
            // 优化OCR参数
            tesseract.setPageSegMode(1); // 自动页面分割
            tesseract.setOcrEngineMode(1); // 使用LSTM引擎（更准确）
            
            // 设置 Tesseract 数据路径（需要下载语言包）
            // 默认路径通常是: C:\Program Files\Tesseract-OCR\tessdata
            // 如果需要自定义路径，取消下面的注释
            // tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
            
            // 执行OCR
            String text = tesseract.doOCR(image);
            
            long processingTime = System.currentTimeMillis() - startTime;
            System.out.println(">>> OCR 识别成功，提取文本长度: " + text.length() + " 字符");
            System.out.println(">>> OCR 耗时: " + processingTime + "ms");
            
            // 4. 保存到缓存
            if (ocrCacheEnabled) {
                OcrCache cache = new OcrCache(
                    imageHash,
                    text,
                    fileName,
                    file.getSize(),
                    "tesseract",
                    processingTime
                );
                cacheService.saveOcrCache(cache);
                System.out.println(">>> OCR结果已缓存");
            }
            
            return text;
            
        } catch (TesseractException e) {
            System.err.println(">>> OCR 识别失败: " + e.getMessage());
            throw new IOException("OCR识别失败。请确保已安装 Tesseract-OCR 并配置了中英文语言包。\n" +
                    "安装指南: https://github.com/tesseract-ocr/tesseract\n" +
                    "错误详情: " + e.getMessage(), e);
        } finally {
            // 删除临时文件
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                System.err.println(">>> 删除临时文件失败: " + e.getMessage());
            }
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }
}
