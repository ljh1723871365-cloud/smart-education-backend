package com.ljh.smarteducation.service.impl;

import com.ljh.smarteducation.service.DocumentParserService;
import com.ljh.smarteducation.service.TextExtractionResult;
import com.ljh.smarteducation.service.TextExtractionService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simple factory-style implementation that delegates to concrete
 * TextExtractor implementations based on file extension / content type.
 */
@Service
public class TextExtractionServiceImpl implements TextExtractionService {

    private final DocxTextExtractor docxTextExtractor;
    private final PdfTextExtractor pdfTextExtractor;
    private final OcrImageTextExtractor ocrImageTextExtractor;
    private final DocumentParserService documentParserService;

    public TextExtractionServiceImpl(DocxTextExtractor docxTextExtractor,
                                     PdfTextExtractor pdfTextExtractor,
                                     OcrImageTextExtractor ocrImageTextExtractor,
                                     DocumentParserService documentParserService) {
        this.docxTextExtractor = docxTextExtractor;
        this.pdfTextExtractor = pdfTextExtractor;
        this.ocrImageTextExtractor = ocrImageTextExtractor;
        this.documentParserService = documentParserService;
    }

    @Override
    public TextExtractionResult extract(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("Uploaded file is empty.");
        }
        String originalName = file.getOriginalFilename();
        String lowerName = originalName != null ? originalName.toLowerCase() : "";
        String inferredType = "UNKNOWN";
        long startNs = System.nanoTime();
        TextExtractionResult result;

        if (lowerName.endsWith(".docx")) {
            inferredType = "DOCX";
            // 对于真实 DOCX 文件，继续复用历史上验证过的 DocumentParserService 解析逻辑，避免将二进制内容误当作 UTF-8 文本。
            String fullText = documentParserService.parseDocument(file);

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
                pageTexts = Collections.singletonList(normalized);
            }

            result = new TextExtractionResult(
                    fullText != null ? fullText : "",
                    Collections.unmodifiableList(titles),
                    Collections.unmodifiableList(pageTexts)
            );
        } else if (lowerName.endsWith(".pdf")) {
            inferredType = "PDF";
            result = pdfTextExtractor.extract(file.getInputStream(), originalName);
        } else if (lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            inferredType = "IMAGE";
            // 预留 OCR 图片抽取分支：当前阶段统一报不支持
            throw new IOException("OCR image extraction not supported yet for file: " + originalName);
        } else {
            // 其他格式暂不支持，抛出明确错误信息
            throw new IOException("Unsupported file format for text extraction: " + originalName
                    + ". Currently only .docx and .pdf (simple text-based) are supported; OCR images are not yet supported.");
        }

        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;

        String fullText = (result != null && result.getFullText() != null) ? result.getFullText() : "";
        int textLength = fullText.length();
        String preview;
        if (textLength <= 200) {
            preview = fullText;
        } else {
            preview = fullText.substring(0, 200) + "...";
        }

        java.util.List<String> titles = (result != null && result.getPossibleTitles() != null)
                ? result.getPossibleTitles()
                : java.util.Collections.emptyList();
        java.util.List<String> titlePreview = titles.size() > 3 ? titles.subList(0, 3) : titles;

        System.out.println(">>> [TextExtraction] file=" + originalName
                + ", type=" + inferredType
                + ", length=" + textLength + " chars"
                + ", duration=" + durationMs + " ms");
        System.out.println(">>> [TextExtraction] title candidates: " + titlePreview);
        System.out.println(">>> [TextExtraction] preview (up to 200 chars): " + preview);

        return result;
    }
}
