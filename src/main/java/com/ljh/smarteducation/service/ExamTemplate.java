package com.ljh.smarteducation.service;

/**
 * Exam template type used to describe the overall structure of an exam paper.
 *
 * This is a lightweight enum introduced in phase A-1.
 * It is currently only used as a marker and does not change existing logic.
 */
public enum ExamTemplate {

    /**
     * Standard high school English exam template, e.g. the Gaokao-style paper
     * with Listening / Grammar & Vocabulary / Reading / Writing sections.
     * Focuses on Part/Section-level structure and overall question counts.
     */
    GAOKAO_ENGLISH_A,

    /**
     * Enhanced Gaokao English template that, in addition to GAOKAO_ENGLISH_A
     * structure, also models question groups (e.g. listening shared-material
     * groups and reading passages with multiple questions).
     */
    GAOKAO_ENGLISH_A_GROUPED,

    /**
     * Generic CET-4 template with only coarse-grained assumptions about
     * Writing / Listening / Reading / Translation sections.
     */
    Template_CET4_Generic,

    /**
     * Generic CET-6 template with only coarse-grained assumptions about
     * Writing / Listening / Reading / Translation sections.
     */
    Template_CET6_Generic,

    /**
     * Generic template with minimal assumptions about exam structure.
     * Used as a fallback when no specific template is selected.
     */
    GENERIC
}
