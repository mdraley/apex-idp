package com.apex.idp.infrastructure.ocr;

import com.apex.idp.domain.document.Document;
import com.apex.idp.infrastructure.storage.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * Implementation of OCR service that integrates with external OCR APIs
 * and provides local PDF text extraction capabilities.
 */
@Slf4j
@Service
public class OCRServiceImpl implements OCRService {

    private final RestTemplate restTemplate;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    @Value("${ocr.api.endpoint:http://localhost:5000/api/ocr}")
    private String ocrApiEndpoint;

    @Value("${ocr.api.timeout:60000}")
    private int apiTimeout;

    @Value("${ocr.supported.formats:application/pdf,image/jpeg,image/png,image/tiff}")
    private List<String> supportedFormats;

    @Value("${ocr.max.file.size:52428800}") // 50MB default
    private long maxFileSize;

    @Value("${ocr.enable.local.pdf:true}")
    private boolean enableLocalPdfExtraction;

    @Value("${ocr.confidence.threshold:0.7}")
    private double confidenceThreshold;

    public OCRServiceImpl(RestTemplate restTemplate,
                          StorageService storageService,
                          ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public OCRResult performOCR(MultipartFile file) throws OCRException {
        log.info("Performing OCR on file: {}", file.getOriginalFilename());

        // Validate file
        ValidationResult validation = validateFile(file);
        if (!validation.isValid()) {
            throw new OCRException("File validation failed: " +
                    String.join(", ", validation.getIssues()));
        }

        try {
            return performOCR(file.getInputStream(), file.getOriginalFilename(),
                    file.getContentType());
        } catch (IOException e) {
            throw new OCRException("Failed to read file", e);
        }
    }

    @Override
    public OCRResult performOCR(InputStream inputStream, String fileName, String contentType)
            throws OCRException {
        log.debug("Performing OCR - fileName: {}, contentType: {}", fileName, contentType);

        try {
            // For PDFs, try local extraction first if enabled
            if (enableLocalPdfExtraction && isPdfFile(contentType)) {
                try {
                    OCRResult localResult = extractTextFromPdf(inputStream);
                    if (localResult.getConfidence() >= confidenceThreshold) {
                        return localResult;
                    }
                } catch (IOException e) {
                    log.warn("Local PDF extraction failed, falling back to OCR API", e);
                    // Reset input stream for API call
                    inputStream = resetInputStream(inputStream);
                }
            }

            // Call external OCR API
            return callOcrApi(inputStream, fileName, contentType);

        } catch (Exception e) {
            log.error("OCR processing failed for file: {}", fileName, e);
            throw new OCRException("OCR processing failed", e);
        }
    }

    @Override
    public OCRResult performOCR(Document document) throws OCRException {
        log.info("Performing OCR on document: {}", document.getId());

        try {
            // Retrieve document from storage
            // Use MinIOStorageService's simplified retrieve method if available
            InputStream fileStream;
            if (storageService instanceof com.apex.idp.infrastructure.storage.MinIOStorageService) {
                fileStream = ((com.apex.idp.infrastructure.storage.MinIOStorageService) storageService)
                        .retrieve(document.getFilePath());
            } else {
                // Fallback to interface method
                fileStream = storageService.retrieveDocument(null, document.getFilePath())
                        .orElseThrow(() -> new OCRException("Document not found in storage"));
            }

            OCRResult result = performOCR(fileStream, document.getFileName(),
                    document.getContentType());

            // Store OCR result in document
            document.setExtractedText(result.getExtractedText());
            document.setOcrConfidence(result.getConfidence());

            return result;

        } catch (Exception e) {
            log.error("OCR failed for document: {}", document.getId(), e);
            throw new OCRException("Failed to perform OCR on document", e);
        }
    }

    @Override
    public CompletableFuture<BatchOCRResult> performBatchOCR(List<Document> documents) {
        log.info("Starting batch OCR for {} documents", documents.size());

        return CompletableFuture.supplyAsync(() -> {
            List<DocumentOCRResult> results = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;
            long startTime = System.currentTimeMillis();

            for (Document document : documents) {
                try {
                    OCRResult ocrResult = performOCR(document);
                    results.add(new DocumentOCRResult(
                            document.getId(),
                            document.getFileName(),
                            true,
                            ocrResult,
                            null
                    ));
                    successCount++;
                } catch (Exception e) {
                    log.error("OCR failed for document: {}", document.getId(), e);
                    results.add(new DocumentOCRResult(
                            document.getId(),
                            document.getFileName(),
                            false,
                            null,
                            e.getMessage()
                    ));
                    failureCount++;
                }
            }

            long totalTime = System.currentTimeMillis() - startTime;
            return new BatchOCRResult(results, successCount, failureCount, totalTime);
        });
    }

    @Override
    public Map<String, Object> extractStructuredData(String ocrText, String documentType)
            throws OCRException {
        log.debug("Extracting structured data for document type: {}", documentType);

        // This would integrate with AI service for intelligent extraction
        Map<String, Object> structuredData = new HashMap<>();

        // Simple pattern-based extraction as placeholder
        // In production, this would use AI service
        structuredData.put("documentType", documentType);
        structuredData.put("textLength", ocrText.length());
        structuredData.put("extractionMethod", "pattern-based");

        return structuredData;
    }

    @Override
    public LayoutOCRResult performLayoutOCR(MultipartFile file, LayoutOptions options)
            throws OCRException {
        throw new OCRException("Layout OCR not yet implemented");
    }

    @Override
    public ValidationResult validateFile(MultipartFile file) {
        List<String> issues = new ArrayList<>();

        // Check file size
        if (file.getSize() > maxFileSize) {
            issues.add("File size exceeds maximum allowed size of " + maxFileSize + " bytes");
        }

        // Check file type
        String contentType = file.getContentType();
        if (contentType == null || !supportedFormats.contains(contentType)) {
            issues.add("Unsupported file format: " + contentType);
        }

        // Check filename
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            issues.add("Invalid filename");
        }

        return new ValidationResult(issues.isEmpty(), issues);
    }

