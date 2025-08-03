package com.apex.idp.domain.invoice;

import com.apex.idp.domain.document.Document;
import com.apex.idp.domain.vendor.Vendor;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing an invoice in the system.
 */
@Entity
@Table(name = "invoices")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @Column(length = 36)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false, unique = true)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @Column(name = "invoice_number", unique = true)
    private String invoiceNumber;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "payment_terms")
    private String paymentTerms;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "invoice_line_items", joinColumns = @JoinColumn(name = "invoice_id"))
    @Builder.Default
    private List<LineItem> lineItems = new ArrayList<>();

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Factory method to create an invoice.
     */
    public static Invoice create(Document document) {
        return Invoice.builder()
                .id(UUID.randomUUID().toString())
                .document(document)
                .status(InvoiceStatus.DRAFT)
                .currency("USD")
                .lineItems(new ArrayList<>())
                .build();
    }

    /**
     * Approves the invoice.
     */
    public void approve(String approvedBy) {
        validateStatusTransition(InvoiceStatus.DRAFT, "approve");
        validateNotEmpty(approvedBy, "Approved by is required");

        this.status = InvoiceStatus.APPROVED;
        this.approvedBy = approvedBy;
        this.approvedAt = LocalDateTime.now();
    }

    /**
     * Rejects the invoice.
     */
    public void reject(String reason) {
        validateStatusTransition(InvoiceStatus.DRAFT, "reject");
        validateNotEmpty(reason, "Rejection reason is required");

        this.status = InvoiceStatus.REJECTED;
        this.rejectionReason = reason;
        this.rejectedAt = LocalDateTime.now();
        addNote("Rejected: " + reason);
    }

    /**
     * Marks the invoice as paid.
     */
    public void markAsPaid(LocalDate paymentDate, String reference) {
        validateStatusTransition(InvoiceStatus.APPROVED, "mark as paid");

        this.status = InvoiceStatus.PAID;
        this.paymentDate = paymentDate != null ? paymentDate : LocalDate.now();
        this.paymentReference = reference;
    }

    /**
     * Marks the invoice as in review.
     */
    public void markAsInReview() {
        this.status = InvoiceStatus.IN_REVIEW;
    }

    /**
     * Adds a note to the invoice.
     */
    public void addNote(String note) {
        validateNotEmpty(note, "Note cannot be empty");

        this.notes = this.notes == null ? note : this.notes + "\n" + note;
    }

    /**
     * Calculates days until due.
     */
    public Integer getDaysUntilDue() {
        if (this.dueDate == null) {
            return null;
        }

        LocalDate today = LocalDate.now();
        return (int) ChronoUnit.DAYS.between(today, this.dueDate);
    }

    /**
     * Checks if invoice is overdue.
     */
    public boolean isOverdue() {
        return this.dueDate != null
                && this.status != InvoiceStatus.PAID
                && LocalDate.now().isAfter(this.dueDate);
    }

    /**
     * Adds a line item to the invoice.
     */
    public void addLineItem(LineItem lineItem) {
        if (lineItem != null) {
            this.lineItems.add(lineItem);
            recalculateTotal();
        }
    }

    /**
     * Removes a line item from the invoice.
     */
    public void removeLineItem(LineItem lineItem) {
        if (this.lineItems.remove(lineItem)) {
            recalculateTotal();
        }
    }

    private void recalculateTotal() {
        BigDecimal lineItemsTotal = lineItems.stream()
                .map(LineItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tax = taxAmount != null ? taxAmount : BigDecimal.ZERO;
        this.amount = lineItemsTotal.add(tax);
    }

    private void validateStatusTransition(InvoiceStatus expectedStatus, String operation) {
        if (this.status == null) {
            throw new IllegalStateException("Invoice status cannot be null");
        }
        if (this.status != expectedStatus) {
            throw new IllegalStateException(
                    String.format("Can only %s invoices with status %s, current status: %s",
                            operation, expectedStatus, this.status));
        }
    }

    private void validateNotEmpty(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }
}