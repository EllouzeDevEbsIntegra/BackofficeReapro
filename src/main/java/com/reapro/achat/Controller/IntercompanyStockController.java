package com.reapro.achat.Controller;

import com.reapro.achat.DTO.IntercompanyStockResponse;
import com.reapro.achat.services.IntercompanyStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// RBAC Lot 4bis-B : stock intercompany (Info Article) ; société dérivée du profil (déjà via email).
// Lecture si Info Article OU module consommateur.
@RestController
@RequestMapping("/api/bc/items")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ARTICLE_INFO_READ','COMPARATOR_ACCESS','PURCHASE_CONFIRMATION_ACCESS','B2B_ACCESS','ARTICLE_MANAGEMENT_ACCESS','TECDOC_CATALOG_ACCESS')")
public class IntercompanyStockController {

    private final IntercompanyStockService service;

    /**
     * Retourne le stock des autres sociétés (exclut la société du profil)
     * GET /api/bc/items/intercompany-stock?no=LX804/S
     */
    @GetMapping("/intercompany-stock")
    public List<IntercompanyStockResponse> getIntercompanyStock(
            @AuthenticationPrincipal String email,
            @RequestParam String no
    ) {
        return service.getIntercompanyStockExcludingMyCompany(email, no);
    }
}