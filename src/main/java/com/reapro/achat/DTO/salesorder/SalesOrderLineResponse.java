package com.reapro.achat.DTO.salesorder;

import com.reapro.achat.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SalesOrderLineResponse {
    private Long id;
    private String reference;
    private String designation;
    private String brand;
    private String unit;
    private int quantity;
    private BigDecimal unitPrice;
    private int availableStock;
    private OrderStatus status;
    private String businessCentralLineNumber;
    private String businessCentralSystemId;
    private BigDecimal discountPercent;
    private BigDecimal discountAmount;
    private BigDecimal vatPercent;
    private BigDecimal vatAmount;
    private BigDecimal amountExcludingTax;
    private BigDecimal amountIncludingTax;
}
