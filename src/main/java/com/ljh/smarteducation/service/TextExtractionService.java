package com.ljh.smarteducation.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * High-level service for extracting text from uploaded exam files.
 *
 * Phase D-3: This service hides concrete extractor implementations
 * (DOCX/PDF/IMAGE) from business code.
 */
public interface TextExtractionService {

    /**
     * Extract plain text and basic metadata from the given uploaded file.
     *
     * @param file uploaded exam file (DOCX/PDF/IMAGE, etc.)
     * @return unified TextExtractionResult
     * @throws IOException if reading fails or format is unsupported
     */
    TextExtractionResult extract(MultipartFile file) throws IOException;
}
