package com.ljh.smarteducation.service.impl;

import com.ljh.smarteducation.service.TextExtractionResult;
import com.ljh.smarteducation.service.TextExtractor;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal, placeholder-style PDF text extractor.
 *
 * Phase D-5: this implementation is ONLY intended to support very simple
 * text-based PDFs, by treating the InputStream as a UTF-8 text stream.
 * For real-world binary PDFs, this must be replaced with a proper
 * PDF parsing library (e.g. PDFBox) in a later iteration.
 */
@Component
public class PdfTextExtractor implements TextExtractor {

    @Override
    public TextExtractionResult extract(InputStream inputStream, String fileName) throws IOException {
        if (inputStream == null) {
            return new TextExtractionResult("", Collections.emptyList(), Collections.emptyList());
        }

        // Phase D-5: extremely simple text extraction assuming UTF-8 content.
        String fullText = readAllAsUtf8(inputStream);

        // Heuristic: derive possible titles from the first few non-empty lines
        List<String> titles = new ArrayList<>();
        List<String> pageTexts = new ArrayList<>();

        if (fullText != null && !fullText.isBlank()) {
            String normalized = fullText.replace("\r\n", "\n");
            String[] lines = normalized.split("\n");
            for (String raw : lines) {
                if (raw == null) continue;
                String line = raw.trim();
                if (line.isEmpty()) continue;
                titles.add(line);
                if (titles.size() >= 3) {
                    break;
                }
            }

            // Very rough page splitting (if the upstream has inserted page markers),
            // otherwise we just treat the whole text as a single page.
            String[] pages = normalized.split("\\f"); // PDF text extractors often use form feed as page break
            if (pages.length == 0) {
                pageTexts = Collections.singletonList(normalized);
            } else {
                Collections.addAll(pageTexts, pages);
            }
        }

        return new TextExtractionResult(
                fullText != null ? fullText : "",
                Collections.unmodifiableList(titles),
                Collections.unmodifiableList(pageTexts)
        );
    }

    private String readAllAsUtf8(InputStream in) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(in);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = bis.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }
}
