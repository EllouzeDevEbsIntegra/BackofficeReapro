package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BcSalesOrderBC {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("number") // Modifié : c'est 'number' et non 'no'
    private String number;
    
    @JsonProperty("customerNumber") // Modifié
    private String customerNumber;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("totalAmountExcludingTax") // Modifié
    private BigDecimal totalAmountExcludingTax;
    
    @JsonProperty("totalAmountIncludingTax") // Modifié
    private BigDecimal totalAmountIncludingTax;
    
    @JsonProperty("salesOrderLines")
    private List<BcSalesOrderLineBC> salesOrderLines;
}