package com.apex.idp.interfaces.rest;

import com.apex.idp.application.service.VendorService;
import com.apex.idp.domain.vendor.Vendor;
import com.apex.idp.interfaces.dto.VendorDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for vendor management operations.
 * Handles CRUD operations and vendor-related business logic.
 */
@RestController
@RequestMapping("/api/v1/vendors")
@Tag(name = "Vendor Management", description = "Vendor management APIs")
@Validated
@PreAuthorize("isAuthenticated()")
public class VendorController {

    private static final Logger log = LoggerFactory.getLogger(VendorController.class);

    private final VendorService vendorService;

    public VendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    /**
     * Gets all vendors with pagination and search.
     */
    @GetMapping
    @Operation(summary = "List vendors", description = "Get paginated list of vendors with search")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vendors retrieved successfully")
    })
    public ResponseEntity<Page<VendorDTO>> getVendors(
            @PageableDefault(size = 20, sort = "name,asc") Pageable pageable,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category) {

        log.debug("Fetching vendors - search: {}, status: {}, category: {}",
                search, status, category);

        Page<Vendor> vendors = vendorService.getVendors(pageable, search, status, category);
        Page<VendorDTO> vendorDTOs = vendors.map(this::convertToDTO);

        return ResponseEntity.ok(vendorDTOs);
    }

    /**
     * Gets a specific vendor by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get vendor", description = "Get vendor details by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vendor found"),
            @ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    public ResponseEntity<VendorDTO> getVendor(
            @PathVariable @Parameter(description = "Vendor ID") Long id) {

        log.debug("Fetching vendor with ID: {}", id);

        Optional<Vendor> vendor = vendorService.getVendorById(id);
        if (vendor.isPresent()) {
            VendorDTO vendorDTO = convertToDTO(vendor.get());
            return ResponseEntity.ok(vendorDTO);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Creates a new vendor.
     */
    @PostMapping
    @Operation(summary = "Create vendor", description = "Create a new vendor")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Vendor created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Vendor already exists")
    })
    public ResponseEntity<VendorDTO> createVendor(@Valid @RequestBody VendorDTO vendorDTO) {
        log.info("Creating new vendor: {}", vendorDTO.getName());

        try {
            // Check if vendor already exists
            if (vendorService.vendorExistsByTaxId(vendorDTO.getTaxId())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            Vendor vendor = vendorService.createVendor(vendorDTO);
            VendorDTO createdDTO = convertToDTO(vendor);

            return ResponseEntity.status(HttpStatus.CREATED).body(createdDTO);

        } catch (IllegalArgumentException e) {
            log.error("Invalid vendor creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating vendor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Updates an existing vendor.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update vendor", description = "Update vendor information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vendor updated successfully"),
            @ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    public ResponseEntity<VendorDTO> updateVendor(
            @PathVariable Long id,
            @Valid @RequestBody VendorDTO vendorDTO) {

        log.info("Updating vendor ID: {}", id);

        try {
            Vendor updatedVendor = vendorService.updateVendor(id, vendorDTO);
            VendorDTO updatedDTO = convertToDTO(updatedVendor);
            return ResponseEntity.ok(updatedDTO);

        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating vendor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Deletes a vendor.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete vendor", description = "Delete a vendor")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Vendor deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Vendor not found"),
            @ApiResponse(responseCode = "409", description = "Vendor has associated invoices")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteVendor(@PathVariable Long id) {
        log.info("Deleting vendor ID: {}", id);

        try {
            vendorService.deleteVendor(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            // Vendor has associated invoices
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            log.error("Error deleting vendor", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Updates vendor status (active/inactive).
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Update vendor status", description = "Activate or deactivate a vendor")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    public ResponseEntity<VendorDTO> updateVendorStatus(
            @PathVariable Long id,
            @RequestBody UpdateStatusRequest request) {

        log.info("Updating status for vendor ID: {} to {}", id, request.getStatus());

        try {
            Vendor updatedVendor = vendorService.updateVendorStatus(id, request.getStatus());
            VendorDTO updatedDTO = convertToDTO(updatedVendor);
            return ResponseEntity.ok(updatedDTO);

        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating vendor status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Gets vendor invoices.
     */
    @GetMapping("/{id}/invoices")
    @Operation(summary = "Get vendor invoices", description = "Get all invoices for a vendor")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoices retrieved"),
            @ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    public ResponseEntity<VendorInvoicesResponse> getVendorInvoices(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "invoiceDate,desc") Pageable pageable) {

        log.debug("Fetching invoices for vendor ID: {}", id);

        try {
            VendorInvoicesResponse response = vendorService.getVendorInvoices(id, pageable);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Gets vendor statistics.
     */
    @GetMapping("/{id}/statistics")
    @Operation(summary = "Get vendor statistics",
            description = "Get statistics and analytics for a vendor")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved"),
            @ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    public ResponseEntity<VendorStatistics> getVendorStatistics(@PathVariable Long id) {
        log.debug("Fetching statistics for vendor ID: {}", id);

        try {
            VendorStatistics stats = vendorService.getVendorStatistics(id);
            return ResponseEntity.ok(stats);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Searches vendors by name or tax ID.
     */
    @GetMapping("/search")
    @Operation(summary = "Search vendors", description = "Search vendors by name or tax ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results")
    })
    public ResponseEntity<List<VendorDTO>> searchVendors(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") Integer limit) {

        log.debug("Searching vendors with query: {}", query);

        List<Vendor> vendors = vendorService.searchVendors(query, limit);
        List<VendorDTO> vendorDTOs = vendors.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(vendorDTOs);
    }

    /**
     * Validates vendor tax ID.
     */
    @PostMapping("/validate-tax-id")
    @Operation(summary = "Validate tax ID", description = "Check if a tax ID is valid and unique")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Validation result")
    })
    public ResponseEntity<TaxIdValidationResponse> validateTaxId(
            @RequestBody TaxIdValidationRequest request) {

        boolean isValid = vendorService.isValidTaxId(request.getTaxId());
        boolean exists = vendorService.vendorExistsByTaxId(request.getTaxId());

        TaxIdValidationResponse response = new TaxIdValidationResponse(
                isValid, !exists, isValid && !exists ? null :
                (!isValid ? "Invalid tax ID format" : "Tax ID already exists")
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Gets vendor categories.
     */
    @GetMapping("/categories")
    @Operation(summary = "Get vendor categories", description = "Get list of vendor categories")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Categories retrieved")
    })
    public ResponseEntity<List<String>> getVendorCategories() {
        List<String> categories = vendorService.getVendorCategories();
        return ResponseEntity.ok(categories);
    }

    /**
     * Imports vendors from CSV.
     */
    @PostMapping("/import")
    @Operation(summary = "Import vendors", description = "Import vendors from CSV file")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Import completed"),
            @ApiResponse(responseCode = "400", description = "Invalid file format")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ImportResponse> importVendors(
            @RequestParam("file") MultipartFile file) {

        log.info("Importing vendors from file: {}", file.getOriginalFilename());

        try {
            ImportResponse response = vendorService.importVendorsFromCsv(file);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error importing vendors", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Helper method to convert Vendor to VendorDTO
    private VendorDTO convertToDTO(Vendor vendor) {
        VendorDTO dto = new VendorDTO();
        dto.setId(vendor.getId());
        dto.setName(vendor.getName());
        dto.setTaxId(vendor.getTaxId());
        dto.setAddress(vendor.getAddress());
        dto.setCity(vendor.getCity());
        dto.setState(vendor.getState());
        dto.setZipCode(vendor.getZipCode());
        dto.setCountry(vendor.getCountry());
        dto.setPhone(vendor.getPhone());
        dto.setEmail(vendor.getEmail());
        dto.setWebsite(vendor.getWebsite());
        dto.setContactName(vendor.getContactName());
        dto.setContactEmail(vendor.getContactEmail());
        dto.setContactPhone(vendor.getContactPhone());
        dto.setCategory(vendor.getCategory());
        dto.setPaymentTerms(vendor.getPaymentTerms());
        dto.setStatus(vendor.getStatus());
        dto.setNotes(vendor.getNotes());
        dto.setCreatedAt(vendor.getCreatedAt());
        dto.setUpdatedAt(vendor.getUpdatedAt());
        dto.setInvoiceCount(vendor.getInvoices() != null ? vendor.getInvoices().size() : 0);

        return dto;
    }

    // Request/Response DTOs

    public static class UpdateStatusRequest {
        private String status; // "ACTIVE" or "INACTIVE"

        // Getter and setter
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class VendorInvoicesResponse {
        private final Long vendorId;
        private final String vendorName;
        private final Page<InvoiceSummary> invoices;
        private final InvoiceStats stats;

        public VendorInvoicesResponse(Long vendorId, String vendorName,
                                      Page<InvoiceSummary> invoices, InvoiceStats stats) {
            this.vendorId = vendorId;
            this.vendorName = vendorName;
            this.invoices = invoices;
            this.stats = stats;
        }

        // Getters
        public Long getVendorId() { return vendorId; }
        public String getVendorName() { return vendorName; }
        public Page<InvoiceSummary> getInvoices() { return invoices; }
        public InvoiceStats getStats() { return stats; }

        public static class InvoiceSummary {
            private final Long id;
            private final String invoiceNumber;
            private final LocalDateTime invoiceDate;
            private final Double amount;
            private final String status;

            public InvoiceSummary(Long id, String invoiceNumber, LocalDateTime invoiceDate,
                                  Double amount, String status) {
                this.id = id;
                this.invoiceNumber = invoiceNumber;
                this.invoiceDate = invoiceDate;
                this.amount = amount;
                this.status = status;
            }

            // Getters
            public Long getId() { return id; }
            public String getInvoiceNumber() { return invoiceNumber; }
            public LocalDateTime getInvoiceDate() { return invoiceDate; }
            public Double getAmount() { return amount; }
            public String getStatus() { return status; }
        }

        public static class InvoiceStats {
            private final Long totalInvoices;
            private final Double totalAmount;
            private final Double pendingAmount;
            private final Double paidAmount;

            public InvoiceStats(Long totalInvoices, Double totalAmount,
                                Double pendingAmount, Double paidAmount) {
                this.totalInvoices = totalInvoices;
                this.totalAmount = totalAmount;
                this.pendingAmount = pendingAmount;
                this.paidAmount = paidAmount;
            }

            // Getters
            public Long getTotalInvoices() { return totalInvoices; }
            public Double getTotalAmount() { return totalAmount; }
            public Double getPendingAmount() { return pendingAmount; }
            public Double getPaidAmount() { return paidAmount; }
        }
    }

    public static class VendorStatistics {
        private final Long vendorId;
        private final String vendorName;
        private final Long totalInvoices;
        private final Double totalSpent;
        private final Double averageInvoiceAmount;
        private final Integer averagePaymentDays;
        private final LocalDateTime firstInvoiceDate;
        private final LocalDateTime lastInvoiceDate;
        private final Map<String, Long> invoicesByStatus;
        private final List<MonthlySpend> monthlySpends;

        public VendorStatistics(Long vendorId, String vendorName, Long totalInvoices,
                                Double totalSpent, Double averageInvoiceAmount,
                                Integer averagePaymentDays, LocalDateTime firstInvoiceDate,
                                LocalDateTime lastInvoiceDate, Map<String, Long> invoicesByStatus,
                                List<MonthlySpend> monthlySpends) {
            this.vendorId = vendorId;
            this.vendorName = vendorName;
            this.totalInvoices = totalInvoices;
            this.totalSpent = totalSpent;
            this.averageInvoiceAmount = averageInvoiceAmount;
            this.averagePaymentDays = averagePaymentDays;
            this.firstInvoiceDate = firstInvoiceDate;
            this.lastInvoiceDate = lastInvoiceDate;
            this.invoicesByStatus = invoicesByStatus;
            this.monthlySpends = monthlySpends;
        }

        // Getters
        public Long getVendorId() { return vendorId; }
        public String getVendorName() { return vendorName; }
        public Long getTotalInvoices() { return totalInvoices; }
        public Double getTotalSpent() { return totalSpent; }
        public Double getAverageInvoiceAmount() { return averageInvoiceAmount; }
        public Integer getAveragePaymentDays() { return averagePaymentDays; }
        public LocalDateTime getFirstInvoiceDate() { return firstInvoiceDate; }
        public LocalDateTime getLastInvoiceDate() { return lastInvoiceDate; }
        public Map<String, Long> getInvoicesByStatus() { return invoicesByStatus; }
        public List<MonthlySpend> getMonthlySpends() { return monthlySpends; }

        public static class MonthlySpend {
            private final String month;
            private final Double amount;
            private final Long invoiceCount;

            public MonthlySpend(String month, Double amount, Long invoiceCount) {
                this.month = month;
                this.amount = amount;
                this.invoiceCount = invoiceCount;
            }

            // Getters
            public String getMonth() { return month; }
            public Double getAmount() { return amount; }
            public Long getInvoiceCount() { return invoiceCount; }
        }
    }

    public static class TaxIdValidationRequest {
        @Pattern(regexp = "^[0-9-]+$", message = "Invalid tax ID format")
        private String taxId;

        // Getter and setter
        public String getTaxId() { return taxId; }
        public void setTaxId(String taxId) { this.taxId = taxId; }
    }

    public static class TaxIdValidationResponse {
        private final boolean valid;
        private final boolean unique;
        private final String message;

        public TaxIdValidationResponse(boolean valid, boolean unique, String message) {
            this.valid = valid;
            this.unique = unique;
            this.message = message;
        }

        // Getters
        public boolean isValid() { return valid; }
        public boolean isUnique() { return unique; }
        public String getMessage() { return message; }
    }

    public static class ImportResponse {
        private final int totalRecords;
        private final int successCount;
        private final int failureCount;
        private final List<ImportError> errors;

        public ImportResponse(int totalRecords, int successCount, int failureCount,
                              List<ImportError> errors) {
            this.totalRecords = totalRecords;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.errors = errors;
        }

        // Getters
        public int getTotalRecords() { return totalRecords; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public List<ImportError> getErrors() { return errors; }

        public static class ImportError {
            private final int row;
            private final String field;
            private final String error;

            public ImportError(int row, String field, String error) {
                this.row = row;
                this.field = field;
                this.error = error;
            }

            // Getters
            public int getRow() { return row; }
            public String getField() { return field; }
            public String getError() { return error; }
        }
    }
}