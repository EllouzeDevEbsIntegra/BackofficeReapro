package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class ItemLedgerEntryPageResponse {

    private List<ItemLedgerEntryResponse> content; // lignes
    private int page;                              // page courante (0-based)
    private int size;                              // taille de page
    private long totalElements;                    // total de lignes
    private int totalPages;                        // total de pages
    private Map<String, BigDecimal> quantityByEntryType;
}