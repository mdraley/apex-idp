package com.apex.idp.application.service;

import com.apex.idp.domain.batch.Batch;
import com.apex.idp.domain.batch.BatchRepository;
import com.apex.idp.domain.document.Document;
import com.apex.idp.domain.document.DocumentRepository;
import com.apex.idp.domain.document.DocumentStatus;
import com.apex.idp.domain.invoice.Invoice;
import com.apex.idp.domain.invoice.InvoiceRepository;
import com.apex.idp.domain.invoice.InvoiceStatus;
import com.apex.idp.domain.vendor.Vendor;
import com.apex.idp.domain.vendor.VendorRepository;
import com.apex.idp.infrastructure.kafka.BatchEventProducer;
import com.apex.idp.infrastructure.ocr.OCRResult;
import com.apex.idp.infrastructure.ocr.OCRService;
import com.apex.idp.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application service for document processing operations.
 * Orchestrates document OCR, invoice extraction, and workflow management.
 */
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
    private final InvoiceExtractionService invoiceExtractionService;

    @Value("${document.processing.retry-count:3}")
    private int maxRetryCount;

    /**
     * Processes a document through OCR and invoice extraction.
     */
    @Transactional
    public Document processDocument(String documentId) {
        log.info("Processing document: {}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));

        try {
            // Update status
            document.startProcessing();
            documentRepository.save(document);

            // Perform OCR
            OCRResult ocrResult = performOCR(document);
            document.setExtractedText(ocrResult.getText());
            document.setOcrConfidence(ocrResult.getConfidence());

            // Extract invoice data using dedicated service
            Invoice invoice = invoiceExtractionService.extractInvoice(document, ocrResult.getText());
            invoiceRepository.save(invoice);

            // Set invoice relationship
            document.getInvoices().add(invoice);

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
            throw new DocumentProcessingException("Document processing failed", e);
        }
    }

    /**
     * Reprocesses a document (for retry logic).
     */
    @Transactional
    public Document reprocessDocument(String documentId) {
        log.info("Reprocessing document: {}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));

        // Check retry count
        if (document.getRetryCount() >= maxRetryCount) {
            log.error("Max retry count reached for document: {}", documentId);
            document.failProcessing("Max retry count exceeded");
            return documentRepository.save(document);
        }

        // Increment retry count and reprocess
        document.incrementRetryCount();
        documentRepository.save(document);

        return processDocument(documentId);
    }

    /**
     * Retrieves a document by ID.
     */
    @Transactional(readOnly = true)
    public Document getDocument(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));
    }

    /**
     * Updates document status.
     */
    public void updateDocumentStatus(String documentId, DocumentStatus status) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));

        document.setStatus(status);
        documentRepository.save(document);

        log.info("Document {} status updated to {}", documentId, status);
    }

    /**
     * Performs OCR on document.
     */
    private OCRResult performOCR(Document document) throws Exception {
        log.debug("Starting OCR for document: {}", document.getId());

        // Retrieve document from storage
        try (InputStream fileStream = storageService.retrieve(document.getFilePath())) {
            OCRResult result = ocrService.performOCR(fileStream, document.getContentType());

            log.info("OCR completed for document: {} with confidence: {}%",
                    document.getId(), result.getConfidence() * 100);

            return result;
        } catch (Exception e) {
            log.error("OCR failed for document: {}", document.getId(), e);
            throw new OCRException("OCR processing failed", e);
        }
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

            boolean hasFailures = batch.getDocuments().stream()
                    .anyMatch(doc -> doc.getStatus() == DocumentStatus.FAILED);

            if (hasFailures) {
                log.warn("Batch {} completed with failures", batch.getId());
            }

            // Trigger analysis
            batchEventProducer.sendBatchOCRCompletedEvent(batch.getId());
        }
    }

    /**
     * Gets documents by batch ID.
     */
    @Transactional(readOnly = true)
    public List<Document> getDocumentsByBatch(String batchId) {
        return documentRepository.findByBatchId(batchId);
    }

    /**
     * Gets document count by status.
     */
    @Transactional(readOnly = true)
    public long getDocumentCountByStatus(DocumentStatus status) {
        return documentRepository.findByStatus(status).size();
    }

    /**
     * Custom exceptions
     */
    public static class DocumentProcessingException extends RuntimeException {
        public DocumentProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class OCRException extends Exception {
        public OCRException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}