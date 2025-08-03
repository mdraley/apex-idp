package com.apex.idp.infrastructure.ocr;
package com.apex.idp.infrastructure.ocr;

import java.io.InputStream;

/**
 * Interface for OCR (Optical Character Recognition) operations.
 */
public interface OCRService {

    /**
     * Performs OCR on the provided input stream.
     *
     * @param inputStream The file content as an input stream
     * @param contentType The content type of the file (e.g., "application/pdf", "image/jpeg")
     * @return The OCR result containing extracted text and confidence score
     * @throws Exception If OCR operation fails
     */
    OCRResult performOCR(InputStream inputStream, String contentType) throws Exception;

    /**
     * Performs OCR on a specific page of a multi-page document.
     *
     * @param inputStream The file content as an input stream
     * @param contentType The content type of the file
     * @param pageNumber The page number to process (0-based)
     * @return The OCR result for the specified page
     * @throws Exception If OCR operation fails
     */
    OCRResult performOCROnPage(InputStream inputStream, String contentType, int pageNumber) throws Exception;

    /**
     * Gets the number of pages in a document.
     *
     * @param inputStream The file content as an input stream
     * @param contentType The content type of the file
     * @return The number of pages in the document
     * @throws Exception If the operation fails
     */
    int getPageCount(InputStream inputStream, String contentType) throws Exception;

    /**
     * Checks if OCR is supported for the given content type.
     *
     * @param contentType The content type to check
     * @return true if OCR is supported, false otherwise
     */
    boolean isOCRSupported(String contentType);
}
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * OCR Service interface for document text extraction.
 * This is a placeholder for actual OCR implementation (Tesseract, LayoutLMv3, etc.)
 */
@Service
@Slf4j
public class OCRService {

    @Value("${ocr.confidence.threshold:0.8}")
    private double confidenceThreshold;

    /**
     * Performs OCR on the given document stream.
     *
     * @param documentStream Input stream of the document
     * @param contentType MIME type of the document
     * @return OCR result containing extracted text and metadata
     */
    public OCRResult performOCR(InputStream documentStream, String contentType) throws Exception {
        log.info("Starting OCR processing for content type: {}", contentType);

        try {
            // TODO: Implement actual OCR logic here
            // This is a placeholder implementation

            // For now, return a mock result
            return OCRResult.builder()
                    .text("Sample extracted text from document")
                    .confidence(0.95)
                    .language("en")
                    .pageCount(1)
                    .metadata(Map.of(
                            "processingTime", "1500ms",
                            "ocrEngine", "Tesseract",
                            "contentType", contentType
                    ))
                    .build();

        } catch (Exception e) {
            log.error("OCR processing failed", e);
            throw new OCRProcessingException("Failed to perform OCR", e);
        }
    }

    /**
     * Performs OCR with region detection.
     */
    public OCRResult performOCRWithRegions(InputStream documentStream, String contentType) throws Exception {
        log.info("Starting OCR with region detection for content type: {}", contentType);

        OCRResult basicResult = performOCR(documentStream, contentType);

        // Add region information
        List<TextRegion> regions = detectTextRegions(basicResult.getText());

        return OCRResult.builder()
                .text(basicResult.getText())
                .confidence(basicResult.getConfidence())
                .language(basicResult.getLanguage())
                .pageCount(basicResult.getPageCount())
                .regions(regions)
                .metadata(basicResult.getMetadata())
                .build();
    }

    /**
     * Detects text regions in the extracted text.
     */
    private List<TextRegion> detectTextRegions(String text) {
        // TODO: Implement actual region detection
        return List.of(
                TextRegion.builder()
                        .type("HEADER")
                        .text("Invoice Header")
                        .confidence(0.95)
                        .boundingBox(new BoundingBox(0, 0, 100, 50))
                        .build()
        );
    }

    /**
     * Custom exception for OCR processing errors.
     */
    public static class OCRProcessingException extends Exception {
        public OCRProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

/**
 * Represents a region of text within a document.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class TextRegion {
    private String type; // HEADER, BODY, FOOTER, TABLE, etc.
    private String text;
    private double confidence;
    private BoundingBox boundingBox;
    private Map<String, String> attributes;
}

/**
 * Represents a bounding box for text regions.
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
class BoundingBox {
    private int x;
    private int y;
    private int width;
    private int height;
}