package com.apex.idp.interfaces.rest;

import com.apex.idp.application.InvoiceApplicationService;
import com.apex.idp.domain.invoice.Invoice;
import com.apex.idp.domain.invoice.InvoiceStatus;
import com.apex.idp.interfaces.dto.InvoiceDTO;
import com.apex.idp.interfaces.dto.CountResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for invoice management operations.
 * Handles invoice retrieval and count operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoice Management", description = "Invoice management APIs")
@Validated
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceApplicationService invoiceApplicationService;

    /**
     * Gets invoices for a specific batch.
     */
    @GetMapping("/batch/{batchId}")
    @Operation(summary = "Get batch invoices", description = "Get all invoices for a specific batch")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoices retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Batch not found")
    })
    public ResponseEntity<List<InvoiceDTO>> getInvoicesByBatch(
            @PathVariable @Parameter(description = "Batch ID") String batchId) {

        log.debug("Fetching invoices for batch: {}", batchId);

        try {
            List<InvoiceDTO> invoices = invoiceApplicationService.getInvoicesByBatch(batchId);
            return ResponseEntity.ok(invoices);
        } catch (IllegalArgumentException e) {
            log.error("Batch not found: {}", batchId);
            return ResponseEntity.notFound().build();
        }
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
            @PathVariable @Parameter(description = "Invoice ID") String id) {

        log.debug("Fetching invoice: {}", id);

        try {
            InvoiceDTO invoice = invoiceApplicationService.getInvoice(id);
            return ResponseEntity.ok(invoice);
        } catch (RuntimeException e) {
            log.error("Invoice not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Gets total invoice count.
     */
    @GetMapping("/count")
    @Operation(summary = "Get invoice count", description = "Get total count of all invoices")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully")
    })
    public ResponseEntity<CountResponseDTO> getInvoiceCount() {
        log.debug("Fetching total invoice count");

        long count = invoiceApplicationService.getTotalInvoiceCount();
        return ResponseEntity.ok(new CountResponseDTO(count));
    }

    /**
     * Updates invoice status.
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Update invoice status", description = "Update the status of an invoice")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found"),
            @ApiResponse(responseCode = "400", description = "Invalid status")
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<InvoiceDTO> updateInvoiceStatus(
            @PathVariable String id,
            @RequestBody UpdateInvoiceStatusRequest request) {

        log.info("Updating invoice {} status to {}", id, request.getStatus());

        try {
            InvoiceStatus status = InvoiceStatus.valueOf(request.getStatus().toUpperCase());
            InvoiceDTO updatedInvoice = invoiceApplicationService.updateInvoiceStatus(id, status);
            return ResponseEntity.ok(updatedInvoice);
        } catch (IllegalArgumentException e) {
            log.error("Invalid status: {}", request.getStatus());
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            log.error("Invoice not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Approves an invoice.
     */
    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve invoice", description = "Approve an invoice for processing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoice approved successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found"),
            @ApiResponse(responseCode = "409", description = "Invoice cannot be approved in current state")
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<InvoiceDTO> approveInvoice(@PathVariable String id) {
        log.info("Approving invoice: {}", id);

        try {
            InvoiceDTO approvedInvoice = invoiceApplicationService.approveInvoice(id);
            return ResponseEntity.ok(approvedInvoice);
        } catch (IllegalStateException e) {
            log.error("Cannot approve invoice in current state: {}", id);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (RuntimeException e) {
            log.error("Invoice not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Rejects an invoice.
     */
    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject invoice", description = "Reject an invoice")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoice rejected successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<InvoiceDTO> rejectInvoice(@PathVariable String id) {
        log.info("Rejecting invoice: {}", id);

        try {
            InvoiceDTO rejectedInvoice = invoiceApplicationService.rejectInvoice(id);
            return ResponseEntity.ok(rejectedInvoice);
        } catch (RuntimeException e) {
            log.error("Invoice not found: {}", id);
            return ResponseEntity.notFound().build();
        }
    }

    // Request DTOs

    public static class UpdateInvoiceStatusRequest {
        private String status;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}