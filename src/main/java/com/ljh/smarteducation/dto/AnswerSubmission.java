package com.ljh.smarteducation.dto;

import lombok.Data;
import java.util.Map;

@Data
public class AnswerSubmission {

    // (原有的)
    private Map<Long, String> answers;

    // --- ↓↓↓ (新增) ↓↓↓ ---
    /**
     * 本次提交的答卷属于哪一套题
     */
    private Long questionSetId;
    // --- ↑↑↑ (新增结束) ↑↑↑ ---
}
