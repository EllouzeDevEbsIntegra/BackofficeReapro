package com.reapro.achat.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reapro.achat.DTO.tecdoc.*;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TecDocService {

    private final WebClient.Builder webClientBuilder;
    private final ParameterService parameterService;
    private final ObjectMapper objectMapper;

    /** Langue TecDoc — toujours en minuscules ("fr"), conformément à la licence Reapro. */
    private static final String LANG = "fr";
    /** Type de cible de liaison par défaut : "P" = véhicules de tourisme (Passenger). */
    private static final String DEFAULT_LINKAGE_TYPE = "P";

    public TecDocApiResponse searchArticles(String searchQuery, Integer supplierId) {
        TecDocApiResponse resp = searchInternal(searchQuery, 0, true, supplierId);
        if (resp != null && resp.getArticles() != null) {
            for (TecDocArticle article : resp.getArticles()) {
                if (supplierId != null) article.setSupplierLogoUrl(fetchSupplierLogoUrl(supplierId));
                if (article.getGenericArticles() != null && !article.getGenericArticles().isEmpty()) {
                    Long legacyId = article.getGenericArticles().get(0).getLegacyArticleId();
                    if (legacyId != null) {
                        article.setLinkedVehicles(fetchLinkedVehicles(legacyId));
                        article.setArticleParts(fetchArticleParts(legacyId));
                    }
                }
            }
        }
        return resp;
    }

    private List<TecDocArticle.VehicleManufacturer> fetchLinkedVehicles(Long legacyArticleId) {
        String url = parameterService.getValue(ParameterService.TECDOC_API_URL);
        String apiKey = parameterService.getValue(ParameterService.TECDOC_API_KEY);
        String country = parameterService.getValue(ParameterService.TECDOC_COUNTRY);
        Long providerId = Long.parseLong(parameterService.getValue(ParameterService.TECDOC_PROVIDER));

        Map<String, Object> requestBody = Map.of("getArticleLinkedAllLinkingTargetManufacturer2", Map.of(
                "articleCountry", country, "articleId", legacyArticleId, "country", country,
                "lang", LANG, "linkingTargetType", "P", "provider", providerId));

        try {
            Map<?, ?> response = webClientBuilder.build().post().uri(url).header("api-key", apiKey)
                    .bodyValue(requestBody).retrieve().bodyToMono(Map.class).block();

            if (response != null && response.get("data") instanceof Map<?, ?> data) {
                if (data.get("array") instanceof List<?> array) {
                    return array.stream().map(m -> {
                        Map<?, ?> map = (Map<?, ?>) m;
                        TecDocArticle.VehicleManufacturer vm = new TecDocArticle.VehicleManufacturer();
                        vm.setManuId(Long.valueOf(map.get("manuId").toString()));
                        vm.setManuName(String.valueOf(map.get("manuName")));
                        return vm;
                    }).collect(Collectors.toList());
                }
            }
        } catch (Exception e) { log.warn("Erreur fetchLinkedVehicles: {}", e.getMessage()); }
        return Collections.emptyList();
    }

    private List<Map<String, Object>> fetchArticleParts(Long legacyArticleId) {
        String url = parameterService.getValue(ParameterService.TECDOC_API_URL);
        String apiKey = parameterService.getValue(ParameterService.TECDOC_API_KEY);
        String country = parameterService.getValue(ParameterService.TECDOC_COUNTRY);
        Long providerId = Long.parseLong(parameterService.getValue(ParameterService.TECDOC_PROVIDER));

        Map<String, Object> requestBody = Map.of("getArticlePartList", Map.of(
                "articleCountry", country, "articleId", legacyArticleId,
                "lang", LANG, "provider", providerId));

        try {
            Map<?, ?> response = webClientBuilder.build().post().uri(url).header("api-key", apiKey)
                    .bodyValue(requestBody).retrieve().bodyToMono(Map.class).block();

            if (response != null && response.get("data") instanceof Map<?, ?> data) {
                if (data.get("array") instanceof List<?> array && !array.isEmpty()) {
                    Object firstItem = array.get(0);
                    if (firstItem instanceof Map<?, ?> itemMap) {
                        if (itemMap.get("partlistInfo") instanceof Map<?, ?> partlistInfo) {
                            if (partlistInfo.get("array") instanceof List<?> partsList) {
                                List<Map<String, Object>> result = new ArrayList<>();
                                for (Object partWrapper : partsList) {
                                    if (partWrapper instanceof Map<?, ?> wrapperMap) {
                                        if (wrapperMap.get("partlistDetails") instanceof Map<?, ?> details) {
                                            Map<String, Object> cleanPart = new HashMap<>();
                                            cleanPart.put("articleNo", details.get("articleNo"));
                                            cleanPart.put("articleName", details.get("articleName"));
                                            cleanPart.put("brandName", details.get("brandName"));
                                            cleanPart.put("quantity", details.get("quantity"));
                                            result.add(cleanPart);
                                        }
                                    }
                                }
                                return result;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) { log.warn("Erreur fetchArticleParts: {}", e.getMessage()); }
        return Collections.emptyList();
    }

    public List<TecDocVehicleDetail> getLinkedVehiclesDetails(Long articleId, Long manuId) {
        log.info("[TECDOC] Début Step 1 pour articleId: {} et manuId: {}", articleId, manuId);

        String url = parameterService.getValue(ParameterService.TECDOC_API_URL);
        String apiKey = parameterService.getValue(ParameterService.TECDOC_API_KEY);
        String country = parameterService.getValue(ParameterService.TECDOC_COUNTRY);
        Long providerId = Long.parseLong(parameterService.getValue(ParameterService.TECDOC_PROVIDER));

        Map<String, Object> step1Request = Map.of("getArticleLinkedAllLinkingTarget4", Map.of(
                "articleCountry", country, "articleId", articleId, "country", country,
                "lang", LANG, "linkingTargetManuId", manuId, "linkingTargetType", "P", "provider", providerId));

        try {
            Map<?, ?> resp1 = webClientBuilder.build().post().uri(url).header("api-key", apiKey)
                    .bodyValue(step1Request).retrieve().bodyToMono(Map.class).block();

            List<Map<String, Object>> linkages = extractLinkages(resp1);
            if (linkages.isEmpty()) return Collections.emptyList();

            log.info("[TECDOC] Step 2: {} liens trouvés. Découpage par lots.", linkages.size());

            List<TecDocVehicleDetail> allResults = new ArrayList<>();

            // On traite par lots de 10 pour éviter de saturer l'API
            for (int i = 0; i < linkages.size(); i += 10) {
                int end = Math.min(i + 10, linkages.size());
                List<Map<String, Object>> batch = linkages.subList(i, end);

                List<Map<String, Object>> pairs = batch.stream()
                        .map(l -> Map.of("articleLinkId", l.get("articleLinkId"), "linkingTargetId", l.get("linkingTargetId")))
                        .collect(Collectors.toList());

                Map<String, Object> step2Request = Map.of("getArticleLinkedAllLinkingTargetsByIds3", Map.of(
                        "articleCountry", country, "articleId", articleId, "lang", LANG,
                        "linkedArticlePairs", Map.of("array", pairs), "linkingTargetType", "P", "provider", providerId));

                Map<?, ?> resp2 = webClientBuilder.build().post().uri(url).header("api-key", apiKey)
                        .bodyValue(step2Request).retrieve().bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(15)).block();

                allResults.addAll(parseVehicleDetails(resp2));
            }

            log.info("[TECDOC] Total: {} véhicules récupérés.", allResults.size());
            return allResults;

        } catch (Exception e) {
            log.error("[TECDOC] Erreur : {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> extractLinkages(Map<?, ?> response) {
        try {
            if (response.get("data") instanceof Map<?, ?> data) {
                if (data.get("array") instanceof List<?> array && !array.isEmpty()) {
                    if (((Map<?, ?>) array.get(0)).get("articleLinkages") instanceof Map<?, ?> artLinks) {
                        return (List<Map<String, Object>>) artLinks.get("array");
                    }
                }
            }
        } catch (Exception e) { log.warn("Erreur extraction linkages"); }
        return Collections.emptyList();
    }

    private List<TecDocVehicleDetail> parseVehicleDetails(Map<?, ?> response) {
        List<TecDocVehicleDetail> results = new ArrayList<>();
        try {
            List<?> mainArray = null;
            if (response.get("data") instanceof Map<?, ?> data) {
                mainArray = (List<?>) data.get("array");
            } else if (response.get("array") instanceof List<?> array) {
                mainArray = array;
            }

            if (mainArray == null) return results;

            for (Object obj : mainArray) {
                Map<?, ?> item = (Map<?, ?>) obj;
                if (item.get("linkedVehicles") instanceof Map<?, ?> linkedVehicles) {
                    if (linkedVehicles.get("array") instanceof List<?> vArray) {
                        for (Object vObj : vArray) {
                            Map<?, ?> v = (Map<?, ?>) vObj;
                            TecDocVehicleDetail d = new TecDocVehicleDetail();
                            d.setCarId(v.get("carId") != null ? Long.valueOf(v.get("carId").toString()) : null);
                            d.setCarDesc(String.valueOf(v.get("carDesc")));
                            d.setManuDesc(String.valueOf(v.get("manuDesc")));
                            d.setModelDesc(String.valueOf(v.get("modelDesc")));
                            d.setConstructionType(String.valueOf(v.get("constructionType")));
                            d.setCylinderCapacity(safeInt(v.get("cylinderCapacity")));
                            d.setPowerHpFrom(safeInt(v.get("powerHpFrom")));
                            d.setPowerKwFrom(safeInt(v.get("powerKwFrom")));
                            d.setYearOfConstructionFrom(safeInt(v.get("yearOfConstructionFrom")));
                            d.setYearOfConstructionTo(safeInt(v.get("yearOfConstructionTo")));
                            results.add(d);
                        }
                    }
                }
            }
        } catch (Exception e) { log.error("Erreur parsing: {}", e.getMessage()); }
        return results;
    }

    private Integer safeInt(Object obj) {
        if (obj == null) return null;
        try {
            return (obj instanceof Number n) ? n.intValue() : Double.valueOf(obj.toString()).intValue();
        } catch (Exception e) { return null; }
    }

    private String fetchSupplierLogoUrl(Integer supplierId) {
        String url = parameterService.getValue(ParameterService.TECDOC_API_URL);
        String apiKey = parameterService.getValue(ParameterService.TECDOC_API_KEY);
        Map<String, Object> body = Map.of("getBrands", Map.of(
                "articleCountry", parameterService.getValue(ParameterService.TECDOC_COUNTRY),
                "provider", Long.parseLong(parameterService.getValue(ParameterService.TECDOC_PROVIDER)),
                "lang", LANG, "dataSupplierIds", supplierId, "includeDataSupplierLogo", true));
        try {
            TecDocBrandsResponse resp = webClientBuilder.build().post().uri(url).header("api-key", apiKey)
                    .bodyValue(body).retrieve().bodyToMono(TecDocBrandsResponse.class).block();
            return resp.getData().getArray().get(0).getDataSupplierLogo().getImageURL200();
        } catch (Exception e) { return null; }
    }

    public TecDocApiResponse searchArticlesType1(String searchQuery) { return searchInternal(searchQuery, 1, false, null); }

    private TecDocApiResponse searchInternal(String searchQuery, int searchType, boolean includeAll, Integer supplierId) {
        String url = parameterService.getValue(ParameterService.TECDOC_API_URL);
        String apiKey = parameterService.getValue(ParameterService.TECDOC_API_KEY);
        String country = parameterService.getValue(ParameterService.TECDOC_COUNTRY);
        Long providerId = Long.parseLong(parameterService.getValue(ParameterService.TECDOC_PROVIDER));

        Map<String, Object> params = new HashMap<>(Map.of(
                "articleCountry", country, "provider", providerId, "searchQuery", searchQuery,
                "searchType", searchType, "lang", LANG, "includeAll", includeAll));
        if (supplierId != null && supplierId > 0) params.put("dataSupplierIds", supplierId);

        try {
            return webClientBuilder.build().post().uri(url).header("api-key", apiKey)
                    .bodyValue(Map.of("getArticles", params)).retrieve().bodyToMono(TecDocApiResponse.class).block();
        } catch (Exception e) { throw new ApiException(ErrorCode.BC_API_ERROR, "Erreur TecDoc"); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CATALOGUE TECDOC (Phase 1) — autocomplete, sélection véhicule, familles, articles
    //
    // Tous les appels passent par le proxy serveur : api-key / provider / country
    // restent côté backend et ne sont JAMAIS exposés au frontend.
    // articleCountry / linkageTargetCountry = TECDOC_COUNTRY (= "TN") ; lang = "fr".
    // Aucune recherche VIN (hors licence) : aucune méthode getVehiclesByVIN ici.
    // ─────────────────────────────────────────────────────────────────────────

    /** Appel bas niveau générique au GW TecDoc ; renvoie la réponse JSON brute (sans secret). */
    private Map<String, Object> callTecDoc(String method, Map<String, Object> params) {
        String url = parameterService.getValue(ParameterService.TECDOC_API_URL);
        String apiKey = parameterService.getValue(ParameterService.TECDOC_API_KEY);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = webClientBuilder.build().post().uri(url)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(method, params))
                    .retrieve().bodyToMono(Map.class).block();
            return resp != null ? resp : Collections.emptyMap();
        } catch (Exception e) {
            log.error("[TECDOC] Erreur appel {} : {}", method, e.getMessage());
            throw new ApiException(ErrorCode.BC_API_ERROR, "Erreur TecDoc");
        }
    }

    private Long providerId() {
        return Long.parseLong(parameterService.getValue(ParameterService.TECDOC_PROVIDER));
    }

    private String country() {
        return parameterService.getValue(ParameterService.TECDOC_COUNTRY);
    }

    private static String safeType(String type) {
        return (type == null || type.isBlank()) ? DEFAULT_LINKAGE_TYPE : type.trim();
    }

    /** Autocomplétion de la barre de recherche catalogue. */
    public Map<String, Object> getAutoCompleteSuggestions(String query) {
        Map<String, Object> params = new HashMap<>();
        params.put("articleCountry", country());
        params.put("lang", LANG);
        params.put("provider", providerId());
        params.put("searchQuery", query);
        return callTecDoc("getAutoCompleteSuggestions", params);
    }

    /** Étape 1 véhicule : constructeurs (facettes fabricants). */
    public Map<String, Object> getVehicleManufacturers(String linkageTargetType) {
        Map<String, Object> params = new HashMap<>();
        params.put("linkageTargetCountry", country());
        params.put("lang", LANG);
        params.put("provider", providerId());
        params.put("linkageTargetType", safeType(linkageTargetType));
        params.put("includeMfrFacets", true);
        params.put("page", 1);
        params.put("perPage", 1);
        return callTecDoc("getLinkageTargets", params);
    }

    /** Étape 2 véhicule : modèles / séries d'un constructeur (facettes séries). */
    public Map<String, Object> getVehicleModels(String linkageTargetType, Long mfrId) {
        Map<String, Object> params = new HashMap<>();
        params.put("linkageTargetCountry", country());
        params.put("lang", LANG);
        params.put("provider", providerId());
        params.put("linkageTargetType", safeType(linkageTargetType));
        params.put("mfrIds", mfrId);
        params.put("includeVehicleModelSeriesFacets", true);
        params.put("page", 1);
        params.put("perPage", 1);
        return callTecDoc("getLinkageTargets", params);
    }

    /** Étape 3 véhicule : types / motorisations d'une série de modèle. */
    public Map<String, Object> getVehicleTypes(String linkageTargetType, Long mfrId, Long modelSeriesId,
                                               int page, int perPage) {
        Map<String, Object> params = new HashMap<>();
        params.put("linkageTargetCountry", country());
        params.put("lang", LANG);
        params.put("provider", providerId());
        params.put("linkageTargetType", safeType(linkageTargetType));
        params.put("mfrIds", mfrId);
        params.put("vehicleModelSeriesIds", modelSeriesId);
        params.put("page", page);
        params.put("perPage", perPage);
        return callTecDoc("getLinkageTargets", params);
    }

    /** Familles / sous-familles (arbre assembly groups) pour un véhicule sélectionné. */
    public Map<String, Object> getAssemblyGroups(Long linkageTargetId, String linkageTargetType) {
        Map<String, Object> params = new HashMap<>();
        params.put("articleCountry", country());
        params.put("lang", LANG);
        params.put("provider", providerId());
        if (linkageTargetId != null) params.put("linkageTargetId", linkageTargetId);
        params.put("linkageTargetType", safeType(linkageTargetType));
        params.put("assemblyGroupFacetOptions", Map.of(
                "enabled", true,
                "includeCompleteTree", true));
        params.put("page", 1);
        params.put("perPage", 1);
        return callTecDoc("getArticles", params);
    }

    /** Recherche catalogue d'articles (texte / famille / véhicule) avec pagination + enrichissements V1. */
    public Map<String, Object> getCatalogArticles(TecDocCatalogQuery q) {
        Map<String, Object> params = new HashMap<>();
        params.put("articleCountry", country());
        params.put("lang", LANG);
        params.put("provider", providerId());

        if (q.searchQuery() != null && !q.searchQuery().isBlank()) {
            params.put("searchQuery", q.searchQuery());
            params.put("searchType", q.searchType() != null ? q.searchType() : 0);
            params.put("searchMatchType", q.searchMatchType() != null ? q.searchMatchType() : "exact");
        }
        if (q.assemblyGroupNodeId() != null) {
            params.put("assemblyGroupNodeIds", q.assemblyGroupNodeId());
        }
        if (q.linkageTargetId() != null) {
            params.put("linkageTargetId", q.linkageTargetId());
            params.put("linkageTargetType", safeType(q.linkageTargetType()));
        }
        if (q.dataSupplierIds() != null) {
            params.put("dataSupplierIds", q.dataSupplierIds());
        }

        params.put("page", q.page());
        params.put("perPage", q.perPage());

        // Enrichissements V1 (catalogue en ligne)
        params.put("includeGenericArticles", true);
        params.put("includeArticleText", true);
        params.put("includeOEMNumbers", true);
        params.put("includeImages", true);
        params.put("includeGTINs", true);
        params.put("includeArticleCriteria", true);

        // NB : pas de facette fabricants sur getArticles (non documentée par Pegasus et absente
        // de la réponse runtime). Le filtre fabricant s'appuie exclusivement sur la liste GLOBALE
        // getBrands (getDataSuppliers / GET /api/tecdoc/data-suppliers) + le param dataSupplierIds.
        return callTecDoc("getArticles", params);
    }

    /**
     * Liste GLOBALE des fabricants / fournisseurs TecDoc (référence getBrands, NON scopée à une
     * recherche). Sert de source au filtre fabricant quand la facette serveur n'est pas disponible.
     * On renvoie une liste légère {dataSupplierId, mfrName} (sans logos/adresses).
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getDataSuppliers() {
        Map<String, Object> params = new HashMap<>();
        params.put("articleCountry", country());
        params.put("lang", LANG);
        params.put("provider", providerId());
        Map<String, Object> resp = callTecDoc("getBrands", params);

        Object data = resp.get("data");
        Object array = (data instanceof Map) ? ((Map<String, Object>) data).get("array") : null;
        List<Map<String, Object>> out = new ArrayList<>();
        if (array instanceof List) {
            for (Object o : (List<?>) array) {
                if (o instanceof Map) {
                    Map<String, Object> b = (Map<String, Object>) o;
                    Object id = b.get("dataSupplierId");
                    Object name = b.get("mfrName");
                    if (id == null || name == null) continue;
                    Map<String, Object> m = new HashMap<>();
                    m.put("dataSupplierId", id);
                    m.put("mfrName", name);
                    out.add(m);
                }
            }
        }
        return out;
    }
}