package com.apex.idp.interfaces.dto;

import com.apex.idp.domain.invoice.Invoice;
import com.apex.idp.domain.invoice.LineItem;
import lombok.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
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
    private String imageUrl;
    private List<LineItemDTO> lineItems;
    private String createdAt;
    private String updatedAt;

    // Additional calculated fields
    private boolean overdue;
    private BigDecimal totalWithTax;
    private String currency;
    private String paymentTerms;
    private String description;

    /**
     * Static factory method to create an InvoiceDTO from an Invoice domain entity.
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
                .status(invoice.getStatus().name())
                .lineItems(lineItemDTOs)
                .createdAt(invoice.getCreatedAt().toString())
                .updatedAt(invoice.getUpdatedAt() != null ? invoice.getUpdatedAt().toString() : null)
                .currency(invoice.getCurrency() != null ? invoice.getCurrency() : "USD")
                .paymentTerms(invoice.getPaymentTerms())
                .description(invoice.getDescription())
                .build();
    }

    /**
     * Nested DTO for line items
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineItemDTO {
        private String description;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;

        public static LineItemDTO from(LineItem lineItem) {
            return LineItemDTO.builder()
                    .description(lineItem.getDescription())
                    .quantity(lineItem.getQuantity())
                    .unitPrice(lineItem.getUnitPrice())
                    .totalPrice(lineItem.getTotalPrice())
                    .build();
        }
    }
}