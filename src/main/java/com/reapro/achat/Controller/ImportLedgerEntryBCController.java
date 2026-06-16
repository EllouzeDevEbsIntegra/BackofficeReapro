package com.reapro.achat.Controller;

import com.reapro.achat.DTO.ImportLedgerLinePageResponse;
import com.reapro.achat.services.ItemLedgerEntryBCService;
import lombok.RequiredArgsConstructor;
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
@RestController
@RequestMapping("/api/bc/import-ledger-entries")
@RequiredArgsConstructor
public class ImportLedgerEntryBCController {

    private final ItemLedgerEntryBCService service;

    @GetMapping
    public ImportLedgerLinePageResponse getImportLedgerLines(
            @RequestParam String itemNo,
            @RequestParam String sourceNo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "20C5337E-2E49-EC11-A103-00155DB6A301") String companyId,
            @RequestParam(required = false) String sort   // ex: "PostingDate,desc" (colonne whitelistée) ; défaut = PostingDate desc
    ) {
        return service.getImportLedgerLines(companyId, itemNo, sourceNo, page, size, sort);
    }
}
