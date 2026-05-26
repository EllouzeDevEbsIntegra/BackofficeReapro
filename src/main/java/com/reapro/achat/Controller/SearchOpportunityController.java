package com.reapro.achat.Controller;

import com.reapro.achat.DTO.CloseSearchOpportunityRequest;
import com.reapro.achat.DTO.SearchOpportunityResponse;
import com.reapro.achat.DTO.SearchOpportunityStatsResponse;
import com.reapro.achat.entities.primary.LocalSearchHistory;
import com.reapro.achat.services.SearchHistorySyncService;
import com.reapro.achat.services.SearchOpportunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search-opportunities")
@RequiredArgsConstructor
public class SearchOpportunityController {

    private final SearchOpportunityService searchOpportunityService;
    private final SearchHistorySyncService searchHistorySyncService; // Injection pour la migration

    @GetMapping
    public Page<SearchOpportunityResponse> getSearchOpportunities(
            Pageable pageable,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                java.time.LocalDate fromDateOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                java.time.LocalDate toDateOnly,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String extId,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean onlyZeroResults,
            @RequestParam(required = false) Boolean onlyNoStock,
            @RequestParam(required = false) Integer minAttempts,
            @RequestParam(required = false) Integer minDistinctCustomers,
            @RequestParam(required = false) Boolean includeExistingInErp) {

        // Support both ISO datetime and date-only params
        LocalDateTime resolvedFrom = fromDate != null ? fromDate
                : (fromDateOnly != null ? fromDateOnly.atStartOfDay() : null);
        LocalDateTime resolvedTo = toDate != null ? toDate
                : (toDateOnly != null ? toDateOnly.atTime(23, 59, 59) : null);

        return searchOpportunityService.getSearchOpportunities(
                pageable,
                resolvedFrom, resolvedTo,
                customerId, extId, companyName, type,
                onlyZeroResults, onlyNoStock,
                minAttempts, minDistinctCustomers, includeExistingInErp);
    }

    @GetMapping("/stats")
    public SearchOpportunityStatsResponse getStats() {
        return searchOpportunityService.getStats();
    }

    @GetMapping("/{normalizedFilter}")
    public List<LocalSearchHistory> getOpportunityDetails(@PathVariable String normalizedFilter) {
        return searchOpportunityService.getOpportunityDetails(normalizedFilter);
    }

    @PostMapping("/{normalizedFilter}/close")
    public ResponseEntity<Map<String, Object>> closeOpportunity(
            @AuthenticationPrincipal String email,
            @PathVariable String normalizedFilter,
            @Valid @RequestBody CloseSearchOpportunityRequest request) {

        int closedRows = searchOpportunityService.closeOpportunity(email, normalizedFilter, request);

        return ResponseEntity.ok(Map.of(
                "message", "Opportunité clôturée avec succès.",
                "closedRows", closedRows
        ));
    }

    // NOUVEL ENDPOINT TEMPORAIRE DE MIGRATION
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping("/admin/renormalize-history")
    public ResponseEntity<Map<String, Object>> renormalizeHistory() {
        int updatedCount = searchHistorySyncService.renormalizeExistingData();
        return ResponseEntity.ok(Map.of(
                "message", "Renormalisation terminée avec succès.",
                "updatedLines", updatedCount
        ));
    }
}