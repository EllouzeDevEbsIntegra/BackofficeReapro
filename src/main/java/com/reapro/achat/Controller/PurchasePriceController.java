package com.reapro.achat.Controller;

import com.reapro.achat.entities.sqlserver.PurchasePrice;
import com.reapro.achat.services.PurchasePriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// RBAC Lot 4bis-B : historique prix achat (Info Article) → lecture si Info Article OU module consommateur.
@RestController
@RequestMapping("/api/purchase-prices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ARTICLE_INFO_READ','COMPARATOR_ACCESS','PURCHASE_CONFIRMATION_ACCESS','B2B_ACCESS','ARTICLE_MANAGEMENT_ACCESS','TECDOC_CATALOG_ACCESS')")
public class PurchasePriceController {

    private final PurchasePriceService service;

    @GetMapping
    public List<PurchasePrice> getPurchasePrices(
            @RequestParam String itemNo,
            @RequestParam(required = false) String vendorNo  // ✅ plus obligatoire
    ) {
        return service.getPrices(vendorNo, itemNo);
    }
}