    @Override
    public InputStream preprocessImage(InputStream inputStream, PreprocessingOptions options)
            throws OCRException {
        throw new OCRException("Image preprocessing not yet implemented");
    }

    @Override
    public List<String> getSupportedFormats() {
        return new ArrayList<>(supportedFormats);
    }

    @Override
    public int estimateProcessingTime(long fileSize, int pageCount) {
        // Simple estimation: 1 second per MB + 2 seconds per page
        int sizeBasedTime = (int) (fileSize / (1024 * 1024));
        int pageBasedTime = pageCount * 2;
        return Math.max(sizeBasedTime + pageBasedTime, 5); // Minimum 5 seconds
    }

    @Override
    public String extractText(Document document) {
        try {
            OCRResult result = performOCR(document);
            return result.getExtractedText();
        } catch (OCRException e) {
            log.error("Failed to extract text from document: {}", document.getId(), e);
            return "";
        }
    }

    // Private helper methods

    private OCRResult extractTextFromPdf(InputStream inputStream) throws IOException {
        log.debug("Attempting local PDF text extraction");

        // Read input stream into byte array to allow multiple reads
        byte[] pdfBytes = inputStream.readAllBytes();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);

            String extractedText = textStripper.getText(document);

            // Calculate a simple confidence score based on text content
            double confidence = calculateTextConfidence(extractedText);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("method", "local_pdf_extraction");
            metadata.put("pageCount", document.getNumberOfPages());
            metadata.put("encrypted", document.isEncrypted());

