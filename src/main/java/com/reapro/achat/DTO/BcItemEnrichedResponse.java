package com.reapro.achat.DTO;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.reapro.achat.DTO.bc.BcItemBC;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class BcItemEnrichedResponse {

    @JsonUnwrapped
    private BcItemBC item;  // <-- tous les champs bcItems restent au même niveau JSON

    private BigDecimal lastInvoicedDirectCost;
    private BigDecimal quantity;
    private LocalDate lastInvoicedCostDate;
}