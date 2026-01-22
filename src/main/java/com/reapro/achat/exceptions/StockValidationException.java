package com.reapro.achat.exceptions;

import com.reapro.achat.DTO.salesorder.InvalidStockLineDto;
import lombok.Getter;

import java.util.List;

@Getter
public class StockValidationException extends RuntimeException {
    private final List<InvalidStockLineDto> invalidLines;

    public StockValidationException(String message, List<InvalidStockLineDto> invalidLines) {
        super(message);
        this.invalidLines = invalidLines;
    }
}
