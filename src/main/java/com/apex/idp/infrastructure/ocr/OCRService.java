package com.apex.idp.infrastructure.ocr;

import com.apex.idp.domain.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface OCRService {

    // Basic OCR operations
    OCRResult performOCR(MultipartFile file) throws OCRException;

    OCRResult performOCR(InputStream inputStream, String fileName, String contentType) throws OCRException;

    OCRResult performOCR(Document document) throws OCRException;

    // Batch processing
    CompletableFuture<BatchOCRResult> performBatchOCR(List<Document> documents);

    // Advanced features
    Map<String, Object> extractStructuredData(String ocrText, String documentType) throws OCRException;

    LayoutOCRResult performLayoutOCR(MultipartFile file, LayoutOptions options) throws OCRException;

    // Utilities
    ValidationResult validateFile(MultipartFile file);

    InputStream preprocessImage(InputStream inputStream, PreprocessingOptions options) throws OCRException;

    List<String> getSupportedFormats();

    int estimateProcessingTime(long fileSize, int pageCount);

    // Simple text extraction (for backward compatibility)
    String extractText(Document document);

    // Result classes
    class OCRResult {
        private final String extractedText;
        private final double confidence;
        private final String language;
        private final int pageCount;
        private final Map<String, Object> metadata;
        private final long processingTimeMs;

        public OCRResult(String extractedText, double confidence, String language,
                         int pageCount, Map<String, Object> metadata, long processingTimeMs) {
            this.extractedText = extractedText;
            this.confidence = confidence;
            this.language = language;
            this.pageCount = pageCount;
            this.metadata = metadata;
            this.processingTimeMs = processingTimeMs;
        }

        // Getters
        public String getExtractedText() { return extractedText; }
        public double getConfidence() { return confidence; }
        public String getLanguage() { return language; }
        public int getPageCount() { return pageCount; }
        public Map<String, Object> getMetadata() { return metadata; }
        public long getProcessingTimeMs() { return processingTimeMs; }
    }

    class BatchOCRResult {
        private final List<DocumentOCRResult> results;
        private final int successCount;
        private final int failureCount;
        private final long totalProcessingTimeMs;

        public BatchOCRResult(List<DocumentOCRResult> results, int successCount,
                              int failureCount, long totalProcessingTimeMs) {
            this.results = results;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.totalProcessingTimeMs = totalProcessingTimeMs;
        }

        // Getters
        public List<DocumentOCRResult> getResults() { return results; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public long getTotalProcessingTimeMs() { return totalProcessingTimeMs; }
    }

    class DocumentOCRResult {
        private final String documentId;
        private final String fileName;
        private final boolean success;
        private final OCRResult ocrResult;
        private final String errorMessage;

        public DocumentOCRResult(String documentId, String fileName, boolean success,
                                 OCRResult ocrResult, String errorMessage) {
            this.documentId = documentId;
            this.fileName = fileName;
            this.success = success;
            this.ocrResult = ocrResult;
            this.errorMessage = errorMessage;
        }

        // Getters
        public String getDocumentId() { return documentId; }
        public String getFileName() { return fileName; }
        public boolean isSuccess() { return success; }
        public OCRResult getOcrResult() { return ocrResult; }
        public String getErrorMessage() { return errorMessage; }
    }

    class LayoutOCRResult extends OCRResult {
        private final List<TextBlock> textBlocks;
        private final List<Table> tables;
        private final Map<String, BoundingBox> fields;

        public LayoutOCRResult(String extractedText, double confidence, String language,
                               int pageCount, Map<String, Object> metadata, long processingTimeMs,
                               List<TextBlock> textBlocks, List<Table> tables,
                               Map<String, BoundingBox> fields) {
            super(extractedText, confidence, language, pageCount, metadata, processingTimeMs);
            this.textBlocks = textBlocks;
            this.tables = tables;
            this.fields = fields;
        }

        // Getters
        public List<TextBlock> getTextBlocks() { return textBlocks; }
        public List<Table> getTables() { return tables; }
        public Map<String, BoundingBox> getFields() { return fields; }
    }

    class ValidationResult {
        private final boolean valid;
        private final List<String> issues;

        public ValidationResult(boolean valid, List<String> issues) {
            this.valid = valid;
            this.issues = issues;
        }

        public boolean isValid() { return valid; }
        public List<String> getIssues() { return issues; }
    }

    class TextBlock {
        private final String text;
        private final BoundingBox boundingBox;
        private final double confidence;
        private final int page;

        public TextBlock(String text, BoundingBox boundingBox, double confidence, int page) {
            this.text = text;
            this.boundingBox = boundingBox;
            this.confidence = confidence;
            this.page = page;
        }

        // Getters
        public String getText() { return text; }
        public BoundingBox getBoundingBox() { return boundingBox; }
        public double getConfidence() { return confidence; }
        public int getPage() { return page; }
    }

    class Table {
        private final List<List<String>> cells;
        private final BoundingBox boundingBox;
        private final int page;

        public Table(List<List<String>> cells, BoundingBox boundingBox, int page) {
            this.cells = cells;
            this.boundingBox = boundingBox;
            this.page = page;
        }

        // Getters
        public List<List<String>> getCells() { return cells; }
        public BoundingBox getBoundingBox() { return boundingBox; }
        public int getPage() { return page; }
    }

    class BoundingBox {
        private final int x;
        private final int y;
        private final int width;
        private final int height;

        public BoundingBox(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        // Getters
        public int getX() { return x; }
        public int getY() { return y; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }

    class LayoutOptions {
        private boolean detectTables;
        private boolean detectForms;
        private boolean preserveLayout;

        // Getters and setters
        public boolean isDetectTables() { return detectTables; }
        public void setDetectTables(boolean detectTables) { this.detectTables = detectTables; }
        public boolean isDetectForms() { return detectForms; }
        public void setDetectForms(boolean detectForms) { this.detectForms = detectForms; }
        public boolean isPreserveLayout() { return preserveLayout; }
        public void setPreserveLayout(boolean preserveLayout) { this.preserveLayout = preserveLayout; }
    }

    class PreprocessingOptions {
        private boolean deskew;
        private boolean removeNoise;
        private boolean enhanceContrast;
        private boolean convertToGrayscale;

        // Getters and setters
        public boolean isDeskew() { return deskew; }
        public void setDeskew(boolean deskew) { this.deskew = deskew; }
        public boolean isRemoveNoise() { return removeNoise; }
        public void setRemoveNoise(boolean removeNoise) { this.removeNoise = removeNoise; }
        public boolean isEnhanceContrast() { return enhanceContrast; }
        public void setEnhanceContrast(boolean enhanceContrast) { this.enhanceContrast = enhanceContrast; }
        public boolean isConvertToGrayscale() { return convertToGrayscale; }
        public void setConvertToGrayscale(boolean convertToGrayscale) { this.convertToGrayscale = convertToGrayscale; }
    }

    class OCRException extends Exception {
        public OCRException(String message) {
            super(message);
        }

        public OCRException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}