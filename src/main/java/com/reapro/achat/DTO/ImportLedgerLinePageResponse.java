package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Page de lignes Import (item-ledger magasin import, RemainingQuantity > 0).
 * Même forme de pagination que ItemLedgerEntryPageResponse, sans le recap par type.
 */
@Data
@AllArgsConstructor
public class ImportLedgerLinePageResponse {

    private List<ImportLedgerLineResponse> content; // lignes
    private int page;                               // page courante (0-based)
    private int size;                               // taille de page
    private long totalElements;                     // total de lignes
    private int totalPages;                         // total de pages
}
