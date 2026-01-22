package com.reapro.achat.DTO.salesorder;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddSalesOrderLineRequest {
    @NotBlank
    private String reference;

    @Min(1)
    private int quantity;
}
