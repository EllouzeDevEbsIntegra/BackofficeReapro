package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BcSalesOrderCreateRequest {
    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("customerNumber")
    private String customerNumber;
}