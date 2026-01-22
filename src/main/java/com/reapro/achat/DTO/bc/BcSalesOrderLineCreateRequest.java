package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BcSalesOrderLineCreateRequest {

    @JsonProperty("lineType")
    private String lineType;

    @JsonProperty("itemId")
    private String itemId;

    @JsonProperty("quantity")
    private BigDecimal quantity;
}
