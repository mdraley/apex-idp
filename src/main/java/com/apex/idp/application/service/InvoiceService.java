// File: src/main/java/com/apex/idp/application/service/InvoiceService.java
package com.apex.idp.application.service;

import com.apex.idp.domain.invoice.DomainInvoiceService;
import com.apex.idp.domain.invoice.Invoice;
import com.apex.idp.domain.invoice.InvoiceRepository;
import com.apex.idp.interfaces.dto.InvoiceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        // Create invoice entity
        Invoice invoice = new Invoice();
        invoice.setDocumentId(documentId);
        invoice.setInvoiceNumber(invoiceDTO.getInvoiceNumber());
        invoice.setAmount(invoiceDTO.getAmount());
        invoice.setInvoiceDate(invoiceDTO.getInvoiceDate());
        invoice.setDueDate(invoiceDTO.getDueDate());

        // Use domain service for validation
        domainInvoiceService.validateInvoice(invoice);

        // Link to vendor if exists
        if (invoiceDTO.getVendorId() != null) {
            invoice.setVendorId(invoiceDTO.getVendorId());
        }

        // Save invoice
        invoice = invoiceRepository.save(invoice);

        // Update document status
        documentService.updateDocumentStatus(documentId, DocumentStatus.PROCESSED);

        log.info("Invoice {} processed successfully", invoice.getInvoiceNumber());
        return invoice;
    }

    /**
     * Get invoice with calculated fields
     */
    public InvoiceDTO getInvoiceWithDetails(String invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new EntityNotFoundException("Invoice not found: " + invoiceId));

        InvoiceDTO dto = mapToDTO(invoice);

        // Add domain calculations
        dto.setOverdue(domainInvoiceService.isOverdue(invoice));
        dto.setTotalWithTax(domainInvoiceService.calculateTotalWithTax(invoice, new BigDecimal("0.08")));

        return dto;
    }

    private InvoiceDTO mapToDTO(Invoice invoice) {
        // Mapping logic here
        return InvoiceDTO.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .amount(invoice.getAmount())
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .status(invoice.getStatus())
                .build();
    }
}