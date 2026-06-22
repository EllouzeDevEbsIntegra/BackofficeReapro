package com.reapro.achat.Controller;

import com.reapro.achat.entities.sqlserver.LastInvoicedItemCost;
import com.reapro.achat.services.LastInvoicedItemCostService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// RBAC Lot 4bis-B : coûts facturés (Info Article) → lecture si Info Article OU module consommateur.
@RestController
@RequestMapping("/api/last-invoiced-cost")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ARTICLE_INFO_READ','COMPARATOR_ACCESS','PURCHASE_CONFIRMATION_ACCESS','B2B_ACCESS','ARTICLE_MANAGEMENT_ACCESS','TECDOC_CATALOG_ACCESS')")
public class LastInvoicedItemCostController {

    private final LastInvoicedItemCostService service;


    @GetMapping
    public List<LastInvoicedItemCost> getLastInvoicedCost(@RequestParam String itemNo) {
        return service.getByItemNo(itemNo);
    }
}