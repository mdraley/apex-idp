package com.apex.idp.interfaces.dto;

import com.apex.idp.domain.invoice.Invoice;
import com.apex.idp.domain.invoice.LineItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDTO {

    private String id;
    private String documentId;
    private String vendorId;
    private String vendorName;
    private String invoiceNumber;
    private String invoiceDate;
    private String dueDate;
    private BigDecimal amount;
    private String status;
    private String imageUrl; // This would be populated from a different source, like MinIO presigned URL
    private List<LineItemDTO> lineItems;
    private String createdAt;
    private String updatedAt;

    /**
     * A static factory method to create an InvoiceDTO from an Invoice domain entity.
     * This resolves the "cannot find symbol" compilation errors in the services.
     *
     * @param invoice The Invoice entity from the database.
     * @return A new InvoiceDTO object with data structured for the frontend.
     */
    public static InvoiceDTO from(Invoice invoice) {
        // Handle potential null vendor
        String vendorId = invoice.getVendor() != null ? invoice.getVendor().getId() : null;
        String vendorName = invoice.getVendor() != null ? invoice.getVendor().getName() : null;

        // Safely map line items
        List<LineItemDTO> lineItemDTOs = (invoice.getLineItems() != null)
                ? invoice.getLineItems().stream().map(LineItemDTO::from).collect(Collectors.toList())
                : Collections.emptyList();

        return InvoiceDTO.builder()
                .id(invoice.getId())
                .documentId(invoice.getDocument().getId())
                .vendorId(vendorId)
                .vendorName(vendorName)
                .invoiceNumber(invoice.getInvoiceNumber())
                .invoiceDate(invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toString() : null)
                .dueDate(invoice.getDueDate() != null ? invoice.getDueDate().toString() : null)
                .amount(invoice.getAmount())
                .status(invoice.getStatus().name()) // Convert enum to String
                .lineItems(lineItemDTOs)
                .createdAt(invoice.getCreatedAt().toString())
                .updatedAt(invoice.getUpdatedAt() != null ? invoice.getUpdatedAt().toString() : null)
                // Note: imageUrl would typically be set separately after generating a presigned URL
                .build();
    }

    /**
     * A nested DTO to represent individual line items within an invoice.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItemDTO {
        private String description;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;

        public static LineItemDTO from(LineItem lineItem) {
            return LineItemDTO.builder()
                    .description(lineItem.getDescription())
                    .quantity(lineItem.getQuantity())
                    .unitPrice(lineItem.getUnitPrice())
                    .amount(lineItem.getAmount())
                    .build();
        }
    }
}