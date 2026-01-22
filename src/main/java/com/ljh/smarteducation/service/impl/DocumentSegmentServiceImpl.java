package com.ljh.smarteducation.service.impl;

import com.ljh.smarteducation.service.DocumentSegmentService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentSegmentServiceImpl implements DocumentSegmentService {

    @Override
    public List<String> segmentDocument(String fullText, int maxCharsPerSegment) {
        List<String> segments = new ArrayList<>();
        
        if (fullText == null || fullText.isEmpty()) {
            return segments;
        }
        
        // 如果文档小于阈值，直接返回
        if (fullText.length() <= maxCharsPerSegment) {
            segments.add(fullText);
            return segments;
        }
        
        // 按段落分割
        String[] paragraphs = fullText.split("\n\n+");
        StringBuilder currentSegment = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            // 如果当前段落加上当前分段超过限制
            if (currentSegment.length() + paragraph.length() > maxCharsPerSegment && currentSegment.length() > 0) {
                // 保存当前分段
                segments.add(currentSegment.toString());
                currentSegment = new StringBuilder();
            }
            
            // 如果单个段落就超过限制，强制分割
            if (paragraph.length() > maxCharsPerSegment) {
                if (currentSegment.length() > 0) {
                    segments.add(currentSegment.toString());
                    currentSegment = new StringBuilder();
                }
                // 按句子分割超长段落
                String[] sentences = paragraph.split("(?<=[.!?。！？]\\s)");
                for (String sentence : sentences) {
                    if (currentSegment.length() + sentence.length() > maxCharsPerSegment && currentSegment.length() > 0) {
                        segments.add(currentSegment.toString());
                        currentSegment = new StringBuilder();
                    }
                    currentSegment.append(sentence);
                }
            } else {
                currentSegment.append(paragraph).append("\n\n");
            }
        }
        
        // 添加最后一段
        if (currentSegment.length() > 0) {
            segments.add(currentSegment.toString());
        }
        
        return segments;
    }

    @Override
    public List<String> smartSegmentByQuestions(String fullText) {
        List<String> segments = new ArrayList<>();
        
        if (fullText == null || fullText.isEmpty()) {
            return segments;
        }
        
        System.out.println(">>> 开始智能分段，文档长度: " + fullText.length() + " 字符");
        
        // 检测题目编号模式（支持多种格式）
        // 1. 2. 3. 或 1、2、3、 或 一、二、三、 或 Question 1. Question 2.
        Pattern questionPattern = Pattern.compile(
            "(?m)^\\s*(?:" +
            "(?:\\d+)[.、]|" +                    // 1. 或 1、
            "(?:[一二三四五六七八九十百]+)[、.]|" + // 一、 或 一.
            "(?:Question|Part|Section)\\s+\\d+|" + // Question 1
            "(?:\\(\\d+\\))|" +                    // (1)
            "(?:\\[\\d+\\])" +                     // [1]
            ")"
        );
        
        Matcher matcher = questionPattern.matcher(fullText);
        List<Integer> questionStarts = new ArrayList<>();
        
        while (matcher.find()) {
            questionStarts.add(matcher.start());
        }
        
        System.out.println(">>> 检测到 " + questionStarts.size() + " 个题目标记");
        
        // 如果没有检测到题目标记，使用简单分段
        if (questionStarts.isEmpty()) {
            System.out.println(">>> 未检测到题目标记，按字符数强制分段");
            return segmentDocument(fullText, 6000);
        }
        
        // 如果检测到的题目太少（可能是误检测），也使用简单分段
        if (questionStarts.size() < 3) {
            System.out.println(">>> 检测到的题目标记太少(" + questionStarts.size() + "个)，按字符数强制分段");
            return segmentDocument(fullText, 6000);
        }
        
        // 按题目分段，每10-15题一段
        int questionsPerSegment = 10;
        int maxCharsPerSegment = 8000; // 最大字符数限制
        
        for (int i = 0; i < questionStarts.size(); i += questionsPerSegment) {
            int startIdx = questionStarts.get(i);
            int endIdx;
            
            // 确定结束位置
            if (i + questionsPerSegment < questionStarts.size()) {
                endIdx = questionStarts.get(i + questionsPerSegment);
            } else {
                endIdx = fullText.length();
            }
            
            String segment = fullText.substring(startIdx, endIdx).trim();
            
            // 如果分段太大，进一步细分
            if (segment.length() > maxCharsPerSegment) {
                System.out.println(">>> 分段过大 (" + segment.length() + " 字符)，进一步细分");
                List<String> subSegments = segmentDocument(segment, maxCharsPerSegment);
                segments.addAll(subSegments);
            } else {
                segments.add(segment);
            }
        }
        
        System.out.println(">>> 智能分段完成，共 " + segments.size() + " 段");
        for (int i = 0; i < segments.size(); i++) {
            System.out.println(">>> 第 " + (i + 1) + " 段: " + segments.get(i).length() + " 字符");
        }
        
        return segments;
    }
}
