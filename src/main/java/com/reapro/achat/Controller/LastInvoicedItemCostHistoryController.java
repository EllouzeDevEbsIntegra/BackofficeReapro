package com.reapro.achat.Controller;

import com.reapro.achat.DTO.LastInvoicedItemCostDTO;
import com.reapro.achat.services.LastInvoicedItemCostHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historique paginé des derniers coûts facturés d'un article
 * (vue SQL Server View_ProjectReapro_LastInvoicedItemCost), trié date décroissante.
 * Alimente le dialog "Historique Der P" de Confirmation Achat C2.
 *
 * NB : distinct de /api/last-invoiced-cost (LastInvoicedItemCostController, liste non paginée
 * utilisée par le chargement secondaire FRS) et de /api/purchase-prices (historique prix achat).
 * Les trois coexistent.
 */
@RestController
@RequestMapping("/api/sqlserver")
public class LastInvoicedItemCostHistoryController {

    private final LastInvoicedItemCostHistoryService service;

    public LastInvoicedItemCostHistoryController(LastInvoicedItemCostHistoryService service) {
        this.service = service;
    }

    @GetMapping("/last-invoiced-item-costs/{itemNo}")
    public ResponseEntity<Page<LastInvoicedItemCostDTO>> getByItemNo(
            @PathVariable String itemNo,
            Pageable pageable) {
        Page<LastInvoicedItemCostDTO> page = service.getByItemNo(itemNo, pageable);
        if (page.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(page);
    }
}
