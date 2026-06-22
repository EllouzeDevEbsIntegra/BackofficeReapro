package com.reapro.achat.Controller;

import com.reapro.achat.DTO.ImportLedgerLinePageResponse;
import com.reapro.achat.services.CompanyScopeService;
import com.reapro.achat.services.ItemLedgerEntryBCService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Lignes "Import" (clic sur la quantité Import « I » de la colonne Appro, détail C2).
 *
 * GET /api/bc/import-ledger-entries?itemNo=0000100301&sourceNo=401219&page=0&size=50
 *
 * => BC (même base custom que /api/bc/item-ledger-entries, entité specificItemLedgerEntries) :
 *   specificItemLedgerEntries?
 *     $filter=ItemNo eq '0000100301' and isImportLocation eq true
 *             and RemainingQuantity gt 0 and SourceNo eq '401219'
 *     &$orderby=PostingDate desc&$top=50&$skip=0&$count=true
 *
 * Paramètres métier obligatoires : itemNo + sourceNo. page/size optionnels (pagination).
 */
// RBAC Lot 4bis-A : société = celle de l'utilisateur authentifié ; companyId client supprimé (anti-spoofing).
@RestController
@RequestMapping("/api/bc/import-ledger-entries")
@RequiredArgsConstructor
public class ImportLedgerEntryBCController {

    private final ItemLedgerEntryBCService service;
    private final CompanyScopeService companyScopeService;

    @GetMapping
    public ImportLedgerLinePageResponse getImportLedgerLines(
            @AuthenticationPrincipal String email,
            @RequestParam String itemNo,
            @RequestParam String sourceNo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort   // ex: "PostingDate,desc" (colonne whitelistée) ; défaut = PostingDate desc
    ) {
        String companyId = companyScopeService.requireUserCompanyId(email);
        return service.getImportLedgerLines(companyId, itemNo, sourceNo, page, size, sort);
    }
}
