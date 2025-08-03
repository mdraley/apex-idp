package com.apex.idp.domain.vendor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service for vendor-related business logic.
 * Contains vendor domain-specific operations and business rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DomainVendorService {

    private final VendorRepository vendorRepository;
    private final VendorValidator vendorValidator;

    /**
     * Creates a new vendor or updates an existing one by name.
     * Validates input data before processing.
     */
    public Vendor createOrUpdateVendor(String name, String email, String phone, String address) {
        log.debug("Creating or updating vendor with name: {}", name);

        if (!vendorValidator.isValidVendorName(name)) {
            throw new IllegalArgumentException("Invalid vendor name: " + name);
        }

        if (!vendorValidator.isValidEmail(email)) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }

        if (!vendorValidator.isValidPhone(phone)) {
            throw new IllegalArgumentException("Invalid phone format: " + phone);
        }

        return vendorRepository.findByName(name)
                .map(vendor -> {
                    log.debug("Updating existing vendor: {}", vendor.getId());
                    vendor.updateContactInfo(email, phone, address);
                    return vendor;
                })
                .orElseGet(() -> {
                    log.debug("Creating new vendor with name: {}", name);
                    Vendor newVendor = Vendor.create(name);
                    newVendor.updateContactInfo(email, phone, address);
                    return vendorRepository.save(newVendor);
                });
    }

    /**
     * Checks if a vendor can be deleted based on business rules.
     */
    public boolean canDeleteVendor(Vendor vendor) {
        log.debug("Checking if vendor can be deleted: {}", vendor.getId());
        // Business logic for deletion validation
        // Check for active invoices, dependencies, etc.
        return vendor.getStatus() != VendorStatus.ACTIVE || vendor.getInvoiceCount() == 0;
    }

    /**
     * Validator class for vendor data validation.
     */
    @Service
    public static class VendorValidator {

        /**
         * Validates vendor name format.
         */
        public boolean isValidVendorName(String name) {
            return name != null && !name.trim().isEmpty() && name.length() <= 255;
        }

        /**
         * Validates vendor email format.
         */
        public boolean isValidEmail(String email) {
            if (email == null || email.trim().isEmpty()) {
                return true; // Optional field
            }
            // Simple email validation
            return email.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$");
        }

        /**
         * Validates vendor phone format.
         */
        public boolean isValidPhone(String phone) {
            if (phone == null || phone.trim().isEmpty()) {
                return true; // Optional field
            }
            // Simple phone validation - allows various formats
            return phone.matches("^[0-9\\+\\-\\(\\) ]{6,20}$");
        }
    }
}