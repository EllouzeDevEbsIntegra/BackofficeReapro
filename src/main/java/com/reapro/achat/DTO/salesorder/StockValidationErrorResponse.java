package com.reapro.achat.DTO.salesorder;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockValidationErrorResponse {
    private String message;
    private List<InvalidStockLineDto> invalidLines;
}
