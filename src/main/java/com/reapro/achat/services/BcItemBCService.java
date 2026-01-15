package com.reapro.achat.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.reapro.achat.DTO.BcItemEnrichedResponse;
import com.reapro.achat.DTO.PagedResponse;
import com.reapro.achat.DTO.bc.BcItemBC;
import com.reapro.achat.DTO.bc.BcListResponse;
import com.reapro.achat.DTO.bc.PurchaseCartLineBC;
import com.reapro.achat.DTO.bc.SiItemCategory;
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
        return getCountFromBc(companyId, params);
    }

    private long getCountFromBc(String companyId, Map<String, String> params) {
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
                        "LastPreferential,venduCurrYear,acheteCurrYear,toVerify"
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

    public BcItemBC updateToVerifyByNo(String companyId, String no) {
        // 1. Récupérer l'item pour avoir son ID et son ETag
        Map<String, String> params = new HashMap<>();
        params.put("$filter", "no eq '" + escapeOData(no) + "'");
        
        BcItemListResponse resp = bcService.getCustom("bcItems", companyId, params, BcItemListResponse.class);
        
        if (resp == null || resp.getValue() == null || resp.getValue().isEmpty()) {
            throw new RuntimeException("Item not found with no: " + no);
        }
        
        BcItemBC item = resp.getValue().get(0);
        String id = item.getId();
        String etag = item.getEtag(); 

        if (etag == null || etag.isBlank()) {
            etag = "*";
        }

        // 2. Faire le PATCH
        Map<String, Object> body = new HashMap<>();
        body.put("toVerify", true);
        
        return bcService.patchCustom("bcItems", companyId, id, body, etag, BcItemBC.class);
    }

    public List<SiItemCategory> getItemCategories(String companyId, Integer indentation, String parentCategory) {
        Map<String, String> params = new HashMap<>();
        
        StringBuilder filter = new StringBuilder();
        if (indentation != null) {
            filter.append("Indentation eq ").append(indentation);
        }
        
        if (parentCategory != null && !parentCategory.isBlank()) {
            if (filter.length() > 0) {
                filter.append(" and ");
            }
            filter.append("ParentCategory eq '").append(escapeOData(parentCategory)).append("'");
        }
        
        if (filter.length() > 0) {
            params.put("$filter", filter.toString());
        }
        
        SiItemCategoryListResponse resp = bcService.getCustom("SiItemCategory", companyId, params, SiItemCategoryListResponse.class);
        return (resp != null && resp.getValue() != null) ? resp.getValue() : List.of();
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

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SiItemCategoryListResponse extends BcListResponse<SiItemCategory> {}
}
