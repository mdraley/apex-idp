package com.apex.idp.interfaces.rest;

import com.apex.idp.application.service.InvoiceService;
import com.apex.idp.domain.invoice.Invoice;
import com.apex.idp.interfaces.dto.InvoiceDTO;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for invoice management operations.
 * Handles CRUD operations and business logic for invoices.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoice Management", description = "Invoice management APIs")
@Validated
@PreAuthorize("isAuthenticated()")
public class InvoiceController {

    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * Gets all invoices with pagination and filtering.
     */
    @GetMapping
    @Operation(summary = "List invoices", description = "Get paginated list of invoices with filtering")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoices retrieved successfully")
    })
    public ResponseEntity<Page<InvoiceDTO>> getInvoices(
            @PageableDefault(size = 20, sort = "invoiceDate,desc") Pageable pageable,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount) {

        log.debug("Fetching invoices with filters - vendor: {}, status: {}, dates: {} to {}",
                vendorId, status, startDate, endDate);

        InvoiceFilterCriteria criteria = new InvoiceFilterCriteria(
                search, vendorId, status, startDate, endDate, minAmount, maxAmount
        );

        Page<Invoice> invoices = invoiceService.getInvoices(pageable, criteria);
        Page<InvoiceDTO> invoiceDTOs = invoices.map(this::convertToDTO);

        return ResponseEntity.ok(invoiceDTOs);
    }

    /**
     * Gets a specific invoice by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get invoice", description = "Get invoice details by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoice found"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    public ResponseEntity<InvoiceDTO> getInvoice(
            @PathVariable @Parameter(description = "Invoice ID") Long id) {

        log.debug("Fetching invoice with ID: {}", id);

        Optional<Invoice> invoice = invoiceService.getInvoiceById(id);
        if (invoice.isPresent()) {
            InvoiceDTO invoiceDTO = convertToDTO(invoice.get());
            return ResponseEntity.ok(invoiceDTO);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Creates a new invoice.
     */
    @PostMapping
    @Operation(summary = "Create invoice", description = "Create a new invoice")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Invoice created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<InvoiceDTO> createInvoice(@Valid @RequestBody InvoiceDTO invoiceDTO) {
        log.info("Creating new invoice: {}", invoiceDTO.getInvoiceNumber());

        try {
            Invoice invoice = invoiceService.createInvoice(invoiceDTO);
            InvoiceDTO createdDTO = convertToDTO(invoice);

            return ResponseEntity.status(HttpStatus.CREATED).body(createdDTO);

        } catch (IllegalArgumentException e) {
            log.error("Invalid invoice creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error creating invoice", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Updates an existing invoice.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update invoice", description = "Update invoice information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoice updated successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    public ResponseEntity<InvoiceDTO> updateInvoice(
            @PathVariable Long id,
            @Valid @RequestBody InvoiceDTO invoiceDTO) {

        log.info("Updating invoice ID: {}", id);

        try {
            Invoice updatedInvoice = invoiceService.updateInvoice(id, invoiceDTO);
            InvoiceDTO updatedDTO = convertToDTO(updatedInvoice);
            return ResponseEntity.ok(updatedDTO);

        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating invoice", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Deletes an invoice.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete invoice", description = "Delete an invoice")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Invoice deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteInvoice(@PathVariable Long id) {
        log.info("Deleting invoice ID: {}", id);

        try {
            invoiceService.deleteInvoice(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deleting invoice", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Updates invoice status.
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Update invoice status", description = "Update the status of an invoice")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    public ResponseEntity<InvoiceDTO> updateInvoiceStatus(
            @PathVariable Long id,
            @RequestBody @NotNull UpdateStatusRequest request) {

        log.info("Updating status for invoice ID: {} to {}", id, request.getStatus());

        try {
            Invoice updatedInvoice = invoiceService.updateInvoiceStatus(
                    id, request.getStatus(), request.getReason()
            );
            InvoiceDTO updatedDTO = convertToDTO(updatedInvoice);
            return ResponseEntity.ok(updatedDTO);

        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating invoice status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Approves an invoice for payment.
     */
    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve invoice", description = "Approve an invoice for payment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoice approved successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found"),
            @ApiResponse(responseCode = "409", description = "Invoice cannot be approved")
    })
    @PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
    public ResponseEntity<InvoiceDTO> approveInvoice(
            @PathVariable Long id,
            @RequestBody(required = false) ApprovalRequest request) {

        log.info("Approving invoice ID: {}", id);

        try {
            Invoice approvedInvoice = invoiceService.approveInvoice(
                    id,
                    request != null ? request.getApprovalNotes() : null
            );
            InvoiceDTO approvedDTO = convertToDTO(approvedInvoice);
            return ResponseEntity.ok(approvedDTO);

        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            log.error("Error approving invoice", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Gets invoice statistics.
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get invoice statistics", description = "Get invoice statistics and analytics")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved")
    })
    public ResponseEntity<InvoiceStatistics> getInvoiceStatistics(
            @RequestParam(required = false, defaultValue = "month") String period,
            @RequestParam(required = false) Long vendorId) {

        InvoiceStatistics stats = invoiceService.getInvoiceStatistics(period, vendorId);
        return ResponseEntity.ok(stats);
    }

    /**
     * Exports invoices to CSV.
     */
    @GetMapping("/export")
    @Operation(summary = "Export invoices", description = "Export invoices to CSV format")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Export successful")
    })
    public ResponseEntity<byte[]> exportInvoices(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("Exporting invoices - status: {}, dates: {} to {}", status, startDate, endDate);

        try {
            byte[] csvData = invoiceService.exportInvoicesToCsv(status, startDate, endDate);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDispositionFormData("attachment",
                    "invoices_" + LocalDate.now() + ".csv");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csvData);

        } catch (Exception e) {
            log.error("Error exporting invoices", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Gets invoice by document ID.
     */
    @GetMapping("/document/{documentId}")
    @Operation(summary = "Get invoice by document",
            description = "Get invoice associated with a document")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoice found"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    public ResponseEntity<InvoiceDTO> getInvoiceByDocument(@PathVariable Long documentId) {
        log.debug("Fetching invoice for document ID: {}", documentId);

        Optional<Invoice> invoice = invoiceService.getInvoiceByDocumentId(documentId);
        if (invoice.isPresent()) {
            InvoiceDTO invoiceDTO = convertToDTO(invoice.get());
            return ResponseEntity.ok(invoiceDTO);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Gets upcoming due invoices.
     */
    @GetMapping("/due")
    @Operation(summary = "Get due invoices", description = "Get invoices that are due soon")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Due invoices retrieved")
    })
    public ResponseEntity<List<InvoiceDTO>> getDueInvoices(
            @RequestParam(defaultValue = "7") Integer daysAhead) {

        List<Invoice> dueInvoices = invoiceService.getUpcomingDueInvoices(daysAhead);
        List<InvoiceDTO> dueDTOs = dueInvoices.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dueDTOs);
    }

    // Helper method to convert Invoice to InvoiceDTO
    private InvoiceDTO convertToDTO(Invoice invoice) {
        InvoiceDTO dto = new InvoiceDTO();
        dto.setId(invoice.getId());
        dto.setInvoiceNumber(invoice.getInvoiceNumber());
        dto.setVendorId(invoice.getVendor().getId());
        dto.setVendorName(invoice.getVendor().getName());
        dto.setInvoiceDate(invoice.getInvoiceDate());
        dto.setDueDate(invoice.getDueDate());
        dto.setTotalAmount(invoice.getTotalAmount());
        dto.setTaxAmount(invoice.getTaxAmount());
        dto.setCurrency(invoice.getCurrency());
        dto.setStatus(invoice.getStatus());
        dto.setPaymentTerms(invoice.getPaymentTerms());
        dto.setNotes(invoice.getNotes());
        dto.setDocumentId(invoice.getDocument() != null ? invoice.getDocument().getId() : null);
        dto.setBatchId(invoice.getDocument() != null && invoice.getDocument().getBatch() != null
                ? invoice.getDocument().getBatch().getId() : null);
        dto.setCreatedAt(invoice.getCreatedAt());
        dto.setUpdatedAt(invoice.getUpdatedAt());
        dto.setApprovedBy(invoice.getApprovedBy());
        dto.setApprovedAt(invoice.getApprovedAt());

        // Convert line items
        if (invoice.getLineItems() != null) {
            dto.setLineItems(invoice.getLineItems().stream()
                    .map(item -> {
                        InvoiceDTO.LineItemDTO lineItemDTO = new InvoiceDTO.LineItemDTO();
                        lineItemDTO.setDescription(item.getDescription());
                        lineItemDTO.setQuantity(item.getQuantity());
                        lineItemDTO.setUnitPrice(item.getUnitPrice());
                        lineItemDTO.setTotalPrice(item.getTotalPrice());
                        return lineItemDTO;
                    })
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    // Request/Response DTOs

    public static class InvoiceFilterCriteria {
        private final String search;
        private final Long vendorId;
        private final String status;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final BigDecimal minAmount;
        private final BigDecimal maxAmount;

        public InvoiceFilterCriteria(String search, Long vendorId, String status,
                                     LocalDate startDate, LocalDate endDate,
                                     BigDecimal minAmount, BigDecimal maxAmount) {
            this.search = search;
            this.vendorId = vendorId;
            this.status = status;
            this.startDate = startDate;
            this.endDate = endDate;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }

        // Getters
        public String getSearch() { return search; }
        public Long getVendorId() { return vendorId; }
        public String getStatus() { return status; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public BigDecimal getMinAmount() { return minAmount; }
        public BigDecimal getMaxAmount() { return maxAmount; }
    }

    public static class UpdateStatusRequest {
        @NotNull
        private String status;
        private String reason;

        // Getters and setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class ApprovalRequest {
        private String approvalNotes;

        // Getter and setter
        public String getApprovalNotes() { return approvalNotes; }
        public void setApprovalNotes(String approvalNotes) {
            this.approvalNotes = approvalNotes;
        }
    }

    public static class InvoiceStatistics {
        private final Long totalInvoices;
        private final Long pendingInvoices;
        private final Long approvedInvoices;
        private final Long paidInvoices;
        private final BigDecimal totalAmount;
        private final BigDecimal pendingAmount;
        private final BigDecimal paidAmount;
        private final Map<String, BigDecimal> amountByVendor;
        private final Map<String, Long> countByStatus;
        private final List<MonthlyTrend> monthlyTrends;

        public InvoiceStatistics(Long totalInvoices, Long pendingInvoices,
                                 Long approvedInvoices, Long paidInvoices,
                                 BigDecimal totalAmount, BigDecimal pendingAmount,
                                 BigDecimal paidAmount, Map<String, BigDecimal> amountByVendor,
                                 Map<String, Long> countByStatus, List<MonthlyTrend> monthlyTrends) {
            this.totalInvoices = totalInvoices;
            this.pendingInvoices = pendingInvoices;
            this.approvedInvoices = approvedInvoices;
            this.paidInvoices = paidInvoices;
            this.totalAmount = totalAmount;
            this.pendingAmount = pendingAmount;
            this.paidAmount = paidAmount;
            this.amountByVendor = amountByVendor;
            this.countByStatus = countByStatus;
            this.monthlyTrends = monthlyTrends;
        }

        // Getters
        public Long getTotalInvoices() { return totalInvoices; }
        public Long getPendingInvoices() { return pendingInvoices; }
        public Long getApprovedInvoices() { return approvedInvoices; }
        public Long getPaidInvoices() { return paidInvoices; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public BigDecimal getPendingAmount() { return pendingAmount; }
        public BigDecimal getPaidAmount() { return paidAmount; }
        public Map<String, BigDecimal> getAmountByVendor() { return amountByVendor; }
        public Map<String, Long> getCountByStatus() { return countByStatus; }
        public List<MonthlyTrend> getMonthlyTrends() { return monthlyTrends; }

        public static class MonthlyTrend {
            private final String month;
            private final Long count;
            private final BigDecimal amount;

            public MonthlyTrend(String month, Long count, BigDecimal amount) {
                this.month = month;
                this.count = count;
                this.amount = amount;
            }

            // Getters
            public String getMonth() { return month; }
            public Long getCount() { return count; }
            public BigDecimal getAmount() { return amount; }
        }
    }
}