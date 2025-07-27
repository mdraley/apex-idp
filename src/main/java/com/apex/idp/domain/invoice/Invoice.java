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
@Setter // FIX: Add @Setter
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
    @Setter
    private Vendor vendor;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(precision = 10, scale = 2)
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

    // NEW FIELDS
    private BigDecimal taxAmount;
    private String currency;
    private String paymentTerms;
    private String notes;
    private String approvedBy;
    private LocalDateTime approvedAt;

    public static Invoice create(Document document) {
        return Invoice.builder()
                .id(UUID.randomUUID().toString())
                .document(document)
                .status(InvoiceStatus.PENDING)
                .build();
    }

    public void updateFromExtractedData(String invoiceNumber, LocalDate invoiceDate,
                                        LocalDate dueDate, BigDecimal amount) {
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.amount = amount;
        this.status = InvoiceStatus.PROCESSED;
    }

    public void approve() {
        if (status != InvoiceStatus.PROCESSED) {
            throw new IllegalStateException("Only processed invoices can be approved");
        }
        this.status = InvoiceStatus.APPROVED;
    }

    public void reject() {
        this.status = InvoiceStatus.REJECTED;
    }

    public void addLineItem(LineItem lineItem) {
        lineItems.add(lineItem);
    }

    public BigDecimal calculateTotal() {
        return lineItems.stream()
                .map(LineItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Helper method to get total amount
    public BigDecimal getTotalAmount() {
        return this.amount; // Assuming 'amount' is the total. Adjust if necessary.
    }
}
