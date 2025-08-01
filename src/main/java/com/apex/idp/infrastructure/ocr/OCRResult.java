package com.apex.idp.infrastructure.ocr;

import lombok.*;
import java.util.Map;

/**
 * OCR processing result containing extracted text and metadata.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OCRResult {
    private String extractedText;
    private double confidence;
    private String language;
    private int pageCount;
    private Map<String, Object> metadata;
    private long processingTimeMs;

    // Convenience getter for backward compatibility
    public String getText() {
        return extractedText;
    }
}