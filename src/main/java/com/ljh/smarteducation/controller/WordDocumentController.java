package com.ljh.smarteducation.controller;

import com.ljh.smarteducation.util.WordDocumentGenerator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Word文档生成控制器
 * 提供HTTP接口手动触发Word文档生成
 */
@RestController
@RequestMapping("/api/admin")
public class WordDocumentController {

    /**
     * 手动生成Word文档接口
     * 允许公开访问（已在SecurityConfig中配置）
     */
    @GetMapping("/generate-word")
    public ResponseEntity<Map<String, Object>> generateWordDocument() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 检查Markdown文件是否存在
            String projectDir = System.getProperty("user.dir");
            String markdownFile = projectDir + File.separator + "项目功能清单.md";
            
            if (!Files.exists(Paths.get(markdownFile))) {
                response.put("success", false);
                response.put("message", "找不到文件: " + markdownFile);
                return ResponseEntity.badRequest().body(response);
            }
            
            // 生成Word文档
            String outputFile = "智能教育平台功能清单.docx";
            String outputPath = WordDocumentGenerator.generateWordFromMarkdown(markdownFile, outputFile);
            
            response.put("success", true);
            response.put("message", "Word文档生成成功！");
            response.put("path", outputPath);
            response.put("fileName", outputFile);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "生成Word文档时出错: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}

