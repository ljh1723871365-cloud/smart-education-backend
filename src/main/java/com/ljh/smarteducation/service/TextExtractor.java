package com.ljh.smarteducation.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Unified abstraction for extracting plain text (and basic metadata) from
 * different exam file formats such as DOCX / PDF / images (via OCR).
 *
 * Phase D-1: only a simple DOCX implementation will be provided. Other
 * extractors (PDF/Images) can be added later.
 */
public interface TextExtractor {

    /**
     * Extract text and basic metadata from the given input stream.
     *
     * @param inputStream the source bytes of the exam file (caller retains ownership)
     * @param fileName    original file name, can be used for format-specific hints
     * @return TextExtractionResult containing full plain text and optional metadata
     */
    TextExtractionResult extract(InputStream inputStream, String fileName) throws IOException;
}
