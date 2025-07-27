package com.apex.idp.infrastructure.ocr;

import com.apex.idp.domain.document.Document;
import com.apex.idp.infrastructure.storage.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader; // Add this import
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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

    // Remove the duplicate Logger declaration - @Slf4j handles this

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

    // Remove @RequiredArgsConstructor and keep only one constructor
    public OCRServiceImpl(RestTemplate restTemplate,
                          StorageService storageService,
                          ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Override
    public OCRResult performOCR(MultipartFile file) throws OCRException {
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
        long startTime = System.currentTimeMillis();

        try {
            // For PDFs, try local extraction first if enabled
            if (enableLocalPdfExtraction && "application/pdf".equals(contentType)) {
                OCRResult localResult = performLocalPdfExtraction(inputStream, fileName);
                if (localResult != null && !localResult.getExtractedText().trim().isEmpty()) {
                    return localResult;
                }
                // Reset stream for API call if local extraction didn't work well
                inputStream.reset();
            }

            // Call external OCR API
            return callExternalOCR(inputStream, fileName, contentType, startTime);

        } catch (Exception e) {
            log.error("OCR processing failed for file: {}", fileName, e);
            throw new OCRException("OCR processing failed", e);
        }
    }

    @Override
    public OCRResult performOCR(Document document) throws OCRException {
        try {
            // Retrieve document from storage
            Optional<InputStream> documentStream = storageService.retrieveDocument(
                    document.getBucketName(),
                    document.getStoragePath()
            );

            if (documentStream.isEmpty()) {
                throw new OCRException("Document not found in storage: " + document.getStoragePath());
            }

            return performOCR(documentStream.get(), document.getFileName(),
                    document.getContentType());

        } catch (StorageService.StorageException e) {
            throw new OCRException("Failed to retrieve document from storage", e);
        }
    }

    @Override
    @Async
    public CompletableFuture<BatchOCRResult> performBatchOCR(List<Document> documents) {
        log.info("Starting batch OCR processing for {} documents", documents.size());
        long startTime = System.currentTimeMillis();

        List<CompletableFuture<DocumentOCRResult>> futures = documents.stream()
                .map(doc -> CompletableFuture.supplyAsync(() -> {
                    try {
                        OCRResult result = performOCR(doc);
                        return new DocumentOCRResult(
                                doc.getId(),
                                doc.getFileName(),
                                true,
                                result,
                                null
                        );
                    } catch (Exception e) {
                        log.error("OCR failed for document: {}", doc.getFileName(), e);
                        return new DocumentOCRResult(
                                doc.getId(),
                                doc.getFileName(),
                                false,
                                null,
                                e.getMessage()
                        );
                    }
                }))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<DocumentOCRResult> results = futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList());

                    int successCount = (int) results.stream().filter(DocumentOCRResult::isSuccess).count();
                    int failureCount = results.size() - successCount;
                    long totalTime = System.currentTimeMillis() - startTime;

                    log.info("Batch OCR completed. Success: {}, Failed: {}, Time: {}ms",
                            successCount, failureCount, totalTime);

                    return new BatchOCRResult(results, successCount, failureCount, totalTime);
                });
    }

    @Override
    public Map<String, Object> extractStructuredData(String ocrText, String documentType)
            throws OCRException {
        try {
            // Call external API for structured extraction
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", ocrText);
            requestBody.put("documentType", documentType);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    ocrApiEndpoint + "/extract",
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            } else {
                throw new OCRException("Structured data extraction failed with status: " +
                        response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to extract structured data", e);
            // Fallback to basic pattern matching
            return performBasicExtraction(ocrText, documentType);
        }
    }

    @Override
    public LayoutOCRResult performLayoutOCR(MultipartFile file, LayoutOptions options)
            throws OCRException {
        long startTime = System.currentTimeMillis();

        try {
            // Prepare multipart request for layout OCR
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            body.add("options", objectMapper.writeValueAsString(options));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    ocrApiEndpoint + "/layout",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseLayoutOCRResponse(response.getBody(),
                        System.currentTimeMillis() - startTime);
            } else {
                throw new OCRException("Layout OCR failed with status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Layout OCR processing failed", e);
            throw new OCRException("Layout OCR processing failed", e);
        }
    }

    @Override
    public ValidationResult validateFile(MultipartFile file) {
        List<String> issues = new ArrayList<>();

        // Check file size
        if (file.getSize() > maxFileSize) {
            issues.add("File size exceeds maximum allowed size of " +
                    (maxFileSize / 1024 / 1024) + "MB");
        }

        // Check file type
        String contentType = file.getContentType();
        if (contentType == null || !supportedFormats.contains(contentType)) {
            issues.add("Unsupported file format: " + contentType);
        }

        // Check if file is empty
        if (file.isEmpty()) {
            issues.add("File is empty");
        }

        // Check filename
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            issues.add("Invalid filename");
        }

        return new ValidationResult(issues.isEmpty(), issues);
    }

    @Override
    public InputStream preprocessImage(InputStream inputStream,
                                       PreprocessingOptions options) throws OCRException {
        try {
            // For now, return the original stream
            // In a real implementation, this would apply image processing
            log.debug("Image preprocessing requested with options: deskew={}, removeNoise={}",
                    options.isDeskew(), options.isRemoveNoise());

            // TODO: Implement actual image preprocessing
            // - Deskew
            // - Noise removal
            // - Contrast enhancement
            // - Grayscale conversion
            // - DPI adjustment

            return inputStream;

        } catch (Exception e) {
            log.error("Image preprocessing failed", e);
            throw new OCRException("Image preprocessing failed", e);
        }
    }

    @Override
    public List<String> getSupportedFormats() {
        return new ArrayList<>(supportedFormats);
    }

    @Override
    public int estimateProcessingTime(long fileSize, int pageCount) {
        // Basic estimation formula
        int baseTime = 2; // 2 seconds base
        int perPageTime = 3; // 3 seconds per page
        int perMbTime = 1; // 1 second per MB

        int sizeBasedTime = (int) (fileSize / (1024 * 1024)) * perMbTime;
        int pageBasedTime = pageCount * perPageTime;

        return baseTime + Math.max(sizeBasedTime, pageBasedTime);
    }

    // Private helper methods

    private OCRResult performLocalPdfExtraction(InputStream inputStream, String fileName) {
        try {
            PDDocument document = PDDocument.load(inputStream);
            PDFTextStripper stripper = new PDFTextStripper();

            String text = stripper.getText(document);
            int pageCount = document.getNumberOfPages();
            document.close();

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("extractionMethod", "local_pdf");
            metadata.put("fileName", fileName);

            return new OCRResult(
                    text,
                    1.0, // High confidence for direct PDF text extraction
                    "en", // Assume English
                    pageCount,
                    metadata,
                    System.currentTimeMillis()
            );

        } catch (Exception e) {
            log.warn("Local PDF extraction failed, will try external OCR", e);
            return null;
        }
    }

    private OCRResult callExternalOCR(InputStream inputStream, String fileName,
                                      String contentType, long startTime) throws OCRException {
        try {
            // Prepare multipart request
            byte[] fileBytes = inputStream.readAllBytes();
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            });

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    ocrApiEndpoint,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseOCRResponse(response.getBody(), System.currentTimeMillis() - startTime);
            } else {
                throw new OCRException("External OCR API returned status: " +
                        response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("External OCR API call failed", e);
            throw new OCRException("External OCR processing failed", e);
        }
    }

    private OCRResult parseOCRResponse(Map<String, Object> response, long processingTime) {
        String text = (String) response.getOrDefault("text", "");
        Double confidence = ((Number) response.getOrDefault("confidence", 0.0)).doubleValue();
        String language = (String) response.getOrDefault("language", "en");
        Integer pageCount = ((Number) response.getOrDefault("pages", 1)).intValue();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("processingEngine", response.getOrDefault("engine", "unknown"));
        metadata.put("apiVersion", response.getOrDefault("version", "1.0"));

        return new OCRResult(text, confidence, language, pageCount, metadata, processingTime);
    }

    private LayoutOCRResult parseLayoutOCRResponse(Map<String, Object> response,
                                                   long processingTime) {
        // Parse basic OCR result
        OCRResult baseResult = parseOCRResponse(response, processingTime);

        // Parse layout-specific data
        List<TextBlock> textBlocks = parseTextBlocks(
                (List<Map<String, Object>>) response.getOrDefault("textBlocks", new ArrayList<>())
        );

        List<Table> tables = parseTables(
                (List<Map<String, Object>>) response.getOrDefault("tables", new ArrayList<>())
        );

        Map<String, BoundingBox> fields = parseFields(
                (Map<String, Map<String, Object>>) response.getOrDefault("fields", new HashMap<>())
        );

        return new LayoutOCRResult(
                baseResult.getExtractedText(),
                baseResult.getConfidence(),
                baseResult.getLanguage(),
                baseResult.getPageCount(),
                baseResult.getMetadata(),
                baseResult.getProcessingTimeMs(),
                textBlocks,
                tables,
                fields
        );
    }

    private List<TextBlock> parseTextBlocks(List<Map<String, Object>> textBlockData) {
        return textBlockData.stream()
                .map(data -> new TextBlock(
                        (String) data.get("text"),
                        parseBoundingBox((Map<String, Object>) data.get("boundingBox")),
                        ((Number) data.getOrDefault("confidence", 0.0)).doubleValue(),
                        ((Number) data.getOrDefault("page", 1)).intValue()
                ))
                .collect(Collectors.toList());
    }

    private List<Table> parseTables(List<Map<String, Object>> tableData) {
        return tableData.stream()
                .map(data -> new Table(
                        (List<List<String>>) data.get("cells"),
                        parseBoundingBox((Map<String, Object>) data.get("boundingBox")),
                        ((Number) data.getOrDefault("page", 1)).intValue()
                ))
                .collect(Collectors.toList());
    }

    private Map<String, BoundingBox> parseFields(Map<String, Map<String, Object>> fieldData) {
        Map<String, BoundingBox> fields = new HashMap<>();
        fieldData.forEach((fieldName, boxData) -> {
            fields.put(fieldName, parseBoundingBox(boxData));
        });
        return fields;
    }

    private BoundingBox parseBoundingBox(Map<String, Object> boxData) {
        if (boxData == null) return null;

        return new BoundingBox(
                ((Number) boxData.getOrDefault("x", 0)).intValue(),
                ((Number) boxData.getOrDefault("y", 0)).intValue(),
                ((Number) boxData.getOrDefault("width", 0)).intValue(),
                ((Number) boxData.getOrDefault("height", 0)).intValue()
        );
    }

    private Map<String, Object> performBasicExtraction(String ocrText, String documentType) {
        Map<String, Object> extracted = new HashMap<>();

        if ("INVOICE".equalsIgnoreCase(documentType)) {
            // Basic invoice field extraction using regex patterns
            extracted.put("invoiceNumber", extractPattern(ocrText,
                    "(?i)invoice\\s*#?\\s*:?\\s*([A-Z0-9-]+)", 1));
            extracted.put("date", extractPattern(ocrText,
                    "(?i)date\\s*:?\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})", 1));
            extracted.put("total", extractPattern(ocrText,
                    "(?i)total\\s*:?\\s*\\$?([0-9,]+\\.?\\d{0,2})", 1));
        }

        return extracted;
    }

    private String extractPattern(String text, String pattern, int group) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                return m.group(group);
            }
        } catch (Exception e) {
            log.debug("Pattern extraction failed for pattern: {}", pattern);
        }
        return null;
    }

    @Override
    public String extractText(Document document) {
        log.info("Extracting text from document: {}", document.getFileName());

        try {
            if (document.getFileType().toLowerCase().contains("pdf")) {
                return extractTextFromPDF(document.getFilePath());
            } else {
                // For images, we would integrate with Tesseract or another OCR service
                // For now, return a placeholder
                log.warn("Image OCR not yet implemented for file type: {}", document.getFileType());
                return "OCR processing for images coming soon...";
            }
        } catch (Exception e) {
            log.error("Error extracting text from document: {}", document.getFileName(), e);
            throw new RuntimeException("Failed to extract text from document", e);
        }
    }

    private String extractTextFromPDF(String filePath) throws IOException {
        // FIX: Use the modern Loader class
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}