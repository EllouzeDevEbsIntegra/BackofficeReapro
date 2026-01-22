package com.reapro.achat.DTO.order;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateOrderLineQuantityRequest {
    @Min(1)
    private int quantity;
}
