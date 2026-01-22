package com.ljh.smarteducation.config;

import com.ljh.smarteducation.util.WordDocumentGenerator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Word文档生成启动器
 * 在Spring Boot应用启动时自动生成Word文档
 * 
 * 使用方式：
 * 1. 启动应用时添加参数: --generate-word 或 --generate-word=true
 * 2. 或者启动应用后，访问接口: GET /api/admin/generate-word
 */
@Component
@Order(100) // 在其他初始化之后执行
public class WordDocumentGeneratorRunner implements CommandLineRunner {

    @Override
    public void run(String... args) throws Exception {
        // 检查是否有生成Word文档的命令行参数
        boolean shouldGenerate = false;
        for (String arg : args) {
            if (arg.equals("--generate-word") || 
                arg.equals("--generate-word=true") || 
                arg.contains("generate-word=true") ||
                arg.contains("--generate.word=true")) {
                shouldGenerate = true;
                break;
            }
        }
        
        if (shouldGenerate) {
            System.out.println("\n========================================");
            System.out.println("正在生成Word文档...");
            System.out.println("========================================\n");
            
            try {
                // 检查Markdown文件是否存在
                String projectDir = System.getProperty("user.dir");
                String markdownFile = projectDir + File.separator + "项目功能清单.md";
                
                if (!Files.exists(Paths.get(markdownFile))) {
                    System.err.println("错误: 找不到文件 " + markdownFile);
                    System.err.println("请确保 '项目功能清单.md' 文件存在于项目根目录");
                    return;
                }
                
                System.out.println("找到Markdown文件: " + markdownFile);
                
                // 生成Word文档
                String outputFile = "智能教育平台功能清单.docx";
                String outputPath = WordDocumentGenerator.generateWordFromMarkdown(markdownFile, outputFile);
                
                System.out.println("\n✓ Word文档已成功生成！");
                System.out.println("保存路径: " + outputPath);
                System.out.println("========================================\n");
                
                // 生成完成后，可以选择退出应用（如果需要）
                // System.exit(0);
                
            } catch (Exception e) {
                System.err.println("❌ 生成Word文档时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}

