package com.apex.idp.infrastructure.ocr;
package com.apex.idp.infrastructure.ocr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class representing the result of an OCR operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OCRResult {

    /**
     * The extracted text content.
     */
    private String text;

    /**
     * Confidence score of the OCR result (0.0 to 1.0).
     */
    private double confidence;

    /**
     * Number of pages processed.
     */
    @Builder.Default
    private int pageCount = 1;

    /**
     * List of word positions and confidences.
     */
    private List<OCRWord> words;

    /**
     * Additional metadata about the OCR result.
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Class representing a word identified by OCR.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OCRWord {
        private String text;
        private double confidence;
        private int x;
        private int y;
        private int width;
        private int height;
        private int page;
    }
}
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