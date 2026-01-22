package com.ljh.smarteducation.util;

import org.apache.poi.xwpf.usermodel.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Word文档生成工具
 * 用于将Markdown格式的内容转换为格式美观的Word文档
 */
public class WordDocumentGenerator {

    /**
     * 从Markdown文件生成Word文档并保存到桌面
     */
    public static String generateWordFromMarkdown(String markdownFilePath, String outputFileName) throws IOException {
        // 尝试多个可能的路径
        java.nio.file.Path markdownPath = null;
        
        // 尝试1: 直接路径
        if (Files.exists(Paths.get(markdownFilePath))) {
            markdownPath = Paths.get(markdownFilePath);
        } else {
            // 尝试2: 相对于项目根目录
            String projectDir = System.getProperty("user.dir");
            java.nio.file.Path projectPath = Paths.get(projectDir, markdownFilePath);
            if (Files.exists(projectPath)) {
                markdownPath = projectPath;
            } else {
                // 尝试3: 项目根目录下的文件名
                java.nio.file.Path rootPath = Paths.get(projectDir, "项目功能清单.md");
                if (Files.exists(rootPath)) {
                    markdownPath = rootPath;
                }
            }
        }
        
        if (markdownPath == null || !Files.exists(markdownPath)) {
            throw new IOException("找不到Markdown文件: " + markdownFilePath + "\n当前目录: " + System.getProperty("user.dir"));
        }
        
        // 读取Markdown文件内容
        String content = Files.readString(markdownPath, java.nio.charset.StandardCharsets.UTF_8);
        
        // 获取桌面路径
        String desktopPath = getDesktopPath();
        String outputPath = desktopPath + "\\" + outputFileName;
        
        System.out.println(">>> 读取Markdown文件: " + markdownPath);
        System.out.println(">>> 文件大小: " + content.length() + " 字符");
        System.out.println(">>> 将保存到: " + outputPath);
        
        // 创建Word文档
        try (XWPFDocument document = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(outputPath)) {
            
            // 解析并添加内容
            parseMarkdownToWord(document, content);
            
            // 保存文档
            document.write(out);
            System.out.println(">>> Word文档已成功保存！");
        }
        
        return outputPath;
    }

    /**
     * 获取Windows桌面路径
     */
    private static String getDesktopPath() {
        String userHome = System.getProperty("user.home");
        return userHome + "\\Desktop";
    }

    /**
     * 解析Markdown内容并添加到Word文档
     */
    private static void parseMarkdownToWord(XWPFDocument document, String content) {
        String[] lines = content.split("\n");
        List<String> listItems = new ArrayList<>();
        boolean inList = false;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmedLine = line.trim();
            
            // 跳过空行（除非是在列表内部）
            if (trimmedLine.isEmpty() && !inList) {
                continue;
            }
            
            // 处理一级标题
            if (trimmedLine.startsWith("# ")) {
                flushListItems(document, listItems);
                listItems.clear();
                inList = false;
                addHeading(document, trimmedLine.substring(2), 1);
                continue;
            }
            
            // 处理二级标题
            if (trimmedLine.startsWith("## ") && !trimmedLine.startsWith("###")) {
                flushListItems(document, listItems);
                listItems.clear();
                inList = false;
                addHeading(document, trimmedLine.substring(3), 2);
                continue;
            }
            
            // 处理三级标题
            if (trimmedLine.startsWith("### ") && !trimmedLine.startsWith("####")) {
                flushListItems(document, listItems);
                listItems.clear();
                inList = false;
                addHeading(document, trimmedLine.substring(4), 3);
                continue;
            }
            
            // 处理四级标题
            if (trimmedLine.startsWith("#### ")) {
                flushListItems(document, listItems);
                listItems.clear();
                inList = false;
                addHeading(document, trimmedLine.substring(5), 4);
                continue;
            }
            
            // 处理分隔线
            if (trimmedLine.equals("---")) {
                flushListItems(document, listItems);
                listItems.clear();
                inList = false;
                addSeparator(document);
                continue;
            }
            
            // 处理列表项
            if (trimmedLine.startsWith("- ")) {
                inList = true;
                String itemText = trimmedLine.substring(2);
                // 检查是否有续行（下一行是缩进的）
                if (i + 1 < lines.length) {
                    String nextLine = lines[i + 1];
                    if (nextLine.startsWith("  ") && !nextLine.trim().startsWith("-")) {
                        itemText += " " + nextLine.trim();
                        i++; // 跳过下一行
                        // 继续检查更多续行
                        while (i + 1 < lines.length && lines[i + 1].startsWith("  ") 
                               && !lines[i + 1].trim().startsWith("-") 
                               && !lines[i + 1].trim().isEmpty()) {
                            itemText += " " + lines[i + 1].trim();
                            i++;
                        }
                    }
                }
                listItems.add(itemText);
                continue;
            }
            
            // 如果遇到非列表行，刷新列表
            if (inList && !trimmedLine.isEmpty() && !trimmedLine.startsWith("-")) {
                flushListItems(document, listItems);
                listItems.clear();
                inList = false;
            }
            
            // 处理普通段落（非列表、非标题）
            if (!inList && !trimmedLine.isEmpty() && !trimmedLine.startsWith("#") 
                && !trimmedLine.equals("---")) {
                addParagraph(document, trimmedLine);
            }
        }
        
