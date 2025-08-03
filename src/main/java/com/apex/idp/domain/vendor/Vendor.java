package com.apex.idp.domain.vendor;

import com.apex.idp.domain.invoice.Invoice;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity representing a vendor in the system.
 * Vendors are organizations that supply goods or services.
 */
@Entity
@Table(name = "vendors")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Vendor {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column
    private String email;

    @Column
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "tax_id")
    private String taxId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VendorStatus status;

    @Column(name = "payment_terms")
    private String paymentTerms;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "invoice_count")
    private int invoiceCount;

    @OneToMany(mappedBy = "vendor", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Invoice> invoices = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Factory method to create a new vendor.
     */
    public static Vendor create(String name) {
        return Vendor.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .status(VendorStatus.ACTIVE)
                .invoiceCount(0)
                .build();
    }

    /**
     * Updates vendor contact information.
     */
    public void updateContactInfo(String email, String phone, String address) {
        if (email != null) this.email = email;
        if (phone != null) this.phone = phone;
        if (address != null) this.address = address;
    }

    /**
     * Updates vendor payment terms.
     */
    public void updatePaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    /**
     * Updates vendor tax ID.
     */
    public void updateTaxId(String taxId) {
        this.taxId = taxId;
    }

    /**
     * Activates the vendor.
     */
    public void activate() {
        this.status = VendorStatus.ACTIVE;
    }

    /**
     * Deactivates the vendor.
     */
    public void deactivate() {
        this.status = VendorStatus.INACTIVE;
    }

    /**
     * Increments the invoice count.
     */
    public void incrementInvoiceCount() {
        this.invoiceCount++;
    }

    /**
     * Adds notes to the vendor.
     */
    public void addNotes(String additionalNotes) {
        if (additionalNotes != null && !additionalNotes.trim().isEmpty()) {
            this.notes = this.notes == null ? additionalNotes : this.notes + "\n" + additionalNotes;
        }
    }
}