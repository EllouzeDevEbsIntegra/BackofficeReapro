package com.reapro.achat.services;

import com.reapro.achat.DTO.ElvaItemResponse;
import com.reapro.achat.DTO.ElvaItemSearchRequest;
import com.reapro.achat.entities.primary.ElvaItemCache;
import com.reapro.achat.entities.sqlserver.ElvaItem;
import com.reapro.achat.repositories.primary.ElvaItemCacheRepository;
import com.reapro.achat.repositories.sqlserver.ElvaItemRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ElvaItemService {

    private final ElvaItemRepository elvaItemRepository;
    private final ElvaItemCacheRepository elvaItemCacheRepository;
    private final JdbcTemplate jdbcTemplate;

    @Qualifier("sqlServerJdbcTemplate")
    private final NamedParameterJdbcTemplate sqlServerJdbcTemplate;

    @Transactional(readOnly = true)
    public List<ElvaItemResponse> getTop1000ElvaItems() {
        List<ElvaItemCache> cachedItems = elvaItemCacheRepository.findAll(Pageable.ofSize(1000)).getContent();
        if (cachedItems.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> itemNos = cachedItems.stream()
                .map(ElvaItemCache::getNo)
                .collect(Collectors.toList());

        List<ElvaItem> realTimeItems = elvaItemRepository.findAllById(itemNos);
        Map<String, ElvaItem> realTimeMap = realTimeItems.stream()
                .collect(Collectors.toMap(ElvaItem::getNo, item -> item, (i1, i2) -> i1));

        return cachedItems.stream()
                .map(cacheItem -> {
                    ElvaItemResponse dto = mapCacheToDto(cacheItem);
                    ElvaItem realTime = realTimeMap.get(cacheItem.getNo());
                    if (realTime != null) {
                        dto.setUnitPrice(realTime.getUnitPrice());
                        dto.setQuantite(realTime.getQuantite());
                        dto.setReservedQuantity(realTime.getReservedQuantity());
                        dto.setReceptionQty(realTime.getReceptionQty());
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ElvaItemResponse> searchElvaItems(ElvaItemSearchRequest searchRequest, Pageable pageable) {
        Specification<ElvaItemCache> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Generic search term (OR across No_ and Search Description only)
            if (StringUtils.hasText(searchRequest.getSearchTerm())) {
                String searchTerm = searchRequest.getSearchTerm().trim();
                String likePattern = "%" + searchTerm + "%";
                predicates.add(cb.or(
                        cb.like(root.get("no"), likePattern),
                        cb.like(root.get("searchDescription"), likePattern)
                ));
            } else {
                // Specific field searches (OR conditions as requested, without LOWER for speed)
                List<Predicate> specificFieldPredicates = new ArrayList<>();

                if (StringUtils.hasText(searchRequest.getNo())) {
                    specificFieldPredicates.add(cb.like(root.get("no"), "%" + searchRequest.getNo() + "%"));
                }
                if (StringUtils.hasText(searchRequest.getDescription())) {
                    specificFieldPredicates.add(cb.like(root.get("description"), "%" + searchRequest.getDescription() + "%"));
                }
                if (StringUtils.hasText(searchRequest.getSearchDescription())) {
                    specificFieldPredicates.add(cb.like(root.get("searchDescription"), "%" + searchRequest.getSearchDescription() + "%"));
                }
                if (StringUtils.hasText(searchRequest.getDescription2())) {
                    specificFieldPredicates.add(cb.like(root.get("description2"), "%" + searchRequest.getDescription2() + "%"));
                }
                if (StringUtils.hasText(searchRequest.getVendorItemNo())) {
                    specificFieldPredicates.add(cb.like(root.get("vendorItemNo"), "%" + searchRequest.getVendorItemNo() + "%"));
                }
                if (StringUtils.hasText(searchRequest.getMakeCode())) {
                    specificFieldPredicates.add(cb.like(root.get("makeCode"), "%" + searchRequest.getMakeCode() + "%"));
                }
                if (StringUtils.hasText(searchRequest.getDescriptionStructuree())) {
                    specificFieldPredicates.add(cb.like(root.get("descriptionStructuree"), "%" + searchRequest.getDescriptionStructuree() + "%"));
                }
                if (StringUtils.hasText(searchRequest.getGroupe())) {
                    specificFieldPredicates.add(cb.like(root.get("groupe"), "%" + searchRequest.getGroupe() + "%"));
                }
                if (StringUtils.hasText(searchRequest.getSousGroupe())) {
                    specificFieldPredicates.add(cb.like(root.get("sousGroupe"), "%" + searchRequest.getSousGroupe() + "%"));
                }
                if (StringUtils.hasText(searchRequest.getReferenceOrigineLie())) {
                    specificFieldPredicates.add(cb.like(root.get("referenceOrigineLie"), "%" + searchRequest.getReferenceOrigineLie() + "%"));
                }
                if (StringUtils.hasText(searchRequest.getFabricant())) {
                    specificFieldPredicates.add(cb.like(root.get("fabricant"), "%" + searchRequest.getFabricant() + "%"));
                }

                if (!specificFieldPredicates.isEmpty()) {
                    predicates.add(cb.or(specificFieldPredicates.toArray(new Predicate[0])));
                }
            }

            // Filtres avancés appliqués en intersection (AND)
            if (StringUtils.hasText(searchRequest.getItemProductCode())) {
                predicates.add(cb.equal(root.get("itemProductCode"), searchRequest.getItemProductCode()));
            }
            if (StringUtils.hasText(searchRequest.getItemSubProductCode())) {
                predicates.add(cb.equal(root.get("itemSubProductCode"), searchRequest.getItemSubProductCode()));
            }
            if (StringUtils.hasText(searchRequest.getCodeFabricant())) {
                predicates.add(cb.equal(root.get("codeFabricant"), searchRequest.getCodeFabricant()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ElvaItemCache> cachedPage = elvaItemCacheRepository.findAll(spec, pageable);

        if (cachedPage.isEmpty()) {
            return cachedPage.map(this::mapCacheToDto);
        }

        List<String> itemNos = cachedPage.getContent().stream()
                .map(ElvaItemCache::getNo)
                .collect(Collectors.toList());

        List<ElvaItem> realTimeItems = elvaItemRepository.findAllById(itemNos);
        Map<String, ElvaItem> realTimeMap = realTimeItems.stream()
                .collect(Collectors.toMap(ElvaItem::getNo, item -> item, (i1, i2) -> i1));

        return cachedPage.map(cacheItem -> {
            ElvaItemResponse dto = mapCacheToDto(cacheItem);
            ElvaItem realTime = realTimeMap.get(cacheItem.getNo());
            if (realTime != null) {
                dto.setUnitPrice(realTime.getUnitPrice());
                dto.setQuantite(realTime.getQuantite());
                dto.setReservedQuantity(realTime.getReservedQuantity());
                dto.setReceptionQty(realTime.getReceptionQty());
            }
            return dto;
        });
    }

    /**
     * Synchronise les articles de SQL Server vers PostgreSQL par lots.
     * Cette méthode exclut les colonnes de stock en temps réel de la vue pour s'exécuter en quelques secondes.
     */
    @Transactional
    public void syncElvaItemsFromSqlServer() {
        String sql = "SELECT No_, Description, [Search Description], [Description 2], [Base Unit of Measure], Type, " +
                "[Inventory Posting Group], [Unit Price], [Unit Cost], [Last Direct Cost], [Vendor No_], [Vendor Item No_], " +
                "Blocked, [Item Category Code], [Make Code], [Description structurée], [Item Product Code], [Item Sub Product Code], " +
                "Groupe, [Sous Groupe], [Reference Origine Lié], [code Fabricant], Fabricant, [Tecdoc id fabricant], " +
                "isOEM, Id, Produit, isKit, HaveInfo, [Champs libre] FROM ELVA_Item";

        // Vider la table locale avant d'insérer
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

    /**
     * Synchronisation planifiée tous les jours à 2h du matin.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledSync() {
        syncElvaItemsFromSqlServer();
    }

    @Transactional(readOnly = true)
    public List<ElvaItemResponse> getEquivalences(String no, String referenceOrigineLie) {
        if (!org.springframework.util.StringUtils.hasText(referenceOrigineLie)) {
            return new ArrayList<>();
        }

        // 1. Fetch equivalent items from the PostgreSQL cache
        List<ElvaItemCache> cachedEquivalences = elvaItemCacheRepository.findByReferenceOrigineLieAndNoNot(referenceOrigineLie, no);
        if (cachedEquivalences.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. Fetch real-time stocks and prices from SQL Server for these items
        List<String> itemNos = cachedEquivalences.stream()
                .map(ElvaItemCache::getNo)
                .collect(Collectors.toList());

        List<ElvaItem> realTimeItems = elvaItemRepository.findAllById(itemNos);
        Map<String, ElvaItem> realTimeMap = realTimeItems.stream()
                .collect(Collectors.toMap(ElvaItem::getNo, item -> item, (i1, i2) -> i1));

        // 3. Map and merge
        List<ElvaItemResponse> result = cachedEquivalences.stream()
                .map(cacheItem -> {
                    ElvaItemResponse dto = mapCacheToDto(cacheItem);
                    ElvaItem realTime = realTimeMap.get(cacheItem.getNo());
                    if (realTime != null) {
                        dto.setUnitPrice(realTime.getUnitPrice());
                        dto.setQuantite(realTime.getQuantite());
                        dto.setReservedQuantity(realTime.getReservedQuantity());
                        dto.setReceptionQty(realTime.getReceptionQty());
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        // Sort by Qte DESC, then Reception DESC, then UnitPrice DESC (décroissant)
        result.sort((a, b) -> {
            java.math.BigDecimal q1 = a.getQuantite() != null ? a.getQuantite() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal q2 = b.getQuantite() != null ? b.getQuantite() : java.math.BigDecimal.ZERO;
            int cmp = q2.compareTo(q1); // DESC
            if (cmp != 0) {
                return cmp;
            }
            java.math.BigDecimal r1 = a.getReceptionQty() != null ? a.getReceptionQty() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal r2 = b.getReceptionQty() != null ? b.getReceptionQty() : java.math.BigDecimal.ZERO;
            int cmpRec = r2.compareTo(r1); // DESC
            if (cmpRec != 0) {
                return cmpRec;
            }
            java.math.BigDecimal u1 = a.getUnitPrice() != null ? a.getUnitPrice() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal u2 = b.getUnitPrice() != null ? b.getUnitPrice() : java.math.BigDecimal.ZERO;
            return u2.compareTo(u1); // DESC
        });

        return result;
    }

    private ElvaItemResponse mapCacheToDto(ElvaItemCache cacheItem) {
        ElvaItemResponse dto = new ElvaItemResponse();
        dto.setNo(cacheItem.getNo());
        dto.setDescription(cacheItem.getDescription());
        dto.setSearchDescription(cacheItem.getSearchDescription());
        dto.setDescription2(cacheItem.getDescription2());
        dto.setBaseUnitOfMeasure(cacheItem.getBaseUnitOfMeasure());
        dto.setType(cacheItem.getType());
        dto.setInventoryPostingGroup(cacheItem.getInventoryPostingGroup());
        dto.setUnitPrice(cacheItem.getUnitPrice());
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
        dto.setQuantite(null);
        dto.setReservedQuantity(null);
        dto.setReceptionQty(null);
        dto.setId(cacheItem.getId());
        dto.setProduit(cacheItem.getProduit());
        dto.setIsKit(cacheItem.getIsKit());
        dto.setHaveInfo(cacheItem.getHaveInfo());
        dto.setChampsLibre(cacheItem.getChampsLibre());
        return dto;
    }
}
