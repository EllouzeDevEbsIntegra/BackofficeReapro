package com.reapro.achat.Controller;

import com.reapro.achat.DTO.OemCountDetailsResponseDTO;
import com.reapro.achat.DTO.PurchaseLineSqlServerDTO;
import com.reapro.achat.services.OemEquivalenceService;
import com.reapro.achat.services.PurchaseLineSqlServerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// RBAC Lot 4bis-B : lignes commandes + comptage OEM (Info Article) → lecture si Info Article OU module consommateur.
@RestController
@RequestMapping("/api/sqlserver")
@PreAuthorize("hasAnyAuthority('ARTICLE_INFO_READ','COMPARATOR_ACCESS','PURCHASE_CONFIRMATION_ACCESS','B2B_ACCESS','ARTICLE_MANAGEMENT_ACCESS','TECDOC_CATALOG_ACCESS')")
public class PurchaseLineSqlServerController {

    private final PurchaseLineSqlServerService purchaseLineSqlServerService;
    private final OemEquivalenceService oemEquivalenceService;

    public PurchaseLineSqlServerController(
            PurchaseLineSqlServerService purchaseLineSqlServerService,
            OemEquivalenceService oemEquivalenceService) {
        this.purchaseLineSqlServerService = purchaseLineSqlServerService;
        this.oemEquivalenceService = oemEquivalenceService;
    }

    @GetMapping("/purchase-lines/{no}")
    public ResponseEntity<Page<PurchaseLineSqlServerDTO>> getPurchaseLinesByNo(
            @PathVariable String no,
            Pageable pageable) {
        Page<PurchaseLineSqlServerDTO> purchaseLines = purchaseLineSqlServerService.getPurchaseLinesByNo(no, pageable);
        if (purchaseLines.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(purchaseLines);
    }

    @GetMapping("/oem-equivalence-count/{masterItemNo}")
    public ResponseEntity<OemCountDetailsResponseDTO> getOemEquivalenceCountWithDetails(@PathVariable String masterItemNo) {
        OemCountDetailsResponseDTO response = oemEquivalenceService.calculateOemCountWithDetails(masterItemNo);
        return ResponseEntity.ok(response);
    }
}
