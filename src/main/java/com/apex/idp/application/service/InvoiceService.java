package com.apex.idp.application.service;

import com.apex.idp.domain.document.DocumentStatus;
import com.apex.idp.domain.invoice.DomainInvoiceService;
import com.apex.idp.domain.invoice.Invoice;
import com.apex.idp.domain.invoice.InvoiceRepository;
import com.apex.idp.interfaces.dto.InvoiceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Application service for invoice operations.
 * Orchestrates use cases and coordinates between domain services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final DomainInvoiceService domainInvoiceService;
    private final DocumentService documentService;
    private final VendorService vendorService;

    /**
     * Process a new invoice from document
     */
    public Invoice processInvoice(String documentId, InvoiceDTO invoiceDTO) {
        log.info("Processing invoice from document: {}", documentId);

        try {
            // Fetch the document to create invoice relationship
            var document = documentService.getDocument(documentId);

            // Create invoice using static factory method
            Invoice invoice = Invoice.create(document);

            // Set invoice properties
            invoice.setInvoiceNumber(invoiceDTO.getInvoiceNumber());
            invoice.setAmount(invoiceDTO.getAmount());

            // Convert String dates to LocalDate if needed
            if (invoiceDTO.getInvoiceDate() != null) {
                invoice.setInvoiceDate(LocalDate.parse(invoiceDTO.getInvoiceDate()));
            }
            if (invoiceDTO.getDueDate() != null) {
                invoice.setDueDate(LocalDate.parse(invoiceDTO.getDueDate()));
            }

            // Use domain service for validation
            domainInvoiceService.validateInvoice(invoice);

            // Link to vendor if exists
            if (invoiceDTO.getVendorId() != null) {
                var vendor = vendorService.getVendorById(invoiceDTO.getVendorId());
                invoice.setVendor(vendor);
            }

            // Save invoice
            invoice = invoiceRepository.save(invoice);

            // Update document status
            documentService.updateDocumentStatus(documentId, DocumentStatus.PROCESSED);

            log.info("Invoice {} processed successfully", invoice.getInvoiceNumber());
            return invoice;

        } catch (Exception e) {
            log.error("Error processing invoice for document: {}", documentId, e);
            throw new RuntimeException("Failed to process invoice", e);
        }
    }

    /**
     * Get invoice with calculated fields
     */
    public InvoiceDTO getInvoiceWithDetails(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + invoiceId));

        InvoiceDTO dto = InvoiceDTO.from(invoice);

        // Add domain calculations
        dto.setOverdue(domainInvoiceService.isOverdue(invoice));
        dto.setTotalWithTax(domainInvoiceService.calculateTotalWithTax(invoice, new BigDecimal("0.08")));

        return dto;
    }

    /**
     * Get all invoices for a batch
     */
    @Transactional(readOnly = true)
    public List<InvoiceDTO> getInvoicesByBatch(String batchId) {
        log.debug("Fetching invoices for batch: {}", batchId);

        return invoiceRepository.findByBatchId(batchId).stream()
                .map(InvoiceDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * Get invoice by ID
     */
    @Transactional(readOnly = true)
    public InvoiceDTO getInvoice(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + invoiceId));

        return InvoiceDTO.from(invoice);
    }

    /**
     * Update invoice status
     */
    public InvoiceDTO updateInvoiceStatus(String invoiceId, String status) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + invoiceId));

        // Update status based on business logic
        switch (status.toUpperCase()) {
            case "APPROVED":
                invoice.approve();
                break;
            case "REJECTED":
                invoice.reject("Manual rejection");
                break;
            default:
                throw new IllegalArgumentException("Invalid status: " + status);
        }

        invoice = invoiceRepository.save(invoice);
        return InvoiceDTO.from(invoice);
    }

    /**
     * Get total invoice count
     */
    @Transactional(readOnly = true)
    public long getTotalInvoiceCount() {
        return invoiceRepository.countAllInvoices();
    }
}