            // Fixed: Use correct constructor parameters
            return new OCRResult(
                    extractedText,                    // extractedText
                    confidence,                       // confidence
                    "en",                            // language
                    document.getNumberOfPages(),      // pageCount
                    metadata,                         // metadata
                    System.currentTimeMillis()        // processingTimeMs
            );

        } catch (Exception e) {
            log.error("Local PDF extraction failed", e);
            throw new IOException("Failed to extract text from PDF", e);
        }
    }

    private OCRResult callOcrApi(InputStream inputStream, String fileName, String contentType)
            throws IOException {
        log.debug("Calling external OCR API for file: {}", fileName);

        // Convert input stream to byte array
        byte[] fileBytes = inputStream.readAllBytes();

        // Prepare multipart request
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
            @Override
            public String getContentType() {
                return contentType;
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity =
                new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    ocrApiEndpoint,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseOCRResponse(response.getBody());
            } else {
                String error = "OCR API returned status: " + response.getStatusCode();
                // Fixed: Use correct constructor parameters
                return new OCRResult("", 0.0, "en", 0, new HashMap<>(), 0L);
            }

        } catch (Exception e) {
            log.error("OCR API call failed", e);
            // Fixed: Use correct constructor parameters
            return new OCRResult("", 0.0, "en", 0, new HashMap<>(), 0L);
        }
    }

    private OCRResult parseOCRResponse(Map<String, Object> response) {
        try {
            String text = (String) response.getOrDefault("text", "");
            Double confidence = extractDoubleValue(response.get("confidence"));
            Map<String, Object> metadata = (Map<String, Object>) response.getOrDefault("metadata", new HashMap<>());
            Integer pageCount = (Integer) response.getOrDefault("pageCount", 1);
            String language = (String) response.getOrDefault("language", "en");

            // Fixed: Use correct constructor parameters
            return new OCRResult(
                    text,                           // extractedText
                    confidence,                     // confidence
                    language,                       // language
                    pageCount,                      // pageCount
                    metadata,                       // metadata
                    System.currentTimeMillis()      // processingTimeMs
            );

        } catch (Exception e) {
            log.error("Failed to parse OCR response", e);
            // Fixed: Use correct constructor parameters
            return new OCRResult("", 0.0, "en", 0, new HashMap<>(), 0L);
        }
    }

    private OCRResult parseLayoutOCRResponse(Map<String, Object> response) {
        try {
            OCRResult basicResult = parseOCRResponse(response);

            // Add layout-specific metadata
            if (response.containsKey("layout")) {
                Map<String, Object> layoutData = (Map<String, Object>) response.get("layout");
                Map<String, Object> enrichedMetadata = new HashMap<>(basicResult.getMetadata());
                enrichedMetadata.put("layout", layoutData);

                // Fixed: Use correct constructor parameters and methods
                return new OCRResult(
                        basicResult.getExtractedText(),        // extractedText
                        basicResult.getConfidence(),           // confidence
                        basicResult.getLanguage(),             // language
                        basicResult.getPageCount(),            // pageCount
                        enrichedMetadata,                      // metadata
                        basicResult.getProcessingTimeMs()      // processingTimeMs
                );
            }

            return basicResult;

        } catch (Exception e) {
            log.error("Failed to parse layout OCR response", e);
            // Fixed: Use correct constructor parameters
            return new OCRResult("", 0.0, "en", 0, new HashMap<>(), 0L);
        }
    }

    private double calculateTextConfidence(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0.0;
        }

        // Simple heuristic based on text characteristics
        int totalChars = text.length();
        int alphanumeric = text.replaceAll("[^a-zA-Z0-9]", "").length();
        int words = text.split("\\s+").length;

        // Calculate confidence based on:
        // - Ratio of alphanumeric characters
        // - Average word length
        // - Presence of common patterns
        double alphanumericRatio = (double) alphanumeric / totalChars;
        double avgWordLength = (double) alphanumeric / words;

        double confidence = 0.0;

        // Higher ratio of alphanumeric characters = higher confidence
        confidence += alphanumericRatio * 0.5;

        // Reasonable word length (3-10 chars) = higher confidence
        if (avgWordLength >= 3 && avgWordLength <= 10) {
            confidence += 0.3;
        }

        // Check for common document patterns
        if (text.matches(".*\\b(invoice|date|amount|total|vendor)\\b.*")) {
            confidence += 0.2;
        }

        return Math.min(confidence, 1.0);
    }

    private boolean isPdfFile(String contentType) {
        return "application/pdf".equalsIgnoreCase(contentType);
    }

    private InputStream resetInputStream(InputStream original) throws IOException {
        if (original.markSupported()) {
            original.reset();
            return original;
        } else {
            // If mark/reset not supported, we need to re-read the stream
            // This is why we convert to byte array in most methods
            throw new IOException("Cannot reset input stream");
        }
    }

    private Double extractDoubleValue(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // Helper method to create OCRResult with minimal parameters (for backward compatibility)
    private OCRResult createOCRResult(String text, double confidence, String language, int pageCount) {
        return new OCRResult(text, confidence, language, pageCount, new HashMap<>(), System.currentTimeMillis());
    }
}