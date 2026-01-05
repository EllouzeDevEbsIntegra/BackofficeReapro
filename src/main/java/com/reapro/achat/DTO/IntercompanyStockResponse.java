package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class IntercompanyStockResponse {
    private String companyId;   // id BC
    private String company;     // pour l'instant = companyId (étape 2: COPIM/MPAA/...)
    private String no;          // item no
    private BigDecimal stock;   // qtyStock
    private LocalDate lastPurchaseDate;
}