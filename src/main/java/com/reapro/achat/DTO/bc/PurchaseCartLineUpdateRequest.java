package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PurchaseCartLineUpdateRequest {
    
    @JsonProperty("quantity")
    private BigDecimal quantity;
    
    @JsonProperty("directUnitCost")
    private BigDecimal directUnitCost;
    
    @JsonProperty("status")
    private String status;

    @JsonProperty("comment")
    private String comment;
}
