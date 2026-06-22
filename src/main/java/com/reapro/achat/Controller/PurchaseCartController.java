package com.reapro.achat.Controller;

import com.reapro.achat.DTO.bc.PurchaseCartLineBC;
import com.reapro.achat.DTO.bc.PurchaseCartLineCreateRequest;
import com.reapro.achat.DTO.bc.PurchaseCartLineUpdateRequest;
import com.reapro.achat.services.CompanyScopeService;
import com.reapro.achat.services.PurchaseCartService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// RBAC Lot 4 : panier achat du Comparateur. Lecture = accès Comparateur/Confirmation ; écriture = actions panier.
// RBAC Lot 4bis-A : la société n'est plus fournie par le client (companyId @RequestParam supprimé) → elle est
// résolue depuis l'utilisateur authentifié (CompanyScopeService) ; absente → 403. Plus de company-spoofing.
@RestController
@RequestMapping("/api/bc/purchase-cart")
@RequiredArgsConstructor
public class PurchaseCartController {

    private final PurchaseCartService purchaseCartService;
    private final CompanyScopeService companyScopeService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('COMPARATOR_ACCESS','PURCHASE_CONFIRMATION_ACCESS')")
    public ResponseEntity<Page<PurchaseCartLineBC>> getPurchaseCartLines(
            @AuthenticationPrincipal String email,
            @RequestParam(required = false) String compareQuoteNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String vendorNo,
            @RequestParam(required = false) String itemNo, // Added itemNo
            Pageable pageable) {

        String companyId = companyScopeService.requireUserCompanyId(email);
        Page<PurchaseCartLineBC> response = purchaseCartService.getPurchaseCartLines(companyId, compareQuoteNo, status, vendorNo, itemNo, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyAuthority('COMPARATOR_ACCESS','PURCHASE_CONFIRMATION_ACCESS')")
    public ResponseEntity<Map<String, Long>> getPurchaseCartLinesCount(
            @AuthenticationPrincipal String email,
            @RequestParam String compareQuoteNo,
            @RequestParam(required = false) String itemNo) {

        String companyId = companyScopeService.requireUserCompanyId(email);
        long count = purchaseCartService.getPurchaseCartLinesCount(companyId, compareQuoteNo, itemNo);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('COMPARATOR_CART_ACTIONS')")
    public ResponseEntity<PurchaseCartLineBC> addPurchaseCartLine(
            @AuthenticationPrincipal String email,
            @RequestBody PurchaseCartLineCreateRequest request) {

        String companyId = companyScopeService.requireUserCompanyId(email);
        PurchaseCartLineBC response = purchaseCartService.addPurchaseCartLine(companyId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{lineNo}")
    @PreAuthorize("hasAuthority('COMPARATOR_CART_ACTIONS')")
    public ResponseEntity<PurchaseCartLineBC> updatePurchaseCartLine(
            @AuthenticationPrincipal String email,
            @PathVariable Integer lineNo,
            @RequestBody PurchaseCartLineUpdateRequest request) {

        String companyId = companyScopeService.requireUserCompanyId(email);
        PurchaseCartLineBC response = purchaseCartService.updatePurchaseCartLine(companyId, lineNo, request);
        return ResponseEntity.ok(response);
    }
}
