package com.reapro.achat.DTO.order;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvalidStockLineDto {
    private String reference;
    private int orderedQuantity;
    private int availableQuantity;
}
