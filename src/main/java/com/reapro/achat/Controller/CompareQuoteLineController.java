// src/main/java/com/reapro/achat/Controller/CompareQuoteLineController.java

package com.reapro.achat.Controller;

import com.reapro.achat.DTO.CompareQuoteLineResponse;
import com.reapro.achat.entities.sqlserver.CompareQuoteLine;
import com.reapro.achat.services.CompareQuoteLineService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// RBAC Lot 4 : lignes de comparateur — partagées Comparateur / Confirmation Achat (lecture, super-admin bypass).
@RestController
@RequestMapping("/api/compare-quotes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('COMPARATOR_ACCESS','PURCHASE_CONFIRMATION_ACCESS')")
public class CompareQuoteLineController {

    private final CompareQuoteLineService service;

    @GetMapping("/{compareQuoteNo}/lines")
    public Page<CompareQuoteLineResponse> getLines(
            @AuthenticationPrincipal String email,
            @PathVariable String compareQuoteNo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String itemNo,
            @RequestParam(required = false) Integer pageNumber,
            @RequestParam(required = false) Boolean treated // ✅ Nouveau paramètre
    ) {
        return service.getLinesFiltered(
                email,
                compareQuoteNo,
                search,
                itemNo,
                pageNumber,
                treated,
                PageRequest.of(page, size, Sort.by("pageNumber").ascending())
        );
    }
}