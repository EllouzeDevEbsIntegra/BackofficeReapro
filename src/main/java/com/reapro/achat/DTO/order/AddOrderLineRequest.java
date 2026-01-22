package com.reapro.achat.DTO.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddOrderLineRequest {
    @NotBlank
    private String reference;

    @Min(1)
    private int quantity;
}
