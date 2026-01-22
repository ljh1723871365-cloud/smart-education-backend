package com.ljh.smarteducation.service;

import java.util.List;

/**
 * Unified result of text extraction from an exam source file (DOCX / PDF / image / etc.).
 *
 * Phase D-1: simple immutable value object, only for in-memory use.
 */
public class TextExtractionResult {

    /**
     * Full plain text content extracted from the file, with line breaks preserved.
     */
    private final String fullText;

    /**
     * Possible titles detected from the document (e.g. main title, sub-title).
     * Order is from most confident / earliest to later candidates.
     */
    private final List<String> possibleTitles;

    /**
     * Optional page-wise text information for multi-page formats (PDF / images).
     * For DOCX we can leave this null or an empty list in the first iteration.
     */
    private final List<String> pageTexts;

    public TextExtractionResult(String fullText, List<String> possibleTitles, List<String> pageTexts) {
        this.fullText = fullText;
        this.possibleTitles = possibleTitles;
        this.pageTexts = pageTexts;
    }

    public String getFullText() {
        return fullText;
    }

    public List<String> getPossibleTitles() {
        return possibleTitles;
    }

    public List<String> getPageTexts() {
        return pageTexts;
    }
}
