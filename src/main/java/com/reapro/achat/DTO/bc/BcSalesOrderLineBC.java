package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BcSalesOrderLineBC {

    @JsonProperty("id")
    private String id;
    
    @JsonProperty("documentId")
    private String documentId;
    
    @JsonProperty("sequence")
    private Integer sequence;
    
    @JsonProperty("itemId")
    private String itemId;
    
    @JsonProperty("lineType")
    private String lineType;

    // Modifié : la référence de l'article est dans lineDetails
    @JsonProperty("lineDetails")
    private LineDetailsBC lineDetails;
    
    @JsonProperty("quantity")
    private BigDecimal quantity;
    
    @JsonProperty("unitPrice")
    private BigDecimal unitPrice;
    
    @JsonProperty("discountAmount")
    private BigDecimal discountAmount;
    
    @JsonProperty("discountPercent")
    private BigDecimal discountPercent;
    
    @JsonProperty("amountExcludingTax")
    private BigDecimal amountExcludingTax;
    
    @JsonProperty("taxPercent")
    private BigDecimal taxPercent;
    
    @JsonProperty("totalTaxAmount")
    private BigDecimal totalTaxAmount;
    
    @JsonProperty("amountIncludingTax")
    private BigDecimal amountIncludingTax;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LineDetailsBC {
        private String number;
    }
}