package com.reapro.achat.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @Cacheable(
            cacheNames = CacheConfig.LEDGER_PAGE,
            key = "T(String).valueOf(#companyId).trim().toLowerCase() + '|' + " +
                    "T(String).valueOf(#itemNo).trim().toLowerCase() + '|' + " +
                    "#year + '|' + #page + '|' + #size + '|noTransfer'"
    )
    public ItemLedgerEntryPageResponse getItemLedgerEntriesByItemAndYear(
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

        // Filtre année par plage (BC n'aime pas year(PostingDate))
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = start.plusYears(1);

        String filter = String.format(
                "ItemNo eq '%s' and PostingDate ge %s and PostingDate lt %s and EntryType ne 'Transfer'",
                escapeODataString(itemNo), start, end
        );

        // 1) Appel BC paginé pour content
        Map<String, String> params = new LinkedHashMap<>();
        params.put("$filter", filter);
        params.put("$orderby", "PostingDate desc");
        params.put("$top", String.valueOf(size));
        params.put("$skip", String.valueOf(skip));
        params.put("$count", "true");

        BcPageResponse pageResponse = bcService.getCustom(
                BC_ENDPOINT,
                companyId,
                params,
                BcPageResponse.class
        );

        List<ItemLedgerEntryBC> bcList = (pageResponse != null && pageResponse.value != null)
                ? pageResponse.value
                : Collections.emptyList();

        long totalElements = (pageResponse != null && pageResponse.count != null)
                ? pageResponse.count
                : bcList.size();

        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 1;

        List<ItemLedgerEntryResponse> content = bcList.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        // 2) ✅ Recap global : maintenant caché séparément
        // Important : appel via proxy Spring (sinon @Cacheable ignoré)
        ItemLedgerEntryBCService self = ApplicationContextProvider.getBean(ItemLedgerEntryBCService.class);
        Map<String, BigDecimal> quantityByEntryType = self.getRecapQuantityByEntryTypeCached(companyId, itemNo, year);

        return new ItemLedgerEntryPageResponse(
                content,
                page,
                size,
                totalElements,
                totalPages,
                quantityByEntryType
        );
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