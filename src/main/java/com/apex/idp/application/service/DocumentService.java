package com.apex.idp.application.service;

import com.apex.idp.domain.batch.Batch;
import com.apex.idp.domain.batch.BatchRepository;
import com.apex.idp.domain.document.Document;
import com.apex.idp.domain.document.DocumentRepository;
import com.apex.idp.domain.invoice.Invoice;
import com.apex.idp.domain.invoice.InvoiceRepository;
import com.apex.idp.domain.invoice.InvoiceStatus;
import com.apex.idp.domain.vendor.Vendor;
import com.apex.idp.domain.vendor.VendorRepository;
import com.apex.idp.domain.vendor.VendorStatus;
import com.apex.idp.infrastructure.kafka.BatchEventProducer;
import com.apex.idp.infrastructure.ocr.OCRService;
import com.apex.idp.infrastructure.storage.MinIOStorageService;
import com.apex.idp.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final BatchRepository batchRepository;
    private final InvoiceRepository invoiceRepository;
    private final VendorRepository vendorRepository;
    private final OCRService ocrService;
    private final StorageService storageService;
    private final BatchEventProducer batchEventProducer;

    @Value("${document.processing.retry-count:3}")
    private int maxRetryCount;

    // Regex patterns for invoice data extraction
    private static final Pattern INVOICE_NUMBER_PATTERN = Pattern.compile(
            "(?i)(?:invoice|inv|bill)\\s*(?:#|no\\.?|number)?\\s*[:‑-]?\\s*([A-Z0-9\\-]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?i)(?:total|amount|balance|due)\\s*[:‑-]?\\s*\\$?\\s*([0-9,]+\\.?[0-9]*)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(?i)(?:date|dated|invoice date)\\s*[:‑-]?\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern VENDOR_PATTERN = Pattern.compile(
            "(?i)(?:from|vendor|supplier|company)\\s*[:‑-]?\\s*([A-Za-z0-9\\s&.,]+?)(?:\\n|$)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Processes a document through OCR and invoice extraction.
     */
    @Transactional
    public Document processDocument(String documentId) {
        log.info("Processing document: {}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        try {
            // Update status
            document.startProcessing();
            documentRepository.save(document);

            // Perform OCR
            String extractedText = performOCR(document);
            document.setExtractedText(extractedText);

            // Extract invoice data
            Invoice invoice = extractInvoiceData(document, extractedText);
            invoiceRepository.save(invoice);

            // Mark as completed
            document.completeProcessing();
            Document savedDocument = documentRepository.save(document);

            // Update batch status if all documents are processed
            checkAndUpdateBatchStatus(document.getBatch());

            log.info("Document processed successfully: {}", documentId);
            return savedDocument;

        } catch (Exception e) {
            log.error("Error processing document: {}", documentId, e);
            document.failProcessing(e.getMessage());
            documentRepository.save(document);
            throw new RuntimeException("Document processing failed", e);
        }
    }

    /**
     * Retrieves a document by ID.
     */
    @Transactional(readOnly = true)
    public Optional<Document> getDocument(String documentId) {
        return documentRepository.findById(documentId);
    }

    /**
     * Retrieves documents for a batch.
     */
    @Transactional(readOnly = true)
    public List<Document> getDocumentsByBatchId(String batchId) {
        return documentRepository.findByBatchId(batchId);
    }

    /**
     * Performs OCR on a document.
     */
    private String performOCR(Document document) throws Exception {
        log.debug("Performing OCR on document: {}", document.getId());

        try {
            // FIX: Use the correct storage service method
            InputStream fileStream;
            if (storageService instanceof MinIOStorageService) {
                fileStream = ((MinIOStorageService) storageService).retrieve(document.getFilePath());
            } else {
                fileStream = storageService.retrieveDocument(null, document.getFilePath())
                        .orElseThrow(() -> new RuntimeException("File not found: " + document.getFilePath()));
            }

            try (fileStream) {
                OCRService.OCRResult result = ocrService.performOCR(
                        fileStream,
                        document.getFileName(),
                        document.getContentType()
                );

                // FIX: Use correct OCRResult methods
                if (result.getConfidence() < 0.5) {
                    throw new RuntimeException("OCR confidence too low: " + result.getConfidence());
                }

                log.debug("OCR completed for document: {}, confidence: {}",
                        document.getId(), result.getConfidence());

                return result.getExtractedText();
            }

        } catch (Exception e) {
            log.error("OCR failed for document: {}", document.getId(), e);
            throw new RuntimeException("OCR processing failed", e);
        }
    }

    /**
     * Creates and processes invoice from extracted text.
     */
    private Invoice extractInvoiceData(Document document, String extractedText) {
        log.debug("Extracting invoice data from document: {}", document.getId());

        Invoice invoice = Invoice.create(document);

        // Extract invoice number
        Matcher invoiceNumberMatcher = INVOICE_NUMBER_PATTERN.matcher(extractedText);
        if (invoiceNumberMatcher.find()) {
            invoice.setInvoiceNumber(invoiceNumberMatcher.group(1).trim());
        }

        // Extract amount
        Matcher amountMatcher = AMOUNT_PATTERN.matcher(extractedText);
        if (amountMatcher.find()) {
            String amountStr = amountMatcher.group(1).replaceAll(",", "");
            try {
                invoice.setAmount(new BigDecimal(amountStr));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse amount: {}", amountStr);
            }
        }

        // Extract date
        Matcher dateMatcher = DATE_PATTERN.matcher(extractedText);
        if (dateMatcher.find()) {
            String dateStr = dateMatcher.group(1);
            LocalDate invoiceDate = parseDate(dateStr);
            if (invoiceDate != null) {
                invoice.setInvoiceDate(invoiceDate);
            }
        }

        // Extract vendor
        Matcher vendorMatcher = VENDOR_PATTERN.matcher(extractedText);
        if (vendorMatcher.find()) {
            String vendorName = vendorMatcher.group(1).trim();
            Vendor vendor = findOrCreateVendor(vendorName);
            invoice.setVendor(vendor);
        }

        // FIX: Use correct InvoiceStatus enum
        invoice.setStatus(InvoiceStatus.PENDING);

        return invoice;
    }

    /**
     * Parses various date formats.
     */
    private LocalDate parseDate(String dateStr) {
        String[] patterns = {
                "MM/dd/yyyy", "MM-dd-yyyy", "MM/dd/yy", "MM-dd-yy",
                "yyyy/MM/dd", "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy"
        };

        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next pattern
            }
        }

        log.warn("Unable to parse date: {}", dateStr);
        return null;
    }

    /**
     * Finds or creates a vendor.
     */
    private Vendor findOrCreateVendor(String vendorName) {
        return vendorRepository.findByName(vendorName)
                .orElseGet(() -> {
                    Vendor newVendor = Vendor.create(vendorName);
                    // FIX: Use the correct method to set status
                    if (newVendor.getStatus() != VendorStatus.ACTIVE) {
                        newVendor.activate();
                    }
                    return vendorRepository.save(newVendor);
                });
    }

    /**
     * Checks and updates batch status if all documents are processed.
     */
    private void checkAndUpdateBatchStatus(Batch batch) {
        long processedCount = batch.getDocuments().stream()
                .filter(doc -> doc.getStatus().isTerminal())
                .count();

        if (processedCount == batch.getDocuments().size()) {
            // All documents processed
            batch.completeOCR();
            batchRepository.save(batch);

            // FIX: Use correct InvoiceStatus enum
            boolean hasFailures = batch.getDocuments().stream()
                    .anyMatch(doc -> doc.getInvoices().stream()
                            .anyMatch(inv -> inv.getStatus() == InvoiceStatus.EXTRACTION_FAILED));

            if (hasFailures) {
                log.warn("Batch {} completed with failures", batch.getId());
            }

            // Trigger analysis
            batchEventProducer.sendBatchOCRCompletedEvent(batch.getId());
        }
    }
}