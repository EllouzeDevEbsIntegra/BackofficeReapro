package com.reapro.achat.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.reapro.achat.DTO.BcItemEnrichedResponse;
import com.reapro.achat.DTO.PagedResponse;
import com.reapro.achat.DTO.bc.BcItemBC;
import com.reapro.achat.DTO.bc.BcListResponse;
import com.reapro.achat.entities.sqlserver.LastInvoicedItemCost;
import com.reapro.achat.repositories.sqlserver.LastInvoicedItemCostRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BcItemBCService {

    private final BusinessCentralService bcService;
    private final LastInvoicedItemCostRepository lastInvoicedRepo;

    public PagedResponse<BcItemEnrichedResponse> getItemsByReferenceAndNotNoSortedLocally(
            String companyId,
            String referenceMaster,
            String noNe,
            int page,
            int size
    ) {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;


        List<BcItemEnrichedResponse> allEnriched =
                loadItemsEqvRaw(companyId, referenceMaster, noNe);

        long totalElements = allEnriched.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        int from = page * size;
        if (from >= allEnriched.size()) {
            return new PagedResponse<>(List.of(), page, size, totalElements, totalPages);
        }
        int to = Math.min(from + size, allEnriched.size());

        List<BcItemEnrichedResponse> content = allEnriched.subList(from, to);

        return new PagedResponse<>(content, page, size, totalElements, totalPages);
    }


    private List<BcItemBC> fetchAllBcItems(String companyId, String referenceMaster, String noNe) {
        String filter = String.format(
                "ReferenceMaster eq '%s' and no ne '%s'",
                escapeOData(referenceMaster),
                escapeOData(noNe)
        );

        final int top = 1000;
        int skip = 0;

        List<BcItemBC> result = new ArrayList<>();

        while (true) {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("$filter", filter);

            // Limiter les champs retournés (inclut manufacturerTecdocId)
            params.put("$select",
                    "id,vendorNo,VendorItemNo, manufacturerTecdocId,no,ReferenceMaster,descriptionStructured," +
                            "qtyStock,qtyImport,qtyOnPurchOrder,totalVendu,totalAchete," +
                            "lastPurshCostDS,lastPurshDate,unitPrice,lastCurrPrice,lastDate," +
                            "styleQty,styleImportQty,styleOnPurchQty," +
                            "LastPreferential,venduCurrYear,acheteCurrYear"
            );

            params.put("$top", String.valueOf(top));
            params.put("$skip", String.valueOf(skip));

            BcItemListResponse resp = bcService.getCustom(
                    "bcItems",
                    companyId,
                    params,
                    BcItemListResponse.class
            );

            List<BcItemBC> page = (resp != null && resp.getValue() != null) ? resp.getValue() : List.of();
            if (page.isEmpty()) break;

            result.addAll(page);

            if (page.size() < top) break;
            skip += top;

            if (skip > 500_000) break; // garde-fou
        }

        return result;
    }
    public List<BcItemEnrichedResponse> loadItemsEqvRaw(String companyId, String referenceMaster, String noNe) {

        log.info("CACHE MISS itemsEqvRaw => recalcul pour companyId={}, referenceMaster={}, noNe={}",
                companyId, referenceMaster, noNe);

        // 1) Charger toutes les lignes BC
        List<BcItemBC> all = fetchAllBcItems(companyId, referenceMaster, noNe);

        // 2) Tri local (LastPurshCostDS DESC)
        all.sort(Comparator
                .comparing((BcItemBC i) -> nz(i.getLastPurshCostDS()), Comparator.reverseOrder())
                .thenComparing(i -> safe(i.getNo()))
        );

        // 3) Enrichissement SQL (index par no uniquement) - 1 seule requête
        List<String> itemNos = all.stream()
                .map(BcItemBC::getNo)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        List<LastInvoicedItemCost> lastCosts = itemNos.isEmpty()
                ? List.of()
                : lastInvoicedRepo.findByNoIn(itemNos);

        Map<String, LastInvoicedItemCost> byNo = new HashMap<>();
        for (LastInvoicedItemCost lc : lastCosts) {
            String keyNo = safe(lc.getNo()).trim();
            byNo.merge(keyNo, lc, (a, b) -> {
                if (a.getLastInvoicedCostDate() == null) return b;
                if (b.getLastInvoicedCostDate() == null) return a;
                return b.getLastInvoicedCostDate().isAfter(a.getLastInvoicedCostDate()) ? b : a;
            });
        }

        // 4) Construire la liste enrichie finale
        return all.stream().map(it -> {
            LastInvoicedItemCost lc = byNo.get(safe(it.getNo()).trim());
            return new BcItemEnrichedResponse(
                    it,
                    lc != null ? lc.getLastInvoicedDirectCost() : null,
                    lc != null ? lc.getQuantity() : null,
                    lc != null ? lc.getLastInvoicedCostDate() : null
            );
        }).toList();
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String escapeOData(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BcItemListResponse extends BcListResponse<BcItemBC> {}
}