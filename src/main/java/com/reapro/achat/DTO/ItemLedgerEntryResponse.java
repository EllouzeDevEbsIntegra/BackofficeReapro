// src/main/java/com/reapro/achat/DTO/ItemLedgerEntryResponse.java

package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class ItemLedgerEntryResponse {

    private LocalDate postingDate;
    private String entryType;
    private String documentType;
    private String documentNo;
    private String itemNo;
    private String locationCode;
    private BigDecimal quantity;
    private String unitOfMeasureCode;
    private BigDecimal qtyPerUnitOfMeasure;
    private BigDecimal invoicedQuantity;
    private BigDecimal salesAmountExpected;
    private BigDecimal salesAmountActual;
    private BigDecimal costAmountExpected;
    private BigDecimal costAmountActual;
    private String sourceNo;
    private String sourceType;
    private String sourceName;
}