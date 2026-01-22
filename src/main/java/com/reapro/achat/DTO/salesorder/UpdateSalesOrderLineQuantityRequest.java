package com.reapro.achat.DTO.salesorder;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateSalesOrderLineQuantityRequest {
    @Min(1)
    private int quantity;
}
