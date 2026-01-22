package com.reapro.achat.DTO.salesorder;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSalesOrderRequest {
    @NotBlank
    private String clientId;
}
