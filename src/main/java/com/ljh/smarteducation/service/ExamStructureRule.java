package com.ljh.smarteducation.service;

import java.util.List;

/**
 * Lightweight description of a structural rule for a specific part/section
 * of an exam under a given {@link ExamTemplate}.
 *
 * Phase A-1: this class only defines the minimal skeleton and is not yet
 * wired into existing business logic.
 */
public class ExamStructureRule {

    /**
     * Logical identifier of this rule, e.g. "LISTENING_SECTION_A".
     */
    private String id;

    /**
     * The template this rule belongs to.
     */
    private ExamTemplate template;

    /**
     * Order of this section within the whole exam (1-based, coarse-grained).
     */
    private int orderInExam;

    /**
     * Minimum expected number of questions for this section.
     */
    private Integer minQuestionCount;

    /**
     * Maximum expected number of questions for this section.
     */
    private Integer maxQuestionCount;

    /**
     * Allowed question types (string identifiers such as "LISTENING",
     * "MULTIPLE_CHOICE", "CLOZE", etc.).
     */
    private List<String> allowedQuestionTypes;

    /**
     * Keywords that typically appear in the section heading or directions
     * to help identify this part from raw text.
     */
    private List<String> sectionKeywords;

    /**
     * Additional instruction keywords that may appear in the directions.
     */
    private List<String> instructionKeywords;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ExamTemplate getTemplate() {
        return template;
    }

    public void setTemplate(ExamTemplate template) {
        this.template = template;
    }

    public int getOrderInExam() {
        return orderInExam;
    }

    public void setOrderInExam(int orderInExam) {
        this.orderInExam = orderInExam;
    }

    public Integer getMinQuestionCount() {
        return minQuestionCount;
    }

    public void setMinQuestionCount(Integer minQuestionCount) {
        this.minQuestionCount = minQuestionCount;
    }

    public Integer getMaxQuestionCount() {
        return maxQuestionCount;
    }

    public void setMaxQuestionCount(Integer maxQuestionCount) {
        this.maxQuestionCount = maxQuestionCount;
    }

    public List<String> getAllowedQuestionTypes() {
        return allowedQuestionTypes;
    }

    public void setAllowedQuestionTypes(List<String> allowedQuestionTypes) {
        this.allowedQuestionTypes = allowedQuestionTypes;
    }

    public List<String> getSectionKeywords() {
        return sectionKeywords;
    }

    public void setSectionKeywords(List<String> sectionKeywords) {
        this.sectionKeywords = sectionKeywords;
    }

    public List<String> getInstructionKeywords() {
        return instructionKeywords;
    }

    public void setInstructionKeywords(List<String> instructionKeywords) {
        this.instructionKeywords = instructionKeywords;
    }
}
