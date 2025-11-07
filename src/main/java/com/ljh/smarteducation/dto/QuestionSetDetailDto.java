package com.ljh.smarteducation.dto;

import com.ljh.smarteducation.entity.Question;
import com.ljh.smarteducation.entity.ResourceFile;
import lombok.Data;

import java.util.List;

@Data
public class QuestionSetDetailDto {
    private List<Question> questions;
    private ResourceFile resourceFile;
}
