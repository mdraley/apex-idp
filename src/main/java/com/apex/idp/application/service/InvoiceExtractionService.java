package com.apex.idp.application.service;

import com.apex.idp.domain.document.Document;
import com.apex.idp.domain.invoice.Invoice;
import com.apex.idp.domain.invoice.InvoiceStatus;
import com.apex.idp.domain.vendor.Vendor;
import com.apex.idp.domain.vendor.VendorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service dedicated to extracting invoice information from OCR text.
 * Implements pattern matching and parsing logic for invoice data extraction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceExtractionService {

    private final VendorRepository vendorRepository;
    private final VendorService vendorService;

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

    private static final Pattern DUE_DATE_PATTERN = Pattern.compile(
            "(?i)(?:due date|payment due|pay by)\\s*[:‑-]?\\s*(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}[/-]\\d{1,2}[/-]\\d{1,2})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PO_NUMBER_PATTERN = Pattern.compile(
            "(?i)(?:po|purchase order)\\s*(?:#|no\\.?|number)?\\s*[:‑-]?\\s*([A-Z0-9\\-]+)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Extracts invoice data from OCR text.
     */
    public Invoice extractInvoice(Document document, String extractedText) {
        log.debug("Extracting invoice data from document: {}", document.getId());

        Invoice invoice = Invoice.create(document);

        try {
            // Extract invoice number
            extractInvoiceNumber(extractedText).ifPresent(invoice::setInvoiceNumber);

            // Extract amount
            extractAmount(extractedText).ifPresent(invoice::setAmount);

            // Extract invoice date
            extractInvoiceDate(extractedText).ifPresent(invoice::setInvoiceDate);

            // Extract due date
            extractDueDate(extractedText).ifPresent(invoice::setDueDate);

            // Extract vendor
            extractVendor(extractedText).ifPresent(invoice::setVendor);

            // Extract PO number if present
            extractPONumber(extractedText).ifPresent(po -> {
                if (invoice.getNotes() == null) {
                    invoice.setNotes("PO Number: " + po);
                } else {
                    invoice.setNotes(invoice.getNotes() + "\nPO Number: " + po);
                }
            });

            // Set initial status based on extraction success
            if (invoice.getInvoiceNumber() != null && invoice.getAmount() != null) {
                invoice.setStatus(InvoiceStatus.PENDING);
            } else {
                invoice.setStatus(InvoiceStatus.EXTRACTION_FAILED);
                log.warn("Invoice extraction incomplete for document: {}", document.getId());
            }

        } catch (Exception e) {
            log.error("Error extracting invoice data from document: {}", document.getId(), e);
            invoice.setStatus(InvoiceStatus.EXTRACTION_FAILED);
            invoice.setNotes("Extraction error: " + e.getMessage());
        }

        return invoice;
    }

    /**
     * Extracts invoice number from text.
     */
    private Optional<String> extractInvoiceNumber(String text) {
        Matcher matcher = INVOICE_NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            String invoiceNumber = matcher.group(1).trim();
            log.debug("Extracted invoice number: {}", invoiceNumber);
            return Optional.of(invoiceNumber);
        }
        return Optional.empty();
    }

    /**
     * Extracts amount from text.
     */
    private Optional<BigDecimal> extractAmount(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        if (matcher.find()) {
            String amountStr = matcher.group(1).replaceAll(",", "");
            try {
                BigDecimal amount = new BigDecimal(amountStr);
                log.debug("Extracted amount: {}", amount);
                return Optional.of(amount);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse amount: {}", amountStr);
            }
        }
        return Optional.empty();
    }

    /**
     * Extracts invoice date from text.
     */
    private Optional<LocalDate> extractInvoiceDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            String dateStr = matcher.group(1);
            Optional<LocalDate> date = parseDate(dateStr);
            date.ifPresent(d -> log.debug("Extracted invoice date: {}", d));
            return date;
        }
        return Optional.empty();
    }

    /**
     * Extracts due date from text.
     */
    private Optional<LocalDate> extractDueDate(String text) {
        Matcher matcher = DUE_DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            String dateStr = matcher.group(1);
            Optional<LocalDate> date = parseDate(dateStr);
            date.ifPresent(d -> log.debug("Extracted due date: {}", d));
            return date;
        }
        return Optional.empty();
    }

    /**
     * Extracts vendor from text.
     */
    private Optional<Vendor> extractVendor(String text) {
        Matcher matcher = VENDOR_PATTERN.matcher(text);
        if (matcher.find()) {
            String vendorName = matcher.group(1).trim();
            log.debug("Extracted vendor name: {}", vendorName);
            return Optional.of(findOrCreateVendor(vendorName));
        }
        return Optional.empty();
    }

    /**
     * Extracts PO number from text.
     */
    private Optional<String> extractPONumber(String text) {
        Matcher matcher = PO_NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            String poNumber = matcher.group(1).trim();
            log.debug("Extracted PO number: {}", poNumber);
            return Optional.of(poNumber);
        }
        return Optional.empty();
    }

    /**
     * Parses various date formats.
     */
    private Optional<LocalDate> parseDate(String dateStr) {
        String[] patterns = {
                "MM/dd/yyyy", "MM-dd-yyyy", "MM/dd/yy", "MM-dd-yy",
                "yyyy/MM/dd", "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy",
                "MMM dd, yyyy", "dd MMM yyyy", "MMMM dd, yyyy"
        };

        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                LocalDate date = LocalDate.parse(dateStr, formatter);

                // Handle 2-digit years
                if (pattern.contains("yy") && !pattern.contains("yyyy")) {
                    if (date.getYear() < 100) {
                        // Assume 00-29 is 2000-2029, 30-99 is 1930-1999
                        int year = date.getYear() < 30 ? 2000 + date.getYear() : 1900 + date.getYear();
                        date = date.withYear(year);
                    }
                }

                return Optional.of(date);
            } catch (DateTimeParseException e) {
                // Try next pattern
            }
        }

        log.warn("Unable to parse date: {}", dateStr);
        return Optional.empty();
    }

    /**
     * Finds or creates a vendor.
     */
    private Vendor findOrCreateVendor(String vendorName) {
        // Try exact match first
        Optional<Vendor> vendor = vendorRepository.findByNameIgnoreCase(vendorName);

        if (vendor.isPresent()) {
            return vendor.get();
        }

        // Try fuzzy match
        List<Vendor> similarVendors = vendorRepository.findByNameContainingIgnoreCase(vendorName);
        if (!similarVendors.isEmpty()) {
            // Return the first match (in production, implement better fuzzy matching)
            return similarVendors.get(0);
        }

        // Create new vendor
        return vendorService.createVendor(vendorName, null, null, null);
    }
}