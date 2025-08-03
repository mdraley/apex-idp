package com.apex.idp.interfaces.dto;
package com.apex.idp.interfaces.dto;

import com.apex.idp.domain.vendor.Vendor;
import com.apex.idp.domain.vendor.VendorStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for Vendor entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorDTO {
    private String id;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String status;
    private String taxId;
    private String paymentTerms;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Factory method to create DTO from entity.
     */
    public static VendorDTO from(Vendor vendor) {
        return VendorDTO.builder()
                .id(vendor.getId())
                .name(vendor.getName())
                .email(vendor.getEmail())
                .phone(vendor.getPhone())
                .address(vendor.getAddress())
                .status(vendor.getStatus().name())
                .taxId(vendor.getTaxId())
                .paymentTerms(vendor.getPaymentTerms())
                .notes(vendor.getNotes())
                .createdAt(vendor.getCreatedAt())
                .updatedAt(vendor.getUpdatedAt())
                .build();
    }

    /**
     * Converts DTO to entity for updates.
     * Note: This doesn't set all fields, only updatable ones.
     */
    public Vendor toEntity() {
        Vendor vendor = new Vendor();
        vendor.setId(this.id);
        vendor.setName(this.name);
        vendor.setEmail(this.email);
        vendor.setPhone(this.phone);
        vendor.setAddress(this.address);
        if (this.status != null) {
            vendor.setStatus(VendorStatus.valueOf(this.status));
        }
        vendor.setTaxId(this.taxId);
        vendor.setPaymentTerms(this.paymentTerms);
        vendor.setNotes(this.notes);
        return vendor;
    }
}
import com.apex.idp.domain.vendor.Vendor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorDTO {

    private String id;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String taxId;
    private String status;
    private int invoiceCount;
    private String createdAt;
    private String updatedAt;

    /**
     * A static factory method to create a VendorDTO from a Vendor domain entity.
     * This resolves the "cannot find symbol" compilation errors in the services.
     *
     * @param vendor The Vendor entity from the database.
     * @return A new VendorDTO object with data structured for the frontend.
     */
    public static VendorDTO from(Vendor vendor) {
        return VendorDTO.builder()
                .id(vendor.getId())
                .name(vendor.getName())
                .email(vendor.getEmail())
                .phone(vendor.getPhone())
                .address(vendor.getAddress())
                .taxId(vendor.getTaxId())
                .status(vendor.getStatus().name()) // Convert enum to a String
                .invoiceCount(vendor.getInvoiceCount())
                .createdAt(vendor.getCreatedAt().toString())
                .updatedAt(vendor.getUpdatedAt() != null ? vendor.getUpdatedAt().toString() : null)
                .build();
    }
}