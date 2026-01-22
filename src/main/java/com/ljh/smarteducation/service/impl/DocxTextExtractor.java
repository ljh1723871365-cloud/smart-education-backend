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
 * Very simple, best-effort DOCX text extractor.
 *
 * Phase D-1: we intentionally avoid introducing new dependencies (e.g. Apache POI)
 * and simply treat the DOCX as a UTF-8 byte stream. This will not yield
 * perfect text for real DOCX binaries, but is sufficient as a placeholder
 * implementation and for unit wiring of the TextExtractor abstraction.
 */
@Component
public class DocxTextExtractor implements TextExtractor {

    @Override
    public TextExtractionResult extract(InputStream inputStream, String fileName) throws IOException {
        if (inputStream == null) {
            return new TextExtractionResult("", Collections.emptyList(), Collections.emptyList());
        }

        // Read all bytes into memory (Phase D-1: small exam files only)
        String fullText = readAllAsUtf8(inputStream);

        // Heuristic: derive possible titles from the first few non-empty lines
        List<String> titles = new ArrayList<>();
        if (fullText != null && !fullText.isBlank()) {
            String[] lines = fullText.replace("\r\n", "\n").split("\n");
            for (String raw : lines) {
                if (raw == null) continue;
                String line = raw.trim();
                if (line.isEmpty()) continue;
                titles.add(line);
                if (titles.size() >= 3) {
                    break;
                }
            }
        }

        return new TextExtractionResult(
                fullText != null ? fullText : "",
                Collections.unmodifiableList(titles),
                Collections.emptyList() // page-wise texts not available for simple DOCX extractor
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
