package com.apex.idp.domain.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineItem {

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    public static LineItem create(String description, Integer quantity, BigDecimal unitPrice) {
        BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        return LineItem.builder()
                .description(description)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .amount(amount)
                .build();
    }
}
