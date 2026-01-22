package com.ljh.smarteducation.util;

import org.apache.poi.xwpf.usermodel.*;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 独立的Word文档生成工具主类
 * 直接运行此类的main方法即可生成Word文档
 */
public class GenerateWordDocMain {
    
    public static void main(String[] args) {
        try {
            // 使用项目根目录的Markdown文件 - 尝试多个可能的位置
            String projectDir = System.getProperty("user.dir");
            String[] possiblePaths = {
                "项目功能清单.md",
                projectDir + "\\项目功能清单.md",
                projectDir + "\\..\\项目功能清单.md"
            };
            
            String markdownFile = null;
            for (String path : possiblePaths) {
                if (Files.exists(Paths.get(path))) {
                    markdownFile = path;
                    break;
                }
            }
            
            // 如果还找不到，尝试从类路径查找
            if (markdownFile == null) {
                java.net.URL url = GenerateWordDocMain.class.getResource("/项目功能清单.md");
                if (url != null) {
                    markdownFile = url.getPath();
                    // Windows路径处理
                    if (markdownFile.startsWith("/") && System.getProperty("os.name").toLowerCase().contains("win")) {
                        markdownFile = markdownFile.substring(1);
                    }
                }
            }
            
            String outputFile = "智能教育平台功能清单.docx";
            
            // 获取桌面路径
            String desktopPath = System.getProperty("user.home") + "\\Desktop";
            String outputPath = desktopPath + "\\" + outputFile;
            
            System.out.println("========================================");
            System.out.println("正在生成Word文档...");
            System.out.println("项目目录: " + projectDir);
            
            if (markdownFile == null || !Files.exists(Paths.get(markdownFile))) {
                // 尝试读取项目功能清单.md的内容 - 直接内嵌读取
                System.out.println("尝试直接读取Markdown文件...");
                markdownFile = projectDir + "\\项目功能清单.md";
            }
            
            System.out.println("输入文件: " + markdownFile);
            System.out.println("输出路径: " + outputPath);
            System.out.println("========================================");
            System.out.println("");
            
            // 读取Markdown内容 - 使用UTF-8编码
            String content;
            java.nio.file.Path filePath = Paths.get(markdownFile);
            
            if (!Files.exists(filePath)) {
                // 尝试在项目根目录查找
                filePath = Paths.get(projectDir, "项目功能清单.md");
            }
            
            if (Files.exists(filePath)) {
                content = Files.readString(filePath, java.nio.charset.StandardCharsets.UTF_8);
                System.out.println("✓ 成功读取Markdown文件: " + filePath);
                System.out.println("   文件大小: " + content.length() + " 字符");
            } else {
                System.err.println("错误: 找不到Markdown文件");
                System.err.println("尝试的路径: " + markdownFile);
                System.err.println("当前目录: " + projectDir);
                System.err.println("请确保 '项目功能清单.md' 文件存在于项目根目录");
                return;
            }
            
            // 创建Word文档
            try (XWPFDocument document = new XWPFDocument();
                 FileOutputStream out = new FileOutputStream(outputPath)) {
                
                // 解析并添加内容
                parseContent(document, content);
                
                // 保存文档
                document.write(out);
            }
            
            System.out.println("✓ Word文档已成功生成！");
            System.out.println("保存路径: " + outputPath);
            System.out.println("========================================");
            
        } catch (Exception e) {
            System.err.println("❌ 错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void parseContent(XWPFDocument doc, String content) {
        String[] lines = content.split("\n");
        StringBuilder currentListItem = null;
        boolean inList = false;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            boolean isIndented = line.startsWith("  ") || line.startsWith("\t");
            
            // 跳过空行（除非在列表中）
            if (trimmed.isEmpty() && !inList) {
                continue;
            }
            
            // 处理一级标题
            if (trimmed.startsWith("# ") && !trimmed.startsWith("##")) {
                flushListItem(doc, currentListItem);
                currentListItem = null;
                inList = false;
                addHeading(doc, trimmed.substring(2), 1);
            }
            // 处理二级标题
            else if (trimmed.startsWith("## ") && !trimmed.startsWith("###")) {
                flushListItem(doc, currentListItem);
                currentListItem = null;
                inList = false;
                addHeading(doc, trimmed.substring(3), 2);
            }
            // 处理三级标题
            else if (trimmed.startsWith("### ") && !trimmed.startsWith("####")) {
                flushListItem(doc, currentListItem);
                currentListItem = null;
                inList = false;
                addHeading(doc, trimmed.substring(4), 3);
            }
            // 处理四级标题
            else if (trimmed.startsWith("#### ")) {
                flushListItem(doc, currentListItem);
                currentListItem = null;
                inList = false;
                addHeading(doc, trimmed.substring(5), 4);
            }
            // 处理分隔线
            else if (trimmed.equals("---")) {
                flushListItem(doc, currentListItem);
                currentListItem = null;
                inList = false;
                addSeparator(doc);
            }
            // 处理列表项
            else if (trimmed.startsWith("- ")) {
                flushListItem(doc, currentListItem);
                currentListItem = new StringBuilder(trimmed.substring(2));
                inList = true;
            }
            // 处理列表项的续行（缩进的内容）
            else if (inList && isIndented && !trimmed.isEmpty()) {
                if (currentListItem != null) {
                    currentListItem.append(" ").append(trimmed);
                }
            }
            // 处理普通段落
            else if (!trimmed.startsWith("#")) {
                flushListItem(doc, currentListItem);
                currentListItem = null;
                inList = false;
                if (!trimmed.isEmpty()) {
                    addParagraph(doc, trimmed);
                }
            }
        }
        
        // 刷新最后一个列表项
        flushListItem(doc, currentListItem);
    }
    
    private static void flushListItem(XWPFDocument doc, StringBuilder item) {
        if (item != null && item.length() > 0) {
            addListItem(doc, item.toString());
        }
    }
    
    private static void addHeading(XWPFDocument doc, String text, int level) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        
        // 清理Markdown标记
        text = cleanMarkdown(text);
        run.setText(text);
        run.setBold(true);
        
        // 设置字体大小和颜色
        int[] sizes = {24, 20, 16, 14};
        String[] colors = {"2E75B6", "4472C4", "5B9BD5", "000000"};
        
        if (level >= 1 && level <= 4) {
            run.setFontSize(sizes[level - 1]);
            run.setColor(colors[level - 1]);
        }
        
        // 设置间距
        p.setSpacingBefore(level == 1 ? 400 : 200);
        p.setSpacingAfter(100);
    }
    
