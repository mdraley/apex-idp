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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
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

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @ElementCollection
    @CollectionTable(name = "invoice_line_items", joinColumns = @JoinColumn(name = "invoice_id"))
    @Builder.Default
    private List<LineItem> lineItems = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Additional fields
    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "payment_terms")
    private String paymentTerms;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    /**
     * Static factory method to create a new Invoice
     */
    public static Invoice create(Document document) {
        return Invoice.builder()
                .id(UUID.randomUUID().toString())
                .document(document)
                .status(InvoiceStatus.PENDING)
                .currency("USD")
                .lineItems(new ArrayList<>())
                .build();
    }

    /**
     * Business method to approve the invoice
     */
    public void approve() {
        if (this.status != InvoiceStatus.PENDING) {
            throw new IllegalStateException("Can only approve pending invoices");
        }
        this.status = InvoiceStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
    }

    /**
     * Business method to reject the invoice
     */
    public void reject(String reason) {
        if (this.status != InvoiceStatus.PENDING) {
            throw new IllegalStateException("Can only reject pending invoices");
        }
        this.status = InvoiceStatus.REJECTED;
        this.rejectionReason = reason;
        this.rejectedAt = LocalDateTime.now();
    }

    /**
     * Adds a line item to the invoice
     */
    public void addLineItem(LineItem lineItem) {
        this.lineItems.add(lineItem);
        recalculateTotal();
    }

    /**
     * Removes a line item from the invoice
     */
    public void removeLineItem(LineItem lineItem) {
        this.lineItems.remove(lineItem);
        recalculateTotal();
    }

    /**
     * Recalculates the total amount based on line items
     */
    private void recalculateTotal() {
        this.amount = lineItems.stream()
                .map(LineItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Checks if the invoice is overdue
     */
    public boolean isOverdue() {
        return dueDate != null && LocalDate.now().isAfter(dueDate)
                && status != InvoiceStatus.PAID;
    }

    /**
     * Calculates days until due date
     */
    public long getDaysUntilDue() {
        if (dueDate == null) {
            return 0;
        }
        return LocalDate.now().until(dueDate).getDays();
    }

    /**
     * Marks the invoice as paid
     */
    public void markAsPaid() {
        if (this.status != InvoiceStatus.APPROVED) {
            throw new IllegalStateException("Can only mark approved invoices as paid");
        }
        this.status = InvoiceStatus.PAID;
    }

    /**
     * Sets vendor and validates
     */
    public void assignVendor(Vendor vendor) {
        if (vendor == null) {
            throw new IllegalArgumentException("Vendor cannot be null");
        }
        this.vendor = vendor;
    }

    /**
     * Validates the invoice data
     */
    public void validate() {
        if (invoiceNumber == null || invoiceNumber.trim().isEmpty()) {
            throw new IllegalStateException("Invoice number is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Invoice amount must be positive");
        }
        if (document == null) {
            throw new IllegalStateException("Invoice must be associated with a document");
        }
    }
}