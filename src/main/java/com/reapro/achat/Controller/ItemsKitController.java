package com.reapro.achat.Controller;

import com.reapro.achat.DTO.BcItemEnrichedResponse;
import com.reapro.achat.DTO.PagedResponse;
import com.reapro.achat.services.ItemsKitService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

// RBAC Lot 4bis-B : kits article (Info Article / Comparateur) → lecture si Info Article OU module consommateur.
@RestController
@RequestMapping("/api/itemsKit")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ARTICLE_INFO_READ','COMPARATOR_ACCESS','PURCHASE_CONFIRMATION_ACCESS','B2B_ACCESS','ARTICLE_MANAGEMENT_ACCESS','TECDOC_CATALOG_ACCESS')")
public class ItemsKitController {

    private final ItemsKitService service;

    /**
     * Exemple:
     * GET /api/itemsKit?no=39613&page=0&size=20
     */
    @GetMapping
    public PagedResponse<BcItemEnrichedResponse> getItemsKit(
            @AuthenticationPrincipal String email,
            @RequestParam String no,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String compareQuoteNo,
            @RequestParam(required = false) String stockOperator,
            @RequestParam(required = false) BigDecimal stockValue,
            @RequestParam(required = false) String dateDernierAchatOperator,
            @RequestParam(required = false) String dateDernierAchatValue,
            @RequestParam(required = false) String referenceOperator,
            @RequestParam(required = false) String referenceValue
    ) {
        return service.getItemsKit(email, no, page, size, compareQuoteNo,
                stockOperator, stockValue, dateDernierAchatOperator, dateDernierAchatValue,
                referenceOperator, referenceValue);
    }
}