    private static void addParagraph(XWPFDocument doc, String text) {
        text = cleanMarkdown(text);
        
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontSize(12);
        run.setFontFamily("微软雅黑");
        p.setSpacingAfter(100);
    }
    
    private static void addListItem(XWPFDocument doc, String text) {
        text = cleanMarkdown(text);
        text = text.replace("✅", "✓");
        
        XWPFParagraph p = doc.createParagraph();
        
        // 添加项目符号
        XWPFRun bullet = p.createRun();
        bullet.setText("• ");
        bullet.setFontSize(12);
        bullet.setBold(true);
        bullet.setColor("2E75B6");
        
        // 添加内容
        XWPFRun content = p.createRun();
        content.setText(text);
        content.setFontSize(11);
        content.setFontFamily("微软雅黑");
        
        // 设置缩进
        p.setIndentationLeft(360);
        p.setFirstLineIndent(-360);
        p.setSpacingAfter(60);
    }
    
    private static void addSeparator(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText("─────────────────────────────────────────────────────────────");
        run.setColor("CCCCCC");
        run.setFontSize(10);
        p.setSpacingBefore(150);
        p.setSpacingAfter(150);
        p.setAlignment(ParagraphAlignment.CENTER);
    }
    
    private static String cleanMarkdown(String text) {
        // 移除粗体标记
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        // 移除代码标记
        text = text.replaceAll("`(.+?)`", "$1");
        return text;
    }
}

