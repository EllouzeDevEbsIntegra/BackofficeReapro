package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LastInvoicedItemCostDTO {
    private String frs;
    private String refFrs;
    private String no;
    private BigDecimal lastInvoicedDirectCost;
    private BigDecimal quantity;
    private LocalDate lastInvoicedCostDate;
    private String systemId;
}
