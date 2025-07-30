package com.apex.idp.domain.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.math.BigDecimal;

/**
 * Value object representing a line item in an invoice.
 * This is an embeddable type that will be stored as part of the Invoice entity.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class LineItem {

    @Column(name = "line_description")
    private String description;

    @Column(name = "line_quantity")
    private Integer quantity;

    @Column(name = "line_unit_price", precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total_price", precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "line_item_code")
    private String itemCode;

    @Column(name = "line_tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;

    /**
     * Creates a line item with automatic total calculation
     */
    public static LineItem create(String description, Integer quantity, BigDecimal unitPrice) {
        LineItem item = new LineItem();
        item.description = description;
        item.quantity = quantity;
        item.unitPrice = unitPrice;
        item.calculateTotal();
        return item;
    }

    /**
     * Calculates the total price based on quantity and unit price
     */
    public void calculateTotal() {
        if (quantity != null && unitPrice != null) {
            this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
        } else {
            this.totalPrice = BigDecimal.ZERO;
        }
    }

    /**
     * Calculates total with tax
     */
    public BigDecimal getTotalWithTax() {
        if (totalPrice == null) {
            return BigDecimal.ZERO;
        }

        if (taxRate == null) {
            return totalPrice;
        }

        BigDecimal taxAmount = totalPrice.multiply(taxRate);
        return totalPrice.add(taxAmount);
    }

    /**
     * Updates quantity and recalculates total
     */
    public void updateQuantity(Integer newQuantity) {
        this.quantity = newQuantity;
        calculateTotal();
    }

    /**
     * Updates unit price and recalculates total
     */
    public void updateUnitPrice(BigDecimal newUnitPrice) {
        this.unitPrice = newUnitPrice;
        calculateTotal();
    }
}