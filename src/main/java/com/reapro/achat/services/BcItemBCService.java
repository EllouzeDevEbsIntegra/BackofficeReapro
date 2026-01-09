package com.reapro.achat.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.reapro.achat.DTO.BcItemEnrichedResponse;
import com.reapro.achat.DTO.PagedResponse;
import com.reapro.achat.DTO.bc.BcItemBC;
import com.reapro.achat.DTO.bc.BcListResponse;
import com.reapro.achat.DTO.bc.PurchaseCartLineBC;
import com.reapro.achat.entities.sqlserver.LastInvoicedItemCost;
import com.reapro.achat.repositories.sqlserver.LastInvoicedItemCostRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class BcItemBCService {

    private final BusinessCentralService bcService;
    private final LastInvoicedItemCostRepository lastInvoicedRepo;
    private final PurchaseCartService purchaseCartService;

    public PagedResponse<BcItemEnrichedResponse> getItemsByReferenceAndNotNoSortedLocally(
            String companyId,
            String referenceMaster,
            String noNe,
            int page,
            int size,
            String compareQuoteNo
    ) {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;

        // 1. Récupérer le total (count) pour la pagination correcte
        long totalElements = countItems(companyId, referenceMaster, noNe);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        if (totalElements == 0) {
            return new PagedResponse<>(List.of(), page, size, 0, 0);
        }

        // 2. Récupérer la page demandée directement depuis BC (Trié et Paginé)
        List<BcItemEnrichedResponse> content = loadItemsEqvPage(
                companyId, referenceMaster, noNe, compareQuoteNo, page, size
        );

        return new PagedResponse<>(content, page, size, totalElements, totalPages);
    }

    private long countItems(String companyId, String referenceMaster, String noNe) {
        String filter = buildFilter(referenceMaster, noNe);
        Map<String, String> params = new HashMap<>();
        params.put("$filter", filter);
        params.put("$top", "0");
        params.put("$count", "true");

        BcItemListResponse resp = bcService.getCustom("bcItems", companyId, params, BcItemListResponse.class);
        // BcListResponse a un champ @JsonProperty("@odata.count") mappé si on utilise BcCountListResponse,
        // mais ici on utilise BcItemListResponse qui hérite de BcListResponse simple.
        // Il faut vérifier si BcListResponse gère le count ou utiliser une classe dédiée.
        // Pour simplifier et éviter de créer une classe, on peut utiliser BcCountListResponseWrapper de PurchaseCartService ou similaire,
        // ou supposer que le count est dans le body si on le mappe.
        // Le plus simple est de faire un appel dédié ou d'adapter la réponse.
        // Comme je ne veux pas casser l'existant, je vais utiliser une classe interne dédiée au count.

        return getCountFromBc(companyId, params);
    }

    private long getCountFromBc(String companyId, Map<String, String> params) {
        // Utilisation d'une classe ad-hoc pour récupérer le count
        BcCountResponse resp = bcService.getCustom("bcItems", companyId, params, BcCountResponse.class);
        return resp != null && resp.getCount() != null ? resp.getCount() : 0;
    }

    private List<BcItemEnrichedResponse> loadItemsEqvPage(
            String companyId,
            String referenceMaster,
            String noNe,
            String compareQuoteNo,
            int page,
            int size
    ) {
        // 1) Appel BC Paginé
        String filter = buildFilter(referenceMaster, noNe);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("$filter", filter);
        params.put("$orderby", "lastPurshCostDS desc, no asc"); // Tri demandé
        params.put("$top", String.valueOf(size));
        params.put("$skip", String.valueOf(page * size));
        params.put("$select",
                "id,vendorNo,VendorItemNo, manufacturerTecdocId,no,ReferenceMaster,descriptionStructured," +
                        "qtyStock,qtyImport,qtyOnPurchOrder,totalVendu,totalAchete," +
                        "lastPurshCostDS,lastPurshDate,unitPrice,lastCurrPrice,lastDate," +
                        "styleQty,styleImportQty,styleOnPurchQty," +
                        "LastPreferential,venduCurrYear,acheteCurrYear"
        );

        BcItemListResponse resp = bcService.getCustom("bcItems", companyId, params, BcItemListResponse.class);
        List<BcItemBC> pageItems = (resp != null && resp.getValue() != null) ? resp.getValue() : List.of();

        if (pageItems.isEmpty()) {
            return List.of();
        }

        // 2) Enrichissement Parallèle (SQL + Panier) sur les items de la page SEULEMENT

        List<String> itemNos = pageItems.stream()
                .map(BcItemBC::getNo)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        CompletableFuture<Map<String, LastInvoicedItemCost>> costsFuture = CompletableFuture.supplyAsync(() -> {
            if (itemNos.isEmpty()) return Collections.emptyMap();
            List<LastInvoicedItemCost> lastCosts = lastInvoicedRepo.findByNoIn(itemNos);
            Map<String, LastInvoicedItemCost> map = new HashMap<>();
            for (LastInvoicedItemCost lc : lastCosts) {
                String keyNo = safe(lc.getNo()).trim();
                map.merge(keyNo, lc, (a, b) -> {
                    if (a.getLastInvoicedCostDate() == null) return b;
                    if (b.getLastInvoicedCostDate() == null) return a;
                    return b.getLastInvoicedCostDate().isAfter(a.getLastInvoicedCostDate()) ? b : a;
                });
            }
            return map;
        });

        CompletableFuture<Map<String, PurchaseCartLineBC>> cartFuture = CompletableFuture.supplyAsync(() ->
            purchaseCartService.getPurchaseCartLinesMap(companyId, compareQuoteNo)
        );

        CompletableFuture.allOf(costsFuture, cartFuture).join();

        Map<String, LastInvoicedItemCost> byNo = costsFuture.join();
        Map<String, PurchaseCartLineBC> cartLinesMap = cartFuture.join();

        // 3) Construction de la réponse
        return pageItems.stream().map(it -> {
            LastInvoicedItemCost lc = byNo.get(safe(it.getNo()).trim());
            
            PurchaseCartLineBC cartLine = cartLinesMap.get(safe(it.getNo()));
            boolean existInCart = (cartLine != null);
            String commentInCart = (cartLine != null) ? cartLine.getComment() : null;
            
            return new BcItemEnrichedResponse(
                    it,
                    lc != null ? lc.getLastInvoicedDirectCost() : null,
                    lc != null ? lc.getQuantity() : null,
                    lc != null ? lc.getLastInvoicedCostDate() : null,
                    existInCart,
                    commentInCart
            );
        }).toList();
    }

    private String buildFilter(String referenceMaster, String noNe) {
        return String.format(
                "ReferenceMaster eq '%s' and no ne '%s'",
                escapeOData(referenceMaster),
                escapeOData(noNe)
        );
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

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BcCountResponse {
        @com.fasterxml.jackson.annotation.JsonProperty("@odata.count")
        private Long count;
    }
}
