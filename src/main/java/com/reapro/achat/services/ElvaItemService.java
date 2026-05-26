package com.reapro.achat.services;

import com.reapro.achat.DTO.ElvaItemKitResponse;
import com.reapro.achat.DTO.ElvaItemResponse;
import com.reapro.achat.DTO.ElvaItemSearchRequest;
import com.reapro.achat.entities.primary.ElvaItemCache;
import com.reapro.achat.entities.sqlserver.ElvaSalesPrice;
import com.reapro.achat.repositories.primary.ElvaItemCacheRepository;
import com.reapro.achat.repositories.sqlserver.ElvaItemRepository;
import com.reapro.achat.repositories.sqlserver.ElvaSalesPriceRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElvaItemService {

    private final ElvaItemRepository elvaItemRepository;
    private final ElvaItemCacheRepository elvaItemCacheRepository;
    private final ElvaSalesPriceRepository elvaSalesPriceRepository;
    private final ElvaItemKitService elvaItemKitService;

    @Qualifier("sqlServerJdbcTemplate")
    private final NamedParameterJdbcTemplate sqlServerJdbcTemplate;

    @Transactional(readOnly = true)
    public List<ElvaItemResponse> getTop1000ElvaItems(String clientId) {
        List<ElvaItemCache> cachedItems = elvaItemCacheRepository.findAll(Pageable.ofSize(1000)).getContent();
        if (cachedItems.isEmpty()) {
            return new ArrayList<>();
        }
        return processAndMergeData(cachedItems, clientId);
    }

    @Transactional(readOnly = true)
    public Page<ElvaItemResponse> searchElvaItems(ElvaItemSearchRequest searchRequest, Pageable pageable) {
        // 1. Recherche rapide dans le cache PostgreSQL
        Specification<ElvaItemCache> spec = createSearchSpecification(searchRequest);
        Page<ElvaItemCache> cachedPage = elvaItemCacheRepository.findAll(spec, pageable);

        if (cachedPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<String> itemNos = cachedPage.getContent().stream()
                .map(ElvaItemCache::getNo)
                .collect(Collectors.toList());

        // 2. Lancement des appels SQL Server en parallèle (via @Async)
        CompletableFuture<Map<String, ElvaItemRepository.ElvaItemRealTimeProjection>> realTimeDataFuture = fetchRealTimeDataAsync(itemNos);
        CompletableFuture<Map<String, ElvaSalesPrice>> specificPricesFuture = fetchSpecificPricesAsync(itemNos, searchRequest.getClientId());

        // 3. Attente de la fin des deux appels (Temps total = le temps de l'appel le plus long)
        CompletableFuture.allOf(realTimeDataFuture, specificPricesFuture).join();

        Map<String, ElvaItemRepository.ElvaItemRealTimeProjection> realTimeDataMap = realTimeDataFuture.join();
        Map<String, ElvaSalesPrice> specificPricesMap = specificPricesFuture.join();

        // 4. Fusion des données et création de la réponse
        return cachedPage.map(cacheItem -> {
            ElvaItemRepository.ElvaItemRealTimeProjection realTimeData = realTimeDataMap.get(cacheItem.getNo());
            ElvaSalesPrice specificPrice = specificPricesMap.get(cacheItem.getNo());
            return mapToDto(cacheItem, realTimeData, specificPrice);
        });
    }

    @Transactional(readOnly = true)
    public List<ElvaItemResponse> getEquivalences(String no, String clientId, String referenceOrigineLie) {
        if (!StringUtils.hasText(referenceOrigineLie)) {
            return new ArrayList<>();
        }

        List<ElvaItemCache> cachedEquivalences = elvaItemCacheRepository.findByReferenceOrigineLieAndNoNot(referenceOrigineLie, no);
        if (cachedEquivalences.isEmpty()) {
            return new ArrayList<>();
        }

        List<ElvaItemResponse> result = processAndMergeData(cachedEquivalences, clientId);

        // Tri : Qte DESC, Reception DESC, UnitPrice DESC
        result.sort((a, b) -> {
            java.math.BigDecimal q1 = a.getQuantite() != null ? a.getQuantite() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal q2 = b.getQuantite() != null ? b.getQuantite() : java.math.BigDecimal.ZERO;
            int cmp = q2.compareTo(q1);
            if (cmp != 0) return cmp;

            java.math.BigDecimal r1 = a.getReceptionQty() != null ? a.getReceptionQty() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal r2 = b.getReceptionQty() != null ? b.getReceptionQty() : java.math.BigDecimal.ZERO;
            int cmpRec = r2.compareTo(r1);
            if (cmpRec != 0) return cmpRec;

            java.math.BigDecimal u1 = a.getUnitPrice() != null ? a.getUnitPrice() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal u2 = b.getUnitPrice() != null ? b.getUnitPrice() : java.math.BigDecimal.ZERO;
            return u2.compareTo(u1);
        });

        return result;
    }

    @Transactional(readOnly = true)
    public List<ElvaItemResponse> getKitItems(String no, String clientId) {
        if (!StringUtils.hasText(no)) {
            return new ArrayList<>();
        }

        List<ElvaItemKitResponse> kitLines = elvaItemKitService.getByArticle(no.trim());
        if (kitLines.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> itemNosSet = new LinkedHashSet<>();
        String inputNo = no.trim();
        for (ElvaItemKitResponse line : kitLines) {
            if (line.getArticle() != null && !line.getArticle().trim().equalsIgnoreCase(inputNo)) {
                itemNosSet.add(line.getArticle().trim());
            }
            if (line.getItemKit() != null && !line.getItemKit().trim().equalsIgnoreCase(inputNo)) {
                itemNosSet.add(line.getItemKit().trim());
            }
        }

        if (itemNosSet.isEmpty()) {
            return new ArrayList<>();
        }

        List<ElvaItemCache> cachedItems = elvaItemCacheRepository.findAllById(new ArrayList<>(itemNosSet));
        if (cachedItems.isEmpty()) {
            return new ArrayList<>();
        }

        List<ElvaItemResponse> result = processAndMergeData(cachedItems, clientId);

        // Tri : Qte DESC, Reception DESC, UnitPrice DESC
        result.sort((a, b) -> {
            java.math.BigDecimal q1 = a.getQuantite() != null ? a.getQuantite() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal q2 = b.getQuantite() != null ? b.getQuantite() : java.math.BigDecimal.ZERO;
            int cmp = q2.compareTo(q1);
            if (cmp != 0) return cmp;

            java.math.BigDecimal r1 = a.getReceptionQty() != null ? a.getReceptionQty() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal r2 = b.getReceptionQty() != null ? b.getReceptionQty() : java.math.BigDecimal.ZERO;
            int cmpRec = r2.compareTo(r1);
            if (cmpRec != 0) return cmpRec;

            java.math.BigDecimal u1 = a.getUnitPrice() != null ? a.getUnitPrice() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal u2 = b.getUnitPrice() != null ? b.getUnitPrice() : java.math.BigDecimal.ZERO;
            return u2.compareTo(u1);
        });

        return result;
    }

    private List<ElvaItemResponse> processAndMergeData(List<ElvaItemCache> cachedItems, String clientId) {
        List<String> itemNos = cachedItems.stream().map(ElvaItemCache::getNo).collect(Collectors.toList());

        // Lancement en parallèle des deux appels lourds vers SQL Server
        CompletableFuture<Map<String, ElvaItemRepository.ElvaItemRealTimeProjection>> realTimeDataFuture = fetchRealTimeDataAsync(itemNos);
        CompletableFuture<Map<String, ElvaSalesPrice>> specificPricesFuture = fetchSpecificPricesAsync(itemNos, clientId);

        CompletableFuture.allOf(realTimeDataFuture, specificPricesFuture).join();

        Map<String, ElvaItemRepository.ElvaItemRealTimeProjection> realTimeDataMap = realTimeDataFuture.join();
        Map<String, ElvaSalesPrice> specificPricesMap = specificPricesFuture.join();

        return cachedItems.stream()
                .map(cacheItem -> {
                    ElvaItemRepository.ElvaItemRealTimeProjection realTimeData = realTimeDataMap.get(cacheItem.getNo());
                    ElvaSalesPrice specificPrice = specificPricesMap.get(cacheItem.getNo());
                    return mapToDto(cacheItem, realTimeData, specificPrice);
                })
                .collect(Collectors.toList());
    }

    @Async
    public CompletableFuture<Map<String, ElvaItemRepository.ElvaItemRealTimeProjection>> fetchRealTimeDataAsync(List<String> itemNos) {
        if (itemNos.isEmpty()) return CompletableFuture.completedFuture(Map.of());
        
        // On récupère uniquement la projection (très léger en réseau et en mémoire)
        List<ElvaItemRepository.ElvaItemRealTimeProjection> realTimeData = elvaItemRepository.findRealTimeDataByNos(itemNos);
        
        return CompletableFuture.completedFuture(
                realTimeData.stream().collect(Collectors.toMap(ElvaItemRepository.ElvaItemRealTimeProjection::getNo, item -> item, (i1, i2) -> i1))
        );
    }

    @Async
    public CompletableFuture<Map<String, ElvaSalesPrice>> fetchSpecificPricesAsync(List<String> itemNos, String clientId) {
        if (itemNos.isEmpty() || !StringUtils.hasText(clientId)) return CompletableFuture.completedFuture(Map.of());
        
        // On récupère uniquement le prix actif (un seul par article)
        List<ElvaSalesPrice> specificPrices = elvaSalesPriceRepository.findActivePricesForClientAndItems(itemNos, clientId, LocalDateTime.now());
        
        return CompletableFuture.completedFuture(
                specificPrices.stream().collect(Collectors.toMap(ElvaSalesPrice::getItemNo, price -> price, (p1, p2) -> p1))
        );
    }

    private Specification<ElvaItemCache> createSearchSpecification(ElvaItemSearchRequest searchRequest) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(searchRequest.getSearchTerm())) {
                String likePattern = "%" + searchRequest.getSearchTerm().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("no")), likePattern),
                        cb.like(cb.lower(root.get("description")), likePattern),
                        cb.like(cb.lower(root.get("searchDescription")), likePattern),
                        cb.like(cb.lower(root.get("description2")), likePattern),
                        cb.like(cb.lower(root.get("vendorItemNo")), likePattern),
                        cb.like(cb.lower(root.get("makeCode")), likePattern),
                        cb.like(cb.lower(root.get("fabricant")), likePattern)
                ));
            }

            if (StringUtils.hasText(searchRequest.getNo())) {
                predicates.add(cb.like(cb.lower(root.get("no")), "%" + searchRequest.getNo().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(searchRequest.getDescription())) {
                predicates.add(cb.like(cb.lower(root.get("description")), "%" + searchRequest.getDescription().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(searchRequest.getSearchDescription())) {
                predicates.add(cb.like(cb.lower(root.get("searchDescription")), "%" + searchRequest.getSearchDescription().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(searchRequest.getDescription2())) {
                predicates.add(cb.like(cb.lower(root.get("description2")), "%" + searchRequest.getDescription2().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(searchRequest.getVendorItemNo())) {
                predicates.add(cb.like(cb.lower(root.get("vendorItemNo")), "%" + searchRequest.getVendorItemNo().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(searchRequest.getMakeCode())) {
                predicates.add(cb.like(cb.lower(root.get("makeCode")), "%" + searchRequest.getMakeCode().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(searchRequest.getDescriptionStructuree())) {
                predicates.add(cb.like(cb.lower(root.get("descriptionStructuree")), "%" + searchRequest.getDescriptionStructuree().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(searchRequest.getItemProductCode())) {
                predicates.add(cb.like(cb.lower(root.get("itemProductCode")), "%" + searchRequest.getItemProductCode().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(searchRequest.getItemSubProductCode())) {
                predicates.add(cb.like(cb.lower(root.get("itemSubProductCode")), "%" + searchRequest.getItemSubProductCode().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(searchRequest.getGroupe())) {
                predicates.add(cb.like(cb.lower(root.get("groupe")), "%" + searchRequest.getGroupe().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(searchRequest.getSousGroupe())) {
                predicates.add(cb.like(cb.lower(root.get("sousGroupe")), "%" + searchRequest.getSousGroupe().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(searchRequest.getReferenceOrigineLie())) {
                predicates.add(cb.like(cb.lower(root.get("referenceOrigineLie")), "%" + searchRequest.getReferenceOrigineLie().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(searchRequest.getCodeFabricant())) {
                predicates.add(cb.like(cb.lower(root.get("codeFabricant")), "%" + searchRequest.getCodeFabricant().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(searchRequest.getFabricant())) {
                predicates.add(cb.like(cb.lower(root.get("fabricant")), "%" + searchRequest.getFabricant().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private ElvaItemResponse mapToDto(ElvaItemCache cacheItem, ElvaItemRepository.ElvaItemRealTimeProjection realTimeData, ElvaSalesPrice specificPrice) {
        ElvaItemResponse dto = new ElvaItemResponse();
        
        dto.setNo(cacheItem.getNo());
        dto.setDescription(cacheItem.getDescription());
        dto.setSearchDescription(cacheItem.getSearchDescription());
        dto.setDescription2(cacheItem.getDescription2());
        dto.setBaseUnitOfMeasure(cacheItem.getBaseUnitOfMeasure());
        dto.setType(cacheItem.getType());
        dto.setInventoryPostingGroup(cacheItem.getInventoryPostingGroup());
        dto.setUnitCost(cacheItem.getUnitCost());
        dto.setLastDirectCost(cacheItem.getLastDirectCost());
        dto.setVendorNo(cacheItem.getVendorNo());
        dto.setVendorItemNo(cacheItem.getVendorItemNo());
        dto.setBlocked(cacheItem.getBlocked());
        dto.setItemCategoryCode(cacheItem.getItemCategoryCode());
        dto.setMakeCode(cacheItem.getMakeCode());
        dto.setDescriptionStructuree(cacheItem.getDescriptionStructuree());
        dto.setItemProductCode(cacheItem.getItemProductCode());
        dto.setItemSubProductCode(cacheItem.getItemSubProductCode());
        dto.setGroupe(cacheItem.getGroupe());
        dto.setSousGroupe(cacheItem.getSousGroupe());
        dto.setReferenceOrigineLie(cacheItem.getReferenceOrigineLie());
        dto.setCodeFabricant(cacheItem.getCodeFabricant());
        dto.setFabricant(cacheItem.getFabricant());
        dto.setTecdocIdFabricant(cacheItem.getTecdocIdFabricant());
        dto.setIsOem(cacheItem.getIsOem());
        dto.setId(cacheItem.getId());
        dto.setProduit(cacheItem.getProduit());
        dto.setIsKit(cacheItem.getIsKit());
        dto.setHaveInfo(cacheItem.getHaveInfo());
        dto.setChampsLibre(cacheItem.getChampsLibre());

        if (realTimeData != null) {
            dto.setQuantite(realTimeData.getQuantite());
            dto.setReservedQuantity(realTimeData.getReservedQuantity());
            dto.setReceptionQty(realTimeData.getReceptionQty());
        }

        if (specificPrice != null && specificPrice.getUnitPrice() != null) {
            dto.setUnitPrice(specificPrice.getUnitPrice());
        } else if (realTimeData != null && realTimeData.getUnitPrice() != null) {
            dto.setUnitPrice(realTimeData.getUnitPrice());
        } else {
            dto.setUnitPrice(cacheItem.getUnitPrice());
        }

        return dto;
    }

    @Transactional
    public void syncElvaItemsFromSqlServer() {
        String sql = "SELECT No_, Description, [Search Description], [Description 2], [Base Unit of Measure], Type, " +
                "[Inventory Posting Group], [Unit Price], [Unit Cost], [Last Direct Cost], [Vendor No_], [Vendor Item No_], " +
                "Blocked, [Item Category Code], [Make Code], [Description structurée], [Item Product Code], [Item Sub Product Code], " +
                "Groupe, [Sous Groupe], [Reference Origine Lié], [code Fabricant], Fabricant, [Tecdoc id fabricant], " +
                "isOEM, Id, Produit, isKit, HaveInfo, [Champs libre] FROM ELVA_Item";

        elvaItemCacheRepository.deleteAllInBatch();

        List<ElvaItemCache> batch = new ArrayList<>();
        sqlServerJdbcTemplate.query(sql, rs -> {
            ElvaItemCache item = new ElvaItemCache();
            item.setNo(rs.getString("No_"));
            item.setDescription(rs.getString("Description"));
            item.setSearchDescription(rs.getString("Search Description"));
            item.setDescription2(rs.getString("Description 2"));
            item.setBaseUnitOfMeasure(rs.getString("Base Unit of Measure"));

            int typeVal = rs.getInt("Type");
            item.setType(rs.wasNull() ? null : typeVal);

            item.setInventoryPostingGroup(rs.getString("Inventory Posting Group"));
            item.setUnitPrice(rs.getBigDecimal("Unit Price"));
            item.setUnitCost(rs.getBigDecimal("Unit Cost"));
            item.setLastDirectCost(rs.getBigDecimal("Last Direct Cost"));
            item.setVendorNo(rs.getString("Vendor No_"));
            item.setVendorItemNo(rs.getString("Vendor Item No_"));

            int blockedVal = rs.getInt("Blocked");
            item.setBlocked(rs.wasNull() ? null : blockedVal);

            item.setItemCategoryCode(rs.getString("Item Category Code"));
            item.setMakeCode(rs.getString("Make Code"));
            item.setDescriptionStructuree(rs.getString("Description structurée"));
            item.setItemProductCode(rs.getString("Item Product Code"));
            item.setItemSubProductCode(rs.getString("Item Sub Product Code"));
            item.setGroupe(rs.getString("Groupe"));
            item.setSousGroupe(rs.getString("Sous Groupe"));
            item.setReferenceOrigineLie(rs.getString("Reference Origine Lié"));
            item.setCodeFabricant(rs.getString("code Fabricant"));
            item.setFabricant(rs.getString("Fabricant"));
            item.setTecdocIdFabricant(rs.getString("Tecdoc id fabricant"));
            item.setIsOem(rs.getString("isOEM"));
            item.setId(rs.getString("Id"));
            item.setProduit(rs.getString("Produit"));
            item.setIsKit(rs.getString("isKit"));
            item.setHaveInfo(rs.getString("HaveInfo"));
            item.setChampsLibre(rs.getString("Champs libre"));

            batch.add(item);
            if (batch.size() >= 1000) {
                elvaItemCacheRepository.saveAll(batch);
                batch.clear();
            }
        });

        if (!batch.isEmpty()) {
            elvaItemCacheRepository.saveAll(batch);
        }
    }

    // Synchronisation horaire : vide et recharge elva_item_cache depuis SQL Server.
    // Première exécution : 5 minutes après le démarrage du serveur (initialDelayMs).
    @Scheduled(fixedRate = 900000, initialDelay = 300000)
    public void scheduledSync() {
        log.info("Démarrage de la synchronisation horaire des articles ElvaItem...");
        syncElvaItemsFromSqlServer();
        log.info("Synchronisation horaire des articles ElvaItem terminée.");
    }
}
