package com.apex.idp.application.service;

import com.apex.idp.domain.batch.Batch;
import com.apex.idp.domain.batch.BatchRepository;
import com.apex.idp.domain.document.Document;
import com.apex.idp.domain.document.DocumentRepository;
import com.apex.idp.domain.invoice.Invoice;
import com.apex.idp.domain.invoice.InvoiceRepository;
import com.apex.idp.domain.invoice.InvoiceService;
import com.apex.idp.infrastructure.kafka.BatchEventProducer;
import com.apex.idp.infrastructure.ocr.OCRService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final InvoiceService invoiceService;
    private final OCRService ocrService;
    private final BatchEventProducer batchEventProducer;

    public void processDocument(String documentId) {
        log.info("Processing document: {}", documentId);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        try {
            document.startProcessing();

            // Extract text using OCR
            String extractedText = ocrService.extractText(document);
            document.completeProcessing(extractedText);

            // Create and process invoice
            Invoice invoice = Invoice.create(document);

            // Extract invoice data from OCR text
            extractInvoiceData(invoice, extractedText);

            invoiceRepository.save(invoice);
            documentRepository.save(document);

            // Update batch progress
            Batch batch = document.getBatch();
            batch.incrementProcessedCount();
            batchRepository.save(batch);

            // Send completion event
            batchEventProducer.sendDocumentProcessedEvent(document);

        } catch (Exception e) {
            log.error("Failed to process document: {}", documentId, e);
            document.failProcessing();
            documentRepository.save(document);
            throw new RuntimeException("Document processing failed", e);
        }
    }

    private void extractInvoiceData(Invoice invoice, String ocrText) {
        // Simple extraction logic - in production, use AI/ML models
        String invoiceNumber = extractPattern(ocrText, "Invoice\\s*#?\\s*:?\\s*(\\S+)", 1);
        String vendorName = extractPattern(ocrText, "From:?\\s*([^\\n]+)", 1);
        String amountStr = extractPattern(ocrText, "Total:?\\s*\\$?([0-9,]+\\.?[0-9]*)", 1);

        BigDecimal amount = null;
        if (amountStr != null) {
            amountStr = amountStr.replaceAll(",", "");
            try {
                amount = new BigDecimal(amountStr);
            } catch (NumberFormatException e) {
                log.warn("Could not parse amount: {}", amountStr);
            }
        }

        invoice.updateFromExtractedData(
                invoiceNumber != null ? invoiceNumber : "UNKNOWN",
                LocalDate.now(), // In production, extract from OCR
                LocalDate.now().plusDays(30), // In production, extract from OCR
                amount != null ? amount : BigDecimal.ZERO
        );

        // Assign vendor if found
        if (vendorName != null && !vendorName.trim().isEmpty()) {
            invoiceService.assignVendor(invoice, vendorName.trim());
        }
    }

    private String extractPattern(String text, String pattern, int group) {
        try {
            Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) {
                return m.group(group).trim();
            }
        } catch (Exception e) {
            log.warn("Pattern extraction failed for pattern: {}", pattern, e);
        }
        return null;
    }
}