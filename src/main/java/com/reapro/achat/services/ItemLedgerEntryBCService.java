package com.reapro.achat.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.reapro.achat.DTO.ImportLedgerLinePageResponse;
import com.reapro.achat.DTO.ImportLedgerLineResponse;
import com.reapro.achat.DTO.ItemLedgerEntryPageResponse;
import com.reapro.achat.DTO.ItemLedgerEntryResponse;
import com.reapro.achat.DTO.bc.ItemLedgerEntryBC;
import com.reapro.achat.config.ApplicationContextProvider;
import com.reapro.achat.config.CacheConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemLedgerEntryBCService {

    private final BusinessCentralService bcService;

    // ✅ Mets ici le bon endpoint BC
    private static final String BC_ENDPOINT = "specificItemLedgerEntries";

    // ------------------------------------------------------------
    // Cache PAGE : une entrée par (companyId, itemNo, year, page, size)
    // ------------------------------------------------------------
    public ItemLedgerEntryPageResponse getItemLedgerEntriesByItemAndYear(
            String companyId,
            String itemNo,
            int year,
            int page,
            int size
    ) {
        return getItemLedgerEntriesByItemAndYear(companyId, itemNo, year, page, size, null, false);
    }

    public ItemLedgerEntryPageResponse getItemLedgerEntriesByItemAndYear(
            String companyId,
            String itemNo,
            int year,
            int page,
            int size,
            String sourceNo,
            boolean allYears
    ) {
        // Si filtrage client → pas de cache (données trop spécifiques)
        if (sourceNo != null && !sourceNo.isBlank()) {
            return fetchFromBcDirectly(companyId, itemNo, year, page, size, sourceNo, allYears);
        }
        // Sinon, déléguer à la méthode cachée
        ItemLedgerEntryBCService self = ApplicationContextProvider.getBean(ItemLedgerEntryBCService.class);
        return self.getItemLedgerEntriesByItemAndYearCached(companyId, itemNo, year, page, size);
    }

    private ItemLedgerEntryPageResponse fetchFromBcDirectly(
            String companyId, String itemNo, int year, int page, int size,
            String sourceNo, boolean allYears) {

        if (page < 0) page = 0;
        if (size <= 0) size = 50;
        int skip = page * size;

        StringBuilder filterSb = new StringBuilder();
        filterSb.append(String.format("ItemNo eq '%s'", escapeODataString(itemNo)));
        filterSb.append(" and EntryType ne 'Transfer'");

        if (!allYears && year > 0) {
            LocalDate start = LocalDate.of(year, 1, 1);
            LocalDate end = start.plusYears(1);
            filterSb.append(String.format(" and PostingDate ge %s and PostingDate lt %s", start, end));
        }

        if (sourceNo != null && !sourceNo.isBlank()) {
            filterSb.append(String.format(" and SourceNo eq '%s'", escapeODataString(sourceNo)));
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("$filter", filterSb.toString());
        params.put("$orderby", "PostingDate desc");
        params.put("$top", String.valueOf(size));
        params.put("$skip", String.valueOf(skip));
        params.put("$count", "true");

        BcPageResponse pageResponse = bcService.getCustom(BC_ENDPOINT, companyId, params, BcPageResponse.class);

        List<ItemLedgerEntryBC> bcList = (pageResponse != null && pageResponse.value != null)
                ? pageResponse.value : Collections.emptyList();

        long totalElements = (pageResponse != null && pageResponse.count != null)
                ? pageResponse.count : bcList.size();

        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 1;

        List<ItemLedgerEntryResponse> content = bcList.stream().map(this::toResponse).collect(Collectors.toList());

        // Recap (total par type)
        Map<String, BigDecimal> recap = computeQuantityRecapByEntryType(companyId, filterSb.toString());

        return new ItemLedgerEntryPageResponse(content, page, size, totalElements, totalPages, recap);
    }

    // Méthode cachée originale (sans client)
    @Cacheable(
            cacheNames = CacheConfig.LEDGER_PAGE,
            key = "T(String).valueOf(#companyId).trim().toLowerCase() + '|' + " +
                    "T(String).valueOf(#itemNo).trim().toLowerCase() + '|' + " +
                    "#year + '|' + #page + '|' + #size + '|noTransfer'"
    )
    public ItemLedgerEntryPageResponse getItemLedgerEntriesByItemAndYearCached(
            String companyId,
            String itemNo,
            int year,
            int page,
            int size
    ) {
        log.info("CACHE MISS itemLedgerEntriesPage => companyId={}, itemNo={}, year={}, page={}, size={}",
                companyId, itemNo, year, page, size);

        if (page < 0) page = 0;
        if (size <= 0) size = 50;

        int skip = page * size;

        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = start.plusYears(1);

        String filter = String.format(
                "ItemNo eq '%s' and PostingDate ge %s and PostingDate lt %s and EntryType ne 'Transfer'",
                escapeODataString(itemNo), start, end
        );

        Map<String, String> params = new LinkedHashMap<>();
        params.put("$filter", filter);
        params.put("$orderby", "PostingDate desc");
        params.put("$top", String.valueOf(size));
        params.put("$skip", String.valueOf(skip));
        params.put("$count", "true");

        BcPageResponse pageResponse = bcService.getCustom(BC_ENDPOINT, companyId, params, BcPageResponse.class);

        List<ItemLedgerEntryBC> bcList = (pageResponse != null && pageResponse.value != null)
                ? pageResponse.value : Collections.emptyList();

        long totalElements = (pageResponse != null && pageResponse.count != null)
                ? pageResponse.count : bcList.size();

        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 1;

        List<ItemLedgerEntryResponse> content = bcList.stream().map(this::toResponse).collect(Collectors.toList());

        ItemLedgerEntryBCService self = ApplicationContextProvider.getBean(ItemLedgerEntryBCService.class);
        Map<String, BigDecimal> quantityByEntryType = self.getRecapQuantityByEntryTypeCached(companyId, itemNo, year);

        return new ItemLedgerEntryPageResponse(content, page, size, totalElements, totalPages, quantityByEntryType);
    }

    // ------------------------------------------------------------
    // Cache RECAP : une entrée par (companyId, itemNo, year)
    // ------------------------------------------------------------
    @Cacheable(
            cacheNames = CacheConfig.LEDGER_RECAP,
            key = "T(String).valueOf(#companyId).trim().toLowerCase() + '|' + " +
                    "T(String).valueOf(#itemNo).trim().toLowerCase() + '|' + #year + '|noTransfer'"
    )
    public Map<String, BigDecimal> getRecapQuantityByEntryTypeCached(String companyId, String itemNo, int year) {

        log.info("CACHE MISS itemLedgerEntriesRecap => companyId={}, itemNo={}, year={}", companyId, itemNo, year);

        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = start.plusYears(1);

        String filter = String.format(
                "ItemNo eq '%s' and PostingDate ge %s and PostingDate lt %s and EntryType ne 'Transfer'",
                escapeODataString(itemNo), start, end
        );

        Map<String, BigDecimal> sums = computeQuantityRecapByEntryType(companyId, filter);

        // éviter de renvoyer une map mutable depuis le cache
        return Collections.unmodifiableMap(new LinkedHashMap<>(sums));
    }

    // ------------------------------------------------------------
    // Lignes IMPORT : écritures item-ledger en magasin d'import (RemainingQuantity > 0)
    // pour un article + fournisseur. Réutilise la MÊME base BC custom (specificItemLedgerEntries).
    // Filtres BC fixes : ItemNo + isImportLocation eq true + RemainingQuantity gt 0 + SourceNo.
    // Pas de cache (filtré par fournisseur, données spécifiques) — comme le filtrage par sourceNo.
    // ------------------------------------------------------------
    // Colonnes Import autorisées au tri (whitelist) → on n'injecte JAMAIS une colonne brute dans $orderby.
    private static final java.util.Set<String> IMPORT_SORT_WHITELIST = java.util.Set.of(
            "DocumentNo", "SourceNo", "SourceName", "LocationCode",
            "PostingDate", "Quantity", "RemainingQuantity", "CostAmountExpected"
    );

    /** Construit un $orderby sûr à partir de "field,dir". Défaut = "PostingDate desc". */
    private String buildImportOrderBy(String sort) {
        String field = "PostingDate";
        String dir = "desc";
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String requested = parts[0].trim();
            String canonical = IMPORT_SORT_WHITELIST.stream()
                    .filter(w -> w.equalsIgnoreCase(requested))
                    .findFirst()
                    .orElse(null);
            if (canonical != null) {
                field = canonical;
                String d = (parts.length > 1) ? parts[1].trim().toLowerCase() : "asc";
                dir = ("asc".equals(d) || "desc".equals(d)) ? d : "desc";
            }
        }
        return field + " " + dir;
    }

    public ImportLedgerLinePageResponse getImportLedgerLines(
            String companyId,
            String itemNo,
            String sourceNo,
            int page,
            int size,
            String sort
    ) {
        if (page < 0) page = 0;
        if (size <= 0) size = 50;
        int skip = page * size;

        StringBuilder filterSb = new StringBuilder();
        filterSb.append(String.format("ItemNo eq '%s'", escapeODataString(itemNo)));
        filterSb.append(" and isImportLocation eq true");
        filterSb.append(" and RemainingQuantity gt 0");
        filterSb.append(String.format(" and SourceNo eq '%s'", escapeODataString(sourceNo)));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("$filter", filterSb.toString());
        params.put("$orderby", buildImportOrderBy(sort));   // whitelist + défaut PostingDate desc
        params.put("$top", String.valueOf(size));
        params.put("$skip", String.valueOf(skip));
        params.put("$count", "true");

        BcPageResponse pageResponse = bcService.getCustom(BC_ENDPOINT, companyId, params, BcPageResponse.class);

        List<ItemLedgerEntryBC> bcList = (pageResponse != null && pageResponse.value != null)
                ? pageResponse.value : Collections.emptyList();

        long totalElements = (pageResponse != null && pageResponse.count != null)
                ? pageResponse.count : bcList.size();

        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 1;

        List<ImportLedgerLineResponse> content = bcList.stream()
                .map(this::toImportLineResponse)
                .collect(Collectors.toList());

        return new ImportLedgerLinePageResponse(content, page, size, totalElements, totalPages);
    }

    private ImportLedgerLineResponse toImportLineResponse(ItemLedgerEntryBC e) {
        return new ImportLedgerLineResponse(
                e.getDocumentNo(),
                e.getSourceNo(),
                e.getSourceName(),
                e.getLocationCode(),
                e.getPostingDate(),
                e.getQuantity(),
                e.getRemainingQuantity(),
                e.getCostAmountExpected()
        );
    }

    private ItemLedgerEntryResponse toResponse(ItemLedgerEntryBC e) {
        return new ItemLedgerEntryResponse(
                e.getPostingDate(),
                e.getEntryType(),
                e.getDocumentType(),
                e.getDocumentNo(),
                e.getItemNo(),
                e.getLocationCode(),
                e.getQuantity(),
                e.getUnitOfMeasureCode(),
                e.getQtyPerUnitOfMeasure(),
                e.getInvoicedQuantity(),
                e.getSalesAmountExpected(),
                e.getSalesAmountActual(),
                e.getCostAmountExpected(),
                e.getCostAmountActual(),
                e.getSourceNo(),
                e.getSourceType(),
                e.getSourceName()
        );
    }

    /**
     * Recap global via pagination BC en $select (EntryType, Quantity) pour réduire la charge.
     */
    private Map<String, BigDecimal> computeQuantityRecapByEntryType(String companyId, String filter) {
        Map<String, BigDecimal> sums = new LinkedHashMap<>();

        final int top = 1000;
        int skip = 0;

        while (true) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("$filter", filter);
            params.put("$select", "EntryType,Quantity");
            params.put("$top", String.valueOf(top));
            params.put("$skip", String.valueOf(skip));

            BcRecapResponse resp = bcService.getCustom(
                    BC_ENDPOINT,
                    companyId,
                    params,
                    BcRecapResponse.class
            );

            List<RecapRow> rows = (resp != null && resp.value != null)
                    ? resp.value
                    : Collections.emptyList();

            if (rows.isEmpty()) break;

            for (RecapRow r : rows) {
                String type = (r.entryType == null || r.entryType.isBlank()) ? "UNKNOWN" : r.entryType;
                BigDecimal q = (r.quantity == null) ? BigDecimal.ZERO : r.quantity;
                sums.merge(type, q, BigDecimal::add);
            }

            if (rows.size() < top) break;
            skip += top;

            if (skip > 500_000) break; // garde-fou
        }

        return sums;
    }

    private String escapeODataString(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    // ───────────── Wrappers BC ─────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BcPageResponse {
        @JsonProperty("@odata.count")
        private Long count;
        private List<ItemLedgerEntryBC> value;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BcRecapResponse {
        private List<RecapRow> value;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RecapRow {
        @JsonProperty("EntryType")
        private String entryType;

        @JsonProperty("Quantity")
        private BigDecimal quantity;
    }
}