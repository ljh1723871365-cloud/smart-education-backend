package com.ljh.smarteducation.dto;

import lombok.Data;
import java.util.Map;

@Data
public class SubmissionResult {
    private int score; // 得分
    private int totalQuestions; // 总题数
    // Key: Question ID, Value: Boolean (true if correct, false if incorrect)
    private Map<Long, Boolean> correctnessMap;
}