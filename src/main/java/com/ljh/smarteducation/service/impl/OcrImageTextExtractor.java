package com.ljh.smarteducation.service.impl;

import com.ljh.smarteducation.service.TextExtractionResult;
import com.ljh.smarteducation.service.TextExtractor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * Placeholder OCR-based image text extractor.
 *
 * Phase D-6: this class only defines the shape of the OCR integration.
 * In a later phase, it should be wired to a real OCR engine/service
 * (e.g. local OCR library or cloud OCR API).
 */
@Component
public class OcrImageTextExtractor implements TextExtractor {

    // Future OCR configuration placeholders
    private final String ocrEndpoint;
    private final String ocrApiKey;

    public OcrImageTextExtractor() {
        // Phase D-6: placeholders only, no real configuration yet
        this.ocrEndpoint = null;
        this.ocrApiKey = null;
    }

    public OcrImageTextExtractor(String ocrEndpoint, String ocrApiKey) {
        this.ocrEndpoint = ocrEndpoint;
        this.ocrApiKey = ocrApiKey;
    }

    @Override
    public TextExtractionResult extract(InputStream inputStream, String fileName) throws IOException {
        // Phase D-6: OCR is not implemented yet.
        throw new UnsupportedOperationException("OCR image extraction not implemented yet");
    }

    public String getOcrEndpoint() {
        return ocrEndpoint;
    }

    public String getOcrApiKey() {
        return ocrApiKey;
    }
}
