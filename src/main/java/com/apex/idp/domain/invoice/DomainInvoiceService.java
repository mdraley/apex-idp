// File: src/main/java/com/apex/idp/domain/invoice/DomainInvoiceService.java
package com.apex.idp.domain.invoice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Domain service for invoice business logic.
 * Handles domain-specific invoice operations and business rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DomainInvoiceService {

    private final InvoiceRepository invoiceRepository;

    /**
     * Validates invoice according to business rules
     */
    public void validateInvoice(Invoice invoice) {
        if (invoice.getAmount() == null || invoice.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invoice amount must be greater than zero");
        }

        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("Invoice number is required");
        }

        // Additional domain validation logic
        log.debug("Invoice {} validated successfully", invoice.getInvoiceNumber());
    }

    /**
     * Calculates total amount including tax
     */
    public BigDecimal calculateTotalWithTax(Invoice invoice, BigDecimal taxRate) {
        BigDecimal amount = invoice.getAmount();
        BigDecimal tax = amount.multiply(taxRate);
        return amount.add(tax);
    }

    /**
     * Checks if invoice is overdue
     */
    public boolean isOverdue(Invoice invoice) {
        if (invoice.getDueDate() == null) {
            return false;
        }
        return LocalDate.now().isAfter(invoice.getDueDate());
    }
}