package com.apex.idp.interfaces.dto;

import com.apex.idp.domain.vendor.Vendor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
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