package com.reapro.achat.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.Duration;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reapro.achat.DTO.bc.BcManufacturer;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.entities.primary.SyncAdaptableItem;
import com.reapro.achat.entities.sqlserver.ElvaItem;
import com.reapro.achat.repositories.primary.AdminRepository;
import com.reapro.achat.repositories.primary.SyncAdaptableItemRepository;
import com.reapro.achat.repositories.sqlserver.ElvaItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.persistence.criteria.Predicate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class ReportErpSyncService {

    private final WebClient webClient;
    private final ElvaItemRepository elvaItemRepository;
    private final AdminRepository adminRepository;
    private final BcManufacturerService manufacturerService;
    private final ObjectMapper objectMapper;
    private final SyncAdaptableItemRepository syncAdaptableItemRepository;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate sqlServerJdbcTemplate;

    private final AtomicBoolean isSyncing = new AtomicBoolean(false);

    @Value("${parts-mgr.api.url}")
    private String apiUrl;

    @Value("${parts-mgr.api.key}")
    private String apiKey;

    public ReportErpSyncService(WebClient.Builder webClientBuilder,
                                ElvaItemRepository elvaItemRepository,
                                AdminRepository adminRepository,
                                BcManufacturerService manufacturerService,
                                ObjectMapper objectMapper,
                                SyncAdaptableItemRepository syncAdaptableItemRepository,
                                JdbcTemplate jdbcTemplate,
                                @Qualifier("sqlServerJdbcTemplate") NamedParameterJdbcTemplate sqlServerJdbcTemplate) {
        this.webClient = webClientBuilder.build();
        this.elvaItemRepository = elvaItemRepository;
        this.adminRepository = adminRepository;
        this.manufacturerService = manufacturerService;
        this.objectMapper = objectMapper;
        this.syncAdaptableItemRepository = syncAdaptableItemRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.sqlServerJdbcTemplate = sqlServerJdbcTemplate;
    }

    public boolean isSyncing() {
        return isSyncing.get();
    }

    /**
     * Démarrage asynchrone de la synchronisation (déclenché par l'API)
     */
    public void startSync() {
        CompletableFuture.runAsync(() -> {
            try {
                syncData();
            } catch (Exception e) {
                log.error("Erreur durant la synchronisation manuelle des articles adaptables", e);
            }
        });
    }

    /**
     * Synchronisation planifiée toutes les 24 heures (ex: à 2h00 du matin)
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledSync() {
        log.info("Début de la synchronisation planifiée des articles adaptables...");
        try {
            syncData();
            log.info("Synchronisation planifiée des articles adaptables terminée avec succès.");
        } catch (Exception e) {
            log.error("Erreur durant la synchronisation planifiée des articles adaptables", e);
        }
    }

    /**
     * Algorithme principal de synchronisation
     */
    public void syncData() {
        if (!isSyncing.compareAndSet(false, true)) {
            log.warn("Synchronisation déjà en cours. Requête ignorée.");
            throw new IllegalStateException("La synchronisation est déjà en cours.");
        }

        try {
            log.info("Démarrage de la synchronisation des articles adaptables...");

            // 1. Chargement des libellés de groupes et sous-groupes depuis SQL Server en mémoire
            log.info("Chargement des catégories depuis SQL Server...");
            String groupSql = "SELECT DISTINCT [Item Product Code] as code, [Groupe] as name FROM ELVA_Item WHERE [Item Product Code] IS NOT NULL";
            Map<String, String> groupNamesMap = new HashMap<>();
            sqlServerJdbcTemplate.getJdbcOperations().query(groupSql, rs -> {
                String code = rs.getString("code");
                String name = rs.getString("name");
                if (code != null && name != null) {
                    groupNamesMap.put(code.trim().toUpperCase(), name.trim());
                }
            });

            String subgroupSql = "SELECT DISTINCT [Item Sub Product Code] as code, [Sous Groupe] as name FROM ELVA_Item WHERE [Item Sub Product Code] IS NOT NULL";
            Map<String, String> subgroupNamesMap = new HashMap<>();
            sqlServerJdbcTemplate.getJdbcOperations().query(subgroupSql, rs -> {
                String code = rs.getString("code");
                String name = rs.getString("name");
                if (code != null && name != null) {
                    subgroupNamesMap.put(code.trim().toUpperCase(), name.trim());
                }
            });

            // 2. Chargement des champs libres depuis SQL Server en mémoire
            log.info("Chargement des champs libres depuis SQL Server...");
            String champsLibreSql = "SELECT No_ as no, [Champs libre] as val FROM ELVA_Item WHERE [Champs libre] IS NOT NULL";
            Map<String, String> champsLibreMap = new HashMap<>();
            sqlServerJdbcTemplate.getJdbcOperations().query(champsLibreSql, rs -> {
                String no = rs.getString("no");
                String val = rs.getString("val");
                if (no != null && val != null) {
                    champsLibreMap.put(no.trim().toUpperCase(), val.trim());
                }
            });

            // 3. Vider la table locale PostgreSQL
            log.info("Vidage de la table locale PostgreSQL...");
            jdbcTemplate.execute("TRUNCATE TABLE sync_adaptable_item");

            // 4. Lecture paginée depuis l'API externe et insertion par lots dans PostgreSQL
            int page = 1;
            int pageSize = 10000;
            boolean hasMore = true;
            int totalInserted = 0;

            while (hasMore) {
                log.info("Récupération de la page {} depuis l'API externe (taille: {})...", page, pageSize);
                String url = apiUrl + "?page=" + page + "&pageSize=" + pageSize;

                String responseJson = webClient.get()
                        .uri(url)
                        .headers(headers -> {
                            if (StringUtils.hasText(apiKey)) {
                                headers.add("X-API-KEY", apiKey);
                            }
                        })
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(120))
                        .block();

                if (responseJson == null || responseJson.isBlank()) {
                    log.warn("Réponse vide de l'API externe à la page {}. Fin de la synchronisation.", page);
                    break;
                }

                JsonNode rootNode = objectMapper.readTree(responseJson);
                if (!rootNode.has("list") || !rootNode.get("list").isArray()) {
                    log.warn("Format JSON invalide ou liste absente à la page {}.", page);
                    break;
                }

                ArrayNode listNode = (ArrayNode) rootNode.get("list");
                if (listNode.isEmpty()) {
                    log.info("Plus de données retournées à la page {}.", page);
                    break;
                }

                // Insertion par lot dans PostgreSQL
                saveBatchToPostgres(listNode, groupNamesMap, subgroupNamesMap, champsLibreMap);
                totalInserted += listNode.size();
                log.info("Batch inséré : {} éléments (total inséré: {})", listNode.size(), totalInserted);

                if (listNode.size() < pageSize) {
                    hasMore = false;
                } else {
                    page++;
                }
            }

            log.info("Synchronisation terminée avec succès. Total inséré : {} articles.", totalInserted);

        } catch (Exception e) {
            log.error("Erreur critique durant la synchronisation des articles adaptables", e);
            throw new RuntimeException("Échec de la synchronisation : " + e.getMessage(), e);
        } finally {
            isSyncing.set(false);
        }
    }

    private void saveBatchToPostgres(ArrayNode listNode,
                                     Map<String, String> groupNamesMap,
                                     Map<String, String> subgroupNamesMap,
                                     Map<String, String> champsLibreMap) {
        String insertSql = "INSERT INTO sync_adaptable_item (" +
                "ext_id, td_ref, td_brand_id, td_brand_name, td_description, " +
                "oem, description, master, part_make_code, " +
                "part_group_code, part_group_name, part_subgroup_code, part_subgroup_name, " +
                "champs_libre" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        List<Object[]> batchArgs = new ArrayList<>();
        for (JsonNode node : listNode) {
            Long extId = node.has("id") && !node.get("id").isNull() ? node.get("id").asLong() : null;
            String tdRef = node.has("tdRef") && !node.get("tdRef").isNull() ? node.get("tdRef").asText() : null;
            Integer tdBrandId = node.has("tdBrandId") && !node.get("tdBrandId").isNull() ? node.get("tdBrandId").asInt() : null;
            String tdBrandName = node.has("tdBrandName") && !node.get("tdBrandName").isNull() ? node.get("tdBrandName").asText() : null;
            String tdDescription = node.has("tdDescription") && !node.get("tdDescription").isNull() ? node.get("tdDescription").asText() : null;
            String oem = node.has("oem") && !node.get("oem").isNull() ? node.get("oem").asText() : null;
            String description = node.has("description") && !node.get("description").isNull() ? node.get("description").asText() : null;
            String master = node.has("master") && !node.get("master").isNull() ? node.get("master").asText() : null;
            String partMakeCode = node.has("partMakeCode") && !node.get("partMakeCode").isNull() ? node.get("partMakeCode").asText() : null;

            String partGroupCode = node.has("partGroup") && !node.get("partGroup").isNull() ? node.get("partGroup").asText() : null;
            String partGroupName = null;
            if (partGroupCode != null) {
                partGroupCode = partGroupCode.trim();
                partGroupName = groupNamesMap.get(partGroupCode.toUpperCase());
            }

            String partSubgroupCode = node.has("partSubGroup") && !node.get("partSubGroup").isNull() ? node.get("partSubGroup").asText() : null;
            String partSubgroupName = null;
            if (partSubgroupCode != null) {
                partSubgroupCode = partSubgroupCode.trim();
                partSubgroupName = subgroupNamesMap.get(partSubgroupCode.toUpperCase());
            }

            String champsLibre = null;
            if (master != null) {
                champsLibre = champsLibreMap.get(master.trim().toUpperCase());
            }

            batchArgs.add(new Object[]{
                    extId, tdRef, tdBrandId, tdBrandName, tdDescription,
                    oem, description, master, partMakeCode,
                    partGroupCode, partGroupName, partSubgroupCode, partSubgroupName,
                    champsLibre
            });
        }

        jdbcTemplate.batchUpdate(insertSql, batchArgs);
    }

    /**
     * Recherche locale paginée avec filtres et enrichissement dynamique du vendorNo (Code Fournisseur)
     */
    public Mono<String> getLocalSyncData(int page, int pageSize, String tdBrandName, String partGroup, String partSubGroup, String master, String email) {
        return Mono.fromCallable(() -> {
            try {
                // 1. Construction des filtres JPA
                Specification<SyncAdaptableItem> spec = (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(
                            cb.or(
                                    cb.isNull(root.get("oem")),
                                    cb.notLike(cb.upper(root.get("oem")), "MASTER%")
                            )
                    );
                    if (tdBrandName != null && !tdBrandName.isBlank()) {
                        predicates.add(cb.equal(root.get("tdBrandName"), tdBrandName));
                    }
                    if (partGroup != null && !partGroup.isBlank()) {
                        predicates.add(cb.equal(root.get("partGroupCode"), partGroup));
                    }
                    if (partSubGroup != null && !partSubGroup.isBlank()) {
                        predicates.add(cb.equal(root.get("partSubgroupCode"), partSubGroup));
                    }
                    if (master != null && !master.isBlank()) {
                        predicates.add(cb.like(cb.lower(root.get("master")), "%" + master.trim().toLowerCase() + "%"));
                    }
                    return cb.and(predicates.toArray(new Predicate[0]));
                };

                // 2. Exécution de la requête JPA avec pagination (page est 1-indexed pour le front, 0-indexed pour JPA)
                Page<SyncAdaptableItem> itemPage = syncAdaptableItemRepository.findAll(spec, PageRequest.of(page - 1, pageSize));

                // 3. Récupération des fournisseurs par défaut depuis BC si authentifié
                Map<String, String> vendorNoMap = new HashMap<>();
                if (email != null && !email.isBlank()) {
                    try {
                        Optional<Admin> adminOpt = adminRepository.findByEmail(email);
                        if (adminOpt.isPresent()) {
                            String companyId = adminOpt.get().getBcCompanyId();
                            if (companyId != null && !companyId.isBlank()) {
                                List<BcManufacturer> bcManufacturers = manufacturerService.getManufacturers(companyId);
                                for (BcManufacturer m : bcManufacturers) {
                                    if (m.getIdTechDoc() != null && !m.getIdTechDoc().isBlank() && m.getVendorNo() != null) {
                                        vendorNoMap.put(m.getIdTechDoc().trim(), m.getVendorNo());
                                    }
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.error("Erreur lors de la récupération des fournisseurs BC pour enrichissement", ex);
                    }
                }

                // 4. Construction de la réponse JSON compatible avec l'interface frontend existante
                ObjectNode responseNode = objectMapper.createObjectNode();
                responseNode.put("total", itemPage.getTotalElements());
                responseNode.put("page", page);
                responseNode.put("pageSize", pageSize);
                responseNode.put("totalArticles", syncAdaptableItemRepository.countExcludingMasterOem());
                responseNode.put("totalMasters", syncAdaptableItemRepository.countDistinctMaster());
                responseNode.put("totalSubGroups", syncAdaptableItemRepository.countDistinctSubGroup());
                responseNode.put("totalBrands", syncAdaptableItemRepository.countDistinctBrand());
                responseNode.put("totalGroups", syncAdaptableItemRepository.countDistinctGroup());

                ArrayNode listNode = responseNode.putArray("list");
                for (SyncAdaptableItem item : itemPage.getContent()) {
                    ObjectNode itemNode = objectMapper.createObjectNode();
                    itemNode.put("id", item.getExtId());
                    itemNode.put("tdRef", item.getTdRef());
                    itemNode.put("tdBrandId", item.getTdBrandId());
                    itemNode.put("tdBrandName", item.getTdBrandName());
                    itemNode.put("tdDescription", item.getTdDescription());
                    itemNode.put("oem", item.getOem());
                    itemNode.put("description", item.getDescription());
                    itemNode.put("master", item.getMaster());
                    itemNode.put("partMakeCode", item.getPartMakeCode());
                    itemNode.put("partGroup", item.getPartGroupCode());
                    itemNode.put("partGroupName", item.getPartGroupName());
                    itemNode.put("partSubGroup", item.getPartSubgroupCode());
                    itemNode.put("partSubGroupName", item.getPartSubgroupName());
                    itemNode.put("freeField", item.getChampsLibre() != null ? item.getChampsLibre() : "");

                    // Alimentation du Code Fournisseur (navFrs) par défaut
                    String brandIdStr = item.getTdBrandId() != null ? String.valueOf(item.getTdBrandId()) : null;
                    if (brandIdStr != null && vendorNoMap.containsKey(brandIdStr.trim())) {
                        itemNode.put("navFrs", vendorNoMap.get(brandIdStr.trim()));
                    } else {
                        itemNode.put("navFrs", "");
                    }

                    listNode.add(itemNode);
                }

                List<SyncAdaptableItemRepository.BrandProjection> distinctBrands = syncAdaptableItemRepository.findDistinctBrands();
                List<SyncAdaptableItemRepository.CodeLabelProjection> distinctGroups = syncAdaptableItemRepository.findDistinctGroups();
                List<SyncAdaptableItemRepository.SubGroupProjection> distinctSubGroups = syncAdaptableItemRepository.findDistinctSubGroups();

                ArrayNode brandsNode = responseNode.putArray("filterBrands");
                for (SyncAdaptableItemRepository.BrandProjection b : distinctBrands) {
                    ObjectNode bNode = objectMapper.createObjectNode();
                    String bCode = b.getCode() != null ? String.valueOf(b.getCode()) : "";
                    bNode.put("code", bCode);
                    bNode.put("label", b.getLabel() != null ? b.getLabel() : bCode);
                    bNode.put("displayName", bCode + " — " + (b.getLabel() != null ? b.getLabel() : bCode));
                    brandsNode.add(bNode);
                }

                ArrayNode groupsNode = responseNode.putArray("filterGroups");
                for (SyncAdaptableItemRepository.CodeLabelProjection g : distinctGroups) {
                    ObjectNode gNode = objectMapper.createObjectNode();
                    gNode.put("code", g.getCode());
                    gNode.put("label", g.getLabel() != null ? g.getLabel() : g.getCode());
                    gNode.put("displayName", g.getCode() + " — " + (g.getLabel() != null ? g.getLabel() : g.getCode()));
                    groupsNode.add(gNode);
                }

                ArrayNode subGroupsNode = responseNode.putArray("filterSubGroups");
                for (SyncAdaptableItemRepository.SubGroupProjection sg : distinctSubGroups) {
                    ObjectNode sgNode = objectMapper.createObjectNode();
                    sgNode.put("code", sg.getCode());
                    sgNode.put("label", sg.getLabel() != null ? sg.getLabel() : sg.getCode());
                    sgNode.put("parentCode", sg.getParentCode());
                    sgNode.put("displayName", sg.getCode() + " — " + (sg.getLabel() != null ? sg.getLabel() : sg.getCode()));
                    subGroupsNode.add(sgNode);
                }

                return objectMapper.writeValueAsString(responseNode);

            } catch (Exception e) {
                log.error("Erreur durant la recherche locale ou le mapping JSON", e);
                throw new RuntimeException("Erreur de recherche locale : " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
