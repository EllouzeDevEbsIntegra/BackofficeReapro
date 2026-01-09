package com.reapro.achat.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.reapro.achat.DTO.BcItemEnrichedResponse;
import com.reapro.achat.DTO.ElvaItemKitResponse;
import com.reapro.achat.DTO.PagedResponse;
import com.reapro.achat.DTO.bc.BcItemBC;
import com.reapro.achat.DTO.bc.BcListResponse;
import com.reapro.achat.DTO.bc.PurchaseCartLineBC;
import com.reapro.achat.config.ApplicationContextProvider;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.entities.sqlserver.LastInvoicedItemCost;
import com.reapro.achat.repositories.primary.AdminRepository;
import com.reapro.achat.repositories.sqlserver.LastInvoicedItemCostRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemsKitService {

    private final AdminRepository adminRepository;
    private final ElvaItemKitService elvaItemKitService;
    private final BusinessCentralService bcService;
    private final LastInvoicedItemCostRepository lastInvoicedRepo;
    private final PurchaseCartService purchaseCartService;

    // Ajuste si tu as beaucoup de kits (évite URL trop longue)
    private static final int BC_FILTER_CHUNK_SIZE = 30;

    // Même $select que ton /api/bc/itemsEqv
    private static final String BC_ITEMS_SELECT =
            "id,vendorNo,VendorItemNo, manufacturerTecdocId,no,ReferenceMaster,descriptionStructured," +
                    "qtyStock,qtyImport,qtyOnPurchOrder,totalVendu,totalAchete," +
                    "lastPurshCostDS,lastPurshDate,unitPrice,lastCurrPrice,lastDate," +
                    "styleQty,styleImportQty,styleOnPurchQty," +
                    "LastPreferential,venduCurrYear,acheteCurrYear";

    public PagedResponse<BcItemEnrichedResponse> getItemsKit(
            String userEmail,
            String no,
            int page,
            int size,
            String compareQuoteNo
    ) {
        if (no == null || no.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le paramètre 'no' est obligatoire.");
        }
        if (page < 0) page = 0;
        if (size <= 0) size = 20;

        // 1) companyId depuis le profil
        Admin admin = adminRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable."));

        if (admin.getBcCompanyId() == null || admin.getBcCompanyId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Aucune société BC n'est affectée à votre profil. Veuillez choisir une société."
            );
        }

        String companyId = admin.getBcCompanyId().trim();

        List<BcItemEnrichedResponse> all = loadItemsKitRaw(companyId, no.trim(), compareQuoteNo);

        // 3) pagination locale
        long totalElements = all.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        int from = page * size;
        if (from >= all.size()) {
            return new PagedResponse<>(List.of(), page, size, totalElements, totalPages);
        }
        int to = Math.min(from + size, all.size());

        return new PagedResponse<>(all.subList(from, to), page, size, totalElements, totalPages);
    }

    public List<BcItemEnrichedResponse> loadItemsKitRaw(String companyId, String no, String compareQuoteNo) {

        log.info("CACHE MISS itemsKitRaw => recalcul pour companyId={}, no={}", companyId, no);
        String inputNo = no.trim();
        // 1) SQL : récupérer les lignes du kit
        List<ElvaItemKitResponse> kitLines = elvaItemKitService.getByArticle(no.trim());

        // 2) Fusion Article + Item Kit dans un seul set
        LinkedHashSet<String> allNos = new LinkedHashSet<>();
        for (ElvaItemKitResponse l : kitLines) {
            if (l.getArticle() != null && !l.getArticle().isBlank()) {
                allNos.add(l.getArticle().trim());
            }
            if (l.getItemKit() != null && !l.getItemKit().isBlank()) {
                allNos.add(l.getItemKit().trim());
            }
        }



        log.info("KIT SQL rows={}, distinct allNos={}", kitLines.size(), allNos.size());

        removeIgnoreCase(allNos, inputNo);

        if (allNos.isEmpty()) {
            return List.of();
        }

        // 3) ✅ Un seul appel BC (chunké)
        List<BcItemBC> allBcItems = fetchBcItemsByNos(companyId, new ArrayList<>(allNos));
        allBcItems.removeIf(it -> equalsIgnoreCaseTrim(it.getNo(), inputNo));
        if (allBcItems.isEmpty()) {
            return List.of();
        }

        // 6) Tri identique à itemsEqv (LastPurshCostDS DESC)
        allBcItems.sort(Comparator
                .comparing((BcItemBC i) -> nz(i.getLastPurshCostDS()), Comparator.reverseOrder())
                .thenComparing(i -> safe(i.getNo()))
        );

        // 7) Enrichissement SQL (indexé uniquement par no)
        List<String> itemNos = allBcItems.stream()
                .map(BcItemBC::getNo)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        // 8) Exécution PARALLÈLE : SQL Costs + BC Panier
        
        // Tâche A : Récupération SQL
        CompletableFuture<Map<String, LastInvoicedItemCost>> costsFuture = CompletableFuture.supplyAsync(() -> {
            if (itemNos.isEmpty()) return Collections.emptyMap();
            
            List<LastInvoicedItemCost> lastCosts = lastInvoicedRepo.findByNoIn(itemNos);
            Map<String, LastInvoicedItemCost> map = new HashMap<>();
            for (LastInvoicedItemCost lc : lastCosts) {
                String key = safe(lc.getNo()).trim();
                map.merge(key, lc, (a, b) -> {
                    LocalDate da = a.getLastInvoicedCostDate();
                    LocalDate db = b.getLastInvoicedCostDate();
                    if (da == null) return b;
                    if (db == null) return a;
                    return db.isAfter(da) ? b : a;
                });
            }
            return map;
        });

        // Tâche B : Récupération Panier BC
        CompletableFuture<Map<String, PurchaseCartLineBC>> cartFuture = CompletableFuture.supplyAsync(() -> 
            purchaseCartService.getPurchaseCartLinesMap(companyId, compareQuoteNo)
        );

        // Attente des deux tâches
        CompletableFuture.allOf(costsFuture, cartFuture).join();

        Map<String, LastInvoicedItemCost> byNo = costsFuture.join();
        Map<String, PurchaseCartLineBC> cartLinesMap = cartFuture.join();

        return allBcItems.stream().map(it -> {
            LastInvoicedItemCost lc = byNo.get(safe(it.getNo()).trim());
            
            // Vérification Panier
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



    private List<BcItemBC> fetchBcItemsByNos(String companyId, List<String> nos) {
        if (nos == null || nos.isEmpty()) return List.of();

        List<BcItemBC> out = new ArrayList<>();

        for (List<String> chunk : chunk(nos, BC_FILTER_CHUNK_SIZE)) {
            String filter = buildNoOrFilter(chunk);

            Map<String, String> params = new LinkedHashMap<>();
            params.put("$filter", filter);
            params.put("$select", BC_ITEMS_SELECT);

            BcItemsListResponse resp = bcService.getCustom(
                    "bcItems",
                    companyId,
                    params,
                    BcItemsListResponse.class
            );

            if (resp != null && resp.getValue() != null && !resp.getValue().isEmpty()) {
                out.addAll(resp.getValue());
            }
        }

        return out;
    }

    private String buildNoOrFilter(List<String> nos) {
        return nos.stream()
                .map(n -> "no eq '" + escapeOData(n) + "'")
                .collect(Collectors.joining(" or "));
    }

    private void removeIgnoreCase(Set<String> set, String value) {
        if (value == null) return;
        String v = value.trim();
        if (v.isBlank()) return;

        set.removeIf(s -> s != null && s.trim().equalsIgnoreCase(v));
    }

    private boolean equalsIgnoreCaseTrim(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private String escapeOData(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private <T> List<List<T>> chunk(List<T> list, int chunkSize) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            out.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return out;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BcItemsListResponse extends BcListResponse<BcItemBC> {}
}
