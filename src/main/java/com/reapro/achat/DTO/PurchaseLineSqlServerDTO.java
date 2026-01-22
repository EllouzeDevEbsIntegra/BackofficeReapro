package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseLineSqlServerDTO {
    private String documentNo;
    private String buyFromVendorNo;
    private String no;
    private String locationCode;
    private LocalDateTime orderDate;
    private String description;
    private BigDecimal quantity;
    private BigDecimal outstandingQuantity;
}