        // 刷新剩余的列表项
        flushListItems(document, listItems);
    }

    /**
     * 刷新列表项到文档
     */
    private static void flushListItems(XWPFDocument document, List<String> items) {
        for (String item : items) {
            addListItem(document, item);
        }
    }

    /**
     * 添加标题
     */
    private static void addHeading(XWPFDocument document, String text, int level) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        
        // 清理文本（移除Markdown标记）
        text = cleanMarkdown(text);
        
        run.setText(text);
        run.setBold(true);
        
        // 设置字体大小和颜色
        switch (level) {
            case 1:
                run.setFontSize(24);
                run.setColor("2E75B6"); // 深蓝色
                paragraph.setSpacingBefore(400);
                paragraph.setSpacingAfter(200);
                break;
            case 2:
                run.setFontSize(20);
                run.setColor("4472C4"); // 蓝色
                paragraph.setSpacingBefore(300);
                paragraph.setSpacingAfter(150);
                break;
            case 3:
                run.setFontSize(16);
                run.setColor("5B9BD5"); // 浅蓝色
                paragraph.setSpacingBefore(200);
                paragraph.setSpacingAfter(100);
                break;
            case 4:
                run.setFontSize(14);
                run.setColor("000000"); // 黑色
                paragraph.setSpacingBefore(150);
                paragraph.setSpacingAfter(80);
                break;
        }
        
        paragraph.setAlignment(ParagraphAlignment.LEFT);
    }

    /**
     * 添加段落
     */
    private static void addParagraph(XWPFDocument document, String text) {
        if (text.trim().isEmpty()) {
            return;
        }
        
        XWPFParagraph paragraph = document.createParagraph();
        
        // 处理包含格式的文本
        addFormattedText(paragraph, text);
        
        paragraph.setSpacingAfter(100);
        paragraph.setAlignment(ParagraphAlignment.LEFT);
    }

    /**
     * 添加带格式的文本（支持粗体、代码等）
     */
    private static void addFormattedText(XWPFParagraph paragraph, String text) {
        // 处理粗体 **text**
        Pattern pattern = Pattern.compile("(\\*\\*.*?\\*\\*|`.*?`|.)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        
        int lastEnd = 0;
        
        while (matcher.find()) {
            String match = matcher.group(1);
            int start = matcher.start();
            
            // 添加粗体前的普通文本
            if (start > lastEnd) {
                XWPFRun normalRun = paragraph.createRun();
                normalRun.setText(text.substring(lastEnd, start));
                normalRun.setFontSize(12);
                normalRun.setFontFamily("微软雅黑");
            }
            
            // 处理粗体
            if (match.startsWith("**") && match.endsWith("**")) {
                XWPFRun boldRun = paragraph.createRun();
                boldRun.setText(match.substring(2, match.length() - 2));
                boldRun.setBold(true);
                boldRun.setFontSize(12);
                boldRun.setFontFamily("微软雅黑");
            }
            // 处理代码（反引号）
            else if (match.startsWith("`") && match.endsWith("`") && match.length() > 2) {
                XWPFRun codeRun = paragraph.createRun();
                codeRun.setText(match.substring(1, match.length() - 1));
                codeRun.setFontFamily("Consolas");
                codeRun.setFontSize(11);
                codeRun.setColor("D32F2F"); // 红色
            }
            // 普通字符
            else {
                XWPFRun normalRun = paragraph.createRun();
                normalRun.setText(match);
                normalRun.setFontSize(12);
                normalRun.setFontFamily("微软雅黑");
            }
            
            lastEnd = matcher.end();
        }
        
        // 添加剩余文本
        if (lastEnd < text.length()) {
            XWPFRun normalRun = paragraph.createRun();
            normalRun.setText(text.substring(lastEnd));
            normalRun.setFontSize(12);
            normalRun.setFontFamily("微软雅黑");
        }
    }

    /**
     * 添加列表项
     */
    private static void addListItem(XWPFParagraph paragraph, String text) {
        // 创建编号
        XWPFRun bulletRun = paragraph.createRun();
        bulletRun.setText("• ");
        bulletRun.setFontSize(12);
        bulletRun.setFontFamily("微软雅黑");
        bulletRun.setBold(true);
        bulletRun.setColor("2E75B6");
        
        // 添加列表项文本
        text = cleanMarkdown(text);
        addFormattedText(paragraph, text);
        
        paragraph.setSpacingAfter(60);
        paragraph.setIndentationLeft(360);
        paragraph.setFirstLineIndent(-360);
    }

    /**
     * 添加列表项（创建新段落）
     */
    private static void addListItem(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        addListItem(paragraph, text);
    }

    /**
     * 添加分隔线
     */
    private static void addSeparator(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText("─────────────────────────────────────────────────────────────");
        run.setColor("CCCCCC");
        run.setFontSize(10);
        paragraph.setSpacingBefore(150);
        paragraph.setSpacingAfter(150);
        paragraph.setAlignment(ParagraphAlignment.CENTER);
    }

    /**
     * 清理Markdown标记
     */
    private static String cleanMarkdown(String text) {
        // 移除粗体标记（保留文本）
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        // 移除代码标记（保留文本）
        text = text.replaceAll("`(.+?)`", "$1");
        // 保留复选框符号，但可以替换为更美观的形式
        return text;
    }
    
    /**
     * 主方法 - 用于测试和生成文档
     */
    public static void main(String[] args) {
        try {
            // 使用项目根目录的Markdown文件
            String markdownFile = "项目功能清单.md";
            String outputFile = "智能教育平台功能清单.docx";
            
            System.out.println("========================================");
            System.out.println("正在生成Word文档...");
            System.out.println("输入文件: " + markdownFile);
            
            String outputPath = generateWordFromMarkdown(markdownFile, outputFile);
            
            System.out.println("✓ Word文档已成功生成！");
            System.out.println("保存路径: " + outputPath);
            System.out.println("========================================");
        } catch (IOException e) {
            System.err.println("❌ 生成Word文档时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
