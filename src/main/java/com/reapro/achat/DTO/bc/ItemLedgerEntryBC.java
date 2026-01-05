// src/main/java/com/reapro/achat/DTO/bc/ItemLedgerEntryBC.java

package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemLedgerEntryBC {

    @JsonProperty("id")
    private String id;

    @JsonProperty("PostingDate")
    private LocalDate postingDate;

    @JsonProperty("EntryType")
    private String entryType;

    @JsonProperty("DocumentType")
    private String documentType;

    @JsonProperty("DocumentNo")
    private String documentNo;

    @JsonProperty("ItemNo")
    private String itemNo;

    @JsonProperty("LocationCode")
    private String locationCode;

    @JsonProperty("Quantity")
    private BigDecimal quantity;

    @JsonProperty("UnitofMeasureCode")
    private String unitOfMeasureCode;

    @JsonProperty("QtyperUnitofMeasure")
    private BigDecimal qtyPerUnitOfMeasure;

    @JsonProperty("InvoicedQuantity")
    private BigDecimal invoicedQuantity;

    @JsonProperty("SalesAmountExpected")
    private BigDecimal salesAmountExpected;

    @JsonProperty("SalesAmountActual")
    private BigDecimal salesAmountActual;

    @JsonProperty("CostAmountExpected")
    private BigDecimal costAmountExpected;

    @JsonProperty("CostAmountActual")
    private BigDecimal costAmountActual;

    @JsonProperty("SourceNo")
    private String sourceNo;

    @JsonProperty("SourceType")
    private String sourceType;

    @JsonProperty("SourceName")
    private String sourceName;
}