package com.reapro.achat.services;

import com.reapro.achat.DTO.PagedResponse;
import com.reapro.achat.DTO.articlemanagement.ArticleExtraResponse;
import com.reapro.achat.DTO.articlemanagement.ArticleManagementItemResponse;
import com.reapro.achat.entities.primary.ElvaItemCache;
import com.reapro.achat.entities.sqlserver.LastInvoicedItemCost;
import com.reapro.achat.repositories.primary.ArticleCatalogRepository;
import com.reapro.achat.repositories.primary.ArticleCatalogRepository.OemCountProjection;
import com.reapro.achat.repositories.sqlserver.ElvaItemRepository;
import com.reapro.achat.repositories.sqlserver.ElvaItemRepository.ElvaItemRealTimeProjection;
import com.reapro.achat.repositories.sqlserver.LastInvoicedItemCostRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Liste des articles « Gestion Articles » (consultation V1) — architecture 2 niveaux :
 *
 *  - CATALOGUE (stable) : lu sur le cache PostgreSQL {@code elva_item_cache} (recherche, pagination,
 *    count, OEM count) → rapide, pas de scan de la vue SQL Server ELVA_Item.
 *  - TEMPS RÉEL (sensible) : stock / qté import / prix unitaire récupérés par batch sur SQL Server
 *    ({@link ElvaItemRepository#findRealTimeDataByNos}) UNIQUEMENT pour les lignes de la page.
 *
 * Données lourdes (dernier achat) déplacées dans le dialog détail ({@link #getExtra}) pour ne pas
 * ralentir la liste. Aucune donnée sensible loggée.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleManagementService {

    private final ArticleCatalogRepository catalogRepo;          // PostgreSQL (cache catalogue)
    private final ElvaItemRepository elvaItemRepository;         // SQL Server (temps réel batch)
    private final LastInvoicedItemCostRepository lastInvoicedRepo; // SQL Server (dialog détail)

    private static final int MAX_SIZE = 200;

    @Transactional(readOnly = true) // datasource PRIMARY (cache) — la lecture temps réel SQL Server ouvre sa propre transaction
    public PagedResponse<ArticleManagementItemResponse> getArticles(
            String search, String manufacturerCode, String groupCode, String subGroupCode, int page, int size) {

        if (page < 0) page = 0;
        if (size <= 0 || size > MAX_SIZE) size = 20;

        long tStart = System.currentTimeMillis();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "no"));
        Specification<ElvaItemCache> spec = buildSpec(search, manufacturerCode, groupCode, subGroupCode);

        // 1) CATALOGUE + count + pagination → PostgreSQL (rapide)
        long t1 = System.currentTimeMillis();
        Page<ElvaItemCache> cached = catalogRepo.findAll(spec, pageable);
        long listMs = System.currentTimeMillis() - t1;

        if (cached.isEmpty()) {
            logTiming(page, size, search, tStart, listMs, 0, 0, 0);
            return new PagedResponse<>(List.of(), page, size, cached.getTotalElements(), cached.getTotalPages());
        }

        List<String> itemNos = cached.getContent().stream()
                .map(ElvaItemCache::getNo).filter(Objects::nonNull)
                .map(String::trim).filter(s -> !s.isBlank()).distinct().toList();

        // 2) TEMPS RÉEL (stock / qté import / prix) → SQL Server batch (page courante uniquement)
        long t2 = System.currentTimeMillis();
        Map<String, ElvaItemRealTimeProjection> realtime = itemNos.isEmpty() ? Map.of()
                : elvaItemRepository.findRealTimeDataByNos(itemNos).stream()
                    .collect(Collectors.toMap(ElvaItemRealTimeProjection::getNo, p -> p, (a, b) -> a));
        long realtimeMs = System.currentTimeMillis() - t2;

        // 3) OEM count → PostgreSQL (cache), groupé pour la page
        long t3 = System.currentTimeMillis();
        Map<String, Long> oemCounts = loadOemCounts(itemNos);
        long oemMs = System.currentTimeMillis() - t3;

        List<ArticleManagementItemResponse> content = cached.getContent().stream()
                .map(c -> map(c, realtime, oemCounts))
                .toList();

        logTiming(page, size, search, tStart, listMs, realtimeMs, oemMs, content.size());
        return new PagedResponse<>(content, page, size, cached.getTotalElements(), cached.getTotalPages());
    }

    /** Données lourdes de l'article (dialog détail) : dernier achat (vue SQL Server, 1 article). */
    @Transactional(readOnly = true)
    public ArticleExtraResponse getExtra(String itemNo) {
        LocalDate last = null;
        if (StringUtils.hasText(itemNo)) {
            for (LastInvoicedItemCost lc : lastInvoicedRepo.findByNoIn(List.of(itemNo.trim()))) {
                LocalDate d = lc.getLastInvoicedCostDate();
                if (d != null && (last == null || d.isAfter(last))) last = d;
            }
        }
        return new ArticleExtraResponse(itemNo, last);
    }

    // ─────────────────────────────────────────────────────────────

    private Map<String, Long> loadOemCounts(List<String> itemNos) {
        if (itemNos.isEmpty()) return Map.of();
        return catalogRepo.findOemCountsByKeys(itemNos).stream()
                .filter(p -> p.getRefKey() != null)
                .collect(Collectors.toMap(p -> p.getRefKey().trim(), OemCountProjection::getCnt, (a, b) -> a));
    }

    private Specification<ElvaItemCache> buildSpec(String search, String manufacturerCode,
                                                   String groupCode, String subGroupCode) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Recherche LIGHT : uniquement si non vide, et sur peu de colonnes (réf, description, fabricant, frs)
            if (StringUtils.hasText(search)) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("no")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("fabricant")), like),
                        cb.like(cb.lower(root.get("vendorNo")), like)
                ));
            }
            if (StringUtils.hasText(manufacturerCode)) {
                String mc = manufacturerCode.trim();
                predicates.add(cb.or(cb.equal(root.get("codeFabricant"), mc), cb.equal(root.get("makeCode"), mc)));
            }
            if (StringUtils.hasText(groupCode)) {
                predicates.add(cb.equal(root.get("itemProductCode"), groupCode.trim()));
            }
            if (StringUtils.hasText(subGroupCode)) {
                predicates.add(cb.equal(root.get("itemSubProductCode"), subGroupCode.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private ArticleManagementItemResponse map(ElvaItemCache c,
                                              Map<String, ElvaItemRealTimeProjection> realtime,
                                              Map<String, Long> oemCounts) {
        String no = (c.getNo() != null) ? c.getNo().trim() : null;
        ElvaItemRealTimeProjection rt = (no != null) ? realtime.get(c.getNo()) : null;

        return ArticleManagementItemResponse.builder()
                .itemNo(c.getNo())
                .description(c.getDescription())
                .descriptionStructured(c.getDescriptionStructuree())
                .groupCode(c.getItemProductCode())
                .groupName(c.getGroupe())
                .subGroupCode(c.getItemSubProductCode())
                .subGroupName(c.getSousGroupe())
                // Temps réel (SQL Server) — null-safe si la ligne n'a pas de données temps réel
                .inventory(rt != null ? rt.getQuantite() : null)
                .qtyImport(rt != null ? rt.getReceptionQty() : null)
                .unitPrice(rt != null && rt.getUnitPrice() != null ? rt.getUnitPrice() : c.getUnitPrice())
                // Stable (cache)
                .unitCost(c.getUnitCost())
                .masterReference(c.getReferenceOrigineLie())
                .oemCount(no != null ? oemCounts.getOrDefault(no, 0L) : 0L)
                .lastPurchaseDate(null) // déplacé dans le dialog détail (getExtra)
                .totalSales(null)       // non disponible dans ELVA_Item (V1)
                .manufacturerCode(c.getCodeFabricant())
                .manufacturerName(c.getFabricant())
                .vendorNo(c.getVendorNo())
                .makeCode(c.getMakeCode())
                .tecdocIdFabricant(c.getTecdocIdFabricant())
                .vendorItemNo(c.getVendorItemNo())
                .build();
    }

    private void logTiming(int page, int size, String search, long tStart,
                           long listMs, long realtimeMs, long oemMs, int rows) {
        long total = System.currentTimeMillis() - tStart;
        log.info("[ArticleManagement] page={} size={} searchLen={} total={}ms cacheList(+count)={}ms realtime={}ms oem={}ms rows={}",
                page, size, (search == null ? 0 : search.trim().length()), total, listMs, realtimeMs, oemMs, rows);
    }
}
