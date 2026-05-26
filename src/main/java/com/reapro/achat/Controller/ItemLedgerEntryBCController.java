package com.reapro.achat.Controller;

import com.reapro.achat.DTO.ItemLedgerEntryPageResponse;
import com.reapro.achat.services.ItemLedgerEntryBCService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bc/item-ledger-entries")
@RequiredArgsConstructor
public class ItemLedgerEntryBCController {

    private final ItemLedgerEntryBCService service;

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
            @RequestParam String itemNo,
            @RequestParam(required = false, defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "20C5337E-2E49-EC11-A103-00155DB6A301") String companyId,
            @RequestParam(required = false) String sourceNo,
            @RequestParam(required = false, defaultValue = "false") boolean allYears
    ) {
        return service.getItemLedgerEntriesByItemAndYear(companyId, itemNo, year, page, size, sourceNo, allYears);
    }
}