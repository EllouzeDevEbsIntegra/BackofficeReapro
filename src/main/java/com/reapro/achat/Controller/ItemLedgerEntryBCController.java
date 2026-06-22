package com.reapro.achat.Controller;

import com.reapro.achat.DTO.ItemLedgerEntryPageResponse;
import com.reapro.achat.services.CompanyScopeService;
import com.reapro.achat.services.ItemLedgerEntryBCService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// RBAC Lot 4bis-A : société = celle de l'utilisateur authentifié ; companyId client supprimé (anti-spoofing).
@RestController
@RequestMapping("/api/bc/item-ledger-entries")
@RequiredArgsConstructor
public class ItemLedgerEntryBCController {

    private final ItemLedgerEntryBCService service;
    private final CompanyScopeService companyScopeService;

    /**
     * GET /api/bc/item-ledger-entries?itemNo=317542&year=2025&page=0&size=50&companyId=...
     *
     * => BC:
     * itemLedgerEntries?
     *   $filter=ItemNo eq '317542' and PostingDate ge 2025-01-01 and PostingDate lt 2026-01-01
     *   &$top=50&$skip=0&$count=true
     */
    @GetMapping
    public ItemLedgerEntryPageResponse getItemLedgerEntries(
            @AuthenticationPrincipal String email,
            @RequestParam String itemNo,
            @RequestParam(required = false, defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sourceNo,
            @RequestParam(required = false, defaultValue = "false") boolean allYears
    ) {
        String companyId = companyScopeService.requireUserCompanyId(email);
        return service.getItemLedgerEntriesByItemAndYear(companyId, itemNo, year, page, size, sourceNo, allYears);
    }
}