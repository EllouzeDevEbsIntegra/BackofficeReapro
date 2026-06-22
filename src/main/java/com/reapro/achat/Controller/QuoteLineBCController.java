package com.reapro.achat.Controller;

import com.reapro.achat.DTO.bc.QuoteLineBC;
import com.reapro.achat.DTO.bc.QuoteLineUpdateRequest;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.repositories.primary.AdminRepository;
import com.reapro.achat.services.CompanyScopeService;
import com.reapro.achat.services.QuoteLineBCService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

// RBAC Lot 4 : lignes de devis BC, partagées Comparateur / Confirmation Achat.
//  - lecture (GET) : accès Comparateur OU Confirmation Achat ;
//  - mise à jour (PATCH = décision/action) : actions Confirmation Achat OU actions panier Comparateur.
// ⚠️ companyId en @RequestParam (défaut codé) → à valider contre la société utilisateur (lot ultérieur).
@RestController
@RequestMapping("/api/bc/quote-lines")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('COMPARATOR_ACCESS','PURCHASE_CONFIRMATION_ACCESS')")
public class QuoteLineBCController {

    private final QuoteLineBCService service;
    private final AdminRepository adminRepository;
    private final CompanyScopeService companyScopeService;

    /**
     * GET : liste des lignes de devis filtrées par CompareQuoteNo + ReferenceMaster
     */
    @GetMapping
    public List<QuoteLineBC> getQuoteLines(
            @AuthenticationPrincipal String email,
            @RequestParam String compareQuoteNo,
            @RequestParam String referenceMaster
    ) {
        String companyId = companyScopeService.requireUserCompanyId(email);
        return service.getQuoteLines(companyId, compareQuoteNo, referenceMaster);
    }

    /**
     * GET : lignes de devis d'un comparateur, filtrées par CompareQuoteNo (sans ReferenceMaster),
     * triées par "no" croissant, paginées (page/size). Filtres optionnels stock / dateDernierAchat / quantity
     * appliqués côté BC AVANT la pagination. Utilisé par l'écran Confirmation Achat.
     */
    @GetMapping("/by-compare-quote")
    public QuoteLineBCService.PagedQuoteLines getQuoteLinesByCompareQuote(
            @AuthenticationPrincipal String email,
            @RequestParam String compareQuoteNo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String stockOperator,
            @RequestParam(required = false) BigDecimal stockValue,
            @RequestParam(required = false) String dateDernierAchatOperator,
            @RequestParam(required = false) String dateDernierAchatValue,
            @RequestParam(required = false) String quantityOperator,
            @RequestParam(required = false) BigDecimal quantityValue,
            @RequestParam(required = false) String qtyFirstConfirmationOperator,
            @RequestParam(required = false) BigDecimal qtyFirstConfirmationValue,
            @RequestParam(required = false) String referenceOperator,
            @RequestParam(required = false) String referenceValue
    ) {
        String companyId = companyScopeService.requireUserCompanyId(email);
        // Réponse paginée { content, totalElements, totalPages, page, size }
        return service.getQuoteLinesByCompareQuote(
                companyId, compareQuoteNo, page, size,
                stockOperator, stockValue,
                dateDernierAchatOperator, dateDernierAchatValue,
                quantityOperator, quantityValue,
                qtyFirstConfirmationOperator, qtyFirstConfirmationValue,
                referenceOperator, referenceValue);
    }

    /**
     * PATCH : mise à jour d'une ligne de devis.
     */
    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('PURCHASE_CONFIRMATION_ACTIONS','COMPARATOR_CART_ACTIONS')")
    public void updateQuoteLine(
            @AuthenticationPrincipal String email,
            @PathVariable String id,
            @RequestBody QuoteLineUpdateRequest request
    ) {
        String companyId = companyScopeService.requireUserCompanyId(email);
        service.updateQuoteLine(companyId, id, request);
    }

    /**
     * GET : Calcule le montant total d'un document via une action BC optimisée.
     */
    @GetMapping("/total-amount")
    public ResponseEntity<BigDecimal> getTotalAmount(
            @RequestParam String documentNo,
            @AuthenticationPrincipal String email) {

        // Correction : Utilisation de l'instance injectée adminRepository
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        // Vérification de la société affectée au profil
        if (admin.getBcCompanyId() == null || admin.getBcCompanyId().isBlank()) {
            return ResponseEntity.badRequest().body(BigDecimal.ZERO);
        }

        BigDecimal total = service.getTotalAmountFromBC(admin.getBcCompanyId(), documentNo);
        return ResponseEntity.ok(total);
    }
}