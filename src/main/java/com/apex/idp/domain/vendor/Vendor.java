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

@Entity
@Table(name = "vendors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Vendor {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    private String email;

    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "tax_id")
    private String taxId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VendorStatus status;

    @Column(name = "invoice_count")
    private int invoiceCount;

    @OneToMany(mappedBy = "vendor")
    @Builder.Default
    private List<Invoice> invoices = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static Vendor create(String name) {
        return Vendor.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .status(VendorStatus.ACTIVE)
                .invoiceCount(0)
                .build();
    }

    public void updateContactInfo(String email, String phone, String address) {
        this.email = email;
        this.phone = phone;
        this.address = address;
    }

    public void activate() {
        this.status = VendorStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = VendorStatus.INACTIVE;
    }

    public void incrementInvoiceCount() {
        this.invoiceCount++;
    }
}
