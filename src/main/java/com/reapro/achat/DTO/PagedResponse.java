package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int size;

    // si BC renvoie @odata.count on le met, sinon -1
    private long totalElements;
    private int totalPages;

    // Totaux de synthèse (calculés sur la liste filtrée COMPLÈTE, avant pagination).
    // Utilisés par les headers EQV/KIT (badges CMD / IMP). 0 par défaut (compat ascendante).
    private BigDecimal totalCmdQuantity = BigDecimal.ZERO;
    private BigDecimal totalImportQuantity = BigDecimal.ZERO;

    /** Constructeur historique (5 args) — conservé pour ne pas casser les consommateurs existants. */
    public PagedResponse(List<T> content, int page, int size, long totalElements, int totalPages) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }
}