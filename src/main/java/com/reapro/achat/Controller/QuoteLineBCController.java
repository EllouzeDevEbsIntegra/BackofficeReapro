package com.reapro.achat.Controller;

import com.reapro.achat.DTO.bc.QuoteLineBC;
import com.reapro.achat.DTO.bc.QuoteLineUpdateRequest;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.repositories.primary.AdminRepository;
import com.reapro.achat.services.QuoteLineBCService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/bc/quote-lines")
@RequiredArgsConstructor
public class QuoteLineBCController {

    private final QuoteLineBCService service;
    private final AdminRepository adminRepository;

    /**
     * GET : liste des lignes de devis filtrées par CompareQuoteNo + ReferenceMaster
     */
    @GetMapping
    public List<QuoteLineBC> getQuoteLines(
            @RequestParam String compareQuoteNo,
            @RequestParam String referenceMaster,
            @RequestParam(defaultValue = "20C5337E-2E49-EC11-A103-00155DB6A301") String companyId
    ) {
        return service.getQuoteLines(companyId, compareQuoteNo, referenceMaster);
    }

    /**
     * PATCH : mise à jour d'une ligne de devis.
     */
    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateQuoteLine(
            @PathVariable String id,
            @RequestParam(defaultValue = "20C5337E-2E49-EC11-A103-00155DB6A301") String companyId,
            @RequestBody QuoteLineUpdateRequest request
    ) {
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